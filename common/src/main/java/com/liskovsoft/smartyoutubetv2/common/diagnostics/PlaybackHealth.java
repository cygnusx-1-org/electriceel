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
    /**
     * When it was last sent elsewhere, on the samples' clock (see #onSeek), -1 for not since the format was tried
     */
    private long mSeekMs = -1;
    private final ArrayDeque<long[]> mFrames = new ArrayDeque<>(); // {time, rendered, dropped}
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

        StartAudioClock(int timestampState, long framesMismatches, long clockMismatches, long holdReleases, long longestHoldMs) {
            this.timestampState = timestampState;
            this.framesMismatches = framesMismatches;
            this.clockMismatches = clockMismatches;
            this.holdReleases = holdReleases;
            this.longestHoldMs = longestHoldMs;
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

            if (!isSameVideo) {
                endResume();
            }
        }

        mVideoId = sample.videoId;

        List<String> result = new ArrayList<>();
        mIsPlaying = sample.isPlaying;
        long elapsedMs = mLastTickMs >= 0 ? Math.max(0, sample.nowMs - mLastTickMs) : 0;
        boolean wasPlaying = mLastTickMs >= 0 && mLastPositionMs >= 0;

        if (sample.stalls >= STALLS && sample.stallMs >= STALLS_MIN_MS) {
            report(result, KIND_STALLS);
        }

        if (!mIsStartMeasured) {
            addStartAudioClock(sample);
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
                mCatchUpEndMs = sample.nowMs + CATCH_UP_MS;
                mCatchUpDropped = -1;
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
     * {played, position, rendered, dropped, given to the decoder, dropped to a keyframe, audio timestamp state, audio playhead, audio
     * latency}
     */
    private static long[] toTrailRow(long playedMs, Sample sample) {
        return new long[]{playedMs, sample.positionMs, sample.renderedFrames, sample.droppedFrames, sample.inputBuffers,
                sample.droppedToKeyframes, sample.audioTimestampState, sample.audioPlayheadMs, sample.audioLatencyMs};
    }

    private void addStartAudioClock(Sample sample) {
        if (sample.audioTotalHoldReleases < 0) {
            return;
        }

        mStartFramesMismatches.add(sample.audioTotalFramesMismatches);
        mStartClockMismatches.add(sample.audioTotalClockMismatches);
        mStartHoldReleases.add(sample.audioTotalHoldReleases);
        mStartTimestampState = sample.audioTimestampState;
        mStartLongestHoldMs = Math.max(mStartLongestHoldMs, sample.audioLongestHoldMs);
    }

    @Nullable
    private StartAudioClock getStartAudioClock() {
        if (!mStartHoldReleases.isKnown()) {
            return null;
        }

        return new StartAudioClock(mStartTimestampState, mStartFramesMismatches.get(), mStartClockMismatches.get(), mStartHoldReleases.get(),
                mStartLongestHoldMs);
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
     * It plays for the first time since the player was prepared: the start (see #getStartTrail)
     */
    public void onStarted() {
        mInterruption = null;
        endResume();
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
            mCatchUpEndMs = nowMs + CATCH_UP_MS;
            mCatchUpDropped = -1;
        }
    }

    /**
     * Sent elsewhere (a seek of any kind): it stopped playing for it (see #onInterrupted), and the drops of its resume are its decoder
     * catching up (see #onResumed)
     */
    public void onSeek(long nowMs) {
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
     * Each sample of the start, oldest first: {played, position, rendered, dropped, given to the decoder, dropped to a keyframe, audio
     * timestamp state, audio playhead, audio latency}. Empty past it: it doesn't tell about what happens later.
     */
    public List<long[]> getStartTrail() {
        return mPlayedMs <= START_MS ? mStartTrail : new ArrayList<>();
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
        mCatchUpEndMs = -1;
        mCatchUpDropped = -1;
        mSeekMs = -1;
        mFrames.clear();
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
