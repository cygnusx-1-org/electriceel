package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLaterData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.subjects.BehaviorSubject;

/**
 * Knows the videos in the signed-in account's Watch later, to mark or hide them in the sections picked in the
 * Watch later setting (see VideoGroup).<br/>
 * The whole playlist is read (15 videos a request) before the first cards that need it are shown (see HiddenVideoResolver),
 * for {@link #MAX_HOLD_MS} at most.
 * It's kept on disk, so a start doesn't wait for it, and read again in the background once it's {@link #REFRESH_PERIOD_MS} old.<br/>
 * Videos added or removed in the app count right away (see {@link #onPlaylistEdited}).
 */
public class WatchLaterManager {
    private static final String TAG = WatchLaterManager.class.getSimpleName();
    public static final String WATCH_LATER_PLAYLIST_ID = "WL";
    public static final String WATCH_LATER_CHANNEL_ID = "VLWL"; // the playlist opened as a channel
    private static final String IDS_FILE = "watchlater/video_ids.txt";
    private static final long REFRESH_PERIOD_MS = 15 * 60 * 1_000;
    private static final long RETRY_PERIOD_MS = 60 * 1_000;
    private static final long SAVE_DELAY_MS = 10_000;
    // A long list (5000 videos at most) takes a minute to read. The cards aren't held that long.
    private static final long MAX_HOLD_MS = 10_000;
    @SuppressLint("StaticFieldLeak")
    private static WatchLaterManager sInstance;
    private final Context mContext;
    private final File mIdsFile;
    private Loader mLoader;
    private AccountSource mAccountSource;
    private volatile Snapshot mSnapshot; // null while no list is known
    private final Map<String, Observable<Boolean>> mRunningLoads = new ConcurrentHashMap<>(); // by account
    private final Map<String, Long> mLoadStartTimes = new ConcurrentHashMap<>(); // by account
    // Added (true) or removed while a load runs: the list it reads might be older
    private final Map<String, Boolean> mPendingEdits = new ConcurrentHashMap<>();
    private String mFailedAccount;
    private long mFailedTimeMs;
    // The list saved on disk is known
    private final BehaviorSubject<Boolean> mRestored = BehaviorSubject.create();
    private final Runnable mSaveIds = () -> RxHelper.runAsync(this::saveIds);
    private OnEdited mOnEdited;

    interface Loader {
        /**
         * @return every video id in Watch later, an error when it can't be read
         */
        Observable<List<String>> getVideoIds();
    }

    public interface OnEdited {
        /**
         * The video was added to Watch later or removed from it in the app
         */
        void onEdited(Video video, boolean isAdded);
    }

    interface AccountSource {
        /**
         * @return the signed-in account or null
         */
        String getAccount();
    }

    /**
     * The list of one account
     */
    private static final class Snapshot {
        final String account;
        final Set<String> videoIds;
        final long loadedTimeMs;

        Snapshot(String account, Set<String> videoIds, long loadedTimeMs) {
            this.account = account;
            this.videoIds = videoIds;
            this.loadedTimeMs = loadedTimeMs;
        }
    }

    private WatchLaterManager(Context context) {
        mContext = context;
        mLoader = YouTubeServiceManager.instance().getContentService()::getWatchLaterVideoIdsObserve;
        mAccountSource = () -> getAccountKey(MediaServiceManager.instance().getSelectedAccount());
        mIdsFile = new File(FileHelpers.getFilesDir(context), IDS_FILE);
        RxHelper.runAsync(this::restoreIds);
    }

    public static WatchLaterManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new WatchLaterManager(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * Watch later videos are looked for in the section (MediaGroup.TYPE_*)
     */
    public boolean isEnabled(int sectionId) {
        return getData().isEnabled(sectionId);
    }

    public boolean isHidden(int sectionId) {
        return getData().isHidingEnabled(sectionId);
    }

    /**
     * Not kept, like in OldVideoFilter: after WatchLaterData.resetInstanceForTesting a kept one would be stale
     */
    private WatchLaterData getData() {
        return WatchLaterData.instance(mContext);
    }

    /**
     * The video is in the signed-in account's Watch later, as far as it's known
     */
    public boolean contains(String videoId) {
        Snapshot snapshot = mSnapshot;

        return videoId != null && snapshot != null && snapshot.videoIds.contains(videoId) && snapshot.account.equals(mAccountSource.getAccount());
    }

    /**
     * For the menus: like {@link #contains}, but only while the setting marks or hides them, since the list is kept fresh only then
     */
    public boolean isInWatchLater(String videoId) {
        return getData().getMode() != WatchLaterData.MODE_SHOW && contains(videoId);
    }

    /**
     * The cards show the Watch later playlist itself, where every video is in it (e.g. its row in Playlists or in Home)
     */
    public static boolean isWatchLaterPlaylist(VideoGroup group, Video video) {
        MediaGroup mediaGroup = group != null ? group.getMediaGroup() : null;

        // A playlist is opened as the channel "VL" + its id. Its cards play in it.
        return (mediaGroup != null && WATCH_LATER_CHANNEL_ID.equals(mediaGroup.getChannelId())) || WATCH_LATER_PLAYLIST_ID.equals(video.playlistId);
    }

    /**
     * Reads the list of the signed-in account before the cards are shown (see HiddenVideoResolver).
     * A known list is used as it is and read again in the background once it's old.
     * Completes once the list is known or can't be read.
     */
    public Observable<Boolean> resolve() {
        return mRestored.take(1).concatMap(restored -> resolveNow());
    }

    private Observable<Boolean> resolveNow() {
        String account = mAccountSource.getAccount();

        // Signed out: there's no Watch later
        if (account == null) {
            return Observable.just(true);
        }

        Snapshot snapshot = mSnapshot;
        long now = System.currentTimeMillis();

        if (snapshot != null && account.equals(snapshot.account)) {
            if (now - snapshot.loadedTimeMs >= REFRESH_PERIOD_MS && !isRetryPending(account, now)) {
                RxHelper.execute(load(account));
            }

            return Observable.just(true);
        }

        // Show the cards rather than wait again for a list that just failed
        if (isRetryPending(account, now)) {
            return Observable.just(true);
        }

        return hold(account);
    }

    /**
     * Waits for the list for {@link #MAX_HOLD_MS} after its load started, the same end for every group that waits.
     * The cards are then shown, and the list applies to the next ones once it's read.
     */
    private Observable<Boolean> hold(String account) {
        Observable<Boolean> load = load(account);
        Long startTimeMs = mLoadStartTimes.get(account);
        long leftMs = startTimeMs != null ? MAX_HOLD_MS - (System.currentTimeMillis() - startTimeMs) : MAX_HOLD_MS;

        if (leftMs <= 0) {
            return Observable.just(true);
        }

        // The load goes on after the wait ends (see load)
        return load.timeout(leftMs, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread(), Observable.just(true));
    }

    private boolean isRetryPending(String account, long now) {
        return account.equals(mFailedAccount) && now - mFailedTimeMs < RETRY_PERIOD_MS;
    }

    /**
     * One load per account at a time, shared by the groups that need it. It ends even when nobody waits for it anymore (cache).
     */
    private Observable<Boolean> load(String account) {
        return mRunningLoads.computeIfAbsent(account, key -> {
            mPendingEdits.clear();
            mLoadStartTimes.put(account, System.currentTimeMillis());

            return mLoader.getVideoIds()
                    .map(videoIds -> {
                        apply(account, videoIds);
                        return true;
                    })
                    .onErrorReturn(error -> {
                        Log.e(TAG, "Can't read Watch later: %s", error.getMessage());
                        mFailedAccount = account;
                        mFailedTimeMs = System.currentTimeMillis();
                        return true;
                    })
                    .doFinally(() -> {
                        mRunningLoads.remove(account);
                        mLoadStartTimes.remove(account);
                    })
                    .cache();
        });
    }

    private void apply(String account, List<String> videoIds) {
        // Another account was picked while it loaded
        if (!account.equals(mAccountSource.getAccount())) {
            return;
        }

        Set<String> result = new HashSet<>(videoIds);

        for (Map.Entry<String, Boolean> edit : mPendingEdits.entrySet()) {
            if (edit.getValue()) {
                result.add(edit.getKey());
            } else {
                result.remove(edit.getKey());
            }
        }

        mPendingEdits.clear();
        mSnapshot = new Snapshot(account, Collections.unmodifiableSet(result), System.currentTimeMillis());
        mFailedAccount = null;
        Utils.postDelayed(mSaveIds, SAVE_DELAY_MS);
    }

    /**
     * Told about each edit of Watch later made in the app, e.g. to show it on the card at once
     */
    public void setOnEdited(OnEdited listener) {
        mOnEdited = listener;
    }

    /**
     * A video was added to a playlist or removed from it in the app, and the change was saved
     */
    public static void onPlaylistEdited(Context context, String playlistId, Video video, boolean isAdded) {
        // No context before the app is initialized (GlobalPreferences)
        if (context == null || !WATCH_LATER_PLAYLIST_ID.equals(playlistId) || video == null || video.videoId == null) {
            return;
        }

        WatchLaterManager manager = instance(context);
        manager.onWatchLaterEdited(video.videoId, isAdded);

        if (manager.mOnEdited != null) {
            manager.mOnEdited.onEdited(video, isAdded);
        }
    }

    private void onWatchLaterEdited(String videoId, boolean isAdded) {
        if (!mRunningLoads.isEmpty()) {
            mPendingEdits.put(videoId, isAdded);
        }

        Snapshot snapshot = mSnapshot;

        if (snapshot == null || !snapshot.account.equals(mAccountSource.getAccount()) || snapshot.videoIds.contains(videoId) == isAdded) {
            return;
        }

        Set<String> videoIds = new HashSet<>(snapshot.videoIds);

        if (isAdded) {
            videoIds.add(videoId);
        } else {
            videoIds.remove(videoId);
        }

        mSnapshot = new Snapshot(snapshot.account, Collections.unmodifiableSet(videoIds), snapshot.loadedTimeMs);
        Utils.postDelayed(mSaveIds, SAVE_DELAY_MS);
    }

    /**
     * The key the list is kept by: the email or, when there's none, the name
     */
    static String getAccountKey(Account account) {
        if (account == null) {
            return null;
        }

        return account.getEmail() != null ? account.getEmail() : account.getName();
    }

    /**
     * Reads with the given ones from now on and forgets the list
     */
    void resetForTesting(Loader loader, AccountSource accountSource) {
        mLoader = loader;
        mAccountSource = accountSource;
        mSnapshot = null;
        mRunningLoads.clear();
        mLoadStartTimes.clear();
        mPendingEdits.clear();
        mFailedAccount = null;
        mRestored.onNext(true);
    }

    /**
     * Makes the list look read the given time ago
     */
    void setLoadedTimeForTesting(long agoMs) {
        Snapshot snapshot = mSnapshot;

        if (snapshot != null) {
            mSnapshot = new Snapshot(snapshot.account, snapshot.videoIds, System.currentTimeMillis() - agoMs);
        }
    }

    private void restoreIds() {
        Snapshot restored = null;

        try {
            restored = readIds();
        } catch (RuntimeException e) {
            Log.e(TAG, "Can't read %s: %s", mIdsFile, e.getMessage());
        }

        Snapshot result = restored;

        // Changed on the main thread only. Always emitted: the cards wait for it.
        Utils.post(() -> {
            // A list read from the network meanwhile is newer
            if (mSnapshot == null && result != null) {
                mSnapshot = result;
            }

            mRestored.onNext(true);
        });
    }

    /**
     * The file: the account, the time the list was read, then a video id per line
     */
    private Snapshot readIds() {
        String content = FileHelpers.getFileContents(mIdsFile);
        String[] lines = content != null ? content.split("\n") : new String[0];

        if (lines.length < 2 || lines[0].isEmpty()) {
            return null;
        }

        Set<String> videoIds = new HashSet<>();

        for (int i = 2; i < lines.length; i++) {
            if (!lines[i].isEmpty()) {
                videoIds.add(lines[i]);
            }
        }

        // An unreadable time makes it old: it's read again at the first use
        return new Snapshot(lines[0], Collections.unmodifiableSet(videoIds), Helpers.parseLong(lines[1], 0));
    }

    private void saveIds() {
        Snapshot snapshot = mSnapshot;

        if (snapshot == null || snapshot.account.contains("\n")) {
            return;
        }

        StringBuilder content = new StringBuilder()
                .append(snapshot.account).append('\n')
                .append(snapshot.loadedTimeMs).append('\n');

        for (String videoId : snapshot.videoIds) {
            content.append(videoId).append('\n');
        }

        File parent = mIdsFile.getParentFile();

        if (parent != null && (parent.exists() || parent.mkdirs())) {
            FileHelpers.stringToFile(content.toString(), mIdsFile);
        }
    }
}
