package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import androidx.annotation.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The failures that throw no error: the player is fed a sample every couple of seconds (see ExoPlayerController) and tells when
 * the video never shows, freezes, drops many frames, stalls again and again, or the sound is silent. Each once per format tried.
 * Used on the main thread.
 */
public final class PlaybackHealth {
    public static final String KIND_NO_VIDEO = "no_video";
    public static final String KIND_VIDEO_FROZEN = "video_frozen";
    public static final String KIND_FRAME_DROPS = "frame_drops";
    public static final String KIND_STALLS = "stalls";
    public static final String KIND_AUDIO_SILENT = "audio_silent";
    /**
     * Playing this long and no frame shown
     */
    @VisibleForTesting
    static final long NO_VIDEO_MS = 6_000;
    /**
     * The position moved on this long and the frames didn't
     */
    @VisibleForTesting
    static final long FROZEN_MS = 5_000;
    @VisibleForTesting
    static final long SILENT_MS = 15_000;
    @VisibleForTesting
    static final int STALLS = 3;
    @VisibleForTesting
    static final long DROPS_WINDOW_MS = 10_000;
    @VisibleForTesting
    static final int DROPS_MIN = 30;
    /**
     * Of the frames in the window
     */
    private static final double DROPS_RATIO = 0.25;
    private long mAttempt = -1;
    private final Set<String> mReported = new HashSet<>();
    private long mLastTickMs = -1;
    private long mLastPositionMs = -1;
    private long mLastRendered = -1;
    private long mNoFrameMs;
    private long mFrozenMs;
    private final ArrayDeque<long[]> mFrames = new ArrayDeque<>(); // {time, rendered, dropped}

    /**
     * Where the player is at
     */
    public static final class Sample {
        public long nowMs;
        /**
         * The format tried, see FormatAttempts#getAttemptId
         */
        public long attempt;
        public boolean isPlaying;
        public long positionMs;
        public boolean hasVideo;
        public boolean hasAudio;
        /**
         * Muted or volume 0: silence is meant
         */
        public boolean isMuted;
        /**
         * Of the video decoder, -1 when unknown
         */
        public long renderedFrames = -1;
        public long droppedFrames = -1;
        /**
         * A frame of the format tried was shown
         */
        public boolean isVideoRendered;
        public long silentMs;
        /**
         * Of the format tried (see FormatAttempts)
         */
        public int stalls;
    }

    /**
     * The kinds found, once each per format tried
     */
    public List<String> onSample(Sample sample) {
        if (sample.attempt != mAttempt) {
            reset(sample.attempt);
        }

        List<String> result = new ArrayList<>();
        long elapsedMs = mLastTickMs >= 0 ? Math.max(0, sample.nowMs - mLastTickMs) : 0;
        boolean wasPlaying = mLastTickMs >= 0 && mLastPositionMs >= 0;

        if (sample.stalls >= STALLS) {
            report(result, KIND_STALLS);
        }

        if (!sample.isPlaying) {
            // Paused or buffering: the time between ticks isn't playing time
            mLastTickMs = sample.nowMs;
            mLastPositionMs = -1;
            mFrozenMs = 0;
            return result;
        }

        if (sample.hasVideo && !sample.isVideoRendered && wasPlaying) {
            mNoFrameMs += elapsedMs;

            if (mNoFrameMs >= NO_VIDEO_MS) {
                report(result, KIND_NO_VIDEO);
            }
        }

        if (sample.hasVideo && sample.isVideoRendered && sample.renderedFrames >= 0 && wasPlaying) {
            boolean hasMoved = sample.positionMs - mLastPositionMs >= elapsedMs / 2;
            boolean isStill = sample.renderedFrames == mLastRendered;
            mFrozenMs = hasMoved && isStill ? mFrozenMs + elapsedMs : 0;

            if (mFrozenMs >= FROZEN_MS) {
                report(result, KIND_VIDEO_FROZEN);
            }
        }

        if (sample.hasVideo && sample.renderedFrames >= 0 && sample.droppedFrames >= 0) {
            checkDrops(sample, result);
        }

        if (sample.hasAudio && !sample.isMuted && sample.silentMs >= SILENT_MS) {
            report(result, KIND_AUDIO_SILENT);
        }

        mLastTickMs = sample.nowMs;
        mLastPositionMs = sample.positionMs;
        mLastRendered = sample.renderedFrames;

        return result;
    }

    private void checkDrops(Sample sample, List<String> result) {
        long[] last = mFrames.peekLast();

        // The decoder was reset (e.g. the engine restarted): counting starts over
        if (last != null && (sample.renderedFrames < last[1] || sample.droppedFrames < last[2])) {
            mFrames.clear();
        }

        mFrames.addLast(new long[]{sample.nowMs, sample.renderedFrames, sample.droppedFrames});

        while (mFrames.size() > 1 && sample.nowMs - mFrames.peekFirst()[0] > DROPS_WINDOW_MS) {
            mFrames.removeFirst();
        }

        long[] first = mFrames.peekFirst();
        long dropped = sample.droppedFrames - first[2];
        long rendered = sample.renderedFrames - first[1];

        if (dropped >= DROPS_MIN && dropped >= DROPS_RATIO * (dropped + rendered)) {
            report(result, KIND_FRAME_DROPS);
        }
    }

    private void report(List<String> result, String kind) {
        if (mReported.add(kind)) {
            result.add(kind);
        }
    }

    private void reset(long attempt) {
        mAttempt = attempt;
        mReported.clear();
        mLastTickMs = -1;
        mLastPositionMs = -1;
        mLastRendered = -1;
        mNoFrameMs = 0;
        mFrozenMs = 0;
        mFrames.clear();
    }
}
