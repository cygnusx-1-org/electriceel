package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.DebuggingData;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The player failures, kept over app restarts and updates: JSON Lines in the app's files, about 1 MB at most (the current file
 * and the one before it). Read and written on a thread of its own, in the order recorded.<br/>
 * The same failure again within a minute of the one written isn't written: the next line of it carries how many times
 * ({@code "repeats"}), so a video that fails each second takes a line a minute.
 */
public final class PlayerFailureLog {
    private static final String TAG = "PlayerFailure";
    static final String DIR = "player_failures";
    static final String CURRENT = "failures.jsonl";
    static final String PREVIOUS = "failures.1.jsonl";
    /**
     * How the formats played, the failures or not (see #countPlayback)
     */
    static final String STATS = "stats.json";
    private static final int MAX_STATS_FORMATS = 100;
    static final long MAX_FILE_BYTES = 512 * 1024;
    static final long REPEAT_WINDOW_MS = 60_000;
    /**
     * Not a failure: the formats tried for a video after its last failure (see FormatAttempts), merged into its failures when read
     */
    static final String KIND_FORMATS = "formats";
    @SuppressLint("StaticFieldLeak")
    private static PlayerFailureLog sInstance;
    private final File mCurrent;
    private final File mPrevious;
    private final File mStatsFile;
    /**
     * Read from mStatsFile when first used. Used on mExecutor only.
     */
    private JSONObject mStats;
    private final String mAppVersion;
    private final int mAppVersionCode;
    private final AccountSource mAccountSource;
    private final Clock mClock;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "PlayerFailureLog");
        thread.setDaemon(true);
        return thread;
    });
    /**
     * By signature (see PlayerFailure#getSignature). Used on mExecutor only.
     */
    private final Map<String, Repeat> mRepeats = new HashMap<>();
    /**
     * The file was checked to end with a whole line (see append). Used on mExecutor only.
     */
    private boolean mIsTailChecked;

    interface Clock {
        long now();
    }

    interface AccountSource {
        /**
         * Of the account in use, see DebuggingData#getAccountNumber
         */
        int getAccountNumber();
    }

    /**
     * Of a failure written in the last minute
     */
    private static final class Repeat {
        long writtenTs;
        int count;
        JSONObject last;
        StateSource lastState;

        Repeat(long writtenTs) {
            this.writtenTs = writtenTs;
        }
    }

    @VisibleForTesting
    PlayerFailureLog(File dir, String appVersion, int appVersionCode, AccountSource accountSource, Clock clock) {
        mCurrent = new File(dir, CURRENT);
        mPrevious = new File(dir, PREVIOUS);
        mStatsFile = new File(dir, STATS);
        mAppVersion = appVersion;
        mAppVersionCode = appVersionCode;
        mAccountSource = accountSource;
        mClock = clock;
    }

    public static synchronized PlayerFailureLog instance(Context context) {
        if (sInstance == null) {
            Context appContext = context.getApplicationContext();
            String versionName = AppInfoHelpers.getAppVersionName(appContext);
            sInstance = new PlayerFailureLog(new File(appContext.getFilesDir(), DIR), versionName != null ? versionName : "unknown",
                    AppInfoHelpers.getAppVersionCode(appContext), () -> DebuggingData.instance(appContext).getAccountNumber(),
                    System::currentTimeMillis);
        }

        return sInstance;
    }

    /**
     * The one {@link #instance} returns: in the directory, of the account 1, null for the app's own again
     */
    @VisibleForTesting
    public static PlayerFailureLog useForTesting(File dir) {
        sInstance = dir != null ? new PlayerFailureLog(dir, "test", 1, () -> 1, System::currentTimeMillis) : null;

        return sInstance;
    }

    /**
     * Where the device was at (see DeviceCapabilities#buildState): built in the background, for the lines written only
     */
    interface StateSource {
        JSONObject build();
    }

    /**
     * Stamped with the time, the app version and the account in use, then written in the background
     */
    public void record(PlayerFailure failure) {
        long ts = failure.getTs() > 0 ? failure.getTs() : mClock.now();
        JSONObject json = failure.toJson(ts, mAppVersion, mAppVersionCode, mAccountSource.getAccountNumber());
        String signature = failure.getSignature();
        StateSource state = failure.getStateSource();

        mExecutor.execute(() -> write(signature, ts, json, state));
    }

    /**
     * Written before it returns, with the repeats not written yet: the app is about to die (a crash)
     */
    public void recordNow(PlayerFailure failure) {
        long ts = failure.getTs() > 0 ? failure.getTs() : mClock.now();
        JSONObject json = failure.toJson(ts, mAppVersion, mAppVersionCode, mAccountSource.getAccountNumber());

        synchronized (this) {
            flushRepeats(ts, true);
            addState(json, failure.getStateSource());
            append(json);
        }
    }

    /**
     * The repeats not written yet are written: the app goes to the background, where it may be killed
     */
    public void flush() {
        mExecutor.execute(() -> flushRepeats(mClock.now(), true));
    }

    /**
     * The formats tried for the video since the session started (see FormatAttempts#end)
     *
     * @param isNow written before it returns: the app is about to die
     */
    void recordFormats(String videoId, long sessionStart, JSONObject formats, boolean isNow) {
        JSONObject json = new JSONObject();

        try {
            json.put("ts", mClock.now());
            json.put("kind", KIND_FORMATS);
            json.put("videoId", videoId);
            json.put("sessionStart", sessionStart);
            json.put("formats", formats);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }

        if (isNow) {
            append(json);
        } else {
            mExecutor.execute(() -> append(json));
        }
    }

    long now() {
        return mClock.now();
    }

    /**
     * A format tried played, worked or failed (see FormatAttempts): counted by its video and audio
     *
     * @param video e.g. "vp9 1080p30", null when it had none
     * @param audio e.g. "opus", null when it had none
     */
    void countPlayback(@Nullable String video, @Nullable String audio, @Nullable String videoStatus, @Nullable String audioStatus,
                       long playedMs) {
        mExecutor.execute(() -> {
            try {
                JSONObject stats = getStats();
                JSONObject formats = stats.getJSONObject("formats");
                String key = video + " | " + audio;
                JSONObject entry = formats.optJSONObject(key);

                if (entry == null) {
                    if (formats.length() >= MAX_STATS_FORMATS) {
                        return; // Kept for the ones counted first: they're the device's usual ones
                    }

                    entry = new JSONObject();
                    entry.put("video", video != null ? video : JSONObject.NULL);
                    entry.put("audio", audio != null ? audio : JSONObject.NULL);
                    formats.put(key, entry);
                }

                increment(entry, "plays", 1);
                increment(entry, "playedMs", playedMs);
                increment(entry, "videoWorked", FormatAttempts.STATUS_WORKED.equals(videoStatus) ? 1 : 0);
                increment(entry, "videoFailed", FormatAttempts.STATUS_FAILED.equals(videoStatus) ? 1 : 0);
                increment(entry, "audioWorked", FormatAttempts.STATUS_WORKED.equals(audioStatus) ? 1 : 0);
                increment(entry, "audioFailed", FormatAttempts.STATUS_FAILED.equals(audioStatus) ? 1 : 0);

                writeStats(stats);
            } catch (JSONException e) {
                android.util.Log.e(TAG, "Can't count the playback: " + e.getMessage());
            }
        });
    }

    /**
     * {"since": ms, "formats": [{"video", "audio", "plays", "playedMs", "videoWorked", …}, …]}, most played first. Blocks: not on
     * the main thread.
     */
    public JSONObject readStats() {
        try {
            return mExecutor.submit(() -> {
                JSONObject stats = getStats();
                List<JSONObject> formats = new ArrayList<>();
                JSONObject byKey = stats.getJSONObject("formats");

                for (java.util.Iterator<String> keys = byKey.keys(); keys.hasNext(); ) {
                    formats.add(new JSONObject(byKey.getJSONObject(keys.next()).toString()));
                }

                Collections.sort(formats, (a, b) -> Long.compare(b.optLong("plays"), a.optLong("plays")));

                JSONObject result = new JSONObject();
                result.put("since", stats.optLong("since"));
                result.put("formats", new org.json.JSONArray(formats));
                return result;
            }).get();
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private JSONObject getStats() throws JSONException {
        if (mStats != null) {
            return mStats;
        }

        if (mStatsFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(mStatsFile), StandardCharsets.UTF_8))) {
                StringBuilder text = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    text.append(line);
                }

                mStats = new JSONObject(text.toString());
            } catch (IOException | JSONException e) {
                // Unreadable: counted again from now
                mStats = null;
            }
        }

        if (mStats == null || !mStats.has("formats")) {
            mStats = new JSONObject();
            mStats.put("since", mClock.now());
            mStats.put("formats", new JSONObject());
        }

        return mStats;
    }

    private static void increment(JSONObject entry, String name, long by) throws JSONException {
        entry.put(name, entry.optLong(name) + by);
    }

    /**
     * Whole or not at all: a kill while writing leaves the last one
     */
    private void writeStats(JSONObject stats) {
        File dir = mStatsFile.getParentFile();

        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            return;
        }

        File temp = new File(dir, STATS + ".tmp");

        try (OutputStream out = new FileOutputStream(temp)) {
            out.write(stats.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            android.util.Log.e(TAG, "Can't write " + temp + ": " + e.getMessage());
            return;
        }

        if (!temp.renameTo(mStatsFile)) {
            android.util.Log.e(TAG, "Can't replace " + mStatsFile);
        }
    }

    /**
     * Oldest first. Each failure's formats end with the last one tried for its video (see FormatAttempts). Blocks till the ones
     * recorded before are written: not on the main thread.
     */
    public List<JSONObject> readAll() {
        try {
            return mExecutor.submit(() -> {
                flushRepeats(mClock.now(), true);
                return read();
            }).get();
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private synchronized void write(String signature, long ts, JSONObject json, @Nullable StateSource state) {
        flushRepeats(ts, false);

        Repeat repeat = mRepeats.get(signature);

        if (repeat != null) {
            repeat.count++;
            repeat.last = json;
            repeat.lastState = state;
            return;
        }

        addState(json, state);
        append(json);
        mRepeats.put(signature, new Repeat(ts));
    }

    private static void addState(JSONObject json, @Nullable StateSource state) {
        if (state == null) {
            return;
        }

        try {
            json.put("state", state.build());
        } catch (JSONException | RuntimeException e) {
            // Written without it
        }
    }

    /**
     * The repeats of the failures whose minute is over (all of them when forced) are written: the last one, with the count of
     * the ones before it
     */
    private synchronized void flushRepeats(long now, boolean force) {
        Iterator<Repeat> iterator = mRepeats.values().iterator();

        while (iterator.hasNext()) {
            Repeat repeat = iterator.next();
            boolean isOver = now - repeat.writtenTs >= REPEAT_WINDOW_MS;

            if (!isOver && !force) {
                continue;
            }

            if (repeat.count > 0) {
                try {
                    repeat.last.put("repeats", repeat.count - 1);
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }

                addState(repeat.last, repeat.lastState);
                append(repeat.last);
            }

            if (isOver) {
                iterator.remove();
            } else if (repeat.count > 0) {
                // Its minute goes on from the line just written
                repeat.writtenTs = repeat.last.optLong("ts", now);
                repeat.count = 0;
                repeat.last = null;
                repeat.lastState = null;
            }
        }
    }

    private synchronized void append(JSONObject json) {
        String line = json.toString();
        android.util.Log.e(TAG, line); // for adb users

        File dir = mCurrent.getParentFile();

        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            android.util.Log.e(TAG, "Can't create " + dir);
            return;
        }

        if (mCurrent.length() >= MAX_FILE_BYTES) {
            // The oldest half goes
            if ((mPrevious.exists() && !mPrevious.delete()) || !mCurrent.renameTo(mPrevious)) {
                android.util.Log.e(TAG, "Can't rotate " + mCurrent);
            }
        }

        // A line cut short by a kill would take this one with it
        if (!mIsTailChecked) {
            mIsTailChecked = true;

            if (!endsWithNewLine(mCurrent)) {
                line = "\n" + line;
            }
        }

        // Written right away: kept even if the app is killed next
        try (OutputStream out = new FileOutputStream(mCurrent, true)) {
            out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            android.util.Log.e(TAG, "Can't write " + mCurrent + ": " + e.getMessage());
        }
    }

    private static boolean endsWithNewLine(File file) {
        if (file.length() == 0) {
            return true;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(file.length() - 1);
            return raf.read() == '\n';
        } catch (IOException e) {
            return true;
        }
    }

    private synchronized List<JSONObject> read() {
        List<JSONObject> result = new ArrayList<>();

        readFile(mPrevious, result);
        readFile(mCurrent, result);

        // A line of repeats is written when their minute is over: by then, others may follow the one it's of
        Collections.sort(result, (a, b) -> Long.compare(a.optLong("ts"), b.optLong("ts")));

        return mergeFormats(result);
    }

    /**
     * The formats a video tried go to its failures of that session (the last ones written win), and their lines are left out
     */
    private static List<JSONObject> mergeFormats(List<JSONObject> events) {
        List<JSONObject> result = new ArrayList<>();

        for (JSONObject event : events) {
            if (!KIND_FORMATS.equals(event.optString("kind"))) {
                result.add(event);
                continue;
            }

            String videoId = event.optString("videoId", null);
            JSONObject formats = event.optJSONObject("formats");
            long sessionStart = event.optLong("sessionStart");

            if (videoId == null || formats == null) {
                continue;
            }

            // Its session's failures before it (an earlier session's: the app was killed before its formats were written)
            for (JSONObject failure : result) {
                if (!videoId.equals(failure.optString("videoId", null)) || failure.optLong("ts") < sessionStart) {
                    continue;
                }

                try {
                    JSONObject target = failure.optJSONObject("formats");

                    if (target == null) {
                        target = new JSONObject();
                        failure.put("formats", target);
                    }

                    // What was asked for is the same, whether it worked is known better now
                    target.put("requested", formats.opt("requested"));
                    target.put("attempted", formats.opt("attempted"));
                    target.put("final", formats.opt("final"));
                    target.put("offered", formats.opt("offered"));
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        return result;
    }

    private static void readFile(File file, List<JSONObject> result) {
        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }

                try {
                    result.add(new JSONObject(line));
                } catch (JSONException e) {
                    // Cut short: the app was killed while writing it
                }
            }
        } catch (IOException e) {
            android.util.Log.e(TAG, "Can't read " + file + ": " + e.getMessage());
        }
    }
}
