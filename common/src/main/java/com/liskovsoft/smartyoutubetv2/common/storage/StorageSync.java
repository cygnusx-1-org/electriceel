package com.liskovsoft.smartyoutubetv2.common.storage;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.FileObserver;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.prefs.RemoteControlData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SyncedSettings;
import com.liskovsoft.smartyoutubetv2.common.prefs.SyncedSettings.Field;
import com.liskovsoft.smartyoutubetv2.common.prefs.SyncedSettings.Store;
import com.liskovsoft.smartyoutubetv2.common.storage.StorageClient.Change;
import com.liskovsoft.smartyoutubetv2.common.storage.StorageClient.DeviceUnknownException;
import com.liskovsoft.smartyoutubetv2.common.storage.StorageClient.PullResult;
import com.liskovsoft.smartyoutubetv2.common.storage.StorageClient.PushChange;
import com.liskovsoft.smartyoutubetv2.common.storage.StorageClient.PushResult;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.ServiceProfileData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Shares the settings listed in {@link SyncedSettings} with the other devices of the device group, through electriceel-storage.
 * The settings stay where they are (the app reads and saves them as always): the sync only mirrors them, so the app works the
 * same with the server down, unreachable or gone.<br/>
 * Two threads: the loop waits for the server's changes (a pull that waits up to {@link StorageClient#WAIT_SEC}), the worker
 * owns the sync state, sends the changes made here (seen by a {@link FileObserver} on the saved settings) and applies the ones
 * from the server, on the main thread, where the settings live.<br/>
 * Each scope ("global", "account:<key>") is joined once: pulled whole, the server's values taken, then this device's values
 * the server lacks sent. Afterwards a change made here is sent on top of the revision last seen, and the server keeps the newer
 * of two concurrent changes. While Child mode is on for an account, its settings aren't synced either way, and when it ends
 * the account joins again: the child's restrictions never reach the other devices, and the other devices' settings never
 * lift them.
 */
public final class StorageSync {
    private static final String TAG = StorageSync.class.getSimpleName();
    private static final String RESTORED_MARKER = "restored";
    private static final long MAX_BACKOFF_MS = 5 * 60_000;
    private static final long PUSH_DELAY_MS = 1_500;
    private static final long CHECK_PERIOD_MS = 5 * 60_000;
    private static final long MAIN_THREAD_TIMEOUT_MS = 10_000;
    private static final int PUSH_BATCH = 500;
    private static final long PUSH_BATCH_BYTES = 400_000;
    @SuppressLint("StaticFieldLeak")
    private static StorageSync sInstance;

    public enum State {
        /**
         * Storage sync is off
         */
        OFF,
        NO_SERVER,
        NOT_AUTHORIZED,
        /**
         * Waiting for the authorization in the web interface: the status has the code
         */
        PAIRING,
        CONNECTING,
        SYNCED,
        /**
         * The server can't be reached (or refused a request): retried, with a growing delay
         */
        OFFLINE
    }

    public static final class Status {
        public final State state;
        @Nullable
        public final String detail;
        public final long lastSyncAt;

        Status(State state, @Nullable String detail, long lastSyncAt) {
            this.state = state;
            this.detail = detail;
            this.lastSyncAt = lastSyncAt;
        }
    }

    public interface Listener {
        void onStatusChanged(Status status);
    }

    /**
     * Where a scope's settings are: the global data keys, or the profile of an account (null: signed out)
     */
    private static final class Target {
        final String scope;
        final boolean isGlobal;
        @Nullable
        final Account account;
        final String name;

        Target(String scope, boolean isGlobal, @Nullable Account account, String name) {
            this.scope = scope;
            this.isGlobal = isGlobal;
            this.account = account;
            this.name = name;
        }

        boolean has(Field field) {
            return field.isAccount != isGlobal;
        }
    }

    /**
     * A server value to save here. expected: the value the worker read; if the saved one differs by the time it's written on
     * the main thread, it was just changed here, and it's sent instead.
     */
    private static final class Write {
        final Target target;
        final Field field;
        final String json;
        final long revision;
        @Nullable
        final String expected;
        boolean isDone;

        Write(Target target, Field field, String json, long revision, @Nullable String expected) {
            this.target = target;
            this.field = field;
            this.json = json;
            this.revision = revision;
            this.expected = expected;
        }
    }

    private final Context mContext;
    private final AppPrefs mPrefs;
    private final StorageConfig mConfig;
    private final File mDir;
    private final ScheduledExecutorService mWorker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ee-storage-sync");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private final CopyOnWriteArrayList<Listener> mListeners = new CopyOnWriteArrayList<>();
    private final Object mLoopLock = new Object();
    // The worker's
    private SyncState mState;
    private Map<String, Target> mTargets = new LinkedHashMap<>();
    @Nullable
    private ScheduledFuture<?> mPushTask;
    // The loop's
    @Nullable
    private volatile Thread mLoop;
    @Nullable
    private volatile StorageClient mClient;
    private volatile boolean mIsWoken;
    private volatile boolean mNeedsReport = true;
    private volatile Status mStatus = new Status(State.OFF, null, 0);
    private volatile long mLastSyncAt;
    // Strong: an observer that's collected stops watching
    private final List<FileObserver> mObservers = new ArrayList<>();
    @Nullable
    private ScheduledFuture<?> mPeriodicCheck;
    // Strong: AppPrefs keeps its listeners weakly
    private final ProfileChangeListener mProfileListener = new ProfileChangeListener() {
        @Override
        public void onProfileChanged() {
            // Another account: other scopes, maybe; Child mode of the new one
            mNeedsReport = true;
            wake();
        }
    };

    private StorageSync(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = AppPrefs.instance(mContext);
        mConfig = new StorageConfig(mContext);
        mDir = new File(FileHelpers.getFilesDir(mContext), StorageConfig.DIR);
        mWorker.execute(() -> mState = new SyncState(mDir));
        mPrefs.addListener(mProfileListener);
    }

    public static StorageSync instance(Context context) {
        if (sInstance == null) {
            sInstance = new StorageSync(context);
        }

        return sInstance;
    }

    public StorageConfig getConfig() {
        return mConfig;
    }

    public Status getStatus() {
        return mStatus;
    }

    public void addListener(Listener listener) {
        mListeners.addIfAbsent(listener);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    private void setStatus(State state, @Nullable String detail) {
        Status status = new Status(state, detail, mLastSyncAt);
        Status old = mStatus;
        mStatus = status;

        if (old.state != state || !TextUtils.equals(old.detail, detail) || old.lastSyncAt != status.lastSyncAt) {
            Utils.post(() -> {
                for (Listener listener : mListeners) {
                    listener.onStatusChanged(status);
                }
            });
        }
    }

    /**
     * When the app starts (and whenever storage sync is turned on): nothing happens while it's off. Never waits for the server.
     */
    public synchronized void start() {
        if (!mConfig.isEnabled()) {
            setStatus(State.OFF, null);
            return;
        }

        consumeRestoredMarker();

        if (mLoop == null || !mLoop.isAlive()) {
            mLoop = new Thread(this::loop, "ee-storage-loop");
            mLoop.setPriority(Thread.MIN_PRIORITY);
            mLoop.setDaemon(true);
            mLoop.start();
        }

        startObserver();
        wake();
    }

    public synchronized void stop() {
        stopObserver();
        Thread loop = mLoop;
        mLoop = null;

        if (loop != null) {
            loop.interrupt();
        }

        wake();
        setStatus(State.OFF, null);
    }

    /**
     * The loop re-reads the settings of the sync and syncs at once, without waiting for the server's next change
     */
    public void wake() {
        mIsWoken = true;
        StorageClient client = mClient;

        if (client != null) {
            client.cancel();
        }

        synchronized (mLoopLock) {
            mLoopLock.notifyAll();
        }
    }

    /**
     * A backup of the app is about to be restored over the settings (see BackupAndRestoreManager): nothing is sent until the next
     * start, when every scope joins again, taking the server's values, so the restored ones don't overwrite the other devices'
     * unasked.
     */
    public static void onBackupRestored(Context context) {
        File marker = new File(new File(FileHelpers.getFilesDir(context), StorageConfig.DIR), RESTORED_MARKER);
        FileHelpers.stringToFileAtomic("1", marker);
    }

    private void consumeRestoredMarker() {
        File marker = new File(mDir, RESTORED_MARKER);

        if (marker.exists()) {
            mWorker.execute(() -> {
                Log.i(TAG, "A backup was restored: every scope joins again");
                mState.resetAll();
                mState.save();
                marker.delete();
            });
        }
    }

    private void loop() {
        long backoffMs = 0;

        while (mLoop == Thread.currentThread() && mConfig.isEnabled()) {
            mIsWoken = false;
            String url = mConfig.getServerUrl();

            if (url == null) {
                setStatus(State.NO_SERVER, null);
                waitForWake(0);
                continue;
            }

            StorageClient client = new StorageClient(url, mConfig.getToken());
            mClient = client;

            try {
                if (!mConfig.isAuthorized()) {
                    if (mConfig.getPairingId() != null) {
                        pairStep(client);
                    } else {
                        setStatus(State.NOT_AUTHORIZED, mStatus.state == State.NOT_AUTHORIZED ? mStatus.detail : null);
                        waitForWake(0);
                    }
                    backoffMs = 0;
                    continue;
                }

                if (mStatus.state != State.SYNCED) {
                    setStatus(State.CONNECTING, null);
                }

                if (mNeedsReport) {
                    mNeedsReport = false;
                    report(client);
                }

                Map<String, Long> cursors = onWorker(() -> prepare(client));
                mLastSyncAt = System.currentTimeMillis();
                setStatus(State.SYNCED, null);
                backoffMs = 0;

                if (cursors.isEmpty()) {
                    // No scope: no account picked, or Child mode
                    waitForWake(CHECK_PERIOD_MS);
                    continue;
                }

                if (mIsWoken) {
                    continue;
                }

                PullResult result = client.pull(cursors, StorageClient.WAIT_SEC);
                onWorker(() -> {
                    applyPull(result, cursors);
                    return null;
                });

                if (!result.changes.isEmpty()) {
                    mLastSyncAt = System.currentTimeMillis();
                    setStatus(State.SYNCED, null);
                }
            } catch (DeviceUnknownException e) {
                Log.w(TAG, "No longer authorized: %s", e.getMessage());
                mConfig.clearAuthorization();
                mWorker.execute(() -> {
                    mState.resetAll();
                    mState.save();
                });
                setStatus(State.NOT_AUTHORIZED, "revoked");
            } catch (IOException e) {
                if (mIsWoken || mLoop != Thread.currentThread()) {
                    continue; // canceled by wake: not an error
                }

                Log.w(TAG, "Sync failed: %s", e.getMessage());
                mNeedsReport = true;
                setStatus(State.OFFLINE, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                backoffMs = backoffMs == 0 ? 2_000 : Math.min(MAX_BACKOFF_MS, backoffMs * 2);
                waitForWake(backoffMs);
            } catch (RuntimeException e) {
                // A bug must not take the app down: the sync stops trying for a while
                Log.e(TAG, "Sync error: %s", e);
                setStatus(State.OFFLINE, e.getClass().getSimpleName());
                waitForWake(MAX_BACKOFF_MS);
            } finally {
                mClient = null;
            }
        }
    }

    private void waitForWake(long ms) {
        synchronized (mLoopLock) {
            if (mIsWoken) {
                return;
            }

            try {
                if (ms > 0) {
                    mLoopLock.wait(ms);
                } else {
                    mLoopLock.wait();
                }
            } catch (InterruptedException e) {
                // stop()
            }
        }
    }

    private <T> T onWorker(Callable<T> task) throws IOException {
        try {
            return mWorker.submit(task).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();

            if (cause instanceof IOException) {
                throw (IOException) cause;
            }

            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }

            throw new IOException(cause);
        }
    }

    // Authorization

    /**
     * Asks the server to authorize this device: the web interface lists it with the code shown here. Off the main thread.
     */
    void requestAuthorization() throws IOException {
        String url = mConfig.getServerUrl();

        if (url == null) {
            throw new IOException("no server");
        }

        StorageClient.Pairing pairing = new StorageClient(url, null).createPairing(deviceInfo());
        mConfig.setPairing(pairing.id, pairing.secret, pairing.code, pairing.expiresAt);
        wake();
    }

    void cancelAuthorization() {
        mConfig.clearPairing();
        setStatus(State.NOT_AUTHORIZED, null);
        wake();
    }

    private void pairStep(StorageClient client) throws IOException {
        setStatus(State.PAIRING, mConfig.getPairingCode());
        String id = mConfig.getPairingId();
        String secret = mConfig.getPairingSecret();

        if (id == null || secret == null) {
            return;
        }

        StorageClient.PairingStatus st = client.pollPairing(id, secret, StorageClient.WAIT_SEC);

        switch (st.status) {
            case "approved":
                if (st.token == null) {
                    throw new IOException("approved without a token");
                }
                mConfig.setAuthorization(st.deviceId, st.token, st.groupId, st.groupName);
                onWorker(() -> {
                    // A new group: every scope joins it
                    mState.resetAll();
                    mState.save();
                    return null;
                });
                mNeedsReport = true;
                setStatus(State.CONNECTING, null);
                break;
            case "denied":
                mConfig.clearPairing();
                setStatus(State.NOT_AUTHORIZED, "denied");
                break;
            case "expired":
                mConfig.clearPairing();
                setStatus(State.NOT_AUTHORIZED, "expired");
                break;
            default:
                // Still waiting
        }
    }

    /**
     * The server forgets this device; it keeps its settings. Off the main thread.
     */
    void forgetServer() {
        String url = mConfig.getServerUrl();
        String token = mConfig.getToken();

        if (url != null && token != null) {
            try {
                new StorageClient(url, token).deleteDevice();
            } catch (IOException e) {
                // Revoked already, or the server is down: an admin removes it from the web interface then
                Log.w(TAG, "Unpairing: %s", e.getMessage());
            }
        }

        mConfig.clearAuthorization();
        mWorker.execute(() -> {
            mState.resetAll();
            mState.save();
        });
        setStatus(State.NOT_AUTHORIZED, null);
        wake();
    }

    /**
     * An account (or the global settings) is no longer synced: when it's synced again, it joins again
     */
    void onScopeDropped(String scope) {
        mWorker.execute(() -> {
            mState.reset(scope);
            mState.save();
        });
        mNeedsReport = true;
        wake();
    }

    /**
     * "All accounts" was turned off: the accounts not picked one by one stop being synced
     */
    void onAllAccountsDropped() {
        mWorker.execute(() -> {
            for (String scope : mState.names()) {
                if (scope.startsWith("account:") && !mConfig.isAccountSynced(scope.substring("account:".length()))) {
                    mState.reset(scope);
                }
            }

            mState.save();
        });
    }

    void onOptionsChanged() {
        mNeedsReport = true;
        wake();
    }

    private JSONObject deviceInfo() {
        JSONObject json = new JSONObject();

        try {
            String name = null;

            if (Build.VERSION.SDK_INT >= 25) {
                name = Settings.Global.getString(mContext.getContentResolver(), Settings.Global.DEVICE_NAME);
            }

            json.put("deviceName", !TextUtils.isEmpty(name) ? name : Build.MODEL);
            json.put("manufacturer", Build.MANUFACTURER);
            json.put("model", Build.MODEL);
            json.put("sdk", Build.VERSION.SDK_INT);
            json.put("appPackage", mContext.getPackageName());
            json.put("appVersion", AppInfoHelpers.getAppVersionName(mContext));
            json.put("appVersionCode", AppInfoHelpers.getAppVersionCode(mContext));
            json.put("syncSchema", SyncedSettings.SCHEMA);
        } catch (JSONException e) {
            // Not with these values
        }

        return json;
    }

    // What's synced

    /**
     * The scopes to sync now. Read on the worker: the accounts of MediaServiceManager and the choices of the config.
     */
    private Map<String, Target> targets() {
        Map<String, Target> targets = new LinkedHashMap<>();

        if (mConfig.isGlobalSynced()) {
            targets.put("global", new Target("global", true, null, "global"));
        }

        List<Account> accounts = MediaServiceManager.instance().getAccounts();

        if (accounts != null) {
            for (Account account : accounts) {
                String key = AccountKeys.of(account);

                if (AccountKeys.isAccount(account) && mConfig.isAccountSynced(key)) {
                    String name = account.getName() != null ? account.getName() : "";
                    targets.put("account:" + key, new Target("account:" + key, false, account, name));
                }
            }
        }

        if (mConfig.isAccountSynced(AccountKeys.SIGNED_OUT)) {
            String scope = "account:" + AccountKeys.SIGNED_OUT;
            targets.put(scope, new Target(scope, false, null, "Signed out"));
        }

        return targets;
    }

    /**
     * The file name the field's data is kept under for the target: the app's and the services' name the profiles alike, but the
     * app's shared settings have no name (see AppPrefs.getProfileKey)
     */
    private String fileKey(Target target, Field field) {
        if (target.isGlobal) {
            return field.dataKey;
        }

        return field.store == Store.SERVICE ? ServiceProfileData.dataKey(target.account, field.dataKey)
                : mPrefs.getAccountDataKey(target.account, field.dataKey);
    }

    private File dataFile(Field field, String fileKey) {
        return field.store == Store.SERVICE ? ServiceProfileData.dataFile(fileKey) : mPrefs.getDataFile(fileKey);
    }

    @Nullable
    private String readData(Target target, Field field, Map<String, String> cache) {
        String fileKey = fileKey(target, field);
        String cacheKey = field.store + "/" + fileKey;

        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        String data = FileHelpers.getFileContents(dataFile(field, fileKey));
        cache.put(cacheKey, data);
        return data;
    }

    private boolean isChildMode(Target target) {
        if (target.isGlobal) {
            return false;
        }

        String general = FileHelpers.getFileContents(mPrefs.getDataFile(mPrefs.getAccountDataKey(target.account, SyncedSettings.generalDataKey())));
        return SyncedSettings.isChildMode(general);
    }

    /**
     * On the worker, before each pull: which scopes are synced, Child mode, the joins, the changes made here. Returns the
     * cursors to pull from.
     */
    private Map<String, Long> prepare(StorageClient client) throws IOException {
        mTargets = targets();
        Map<String, Long> cursors = new LinkedHashMap<>();

        for (Target target : mTargets.values()) {
            SyncState.Scope scope = mState.scope(target.scope);

            if (pauseForChildMode(target)) {
                continue;
            }

            if (scope.paused) {
                // Child mode ended: the account takes the group's settings again
                mState.reset(target.scope);
                scope = mState.scope(target.scope);
            }

            if (!scope.joined) {
                reconcile(client, target, scope);
            }

            cursors.put(target.scope, scope.cursor);
        }

        pushLocal(client);
        return cursors;
    }

    /**
     * While Child mode is on for the account its scope is paused, and forgotten: when it ends, the account joins again
     */
    private boolean pauseForChildMode(Target target) {
        if (!isChildMode(target)) {
            return false;
        }

        if (!mState.scope(target.scope).paused) {
            Log.i(TAG, "Child mode: %s isn't synced until it ends", target.name);
            mState.reset(target.scope);
            mState.scope(target.scope).paused = true;
            mState.save();
        }

        return true;
    }

    /**
     * Joins a scope: its whole state is pulled, the server's values taken; the settings the server hasn't got are sent next
     * (see pushLocal)
     */
    private void reconcile(StorageClient client, Target target, SyncState.Scope scope) throws IOException {
        Map<String, Change> server = new HashMap<>();
        long cursor = 0;
        PullResult result;

        do {
            Map<String, Long> cursors = new HashMap<>();
            cursors.put(target.scope, cursor);
            result = client.pull(cursors, 0);

            for (Change change : result.changes) {
                server.put(change.key, change);
            }

            Long next = result.cursors.get(target.scope);
            cursor = next != null ? next : cursor;
        } while (result.more);

        Map<String, String> cache = new HashMap<>();
        List<Write> writes = new ArrayList<>();

        for (Field field : SyncedSettings.all()) {
            Change change = server.get(field.key);

            if (!target.has(field) || change == null || change.json == null) {
                continue;
            }

            SyncState.Key key = scope.key(field.key);
            String json = SyncValues.canonical(field, change.json);
            String current = SyncValues.read(field, readData(target, field, cache));

            if (json == null) {
                Log.w(TAG, "%s: the server's value doesn't fit this version, kept the own", field.key);
                key.base = change.revision;
                continue;
            }

            if (json.equals(current)) {
                key.base = change.revision;
                key.value = json;
            } else {
                writes.add(new Write(target, field, json, change.revision, current));
            }
        }

        applyWrites(writes, scope);
        scope.cursor = cursor;
        scope.joined = true;
        mState.save();
        Log.i(TAG, "Joined %s: %d settings on the server, %d taken", target.scope, server.size(), writes.size());
    }

    /**
     * On the worker: sends the settings changed here since they were last synced
     */
    private void pushLocal(StorageClient client) throws IOException {
        // A backup is being restored over the settings: they're not changes made here (the app is killed next, see start)
        if (new File(mDir, RESTORED_MARKER).exists()) {
            return;
        }

        List<PushChange> changes = new ArrayList<>();
        Map<String, Field> fields = new HashMap<>();
        long now = System.currentTimeMillis();

        for (Target target : mTargets.values()) {
            // Child mode just turned on: what it changed isn't sent (the player buttons, the menus...)
            if (pauseForChildMode(target)) {
                continue;
            }

            SyncState.Scope scope = mState.scope(target.scope);

            if (!scope.joined || scope.paused) {
                continue;
            }

            Map<String, String> cache = new HashMap<>();

            for (Field field : SyncedSettings.all()) {
                if (!target.has(field)) {
                    continue;
                }

                String current = SyncValues.read(field, readData(target, field, cache));

                if (current == null) {
                    continue; // the default: nothing saved
                }

                SyncState.Key key = scope.keys.get(field.key);

                if (key != null && current.equals(key.value)) {
                    continue;
                }

                // Never had from the server: e.g. a setting this version just started to sync. Like a join, the group's value wins
                // if another device sent it first (the Home rows and the subscription groups of one device go together).
                boolean isNeverSynced = key == null || key.base == 0 && key.value == null;
                key = scope.key(field.key);

                if (key.dirtyAt == 0) {
                    key.dirtyAt = now;
                }

                changes.add(new PushChange(target.scope, field.key, current, key.base, key.dirtyAt, isNeverSynced));
                fields.put(field.key, field);
            }
        }

        if (changes.isEmpty()) {
            return;
        }

        Map<String, PushChange> sent = new HashMap<>();

        for (PushChange change : changes) {
            sent.put(change.scope + "|" + change.key, change);
        }

        List<Write> superseded = new ArrayList<>();

        for (int from = 0; from < changes.size(); ) {
            // By count and by size: the server takes requests of up to 1 MB
            int to = from;
            long bytes = 0;

            while (to < changes.size() && to - from < PUSH_BATCH && (to == from || bytes + changes.get(to).json.length() <= PUSH_BATCH_BYTES)) {
                bytes += changes.get(to).json.length();
                to++;
            }

            List<PushResult> results = client.push(changes.subList(from, to));
            from = to;

            for (PushResult result : results) {
                PushChange change = sent.get(result.scope + "|" + result.key);
                Field field = fields.get(result.key);
                Target target = mTargets.get(result.scope);

                if (change == null || field == null || target == null) {
                    continue;
                }

                SyncState.Key key = mState.scope(result.scope).key(result.key);

                switch (result.status) {
                    case "applied":
                    case "unchanged":
                        key.base = result.revision;
                        key.value = change.json;
                        key.dirtyAt = 0;
                        break;
                    case "superseded":
                        // Another device changed it later: its value is taken here
                        String json = SyncValues.canonical(field, result.json);
                        key.base = result.revision;
                        key.dirtyAt = 0;

                        if (json != null) {
                            key.value = json;
                            superseded.add(new Write(target, field, json, result.revision, change.json));
                        } else {
                            key.value = change.json;
                        }
                        break;
                    default:
                        Log.w(TAG, "%s refused: %s", result.key, result.error);
                        key.value = change.json; // not sent again until it changes
                        key.dirtyAt = 0;
                }
            }

            mState.save();
        }

        for (Write write : superseded) {
            applyWrites(java.util.Collections.singletonList(write), mState.scope(write.target.scope));
        }

        mLastSyncAt = System.currentTimeMillis();
        Log.d(TAG, "Sent %d settings", changes.size());
    }

    /**
     * On the worker: the server's changes since the cursors. A cursor going back means the group's database went back (restored
     * from a backup): the revisions this device knows mean something else now, so the scope joins again.
     */
    private void applyPull(PullResult result, Map<String, Long> sent) throws IOException {
        boolean isRewound = false;

        for (Map.Entry<String, Long> e : result.cursors.entrySet()) {
            Long before = sent.get(e.getKey());

            if (before != null && e.getValue() < before) {
                Log.w(TAG, "%s: the server's revision went back from %s to %s, joining again", e.getKey(), before, e.getValue());
                mState.reset(e.getKey());
                isRewound = true;
            }
        }

        if (isRewound) {
            mState.save();
            mIsWoken = true;
            return;
        }

        Map<String, String> cache = new HashMap<>();
        List<Write> writes = new ArrayList<>();
        Map<String, SyncState.Scope> scopes = new HashMap<>();

        for (Change change : result.changes) {
            Target target = mTargets.get(change.scope);
            Field field = SyncedSettings.get(change.key);
            SyncState.Scope scope = mState.scope(change.scope);

            if (target == null || !scope.joined || scope.paused) {
                continue;
            }

            if (field == null || !target.has(field)) {
                continue; // a setting of another app version
            }

            SyncState.Key key = scope.key(change.key);

            if (change.revision <= key.base) {
                continue; // known: this device's own change, or one taken already
            }

            if (change.json == null) {
                // Removed from the group in the web interface: kept here, and sent again if changed here
                key.base = change.revision;
                continue;
            }

            String json = SyncValues.canonical(field, change.json);
            String current = SyncValues.read(field, readData(target, field, cache));

            if (json == null) {
                Log.w(TAG, "%s: the server's value doesn't fit this version, kept the own", field.key);
                key.base = change.revision;
                continue;
            }

            if (json.equals(current)) {
                key.base = change.revision;
                key.value = json;
                key.dirtyAt = 0;
                continue;
            }

            boolean isChangedHere = key.dirtyAt != 0 || key.value != null && current != null && !current.equals(key.value);

            if (isChangedHere) {
                continue; // sent next, and the server keeps the newer of the two
            }

            writes.add(new Write(target, field, json, change.revision, current));
            scopes.put(change.scope, scope);
        }

        for (Map.Entry<String, SyncState.Scope> e : scopes.entrySet()) {
            List<Write> own = new ArrayList<>();

            for (Write write : writes) {
                if (write.target.scope.equals(e.getKey())) {
                    own.add(write);
                }
            }

            applyWrites(own, e.getValue());
        }

        for (Map.Entry<String, Long> e : result.cursors.entrySet()) {
            SyncState.Scope scope = mState.scope(e.getKey());

            if (scope.joined && !scope.paused) {
                scope.cursor = e.getValue();
            }
        }

        mState.save();

        if (result.more) {
            mIsWoken = true; // the rest right away
        }

        if (!writes.isEmpty()) {
            Log.i(TAG, "Took %d settings from the server", writes.size());
        }
    }

    /**
     * Saves server values here, on the main thread: the values waiting to be saved first (see AppPrefs.persistPendingData), then
     * each data key read, changed and written, then its owner told to read it again. The sync state follows only for what was
     * written.
     */
    private void applyWrites(List<Write> writes, SyncState.Scope scope) throws IOException {
        if (writes.isEmpty()) {
            return;
        }

        CountDownLatch done = new CountDownLatch(1);

        Utils.post(() -> {
            try {
                mPrefs.persistPendingData();
                Map<String, List<Write>> byKey = new LinkedHashMap<>();

                for (Write write : writes) {
                    String key = write.field.store + "/" + fileKey(write.target, write.field);
                    List<Write> list = byKey.get(key);

                    if (list == null) {
                        list = new ArrayList<>();
                        byKey.put(key, list);
                    }

                    list.add(write);
                }

                Set<String> selectedKeys = new HashSet<>();
                Map<String, String> selectedServiceData = new LinkedHashMap<>();
                boolean isRemoteControlChanged = false;
                boolean isLauncherChanged = false;

                for (List<Write> list : byKey.values()) {
                    Target target = list.get(0).target;
                    Field first = list.get(0).field;
                    String fileKey = fileKey(target, first);
                    File file = dataFile(first, fileKey);
                    String data = FileHelpers.getFileContents(file);
                    String original = data;

                    for (Write write : list) {
                        if (!Objects.equals(SyncValues.read(write.field, data), write.expected)) {
                            continue; // just changed here: sent instead
                        }

                        String changed = SyncValues.write(write.field, data, write.json);

                        if (changed != null) {
                            data = changed;
                            write.isDone = true;
                        }
                    }

                    if (data != null && !data.equals(original)) {
                        if (first.store == Store.SERVICE) {
                            ServiceProfileData.setData(fileKey, data);
                        } else {
                            mPrefs.setData(fileKey, data);
                        }

                        if (target.isGlobal) {
                            isRemoteControlChanged |= SyncedSettings.REMOTE_CONTROL_DATA.equals(first.dataKey);
                        } else if (mPrefs.isSelectedProfile(target.account)) {
                            selectedKeys.add(first.dataKey);

                            if (first.store == Store.SERVICE) {
                                selectedServiceData.put(first.dataKey, data);
                            }
                        }

                        // The launcher shows the channels of every account
                        isLauncherChanged |= SyncedSettings.isLauncherData(first.dataKey);
                    }
                }

                // The services first: the app's listeners read them (e.g. the channels of the subscription groups)
                for (Map.Entry<String, String> e : selectedServiceData.entrySet()) {
                    ServiceProfileData.onDataSynced(e.getKey(), e.getValue());
                }

                if (!selectedKeys.isEmpty()) {
                    mPrefs.onProfileDataSynced(selectedKeys);
                }

                if (isRemoteControlChanged) {
                    RemoteControlData.instance(mContext).onDataSynced();
                }

                if (isLauncherChanged) {
                    Utils.updateChannels(mContext);
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Applying the server's settings: %s", e);
            } finally {
                done.countDown();
            }
        });

        try {
            if (!done.await(MAIN_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IOException("the app is busy");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }

        for (Write write : writes) {
            if (write.isDone) {
                SyncState.Key key = scope.key(write.field.key);
                key.base = write.revision;
                key.value = write.json;
                key.dirtyAt = 0;
            }
        }
    }

    // The server's records of the device

    private void report(StorageClient client) throws IOException {
        String manifest = manifest();
        JSONObject report = deviceInfo();
        Map<String, Target> targets = targets();

        try {
            report.put("manifestHash", sha256(manifest));
            report.put("syncGlobal", mConfig.isGlobalSynced());
            JSONArray accounts = new JSONArray();
            List<Account> list = MediaServiceManager.instance().getAccounts();

            if (list != null) {
                for (Account account : list) {
                    if (!AccountKeys.isAccount(account)) {
                        continue;
                    }

                    String key = AccountKeys.of(account);
                    JSONObject item = new JSONObject();
                    item.put("key", key);
                    item.put("name", account.getName() != null ? account.getName() : "");
                    item.put("synced", targets.containsKey("account:" + key));
                    accounts.put(item);
                }
            }

            if (mConfig.isAccountSynced(AccountKeys.SIGNED_OUT)) {
                JSONObject item = new JSONObject();
                item.put("key", AccountKeys.SIGNED_OUT);
                item.put("name", "Signed out");
                item.put("synced", true);
                accounts.put(item);
            }

            report.put("accounts", accounts);
        } catch (JSONException e) {
            throw new IOException(e);
        }

        StorageClient.DeviceResponse response = client.putDevice(report);
        mConfig.setDeviceInfo(response.deviceName, response.groupId, response.groupName);

        if (response.needsManifest) {
            client.putManifest(manifest);
        }
    }

    /**
     * What this version syncs, for the web interface's editor: the same text, so the same hash, for the same version
     */
    String manifest() {
        try {
            JSONArray settings = new JSONArray();

            for (Field field : SyncedSettings.all()) {
                JSONObject item = new JSONObject();
                item.put("scope", field.isAccount ? "account" : "global");
                item.put("key", field.key);
                item.put("type", field.type.name);
                item.put("section", field.section);
                item.put("label", field.label);

                if (!field.options.isEmpty()) {
                    JSONArray options = new JSONArray();

                    for (SyncedSettings.Option option : field.options) {
                        options.put(new JSONObject().put("value", option.value).put("label", option.label));
                    }

                    item.put("options", options);
                }

                settings.put(item);
            }

            JSONObject json = new JSONObject();
            json.put("syncSchema", SyncedSettings.SCHEMA);
            json.put("appVersion", AppInfoHelpers.getAppVersionName(mContext));
            json.put("appVersionCode", AppInfoHelpers.getAppVersionCode(mContext));
            json.put("settings", settings);
            return json.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();

            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // The changes made here

    /**
     * The settings are saved as files: a write of one (renamed into place, see FileHelpers.stringToFileAtomic) is sent a moment
     * later, so a burst of changes goes in one request. A check every few minutes catches what the observer missed.
     */
    private void startObserver() {
        if (!mObservers.isEmpty()) {
            return;
        }

        File appDir = mPrefs.getDataFile(SyncedSettings.generalDataKey()).getParentFile();
        File serviceDir = ServiceProfileData.dataDir();

        for (File dir : new File[] {appDir, serviceDir}) {
            if (dir == null) {
                continue;
            }

            dir.mkdirs();
            int mask = FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE;
            FileObserver observer = Build.VERSION.SDK_INT >= 29 ? new FileObserver(dir, mask) {
                @Override
                public void onEvent(int event, @Nullable String path) {
                    onFileChanged(path);
                }
            } : new FileObserver(dir.getAbsolutePath(), mask) {
                @Override
                public void onEvent(int event, @Nullable String path) {
                    onFileChanged(path);
                }
            };
            observer.startWatching();
            mObservers.add(observer);
        }

        mPeriodicCheck = mWorker.scheduleWithFixedDelay(() -> schedulePush(0), CHECK_PERIOD_MS, CHECK_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    private void stopObserver() {
        for (FileObserver observer : mObservers) {
            observer.stopWatching();
        }

        mObservers.clear();

        if (mPeriodicCheck != null) {
            mPeriodicCheck.cancel(false);
            mPeriodicCheck = null;
        }
    }

    private void onFileChanged(@Nullable String path) {
        if (path == null || path.endsWith(".tmp")) {
            return;
        }

        schedulePush(PUSH_DELAY_MS);
    }

    private void schedulePush(long delayMs) {
        mWorker.execute(() -> {
            if (mPushTask != null && !mPushTask.isDone()) {
                return; // the burst goes with it
            }

            mPushTask = mWorker.schedule(this::pushNow, delayMs, TimeUnit.MILLISECONDS);
        });
    }

    private void pushNow() {
        String url = mConfig.getServerUrl();

        if (!mConfig.isEnabled() || !mConfig.isAuthorized() || url == null || mState == null) {
            return;
        }

        try {
            mTargets = targets();
            pushLocal(new StorageClient(url, mConfig.getToken()));

            // Child mode just ended (it's seen here, once per burst of saves): the loop joins the account again now
            for (Target target : mTargets.values()) {
                if (mState.scope(target.scope).paused && !isChildMode(target)) {
                    wake();
                    break;
                }
            }
        } catch (DeviceUnknownException e) {
            wake(); // the loop deals with it
        } catch (IOException e) {
            Log.w(TAG, "Sending failed, retried by the loop: %s", e.getMessage());
            wake();
        } catch (RuntimeException e) {
            Log.e(TAG, "Sending: %s", e);
        }
    }

    // For the tests

    /**
     * Stops it and drops the instance: the next one reads its files again
     */
    static void resetForTesting() {
        if (sInstance != null) {
            sInstance.stop();
            sInstance.mWorker.shutdownNow();
            sInstance.mPrefs.removeListener(sInstance.mProfileListener);
            sInstance = null;
        }
    }

    /**
     * Runs on the worker and waits: the tests see its state settled
     */
    void awaitWorker() throws InterruptedException, ExecutionException, TimeoutException {
        mWorker.submit(() -> null).get(10, TimeUnit.SECONDS);
    }
}
