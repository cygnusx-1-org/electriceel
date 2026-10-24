package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build.VERSION;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.liskovsoft.smartyoutubetv2.common.prefs.DebuggingData;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The app dying, to the player failure log: a crash when it happens, and on Android 11+ the ANRs, native crashes and kills for
 * memory Android kept, found on the next start
 */
public final class AppExits {
    public static final String KIND_CRASH = "crash";
    public static final String KIND_APP_EXIT = "app_exit";
    private static final int MAX_EXITS = 16;
    /**
     * Of the main thread of an ANR's traces
     */
    private static final int MAX_TRACE_LINES = 30;
    private static final int MAX_TRACE_BYTES = 256 * 1024;

    private AppExits() {
    }

    /**
     * From the app's uncaught exception handler: written before it returns, with what's still in memory. Never throws.
     */
    public static void recordCrash(Context context, Thread thread, Throwable error) {
        try {
            FormatAttempts.flush(context, true);
            PlayerFailure.create(context, KIND_CRASH)
                    .videoId(FormatAttempts.getVideoId())
                    .error(error)
                    .detail("thread", thread.getName())
                    .recordNow();
        } catch (Throwable e) {
            // The crash goes on as it would
        }
    }

    /**
     * The exits since the last start (Android 11+), each once. Not on the main thread: an ANR's traces are read.
     */
    public static void recordNew(Context context) {
        if (VERSION.SDK_INT < 30) {
            return;
        }

        try {
            recordNewExits(context);
        } catch (RuntimeException e) {
            android.util.Log.e("PlayerFailure", "Can't read the app exits: " + e);
        }
    }

    @RequiresApi(30)
    private static void recordNewExits(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        if (activityManager == null) {
            return;
        }

        DebuggingData debuggingData = DebuggingData.instance(context);
        long lastSeen = debuggingData.getLastAppExitTs();
        long newest = lastSeen;
        List<ApplicationExitInfo> exits = activityManager.getHistoricalProcessExitReasons(context.getPackageName(), 0, MAX_EXITS);

        // Newest first: recorded oldest first
        for (int i = exits.size() - 1; i >= 0; i--) {
            ApplicationExitInfo exit = exits.get(i);

            if (exit.getTimestamp() <= lastSeen) {
                continue;
            }

            newest = Math.max(newest, exit.getTimestamp());
            String reason = getReasonName(exit.getReason());

            // A Java crash was recorded when it happened (see recordCrash); the user closing it or the app updating isn't a failure
            if (reason == null) {
                continue;
            }

            PlayerFailure failure = PlayerFailure.create(context, KIND_APP_EXIT)
                    .at(exit.getTimestamp())
                    .withoutState()
                    .detail("reason", reason)
                    .detail("description", Scrubber.scrub(exit.getDescription()))
                    .detail("process", exit.getProcessName())
                    .detail("foreground", exit.getImportance() <= RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE)
                    .detail("pssMb", exit.getPss() / 1024)
                    .detail("rssMb", exit.getRss() / 1024);

            if (exit.getReason() == ApplicationExitInfo.REASON_ANR) {
                failure.detail("mainThread", readMainThread(exit));
            }

            failure.record();
        }

        if (newest > lastSeen) {
            debuggingData.setLastAppExitTs(newest);
        }
    }

    @Nullable
    private static String getReasonName(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_ANR:
                return "anr";
            case ApplicationExitInfo.REASON_CRASH_NATIVE:
                return "crash_native";
            case ApplicationExitInfo.REASON_LOW_MEMORY:
                return "low_memory";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                return "excessive_resource_usage";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
                return "initialization_failure";
            case ApplicationExitInfo.REASON_SIGNALED:
                return "signaled";
            default:
                return null;
        }
    }

    /**
     * The main thread's part of the traces Android took, cut short
     */
    @RequiresApi(30)
    @Nullable
    private static JSONArray readMainThread(ApplicationExitInfo exit) {
        try (InputStream traces = exit.getTraceInputStream()) {
            if (traces == null) {
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(traces, StandardCharsets.UTF_8));
            JSONArray result = new JSONArray();
            boolean isMain = false;
            int bytes = 0;
            String line;

            while ((line = reader.readLine()) != null && bytes < MAX_TRACE_BYTES) {
                bytes += line.length() + 1;

                if (line.startsWith("\"main\"")) {
                    isMain = true;
                } else if (isMain && line.trim().isEmpty()) {
                    break;
                }

                if (isMain) {
                    result.put(Scrubber.scrub(line));

                    if (result.length() >= MAX_TRACE_LINES) {
                        break;
                    }
                }
            }

            return result.length() > 0 ? result : null;
        } catch (IOException e) {
            return null;
        }
    }
}
