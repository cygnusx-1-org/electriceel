package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.os.SystemClock;

import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.C;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A live stream's waits for its next segment at its head (see DefaultSabrChunkSource#setLiveHeadWaitListener): its request got none,
 * the segment not there yet, and the next one waits. Shown in a start's buffering (see VideoStarts): a live start resumed at the head
 * had its first frame at 1177 ms and played at 3249 ms, with no row for the 1.25 s between (AFQQ49Z4 RJFwMQBXPUQ: a quarter of its 5 s
 * segments). Told on the loader's thread, read on the main one.
 */
public final class LiveHeadWaits {
    /**
     * Of a start, rows of their own: more are one row (see #take)
     */
    static final int MAX_ROWS = 8;
    /**
     * The last ones: a start only needs the ones since it was prepared. The shortest wait is 250 ms (DefaultSabrChunkSource), a start
     * is timed for a minute at most, two tracks.
     */
    private static final int MAX_KEPT = 500;
    private static final ArrayDeque<long[]> sWaits = new ArrayDeque<>(); // {told, waitMs, trackType}
    @VisibleForTesting
    static VideoStarts.Clock sClock = SystemClock::elapsedRealtime;

    private LiveHeadWaits() {
    }

    public static synchronized void onWait(int trackType, long waitMs) {
        sWaits.addLast(new long[]{sClock.now(), waitMs, trackType});

        if (sWaits.size() > MAX_KEPT) {
            sWaits.removeFirst();
        }
    }

    /**
     * The ones told between the times, from then for as long as the next request waits, cut to the end, as stages of a start's buffering
     * (in the clock's time), e.g. "video, next asked in 1250 ms". One told before is of the stream before. Past MAX_ROWS, one row from
     * the first told to the end of the last, with how many there were of each track, e.g. "12 waits, video 7, audio 5".
     */
    static synchronized List<VideoStart.Stage> take(long fromMs, long toMs) {
        List<long[]> told = new ArrayList<>();

        for (long[] wait : sWaits) {
            if (wait[0] >= fromMs && getEndMs(wait, toMs) - wait[0] >= 1) {
                told.add(wait);
            }
        }

        List<VideoStart.Stage> result = new ArrayList<>();

        if (told.size() <= MAX_ROWS) {
            for (long[] wait : told) {
                String detail = String.format(Locale.US, "%s, next asked in %d ms", describeTrack(wait[2]), wait[1]);
                result.add(new VideoStart.Stage(VideoStarts.STAGE_LIVE_HEAD, detail, 1, wait[0], getEndMs(wait, toMs)));
            }

            return result;
        }

        int videoWaits = 0;
        int audioWaits = 0;
        long endMs = told.get(0)[0];

        for (long[] wait : told) {
            endMs = Math.max(endMs, getEndMs(wait, toMs));

            if (wait[2] == C.TRACK_TYPE_VIDEO) {
                videoWaits++;
            } else if (wait[2] == C.TRACK_TYPE_AUDIO) {
                audioWaits++;
            }
        }

        String detail = String.format(Locale.US, "%d waits, video %d, audio %d", told.size(), videoWaits, audioWaits);
        result.add(new VideoStart.Stage(VideoStarts.STAGE_LIVE_HEAD, detail, 1, told.get(0)[0], endMs));

        return result;
    }

    private static String describeTrack(long trackType) {
        return trackType == C.TRACK_TYPE_VIDEO ? "video" : trackType == C.TRACK_TYPE_AUDIO ? "audio" : "track " + trackType;
    }

    /**
     * The waits told in the last ms given: a live stall with some is the stream's head standing still, its server answering that
     * the next segment isn't there yet (see ErrorFixerController#runLongBufferingAction)
     */
    public static synchronized int countWithin(long windowMs) {
        long nowMs = sClock.now();
        int result = 0;

        for (long[] wait : sWaits) {
            if (nowMs - wait[0] <= windowMs) {
                result++;
            }
        }

        return result;
    }

    /**
     * When the next request is sent, cut to the time
     */
    private static long getEndMs(long[] wait, long toMs) {
        return Math.min(wait[0] + wait[1], toMs);
    }

    /**
     * None told, on the device's clock: another package's test's
     */
    @VisibleForTesting
    public static void resetForTesting() {
        resetForTesting(null);
    }

    @VisibleForTesting
    static synchronized void resetForTesting(VideoStarts.Clock clock) {
        sWaits.clear();
        sClock = clock != null ? clock : SystemClock::elapsedRealtime;
    }
}
