package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.liskovsoft.sharedutils.helpers.Helpers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The failures that throw no error: the player is fed a sample every couple of seconds, closer at the start (see #getTickMs), and
 * tells when the video never shows, freezes, drops many frames, stalls again and again, or the sound is silent. Each once per format tried.
 * Used on the main thread.
 */
public final class PlaybackHealth {
    public static final String KIND_NO_VIDEO = "no_video";
    public static final String KIND_VIDEO_FROZEN = "video_frozen";
    /**
     * The picture stood still at the start while the sound played, then went on
     */
    public static final String KIND_START_FROZEN = "start_frozen";
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
    /**
     * Of playing time: the start, sampled closer and with its frames kept. A freeze there can end by itself: the renderer waits till
     * the clock passes the next keyframe (about 5 s in) to drop what it's late for.
     */
    @VisibleForTesting
    static final long START_MS = 10_000;
    /**
     * A freeze of the start this long is reported when it ends
     */
    @VisibleForTesting
    static final long START_FROZEN_MS = 2_000;
    /**
     * A freeze of the start this long isn't one that ends by itself
     */
    @VisibleForTesting
    static final long START_STUCK_MS = 8_000;
    /**
     * After the position stood still and jumped, the renderer drops what it's now late for: at most this long
     */
    @VisibleForTesting
    static final long CATCH_UP_MS = 2_000;
    public static final long START_TICK_MS = 500;
    private static final long TICK_MS = 2_000;
    /**
     * Pausing again and again doesn't add to the ticks of the start's playing time
     */
    private static final int MAX_START_TRAIL = 40;
    @VisibleForTesting
    static final long SILENT_MS = 15_000;
    /**
     * Silence that didn't start with the playback is the video's own, unless it goes on this long
     */
    @VisibleForTesting
    static final long LONG_SILENT_MS = 60_000;
    /**
     * Played before the silence, for it to still be from the start
     */
    @VisibleForTesting
    static final long SILENT_START_SLACK_MS = 3_000;
    @VisibleForTesting
    static final int STALLS = 3;
    /**
     * In all: stalls adding up to less can't be seen
     */
    @VisibleForTesting
    static final long STALLS_MIN_MS = 1_000;
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
    /**
     * The position and the frames stood still this long, while it's playing (the audio clock, see AudioClockInfo)
     */
    private long mClockStillMs;
    private boolean mIsStartClockStill;
    private long mCatchUpEndMs = -1;
    private long mCatchUpDropped = -1;
    private final ArrayDeque<long[]> mFrames = new ArrayDeque<>(); // {time, rendered, dropped}
    /**
     * Of the format tried, by the ticks
     */
    private long mPlayedMs;
    private boolean mIsPlaying;
    private long mLongestFrozenMs;
    private long mStartFrozenMs;
    private final List<long[]> mStartTrail = new ArrayList<>();
    private boolean mIsStartMeasured;
    @Nullable
    private StartFrames mStartFrames;
    @Nullable
    private String mVideoId;
    /**
     * Of the last sample with the video's counters: {rendered, dropped, dropped to a keyframe}
     */
    @Nullable
    private long[] mLastFrames;

    /**
     * Where the player is at
     */
    public static final class Sample {
        public long nowMs;
        /**
         * Of the format tried
         */
        @Nullable
        public String videoId;
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
         * Times it dropped what it was late for up to a keyframe
         */
        public long droppedToKeyframes = -1;
        /**
         * Samples given to the decoder
         */
        public long inputBuffers = -1;
        /**
         * Of the audio clock (see AudioClockInfo), -1 when unknown
         */
        public int audioTimestampState = -1;
        public long audioPlayheadMs = -1;
        public long audioLatencyMs = -1;
        /**
         * A frame of the format tried was shown
         */
        public boolean isVideoRendered;
        public long silentMs;
        /**
         * Of the format tried (see FormatAttempts)
         */
        public int stalls;
        public long stallMs;
        /**
         * Of the format tried (see FormatAttempts)
         */
        public long playedMs;
    }

    /**
     * How the video's first seconds went, for the start's record (see VideoStarts#onFrames)
     */
    public static final class StartFrames {
        @Nullable
        public final String videoId;
        /**
         * Under START_MS when the format tried ended sooner (see #endStart)
         */
        public final long playedMs;
        public final long renderedFrames;
        public final long droppedFrames;
        public final long droppedToKeyframes;
        /**
         * The picture stood still this long, while the position moved or stood still too
         */
        public final long longestFrozenMs;

        StartFrames(@Nullable String videoId, long playedMs, long renderedFrames, long droppedFrames, long droppedToKeyframes,
                    long longestFrozenMs) {
            this.videoId = videoId;
            this.playedMs = playedMs;
            this.renderedFrames = renderedFrames;
            this.droppedFrames = droppedFrames;
            this.droppedToKeyframes = droppedToKeyframes;
            this.longestFrozenMs = longestFrozenMs;
        }
    }

    /**
     * Silent for all it played: the stream, not a quiet part of the video
     */
    public static boolean isSilentFromStart(Sample sample) {
        return sample.silentMs >= SILENT_MS && sample.playedMs - sample.silentMs <= SILENT_START_SLACK_MS;
    }

    /**
     * The kinds found, once each per format tried
     */
    public List<String> onSample(Sample sample) {
        if (sample.attempt != mAttempt) {
            // The video before ended in its start (e.g. the next one started): what it played of it. Another format of the same video
            // is measured on its own.
            StartFrames early = Helpers.equals(sample.videoId, mVideoId) ? null : endStart();
            reset(sample.attempt);
            mStartFrames = early;
        }

        mVideoId = sample.videoId;

        List<String> result = new ArrayList<>();
        mIsPlaying = sample.isPlaying;
        long elapsedMs = mLastTickMs >= 0 ? Math.max(0, sample.nowMs - mLastTickMs) : 0;
        boolean wasPlaying = mLastTickMs >= 0 && mLastPositionMs >= 0;

        if (sample.stalls >= STALLS && sample.stallMs >= STALLS_MIN_MS) {
            report(result, KIND_STALLS);
        }

        if (!sample.isPlaying) {
            // Paused or buffering: the time between ticks isn't playing time
            mLastTickMs = sample.nowMs;
            mLastPositionMs = -1;
            mFrozenMs = 0;
            mClockStillMs = 0;
            return result;
        }

        if (wasPlaying) {
            mPlayedMs += elapsedMs;
        }

        if (!mIsStartMeasured && sample.hasVideo && sample.renderedFrames >= 0 && sample.droppedFrames >= 0) {
            mLastFrames = new long[]{sample.renderedFrames, sample.droppedFrames, sample.droppedToKeyframes};
        }

        if (sample.hasVideo && sample.renderedFrames >= 0 && mPlayedMs <= START_MS && mStartTrail.size() < MAX_START_TRAIL) {
            mStartTrail.add(new long[]{mPlayedMs, sample.positionMs, sample.renderedFrames, sample.droppedFrames, sample.inputBuffers,
                    sample.droppedToKeyframes, sample.audioTimestampState, sample.audioPlayheadMs, sample.audioLatencyMs});
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
            long frozenMs = mFrozenMs;
            long clockStillMs = mClockStillMs;
            mFrozenMs = hasMoved && isStill ? mFrozenMs + elapsedMs : 0;
            mClockStillMs = !hasMoved && isStill ? mClockStillMs + elapsedMs : 0;
            mLongestFrozenMs = Math.max(mLongestFrozenMs, Math.max(mFrozenMs, mClockStillMs));
            boolean isOfStart = isFreezeOfStart(Math.max(frozenMs, mFrozenMs));

            if (mFrozenMs >= (isOfStart ? START_STUCK_MS : FROZEN_MS)) {
                report(result, KIND_VIDEO_FROZEN);
            } else if (isOfStart && !isStill && frozenMs >= START_FROZEN_MS && !mReported.contains(KIND_VIDEO_FROZEN)) {
                mStartFrozenMs = frozenMs;
                report(result, KIND_START_FROZEN);
                // What it dropped to catch up is this freeze, not frames dropped on top of it
                mFrames.clear();
            } else if (hasMoved && clockStillMs >= START_FROZEN_MS && isFreezeOfStart(clockStillMs) && !mReported.contains(KIND_VIDEO_FROZEN)) {
                // The clock stood still with the picture, then went on: what it's now late for is dropped from here on
                mStartFrozenMs = clockStillMs;
                mIsStartClockStill = true;
                report(result, KIND_START_FROZEN);
                mCatchUpEndMs = sample.nowMs + CATCH_UP_MS;
                mCatchUpDropped = -1;
            }
        }

        if (sample.hasVideo && sample.renderedFrames >= 0 && sample.droppedFrames >= 0) {
            checkDrops(sample, result);
        }

        if (sample.hasAudio && !sample.isMuted && (isSilentFromStart(sample) || sample.silentMs >= LONG_SILENT_MS)) {
            report(result, KIND_AUDIO_SILENT);
        }

        if (!mIsStartMeasured && mPlayedMs >= START_MS && sample.hasVideo && sample.renderedFrames >= 0 && sample.droppedFrames >= 0) {
            mIsStartMeasured = true;
            mStartFrames = new StartFrames(mVideoId, mPlayedMs, sample.renderedFrames, sample.droppedFrames, sample.droppedToKeyframes,
                    mLongestFrozenMs);
        }

        mLastTickMs = sample.nowMs;
        mLastPositionMs = sample.positionMs;
        mLastRendered = sample.renderedFrames;

        return result;
    }

    private void checkDrops(Sample sample, List<String> result) {
        if (mCatchUpEndMs >= 0) {
            // Till a sample with no new drops: the ones to come are counted from there
            boolean isDropping = sample.droppedFrames > mCatchUpDropped && sample.nowMs < mCatchUpEndMs;
            mCatchUpDropped = sample.droppedFrames;
            mCatchUpEndMs = isDropping ? mCatchUpEndMs : -1;
            mFrames.clear();
            mFrames.addLast(new long[]{sample.nowMs, sample.renderedFrames, sample.droppedFrames});
            return;
        }

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

        // A freeze of the start going on: reported as that when it ends
        boolean isFrozen = mFrozenMs >= START_FROZEN_MS && isFreezeOfStart(mFrozenMs);

        if (!isFrozen && dropped >= DROPS_MIN && dropped >= DROPS_RATIO * (dropped + rendered)) {
            report(result, KIND_FRAME_DROPS);
        }
    }

    private boolean isFreezeOfStart(long frozenMs) {
        return mPlayedMs - frozenMs < START_MS;
    }

    /**
     * Till the next sample
     */
    public long getTickMs() {
        // Paused in it (for hours maybe) there's nothing to watch closely
        return mPlayedMs < START_MS && (mIsPlaying || mPlayedMs == 0) ? START_TICK_MS : TICK_MS;
    }

    /**
     * How long the freeze just reported as KIND_START_FROZEN was
     */
    public long getStartFrozenMs() {
        return mStartFrozenMs;
    }

    /**
     * The freeze just reported as KIND_START_FROZEN had the position stand still too: the clock's, not the renderer's
     */
    public boolean isStartClockStill() {
        return mIsStartClockStill;
    }

    /**
     * Each sample of the start, oldest first: {played, position, rendered, dropped, given to the decoder, dropped to a keyframe, audio
     * timestamp state, audio playhead, audio latency}. Empty past it: it doesn't tell about what happens later.
     */
    public List<long[]> getStartTrail() {
        return mPlayedMs <= START_MS ? mStartTrail : new ArrayList<>();
    }

    /**
     * The format tried ends before its start has played (e.g. the user left): what it played of it, once. Null when nothing of it
     * played, or its start was measured.
     */
    @Nullable
    public StartFrames endStart() {
        if (mIsStartMeasured || mPlayedMs <= 0 || mLastFrames == null) {
            return null;
        }

        mIsStartMeasured = true;

        return new StartFrames(mVideoId, mPlayedMs, mLastFrames[0], mLastFrames[1], mLastFrames[2], mLongestFrozenMs);
    }

    /**
     * Once per format tried, when its start has played or the one before ended in its own: null till then
     */
    @Nullable
    public StartFrames takeStartFrames() {
        StartFrames result = mStartFrames;
        mStartFrames = null;

        return result;
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
        mClockStillMs = 0;
        mIsStartClockStill = false;
        mCatchUpEndMs = -1;
        mCatchUpDropped = -1;
        mFrames.clear();
        mPlayedMs = 0;
        mLongestFrozenMs = 0;
        mStartFrozenMs = 0;
        mStartTrail.clear();
        mIsStartMeasured = false;
        mStartFrames = null;
        mLastFrames = null;
    }
}
