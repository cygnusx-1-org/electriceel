package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.liskovsoft.sharedutils.helpers.Helpers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
     * What it plays again after (see #onInterrupted): a seek makes another AudioTrack, a pause or buffering pauses the one it has. Its
     * clock starts over either way.
     */
    public static final String RESUMED_BY_SEEK = "seek";
    public static final String RESUMED_BY_PAUSE = "pause";
    public static final String RESUMED_BY_BUFFERING = "buffering";
    /**
     * The source was prepared again past the start (see #onStarted): its decoder and its AudioTrack started over
     */
    public static final String RESUMED_BY_PREPARE = "prepare";
    /**
     * The more likely to have started the audio clock over, the later: one comes before the ones it's in front of
     */
    private static final List<String> RESUME_CAUSES = Arrays.asList(RESUMED_BY_BUFFERING, RESUMED_BY_PAUSE, RESUMED_BY_SEEK);
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
     * After the position stood still and jumped, or a seek, the renderer drops what it's now late for: at most this long
     */
    @VisibleForTesting
    static final long CATCH_UP_MS = 2_000;
    /**
     * Played again this soon after a seek, it's the seek's resume (see #onResumed): the buffering after it can come between, and a
     * moment of playing before that
     */
    @VisibleForTesting
    static final long SEEK_RESUME_MS = 5_000;
    public static final long START_TICK_MS = 500;
    private static final long TICK_MS = 2_000;
    /**
     * Ticks farther apart stopped meanwhile (e.g. the player was released, then the video opened again): the time between isn't
     * playing time
     */
    @VisibleForTesting
    static final long MAX_TICK_GAP_MS = 3 * TICK_MS;
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
    /**
     * The freeze reported as KIND_VIDEO_FROZEN had the position stand still too, and it still did (see #isClockStuck)
     */
    private boolean mIsClockStuck;
    private long mCatchUpEndMs = -1;
    private long mCatchUpDropped = -1;
    private long mCatchUpRendered = -1;
    /**
     * The drops' window row of the sample a catch-up ended at (see #isDropsAfterCatchUp), null for none
     */
    @Nullable
    private long[] mCatchUpEndRow;
    /**
     * Of the sample just given: how long the picture had stood still when a pause ended the freeze it reported (see
     * #getPauseFrozenMs), -1 for none
     */
    private long mPauseFrozenMs = -1;
    /**
     * When it was last sent elsewhere, on the samples' clock (see #onSeek), -1 for not since the format was tried
     */
    private long mSeekMs = -1;
    private final ArrayDeque<long[]> mFrames = new ArrayDeque<>(); // {time, rendered, dropped, dropped to a keyframe}
    /**
     * Of the format tried, by the ticks
     */
    private long mPlayedMs;
    private boolean mIsPlaying;
    /**
     * Of the format tried: the sound buffers decoded when it was reported silent, and how long it had played when the sound came
     * back after that (see #takeSoundBackMs), -1 till then
     */
    private long mSoundBuffersSilent = -1;
    private long mSoundBackMs = -1;
    private boolean mIsSoundBackTaken;
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
     * The start's audio clock: what the counts of all the AudioTracks went up by in it, its timestamp's last state and the longest the
     * position waited on one, -1 till a sample has them
     */
    private final Increase mStartFramesMismatches = new Increase();
    private final Increase mStartClockMismatches = new Increase();
    private final Increase mStartHoldReleases = new Increase();
    private final Increase mStartHoldSkips = new Increase();
    private int mStartTimestampState = -1;
    private long mStartLongestHoldMs = -1;
    /**
     * When it last played again after a seek, a pause or buffering, on the samples' clock (see #onResumed): the first seconds from
     * there are sampled closely, as the start's are. -1 when it hasn't since the video started.
     */
    private long mResumedAtMs = -1;
    @Nullable
    private String mResumedBy;
    /**
     * Since it last played, see RESUME_CAUSES
     */
    @Nullable
    private String mInterruption;
    private final List<long[]> mResumeTrail = new ArrayList<>();

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
        /**
         * Not playing as it was told not to (by the user or the app), not buffering
         */
        public boolean isPaused;
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
         * Of the video track alone: how far past the position its samples are loaded, -1 when unknown. None and nothing given to the
         * decoder is no samples to give it, more is a decoder that doesn't take them.
         */
        public long videoBufferedAheadMs = -1;
        /**
         * How long after it was due this sample was taken (e.g. the main thread was held up), -1 when unknown
         */
        public long tickLateMs = -1;
        /**
         * Of the audio clock (see AudioClockInfo), -1 when unknown
         */
        public int audioTimestampState = -1;
        public long audioPlayheadMs = -1;
        public long audioLatencyMs = -1;
        /**
         * Of the audio clock of the AudioTrack in use (see AudioClockInfo), -1 when unknown: the longest the position waited on a
         * timestamp that didn't advance
         */
        public long audioLongestHoldMs = -1;
        /**
         * Of the audio clock of all the AudioTracks so far (see AudioClockInfo), -1 when unknown: the timestamps rejected for their
         * frames or their time being too far off, and the times the position stopped waiting on one that didn't advance
         */
        public long audioTotalFramesMismatches = -1;
        public long audioTotalClockMismatches = -1;
        public long audioTotalHoldReleases = -1;
        /**
         * Of all the AudioTracks so far (see AudioClockInfo), -1 when unknown: the times the position didn't wait on a timestamp that
         * didn't advance, the device's having stood still before
         */
        public long audioTotalHoldSkips = -1;
        /**
         * A frame of the format tried was shown
         */
        public boolean isVideoRendered;
        public long silentMs;
        /**
         * Decoded with sound in them, so far (see SilenceDetector#getSoundBuffers)
         */
        public long soundBuffers;
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
        /**
         * Null when its samples didn't have it
         */
        @Nullable
        public final StartAudioClock audioClock;

        StartFrames(@Nullable String videoId, long playedMs, long renderedFrames, long droppedFrames, long droppedToKeyframes,
                    long longestFrozenMs) {
            this(videoId, playedMs, renderedFrames, droppedFrames, droppedToKeyframes, longestFrozenMs, null);
        }

        StartFrames(@Nullable String videoId, long playedMs, long renderedFrames, long droppedFrames, long droppedToKeyframes,
                    long longestFrozenMs, @Nullable StartAudioClock audioClock) {
            this.videoId = videoId;
            this.playedMs = playedMs;
            this.renderedFrames = renderedFrames;
            this.droppedFrames = droppedFrames;
            this.droppedToKeyframes = droppedToKeyframes;
            this.longestFrozenMs = longestFrozenMs;
            this.audioClock = audioClock;
        }
    }

    /**
     * The audio clock over a video's first seconds (see AudioClockInfo): whether it started on a timestamp that didn't advance, and
     * what the position did about it
     */
    public static final class StartAudioClock {
        /**
         * AudioTimestampPoller's, at their end: -1 unknown, 0 initializing, 1 not advancing, 2 advancing, 3 none, 4 rejected
         */
        public final int timestampState;
        /**
         * Timestamps rejected in them for their frames or their time being too far off: the position waited on one that long
         */
        public final long framesMismatches;
        public final long clockMismatches;
        /**
         * Times in them the position stopped waiting on a timestamp that didn't advance, and went by the playhead
         */
        public final long holdReleases;
        /**
         * The longest it waited on one: how far the playhead got past it meanwhile
         */
        public final long longestHoldMs;
        /**
         * Times in them it didn't wait on one, the device's timestamps having stood still before (see AudioClockInfo#holdSkips): -1
         * when unknown
         */
        public final long holdSkips;

        StartAudioClock(int timestampState, long framesMismatches, long clockMismatches, long holdReleases, long longestHoldMs,
                long holdSkips) {
            this.timestampState = timestampState;
            this.framesMismatches = framesMismatches;
            this.clockMismatches = clockMismatches;
            this.holdReleases = holdReleases;
            this.longestHoldMs = longestHoldMs;
            this.holdSkips = holdSkips;
        }
    }

    /**
     * What a counter went up by from the first value given. Another player's starts over from 0.
     */
    private static final class Increase {
        private long mIncrease;
        private long mLast = -1;

        void add(long value) {
            if (value < 0) {
                return;
            }

            if (mLast >= 0) {
                // Under the one before: it started over, all of it is new
                mIncrease += value >= mLast ? value - mLast : value;
            }

            mLast = value;
        }

        boolean isKnown() {
            return mLast >= 0;
        }

        long get() {
            return mIncrease;
        }

        void reset() {
            mIncrease = 0;
            mLast = -1;
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
            boolean isSameVideo = Helpers.equals(sample.videoId, mVideoId);
            StartFrames early = isSameVideo ? null : endStart();
            reset(sample.attempt);
            mStartFrames = early;

            // A prepare's resume was the format's before (see onStarted): this one never played
            if (!isSameVideo || RESUMED_BY_PREPARE.equals(mResumedBy)) {
                endResume();
            }
        }

        mVideoId = sample.videoId;
        mPauseFrozenMs = -1;

        List<String> result = new ArrayList<>();
        mIsPlaying = sample.isPlaying;
        long elapsedMs = mLastTickMs >= 0 ? Math.max(0, sample.nowMs - mLastTickMs) : 0;
        // Not across a gap in the ticks: the same video opened again 77 s after it was left was "frozen" for those 77 s, with its clock
        // still (CX17FXZW #12). The formats were the same, so the attempt went on (see FormatAttempts#onTracks).
        boolean isTickGap = elapsedMs > MAX_TICK_GAP_MS;
        boolean wasPlaying = mLastTickMs >= 0 && mLastPositionMs >= 0 && !isTickGap;

        if (isTickGap) {
            mFrozenMs = 0;
            mClockStillMs = 0;
        }

        if (sample.stalls >= STALLS && sample.stallMs >= STALLS_MIN_MS) {
            report(result, KIND_STALLS);
        }

        if (!mIsStartMeasured) {
            addStartAudioClock(sample);
        }

        if (!sample.isPlaying) {
            if (sample.isPaused && wasPlaying) {
                checkFreezeAtPause(sample, elapsedMs, result);
            }

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
            mStartTrail.add(toTrailRow(mPlayedMs, sample));
        }

        if (sample.hasVideo && sample.renderedFrames >= 0 && isResuming(sample.nowMs) && mResumeTrail.size() < MAX_START_TRAIL) {
            mResumeTrail.add(toTrailRow(Math.max(0, sample.nowMs - mResumedAtMs), sample));
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
                startCatchUp(sample.nowMs);
            } else if (!hasMoved && mClockStillMs >= START_STUCK_MS && !mReported.contains(KIND_VIDEO_FROZEN)) {
                // The clock stood still with the picture while it played, and still does: a start freeze is reported only once it
                // goes on (above), so one that never did wasn't (51AQC7FS: 2 frames in 10.4 s after a reload, then on and on)
                mIsClockStuck = true;
                report(result, KIND_VIDEO_FROZEN);
            }
        }

        if (sample.hasVideo && sample.renderedFrames >= 0 && sample.droppedFrames >= 0) {
            checkDrops(sample, result);
        }

        if (sample.hasAudio && !sample.isMuted && (isSilentFromStart(sample) || sample.silentMs >= LONG_SILENT_MS)) {
            if (mSoundBuffersSilent < 0) {
                mSoundBuffersSilent = sample.soundBuffers;
            }

            report(result, KIND_AUDIO_SILENT);
        } else if (mSoundBuffersSilent >= 0 && mSoundBackMs < 0 && sample.soundBuffers > mSoundBuffersSilent) {
            // A sound was decoded since: the silence reported ended (e.g. a silent intro). Not a seek's: it only empties the count.
            mSoundBackMs = sample.playedMs;
        }

        if (!mIsStartMeasured && mPlayedMs >= START_MS && sample.hasVideo && sample.renderedFrames >= 0 && sample.droppedFrames >= 0) {
            mIsStartMeasured = true;
            mStartFrames = new StartFrames(mVideoId, mPlayedMs, sample.renderedFrames, sample.droppedFrames, sample.droppedToKeyframes,
                    mLongestFrozenMs, getStartAudioClock());
        }

        mLastTickMs = sample.nowMs;
        mLastPositionMs = sample.positionMs;
        mLastRendered = sample.renderedFrames;

        return result;
    }

    private void checkDrops(Sample sample, List<String> result) {
        if (mCatchUpEndMs >= 0) {
            // Till a sample that shows frames and drops none: the ones to come are counted from there. Not one that does neither, its
            // decoder hasn't put out a frame yet: MD8RQZM2 #1, a start resumed at 0:02, showed none and dropped none in its first
            // 571 ms, then dropped 75 of 83 by 1.6 s, counted as frame drops.
            boolean isCaughtUp = mCatchUpDropped >= 0 && sample.droppedFrames <= mCatchUpDropped
                    && sample.renderedFrames > mCatchUpRendered;
            boolean isCatchingUp = !isCaughtUp && sample.nowMs < mCatchUpEndMs;
            mCatchUpDropped = sample.droppedFrames;
            mCatchUpRendered = sample.renderedFrames;
            mCatchUpEndMs = isCatchingUp ? mCatchUpEndMs : -1;
            mFrames.clear();
            long[] row = toFramesRow(sample);
            mFrames.addLast(row);
            mCatchUpEndRow = isCatchingUp ? null : row;
            return;
        }

        long[] last = mFrames.peekLast();

        // The decoder was reset (e.g. the engine restarted): counting starts over
        if (last != null && (sample.renderedFrames < last[1] || sample.droppedFrames < last[2])) {
            mFrames.clear();
        }

        mFrames.addLast(toFramesRow(sample));

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

    /**
     * Paused while the picture stood still and the position went on: the freeze didn't end by itself, the pause ended it. Reported first,
     * as one that ends, or goes on, while it plays is (see #onSample): H5B64KBE 11-21 12:00:57, still 5.2 s at the start and then paused,
     * had none, and a freeze not reported never reaches its fix (see TunnelingFallback).
     */
    private void checkFreezeAtPause(Sample sample, long elapsedMs, List<String> result) {
        // A seek since: not the picture it was
        if (!sample.hasVideo || !sample.isVideoRendered || sample.renderedFrames < 0 || mSeekMs > mLastTickMs) {
            return;
        }

        // Still till the pause, it played on from the sample before. A frame shown since ended it by itself, as one whose picture goes on.
        boolean isStill = sample.renderedFrames == mLastRendered;
        long frozenMs = isStill ? mFrozenMs + Math.min(elapsedMs, Math.max(0, sample.positionMs - mLastPositionMs)) : mFrozenMs;
        boolean isOfStart = isFreezeOfStart(mFrozenMs);
        String kind;

        if (isStill && frozenMs >= (isOfStart ? START_STUCK_MS : FROZEN_MS)) {
            kind = KIND_VIDEO_FROZEN;
        } else if (isOfStart && frozenMs >= START_FROZEN_MS && !mReported.contains(KIND_VIDEO_FROZEN)) {
            kind = KIND_START_FROZEN;
        } else {
            return;
        }

        if (mReported.contains(kind)) {
            return;
        }

        mLongestFrozenMs = Math.max(mLongestFrozenMs, frozenMs);
        mPauseFrozenMs = isStill ? frozenMs : -1;
        report(result, kind);

        if (KIND_START_FROZEN.equals(kind)) {
            mStartFrozenMs = frozenMs;
            // What it dropped while still is this freeze, not frames dropped on top of it
            mFrames.clear();
        }
    }

    private static long[] toFramesRow(Sample sample) {
        return new long[]{sample.nowMs, sample.renderedFrames, sample.droppedFrames, sample.droppedToKeyframes};
    }

    private boolean isFreezeOfStart(long frozenMs) {
        return mPlayedMs - frozenMs < START_MS;
    }

    /**
     * {played, position, rendered, dropped, given to the decoder, dropped to a keyframe, audio timestamp state, audio playhead, audio
     * latency, video buffered ahead, sampled late by}. The last two tell a decoder given nothing for seconds that had nothing to take from
     * one that took nothing, and a gap between rows from a main thread held up: T26RECBZ #1 had no row for 2.1 s while its decoder took
     * no input, and neither could be told.
     */
    private static long[] toTrailRow(long playedMs, Sample sample) {
        return new long[]{playedMs, sample.positionMs, sample.renderedFrames, sample.droppedFrames, sample.inputBuffers,
                sample.droppedToKeyframes, sample.audioTimestampState, sample.audioPlayheadMs, sample.audioLatencyMs,
                sample.videoBufferedAheadMs, sample.tickLateMs};
    }

    private void addStartAudioClock(Sample sample) {
        if (sample.audioTotalHoldReleases < 0) {
            return;
        }

        mStartFramesMismatches.add(sample.audioTotalFramesMismatches);
        mStartClockMismatches.add(sample.audioTotalClockMismatches);
        mStartHoldReleases.add(sample.audioTotalHoldReleases);
        mStartHoldSkips.add(sample.audioTotalHoldSkips);
        mStartTimestampState = sample.audioTimestampState;
        mStartLongestHoldMs = Math.max(mStartLongestHoldMs, sample.audioLongestHoldMs);
    }

    @Nullable
    private StartAudioClock getStartAudioClock() {
        if (!mStartHoldReleases.isKnown()) {
            return null;
        }

        return new StartAudioClock(mStartTimestampState, mStartFramesMismatches.get(), mStartClockMismatches.get(), mStartHoldReleases.get(),
                mStartLongestHoldMs, mStartHoldSkips.isKnown() ? mStartHoldSkips.get() : -1);
    }

    /**
     * It stopped playing: one of the RESUMED_BY_ kinds. Till it plays again, the one more likely to have started the audio clock over
     * is kept.
     */
    public void onInterrupted(String cause) {
        if (RESUME_CAUSES.indexOf(cause) > RESUME_CAUSES.indexOf(mInterruption)) {
            mInterruption = cause;
        }
    }

    /**
     * The player was prepared with a source (e.g. the video opened again, a reload): the next sample is its first. The time since the
     * last one, of the player before, isn't this one's. Its frame counters start over too.
     */
    public void onPrepare() {
        mLastTickMs = -1;
        mLastPositionMs = -1;
        mLastRendered = -1;
        mNoFrameMs = 0;
        mFrozenMs = 0;
        mClockStillMs = 0;
        // The seeks and the catch up of the source before. Not when the format tried changes (see #reset): its first sample may come
        // after the start's seek, or after it played.
        mSeekMs = -1;
        mCatchUpEndMs = -1;
        mCatchUpDropped = -1;
        mCatchUpRendered = -1;
    }

    /**
     * It plays for the first time since the player was prepared: the start (see #getStartTrail). Sent to a position before (e.g. a
     * resume, see VideoStateController#restorePosition), its decoder catches up to it as after a seek's resume (see #onResumed):
     * YHN2G3G9 dropped 124 of 136 frames in the first 0.8 s of a start resumed at 20:20, none after. Not one sent where it was (see
     * #onSeek): it has nothing to catch up on, the frames it drops are ones it couldn't decode in time.
     * <p>
     * The format tried played past its start before (e.g. the video opened again where it was left, the same format): its seconds from
     * here are sampled as a resume's, after RESUMED_BY_PREPARE. H5B64KBE #1 froze 1 s into such a play, 816 s into the format's, and had
     * neither trail.
     *
     * @return whether they are: a sample now is the resume trail's first
     */
    public boolean onStarted(long nowMs) {
        mInterruption = null;
        endResume();

        if (mSeekMs >= 0) {
            startCatchUp(nowMs);
        }

        if (mPlayedMs <= START_MS) {
            return false;
        }

        mResumedAtMs = nowMs;
        mResumedBy = RESUMED_BY_PREPARE;

        return true;
    }

    /**
     * It plays again since the player was prepared, after a seek, a pause or buffering: the seconds from here are sampled closely,
     * as the start's are (see #getResumeTrail). A sample now is the trail's first.
     */
    public void onResumed(long nowMs) {
        mResumedAtMs = nowMs;
        mResumedBy = mInterruption;
        mInterruption = null;
        mResumeTrail.clear();

        // Its decoder catches up from the new position: what the renderer drops meanwhile isn't frames it can't keep up with. 1440p60
        // VP9 dropped 44 of 76 frames in the 1.35 s after a seek's resume, 12 in the 47 s before.
        if (mSeekMs >= 0 && nowMs - mSeekMs <= SEEK_RESUME_MS) {
            startCatchUp(nowMs);
        }
    }

    private void startCatchUp(long nowMs) {
        mCatchUpEndMs = nowMs + CATCH_UP_MS;
        mCatchUpDropped = -1;
        mCatchUpRendered = -1;
    }

    /**
     * Sent elsewhere (a seek of any kind): it stopped playing for it (see #onInterrupted), and the drops of its resume are its decoder
     * catching up (see #onResumed). Not sent to where it was: ExoPlayer does nothing for that (see ExoPlayerImplInternal#seekToInternal),
     * and VideoStateController#restorePosition makes one to 0:00 at the start of every video over 6 min with no position saved. Those
     * starts' first-second drops were let go as a catch-up, the same of a 0:40 video, with no seek, were reported (EY23XNK9 #1).
     *
     * @param fromMs where it was, -1 when unknown
     * @param toMs where it's sent
     */
    public void onSeek(long nowMs, long fromMs, long toMs) {
        if (fromMs >= 0 && fromMs == toMs) {
            return;
        }

        onInterrupted(RESUMED_BY_SEEK);
        mSeekMs = nowMs;
    }

    private boolean isResuming(long nowMs) {
        return mResumedAtMs >= 0 && nowMs - mResumedAtMs <= START_MS;
    }

    /**
     * Till the next sample
     */
    public long getTickMs() {
        // Paused in it (for hours maybe) there's nothing to watch closely
        return (mPlayedMs < START_MS || isResuming(mLastTickMs)) && (mIsPlaying || mPlayedMs == 0) ? START_TICK_MS : TICK_MS;
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
     * The freeze reported as KIND_VIDEO_FROZEN is the position's too, standing still while it plays: the clock's (e.g. the audio's
     * timestamp), not the video stream's
     */
    public boolean isClockStuck() {
        return mIsClockStuck;
    }

    /**
     * How long the position and the frames have stood still while it plays
     */
    public long getClockStillMs() {
        return mClockStillMs;
    }

    /**
     * Each sample of the start, oldest first: {played, position, rendered, dropped, given to the decoder, dropped to a keyframe, audio
     * timestamp state, audio playhead, audio latency, video buffered ahead, sampled late by} (see Sample). Empty past it: it doesn't tell
     * about what happens later.
     */
    public List<long[]> getStartTrail() {
        return mPlayedMs <= START_MS ? mStartTrail : new ArrayList<>();
    }

    /**
     * The samples the frame drops were counted over (see DROPS_WINDOW_MS), oldest first: {ms before now, rendered, dropped, dropped to
     * a keyframe}
     */
    public List<long[]> getDropsWindow(long nowMs) {
        List<long[]> result = new ArrayList<>();

        for (long[] row : mFrames) {
            result.add(new long[]{nowMs - row[0], row[1], row[2], row[3]});
        }

        return result;
    }

    /**
     * The frames dropped in the window (see #getDropsWindow) went with one drop up to a keyframe: the renderer was late by more than half
     * a second once and skipped to the next keyframe (see MediaCodecVideoRenderer#shouldDropBuffersToKeyframe), a jump in the picture,
     * not drops that go on (PQTQ5DCK: 102 of 5570 frames, one such skip)
     */
    public boolean isDropsOneSkip() {
        long[] first = mFrames.peekFirst();
        long[] last = mFrames.peekLast();

        return first != null && last != null && first[3] >= 0 && last[3] - first[3] == 1;
    }

    /**
     * The frames dropped in the window (see #getDropsWindow) were counted from where a catch-up ended: a start's or a resume's decoder
     * catching up to its position (see #onStarted, #onResumed), or a start's after its clock stood still. They went on past it.
     */
    public boolean isDropsAfterCatchUp() {
        long[] first = mFrames.peekFirst();

        return first != null && first == mCatchUpEndRow;
    }

    /**
     * Of the freeze just reported as KIND_START_FROZEN or KIND_VIDEO_FROZEN: how long the picture had stood still when a pause ended it,
     * -1 when it wasn't a pause
     */
    public long getPauseFrozenMs() {
        return mPauseFrozenMs;
    }

    /**
     * Each sample of the seconds since it last played again after a seek, a pause or buffering (see #onResumed), oldest first, as the
     * start's (see #getStartTrail) but its first is the time since then. Empty past them.
     */
    public List<long[]> getResumeTrail() {
        return isResuming(mLastTickMs) ? mResumeTrail : new ArrayList<>();
    }

    /**
     * What it last played again after (see #getResumeTrail): one of the RESUMED_BY_ kinds, null when unknown
     */
    @Nullable
    public String getResumedBy() {
        return mResumedBy;
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

        return new StartFrames(mVideoId, mPlayedMs, mLastFrames[0], mLastFrames[1], mLastFrames[2], mLongestFrozenMs, getStartAudioClock());
    }

    /**
     * Once per format tried that was reported silent (see KIND_AUDIO_SILENT), when its sound came back: how long it had played then.
     * -1 till then, and after it was taken.
     */
    public long takeSoundBackMs() {
        if (mSoundBackMs < 0 || mIsSoundBackTaken) {
            return -1;
        }

        mIsSoundBackTaken = true;

        return mSoundBackMs;
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
        mIsClockStuck = false;
        mFrames.clear();
        mCatchUpEndRow = null;
        mPlayedMs = 0;
        mSoundBuffersSilent = -1;
        mSoundBackMs = -1;
        mIsSoundBackTaken = false;
        mLongestFrozenMs = 0;
        mStartFrozenMs = 0;
        mStartTrail.clear();
        mIsStartMeasured = false;
        mStartFrames = null;
        mLastFrames = null;
        mStartFramesMismatches.reset();
        mStartClockMismatches.reset();
        mStartHoldReleases.reset();
        mStartHoldSkips.reset();
        mStartTimestampState = -1;
        mStartLongestHoldMs = -1;
        // Of the player's clock, not of a format: another one switched to (e.g. another quality) goes on with it
    }

    private void endResume() {
        mResumedAtMs = -1;
        mResumedBy = null;
        mResumeTrail.clear();
    }
}
