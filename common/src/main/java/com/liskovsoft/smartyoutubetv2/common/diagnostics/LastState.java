package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What the app was doing, kept for an app exit found on the next start to tell (see AppExits): a kill leaves no record of its own.
 * Saved when a foreground service (e.g. the remote control's) starts or stops, when the player starts or stops playing, and at most
 * every 30 s while a video is loaded. No video id: the exit's record is sent without one. The process before's is kept too: an exit
 * is of a process before this one. Used on the main thread.
 */
public final class LastState {
    public static final String SERVICE_REMOTE_CONTROL = "remote_control";
    private static final String PREFS = "diagnostics_last_state";
    private static final String KEY_CURRENT = "current";
    private static final String KEY_PREVIOUS = "previous";
    /**
     * While a video is loaded and nothing changes: what it did minutes before the exit is what it did at it
     */
    @VisibleForTesting
    static final long MIN_INTERVAL_MS = 30_000;
    private static final Set<String> sServices = new LinkedHashSet<>();
    private static boolean sHasPlayer;
    private static boolean sIsPlaying;
    private static long sSavedMs = -1;
    @VisibleForTesting
    static VideoStarts.Clock sClock = SystemClock::elapsedRealtime;
    @VisibleForTesting
    static VideoStarts.Clock sWallClock = System::currentTimeMillis;
    @VisibleForTesting
    static PidSource sPid = Process::myPid;

    interface PidSource {
        int get();
    }

    private LastState() {
    }

    public static void onServiceStarted(Context context, String service) {
        sServices.add(service);
        save(context);
    }

    public static void onServiceStopped(Context context, String service) {
        sServices.remove(service);
        save(context);
    }

    /**
     * At each check of the playback (see ExoPlayerController#checkHealth), and when the player is released
     *
     * @param hasPlayer a video is loaded
     */
    public static void onPlayer(Context context, boolean hasPlayer, boolean isPlaying) {
        boolean isChanged = hasPlayer != sHasPlayer || isPlaying != sIsPlaying;
        sHasPlayer = hasPlayer;
        sIsPlaying = isPlaying;

        if (isChanged || sSavedMs < 0 || sClock.now() - sSavedMs >= MIN_INTERVAL_MS) {
            save(context);
        }
    }

    private static void save(Context context) {
        try {
            JSONObject state = new JSONObject();
            state.put("ts", sWallClock.now());
            state.put("pid", sPid.get());
            state.put("services", new JSONArray(sServices));
            state.put("player", sHasPlayer);
            state.put("playing", sIsPlaying);
            Runtime runtime = Runtime.getRuntime();
            state.put("heapUsedMb", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

            if (activityManager != null) {
                ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(memory);
                state.put("availMemMb", memory.availMem / (1024 * 1024));
            }

            // E.g. 125 a foreground service (the remote control), 400 cached
            RunningAppProcessInfo process = new RunningAppProcessInfo();
            ActivityManager.getMyMemoryState(process);
            state.put("importance", process.importance);

            SharedPreferences prefs = getPrefs(context);
            SharedPreferences.Editor editor = prefs.edit();
            JSONObject current = parse(prefs.getString(KEY_CURRENT, null));

            if (current != null && current.optInt("pid") != sPid.get()) {
                editor.putString(KEY_PREVIOUS, current.toString());
            }

            editor.putString(KEY_CURRENT, state.toString()).apply();
            sSavedMs = sClock.now();
        } catch (JSONException | RuntimeException e) {
            android.util.Log.e("LastState", "Can't save: " + e);
        }
    }

    /**
     * Of the process that exited, saved before it did: however long before, what changes is saved, so it's still what it did (e.g. idle
     * for hours with the remote control's service running). Null with none.
     *
     * @param exitTs wall clock
     */
    @Nullable
    static JSONObject find(Context context, int pid, long exitTs) {
        SharedPreferences prefs = getPrefs(context);

        for (String key : new String[]{KEY_CURRENT, KEY_PREVIOUS}) {
            JSONObject state = parse(prefs.getString(key, null));

            if (state == null || state.optInt("pid", -1) != pid) {
                continue;
            }

            long agoMs = exitTs - state.optLong("ts");

            if (agoMs < 0) {
                continue;
            }

            state.remove("ts");
            state.remove("pid");

            try {
                state.put("agoMs", agoMs);
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }

            return state;
        }

        return null;
    }

    @Nullable
    private static JSONObject parse(@Nullable String json) {
        if (json == null) {
            return null;
        }

        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            return null;
        }
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @VisibleForTesting
    static void resetForTesting(Context context) {
        sServices.clear();
        sHasPlayer = false;
        sIsPlaying = false;
        sSavedMs = -1;
        sClock = SystemClock::elapsedRealtime;
        sWallClock = System::currentTimeMillis;
        sPid = Process::myPid;
        getPrefs(context).edit().clear().commit();
    }
}
