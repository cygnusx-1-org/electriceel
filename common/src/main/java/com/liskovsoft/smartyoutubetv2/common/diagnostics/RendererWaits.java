package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.C;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * When the player's video or audio renderer couldn't play from where it was (see RendererReadiness in the player): the player is ready
 * only once both can, and nothing told which one it waited for. A start whose first frame was shown long before it played had no row
 * for what held it (6EZTNXBF: the frame at 1226 ms, playing at 2882 ms, with 30.8 s buffered). Told on the playback thread, read on
 * the main one.
 */
public final class RendererWaits {
    /**
     * The last ones of a track: a start only needs the ones since its first frame
     */
    static final int MAX_WAITS = 8;
    /**
     * Shorter, a wait says nothing: a renderer isn't ready for a moment each time it's given nothing to show yet (as a response's,
     * see Connections#MIN_RESPONSE_MS)
     */
    static final long MIN_WAIT_MS = 200;
    private static final Track sVideo = new Track(VideoStarts.STAGE_VIDEO_NOT_READY);
    private static final Track sAudio = new Track(VideoStarts.STAGE_AUDIO_NOT_READY);
    @VisibleForTesting
    static VideoStarts.Clock sClock = SystemClock::elapsedRealtime;

    private static final class Wait {
        final long startMs;
        /**
         * -1 while it isn't ready
         */
        long endMs = -1;

        Wait(long startMs) {
            this.startMs = startMs;
        }
    }

    private static final class Track {
        final String stage;
        /**
         * Under the class's lock
         */
        final ArrayDeque<Wait> waits = new ArrayDeque<>();
        @Nullable
        Wait open;
        /**
         * As last told, once it was since the player was prepared. Read on the playback thread without the lock, each time its loop
         * looks: only a change takes it.
         */
        volatile boolean isKnown;
        volatile boolean isReady;

        Track(String stage) {
            this.stage = stage;
        }
    }

    private RendererWaits() {
    }

    /**
     * Whether a renderer can play from where it is, on the playback thread, each time its loop looks (see RendererReadiness.Listener)
     *
     * @param trackType the renderer's, e.g. C#TRACK_TYPE_AUDIO: the others (e.g. the subtitles') hold nothing up
     */
    public static void onReadiness(int trackType, boolean isReady) {
        Track track = trackType == C.TRACK_TYPE_VIDEO ? sVideo : trackType == C.TRACK_TYPE_AUDIO ? sAudio : null;

        if (track == null || track.isKnown && track.isReady == isReady) {
            return;
        }

        onChange(track, isReady);
    }

    private static synchronized void onChange(Track track, boolean isReady) {
        long nowMs = sClock.now();
        track.isReady = isReady;
        track.isKnown = true;

        if (isReady) {
            end(track, nowMs);
        } else if (track.open == null) {
            track.open = new Wait(nowMs);
            track.waits.addLast(track.open);

            if (track.waits.size() > MAX_WAITS) {
                track.waits.removeFirst();
            }
        }
    }

    /**
     * The player was prepared with a source (e.g. another video, or the same one again by a reload): its renderers start over, and
     * the waits before aren't this source's
     */
    public static synchronized void onPrepare() {
        for (Track track : new Track[]{sVideo, sAudio}) {
            track.waits.clear();
            track.open = null;
            track.isKnown = false;
        }
    }

    /**
     * Of a stall: whether each renderer could play from where it was, as last told (null when not told since the player was prepared),
     * and how long one that couldn't hadn't. GWS5HG2H #1: a live stream stalled 21 s with 7.4 s of both tracks buffered, and nothing
     * told which renderer held it.
     */
    public static synchronized void addDetails(PlayerFailure failure) {
        long nowMs = sClock.now();

        for (Track track : new Track[]{sVideo, sAudio}) {
            String name = track == sVideo ? "video" : "audio";
            failure.detail(name + "RendererReady", track.isKnown ? track.isReady : null)
                    .detail(name + "NotReadyMs", track.open != null ? nowMs - track.open.startMs : null);
        }
    }

    /**
     * The waits between the times, cut to them, as stages of a start's buffering (in the clock's time): "audio not ready", "video not
     * ready". One still going at the end says so.
     */
    static synchronized List<VideoStart.Stage> take(long fromMs, long toMs) {
        List<VideoStart.Stage> result = new ArrayList<>();

        for (Track track : new Track[]{sVideo, sAudio}) {
            for (Wait wait : track.waits) {
                boolean isGoing = wait.endMs < 0;
                long startMs = Math.max(wait.startMs, fromMs);
                long endMs = isGoing ? toMs : Math.min(wait.endMs, toMs);

                if (endMs - startMs >= MIN_WAIT_MS) {
                    result.add(new VideoStart.Stage(track.stage, isGoing ? VideoStarts.DETAIL_GOING : null, 1, startMs, endMs));
                }
            }
        }

        return result;
    }

    private static void end(Track track, long nowMs) {
        if (track.open != null) {
            track.open.endMs = nowMs;
            track.open = null;
        }
    }

    @VisibleForTesting
    static synchronized void resetForTesting(@Nullable VideoStarts.Clock clock) {
        onPrepare();
        sClock = clock != null ? clock : SystemClock::elapsedRealtime;
    }
}
