package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.os.SystemClock;

import androidx.annotation.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * When the player held its loading back (see LoadControl#shouldContinueLoading): e.g. its buffer was full, so a segment of the video
 * that started was asked for late (see VideoStarts). Told on the player's thread, read on the main one.
 */
public final class LoadHolds {
    /**
     * The last ones: a start only needs the ones since it was prepared
     */
    static final int MAX_HOLDS = 8;
    /**
     * Past it, what's buffered isn't a time: the player was told the period ended (see C#TIME_END_OF_SOURCE)
     */
    private static final long MAX_BUFFERED_MS = 24 * 60 * 60 * 1_000L;
    private static final ArrayDeque<Hold> sHolds = new ArrayDeque<>();
    @VisibleForTesting
    static VideoStarts.Clock sClock = SystemClock::elapsedRealtime;
    /**
     * The one going on, null when the player loads
     */
    private static Hold sOpen;

    private static final class Hold {
        final long startMs;
        final long bufferedMs;
        final long allocatedBytes;
        long endMs = -1;

        Hold(long startMs, long bufferedMs, long allocatedBytes) {
            this.startMs = startMs;
            this.bufferedMs = bufferedMs;
            this.allocatedBytes = allocatedBytes;
        }
    }

    private LoadHolds() {
    }

    /**
     * The player decided whether to load more
     *
     * @param allocatedBytes what its buffer holds
     */
    public static synchronized void onDecision(boolean isLoading, long bufferedDurationUs, long allocatedBytes) {
        if (isLoading) {
            if (sOpen != null) {
                sOpen.endMs = sClock.now();
                sOpen = null;
            }

            return;
        }

        if (sOpen == null) {
            sOpen = new Hold(sClock.now(), bufferedDurationUs / 1_000, allocatedBytes);
            sHolds.addLast(sOpen);

            if (sHolds.size() > MAX_HOLDS) {
                sHolds.removeFirst();
            }
        }
    }

    /**
     * The ones between the times, cut to them, as stages of a start's buffering (in the clock's time)
     */
    static synchronized List<VideoStart.Stage> take(long fromMs, long toMs) {
        List<VideoStart.Stage> result = new ArrayList<>();

        for (Hold hold : sHolds) {
            long startMs = Math.max(hold.startMs, fromMs);
            long endMs = Math.min(hold.endMs >= 0 ? hold.endMs : toMs, toMs);

            if (endMs - startMs >= 1) {
                long megabytes = Math.round(hold.allocatedBytes / 1_000_000f);
                String detail = hold.bufferedMs >= 0 && hold.bufferedMs <= MAX_BUFFERED_MS
                        ? String.format(Locale.US, "buffered %.1f s, %d MB", hold.bufferedMs / 1_000f, megabytes)
                        : String.format(Locale.US, "buffered to the end, %d MB", megabytes);
                result.add(new VideoStart.Stage(VideoStarts.STAGE_LOADING_HELD, detail, 1, startMs, endMs));
            }
        }

        return result;
    }

    @VisibleForTesting
    static synchronized void resetForTesting(VideoStarts.Clock clock) {
        sHolds.clear();
        sOpen = null;
        sClock = clock != null ? clock : SystemClock::elapsedRealtime;
    }
}
