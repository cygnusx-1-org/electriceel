package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.VisibleForTesting;

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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The starts of videos (see VideoStarts), kept over app restarts and updates for Submit data: the last {@link #MAX_STARTS}, as JSON
 * Lines next to the player failures. Read and written on a thread of its own, in the order recorded.
 */
public final class VideoStartLog {
    private static final String TAG = VideoStarts.LOG_TAG;
    static final String FILE = "video_starts.jsonl";
    /**
     * Every start is kept: enough for the first of each run (the slow one) to stay among the ones watched since
     */
    static final int MAX_STARTS = 100;
    /**
     * Past it, the oldest are dropped down to MAX_STARTS: the file isn't rewritten for each one
     */
    static final int MAX_LINES = MAX_STARTS + 20;
    @SuppressLint("StaticFieldLeak")
    private static VideoStartLog sInstance;
    private final File mFile;
    private final PlayerFailureLog.AccountSource mAccountSource;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "VideoStartLog");
        thread.setDaemon(true);
        return thread;
    });
    /**
     * In the file, -1 till it's read. Used on mExecutor only.
     */
    private int mLines = -1;

    @VisibleForTesting
    VideoStartLog(File dir) {
        this(dir, () -> 1);
    }

    @VisibleForTesting
    VideoStartLog(File dir, PlayerFailureLog.AccountSource accountSource) {
        mFile = new File(dir, FILE);
        mAccountSource = accountSource;
    }

    public static synchronized VideoStartLog instance(Context context) {
        if (sInstance == null) {
            Context appContext = context.getApplicationContext();
            sInstance = new VideoStartLog(new File(appContext.getFilesDir(), PlayerFailureLog.DIR),
                    () -> DebuggingData.instance(appContext).getAccountNumber());
        }

        return sInstance;
    }

    /**
     * The one {@link #instance} returns: in the directory, of the account 1, null for the app's own again
     */
    @VisibleForTesting
    public static VideoStartLog useForTesting(File dir) {
        sInstance = dir != null ? new VideoStartLog(dir) : null;

        return sInstance;
    }

    /**
     * Stamped with the account in use (see DebuggingData#getAccountNumber), then written in the background
     */
    public void record(VideoStart start) {
        JSONObject json = start.toJson();

        try {
            json.put("account", mAccountSource.getAccountNumber());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }

        String line = json.toString();

        mExecutor.execute(() -> append(line));
    }

    /**
     * To the start of that time, recorded before. Written in the background.
     */
    public void addFrames(long ts, JSONObject frames) {
        mExecutor.execute(() -> writeFrames(ts, frames));
    }

    /**
     * Oldest first. Blocks till the ones recorded before are written: not on the main thread.
     */
    public List<JSONObject> readAll() {
        try {
            return mExecutor.submit(this::read).get();
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void append(String line) {
        boolean isTailCut = false;

        if (mLines < 0) {
            mLines = readLines().size();
            isTailCut = isTailCut();
        }

        File dir = mFile.getParentFile();

        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            return;
        }

        try (OutputStream out = new FileOutputStream(mFile, true)) {
            // A line cut by a kill while writing is ended, not joined to this one
            out.write(((isTailCut ? "\n" : "") + line + "\n").getBytes(StandardCharsets.UTF_8));
            mLines++;
        } catch (IOException e) {
            android.util.Log.e(TAG, "Can't write " + mFile + ": " + e.getMessage());
            return;
        }

        if (mLines > MAX_LINES) {
            List<String> lines = readLines();
            rewrite(lines.subList(Math.max(0, lines.size() - MAX_STARTS), lines.size()));
        }
    }

    private void writeFrames(long ts, JSONObject frames) {
        List<String> lines = readLines();

        for (int i = lines.size() - 1; i >= 0; i--) {
            try {
                JSONObject start = new JSONObject(lines.get(i));

                if (start.optLong("ts") == ts) {
                    start.put("frames", frames);
                    lines.set(i, start.toString());
                    rewrite(lines);
                    return;
                }
            } catch (JSONException e) {
                // A line cut by a kill while writing
            }
        }
    }

    private boolean isTailCut() {
        if (!mFile.exists() || mFile.length() == 0) {
            return false;
        }

        try (RandomAccessFile file = new RandomAccessFile(mFile, "r")) {
            file.seek(file.length() - 1);
            return file.read() != '\n';
        } catch (IOException e) {
            return false;
        }
    }

    private List<JSONObject> read() {
        List<String> lines = readLines();
        List<JSONObject> result = new ArrayList<>();

        for (String line : lines.subList(Math.max(0, lines.size() - MAX_STARTS), lines.size())) {
            try {
                result.add(new JSONObject(line));
            } catch (JSONException e) {
                // A line cut by a kill while writing
            }
        }

        return result;
    }

    private List<String> readLines() {
        List<String> result = new ArrayList<>();

        if (!mFile.exists()) {
            return result;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(mFile), StandardCharsets.UTF_8))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    result.add(line);
                }
            }
        } catch (IOException e) {
            android.util.Log.e(TAG, "Can't read " + mFile + ": " + e.getMessage());
        }

        return result;
    }

    /**
     * Whole or not at all: a kill while writing leaves the file as it was
     */
    private void rewrite(List<String> lines) {
        File temp = new File(mFile.getParentFile(), FILE + ".tmp");

        try (OutputStream out = new FileOutputStream(temp)) {
            for (String line : lines) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            android.util.Log.e(TAG, "Can't write " + temp + ": " + e.getMessage());
            return;
        }

        if (temp.renameTo(mFile)) {
            mLines = lines.size();
        } else {
            android.util.Log.e(TAG, "Can't replace " + mFile);
        }
    }
}
