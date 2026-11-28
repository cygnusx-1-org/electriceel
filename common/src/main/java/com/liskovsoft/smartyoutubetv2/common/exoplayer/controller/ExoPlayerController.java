package com.liskovsoft.smartyoutubetv2.common.exoplayer.controller;

import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.SystemClock;
import android.view.Surface;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioClockInfo;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.source.MediaSourceEventListener.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaSourceEventListener.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.sabr.SabrMediaSource;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifest;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifestParser;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.BuildConfig;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.Connections;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.FormatAttempts;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.LastState;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.LoadErrors;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlaybackHealth;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlaybackSpeed;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlayerCommands;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlayerFailure;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.RendererWaits;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.SilenceDetector;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.VideoDecoderState;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.VideoStarts;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.ExoMediaSourceFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.errors.LiveSegmentsGone;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.errors.TrackErrorFixer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.CensoredWords.Range;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.FrameStepper;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.KeyframeResume;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.RangeMuter;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.TunnelingFallback;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.VolumeBooster;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackInfoFormatter2;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.VideoTrack;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.ExoUtils;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer.DebugInfoMediaCodecVideoRenderer;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.json.JSONArray;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.List;

public class ExoPlayerController implements Player.EventListener {
    private static final String TAG = ExoPlayerController.class.getSimpleName();
    private final Context mContext;
    private final ExoMediaSourceFactory mMediaSourceFactory;
    private final TrackSelectorManager mTrackSelectorManager;
    private final TrackInfoFormatter2 mTrackFormatter;
    private final TrackErrorFixer mTrackErrorFixer;
    private final FrameStepper mFrameStepper;
    private final RangeMuter mRangeMuter;
    private boolean mOnSourceChanged;
    private WeakReference<Video> mVideo;
    private final PlayerEventListener mEventListener;
    private SimpleExoPlayer mPlayer;
    private PlayerView mPlayerView;
    private VolumeBooster mVolumeBooster;
    private boolean mIsEnded;
    private Runnable mOnVideoLoaded;
    // The volume set, kept while the range muter has the sound off
    private float mVolume = 1f;
    private boolean mIsMuted;
    private final PlaybackHealth mHealth = new PlaybackHealth();
    @Nullable
    private AudioClockInfo mAudioClockInfo;
    private final Runnable mHealthCheck = this::checkHealth;
    /**
     * When the next check of the playback is due ({@link SystemClock#elapsedRealtime()}), -1 for none: one that runs later tells how
     * late (see #postHealthCheck)
     */
    private long mHealthCheckDueMs = -1;
    /**
     * Where the player was when the seeks going on began (see AnalyticsListener#onSeekStarted), -1 when unknown. Main thread only.
     */
    private long mSeekFromMs = -1;
    /**
     * It played since the player was prepared with its source: playing again is a resume (see PlaybackHealth#onResumed), not the start
     */
    private boolean mHasPlayedSource;
    /**
     * Of the SABR source prepared, null for another source: its streams can be fetched again (see refreshSabr)
     */
    @Nullable
    private SabrManifest mSabrManifest;
    /**
     * The video has a surface to be shown on. Without one (e.g. the sound plays on in the background) no frame is rendered.
     */
    private boolean mHasSurface;
    /**
     * Of the player set (see setVideoRenderer): the frames it decoded to reach a position
     */
    @Nullable
    private DebugInfoMediaCodecVideoRenderer mVideoRenderer;
    /**
     * Of the resume going on (see #setResumePositionMs), till it goes back to its keyframe or another seek is made: -1 for none.
     * Main thread only.
     */
    private long mResumePositionMs = -1;
    /**
     * An audio segment finished loading since the resume's seek (see #maybeResumeFromKeyframe). Main thread only.
     */
    private boolean mIsAudioLoadedSinceResume;
    /**
     * The keyframe a resume didn't go back to while its audio loaded (see #maybeResumeFromKeyframe), and the resume's position: gone
     * back to once the audio is in, if the decoder is still short of the position. -1 for none. Main thread only.
     */
    private long mPendingKeyframeMs = -1;
    private long mPendingKeyframeResumeMs = -1;
    /**
     * A live stream's segments answer 404 on both tracks, none loaded since (see #onHttpError). Main thread only.
     */
    private final LiveSegmentsGone mLiveSegmentsGone = new LiveSegmentsGone();
    /**
     * How the formats tried do (see FormatAttempts): a frame shown means the video works, plus the decoders, the frames dropped
     * and the throughput of the segments
     */
    private final AnalyticsListener mDiagnosticsListener = new AnalyticsListener() {
        @Override
        public void onRenderedFirstFrame(EventTime eventTime, @Nullable Surface surface) {
            FormatAttempts.onVideoRendered();
            VideoStarts.onFirstFrame(getFirstFrame());
        }

        @Override
        public void onSurfaceSizeChanged(EventTime eventTime, int width, int height) {
            // 0x0 is none. A surface of a size not known yet is -1x-1.
            mHasSurface = width != 0 || height != 0;
        }

        @Override
        public void onDecoderInputFormatChanged(EventTime eventTime, int trackType, Format format) {
            // What the decoder plays, when the tracks didn't tell it (see FormatAttempts#onTracks)
            if (trackType == C.TRACK_TYPE_VIDEO) {
                FormatAttempts.onVideoFormat(format);
            }
        }

        @Override
        public void onDecoderInitialized(EventTime eventTime, int trackType, String decoderName, long initializationDurationMs) {
            if (trackType == C.TRACK_TYPE_VIDEO || trackType == C.TRACK_TYPE_AUDIO) {
                FormatAttempts.onDecoder(trackType == C.TRACK_TYPE_VIDEO, decoderName);
                VideoStarts.onDecoder(trackType == C.TRACK_TYPE_VIDEO, decoderName, initializationDurationMs);
            }
        }

        @Override
        public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
            FormatAttempts.onDroppedFrames(droppedFrames);
        }

        /**
         * Before the seek's discontinuity (see #onPositionDiscontinuity), once till it's processed: a seek made meanwhile is from here
         * too
         */
        @Override
        public void onSeekStarted(EventTime eventTime) {
            mSeekFromMs = eventTime.currentPlaybackPositionMs;
        }

        @Override
        public void onLoadStarted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
            // Asked of, here: one that never answers has a server too. Not the subtitles': their server isn't the video's.
            if (isTimedLoad(mediaLoadData) && (isVideoLoad(mediaLoadData) || mediaLoadData.trackType == C.TRACK_TYPE_AUDIO)) {
                FormatAttempts.onRequest(loadEventInfo.uri != null ? loadEventInfo.uri.getHost() : null);
            }

            if (isTimedLoad(mediaLoadData)) {
                VideoStarts.onLoadStarted(mediaLoadData.dataType == C.DATA_TYPE_MEDIA_INITIALIZATION, isVideoLoad(mediaLoadData),
                        mediaLoadData.trackType == C.TRACK_TYPE_AUDIO, loadEventInfo.elapsedRealtimeMs);
            }
        }

        @Override
        public void onLoadCompleted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
            // Muxed (e.g. the URL list's) has the audio too
            if (mediaLoadData.dataType == C.DATA_TYPE_MEDIA
                    && (mediaLoadData.trackType == C.TRACK_TYPE_AUDIO || mediaLoadData.trackType == C.TRACK_TYPE_DEFAULT)) {
                mIsAudioLoadedSinceResume = true;
                maybeResumeFromPendingKeyframe();
            }

            if (mediaLoadData.dataType == C.DATA_TYPE_MEDIA) {
                FormatAttempts.onLoad(loadEventInfo.bytesLoaded, loadEventInfo.loadDurationMs, isVideoLoad(mediaLoadData),
                        mediaLoadData.trackType == C.TRACK_TYPE_AUDIO, mediaLoadData.mediaStartTimeMs, mediaLoadData.mediaEndTimeMs);

                // The track's 404s before it were the live edge's. Muxed has the audio too.
                if (isVideoLoad(mediaLoadData)) {
                    mLiveSegmentsGone.onLoaded(true);
                }

                if (mediaLoadData.trackType == C.TRACK_TYPE_AUDIO || mediaLoadData.trackType == C.TRACK_TYPE_DEFAULT) {
                    mLiveSegmentsGone.onLoaded(false);
                }
            }

            if (isTimedLoad(mediaLoadData)) {
                VideoStarts.onLoad(mediaLoadData.dataType == C.DATA_TYPE_MEDIA_INITIALIZATION, isVideoLoad(mediaLoadData),
                        mediaLoadData.trackType == C.TRACK_TYPE_AUDIO, loadEventInfo.elapsedRealtimeMs - loadEventInfo.loadDurationMs,
                        loadEventInfo.elapsedRealtimeMs, loadEventInfo.bytesLoaded);
            }
        }

        /**
         * A load cut off while the video starts, e.g. by a seek: the start's row for it (2M1DY5CD -BwLZBMKW4g: nothing was recorded
         * loading between a live start's init chunks and its third jump to the live end, whose media loads then needed new connections)
         */
        @Override
        public void onLoadCanceled(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
            if (isTimedLoad(mediaLoadData)) {
                VideoStarts.onLoadCanceled(mediaLoadData.dataType == C.DATA_TYPE_MEDIA_INITIALIZATION, isVideoLoad(mediaLoadData),
                        mediaLoadData.trackType == C.TRACK_TYPE_AUDIO, loadEventInfo.elapsedRealtimeMs - loadEventInfo.loadDurationMs,
                        loadEventInfo.elapsedRealtimeMs, loadEventInfo.bytesLoaded);
            }
        }

        /**
         * The player tries it again after a wait, or fails: either is a gap in the start without it (see LoadErrors)
         */
        @Override
        public void onLoadError(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData, IOException error,
                                boolean wasCanceled) {
            LoadErrors.onLoadError(loadEventInfo.elapsedRealtimeMs - loadEventInfo.loadDurationMs, loadEventInfo.elapsedRealtimeMs,
                    mediaLoadData.dataType, mediaLoadData.trackType, error);
        }

        /**
         * A segment of the media or its initialization (see VideoStarts)
         */
        private boolean isTimedLoad(MediaLoadData mediaLoadData) {
            return mediaLoadData.dataType == C.DATA_TYPE_MEDIA || mediaLoadData.dataType == C.DATA_TYPE_MEDIA_INITIALIZATION;
        }

        /**
         * Muxed (e.g. the URL list's) is video too
         */
        private boolean isVideoLoad(MediaLoadData mediaLoadData) {
            return mediaLoadData.trackType == C.TRACK_TYPE_VIDEO || mediaLoadData.trackType == C.TRACK_TYPE_DEFAULT;
        }
    };

    /**
     * The player is a card's preview (see EmbedPlayerView), not the one the user watches: told in its failures
     */
    private boolean mIsEmbed;

    /**
     * Of the embed player's controller, right after it's made
     */
    public void setEmbed(boolean isEmbed) {
        mIsEmbed = isEmbed;
    }

    public ExoPlayerController(Context context, PlayerEventListener eventListener) {
        PlayerTweaksData playerTweaksData = PlayerTweaksData.instance(context);
        mContext = context.getApplicationContext();
        mMediaSourceFactory = new ExoMediaSourceFactory(context);
        mTrackSelectorManager = new TrackSelectorManager(context);
        mTrackFormatter = new TrackInfoFormatter2();
        mTrackFormatter.enableBitrate(PlayerTweaksData.instance(context).isQualityInfoBitrateEnabled());
        mTrackErrorFixer = new TrackErrorFixer(mTrackSelectorManager);
        mTrackErrorFixer.setOnHttpError(this::onHttpError);
        mTrackErrorFixer.setLiveCheck(() -> {
            // Held weakly: read once
            Video video = getVideo();
            return video != null && video.isLive;
        });
        mFrameStepper = new FrameStepper(this::onFramePositionChanged);
        mRangeMuter = new RangeMuter(this::onMuteChanged);

        mMediaSourceFactory.setTrackErrorFixer(mTrackErrorFixer);
        mEventListener = eventListener;
        // A SABR stream's new session, swapped in while it plays: its formats' streams expire later
        mMediaSourceFactory.setStreamsRefreshListener(mEventListener::onStreamsRefreshed);

        applyShield720pFix();
        VideoTrack.sIsAltPresetsEnabled = playerTweaksData.isAltPresetsEnabled();
        MediaTrack.setAvcOverVp9Preferred(playerTweaksData.isAvcOverVp9Preferred());
        TrackSelectorUtil.setVoiceBoostMark(context.getString(R.string.audio_voice_boost));
    }

    /**
     * To the player failure log: ExoPlayer retries the segment, a fatal one comes again as an engine error
     */
    private void onHttpError(int responseCode, String track, @Nullable String host, @Nullable Format failed, boolean switched) {
        // Where the formats came from, as for the other failures: a SABR stream's HTTP 401s told neither (CX17FXZW #1-#4)
        PlayerFailure failure = PlayerFailure.create(mContext, PlayerFailure.KIND_TRACK_LOAD_ERROR)
                .video(getVideo())
                .embed(mIsEmbed)
                .source(FormatAttempts.getClient(), FormatAttempts.getProtocol())
                .http(responseCode, track, host, failed);

        if (mPlayer != null) {
            failure.position(mPlayer.getCurrentPosition(), mPlayer.getDuration())
                    .formats(getVideoFormat(), getAudioFormat());
        }

        if (switched) {
            failure.action("select_other_codec");

            // Given up on: ExoPlayer retries the segments of one it hasn't
            if (PlayerFailure.TRACK_AUDIO.equals(track) || PlayerFailure.TRACK_VIDEO.equals(track)) {
                FormatAttempts.onStreamFailed(PlayerFailure.TRACK_VIDEO.equals(track));
            }
        }

        long goneMs = getLiveSegmentsGoneMs(responseCode, track);

        try {
            if (goneMs >= 0) {
                // A fix, with its actions recorded (see ErrorFixerController#onLiveSegmentsGone)
                failure.detail("liveSegmentsGoneMs", goneMs);
                mEventListener.onLiveSegmentsGone(failure);
            }
        } finally {
            failure.record();
        }
    }

    /**
     * Of a live stream's 404 on its audio or video: how long the segments of both tracks have been gone (see LiveSegmentsGone), -1
     * when they aren't
     */
    private long getLiveSegmentsGoneMs(int responseCode, String track) {
        Video video = getVideo();
        boolean isVideo = PlayerFailure.TRACK_VIDEO.equals(track);

        if (responseCode != 404 || video == null || !video.isLive || !isVideo && !PlayerFailure.TRACK_AUDIO.equals(track)) {
            return -1;
        }

        return mLiveSegmentsGone.onNotFound(video.videoId, isVideo, SystemClock.elapsedRealtime());
    }

    /**
     * The failures that throw no error (see PlaybackHealth): the video never shows or freezes, frames drop, it stalls again and
     * again, the sound is silent
     */
    private void checkHealth() {
        if (mPlayer == null) {
            return;
        }

        try {
            PlaybackHealth.Sample sample = new PlaybackHealth.Sample();
            sample.nowMs = SystemClock.elapsedRealtime();
            sample.tickLateMs = mHealthCheckDueMs >= 0 ? Math.max(0, sample.nowMs - mHealthCheckDueMs) : -1;
            sample.attempt = FormatAttempts.getAttemptId();
            sample.videoId = FormatAttempts.getVideoId();
            sample.isPlaying = mPlayer.getPlayWhenReady() && mPlayer.getPlaybackState() == Player.STATE_READY;
            // Not buffering: a freeze it ends is the user's doing, or the app's (see PlaybackHealth#onSample)
            sample.isPaused = !mPlayer.getPlayWhenReady() && mPlayer.getPlaybackState() == Player.STATE_READY;
            sample.positionMs = mPlayer.getCurrentPosition();
            // Not shown, the video isn't checked
            sample.hasVideo = mPlayer.getVideoFormat() != null && mHasSurface;
            sample.hasAudio = mPlayer.getAudioFormat() != null;
            sample.isMuted = mIsMuted || mPlayer.getVolume() == 0;
            sample.silentMs = SilenceDetector.getSilentMs();
            sample.soundBuffers = SilenceDetector.getSoundBuffers();
            sample.stalls = FormatAttempts.getStalls();
            sample.stallMs = FormatAttempts.getStallMs();
            sample.playedMs = FormatAttempts.getPlayedMs();

            if (mAudioClockInfo != null) {
                sample.audioTimestampState = mAudioClockInfo.timestampState;
                sample.audioPlayheadMs = mAudioClockInfo.playheadMs;
                sample.audioLatencyMs = mAudioClockInfo.latencyMs;
                sample.audioLongestHoldMs = mAudioClockInfo.longestHoldMs;
                sample.audioTotalFramesMismatches = mAudioClockInfo.totalFramesMismatches;
                sample.audioTotalClockMismatches = mAudioClockInfo.totalClockMismatches;
                sample.audioTotalHoldReleases = mAudioClockInfo.totalHoldReleases;
                sample.audioTotalHoldSkips = mAudioClockInfo.totalHoldSkips;
            }

            DecoderCounters counters = mPlayer.getVideoDecoderCounters();

            if (counters != null) {
                counters.ensureUpdated();

                // Unknown, not none: a tunneled decoder that doesn't report each frame it shows (see isRenderedCountKnown)
                if (mVideoRenderer == null || mVideoRenderer.isRenderedCountKnown()) {
                    sample.renderedFrames = counters.renderedOutputBufferCount;
                    sample.droppedFrames = counters.droppedBufferCount;
                    sample.droppedToKeyframes = counters.droppedToKeyframeCount;
                }

                sample.inputBuffers = counters.inputBufferCount;
            }

            // For the failures recorded elsewhere and the fixes that depend on it (e.g. a long stall's, see ErrorFixerController)
            DebugInfoMediaCodecVideoRenderer renderer = mVideoRenderer;
            sample.videoBufferedAheadMs = getVideoBufferedAheadMs(renderer, sample.positionMs);
            boolean isTunneling = renderer != null && renderer.isTunneling();
            VideoDecoderState.onSample(sample.inputBuffers, sample.renderedFrames, isTunneling,
                    renderer != null ? renderer.getTunneledFrames() : null);

            // The renderer goes on with the same video when only the audio is switched: its first frame isn't rendered again.
            // Its counters start over each time it's enabled, so frames rendered are of the format tried.
            if (sample.hasVideo && sample.renderedFrames > 0 && !FormatAttempts.isVideoRendered()) {
                FormatAttempts.onVideoRendered();
            }

            sample.isVideoRendered = FormatAttempts.isVideoRendered();

            for (String kind : mHealth.onSample(sample)) {
                recordHealthFailure(kind, sample);
            }

            long soundBackMs = mHealth.takeSoundBackMs();

            if (soundBackMs >= 0) {
                FormatAttempts.onSoundBack(soundBackMs);
            }

            onStartFrames(mHealth.takeStartFrames());
            // For an app exit to tell (see AppExits)
            LastState.onPlayer(mContext, true, sample.isPlaying);
        } catch (RuntimeException e) {
            Log.e(TAG, "Can't check the playback: " + e.getMessage());
        } finally {
            if (mPlayer != null) {
                postHealthCheck(mHealth.getTickMs());
            }
        }
    }

    /**
     * The next check of the playback (see #checkHealth), in that long: when it's due, for it to tell how late it ran. 500 ms apart at
     * the start, T26RECBZ #1 had no sample for 2.1 s: the main thread was held up, and nothing said so.
     */
    private void postHealthCheck(long delayMs) {
        mHealthCheckDueMs = SystemClock.elapsedRealtime() + delayMs;
        Utils.postDelayed(mHealthCheck, delayMs);
    }

    /**
     * Of the video track alone, how far past the position its samples are loaded (see
     * DebugInfoMediaCodecVideoRenderer#getBufferedAheadUs): the player's buffered position is the least of its tracks. -1 when unknown.
     */
    private long getVideoBufferedAheadMs(@Nullable DebugInfoMediaCodecVideoRenderer renderer, long positionMs) {
        long aheadUs = renderer != null ? renderer.getBufferedAheadUs() : C.TIME_UNSET;

        if (aheadUs == C.TIME_END_OF_SOURCE) {
            // Loaded to its end
            long durationMs = getDurationMs();

            return durationMs >= 0 ? Math.max(0, durationMs - positionMs) : -1;
        }

        // Behind the position is none ahead
        return aheadUs != C.TIME_UNSET ? Math.max(0, aheadUs / 1_000) : -1;
    }

    private void recordHealthFailure(String kind, PlaybackHealth.Sample sample) {
        PlayerFailure failure = PlayerFailure.create(mContext, kind)
                .video(getVideo())
                .embed(mIsEmbed)
                .position(sample.positionMs, mPlayer.getDuration())
                .formats(getVideoFormat(), getAudioFormat())
                .source(FormatAttempts.getClient(), FormatAttempts.getProtocol())
                .detail("renderedFrames", sample.renderedFrames)
                .detail("droppedFrames", sample.droppedFrames)
                .detail("droppedToKeyframes", sample.droppedToKeyframes)
                .detail("inputBuffers", sample.inputBuffers)
                .detail("bufferedMs", mPlayer.getBufferedPosition() - sample.positionMs)
                .detail("silentMs", sample.silentMs)
                .detail("stalls", sample.stalls)
                .detail("stallMs", sample.stallMs)
                .detail("playedMs", sample.playedMs);

        if (PlaybackHealth.KIND_START_FROZEN.equals(kind)) {
            failure.detail("frozenMs", mHealth.getStartFrozenMs());
            failure.detail("clockStill", mHealth.isStartClockStill());
        }

        // The position stands still with the picture: the clock's, as a start freeze whose clock stood still (see above)
        boolean isClockStuck = PlaybackHealth.KIND_VIDEO_FROZEN.equals(kind) && mHealth.isClockStuck();

        if (isClockStuck) {
            failure.detail("frozenMs", mHealth.getClockStillMs());
            failure.detail("clockStill", true);
        }

        // Paused on a picture standing still: it didn't end by itself, the pause ended it (see PlaybackHealth#getPauseFrozenMs)
        long pauseFrozenMs = mHealth.getPauseFrozenMs();

        if (pauseFrozenMs >= 0 && (PlaybackHealth.KIND_START_FROZEN.equals(kind) || PlaybackHealth.KIND_VIDEO_FROZEN.equals(kind))) {
            failure.detail("frozenMs", pauseFrozenMs);
            failure.detail("endedByPause", true);
        }

        // A frozen picture whose clock moved on has them too: 7 freezes of a tunneled decoder had none, nothing told whether the frames
        // after a seek or a codec change were ever shown (CGHFJ3BZ, 18CK8PQ0, 0YX6EKC1)
        if (PlaybackHealth.KIND_START_FROZEN.equals(kind) || PlaybackHealth.KIND_FRAME_DROPS.equals(kind)
                || PlaybackHealth.KIND_VIDEO_FROZEN.equals(kind)) {
            JSONArray trail = toJson(mHealth.getStartTrail());

            if (trail.length() > 0) {
                failure.detail("startTrail", trail);
            }

            // Past the start too: the audio clock starts over after a seek, a pause or buffering
            JSONArray resumeTrail = toJson(mHealth.getResumeTrail());

            if (resumeTrail.length() > 0) {
                failure.detail("resumeTrail", resumeTrail);
                failure.detail("resumedBy", mHealth.getResumedBy());
            }

            if (mAudioClockInfo != null) {
                failure.detail("audioFramesMismatches", mAudioClockInfo.framesMismatches);
                failure.detail("audioClockMismatches", mAudioClockInfo.clockMismatches);
                failure.detail("audioBadLatencies", mAudioClockInfo.badLatencies);
                failure.detail("audioHoldReleases", mAudioClockInfo.holdReleases);
                failure.detail("audioLongestHoldMs", mAudioClockInfo.longestHoldMs);
                failure.detail("audioHoldSkips", mAudioClockInfo.holdSkips);
            }
        }

        if (PlaybackHealth.KIND_FRAME_DROPS.equals(kind)) {
            // The drops' window, and whether they're one skip to a keyframe rather than ones that go on (PQTQ5DCK: 1.8% of the frames,
            // all of them one skip)
            failure.detail("oneSkip", mHealth.isDropsOneSkip());
            failure.detail("dropsWindow", toJson(mHealth.getDropsWindow(sample.nowMs)));
            // Counted from where a start's or a resume's decoder caught up to its position: drops that went on past it, not of it
            // (MD8RQZM2 #1: a catch-up ended too soon, its drops were counted)
            failure.detail("afterCatchUp", mHealth.isDropsAfterCatchUp());
        }

        // A tunneled decoder shows the frames itself: where it's at against the position (see TunneledFrames)
        VideoDecoderState.addTunnelingDetails(failure);

        // Silent for all it played (the stream's), or for a long stretch of a sound that came (a quiet part: the video's): the fix is of the
        // first (see ErrorFixerController#applySilentAudioFallback)
        if (PlaybackHealth.KIND_AUDIO_SILENT.equals(kind)) {
            failure.detail("silentFromStart", PlaybackHealth.isSilentFromStart(sample));
        }

        // The stream that didn't work: frame drops and stalls play, worse. A clock that stands still isn't the video stream's.
        if (PlaybackHealth.KIND_NO_VIDEO.equals(kind) || PlaybackHealth.KIND_VIDEO_FROZEN.equals(kind) && !isClockStuck) {
            FormatAttempts.onStreamFailed(true);
        } else if (PlaybackHealth.KIND_AUDIO_SILENT.equals(kind) && PlaybackHealth.isSilentFromStart(sample)) {
            FormatAttempts.onStreamFailed(false);
        }

        try {
            // A fix, with its actions recorded (see ErrorFixerController#onHealthFailure)
            mEventListener.onHealthFailure(kind, failure);
        } finally {
            // Recorded as before whatever the fix did
            failure.record();
        }
    }

    /**
     * The first play since the source was prepared is the start. Past it, a play is after a seek, a pause or buffering: its audio
     * clock started over, the seconds from here are sampled closely as the start's are.
     */
    private void onHealthPlaying() {
        if (!mHasPlayedSource) {
            mHasPlayedSource = true;

            // Prepared again past the start of the format, its seconds from here are a resume's (see PlaybackHealth#onStarted)
            if (!mHealth.onStarted(SystemClock.elapsedRealtime())) {
                return;
            }
        } else {
            mHealth.onResumed(SystemClock.elapsedRealtime());
        }

        if (mPlayer != null) {
            // Where it is as it plays again: the trail's first sample
            Utils.removeCallbacks(mHealthCheck);
            postHealthCheck(0);
        }
    }

    private static void onStartFrames(@Nullable PlaybackHealth.StartFrames frames) {
        if (frames != null) {
            VideoStarts.onFrames(frames);
        }
    }

    private static JSONArray toJson(List<long[]> rows) {
        JSONArray result = new JSONArray();

        for (long[] row : rows) {
            JSONArray item = new JSONArray();

            for (long value : row) {
                item.put(value);
            }

            result.put(item);
        }

        return result;
    }

    private void applyShield720pFix() {
        PlayerData playerData = PlayerData.instance(mContext);
        mTrackSelectorManager.selectTrack(FormatItem.toMediaTrack(playerData.getFormat(FormatItem.TYPE_VIDEO)));
        mTrackSelectorManager.selectTrack(FormatItem.toMediaTrack(playerData.getFormat(FormatItem.TYPE_AUDIO)));
        mTrackSelectorManager.selectTrack(FormatItem.toMediaTrack(playerData.getFormat(FormatItem.TYPE_SUBTITLE)));
    }

    public void openSabr(MediaItemFormatInfo formatInfo) {
        MediaSource mediaSource = buildSource(() -> mMediaSourceFactory.fromSabrFormatInfo(formatInfo));
        openMediaSource(mediaSource);
    }

    /**
     * The streams of the SABR video playing, fetched again before they expire: the source goes on with them (see
     * SabrManifestParser#refresh), its buffer plays on
     *
     * @return whether they were put in use: not when another source plays, or they don't fit it (e.g. other formats)
     */
    public boolean refreshSabr(MediaItemFormatInfo formatInfo) {
        SabrManifest manifest = mSabrManifest;

        return mPlayer != null && manifest != null && SabrManifestParser.refresh(manifest, formatInfo);
    }

    public void openDash(MediaItemFormatInfo formatInfo) {
        MediaSource mediaSource = buildSource(() -> mMediaSourceFactory.fromDashFormatInfo(formatInfo));
        openMediaSource(mediaSource);
    }

    public void openDash(InputStream dashManifest) {
        MediaSource mediaSource = buildSource(() -> mMediaSourceFactory.fromDashManifest(dashManifest));
        openMediaSource(mediaSource);
    }

    public void openDashUrl(String dashManifestUrl) {
        MediaSource mediaSource = buildSource(() -> mMediaSourceFactory.fromDashManifestUrl(dashManifestUrl));
        openMediaSource(mediaSource);
    }

    public void openHlsUrl(String hlsPlaylistUrl) {
        MediaSource mediaSource = buildSource(() -> mMediaSourceFactory.fromHlsPlaylist(hlsPlaylistUrl));
        openMediaSource(mediaSource);
    }

    public void openUrlList(List<String> urlList) {
        MediaSource mediaSource = buildSource(() -> mMediaSourceFactory.fromUrlList(urlList));
        openMediaSource(mediaSource);
    }

    public void openMerged(MediaItemFormatInfo formatInfo, String hlsPlaylistUrl) {
        MediaSource dashMediaSource = buildSource(() -> mMediaSourceFactory.fromDashFormatInfo(formatInfo));
        MediaSource hlsMediaSource = buildSource(() -> mMediaSourceFactory.fromHlsPlaylist(hlsPlaylistUrl));
        openMediaSource(new MergingMediaSource(dashMediaSource, hlsMediaSource));
    }

    public void openMerged(InputStream dashManifest, String hlsPlaylistUrl) {
        MediaSource dashMediaSource = buildSource(() -> mMediaSourceFactory.fromDashManifest(dashManifest));
        MediaSource hlsMediaSource = buildSource(() -> mMediaSourceFactory.fromHlsPlaylist(hlsPlaylistUrl));
        openMediaSource(new MergingMediaSource(dashMediaSource, hlsMediaSource));
    }

    /**
     * Its time, a step of the start's open stage (see VideoStarts#timeOpenValue)
     */
    private static MediaSource buildSource(VideoStarts.Step<MediaSource> build) {
        return VideoStarts.timeOpenValue(VideoStarts.STAGE_MEDIA_SOURCE, null, build);
    }

    private void openMediaSource(MediaSource mediaSource) {
        mResumePositionMs = -1; // the previous source's
        mPendingKeyframeMs = -1;
        mFrameStepper.reset();
        mRangeMuter.reset();
        resetPlayerState(); // fixes occasional video artifacts and problems with quality switching
        setQualityInfo("");

        mTrackSelectorManager.setMergedSource(mediaSource instanceof MergingMediaSource);
        mTrackSelectorManager.invalidate();
        mOnSourceChanged = true;
        Video video = getVideo();
        // Each controller's part, of those that take long (see PlaybackPresenter#onSourceChanged)
        VideoStarts.timeOpenStep(VideoStarts.STAGE_SOURCE_CHANGED, null, () -> mEventListener.onSourceChanged(video));
        // The engine its factory was built with: the settings may give another by now (see ExoMediaSourceFactory#getBuiltEngine)
        VideoStarts.onPrepare(mMediaSourceFactory.getBuiltEngine());
        FormatAttempts.onPrepare();
        // The same video again (a reload) goes on with the loads that failed before it
        LoadErrors.onPrepare(video != null ? video.videoId : null);
        RendererWaits.onPrepare();
        Connections.onPrepare();
        mHealth.onPrepare();
        mHasPlayedSource = false;
        mSabrManifest = mediaSource instanceof SabrMediaSource ? ((SabrMediaSource) mediaSource).getManifest() : null;
        mPlayer.prepare(mediaSource);
        // Where its seeks are from till one is told (see AnalyticsListener#onSeekStarted): one of the source before may not be done
        mSeekFromMs = mPlayer.getCurrentPosition();
        // Its first seconds are looked at closely: the check may be 2 s away, past its first frame
        Utils.removeCallbacks(mHealthCheck);
        postHealthCheck(PlaybackHealth.START_TICK_MS);
    }

    public long getPositionMs() {
        if (mPlayer == null) {
            return -1;
        }

        long framePositionMs = mFrameStepper.getPositionMs();

        return framePositionMs != -1 ? framePositionMs : mPlayer.getCurrentPosition();
    }

    /**
     * NOTE: Pos gathered from content block data may slightly exceed video duration
     * (e.g. 302200 when duration is 302000).
     */
    public void setPositionMs(long positionMs) {
        mResumePositionMs = -1; // another seek: the resume's is done with
        mPendingKeyframeMs = -1;

        // Url list videos at load stage has undefined (-1) length. So, we need to remove length check.
        if (canSeekTo(positionMs)) {
            PlayerCommands.onSeek(PlayerCommands.SOURCE_APP, positionMs);
            // A live one's says where it was and the live end, which a jump to the end went by (2M1DY5CD -BwLZBMKW4g): the end as the
            // player's screen gives it (see PlaybackFragment#getDurationMs), the video's live duration for a SABR window longer than a
            // day, not the window's 48 h
            Video video = getVideo();
            boolean isLive = video != null && video.isLive;
            long liveEndMs = -1;

            if (isLive) {
                long durationMs = getDurationMs();
                long liveDurationMs = video.getLiveDurationMs();
                liveEndMs = durationMs > Video.MAX_LIVE_DURATION_MS && liveDurationMs != 0 ? liveDurationMs : durationMs;
            }

            VideoStarts.onSeek(positionMs, isLive ? mPlayer.getCurrentPosition() : -1, liveEndMs);
            mPlayer.seekTo(positionMs);
        }
    }

    /**
     * A resume (see VideoStateController): played from the keyframe before the position, once it's known (see #onDecodedFromKeyframe).
     * Before, every frame from that keyframe to the position was decoded unseen first (AWHPECZK zTkEMVxv6F8: 1661 ms for 210 frames
     * of a 4K stream on a Shield). Its keyframes aren't known before the seek (SABR has no segment index, DASH loads it later), so
     * SeekParameters.PREVIOUS_SYNC can't do it, and a global one would make SponsorBlock skip again and again. Only when the decoder
     * would take longer to get to the position than going back costs (see #maybeResumeFromKeyframe).
     */
    public void setResumePositionMs(long positionMs) {
        mIsAudioLoadedSinceResume = false;
        setPositionMs(positionMs);
        mResumePositionMs = canSeekTo(positionMs) ? positionMs : -1;
    }

    private boolean canSeekTo(long positionMs) {
        return mPlayer != null && positionMs >= 0 && positionMs <= getDurationMs();
    }

    /**
     * On the playback thread: the decoder started from this keyframe to reach the position (see DebugInfoMediaCodecVideoRenderer)
     */
    private void onDecodedFromKeyframe(long keyframeUs, long positionUs) {
        Utils.post(() -> maybeResumeFromKeyframe(keyframeUs, positionUs));
    }

    /**
     * The resume goes back to the keyframe: its frames are shown from there at once. Inside the buffer: the samples from the keyframe
     * before the position are kept (see ExoPlayerInitializer#createLoadControl).
     */
    private void maybeResumeFromKeyframe(long keyframeUs, long positionUs) {
        long resumeMs = mResumePositionMs;

        if (resumeMs < 0) {
            return;
        }

        // Decoded to another position: not the resume's (e.g. the video track enabled later, where a resume had none). Its start says
        // so: a resume's decision went to the log only (see VideoStarts#onKeyframeResume).
        if (Math.abs(positionUs / 1_000 - resumeMs) > 1) {
            VideoStarts.onKeyframeResume(VideoStarts.KEYFRAME_OTHER_POSITION);
            return;
        }

        mResumePositionMs = -1;
        // At or just past it, in ms: a seek before it would find no keyframe in the buffer, and load the segment again
        long keyframeMs = (keyframeUs + 999) / 1_000;

        if (mPlayer == null || keyframeUs < 0 || resumeMs <= keyframeMs) {
            return;
        }

        if (!isKeyframeWorthGoingBack(resumeMs, keyframeMs, false)) {
            return;
        }

        // The audio's first segment still loading: the seek back drops it and asks for the audio again (CX17FXZW 18:04:16: 1.65 s more
        // for it, the start took 4.6 s, where the other resumes played 54-116 ms after their seek). The decoder goes on to the position.
        // A segment's times don't tell where the audio starts: SABR's are of the segment before, or none, till one comes.
        if (!mIsAudioLoadedSinceResume) {
            // Gone back to once it's in, if the decoder hasn't reached the position by then (V9H5DRS0 20:21:31: the audio came 333 ms
            // later, then 1.9 s more went to 228 4K frames; F0SVV9N8 18:32:49: 4 ms later, then 1.5 s)
            Log.d(TAG, "Resume at %s ms, not from the keyframe at %s ms yet: its audio is still loading", resumeMs, keyframeMs);
            VideoStarts.onKeyframeResume(VideoStarts.KEYFRAME_AUDIO_LOADING);
            mPendingKeyframeMs = keyframeMs;
            mPendingKeyframeResumeMs = resumeMs;
            return;
        }

        Log.d(TAG, "Resume from the keyframe at %s ms, not %s ms", keyframeMs, resumeMs);
        PlayerCommands.onSeek(PlayerCommands.SOURCE_APP, keyframeMs);
        VideoStarts.onSeek(keyframeMs);
        mPlayer.seekTo(keyframeMs);
        // After its seek, which ends the decision before
        VideoStarts.onKeyframeResume(VideoStarts.KEYFRAME_GONE_BACK);
    }

    /**
     * The audio of a resume came in after its keyframe was known (see #maybeResumeFromKeyframe): back to the keyframe, if the decoder is
     * still on its way to the position. Not once the player is ready there: its first frame is up.
     */
    private void maybeResumeFromPendingKeyframe() {
        long keyframeMs = mPendingKeyframeMs;
        long resumeMs = mPendingKeyframeResumeMs;
        mPendingKeyframeMs = -1;
        mPendingKeyframeResumeMs = -1;

        if (mPlayer == null || keyframeMs < 0 || mPlayer.getPlaybackState() == Player.STATE_READY
                || Math.abs(mPlayer.getCurrentPosition() - resumeMs) > 1) {
            return;
        }

        // Asked again now: the decoder went on while the audio loaded, and may be at the position or nearly
        if (!isKeyframeWorthGoingBack(resumeMs, keyframeMs, true)) {
            return;
        }

        Log.d(TAG, "Resume from the keyframe at %s ms, not %s ms, its audio in", keyframeMs, resumeMs);
        PlayerCommands.onSeek(PlayerCommands.SOURCE_APP, keyframeMs);
        VideoStarts.onSeek(keyframeMs);
        mPlayer.seekTo(keyframeMs);
        VideoStarts.onKeyframeResume(VideoStarts.describeKeyframeDecision(VideoStarts.KEYFRAME_GONE_BACK, true));
    }

    /**
     * Going back to the keyframe gets the resume's first frame up sooner than the decoder's way to the position (see KeyframeResume):
     * not once the decoder is there. Why not goes to the start too (see VideoStarts#onKeyframeResume).
     *
     * @param isAfterAudio asked again once the resume's audio came in (see #maybeResumeFromPendingKeyframe)
     */
    private boolean isKeyframeWorthGoingBack(long resumeMs, long keyframeMs, boolean isAfterAudio) {
        DebugInfoMediaCodecVideoRenderer renderer = mVideoRenderer;

        // This runs after the main thread's other work, and the decoder went on meanwhile: 292 ms later, the 58 frames to the position
        // were decoded and its frame shown. Going back then threw it away, showed 1:19 after 1:20, and played 282 ms later (6EZTNXBF).
        if (renderer != null && renderer.hasReachedPosition()) {
            Log.d(TAG, "Resume at %s ms, not from the keyframe at %s ms: the decoder is there", resumeMs, keyframeMs);
            VideoStarts.onKeyframeResume(VideoStarts.describeKeyframeDecision(VideoStarts.KEYFRAME_DECODER_THERE, isAfterAudio));
            return false;
        }

        Format format = mPlayer.getVideoFormat();
        long skipStartMs = renderer != null ? renderer.getSkipStartMs() : -1;
        long gapMs = resumeMs - keyframeMs;
        float frameRate = format != null ? format.frameRate : Format.NO_VALUE;
        int height = format != null ? format.height : Format.NO_VALUE;
        int decodedFrames = renderer != null ? renderer.getSkippedToPosition() : 0;
        long decodingMs = skipStartMs >= 0 ? SystemClock.elapsedRealtime() - skipStartMs : 0;

        // A keyframe the decoder gets to the position from sooner than going back costs isn't gone back to (see KeyframeResume)
        if (!KeyframeResume.isWorthIt(gapMs, frameRate, height, decodedFrames, decodingMs)) {
            Log.d(TAG, "Resume at %s ms, not from the keyframe at %s ms: the decoder gets there sooner", resumeMs, keyframeMs);
            VideoStarts.onKeyframeResume(VideoStarts.describeKeyframeNotWorthIt(
                    KeyframeResume.getRemainingMs(gapMs, frameRate, height, decodedFrames, decodingMs), isAfterAudio));
            return false;
        }

        return true;
    }

    public long getBufferedPositionMs() {
        return mPlayer != null ? mPlayer.getBufferedPosition() : -1;
    }

    public long getDurationMs() {
        if (mPlayer == null) {
            return -1;
        }

        long duration = mPlayer.getDuration();
        return duration != C.TIME_UNSET ? duration : -1;
    }

    public void setPlayWhenReady(boolean play) {
        if (mPlayer != null) {
            if (mPlayer.getPlayWhenReady() != play) {
                PlayerCommands.onPlayWhenReady(play, PlayerCommands.SOURCE_APP, mPlayer.getCurrentPosition());
            }

            mPlayer.setPlayWhenReady(play);
        }
    }

    public boolean getPlayWhenReady() {
        if (mPlayer == null) {
            return false;
        }

        return mPlayer.getPlayWhenReady();
    }

    public boolean isPlaying() {
        return ExoUtils.isPlaying(mPlayer);
    }
    
    public boolean isLoading() {
        return ExoUtils.isLoading(mPlayer);
    }

    public boolean isBuffering() {
        return ExoUtils.isBuffering(mPlayer);
    }
    
    public boolean containsMedia() {
        if (mPlayer == null) {
            return false;
        }

        return mPlayer.getPlaybackState() != Player.STATE_IDLE;
    }
    
    public void release() {
        mTrackSelectorManager.release();
        mMediaSourceFactory.release();
        releasePlayer();
        mPlayerView = null;
        // Don't destroy it (needed inside the bridge)!
        //mEventListener = null;
    }
    
    public void setPlayer(SimpleExoPlayer player) {
        mPlayer = player;
        player.addListener(this);
        // Set by the view after this (see onSurfaceSizeChanged)
        mHasSurface = false;
        player.addAnalyticsListener(mDiagnosticsListener);
        postHealthCheck(mHealth.getTickMs());
        mRangeMuter.setPlayer(player);
    }

    /**
     * Enables {@link #nextFrame()}. Pass the video renderer of the player set with {@link #setPlayer(SimpleExoPlayer)}.
     */
    public void setVideoRenderer(DebugInfoMediaCodecVideoRenderer renderer) {
        mFrameStepper.setPlayer(mPlayer, renderer);
        mVideoRenderer = renderer;

        if (renderer != null) {
            renderer.setKeyframeListener(this::onDecodedFromKeyframe);
        }
    }

    /**
     * Where the video starts (e.g. a resume's position) and what with, and the frames decoded first to get there
     */
    private VideoStarts.FirstFrame getFirstFrame() {
        DebugInfoMediaCodecVideoRenderer renderer = mVideoRenderer;

        return VideoStarts.FirstFrame.of(mPlayer != null ? mPlayer.getCurrentPosition() : -1, mPlayer != null ? mPlayer.getVideoFormat() : null,
                renderer != null ? renderer.getSkippedToPosition() : 0, renderer != null ? renderer.getSkipStartMs() : -1);
    }

    /**
     * For the diagnostics (see #checkHealth). Pass the one of the player set with {@link #setPlayer(SimpleExoPlayer)}.
     */
    public void setAudioClockInfo(@Nullable AudioClockInfo audioClockInfo) {
        mAudioClockInfo = audioClockInfo;
    }

    /**
     * Pauses (if playing) and shows the next frame. Live streams aren't stepped.
     */
    public void nextFrame() {
        if (getVideo() != null && getVideo().isLive) {
            return;
        }

        mFrameStepper.stepForward();
    }

    //@Override
    //public void setEventListener(PlayerEventListener eventListener) {
    //    mEventListener = eventListener;
    //}
    
    public void setPlayerView(PlayerView playerView) {
        mPlayerView = playerView;
    }
    
    public void setTrackSelector(DefaultTrackSelector trackSelector) {
        mTrackSelectorManager.setTrackSelector(trackSelector);

        // Not after it froze the picture or stalled the player in this run (see TunnelingFallback)
        if (mContext != null && trackSelector != null && PlayerTweaksData.instance(mContext).isTunneledPlaybackEnabled()
                && !TunnelingFallback.isOff(mContext)) {
            // Enable tunneling if supported by the current media and device configuration.
            if (VERSION.SDK_INT >= 21) {
                trackSelector.setParameters(trackSelector.buildUponParameters().setTunnelingAudioSessionId(C.generateAudioSessionIdV21(mContext)));
            }
        }
    }
    
    public void setVideo(Video video) {
        mVideo = new WeakReference<>(video);
    }
    
    public Video getVideo() {
        return mVideo != null ? mVideo.get() : null;
    }
    
    public List<FormatItem> getVideoFormats() {
        return ExoFormatItem.from(mTrackSelectorManager.getVideoTracks());
    }

    public boolean hasHiddenHdrFormats() {
        return mTrackSelectorManager.hasHiddenHdrFormats();
    }
    
    public List<FormatItem> getAudioFormats() {
        return ExoFormatItem.from(mTrackSelectorManager.getAudioTracks());
    }
    
    public List<FormatItem> getSubtitleFormats() {
        return ExoFormatItem.from(mTrackSelectorManager.getSubtitleTracks());
    }
    
    public void selectFormat(FormatItem formatItem) {
        if (formatItem != null && formatItem.getTrack() != null) {
            FormatItem selectedFormatItem = getSelectedFormat(formatItem.getTrack().rendererIndex);
            if (!formatItem.equals(selectedFormatItem)) {
                mTrackSelectorManager.selectTrack(formatItem.getTrack());
                mEventListener.onTrackSelected(formatItem);
            }
        }
    }

    public FormatItem getVideoFormat() {
        return getSelectedFormat(TrackSelectorManager.RENDERER_INDEX_VIDEO);
    }

    public FormatItem getAudioFormat() {
        return getSelectedFormat(TrackSelectorManager.RENDERER_INDEX_AUDIO);
    }

    public FormatItem getSubtitleFormat() {
        return getSelectedFormat(TrackSelectorManager.RENDERER_INDEX_SUBTITLE);
    }

    private FormatItem getSelectedFormat(int rendererIndex) {
        return ExoFormatItem.from(mTrackSelectorManager.getSelectedTrack(rendererIndex));
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        Log.d(TAG, "onTracksChanged: start: groups length: " + trackGroups.length);

        if (trackGroups.length == 0) {
            Log.i(TAG, "onTracksChanged: Hmm. Strange. Received empty groups, no selections. Why is this happens only on next/prev videos?");
            return;
        }

        notifyOnVideoLoad();

        for (TrackSelection selection : trackSelections.getAll()) {
            if (selection != null) {
                // EXO: 2.12.1
                //Format format = selection.getSelectedFormat();

                // EXO: 2.13.1
                Format format = selection.getFormat(0);

                mEventListener.onTrackChanged(ExoFormatItem.from(format));

                mTrackFormatter.setFormat(format);
            }
        }
        
        setQualityInfo(mTrackFormatter.getQualityLabel());

        // Manage audio focus. E.g. use Spotify when audio is disabled. (NOT NEEDED!!!)
        //MediaTrack audioTrack = mTrackSelectorManager.getAudioTrack();
        //ExoPlayerInitializer.enableAudioFocus(mPlayer, audioTrack != null && !audioTrack.isEmpty());
    }

    private void notifyOnVideoLoad() {
        if (mOnSourceChanged) {
            mOnSourceChanged = false;

            mEventListener.onVideoLoaded(getVideo());

            if (mOnVideoLoaded != null) {
                mOnVideoLoaded.run();
            }

            // Produce thread sync problems
            // Attempt to read from field 'java.util.TreeMap$Node java.util.TreeMap$Node.left' on a null object reference
            //mTrackSelectorManager.fixTracksSelection();
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Log.e(TAG, "onPlayerError: " + error);

        // NOTE: Player is released at this point. So, there is no sense to restore the playback here.

        Throwable nested = error.getCause() != null ? error.getCause() : error;

        mEventListener.onEngineError(error.type, error.rendererIndex, nested);
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onPlayerStateChanged: " + TrackSelectorUtil.stateToString(playbackState));
        }

        PlayerCommands.onState(playbackState, getPositionMs());
        // Before the start may finish below (see VideoStarts#onPlaying)
        VideoStarts.onPlayerState(Player.STATE_READY == playbackState, playWhenReady);

        boolean isPlayPressed = Player.STATE_READY == playbackState && playWhenReady;
        boolean isPausePressed = Player.STATE_READY == playbackState && !playWhenReady;
        boolean isPlaybackEnded = Player.STATE_ENDED == playbackState && playWhenReady;
        boolean isBuffering = Player.STATE_BUFFERING == playbackState && playWhenReady;

        // Fix chapters (seek and play) after playback ends
        if (isPlaybackEnded && mIsEnded) {
            return;
        }

        if (playWhenReady) {
            mFrameStepper.onPlay();
        }

        if (isPlayPressed) {
            VideoStarts.onPlaying(mHasSurface);
            onHealthPlaying();
            mEventListener.onPlay();
        } else if (isPausePressed) {
            mHealth.onInterrupted(PlaybackHealth.RESUMED_BY_PAUSE);
            mEventListener.onPause();
        } else if (isPlaybackEnded) {
            mEventListener.onPlayEnd();
            mIsEnded = true;
        } else if (isBuffering) {
            mHealth.onInterrupted(PlaybackHealth.RESUMED_BY_BUFFERING);
            mEventListener.onBuffering();
        }

        if (getPositionMs() < getDurationMs()) {
            mIsEnded = false;
        }
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        Log.e(TAG, "onPositionDiscontinuity");

        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            mFrameStepper.onSeek();
            // Any seek (SponsorBlock, chapters, remote, not only the UI's): its buffering comes before onSeekProcessed and isn't a stall
            FormatAttempts.onSeek();
            // Where it's sent, as the player tells it till the seek is done
            mHealth.onSeek(SystemClock.elapsedRealtime(), mSeekFromMs, mPlayer.getCurrentPosition());
        }

        mRangeMuter.onSeek();

        // Fix video loop on 480p with legacy codes enabled
        if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION) {
            mPlayer.stop();
            mEventListener.onPlayEnd();
        }
    }

    @Override
    public void onSeekProcessed() {
        mEventListener.onSeekEnd();
    }

    /**
     * Its speed changed (see #setSpeed): the diagnostics' time played goes on at it
     */
    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        PlaybackSpeed.onChanged(playbackParameters.speed);
    }

    public float getSpeed() {
        if (mPlayer != null) {
            return mPlayer.getPlaybackParameters().speed;
        } else {
            return -1;
        }
    }

    public void setSpeed(float speed) {
        if (mPlayer != null && speed > 0) {
            if (PlayerTweaksData.instance(mContext).isAudioTimeStretchingEnabled()) {
                mPlayer.setPlaybackParameters(new PlaybackParameters(speed, mPlayer.getPlaybackParameters().pitch));
            } else {
                mPlayer.setPlaybackParameters(new PlaybackParameters(speed, speed));
            }

            mTrackFormatter.setSpeed(speed);
            setQualityInfo(mTrackFormatter.getQualityLabel());
            mEventListener.onSpeedChanged(speed);
        }
    }

    public float getPitch() {
        if (mPlayer != null) {
            return mPlayer.getPlaybackParameters().pitch;
        } else {
            return -1;
        }
    }

    public void setPitch(float pitch) {
        if (mPlayer != null && pitch > 0) {
            mPlayer.setPlaybackParameters(new PlaybackParameters(mPlayer.getPlaybackParameters().speed, pitch));
        }
    }

    public void setVolume(float volume) {
        if (mPlayer != null && volume >= 0) {
            mVolume = Math.min(volume, 1f);
            mPlayer.setVolume(mIsMuted ? 0 : mVolume);

            //applyVolumeBoost(volume);
        }
    }
    
    public float getVolume() {
        if (mPlayer != null) {
            return mIsMuted ? mVolume : mPlayer.getVolume();
        } else {
            return 1;
        }
    }

    /**
     * Mutes the sound during the ranges of the video that's open (see CensoredWords). Opening another video drops them.
     */
    public void setMuteRanges(@Nullable List<Range> ranges) {
        mRangeMuter.setRanges(ranges);
    }

    private void onMuteChanged(boolean muted) {
        if (mPlayer == null) {
            mIsMuted = false;
            return;
        }

        if (muted && !mIsMuted) {
            mVolume = mPlayer.getVolume();
        }

        mIsMuted = muted;
        mPlayer.setVolume(muted ? 0 : mVolume);
    }

    /**
     * Fixes video artifacts when switching to the next video.<br/>
     * Also could help with memory leaks(??)<br/>
     * Without this also you'll have problems with track quality switching(??).
     */
    public void resetPlayerState() {
        if (containsMedia()) {
            mPlayer.stop(true);
        }
    }
    
    public void setOnVideoLoaded(Runnable onVideoLoaded) {
        mOnVideoLoaded = onVideoLoaded;
    }

    private void onFramePositionChanged(long positionMs) {
        if (mPlayerView != null) {
            mPlayerView.setFramePositionMs(positionMs);
        }
    }

    private void setQualityInfo(String qualityInfoStr) {
        if (mPlayerView != null && qualityInfoStr != null) {
            mPlayerView.setQualityInfo(qualityInfoStr);
        }
    }

    private void applyVolumeBoost(float volume) {
        if (mPlayer == null) {
            return;
        }

        if (mVolumeBooster != null) {
            mPlayer.removeAudioListener(mVolumeBooster);
            mVolumeBooster = null;
        }

        // 5.1 audio cannot be boosted (format isn't supported error)
        // also, other 2.0 tracks in 5.1 group is already too loud. so cancel them too.
        if (volume > 1f && !contains51Audio() && Build.VERSION.SDK_INT >= 19) {
            mVolumeBooster = new VolumeBooster(true, volume, null);
            mPlayer.addAudioListener(mVolumeBooster);
        }
    }
    
    private boolean contains51Audio() {
        if (mTrackSelectorManager == null || mTrackSelectorManager.getAudioTracks() == null) {
            return false;
        }

        for (MediaTrack track : mTrackSelectorManager.getAudioTracks()) {
            if (TrackSelectorUtil.is51Audio(track.format)) {
                return true;
            }
        }

        return false;
    }

    private void releasePlayer() {
        Utils.removeCallbacks(mHealthCheck);
        mSabrManifest = null;
        // Left before its start played: what it played of it
        onStartFrames(mHealth.endStart());
        LastState.onPlayer(mContext, false, false);
        mFrameStepper.setPlayer(null, null);
        mVideoRenderer = null;
        VideoDecoderState.reset();
        mRangeMuter.setPlayer(null);

        if (mPlayer == null) {
            return;
        }

        try {
            mPlayer.removeListener(this);
            mPlayer.removeAnalyticsListener(mDiagnosticsListener);
            mPlayer.stop(true); // Cause input lags due to high cpu load?
            mPlayer.clearVideoSurface();
            mPlayer.release();
        } catch (ArrayIndexOutOfBoundsException e) { // thrown on stop()
            e.printStackTrace();
        } finally {
            mPlayer = null;
        }
    }
}
