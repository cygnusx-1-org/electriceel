package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.liskovsoft.sharedutils.helpers.Helpers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;

/**
 * What the player of the video was told and did, the last ones, for a failure: e.g. a stop left it at the end, a play came from
 * the media session (a Bluetooth speaker or a remote's media key) and it stalled there
 */
public final class PlayerCommands {
    /**
     * The app's own: its controllers, its UI and the remote's keys it handles
     */
    public static final String SOURCE_APP = "app";
    /**
     * The media session's: a remote's media keys, a Bluetooth device (AVRCP), the system
     */
    public static final String SOURCE_MEDIA_SESSION = "media_session";
    /**
     * The player itself: its state changed
     */
    static final String SOURCE_PLAYER = "player";
    static final int MAX_COMMANDS = 20;
    private static final ArrayDeque<Command> sCommands = new ArrayDeque<>();
    @Nullable
    private static String sVideoId;
    private static int sLastState = -1;

    private static final class Command {
        final long time = SystemClock.elapsedRealtime();
        final String event;
        final String source;
        final long positionMs;

        Command(String event, String source, long positionMs) {
            this.event = event;
            this.source = source;
            this.positionMs = positionMs;
        }
    }

    private PlayerCommands() {
    }

    /**
     * Another video: its own commands. The same one again (a reload) goes on with them
     */
    public static synchronized void onNewVideo(@Nullable String videoId) {
        if (Helpers.equals(videoId, sVideoId)) {
            return;
        }

        sVideoId = videoId;
        sCommands.clear();
        sLastState = -1;
    }

    /**
     * Told to play or pause
     */
    public static void onPlayWhenReady(boolean playWhenReady, String source, long positionMs) {
        add(playWhenReady ? "play" : "pause", source, positionMs);
    }

    /**
     * Told to go to a position (e.g. a stopped video left at its end)
     */
    public static void onSeek(String source, long positionMs) {
        add("seek", source, positionMs);
    }

    /**
     * Its state, when it changes (see Player#STATE_IDLE)
     */
    public static synchronized void onState(int playbackState, long positionMs) {
        if (playbackState == sLastState) {
            return;
        }

        sLastState = playbackState;
        add(getStateName(playbackState), SOURCE_PLAYER, positionMs);
    }

    /**
     * Oldest first, each with how long before now it was. Null with none
     */
    @Nullable
    static synchronized JSONArray snapshot() {
        if (sCommands.isEmpty()) {
            return null;
        }

        long now = SystemClock.elapsedRealtime();
        JSONArray result = new JSONArray();

        try {
            for (Command command : sCommands) {
                JSONObject item = new JSONObject();
                item.put("msAgo", now - command.time);
                item.put("event", command.event);
                item.put("source", command.source);
                item.put("positionMs", command.positionMs);
                result.put(item);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }

        return result;
    }

    static synchronized void resetForTesting() {
        sVideoId = null;
        sCommands.clear();
        sLastState = -1;
    }

    private static synchronized void add(String event, String source, long positionMs) {
        if (sCommands.size() >= MAX_COMMANDS) {
            sCommands.removeFirst();
        }

        sCommands.addLast(new Command(event, source, positionMs));
    }

    private static String getStateName(int playbackState) {
        switch (playbackState) {
            case Player.STATE_BUFFERING:
                return "buffering";
            case Player.STATE_READY:
                return "ready";
            case Player.STATE_ENDED:
                return "ended";
            default:
                return "idle";
        }
    }
}
