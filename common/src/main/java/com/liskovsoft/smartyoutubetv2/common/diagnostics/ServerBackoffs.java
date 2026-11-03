package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.os.SystemClock;

import androidx.annotation.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The waits SABR's server asked for (see SabrStream#setBackoffListener). One under 15 s isn't waited (see SabrExtractorInput), so a
 * segment asked for in it may be answered late: shown in a start's buffering (see VideoStarts). Told on the loader's thread, read on
 * the main one.
 */
public final class ServerBackoffs {
    /**
     * The last ones: a start only needs the ones since it was prepared
     */
    static final int MAX_BACKOFFS = 8;
    private static final ArrayDeque<long[]> sBackoffs = new ArrayDeque<>(); // {told, asked}
    @VisibleForTesting
    static VideoStarts.Clock sClock = SystemClock::elapsedRealtime;

    private ServerBackoffs() {
    }

    public static synchronized void onBackoff(int backoffMs) {
        sBackoffs.addLast(new long[]{sClock.now(), backoffMs});

        if (sBackoffs.size() > MAX_BACKOFFS) {
            sBackoffs.removeFirst();
        }
    }

    /**
     * The ones told between the times, from then for as long as asked, cut to the end, as stages of a start's buffering (in the
     * clock's time). One told before is of the stream before.
     */
    static synchronized List<VideoStart.Stage> take(long fromMs, long toMs) {
        List<VideoStart.Stage> result = new ArrayList<>();

        for (long[] backoff : sBackoffs) {
            long startMs = backoff[0];
            long endMs = Math.min(startMs + backoff[1], toMs);

            if (startMs >= fromMs && endMs - startMs >= 1) {
                result.add(new VideoStart.Stage(VideoStarts.STAGE_SERVER_BACKOFF, String.format(Locale.US, "%d ms asked", backoff[1]), 1,
                        startMs, endMs));
            }
        }

        return result;
    }

    @VisibleForTesting
    static synchronized void resetForTesting(VideoStarts.Clock clock) {
        sBackoffs.clear();
        sClock = clock != null ? clock : SystemClock::elapsedRealtime;
    }
}
