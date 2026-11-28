package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.os.SystemClock;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.AudioTrackCounts;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderErrorException;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException;
import com.google.android.exoplayer2.source.sabr.SabrRefusedRequest;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.BrokenResponseError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.PoTokenError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrBackoffError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrProtocolError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrReloadPlayerError;
import com.google.android.exoplayer2.source.sabr.parser.parts.PoTokenStatusSabrPart.PoTokenStatus;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.googlecommon.common.helpers.ConnectionErrors;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.AudioOutputProbe;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.Connections;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.DeviceCapabilities;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.FormatAttempts;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.LiveHeadWaits;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.LoadErrors;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.LoadHolds;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlayerCommands;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlayerFailure;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlayerFailureLog;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.Plays;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlaybackHealth;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlaybackSpeed;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.ProtectionStatuses;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.RendererWaits;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.SilenceDetector;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.VideoDecoderState;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.VideoStarts;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.errors.DecoderErrorFallback;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.errors.SabrDefaultLoadErrorHandlingPolicy;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.FrameDropCodecs;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SoundCodecs;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.TunnelingFallback;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;
import com.liskovsoft.smartyoutubetv2.common.misc.BufferingDetector;
import com.liskovsoft.smartyoutubetv2.common.misc.BufferingDetector.OnLongBuffering;
import com.liskovsoft.smartyoutubetv2.common.misc.MembersVideos;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.NetworkEngineFallback;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;
import com.liskovsoft.youtubeapi.videoinfo.V2.ClientAttempts;

import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ErrorFixerController extends BasePlayerController implements OnLongBuffering {
    private static final String TAG = ErrorFixerController.class.getSimpleName();
    private static final long STREAM_END_THRESHOLD_MS = 180_000;
    /**
     * A video stalled this close to its end has ended: nothing's left to load there (e.g. its whole-second length is past its
     * media), and a reload played it again from 0:00
     */
    private static final long VIDEO_END_THRESHOLD_MS = 2_000;
    /**
     * A video that failed this close to its end has ended: its fix takes longer than what's left (97MSK8B6 #8: WEB's SABR refused it
     * 2.4 s before its end, and four clients in 15 s didn't play it)
     */
    private static final long VIDEO_END_ERROR_THRESHOLD_MS = 5_000;
    private static final int MAX_SABR_ERROR_FIX_ATTEMPTS = 3;
    private static final int MAX_NETWORK_ERROR_RELOADS = 3;
    /**
     * Stalled long again within this: the quality is lowered rather than reloading again
     */
    private static final long REPEATED_STALL_MS = 3 * 60 * 1_000;
    /**
     * Refused again within this after its player response was fetched again: the fresh one didn't help
     */
    private static final long REPEATED_SABR_ERROR_MS = 3 * 60 * 1_000;
    private static final long AUDIO_PLAYED_MS = 1_000;
    private static final int UNPLAYABLE_RELOAD_MS = 30_000;
    /**
     * Why an unplayable one won't play in a while either (see getLastingCause): the start of its action's name, e.g. "members_next"
     */
    private static final String LASTING_MEMBERS = "members";
    private static final String LASTING_AGE_GATED = "age_gated";
    private static final String LASTING_GONE = "gone";
    private static final String LASTING_SIGN_IN = "sign_in";
    /**
     * How long ago a client played a video for its sign-in refusal of another to be that video's (see isSignInRefusedByPlayingClient)
     */
    private static final long PLAYED_CLIENT_MS = 15 * 60 * 1_000;
    /**
     * The playability status of a video that's gone, e.g. removed by the uploader or private
     */
    private static final String STATUS_ERROR = "ERROR";
    private static final String STATUS_LOGIN_REQUIRED = "LOGIN_REQUIRED";
    private static final long AUDIO_PLAYED_CHECK_MS = 2_000;
    /**
     * Of a reload or an engine restart, as before: the player's error passes first
     */
    private static final long RETRY_MS = 1_000;
    /**
     * The longest wait before a fix is tried again (see RetryBackoff)
     */
    private static final long MAX_RETRY_MS = 30_000;
    /**
     * Refused AudioTracks of a video, with nothing left to fall back to, restarted at once: past them, each restart waits longer
     */
    private static final int MAX_AUDIO_INIT_RESTARTS = 2;
    /**
     * Of mDeviceAudioRetries: the device's, whatever the video
     */
    private static final String DEVICE_AUDIO_RETRY_KEY = "device";
    /**
     * A load failed on its connection this recently: the connection is what fails a start that hangs (see LoadErrors)
     */
    private static final long CONNECTION_ERROR_WINDOW_MS = 30_000;
    /**
     * A stall while the player has held its loading back this long has its buffer full: it waits for a renderer that has nothing to
     * play (see runLongBufferingAction)
     */
    private static final long LOAD_HELD_STALL_MS = 5_000;
    /**
     * A new connection of the player still in its lookup or connecting after this long (the connect timeout): the network's (see
     * Connections#getStuckConnection)
     */
    private static final long STUCK_CONNECTION_MS = 5_000;
    /**
     * A live stall with a wait for the stream's head this recent stands at the head (see isLiveHeadStill): the server asks the next
     * request to wait 5 s at most
     */
    private static final long LIVE_HEAD_WAIT_WINDOW_MS = 10_000;
    /**
     * A SABR refusal this soon after the stream fetched the player response again (see SabrProtocolError#getSessionAgeMs): the fresh
     * one was refused, another fetch gets the same
     */
    private static final long FRESH_SABR_SESSION_MS = 60_000;
    /**
     * Played on the client this long before a SABR refusal, it's the stream's, not the client's (see applySabrProtocolErrorAction): a
     * client that refuses each request after its first answer isn't kept on and on. 0CW972QK's TV played 15-40 s each time.
     */
    private static final long SABR_PLAYED_MS = 5_000;
    /**
     * An audio track of fewer bits a second than this carries no sound (see applySilentAudioFallback): the lowest an audio format
     * offers is about 48 kbps
     */
    private static final int MIN_SOUND_BITRATE = 16_000;
    /**
     * A live stream's metadata is read again at most this often for a video (see checkLiveEnd)
     */
    private static final long LIVE_END_CHECK_MS = 60_000;
    /**
     * A live stream's formats aren't fetched again sooner for the video: the ones fetched may be of the stream still live, its segments
     * still gone (see onLiveSegmentsGone)
     */
    private static final long LIVE_REFETCH_MS = 60_000;
    /**
     * Played this far past where the attestation wall stopped it, a client got past it: its lap starts over (see countWallSwitch)
     */
    private static final long WALL_PASSED_MS = 10_000;
    /**
     * No switch counted for this long, its lap was left (e.g. the video closed and opened again later): another starts (see
     * countWallSwitch). 97Z1YWD3's switches came 5-15 s apart.
     */
    private static final long WALL_LAP_GAP_MS = 120_000;
    /**
     * The attestation pending this long, the server's grace period is over: it sends no more media (65FDWR9C #1: 58.6 s; 97Z1YWD3
     * stopped at 0:59.9). Sooner, a request that got no answer is the network's.
     */
    private static final long ATTESTATION_GRACE_MS = 50_000;
    private final BufferingDetector mBufferingDetector = new BufferingDetector(this);
    private VideoLoaderController mVideoLoaderController;
    /**
     * The video whose live stream's metadata was read again last, and when (elapsed realtime): see checkLiveEnd
     */
    private String mLiveEndCheckVideoId;
    private long mLiveEndCheckTimeMs;
    private int mDecoderErrorCount;
    /**
     * The video the decoder error count and fallbacks are for (see applyDecoderErrorFallback)
     */
    private String mDecoderErrorVideoId;
    private FormatItem mVideoFallback;
    /**
     * Dropping frames on this many videos on a codec at a rate, the next ones try another (see onHealthFailure, applySpeedCodec)
     */
    private static final int FRAME_DROP_VIDEOS = 2;
    /**
     * The video whose codec was picked for the playback speed (see applySpeedCodec): once a video, the user's pick after it stays
     */
    @Nullable
    private String mSpeedCodecVideoId;
    private FormatItem mAudioFallback;
    /**
     * Refused AudioTracks on the video in mDecoderErrorVideoId, since its sound last played
     */
    private int mAudioInitErrorCount;
    /**
     * Of the video, the engine restarts for refused AudioTracks past MAX_AUDIO_INIT_RESTARTS: the device refused each track for 20
     * minutes (FARAJV5R #16–#41), restarted 40 times a minute
     */
    private final RetryBackoff mAudioInitRetries = new RetryBackoff(2 * RETRY_MS, MAX_RETRY_MS);
    /**
     * The device refuses every AudioTrack, not the app's (see applyDeviceAudioRefusal), till a video's sound plays: whatever video
     */
    private boolean mIsAudioRefusedByDevice;
    /**
     * The engine restarts while the device refuses every AudioTrack: one wait growing across the videos, not each video's own
     */
    private final RetryBackoff mDeviceAudioRetries = new RetryBackoff(2 * RETRY_MS, MAX_RETRY_MS);
    /**
     * Of the video, the fetches of its formats that failed, till its formats come (see onSourceChanged): 55 fetches a minute for 15
     * minutes while the network was down (FARAJV5R #47–#99)
     */
    private final RetryBackoff mFetchRetries = new RetryBackoff(RETRY_MS, MAX_RETRY_MS);
    /**
     * Of the video, the reloads of a start that hangs on a connection that fails (see runLongBufferingAction), or of a request that
     * failed on a network that's down (see applyNetworkDownWait), till it plays
     */
    private final RetryBackoff mStallRetries = new RetryBackoff(RETRY_MS, MAX_RETRY_MS);
    /**
     * The DNS the DNS fix switched from (see runLongBufferingAction), till a video plays, -1 for none: put back when the player's
     * connections fail the same way with Google's (it didn't help). Before, it stayed after the same timeouts went on (S4PGCB6X
     * #7–#19), each lookup 500 ms longer from then on.
     */
    private int mDnsFixFrom = -1;
    /**
     * A DNS fix was undone: not tried again (see mDnsFixFrom)
     */
    private boolean mIsDnsFixRefuted;
    private String mSabrErrorFixVideoId;
    private int mSabrErrorFixAttempts;
    /**
     * The live stream the user was told its head stands still (see runLongBufferingAction): once a video
     */
    private String mLiveHeadWaitVideoId;
    /**
     * The video the reloads on a broken connection are counted for (see applyNetworkErrorReload)
     */
    private String mNetworkErrorVideoId;
    private int mNetworkErrorReloads;
    /**
     * The last SabrError fixed, and when (see applySabrProtocolErrorAction)
     */
    private String mSabrProtocolErrorVideoId;
    private long mSabrProtocolErrorTimeMs;
    /**
     * The video and the client it played on since the last SabrError fixed, and when it began to (see applySabrProtocolErrorAction),
     * null for none
     */
    private String mSabrPlayedVideoId;
    private String mSabrPlayedClient;
    private long mSabrPlayedMs;
    /**
     * The video that played last, its client, and when it began to (see getPlayedClient)
     */
    @Nullable
    private String mPlayedVideoId;
    @Nullable
    private String mPlayedClient;
    private long mPlayedMs;
    /**
     * The video reloaded once for a first segment that never came (see runLongBufferingAction)
     */
    private String mStuckLoadVideoId;
    /**
     * The video a stall switched the client for, as a client that hangs at start (see runLongBufferingAction): undone if its next
     * failure is the network's (see undoStallClientSwitch)
     */
    private String mStallSwitchVideoId;
    /**
     * The last long stall (see isRepeatedStall)
     */
    private String mStallVideoId;
    private long mStallTimeMs;
    /**
     * The live stream whose formats were last fetched again for its segments gone, and when (see onLiveSegmentsGone)
     */
    private String mLiveRefetchVideoId;
    private long mLiveRefetchMs;
    /**
     * Where the playback started (see onPlay)
     */
    private long mPlayStartPositionMs;
    /**
     * When the player's source last opened (see onSourceChanged), before its streams' first request: a protection status told
     * before is of a stream before (see isAttestationPending)
     */
    private long mSourceChangedMs;
    /**
     * The video stopped at the attestation wall (see countWallSwitch), where it stopped, and the clients its formats came from at the
     * switches made for it since: null for none
     */
    private String mWallVideoId;
    private long mWallPositionMs;
    private final List<String> mWallClients = new ArrayList<>();
    private int mWallSwitches;
    private long mWallSwitchMs;
    /**
     * A tunneled freeze the user paused, of this video: turned off, its player restarted once it plays again (see onPlay)
     */
    private String mTunnelingRestartVideoId;
    /**
     * A client of the wall's lap asked for a sign-in, not for the viewer's age: a bot check (see isSignInAsked)
     */
    private boolean mIsWallSignInAsked;
    /**
     * The video whose SABR request was refused after it played, and when its player response was fetched again for it (see
     * applySabrRefusedRefetch)
     */
    private String mSabrRefetchVideoId;
    private long mSabrRefetchMs;
    /**
     * The decoded buffers with sound in them when it last played (see SilenceDetector#getSoundBuffers): more by the check, its codec was
     * heard (see SoundCodecs)
     */
    private long mSoundBuffersAtPlay;
    /**
     * Still playing and its clock moved on: the audio's clock is the player's, so its sound is output (see FormatAttempts)
     */
    private final Runnable mCheckAudioPlayed = () -> {
        if (getPlayer() != null && getPlayer().isPlaying() && getPlayer().getPositionMs() - mPlayStartPositionMs >= AUDIO_PLAYED_MS) {
            // Heard: the codec plays with sound on the device, a stream of it silent from its start is the video's (see
            // applySilentAudioFallback)
            if (SilenceDetector.getSoundBuffers() > mSoundBuffersAtPlay) {
                SoundCodecs.onSound(getContext(), getMimeType(getPlayer().getAudioFormat()));
            }

            FormatAttempts.onAudioPlayed();
            // A track refused after this is another time's
            mAudioInitErrorCount = 0;
            mAudioInitRetries.reset();
            mIsAudioRefusedByDevice = false;
            mDeviceAudioRetries.reset();
        }
    };


    @Override
    public void onInit() {
        mVideoLoaderController = getController(VideoLoaderController.class);
    }

    @Override
    public void onEngineError(int type, int rendererIndex, Throwable error) {
        Log.e(TAG, "Player error occurred: %s. Trying to fix…", type);

        PlayerFailure failure = newFailure(PlayerFailure.KIND_ENGINE_ERROR).engineError(type, rendererIndex, error);

        if (type == PlayerEventListener.ERROR_TYPE_SOURCE) {
            addSourceErrorDetails(failure, error);
        }

        mBufferingDetector.reset();
        Utils.removeCallbacks(mCheckAudioPlayed);
        FormatAttempts.onPlaying(false);
        Plays.onPlaying(false, getPositionMs());
        markFailedStream(type, rendererIndex, error);

        try {
            runEngineErrorAction(type, rendererIndex, error, failure);
        } finally {
            failure.record();
        }
    }

    /**
     * What the source failed on, as a stall tells it (see onLongBuffering): where its tracks' last loads end and how fast they came, what's
     * buffered, the loads that failed since it last played with each try's time and error (see LoadErrors#describeSincePlay), and the
     * number of the server the failed request went to (see FormatAttempts#getHostNumber). R71TW3F4 #1-#13: a read that timed out every
     * 40 s, 13 times, and nothing told which load hung, how its tries went, nor on which server. A SABR request refused with HTTP 401 or
     * 403 says what it asked for and how old its session was, as a SabrProtocolError does (see applySabrProtocolErrorAction): WVE7ZKDD #2,
     * a 403 on a rewind, couldn't tell whether the seek or a server change drew it. Diagnostics only: the fix goes on without them.
     */
    private void addSourceErrorDetails(PlayerFailure failure, @Nullable Throwable error) {
        try {
            failure.clientAttempts(getSkippedClientAttempts());

            if (getPlayer() != null) {
                long positionMs = getPlayer().getPositionMs();
                long bufferedPositionMs = getPlayer().getBufferedPositionMs();
                FormatAttempts.addStallDetails(failure, positionMs);
                failure.detail("bufferedMs", bufferedPositionMs >= 0 ? bufferedPositionMs - positionMs : null);
            }

            failure.detail("loadErrors", LoadErrors.describeSincePlay());
            int hostNumber = FormatAttempts.getHostNumber(getFailedHost(error));
            failure.detail("hostNumber", hostNumber > 0 ? hostNumber : null);
            SabrRefusedRequest refused = SabrRefusedRequest.of(error);

            if (refused != null) {
                failure.detail("sabrRequest", refused.request);
                failure.detail("sabrSessionAgeMs", refused.sessionAgeMs >= 0 ? refused.sessionAgeMs : null);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Can't read what the source failed on: %s", e.getMessage());
        }
    }

    /**
     * Of the video's last fetch (the one the stream's formats came from, or a prefetch's, see FormatInfoWrapper#selectPlaybackFormatInfo),
     * when a client before the one that gave them failed: why they're that client's. Signed out, autoplay went
     * on WEB after ten videos on TV, and the 217 errors of the attestation wall that followed didn't say what TV had answered (EENAQFZF
     * #1-#65). Null when the first client tried gave them: the player's client says as much.
     */
    @Nullable
    private List<ClientAttempts.Attempt> getSkippedClientAttempts() {
        Video video = getVideo();
        List<ClientAttempts.Attempt> attempts = video != null ? ClientAttempts.get(video.videoId) : null;

        if (attempts == null) {
            return null;
        }

        for (ClientAttempts.Attempt attempt : attempts) {
            if (!ClientAttempts.RESULT_OK.equals(attempt.result)) {
                return attempts;
            }
        }

        return null;
    }

    /**
     * The server's name of the request the error is of (see HttpDataSourceException#dataSpec), null when not known
     */
    @Nullable
    private static String getFailedHost(@Nullable Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpDataSourceException) {
                DataSpec dataSpec = ((HttpDataSourceException) cause).dataSpec;
                return dataSpec != null && dataSpec.uri != null ? dataSpec.uri.getHost() : null;
            }

            if (cause.getCause() == cause) {
                break;
            }
        }

        return null;
    }

    @Override
    public void onLongBuffering() {
        if (getPlayer() == null) {
            return;
        }

        // Paused while it buffered (e.g. by the remote's media key): ExoPlayer tells no pause in its buffering state, so the timer ran
        // on. The user's wait, not a stall, and a reload would play it again (QYZPW0FN #2: 19 s after a pause, its reload unpaused
        // the video). Played again, its buffering is timed anew (see onBuffering).
        if (!getPlayer().getPlayWhenReady()) {
            Log.d(TAG, "Long buffering while paused: not a stall");
            return;
        }

        PlayerFailure failure = newFailure(PlayerFailure.KIND_STALL);
        long positionMs = getPlayer().getPositionMs();
        long bufferedPositionMs = getPlayer().getBufferedPositionMs();
        FormatAttempts.addStallDetails(failure, positionMs);
        // What the player has past the position, the least of its tracks: where the last segments loaded end (see
        // FormatAttempts#addStallDetails) isn't, a stall with "33 s" of video loaded ahead didn't tell why it waited (J9T6DZWN #2)
        failure.detail("bufferedMs", bufferedPositionMs >= 0 ? bufferedPositionMs - positionMs : null);
        // Why nothing loads, and what the video decoder was given and showed: a stall with 30-35 s buffered and nothing loaded for 20 s
        // didn't tell whether the player held its loading back or a decoder had taken it all (96YV3AK4)
        LoadHolds.addDetails(failure);
        VideoDecoderState.addDetails(failure);
        RendererWaits.addDetails(failure);

        try {
            runLongBufferingAction(failure);
        } finally {
            failure.record();
        }
    }

    /**
     * The playback goes wrong without an error (see PlaybackHealth), before its failure is recorded: it stalls again and again on a
     * connection slower than the format plays (J9T6DZWN #3, #4, #6: 4K60, 3 stalls in a minute each time, with no fix), or drops frames
     * at a frame rate no decoder declares for its size (J7VJY9TH #1: 4K60 on a decoder that declares 2160p30, 215 frames dropped).
     * A lower quality, for this video only, as a repeated long stall gets (see runLongBufferingAction). A connection that stops
     * answering isn't one too slow (see FormatAttempts#isTooSlow): a lower quality stalls the same way. A picture that freezes through a
     * tunneled decoder plays on without tunneling (see TunnelingFallback).
     */
    @Override
    public void onHealthFailure(String kind, PlayerFailure failure) {
        // What its loads did, whatever's done: lowered for stalls, it didn't tell whether a server was slow or the format too big for the
        // connection (93HXRTB5 #1: 3 stalls of a 4K VP9 at 8.3 Mbps, lowered to 1440p, which played 48 min). As a long stall's.
        if (PlaybackHealth.KIND_STALLS.equals(kind) && getPlayer() != null) {
            FormatAttempts.addStallDetails(failure, getPlayer().getPositionMs());
        }

        // Not over a fix already asked for (e.g. a long stall's reload)
        if (mVideoLoaderController.isFixPending()) {
            return;
        }

        // A live stream's too: C2TJMVW6's was one
        if (applyDisplaySizeLowering(kind, failure)) {
            return;
        }

        if (!isOfflineVideo()) {
            return;
        }

        // The picture stands still while the sound plays on, through a tunneled decoder: the setting is the user's (off by default),
        // and a restart that kept it froze again 13 s later (18CK8PQ0 #2, #3). All 7 freezes of that device were of its tunneled VP9
        // decoder: after a resume's seek, a switch from AVC, or mid-play. A clock that stands still isn't the decoder's.
        boolean isClockStill = Boolean.TRUE.equals(failure.getDetail("clockStill"));
        // The user paused the frozen picture (see PlaybackHealth): the freeze is over, and a restart or a reload would play it again
        boolean isEndedByPause = Boolean.TRUE.equals(failure.getDetail("endedByPause"));

        // Whether this player tunnels, not whether the fallback was on: a start's freeze, or one the user paused, turned it off without
        // a restart, and this player tunnels still
        if (PlaybackHealth.KIND_VIDEO_FROZEN.equals(kind) && !isClockStill && VideoDecoderState.isTunneling()) {
            applyTunnelingOff(failure, !isEndedByPause, TunnelingFallback.turnOff(getContext()));
            // Paused, restarted once it plays again: the picture may stand still then too, and it's reported once for the format
            Video video = getVideo();
            mTunnelingRestartVideoId = isEndedByPause && video != null ? video.videoId : null;
            return;
        }

        // A start's picture stood still through a tunneled decoder, and it's over: no restart, the next player is made without it (see
        // ExoPlayerController#setTrackSelector), and it counts toward its suspension. Y1D66CNW #1: 3.5 s still after a restore seek, the
        // device's fourth such freeze, all of them a start's, and the fallback heard of none.
        if (PlaybackHealth.KIND_START_FROZEN.equals(kind) && !isClockStill && VideoDecoderState.isTunneling()) {
            // Off already, by a start's freeze before (the player is kept from video to video): this one tunnels still, restarted
            boolean isTurnedOff = TunnelingFallback.turnOff(getContext());
            applyTunnelingOff(failure, !isTurnedOff, isTurnedOff);

            return;
        }

        // The picture and the clock stand still, with the poToken's attestation pending: the server holds back the media, as a stall of
        // it does (see runLongBufferingAction), and the player stays ready. 97Z1YWD3 #13: reloaded at 0:59.9, 3 frames in 8.5 s, and
        // 58 s more of it till the user left. Not one the user paused: the reload would play it.
        if (PlaybackHealth.KIND_VIDEO_FROZEN.equals(kind) && isClockStill && !isEndedByPause && isAtAttestationWall()) {
            applyAttestationWallSwitch(failure);
            return;
        }

        // Silent for all it played: the stream's. A sound that came and went is the video's.
        if (PlaybackHealth.KIND_AUDIO_SILENT.equals(kind)) {
            if (Boolean.TRUE.equals(failure.getDetail("silentFromStart"))) {
                applySilentAudioFallback(failure);
            }

            return;
        }

        FormatItem video = getPlayer().getVideoFormat();
        Format format = video != null && video.getTrack() != null ? video.getTrack().format : null;
        boolean isTooSlow = PlaybackHealth.KIND_STALLS.equals(kind) && FormatAttempts.isTooSlow();
        boolean isBeyondDecoder = PlaybackHealth.KIND_FRAME_DROPS.equals(kind) && TrackSelectorUtil.isFrameRateBeyondDecoder(format);

        if ((isTooSlow || isBeyondDecoder) && lowerVideoQuality()) {
            failure.action("lower_quality").action("restart_engine");
            mVideoLoaderController.restartEngine(failure.getKind());
            return;
        }

        // A codec the decoder declares for this frame rate, dropping frames on video after video at the rate it's decoded at (the video's
        // times the speed, see FrameDropCodecs): another one of the same size, for this video only (0HCC641V #2: AVC 1080p50 at 1.5x
        // dropped 80 of 120 frames, the second such video; VP9 at 1.5x then dropped 9 of 808 in its first 10 s, 150 in 13m44s). The rate
        // isn't the decoder's declared one: VP9 kept up with 75 frames a second where every decoder there declares 1080p60, and the quality
        // lowered to 720p (see isBeyondDecoder) would have been the worse fix. A first video's drops may be its own (436Q6MET: a 4K60 start
        // dropped 151 frames in its first 10 s, then none in 32 min). The drops of a decoder catching up to where a seek or a resume sent
        // it are left out by PlaybackHealth, only the ones that went on past its catch-up are reported (see
        // PlaybackHealth#isDropsAfterCatchUp): MD8RQZM2 #1's start resumed at 0:02 dropped 75 of 83 frames 1.1-1.6 s in, on a device whose
        // AV1 2160p60 had played 69 times with no failure, and two such would have sent its 4K60 to VP9. A start from where the player was
        // has nothing to catch up on: its drops are the codec's (EY23XNK9: VP9 60fps starts dropped 48-57 frames in their first 10 s,
        // AV1 0-1).
        // Not a card's preview: its drops on a small surface over the UI say nothing of the codec in the player
        String otherCodec = PlaybackHealth.KIND_FRAME_DROPS.equals(kind) && !isBeyondDecoder && !isEmbedPlayer()
                && isDroppingOnVideoAfterVideo(video) ? applyOtherVideoCodec(video) : null;

        if (otherCodec != null) {
            failure.action("other_codec:" + otherCodec).action("restart_engine");
            mVideoLoaderController.restartEngine(failure.getKind());
        }
    }

    /**
     * Bigger than the display can show, dropping frames: its size there looks the same and is less to decode, for this video only, the
     * first one too. C2TJMVW6 #1, #2: a live stream's VP9 1080p60 on a 720p TV dropped 102 of 182 frames, then 137 of 214, on a decoder
     * that declares 1080p60, and the user left after 35 s and 8 s; 3TKGJY3Z #1: VP9 1080p25 on a 720p TV, 31 of 91. Not a card's
     * preview: its surface is small whatever the display.
     *
     * @return whether it was lowered, the engine restarting
     */
    private boolean applyDisplaySizeLowering(String kind, PlayerFailure failure) {
        if (!PlaybackHealth.KIND_FRAME_DROPS.equals(kind) || getPlayer() == null || getVideo() == null || isEmbedPlayer()) {
            return false;
        }

        FormatItem video = getPlayer().getVideoFormat();
        int displaySize = getDisplaySize();

        if (displaySize <= 0 || video == null || Math.max(video.getWidth(), video.getHeight()) <= displaySize
                || !lowerVideoQuality(displaySize)) {
            return false;
        }

        failure.action("lower_to_display").action("restart_engine");
        mVideoLoaderController.restartEngine(failure.getKind());

        return true;
    }

    /**
     * Frames were dropped on the kind of the format playing (its codec, size and frame rate, see FrameDropCodecs) in another video
     * before, of this run or one before
     */
    private boolean isDroppingOnVideoAfterVideo(@Nullable FormatItem format) {
        String kind = getFrameDropKind(format, getCodecName(format));
        Video video = getVideo();

        if (kind == null || video == null || video.videoId == null) {
            return false;
        }

        return FrameDropCodecs.add(getContext(), kind, video.videoId) >= FRAME_DROP_VIDEOS;
    }

    /**
     * Of a video format with the codec, at the rate it's decoded at: e.g. "av1 1080p75" for 1080p50 at 1.5x (see FrameDropCodecs), null
     * when its codec or size isn't known
     */
    @Nullable
    private String getFrameDropKind(@Nullable FormatItem format, @Nullable String codec) {
        return getFrameDropKind(format, codec, getPlayer() != null ? getPlayer().getSpeed() : 1);
    }

    /**
     * @param speed the playback's: the player's own reads the speed before the last set till its playback thread takes it (see
     *              #onSpeedChanged)
     */
    @Nullable
    private static String getFrameDropKind(@Nullable FormatItem format, @Nullable String codec, float speed) {
        int size = format != null ? Math.min(format.getWidth(), format.getHeight()) : -1;

        return codec != null && size > 0 ? FrameDropCodecs.kindOf(codec, size, format.getFrameRate(), speed) : null;
    }

    /**
     * Faster than 1x, the codec playing dropped frames at the rate it's decoded at on FRAME_DROP_VIDEOS other videos (see
     * FrameDropCodecs): another one of the same size that hasn't, from the start, as onHealthFailure picks after the drops and an engine
     * restart. 0HCC641V: each 1080p50 video at 1.5x played AV1 for 13-20 s of drops (6-14 % of its frames) till the user picked AVC in
     * the menu, which dropped too; VP9 kept up at that rate (9 of 808 frames in its first 10 s). At 1x the same codecs dropped none
     * (27XPFNZC), so not at 1x. Once a video (see #mSpeedCodecVideoId): the user's pick after it stays. Not a card's preview.
     */
    private void applySpeedCodec(float speed) {
        Video video = getVideo();

        if (getPlayer() == null || video == null || video.videoId == null || isEmbedPlayer() || speed <= 1
                || video.videoId.equals(mSpeedCodecVideoId)) {
            return;
        }

        FormatItem playing = getPlayer().getVideoFormat();
        String kind = getFrameDropKind(playing, getCodecName(playing), speed);

        if (kind == null || FrameDropCodecs.count(getContext(), kind) < FRAME_DROP_VIDEOS) {
            return;
        }

        FormatItem other = findOtherVideoCodec(playing, speed);

        // Every other one dropped frames at its rate too: the one picked stays
        if (other == null || FrameDropCodecs.hasDropped(getContext(), getFrameDropKind(other, getCodecName(other), speed))) {
            return;
        }

        mSpeedCodecVideoId = video.videoId;

        if (applyVideoCodecLimit(other)) {
            Log.d(TAG, "%s drops frames at %sx: %s instead", kind, speed, getCodecName(other));
            FormatAttempts.onSelected(FormatAttempts.REASON_SPEED_CODEC);
            PlayerCommands.onFormat(PlayerCommands.SOURCE_APP, getPlayer().getPositionMs());
            getPlayer().setFormat(other);
        }
    }

    /**
     * Another codec than the one playing, of the same size, for this video only (see lowerVideoQuality): the first of the list, as it
     * prefers, that hasn't dropped frames on the device at its size and the rate it's decoded at (see FrameDropCodecs). Every other one
     * has: the first of them. A limit, as the quality lowered is. 0HCC641V #2: with AVC preferred over VP9, AV1's other codec at 1.5x
     * was AVC, which dropped frames too; once it had on two videos, AVC's was VP9.
     *
     * @return the codec's name, null for none of that size
     */
    @Nullable
    private String applyOtherVideoCodec(@Nullable FormatItem playing) {
        FormatItem other = findOtherVideoCodec(playing, getPlayer() != null ? getPlayer().getSpeed() : 1);

        return other != null && applyVideoCodecLimit(other) ? getCodecName(other) : null;
    }

    /**
     * The format of another codec than the one playing, of the same size (see #applyOtherVideoCodec), null for none
     *
     * @param speed the playback's, of the rates the drops are of (see FrameDropCodecs)
     */
    @Nullable
    private FormatItem findOtherVideoCodec(@Nullable FormatItem playing, float speed) {
        String codec = getCodecName(playing);

        if (getPlayer() == null || codec == null) {
            return null;
        }

        List<FormatItem> videoFormats = getPlayer().getVideoFormats();
        int size = Math.max(playing.getWidth(), playing.getHeight());

        if (videoFormats == null || size <= 0) {
            return null;
        }

        FormatItem other = null;

        for (FormatItem format : videoFormats) {
            String otherCodec = getCodecName(format);

            if (otherCodec == null || otherCodec.equals(codec) || Math.max(format.getWidth(), format.getHeight()) != size) {
                continue;
            }

            if (other == null) {
                other = format;
            }

            String kind = getFrameDropKind(format, otherCodec, speed);

            if (kind == null || !FrameDropCodecs.hasDropped(getContext(), kind)) {
                other = format;
                break;
            }
        }

        return other;
    }

    /**
     * The video plays at most the format's size, of its codec, for this video only: kept through its reloads (see
     * #reapplyDecoderErrorFallback)
     *
     * @return false when the limit couldn't be made
     */
    private boolean applyVideoCodecLimit(FormatItem other) {
        FormatItem limit = ExoFormatItem.fromVideoSpec(String.format(Locale.US, "%d,%d,60,%s", other.getWidth(), other.getHeight(),
                getCodecName(other)), false);

        if (limit == null) {
            return false;
        }

        getPlayerData().setTempVideoFormat(limit);
        mVideoFallback = limit;

        return true;
    }

    /**
     * The sound played silent from the start, for 15 s: AAC in the language of the track for this video, as for an audio decoder's
     * error, unless it's AAC already. The format is marked failed already (see ExoPlayerController#recordHealthFailure), and the
     * restart plays the video from here. Not for a track too small to carry a sound (RF6HBNNH #5 and #7: Opus at 3 and 4 kbps, which
     * is a video with no sound): its silence is the video's, and another format of it is as silent. Nor for a codec the device played with
     * sound lately (see SoundCodecs): its decoding and output work, the silence is the video's, e.g. a silent intro (CQHM6BGP #1: 2
     * minutes of it, reported at 15 s; it played AAC already, but on Opus, which had played 74 times there, it would have swapped to AAC
     * and restarted the engine for nothing). Not checking the sound again later instead: that intro was still silent at 30 s, and a
     * stream that's silent on the device would wait longer for its fix. The trade-off: a codec heard before whose stream of this video
     * alone is silent (none seen so far) stays silent.
     */
    private void applySilentAudioFallback(PlayerFailure failure) {
        FormatItem audio = getPlayer().getAudioFormat();
        Format format = audio != null && audio.getTrack() != null ? audio.getTrack().format : null;

        if (format != null && format.bitrate > 0 && format.bitrate < MIN_SOUND_BITRATE) {
            return;
        }

        long heardAgoMs = SoundCodecs.getHeardAgoMs(getContext(), format != null ? format.sampleMimeType : null);

        if (heardAgoMs >= 0) {
            failure.detail("codecHeardAgoMs", heardAgoMs);
            return;
        }

        FormatItem fallback = applyAudioFallback(format);

        // None: AAC is played already, or its format isn't known. Nothing else to go on with.
        if (fallback == null) {
            return;
        }

        failure.action("audio_fallback:" + getFallbackName(fallback)).action("restart_engine");
        mVideoLoaderController.restartEngine(failure.getKind());
    }

    /**
     * A live stream's segments answer 404 on both tracks, and none came since, for a while (see LiveSegmentsGone): the stream ended,
     * or its URLs aren't served anymore. A reload asks for the same URLs, it takes the formats cached (FVNAAYZD: a stream that stopped
     * at 1:00:01 answered 404 for two minutes, through three stall reloads). Its formats are fetched again instead: of a stream still
     * live, they have new URLs; of one that ended, they aren't live anymore (see Video#sync(MediaItemFormatInfo)), it plays to its end,
     * and its metadata then says it ended (see SuggestionsController#onVideoLoaded); of one taken down, its reason shows. Not again for
     * the video within LIVE_REFETCH_MS.
     */
    @Override
    public void onLiveSegmentsGone(PlayerFailure failure) {
        Video video = getVideo();

        // Not over a fix already asked for (e.g. a stall's reload): its 404s come again after it, and are told again
        if (video == null || !video.isLive || mVideoLoaderController.isFixPending()) {
            return;
        }

        long nowMs = SystemClock.elapsedRealtime();

        if (Helpers.equals(video.videoId, mLiveRefetchVideoId) && nowMs - mLiveRefetchMs < LIVE_REFETCH_MS) {
            return;
        }

        mLiveRefetchVideoId = video.videoId;
        mLiveRefetchMs = nowMs;

        failure.action("refetch_formats").action("reload_video");
        YouTubeServiceManager.instance().invalidateFormatCache();
        mVideoLoaderController.reloadVideo(failure.getKind());
    }

    private void runLongBufferingAction(PlayerFailure failure) {
        // The fixes below go on: a live stream that goes on is reloaded as before
        checkLiveEnd(failure);

        if (isStreamEnded() || isAtVideoEnd(VIDEO_END_THRESHOLD_MS)) {
            failure.action("play_end");
            getMainController().onPlayEnd();
        } else if (isOfflineVideo() && isAttestationPending()) {
            // Past its grace period, the server withholds the media while the poToken's attestation is pending: stalled at 0:59,
            // the same client's reload froze there (51AQC7FS). The SABR stream tells it as a PoTokenError (see
            // applySabrPoTokenAction): this is a stall it didn't tell.
            applyAttestationWallSwitch(failure);
        } else if (isOfflineVideo() && isSubtitlesEnabled()) {
            // Long loading subtitles cause hangs
            failure.action("disable_subtitles").action("reload_video");
            disableSubtitles();
            mVideoLoaderController.reloadVideo(failure.getKind());
        } else if (isOfflineVideo() && LoadHolds.getHeldMs() >= LOAD_HELD_STALL_MS) {
            // The player holds its loading back, its buffer full, and waits all the same: what's buffered is past what a renderer has
            // to play. Not the connection, a wait doesn't help (96YV3AK4: 30-35 s buffered and nothing loaded for 20 s, a network wait
            // and a reload played 2 s more each time, six times), likely a tunneled decoder given all of it. It's given no more than
            // 2 s ahead now (see DebugInfoMediaCodecVideoRenderer#shouldFeedInput); should it happen anyway, the engine plays without
            // tunneling from now on.
            if (VideoDecoderState.isTunneling()) {
                applyTunnelingOff(failure, true, TunnelingFallback.turnOff(getContext()));
            } else {
                failure.action("reload_video");
                mVideoLoaderController.reloadVideo(failure.getKind());
            }
        } else if (isOfflineVideo() && !mBufferingDetector.isPlayable() && isFirstStuckLoad()) {
            // One track's first segment came and the other's request hangs: the name resolved and the client's formats load, so
            // neither the DNS nor the client is to blame. Reloaded once for the video, then the fixes below.
            failure.action("stuck_load_reload").action("reload_video");
            mVideoLoaderController.reloadVideo(failure.getKind());
        } else if (isOfflineVideo() && !mBufferingDetector.isPlayable()) {
            int customDns = PlayerTweaksData.DNS_TYPE_GOOGLE;
            // What the player's new connections say of their lookup: only OkHttp's are told (the DNS setting is OkHttp's too), so
            // another engine's hang has nothing to go by, and the DNS isn't touched for it (S4PGCB6X #7: Cronet, the same timeouts
            // after the switch)
            Connections.Lookup lookup = Connections.getLookup();
            failure.detail("lookup", lookup.name().toLowerCase(Locale.US));

            // The DNS fix may be turned off in the DNS settings: the DNS chosen stays whatever happens (#93)
            if (lookup == Connections.Lookup.FAILING && getPlayerTweaksData().getPreferredDnsType() != customDns
                    && !mIsDnsFixRefuted && !getPlayerTweaksData().isNetworkErrorFixingDisabled()
                    && getPlayerTweaksData().isDnsFixEnabled()) {
                // The formats came, so the network is up, and the player's lookups failed or gave addresses none of which answer:
                // Google's DNS may give working ones. Kept if a video plays, put back if they fail the same way with it.
                // The DNS is read at each lookup (see PreferredDns in SharedModules): the engine's restart applies it, not the app's
                MessageHelpers.showLongMessage(getContext(), "Fixing incorrect DNS resolution...");
                mDnsFixFrom = getPlayerTweaksData().getPreferredDnsType();
                failure.action("dns_google").action("restart_engine");
                getPlayerTweaksData().setPreferredDnsType(customDns);
                mVideoLoaderController.restartEngine(failure.getKind());
            } else {
                // The DNS fix of the last start that hung didn't help: the lookups fail the same way with Google's
                if (lookup == Connections.Lookup.FAILING) {
                    undoDnsFix(failure);
                }

                long delayMs = RETRY_MS;
                // Its connections still in their lookup or connecting: no load error yet, the network all the same (QYZPW0FN #3: the
                // lookups hung 20 s, and the client was switched on a network that was down)
                String stuckConnection = Connections.getStuckConnection(STUCK_CONNECTION_MS);
                failure.detail("stuckConnection", stuckConnection);

                if (lookup == Connections.Lookup.FAILING || LoadErrors.hasConnectionErrorWithin(CONNECTION_ERROR_WINDOW_MS)
                        || stuckConnection != null) {
                    // A load's connection failed (it didn't connect, or no answer came): every client streams from the same servers,
                    // another one wouldn't help (FARAJV5R #46, S4PGCB6X #10, #11, #15). Tried again later each time. Lookups that
                    // fail are the connection's too, before a load has failed on them: a reload's were still going 9.7 s in, and
                    // the stall after the next reload had no load error to go by while the network was down (QYZPW0FN #3: the
                    // client was switched, and the videos opened half an hour later still came from it).
                    failure.action("network_wait");
                    undoStallClientSwitch(failure);
                    delayMs = mStallRetries.next(getVideo().videoId);
                } else {
                    // Also, some clients like ANDROID_REEL may just hang at start
                    MessageHelpers.showLongMessage(getContext(), "Fixing stalled client...");
                    failure.action("switch_next_client_now");
                    YouTubeServiceManager.instance().switchNextClientNow();
                    mStallSwitchVideoId = getVideo().videoId;
                }

                failure.action(delayed("reload_video", delayMs));
                mVideoLoaderController.reloadVideo((int) delayMs, failure.getKind());
            }
        } else if (isOfflineVideo() && isRepeatedStall()) {
            // A reload didn't help. Its loads failed on their connection (they didn't connect, or no answer came), or they came faster
            // than the format plays: the connection stops answering, it isn't too slow for the format, and a lower one stalls the same
            // way (KKSJMSQX #2: neither track loaded for 20 s at 30.7 Mbps of a 20.3 Mbps format; 1440p was lowered for the video, and
            // the read timed out again at 1080p, #4). Tried again later each time instead.
            boolean hasConnectionError = LoadErrors.hasConnectionErrorWithin(CONNECTION_ERROR_WINDOW_MS);
            failure.detail("loadConnectionError", hasConnectionError);
            String stuckConnection = Connections.getStuckConnection(STUCK_CONNECTION_MS);
            failure.detail("stuckConnection", stuckConnection);

            if (hasConnectionError || stuckConnection != null || FormatAttempts.isFastEnough()) {
                failure.action("network_wait");
                undoStallClientSwitch(failure);
                long delayMs = mStallRetries.next(getVideo() != null ? getVideo().videoId : null);
                failure.action(delayed("reload_video", delayMs));
                mVideoLoaderController.reloadVideo((int) delayMs, failure.getKind());
            } else if (lowerVideoQuality()) {
                // The connection can't keep up with this format right now. For this video only.
                failure.action("lower_quality").action("restart_engine");
                mVideoLoaderController.restartEngine(failure.getKind());
            } else {
                failure.action("reload_video");
                mVideoLoaderController.reloadVideo(failure.getKind());
            }
        } else if (isOfflineVideo() && isConnectionTooSlow(failure) && lowerVideoQuality()) {
            // The first stall, and its loads came slower than the format plays: a reload loads the same format as slowly (7QAT714C
            // #1: 4K at 9.9 Mbps of a 15.3 Mbps format, its reload didn't play in 5 s and the user left; #3: 4K at 15.4 of 26.0, the
            // reload stalled again, and two lowerings later it played at 1080p, three minutes on). For this video only.
            failure.action("lower_quality").action("restart_engine");
            mVideoLoaderController.restartEngine(failure.getKind());
        } else if (isLiveHeadStill()) {
            // The live stream's head stands still: its server answers the next segment isn't there yet, and goes on so. A reload seeks to
            // where the head would be by now and waits the same, from a black screen (GWS5HG2H #1-#3: 10 min at the head of a 24 h
            // stream, then a reload every 21 s, each with only the server's 5 s waits for the head, till the user left a minute later).
            // Waited in place: the picture stays, the player asks for the head as the server says (see DefaultSabrChunkSource), and
            // plays once it moves. Checked again a stall later (the detector is reset before this), the user told once.
            failure.action("live_head_wait");
            Video video = getVideo();

            if (video != null && !Helpers.equals(video.videoId, mLiveHeadWaitVideoId)) {
                mLiveHeadWaitVideoId = video.videoId;
                MessageHelpers.showLongMessage(getContext(), R.string.msg_live_head_wait);
            }

            mBufferingDetector.onStartBuffering();
        } else {
            // SABR may hang if the server issues a high backoffTime
            failure.action("reload_video");
            mVideoLoaderController.reloadVideo(failure.getKind());
        }
    }

    /**
     * A live stream's stall at its head: its server told the player's requests in the last LIVE_HEAD_WAIT_WINDOW_MS that the next
     * segment isn't there yet, and none of its loads failed on its connection (see runLongBufferingAction)
     */
    private boolean isLiveHeadStill() {
        Video video = getVideo();

        return getPlayer() != null && video != null && video.isLive && !video.isLiveEnd
                && LiveHeadWaits.countWithin(LIVE_HEAD_WAIT_WINDOW_MS) > 0
                && !LoadErrors.hasConnectionErrorWithin(CONNECTION_ERROR_WINDOW_MS)
                && Connections.getStuckConnection(STUCK_CONNECTION_MS) == null;
    }

    @Override
    public void onBuffering() {
        mBufferingDetector.onStartBuffering();
        FormatAttempts.onBuffering();
        Plays.onPlaying(false, getPositionMs());
    }

    @Override
    public void onSeekEnd() {
        mBufferingDetector.reset();
        // Needed to detect additional buffering (e.g. hanged clients).
        // Don't worry this event will be canceled by subsequent onPlay() or onPause() if everything is ok.
        // Only when it plays: paused where it never gets ready (e.g. past the media), neither comes, and the "stall" reloaded the
        // video from 0:00. Played from there, its buffering is checked (see onBuffering())
        if (getPlayer() != null && getPlayer().getPlayWhenReady()) {
            mBufferingDetector.onStartBuffering();
        }

        // The jump isn't the audio playing: measured from here
        if (getPlayer() != null) {
            mPlayStartPositionMs = getPlayer().getPositionMs();
        }

        FormatAttempts.onSeek();
    }

    @Override
    public void onSeekPositionChanged(long positionMs) {
        // Its buffering isn't a stall
        FormatAttempts.onSeek();
    }

    @Override
    public void onPlay() {
        mBufferingDetector.onStopBuffering();
        // A source failure after this tells the loads that failed since (see addSourceErrorDetails)
        LoadErrors.onPlay();
        mSabrErrorFixAttempts = 0;
        mStallRetries.reset();
        // A video played after it: the DNS fix stays
        mDnsFixFrom = -1;
        // It came: stuck again another time (e.g. opened again), it's reloaded again
        mStuckLoadVideoId = null;
        // Of the player that plays: another (e.g. a card's preview) may have played at its own speed since
        if (getPlayer() != null) {
            PlaybackSpeed.onChanged(getPlayer().getSpeed());
        }

        FormatAttempts.onPlaying(true);
        Plays.onPlaying(true, getPositionMs());
        Video video = getVideo();
        String videoId = video != null ? video.videoId : null;
        String client = mVideoLoaderController.getFormatClient();

        // A tunneled freeze the user paused (see onHealthFailure): played again, the player is made without tunneling
        if (mTunnelingRestartVideoId != null) {
            boolean isSameVideo = mTunnelingRestartVideoId.equals(videoId);
            mTunnelingRestartVideoId = null;

            if (isSameVideo && VideoDecoderState.isTunneling()) {
                mVideoLoaderController.restartEngine(PlaybackHealth.KIND_VIDEO_FROZEN);
            }
        }

        // Played again after a pause or a seek: from when it first played since the refusal
        if (!Helpers.equals(videoId, mSabrPlayedVideoId) || !Helpers.equals(client, mSabrPlayedClient)) {
            mSabrPlayedVideoId = videoId;
            mSabrPlayedClient = client;
            mSabrPlayedMs = SystemClock.elapsedRealtime();
        }

        if (client != null) {
            mPlayedVideoId = videoId;
            mPlayedClient = client;
            mPlayedMs = SystemClock.elapsedRealtime();
        }

        if (getPlayer() != null) {
            mPlayStartPositionMs = getPlayer().getPositionMs();
            mSoundBuffersAtPlay = SilenceDetector.getSoundBuffers();
            Utils.removeCallbacks(mCheckAudioPlayed);
            Utils.postDelayed(mCheckAudioPlayed, AUDIO_PLAYED_CHECK_MS);
        }
    }

    @Override
    public void onPause() {
        mBufferingDetector.onStopBuffering();
        Utils.removeCallbacks(mCheckAudioPlayed);
        FormatAttempts.onPlaying(false);
        Plays.onPlaying(false, getPositionMs());
    }

    /**
     * The formats reached the player: a fetch that fails after this is another time's
     */
    @Override
    public void onSourceChanged(Video item) {
        mFetchRetries.reset();
        mSourceChangedMs = SystemClock.elapsedRealtime();
    }

    @Override
    public void onNewVideo(Video item) {
        mBufferingDetector.start();
        FormatAttempts.onNewVideo(getContext(), item != null ? item.videoId : null);
        PlayerCommands.onNewVideo(item != null ? item.videoId : null);
        Plays.onNewVideo(item != null ? item.videoId : null);
        // OkHttp was for the video it was asked for: the chosen engine again for another one
        NetworkEngineFallback.onNewVideo(item != null ? item.videoId : null);

        if (item != null && item.videoId != null && item.videoId.equals(mDecoderErrorVideoId)) {
            // The same video reloaded (e.g. on a SABR error). The reload cleared the temp formats, the fallback still applies.
            reapplyDecoderErrorFallback();
        } else {
            mDecoderErrorVideoId = item != null ? item.videoId : null;
            mDecoderErrorCount = 0;
            mAudioInitErrorCount = 0;
            mVideoFallback = null;
            mAudioFallback = null;
            mSpeedCodecVideoId = null;
        }
    }

    @Override
    public void onFinish() {
        mBufferingDetector.reset();
        Utils.removeCallbacks(mCheckAudioPlayed);
        FormatAttempts.end(getContext());
        Plays.onFinish(getPositionMs());
    }

    /**
     * The diagnostics kept in memory are written: in the background the app may be killed
     */
    @Override
    public void onViewPaused() {
        flushDiagnostics();
        Plays.onViewPaused(getPositionMs());
    }

    @Override
    public void onViewResumed() {
        Plays.onViewResumed();
    }

    /**
     * Where the video is, -1 when not known (e.g. the player is gone)
     */
    private long getPositionMs() {
        return getPlayer() != null ? getPlayer().getPositionMs() : -1;
    }

    private void flushDiagnostics() {
        Context context = getContext();

        if (context == null) {
            return;
        }

        try {
            FormatAttempts.flush(context, false);
            PlayerFailureLog.instance(context).flush();
        } catch (RuntimeException e) {
            Log.e(TAG, "Can't write the diagnostics: %s", e.getMessage());
        }
    }

    /**
     * The formats the player tries, for the failures of the video (see FormatAttempts)
     */
    @Override
    public void onTrackChanged(FormatItem track) {
        if (getPlayer() == null) {
            return;
        }

        try {
            FormatAttempts.onTracks(getPlayer().getVideoFormat(), getPlayer().getAudioFormat());
            FormatAttempts.onOffered(getPlayer().getVideoFormats(), getPlayer().getAudioFormats());
        } catch (RuntimeException e) {
            // Diagnostics only: the playback goes on without them
            Log.e(TAG, "Can't read the formats tried: %s", e.getMessage());
        }

        // The video's codec is known now: one that drops frames at this speed is swapped before it plays (the speed may be set before)
        applySpeedCodec(getPlayer().getSpeed());
    }

    /**
     * Faster now, the codec playing may drop frames at the rate (see #applySpeedCodec): its tracks may be known before. The speed told,
     * not the player's: it reads the one before till its playback thread takes it (ExoPlayerImpl#getPlaybackParameters), and the
     * switch didn't come at 1.5x on the emulator.
     */
    @Override
    public void onSpeedChanged(float speed) {
        applySpeedCodec(speed);
    }

    @Override
    public void onEngineReleased() {
        mBufferingDetector.reset();
        Utils.removeCallbacks(mCheckAudioPlayed);
        FormatAttempts.onPlaying(false);
        // Still there: where the video was when its player went (see PlaybackFragment#releasePlayer)
        Plays.onPlaying(false, getPositionMs());
    }

    /**
     * The error is of the video or the audio of the formats tried (see FormatAttempts). Others (e.g. a 403 of the whole stream)
     * tell nothing of either.
     */
    private static void markFailedStream(int type, int rendererIndex, Throwable error) {
        Boolean isVideo = null;

        String decoderName = null;

        if (error instanceof DecoderErrorException) {
            int trackType = ((DecoderErrorException) error).trackType;
            isVideo = trackType == C.TRACK_TYPE_VIDEO ? Boolean.TRUE : trackType == C.TRACK_TYPE_AUDIO ? Boolean.FALSE : null;
            decoderName = ((DecoderErrorException) error).decoderName;
        } else if (error instanceof DecoderInitializationException) {
            String mimeType = ((DecoderInitializationException) error).mimeType;
            isVideo = MimeTypes.isVideo(mimeType) ? Boolean.TRUE : MimeTypes.isAudio(mimeType) ? Boolean.FALSE : null;
            // Null: no decoder takes it
            String name = ((DecoderInitializationException) error).decoderName;
            decoderName = name != null ? name : "none";
        } else if (error instanceof AudioSink.InitializationException || error instanceof AudioSink.WriteException
                || error instanceof AudioSink.ConfigurationException) {
            isVideo = false;
        } else if (type == PlayerEventListener.ERROR_TYPE_RENDERER || type == PlayerEventListener.ERROR_TYPE_SOURCE) {
            isVideo = rendererIndex == PlayerEventListener.RENDERER_INDEX_VIDEO ? Boolean.TRUE :
                    rendererIndex == PlayerEventListener.RENDERER_INDEX_AUDIO ? Boolean.FALSE : null;
        }

        if (isVideo != null) {
            FormatAttempts.onDecoder(isVideo, decoderName);
            FormatAttempts.onStreamFailed(isVideo);
        }
    }

    private void runEngineErrorAction(int type, int rendererIndex, Throwable error, PlayerFailure failure) {
        // Hide begin errors in embed mode (e.g. wrong date/time: unable to connect to...)
        if (isEmbedPlayer() && getPlayer() != null && getPlayer().getPositionMs() == 0) {
            failure.action("finish_embed");
            getPlayer().finish();
            return;
        }

        if (isStreamEnded() || isAtVideoEnd(VIDEO_END_ERROR_THRESHOLD_MS)) {
            // Url no longer works (e.g. live stream ended), or a video failed in its last seconds (e.g. draining its sound at the end
            // of the stream): it ended. A restart there plays nothing more, and its error message is about a video that played.
            failure.action("play_end");
            getMainController().onPlayEnd();
            return;
        }

        checkLiveEnd(failure);

        if (applySabrReloadPlayerAction(error, failure)) {
            return;
        }

        if (applySabrBackoffAction(error, failure)) {
            return;
        }

        if (applySabrPoTokenAction(error, failure)) {
            return;
        }

        if (applySabrRefusedAction(error, failure)) {
            return;
        }

        if (applySabrProtocolErrorAction(error, failure)) {
            return;
        }

        if (mVideoLoaderController.isFixPending()) {
            // The player a reload or a restart replaces failed on its way out, e.g. its load that hung timed out (KKSJMSQX #3: 565 ms
            // after a stall's restart was asked for). The fix asked for covers it: another one put the restart off and showed the
            // error.
            failure.action("fix_pending");
            return;
        }

        if (applySabrTimeoutAction(error, failure)) {
            return;
        }

        applyEngineErrorAction(type, rendererIndex, error, failure);
    }

    /**
     * SABR server sent ReloadPlayerResponse. It won't send any media until the player response is re-fetched with the token.
     */
    private boolean applySabrReloadPlayerAction(Throwable error, PlayerFailure failure) {
        SabrReloadPlayerError reloadError = null;
        Video video = getVideo();

        Throwable cause = error.getCause();
        if (cause instanceof SabrReloadPlayerError) {
            reloadError = (SabrReloadPlayerError) cause;
        }

        if (reloadError == null || video == null) {
            return false;
        }

        if (!Helpers.equals(video.videoId, mSabrErrorFixVideoId)) {
            mSabrErrorFixVideoId = video.videoId;
            mSabrErrorFixAttempts = 0;
        }

        // Reload doesn't help. Fallback to the common error handling (switch the client).
        if (++mSabrErrorFixAttempts > MAX_SABR_ERROR_FIX_ATTEMPTS) {
            return false;
        }

        // The first is the server's routine ask (e.g. its session expired in a long pause): reloaded, it plays on without a word
        if (mSabrErrorFixAttempts > 1) {
            MessageHelpers.showLongMessage(getContext(), "%s. Attempt: %s", reloadError.getMessage(), mSabrErrorFixAttempts);
        }

        failure.action("sabr_reload " + mSabrErrorFixAttempts + "/" + MAX_SABR_ERROR_FIX_ATTEMPTS).action("reload_video");
        YouTubeServiceManager.instance().reloadPlayerResponse(video.videoId, reloadError.reloadPlaybackToken);
        // At once: the error came when the buffer ran out, the picture stands still till the video plays again. A second's wait was
        // more than the reload took (FARAJV5R #100: 674 ms to its first frame).
        mVideoLoaderController.reloadVideo(0, failure.getKind());

        return true;
    }

    /**
     * The server told us to back off, honor it instead of re-requesting
     * immediately - otherwise we burn exactly the waiting period the server asked for.
     */
    private boolean applySabrBackoffAction(Throwable error, PlayerFailure failure) {
        SabrBackoffError backoffError = null;
        Video video = getVideo();

        Throwable cause = error.getCause();
        if (cause instanceof SabrBackoffError) {
            backoffError = (SabrBackoffError) cause;
        }

        if (backoffError == null || video == null) {
            return false;
        }

        if (!Helpers.equals(video.videoId, mSabrErrorFixVideoId)) {
            mSabrErrorFixVideoId = video.videoId;
            mSabrErrorFixAttempts = 0;
        }

        // Reload doesn't help. Fallback to the common error handling (switch the client).
        if (++mSabrErrorFixAttempts > MAX_SABR_ERROR_FIX_ATTEMPTS) {
            return false;
        }

        MessageHelpers.showLongMessage(getContext(), "%s. Attempt: %s", backoffError.getMessage(), mSabrErrorFixAttempts);

        failure.action("sabr_backoff " + mSabrErrorFixAttempts + "/" + MAX_SABR_ERROR_FIX_ATTEMPTS).action("reload_video");
        // SABR may hang if the server issues a high backoffTime
        mVideoLoaderController.reloadVideo(failure.getKind());

        return true;
    }

    /**
     * SABR server requires attestation, or it's still pending: it won't send media past its grace period (about a minute) with this
     * poToken, at the same position after every reload. A TV token it rejects goes (e.g. the WebView's BotGuard makes bad ones): the
     * client streams without one, like before it had any. Otherwise another client: a pending TV token was a fresh one (51AQC7FS,
     * 0KPV79TF: the app had run under a minute), and without one the server required one (5V8J002W #11). Counted toward the wall's lap
     * (see countWallSwitch).
     */
    private boolean applySabrPoTokenAction(Throwable error, PlayerFailure failure) {
        Throwable cause = error.getCause();

        if (!(cause instanceof PoTokenError) || getVideo() == null) {
            return false;
        }

        PoTokenStatus status = ((PoTokenError) cause).status;
        // What the server made of it, as the error says: the protection statuses told may be of another stream (97Z1YWD3: PENDING on
        // the web clients, PENDING_MISSING on IOS, which sends none)
        failure.detail("poTokenStatus", status != null ? status.name() : null);

        if (status == PoTokenStatus.INVALID && YouTubeServiceManager.instance().rejectPoToken(mVideoLoaderController.getFormatClient())) {
            failure.action("reject_potoken").action("reload_video");
            mVideoLoaderController.reloadVideo(failure.getKind());
        } else {
            applyAttestationWallSwitch(failure);
        }

        return true;
    }

    /**
     * SABR's media server refused the request (HTTP 401 or 403, see SabrDefaultLoadErrorHandlingPolicy#isRefused) while the poToken's
     * attestation was pending: past its grace period, the server refuses it at the same position after every retry and app restart
     * (CX17FXZW #1-#4: 401s 7 times a minute, protection PENDING since 23 s before). The server holding back its media this way is a
     * PoTokenError of its own (see applySabrPoTokenAction): another client, counted toward the wall's lap (see countWallSwitch). Refused
     * otherwise, see applySabrRefusedRefetch.
     */
    private boolean applySabrRefusedAction(Throwable error, PlayerFailure failure) {
        if (getVideo() == null || !SabrDefaultLoadErrorHandlingPolicy.isRefused(error) || !isAttestationPending()) {
            return false;
        }

        applyAttestationWallSwitch(failure);

        return true;
    }

    /**
     * A SABR request got no answer (e.g. its read timed out, see isConnectionError) while the poToken's attestation was pending: the
     * server holding back the media, as a stall of it is (see runLongBufferingAction), not the network. 65FDWR9C #1: protection went
     * from OK to PENDING 58.6 s before, in a 21 h pause, and the engine restart on the same client played 0 ms before the user left.
     * After a fix asked for already (see runEngineErrorAction): the player it replaces may time out on its way out.
     */
    private boolean applySabrTimeoutAction(Throwable error, PlayerFailure failure) {
        // Within the grace period the server still sends: a timeout then is the network's, reloaded as one (see applyEngineErrorAction)
        if (!isConnectionError(error) || !isAtAttestationWall()) {
            return false;
        }

        applyAttestationWallSwitch(failure);

        return true;
    }

    /**
     * SABR's server holds back the media while the poToken's attestation is pending, past its grace period (see isAttestationPending):
     * the next client, and the video again, counted toward the wall's lap (see countWallSwitch). Every client had its try: it stops
     * there, and says why.
     */
    private void applyAttestationWallSwitch(PlayerFailure failure) {
        // The player a switch asked for a moment ago replaces failed on its way out: its formats are the client counted already, and
        // counting it again would end the lap after one client
        if (mVideoLoaderController.isFixPending()) {
            failure.action("fix_pending");
            return;
        }

        if (!countWallSwitch(failure)) {
            stopAtAttestationWall(failure);
            return;
        }

        failure.action("switch_next_client_now").action("reload_video");
        YouTubeServiceManager.instance().switchNextClientNow();
        mVideoLoaderController.reloadVideo(failure.getKind());
    }

    /**
     * The client is switched for the attestation wall: past its grace period (about the video's first minute), SABR's server holds back
     * the media of a poToken whose attestation is pending, and IOS's fresh sessions are refused. Counted for the video while it never
     * plays past where it stopped. Signed out, every client of the order failed it: the TV ones asked for a sign-in, the web ones'
     * attestation stayed pending, IOS's sessions were refused; the app went from client to client at 0:59.9, a reload each time, for 2
     * and 3.5 minutes till the user left (97Z1YWD3 #1-#23, 45 errors). Both MediaServiceCore's switch and the client picked for the video
     * (see VideoLoaderController#pickNextClientForVideo) go round the order: once a client its formats came from comes round again,
     * every client of it had its try.
     *
     * @return false when every client had its try: the caller stops (see stopAtAttestationWall), it neither switches nor reloads
     */
    private boolean countWallSwitch(PlayerFailure failure) {
        Video video = getVideo();

        if (video == null || video.videoId == null) {
            return true;
        }

        long positionMs = getPlayer() != null ? getPlayer().getPositionMs() : -1;
        long nowMs = SystemClock.elapsedRealtime();

        // Another video, this one played past the wall since, or its lap left long ago: a lap of its own
        if (!video.videoId.equals(mWallVideoId) || positionMs > mWallPositionMs + WALL_PASSED_MS || nowMs - mWallSwitchMs > WALL_LAP_GAP_MS) {
            resetWall();
            mWallVideoId = video.videoId;
        }

        mWallSwitchMs = nowMs;

        mWallPositionMs = Math.max(mWallPositionMs, positionMs);
        mIsWallSignInAsked |= isSignInAsked(ClientAttempts.get(video.videoId));
        // What it played on the client was the server's grace, not the client playing it
        mVideoLoaderController.forgetWallClientPick();

        String client = mVideoLoaderController.getFormatClient();
        boolean isLapDone = client != null && mWallClients.contains(client)
                || mWallSwitches >= MediaServiceData.instance().getClientOrder().size();

        if (!isLapDone) {
            mWallSwitches++;

            if (client != null) {
                mWallClients.add(client);
            }
        }

        // The clients of its lap so far, this one's included
        failure.detail("wallClients", new JSONArray(mWallClients));

        return !isLapDone;
    }

    /**
     * Every client had its try at the attestation wall (see countWallSwitch): nothing more is reloaded, and the user is told why, paused
     * where it stopped. Signed out, or asked by a client for a sign-in (a bot check), to sign in: signed in on the same box, TV played
     * every start (97Z1YWD3). Played or opened again, it has another lap.
     */
    private void stopAtAttestationWall(PlayerFailure failure) {
        boolean isSignInToTell = mIsWallSignInAsked || !isSignedIn();
        failure.action(isSignInToTell ? "attestation_stop_sign_in" : "attestation_stop");
        resetWall();
        Context context = getContext();

        if (context == null) {
            return;
        }

        String message = context.getString(isSignInToTell ? R.string.msg_attestation_sign_in : R.string.msg_attestation_wall);
        MessageHelpers.showLongMessage(context, message);

        if (getPlayer() != null) {
            getPlayer().setPlayWhenReady(false);
            // In place of the error's title: what the user can do about it
            getPlayer().setTitle(message);
            getPlayer().showProgressBar(false);
            // The title is seen only with the controls
            getPlayer().showOverlay(true);
        }
    }

    private void resetWall() {
        mWallVideoId = null;
        mWallPositionMs = -1;
        mWallClients.clear();
        mWallSwitches = 0;
        mIsWallSignInAsked = false;
    }

    /**
     * A client asked for a sign-in, not for the viewer's age: a bot check (97Z1YWD3, signed out: TV, TV_SAMSUNG and TV_DOWNGRADED, "sign
     * in to confirm you're not a bot")
     */
    private static boolean isSignInAsked(@Nullable List<ClientAttempts.Attempt> attempts) {
        if (attempts == null) {
            return false;
        }

        for (ClientAttempts.Attempt attempt : attempts) {
            if (!attempt.ageGated && STATUS_LOGIN_REQUIRED.equals(attempt.status)) {
                return true;
            }
        }

        return false;
    }

    /**
     * An account is signed in. Not the account manager's: a test's.
     */
    boolean isSignedIn() {
        return YouTubeSignInService.instance().isSigned();
    }

    /**
     * SABR's server refused the request with a SabrError (see SabrProtocolError), e.g. sabr.malformed_config: the player response's
     * ustreamer config no longer applies, and every request after it is refused. Sent again, the request gets the same answer: the
     * player response is fetched again, with the same client. Refused again soon after, or refused with a player response the stream
     * had just fetched again itself (the server asked it to, see SabrManifest#requestSessionRefresh), the fresh one didn't help: the
     * next client right away, for this video only (see VideoLoaderController#pickNextClientForVideo). Not its poToken first (see
     * YouTubeServiceManager#switchNextClient): that one may be reset again a minute later, so a client refused each time after that
     * was never left.<br/>
     * Unless the video played on the client since the refusal before: that's of the stream, not of the client. 0CW972QK and H8ZP1GPV:
     * TV played Hf-CqpBeDs0 15-40 s at a time, each time refused 38-95 ms into a session the stream had just swapped in (see
     * SabrManifest#refreshSession), during a burst of seeks, and the clients it was moved to failed it (503s, a poToken pending) for
     * 6-15 s a lap. The player response is fetched again and the video reloaded on it: a whole new stream. Refused again before it
     * played, the next client.<br/>
     * Not reset when it plays: a refused player response plays what it loaded before.
     */
    private boolean applySabrProtocolErrorAction(Throwable error, PlayerFailure failure) {
        Throwable cause = error.getCause();
        Video video = getVideo();

        if (!(cause instanceof SabrProtocolError) || video == null) {
            return false;
        }

        SabrProtocolError protocolError = (SabrProtocolError) cause;
        // What the refused request asked for, and how fresh its session was (see SabrManifest#describeRequest)
        failure.detail("sabrRequest", protocolError.getRequest());
        long sessionAgeMs = protocolError.getSessionAgeMs();
        failure.detail("sabrSessionAgeMs", sessionAgeMs >= 0 ? sessionAgeMs : null);

        long nowMs = SystemClock.elapsedRealtime();
        boolean isRepeated = Helpers.equals(video.videoId, mSabrProtocolErrorVideoId) && nowMs - mSabrProtocolErrorTimeMs <= REPEATED_SABR_ERROR_MS;
        // The server had asked for the player response again, the stream fetched it, and the fresh one was refused too: fetched once
        // more, it's refused the same (89HAAW4J, XWR8QQN7, F0SVV9N8: refused, refetched, refused again, 1.8 s each time)
        boolean isFreshSessionRefused = sessionAgeMs >= 0 && sessionAgeMs <= FRESH_SABR_SESSION_MS;
        String client = mVideoLoaderController.getFormatClient();
        // Once per play: refused again before it plays a while, it's the client's
        boolean hasPlayedOnClient = client != null && mSabrPlayedVideoId != null && mSabrPlayedVideoId.equals(video.videoId)
                && client.equals(mSabrPlayedClient) && nowMs - mSabrPlayedMs >= SABR_PLAYED_MS;
        mSabrPlayedVideoId = null;
        mSabrPlayedClient = null;

        if ((isRepeated || isFreshSessionRefused) && hasPlayedOnClient) {
            failure.detail("sabrPlayedOnClient", true);
        }

        if ((isRepeated || isFreshSessionRefused) && !hasPlayedOnClient) {
            // A fresh session refused with the attestation pending is the wall's (see countWallSwitch): 97Z1YWD3's IOS, which sends no
            // poToken (PENDING_MISSING), was refused sabr.no_video_selected 30-200 ms into each, ten times
            if (isFreshSessionRefused && isAtAttestationWall() && !mVideoLoaderController.isFixPending() && !countWallSwitch(failure)) {
                stopAtAttestationWall(failure);
                return true;
            }

            MessageHelpers.showLongMessage(getContext(), cause.getMessage());
            // The client refuses this video: the next one for it only, remembered for when it's opened again. The other videos play
            // on the order's first (89HAAW4J: TV played the others, and the switch kept TV_SAMSUNG for all of them).
            String picked = mVideoLoaderController.pickNextClientForVideo();

            if (picked != null) {
                failure.action("client_for_video:" + picked);
            } else {
                failure.action("switch_next_client_now");
                YouTubeServiceManager.instance().switchNextClientNow();
            }
        } else {
            failure.action("refetch_player");
            YouTubeServiceManager.instance().reloadPlayerResponse(video.videoId, null);
        }

        mSabrProtocolErrorVideoId = video.videoId;
        mSabrProtocolErrorTimeMs = nowMs;
        failure.action("reload_video");
        mVideoLoaderController.reloadVideo(failure.getKind());

        return true;
    }

    /**
     * Tunneling was turned off (see TunnelingFallback#turnOff): the engine restarts without it, or with a freeze that's over (a start's,
     * see onHealthFailure), the next player is made without it. Turned off in another run before, it's suspended on the device from now
     * on, and the message says so.
     *
     * @param isRestarted the engine restarts now
     * @param isTurnedOff it was on till now (see TunnelingFallback#turnOff): the user is told, once
     */
    private void applyTunnelingOff(PlayerFailure failure, boolean isRestarted, boolean isTurnedOff) {
        boolean isSuspended = TunnelingFallback.isSuspended(getContext());
        failure.action(isSuspended ? "tunneling_suspended" : "tunneling_off");

        if (isTurnedOff) {
            MessageHelpers.showLongMessage(getContext(), isSuspended ? R.string.msg_tunneling_suspended : R.string.msg_tunneling_off);
        }

        if (isRestarted) {
            failure.action("restart_engine");
            mVideoLoaderController.restartEngine(failure.getKind());
        }
    }

    /**
     * The video's stall switched the client as one that hangs at start (see runLongBufferingAction), and its failure now is the
     * network's: the switch was the outage's, the first client again (QYZPW0FN: switched to VISIONOS during an outage, kept for every
     * video of the run after it)
     */
    private void undoStallClientSwitch(PlayerFailure failure) {
        Video video = getVideo();

        if (video == null || mStallSwitchVideoId == null || !mStallSwitchVideoId.equals(video.videoId)) {
            return;
        }

        mStallSwitchVideoId = null;
        failure.action("reset_client");
        YouTubeServiceManager.instance().resetClient();
    }

    /**
     * The start hangs on a first segment that never came while the other track's did (see VideoStarts#isOneTrackStuck), the first
     * time for this video
     */
    private boolean isFirstStuckLoad() {
        Video video = getVideo();

        if (video == null || Helpers.equals(video.videoId, mStuckLoadVideoId) || !VideoStarts.isOneTrackStuck()) {
            return false;
        }

        mStuckLoadVideoId = video.videoId;

        return true;
    }

    /**
     * The connection to the server broke, or the response it sent did, not its answer (e.g. a read timeout, not a 403): another
     * client would stream from the same servers. Up to MAX_NETWORK_ERROR_RELOADS reloads a video, then the client is switched.
     */
    private boolean applyNetworkErrorReload(Throwable error, PlayerFailure failure) {
        Video video = getVideo();

        if (video == null || !(isConnectionError(error) || isBrokenResponse(error))) {
            return false;
        }

        if (!Helpers.equals(video.videoId, mNetworkErrorVideoId)) {
            mNetworkErrorVideoId = video.videoId;
            mNetworkErrorReloads = 0;
        }

        if (++mNetworkErrorReloads > MAX_NETWORK_ERROR_RELOADS) {
            return false;
        }

        failure.action("network_reload " + mNetworkErrorReloads + "/" + MAX_NETWORK_ERROR_RELOADS);

        if (isBrokenResponse(error)) {
            // 15 of the 16 broken SABR responses whose commands were known came 52-786 ms after a seek, OkHttp's too (see
            // PlayerCommands#getMsSinceSeek): said with each, not read from the commands
            long msSinceSeek = PlayerCommands.getMsSinceSeek();
            failure.detail("msSinceSeek", msSinceSeek >= 0 ? msSinceSeek : null);
            applyOkHttpFallback(video, failure);
        }

        return true;
    }

    /**
     * The player's request failed on its connection while the device has no network, or the request says it has none (Cronet's
     * ERR_INTERNET_DISCONNECTED, see ConnectionErrors#DISCONNECTED): no engine or client is to blame, and the engine switches of
     * applyEngineErrorAction are saved in the settings. 3TKGJY3Z #3-#5: a Wi-Fi drop of about 30 s, no network in each failure's state;
     * Cronet's ERR_INTERNET_DISCONNECTED set the default engine, the default's name that didn't resolve switched it to OkHttp, and the
     * user's Cronet was gone for good. Waited out instead, as a fetch that can't connect is (see loadFormatInfo), each wait longer
     * till it plays. A connection that fails with the network up is fixed as before.
     */
    private boolean applyNetworkDownWait(Throwable error, PlayerFailure failure) {
        Video video = getVideo();

        if (video == null || !LoadErrors.isConnectionError(error)
                || (hasActiveNetwork() && !ConnectionErrors.DISCONNECTED.equals(ConnectionErrors.describe(error)))) {
            return false;
        }

        failure.action("network_wait");
        undoStallClientSwitch(failure);
        reloadLater(failure, mStallRetries.next(video.videoId));

        return true;
    }

    /**
     * The device has a network, validated or not (a network that never validates, its check blocked, still plays): true when it can't
     * be told. Not the system's: a test's.
     */
    boolean hasActiveNetwork() {
        Context context = getContext();
        ConnectivityManager manager = context != null ? (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE) : null;

        if (manager == null) {
            return true;
        }

        try {
            Network network = manager.getActiveNetwork();

            return network != null && manager.getNetworkCapabilities(network) != null;
        } catch (RuntimeException e) { // e.g. a SecurityException of a vendor's build: unknown, as before
            return true;
        }
    }

    /**
     * A SABR response read out of step happened with Cronet or the default engine 20 times in 628 plays, and with OkHttp none in 679
     * (2026-11-19's submissions: FVNAAYZD, RF6HBNNH, F8DMFX2M, 84ENEA2S, 2Q5RK79E, DMQEWB9B), so the video's reload read with OkHttp.
     * OkHttp did it too: Z6R2NPPX #2-#4 (32.70.5, 32.71.3, okhttp_no_ipv6), 0.2-0.3 s after a seek each, as 15 of those 16 whose
     * commands were known. Their cause was a load a seek cancelled reading on into the next response, whatever the engine, fixed in
     * 32.80.1 (see SabrExtractorInput). From 32.80.1 on, the 18 submissions of 2026-11-25 had none in about 200 SABR starts on Cronet
     * and 230 on OkHttp. Kept for the one in 16 that came with no seek, till more of them say whether any is left on another engine:
     * it does nothing till one is, for one video, and a response broken again on OkHttp says it isn't the engine's (its failure has the
     * engine in use). For this video only, the engine saved in the settings is the user's (see NetworkEngineFallback). The reload builds
     * the player's connections anew (see ExoMediaSourceFactory#releaseIfEngineChanged).
     */
    private void applyOkHttpFallback(Video video, PlayerFailure failure) {
        if (getPlayerTweaksData().isNetworkErrorFixingDisabled()) {
            return;
        }

        String engine = null;

        try {
            engine = Utils.getEffectiveNetworkEngine(getContext());
        } catch (RuntimeException e) {
            // Cronet couldn't be checked: it's not OkHttp that's in use
        }

        if (Helpers.equalsAny(engine, Utils.NETWORK_ENGINE_OKHTTP, Utils.NETWORK_ENGINE_OKHTTP_NO_IPV6)) {
            return;
        }

        failure.action("engine_okhttp_for_video");
        NetworkEngineFallback.useOkHttpFor(video.videoId);
    }

    /**
     * The response read had bytes its format doesn't start with, broken on the way or read out of step, not the client's formats: a
     * SABR one no part starts with (see SabrStream#nextKnownUMPPart), a part whose size can't be skipped or whose payload isn't its
     * protobuf (DMQEWB9B #1, FVNAAYZD #13 and #15: these were the client's switch), or a segment the extractor can't parse once the
     * video played (CX17FXZW #13: "Skipping atom with length > 2147483647" in a DASH segment, right after a 17.7 s stall at 706 kbps).
     * One that fails the same way each time is the client's after MAX_NETWORK_ERROR_RELOADS.
     */
    private static boolean isBrokenResponse(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof BrokenResponseError || cause instanceof ParserException) {
                return true;
            }
        }

        return false;
    }

    /**
     * The data source failed on the connection: an I/O error under it. An answer of the server (a response code, a content type)
     * has none. SABR wraps it in an IllegalStateException (see UMPDecoder).
     */
    private static boolean isConnectionError(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpDataSourceException) {
                return cause.getCause() instanceof IOException;
            }
        }

        return false;
    }

    /**
     * The stream's server failed its segments (HTTP 5xx, e.g. 503): the next client for this video only, as a SABR refusal's (see
     * applySabrProtocolErrorAction), remembered once it plays there. The other videos keep the order's first client. A switch of the
     * client for all of them kept the run on the client it ended on (97MSK8B6 #2, #4, #7: TV_SAMSUNG's, VISIONOS's and
     * TV_DOWNGRADED's DASH answered 503, and every video after them played on WEB, each start waiting 4–4.8 s for its server's
     * backoff, where TV's SABR waited none in 1,488 starts).
     *
     * @return whether a client was picked, false for none to pick (e.g. the order has one client)
     */
    private boolean applyServerErrorClientPick(Throwable error, PlayerFailure failure) {
        if (getVideo() == null || !isServerError(error)) {
            return false;
        }

        String picked = mVideoLoaderController.pickNextClientForVideo();

        if (picked == null) {
            return false;
        }

        failure.action("client_for_video:" + picked);

        return true;
    }

    /**
     * The server answered the stream's request with an HTTP 5xx code
     */
    @VisibleForTesting
    static boolean isServerError(@Nullable Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidResponseCodeException) {
                int code = ((InvalidResponseCodeException) cause).responseCode;
                return code >= 500 && code < 600;
            }
        }

        return false;
    }

    /**
     * SABR's server refused a request (HTTP 401 or 403) of a video that played, its poToken's attestation not pending (see
     * applySabrRefusedAction): its player response is fetched again first, with the same poToken, as a SabrError's is (see
     * applySabrProtocolErrorAction). WVE7ZKDD #2: refused on a rewind 87 s into a session whose protection was OK; switch_next_client
     * made TV's poToken again, 2.2 s of a 5.3 s reload, and the reload played 15 min on TV. Refused again within REPEATED_SABR_ERROR_MS,
     * the poToken is made again or the client switched (see switchNextClient).
     */
    private boolean applySabrRefusedRefetch(Throwable error, PlayerFailure failure) {
        Video video = getVideo();

        if (video == null || !PlayerFailure.PROTOCOL_SABR.equals(mVideoLoaderController.getFormatProtocol())
                || !SabrDefaultLoadErrorHandlingPolicy.isRefused(error)) {
            return false;
        }

        long nowMs = SystemClock.elapsedRealtime();

        if (Helpers.equals(video.videoId, mSabrRefetchVideoId) && nowMs - mSabrRefetchMs <= REPEATED_SABR_ERROR_MS) {
            return false;
        }

        mSabrRefetchVideoId = video.videoId;
        mSabrRefetchMs = nowMs;
        failure.action("refetch_player");
        YouTubeServiceManager.instance().reloadPlayerResponse(video.videoId, null);

        return true;
    }

    /**
     * The next client for every video, or the same client with its poToken made again when it can be (see
     * YouTubeServiceManager#switchNextClientOrPoToken): the action says which. It read switch_next_client when the reload stayed on TV
     * with a new poToken (WVE7ZKDD #2), as 65FDWR9C #2-#7's first one stayed on WEB.
     *
     * @return the action, e.g. "switch_next_client:WEB", "reset_potoken:TV"
     */
    private String switchNextClient() {
        String client = mVideoLoaderController.getFormatClient();
        String next = switchNextClientOrPoToken();

        return next != null ? "switch_next_client:" + next : "reset_potoken" + (client != null ? ":" + client : "");
    }

    /**
     * See YouTubeServiceManager#switchNextClientOrPoToken. Not the service's: a test's.
     */
    @Nullable
    String switchNextClientOrPoToken() {
        return YouTubeServiceManager.instance().switchNextClientOrPoToken();
    }

    /**
     * An "Unable to connect" error may switch the engine: the user lets the app fix network errors, and IPv6 connects (while it doesn't,
     * the player stays on OkHttp till the app restarts, see ExoMediaSourceFactory)
     */
    private boolean isEngineSwitchable() {
        return !getPlayerTweaksData().isNetworkErrorFixingDisabled() && !OkHttpManager.isIpv6Disabled();
    }

    private void applyEngineErrorAction(int type, int rendererIndex, Throwable error, PlayerFailure failure) {
        boolean restartEngine = true;
        long restartDelayMs = RETRY_MS;
        boolean showMessage = true;
        String errorContent = error != null ? error.getMessage() : null;
        String errorTitle = getErrorTitle(type, rendererIndex, error);
        String errorMessage = errorTitle + "\n" + errorContent;
        Log.e(TAG, errorMessage);

        if (applyNetworkDownWait(error, failure)) {
            return;
        }

        if (Helpers.startsWithAny(errorContent, "Unable to connect to") && mBufferingDetector.isPlayable() && isConnectionError(error)) {
            // It played, and its connection broke (e.g. a read timed out): not the device's date nor no internet. Counted for the video
            // and reloaded without a word, as a broken connection of the source is (see applyNetworkErrorReload), then the next client
            // for this video only. R71TW3F4 #1-#13: WEB's server timed out every 40 s, and 13 engine restarts with nothing changed played
            // 40 s each, with ~7 s of black screen and an error toast every time; TV played that video 17 min on the same device.
            restartEngine = false;
            showMessage = false;

            if (applyNetworkErrorReload(error, failure)) {
                // The first time, another engine as before: it needs the engine restarted
                if (mNetworkErrorReloads == 1 && isEngineSwitchable()) {
                    failure.action("switch_engine:" + Utils.getNetworkEngineId(switchNextEngine()));
                    restartEngine = true;
                }
            } else {
                String picked = mVideoLoaderController.pickNextClientForVideo();

                if (picked != null) {
                    failure.action("client_for_video:" + picked);
                } else {
                    // No other client (e.g. the order has one): as before
                    if (isEngineSwitchable()) {
                        failure.action("switch_engine:" + Utils.getNetworkEngineId(switchNextEngine()));
                    }

                    restartEngine = true;
                    showMessage = true;
                }
            }
        } else if (Helpers.startsWithAny(errorContent, "Unable to connect to")) {
            // No internet connection or WRONG DATE on the device
            // Recently this message starting to show for other unknown reasons
            // Not while IPv6 doesn't connect: the player stays on OkHttp till the app restarts (see ExoMediaSourceFactory)
            if (isEngineSwitchable()) {
                failure.action("switch_engine:" + Utils.getNetworkEngineId(switchNextEngine()));
            }
        } else if (error instanceof OutOfMemoryError || (error != null && error.getCause() instanceof OutOfMemoryError)) {
            if (getPlayerTweaksData().getPlayerDataSource() == PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP && !OkHttpManager.isIpv6Disabled()) {
                // OkHttp has memory leak problems
                if (enableFasterDataSource()) {
                    failure.action("switch_engine:" + Utils.getNetworkEngineId(Utils.getFasterDataSource()));
                }
            } else if (getPlayerData().getVideoBufferType() == PlayerData.BUFFER_HIGH || getPlayerData().getVideoBufferType() == PlayerData.BUFFER_HIGHEST) {
                failure.action("lower_buffer");
                getPlayerData().setVideoBufferType(PlayerData.BUFFER_MEDIUM);
            } else {
                failure.action("lower_quality");
                lowerVideoQuality(); // NOTE: restart engine required
            }
        } else if (Helpers.containsAny(errorContent, "Exception in CronetUrlRequest") && !getPlayerTweaksData().isNetworkErrorFixingDisabled()) {
            if (getVideo() != null && !getVideo().isLive) { // Finished live stream may provoke errors in Cronet
                failure.action("engine_default");
                getPlayerTweaksData().setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT);
            } else {
                restartEngine = false;
            }
        } else if (type == PlayerEventListener.ERROR_TYPE_SOURCE && rendererIndex == PlayerEventListener.RENDERER_INDEX_UNKNOWN) {
            // NOTE: Starts with any (url deciphered incorrectly)
            // "Response code: 403" (poToken error, forbidden)
            // "Response code: 404" (not sure whether below helps)
            // "Response code: 503" (not sure whether below helps)
            // "Response code: 400" (not sure whether below helps)
            // "Response code: 429" (subtitle error, too many requests)
            // "Response code: 500" (subtitle error, generic server error)

            // NOTE: Fixing too many requests or network issues
            // NOTE: All these errors have unknown renderer (-1)
            // "Unable to connect to", "Invalid NAL length", "Response code: 421",
            // "Response code: 404", "Response code: 429", "Invalid integer size",
            // "Unexpected ArrayIndexOutOfBoundsException", "Unexpected IndexOutOfBoundsException"

            restartEngine = false;
            showMessage = false;

            boolean isGeneralError = Helpers.startsWithAny(errorContent, "Response code: 429", "Response code: 500");
            if (isGeneralError && isSubtitlesEnabled()) {
                failure.action("disable_subtitles_for_video");
                disableSubtitlesForVideo(); // Response code: 429
            } else if (isGeneralError && getPlayerTweaksData().isHighBitrateFormatsEnabled()) {
                failure.action("disable_high_bitrate");
                getPlayerTweaksData().setHighBitrateFormatsEnabled(false); // Response code: 429
            } else if (applyServerErrorClientPick(error, failure)) {
                // Response code: 503
                Log.d(TAG, "Server error of the video's stream: the next client for it only");
            } else if (!mBufferingDetector.isPlayable()) { // Response code: 403
                // The stream fails instantly if nParam isn't correct.
                // Note, nParam generation strictly tied to the client but some reported that OkHttp could help.
                failure.action("switch_next_client_now");
                YouTubeServiceManager.instance().switchNextClientNow();
                showMessage = true;
            } else if (applySabrRefusedRefetch(error, failure)) {
                // Response code: 401, 403 of SABR
                Log.d(TAG, "SABR refused a request of a video that played: its player response fetched again");
            } else if (!applyNetworkErrorReload(error, failure)) {
                // A broken connection (e.g. a read timeout) or response (see isBrokenResponse) is only reloaded, with the same client
                failure.action(switchNextClient()); // Response code: 403
            }
        } else if (type == PlayerEventListener.ERROR_TYPE_RENDERER && rendererIndex == PlayerEventListener.RENDERER_INDEX_SUBTITLE) {
            // "Response code: 429" (subtitle error)
            // "Response code: 500" (subtitle error)
            failure.action("disable_subtitles_for_video");
            disableSubtitlesForVideo();
            restartEngine = false;
        } else if (type == PlayerEventListener.ERROR_TYPE_RENDERER && rendererIndex == PlayerEventListener.RENDERER_INDEX_VIDEO) {
            failure.action("temp_video_avc");
            getPlayerData().setTempVideoFormat(FormatItem.VIDEO_FHD_AVC_30);
            if (getPlayerTweaksData().isSWDecoderForced()) {
                failure.action("disable_sw_decoder");
                getPlayerTweaksData().setSWDecoderForced(false);
            } else {
                restartEngine = false;
            }
        } else if (type == PlayerEventListener.ERROR_TYPE_RENDERER && error instanceof AudioSink.InitializationException) {
            // Whose refusal it is, why, and whether the device would take another config (see AudioOutputProbe)
            AudioSink.InitializationException refusal = (AudioSink.InitializationException) error;
            failure.detail("audioOutput", AudioOutputProbe.probe(getContext(), refusal.sampleRate, refusal.channelConfig,
                    refusal.bufferSize));

            // The device refused a new AudioTrack even after DefaultAudioSink retried (see AudioTrackInitRetry). Once, it can be the
            // app's own: a seek briefly holds two tracks. The format plays fine, so don't save another one. A reload frees the old
            // track; if this video keeps failing, restart the engine to free everything the player holds. Refused by the device
            // already (see applyDeviceAudioRefusal), a reload is no use.
            if (++mAudioInitErrorCount == 1 && !mIsAudioRefusedByDevice) {
                failure.action("audio_reload");
                restartEngine = false;
                showMessage = false;
            } else {
                // Or the device refuses this audio config (e.g. passthrough): go on with AAC until another video opens
                FormatItem audioFormat = getPlayer() != null ? getPlayer().getAudioFormat() : null;
                FormatItem fallback = applyAudioFallback(audioFormat != null && audioFormat.getTrack() != null ? audioFormat.getTrack().format : null);
                failure.action("audio_fallback:" + getFallbackName(fallback));

                if (fallback == null && AudioTrackCounts.getOpen() == 0) {
                    restartDelayMs = applyDeviceAudioRefusal(failure);
                    showMessage = false;
                } else if (fallback == null && mAudioInitErrorCount > MAX_AUDIO_INIT_RESTARTS && getVideo() != null) {
                    // Nothing left to change, and refused again: each restart waits longer for the device to have room for a track
                    // (FARAJV5R #17–#35: 40 restarts a minute for 10 minutes). Told once.
                    restartDelayMs = mAudioInitRetries.next(getVideo().videoId);
                    showMessage = mAudioInitRetries.getCount(getVideo().videoId) == 1;
                }
            }
        } else if (type == PlayerEventListener.ERROR_TYPE_RENDERER && rendererIndex == PlayerEventListener.RENDERER_INDEX_AUDIO) {
            failure.action("audio_hq_mp4a");
            getPlayerData().setFormat(FormatItem.AUDIO_HQ_MP4A);
            restartEngine = false;
        } else if (error instanceof DecoderErrorException) {
            // The decoder failed even after the renderer re-initialized it. A restart may be enough, but once the video fails again it
            // goes on with a format most devices decode, until another video opens. The saved formats stay.
            if (++mDecoderErrorCount > 1) {
                failure.action("decoder_fallback:" + getFallbackName(applyDecoderErrorFallback((DecoderErrorException) error)));
            }
        } else if (type == PlayerEventListener.ERROR_TYPE_UNEXPECTED) {
            if (error instanceof NullPointerException) {
                // SABR extractor throws NPE on subtitle error
                // NOTE: the engine should be restarted
                failure.action("disable_subtitles");
                disableSubtitles();
            } else if (Helpers.startsWithAny(errorContent,
                    "Buffer too small", "Invalid to call at Released state; only valid in executing state")) {
                // IllegalStateException: Buffer too small (5242880 < 7208383)
                // NOTE: The bug. Avoid calling reloadVideo() after lowering the quality.
                // This will change current format to 'Disabled'. Do restartEngine() instead.
                failure.action("lower_quality");
                lowerVideoQuality(); // NOTE: restart engine required
            }
        }

        if (showMessage) {
            MessageHelpers.showLongMessage(getContext(), errorMessage);
            if (getPlayer() != null) {
                getPlayer().setTitle(errorContent);
            }
        }

        if (restartEngine) {
            failure.action(delayed("restart_engine", restartDelayMs));
            mVideoLoaderController.restartEngine((int) restartDelayMs, failure.getKind());
        } else {
            // Need at least to reload the video because the player becomes idle after error
            failure.action("reload_video");
            mVideoLoaderController.reloadVideo(failure.getKind());
        }
    }

    @SuppressLint("StringFormatMatches")
    private String getErrorTitle(int type, int rendererIndex, Throwable error) {
        String errorTitle;
        int msgResId;

        switch (type) {
            // Some ciphered data could be outdated.
            // Might happen when the app wasn't used quite a long time.
            case PlayerEventListener.ERROR_TYPE_SOURCE:
                switch (rendererIndex) {
                    case PlayerEventListener.RENDERER_INDEX_VIDEO:
                        msgResId = R.string.msg_player_error_video_source;
                        break;
                    case PlayerEventListener.RENDERER_INDEX_AUDIO:
                        msgResId = R.string.msg_player_error_audio_source;
                        break;
                    case PlayerEventListener.RENDERER_INDEX_SUBTITLE:
                        msgResId = R.string.msg_player_error_subtitle_source;
                        break;
                    default:
                        msgResId = R.string.unknown_source_error;
                }
                errorTitle = getContext().getString(msgResId);
                break;
            case PlayerEventListener.ERROR_TYPE_RENDERER:
                if (error instanceof AudioSink.InitializationException) {
                    // Rarely a format problem (see applyEngineErrorAction)
                    errorTitle = getContext().getString(R.string.msg_player_error, type);
                    break;
                }
                switch (rendererIndex) {
                    case PlayerEventListener.RENDERER_INDEX_VIDEO:
                        msgResId = R.string.msg_player_error_video_renderer;
                        break;
                    case PlayerEventListener.RENDERER_INDEX_AUDIO:
                        msgResId = R.string.msg_player_error_audio_renderer;
                        break;
                    case PlayerEventListener.RENDERER_INDEX_SUBTITLE:
                        msgResId = R.string.msg_player_error_subtitle_renderer;
                        break;
                    default:
                        msgResId = R.string.unknown_renderer_error;
                }
                errorTitle = getContext().getString(msgResId);
                break;
            case PlayerEventListener.ERROR_TYPE_UNEXPECTED:
                errorTitle = getContext().getString(R.string.player_unexpected_error);
                break;
            default:
                errorTitle = getContext().getString(R.string.msg_player_error, type);
                break;
        }

        return errorTitle;
    }

    public void runFormatErrorAction(Throwable error) {
        if (getPlayer() == null) {
            return;
        }

        List<ClientAttempts.Attempt> attempts = getVideo() != null ? ClientAttempts.get(getVideo().videoId) : null;
        ClientAttempts.Attempt unplayable = getUnplayable(attempts);
        PlayerFailure failure = newFailure(unplayable != null ? PlayerFailure.KIND_UNPLAYABLE : PlayerFailure.KIND_FORMAT_ERROR)
                .clientAttempts(attempts);

        // Every client said it can't be played: the error is only that no formats came ("fromNullable result is null"), and it said
        // nothing the playability doesn't (19 of 8E1WXX00's 31 failures, a chain of members-only videos). Not recorded as one.
        if (unplayable == null || !isEmptyResult(error)) {
            failure.error(error);
        }

        // The start ends at the failed fetch, not when the user leaves (see VideoStarts#onFormatsFailed)
        if (getVideo() != null) {
            VideoStarts.onFormatsFailed(getVideo().videoId, unplayable != null);
        }

        try {
            if (unplayable != null) {
                failure.playability(unplayable.status, unplayable.reason);
                runUnplayableAction(unplayable, getLastingCause(attempts, getPlayedClient(getVideo())), failure);
            } else {
                runFormatErrorAction(error, attempts, failure);
            }
        } finally {
            failure.record();
        }
    }

    /**
     * The fetch of the formats gave none: RxHelper's error for a null result, which tells nothing of why
     */
    private static boolean isEmptyResult(@Nullable Throwable error) {
        return error != null && Helpers.containsAny(error.getMessage(), "fromNullable result is null");
    }

    /**
     * Every client said the video can't be played (none answered otherwise): the first one's why. Null when one gave formats or
     * failed some other way (e.g. no network).
     */
    @Nullable
    private static ClientAttempts.Attempt getUnplayable(@Nullable List<ClientAttempts.Attempt> attempts) {
        if (attempts == null) {
            return null;
        }

        ClientAttempts.Attempt first = null;

        for (ClientAttempts.Attempt attempt : attempts) {
            if (ClientAttempts.RESULT_UNPLAYABLE.equals(attempt.result)) {
                if (first == null) {
                    first = attempt;
                }
            } else if (!ClientAttempts.RESULT_NULL_RESPONSE.equals(attempt.result)) {
                return null;
            }
        }

        return first;
    }

    /**
     * Why it won't play in a while either, null when it may: a client said only the channel's members watch it (see
     * ClientAttempts.Attempt#members), and the account isn't one; one refused it for the viewer's age (see #ageGated: every client
     * asked for a sign-in did so for the age, 6ABPRYGH); or each one said it's gone (status ERROR, e.g. removed by the uploader or
     * private, BG2Y27G3); or each one asked for a sign-in, the client a video played on minutes ago too (see
     * isSignInRefusedByPlayingClient). A bot check (a sign-in asked for not for the age, of every video) or an upcoming stream isn't one
     * of them.
     *
     * @param playedClient the client a video played on lately (see getPlayedClient), null for none
     */
    @VisibleForTesting
    @Nullable
    static String getLastingCause(@Nullable List<ClientAttempts.Attempt> attempts, @Nullable String playedClient) {
        if (attempts == null) {
            return null;
        }

        boolean isAgeGated = false;
        boolean isLoginRequired = false;

        for (ClientAttempts.Attempt attempt : attempts) {
            if (attempt.members) {
                return LASTING_MEMBERS;
            }

            isAgeGated |= attempt.ageGated;
            isLoginRequired |= !attempt.ageGated && STATUS_LOGIN_REQUIRED.equals(attempt.status);
        }

        if (isAgeGated) {
            return isLoginRequired ? null : LASTING_AGE_GATED;
        }

        if (isLoginRequired && isSignInRefusedByPlayingClient(attempts, playedClient)) {
            return LASTING_SIGN_IN;
        }

        boolean isGone = false;

        for (ClientAttempts.Attempt attempt : attempts) {
            if (ClientAttempts.RESULT_UNPLAYABLE.equals(attempt.result)) {
                if (!STATUS_ERROR.equals(attempt.status)) {
                    return null;
                }

                isGone = true;
            }
        }

        return isGone ? LASTING_GONE : null;
    }

    /**
     * Each client that answered asked for a sign-in, and one of them is the client a video played on minutes ago: the session isn't
     * refused (a bot check refuses every video), this video is, e.g. a private one (7QAT714C #2: all 11 clients asked for a sign-in,
     * "This video is private" said TV; VISIONOS had played another video 10 min before). The reasons are in the user's language, the
     * client that played isn't.
     */
    private static boolean isSignInRefusedByPlayingClient(List<ClientAttempts.Attempt> attempts, @Nullable String playedClient) {
        if (playedClient == null) {
            return false;
        }

        boolean isRefusedByIt = false;

        for (ClientAttempts.Attempt attempt : attempts) {
            if (!ClientAttempts.RESULT_UNPLAYABLE.equals(attempt.result)) {
                continue;
            }

            if (attempt.ageGated || !STATUS_LOGIN_REQUIRED.equals(attempt.status)) {
                return false;
            }

            isRefusedByIt |= playedClient.equals(attempt.client);
        }

        return isRefusedByIt;
    }

    /**
     * The client another video played on last, if it began to within PLAYED_CLIENT_MS: null for none. Not the video's own: one that
     * played and is refused now (e.g. its reload's fetch, an hour on) was refused for the session, not for what it is.
     */
    @Nullable
    private String getPlayedClient(@Nullable Video video) {
        if (mPlayedClient == null || video == null || Helpers.equals(video.videoId, mPlayedVideoId)) {
            return null;
        }

        return SystemClock.elapsedRealtime() - mPlayedMs <= PLAYED_CLIENT_MS ? mPlayedClient : null;
    }

    /**
     * Its reason is shown, and it's tried again in a while: each second asked every client again (about 20 requests a second).
     * One that won't play in a while either isn't tried again (see getLastingCause): 6ABPRYGH's age-restricted ones asked 10 clients
     * again every 30 s, as did BG2Y27G3's removed one. The next video plays when it came next (autoplay, the Next button, see
     * VideoLoaderController.skipMembersVideo). Its reason shows till then: what's next may be known only once its metadata loaded. A
     * members' one is remembered (see MembersVideos).
     *
     * @param lastingCause why it won't play in a while either, null when it may (see getLastingCause)
     */
    private void runUnplayableAction(ClientAttempts.Attempt unplayable, @Nullable String lastingCause, PlayerFailure failure) {
        if (isEmbedPlayer()) {
            failure.action("finish_embed");
            getPlayer().finish();
            return;
        }

        String reason = unplayable.reason != null ? unplayable.reason : unplayable.status;
        Log.e(TAG, "Unplayable on every client: %s", reason);

        if (reason != null) {
            getPlayer().setTitle(reason);
        }

        getPlayer().showProgressBar(false);
        // The reason is the title, seen only with the controls: up, or the screen stays black. Nothing plays, so they don't hide
        getPlayer().showOverlay(true);

        if (lastingCause != null) {
            Video video = getVideo();

            if (video != null) {
                if (LASTING_MEMBERS.equals(lastingCause)) {
                    MembersVideos.add(video.videoId);
                }

                // The reason stays the title when its metadata loads (see below, VideoLoaderController.skipMembersVideo)
                video.syncUnplayable(reason);
            }

            if (mVideoLoaderController.skipMembersVideo(video)) {
                failure.action(lastingCause + "_next");
                return;
            }

            failure.action(lastingCause + "_stay");
            // Its suggestions, and the next video of the Next button, come with its metadata: that loads once a video plays (see
            // SuggestionsController.onVideoLoaded)
            SuggestionsController suggestions = getController(SuggestionsController.class);

            if (suggestions != null && video != null) {
                suggestions.loadSuggestions(video);
            }

            return;
        }

        failure.action("reload_video_" + UNPLAYABLE_RELOAD_MS / 1_000 + "s");
        mVideoLoaderController.reloadVideo(UNPLAYABLE_RELOAD_MS, failure.getKind());
    }

    /**
     * @param attempts of the fetch that failed, each client's (see ClientAttempts)
     */
    private void runFormatErrorAction(Throwable error, @Nullable List<ClientAttempts.Attempt> attempts, PlayerFailure failure) {
        if (isEmbedPlayer()) {
            failure.action("finish_embed");
            getPlayer().finish();
            return;
        }

        String message = error.getMessage();
        String className = error.getClass().getSimpleName();
        String fullMsg = String.format("loadFormatInfo error: %s: %s", className, Utils.getStackTraceAsString(error));
        Log.e(TAG, fullMsg);
        String videoId = getVideo() != null ? getVideo().videoId : null;
        // Told by the failures themselves, not by what failed before: the fetch's error didn't connect or got no answer (e.g. FARAJV5R
        // #55: something else answered for youtube.com), or every client's attempt didn't (a refused one gives no exception, see
        // RetrofitHelper#takeConnectException). The poToken's WebView keeps what its request failed with (see
        // PoTokenWebView#keepInitError): a broken WebView isn't one that couldn't connect. Another client wouldn't help.
        boolean isNetworkDown = LoadErrors.isConnectionError(error) || isEveryAttemptUnconnected(attempts);

        if (!Helpers.containsAny(message, "fromNullable result is null")) {
            // Once a run, not each time it's tried again (a stack trace a second for 15 minutes)
            if (mFetchRetries.getCount(videoId) == 0) {
                MessageHelpers.showLongMessage(getContext(), fullMsg);
            }

            if (getPlayer() != null) {
                getPlayer().setTitle(fullMsg);
            }
        }

        // A name that didn't resolve ("No address associated with hostname") is one of these, as the network's: the DNS isn't switched
        // to IPv4 and the app restarted for it anymore. Every DNS type ends in the system's lookup, which had just failed (IPv4 is the
        // system's, its addresses reordered; Google's falls back to it), and the DNS is read at each lookup.
        if (isNetworkDown) {
            failure.action("network_wait");
            undoStallClientSwitch(failure);
            reloadLater(failure, mFetchRetries.next(videoId));
        } else if (Helpers.containsAny(message, "Unexpected token", "Syntax error", "invalid argument") || // temporal fix
                Helpers.equalsAny(className, "PoTokenException", "BadWebViewException")) {
            failure.action(switchNextClient()).action("reload_video");
            mVideoLoaderController.reloadVideo(failure.getKind());
        } else if (Helpers.containsAny(message, "is not defined")) {
            failure.action("invalidate_cache").action("reload_video");
            YouTubeServiceManager.instance().invalidateCache();
            mVideoLoaderController.reloadVideo(failure.getKind());
        } else {
            Log.e(TAG, "Probably no internet connection");
            reloadLater(failure, mFetchRetries.next(videoId));
        }
    }

    /**
     * Each client tried failed on its connection (see ClientAttempts.Attempt#connectionError): none got an answer
     */
    private static boolean isEveryAttemptUnconnected(@Nullable List<ClientAttempts.Attempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return false;
        }

        for (ClientAttempts.Attempt attempt : attempts) {
            if (attempt.connectionError == null) {
                return false;
            }
        }

        return true;
    }

    /**
     * The video loaded again, after the wait (see RetryBackoff)
     */
    private void reloadLater(PlayerFailure failure, long delayMs) {
        failure.action(delayed("reload_video", delayMs));
        mVideoLoaderController.reloadVideo((int) delayMs, failure.getKind());
    }

    /**
     * For the failure's actions: e.g. "reload_video", "reload_video_4s" for a wait longer than the usual one (see RETRY_MS)
     */
    private static String delayed(String action, long delayMs) {
        return delayMs > RETRY_MS ? action + "_" + delayMs / 1_000 + "s" : action;
    }

    /**
     * The DNS the DNS fix switched from (see mDnsFixFrom) is put back, and the fix isn't tried again. A lookup reads it, so the reload
     * after this applies it.
     */
    private void undoDnsFix(PlayerFailure failure) {
        if (mDnsFixFrom < 0) {
            return;
        }

        failure.action("dns_revert");
        getPlayerTweaksData().setPreferredDnsType(mDnsFixFrom);
        mDnsFixFrom = -1;
        mIsDnsFixRefuted = true;
    }

    /**
     * Bad idea. Faster source is different among devices
     *
     * @return it wasn't on
     */
    private boolean enableFasterDataSource() {
        if (isFasterDataSourceEnabled()) {
            return false;
        }

        getPlayerTweaksData().setPlayerDataSource(Utils.getFasterDataSource());

        return true;
    }

    /**
     * Bad idea. Faster source is different among devices
     */
    private boolean isFasterDataSourceEnabled() {
        int fasterDataSource = Utils.getFasterDataSource();
        return getPlayerTweaksData().getPlayerDataSource() == fasterDataSource;
    }

    /**
     * @return the engine switched to
     */
    private int switchNextEngine() {
        int nextEngine = getNextEngine();
        getPlayerTweaksData().setPlayerDataSource(nextEngine);

        return nextEngine;
    }

    private int getNextEngine() {
        int currentEngine = getPlayerTweaksData().getPlayerDataSource();
        Integer[] engineList = Utils.skipCronet() ?
                new Integer[] { PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT, PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP } :
                new Integer[] { PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET, PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT, PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP };
        return Helpers.getNextValue(engineList, currentEngine);
    }

    private boolean isSubtitlesEnabled() {
        return getPlayer() != null && !FormatItem.SUBTITLE_NONE.equals(getPlayer().getSubtitleFormat());
    }

    private void disableSubtitles() {
        getPlayerData().setSubtitlesPerChannelEnabled(false); // Important!
        getPlayerData().setFormat(FormatItem.SUBTITLE_NONE);
    }

    /**
     * Off for the video only: a 429 (too many requests) or a 500 is their server's, for now. They were turned off in the settings, and
     * one rate limit left the user without subtitles from then on. Its reloads play without them (see
     * VideoStateController#restoreSubtitleFormat), the next video with the user's.
     */
    private void disableSubtitlesForVideo() {
        Video video = getVideo();
        getPlayerData().setSubtitlesOffFor(video != null ? video.videoId : null);

        if (getPlayer() != null) {
            getPlayer().setFormat(FormatItem.SUBTITLE_NONE);
        }
    }

    private boolean isStreamEnded() {
        if (getPlayer() == null || getVideo() == null) {
            return false;
        }

        return getVideo().isLiveEnd && getPlayer().getDurationMs() > 0
                && getPlayer().getDurationMs() - getPlayer().getPositionMs() < STREAM_END_THRESHOLD_MS;
    }

    /**
     * A live stream stalls, or fails to load, close to the end of its timeline: it may have ended while it played. Its metadata knows
     * (see Video#sync): that is read at the first and a minute after, while the fix below goes on. A stream the server calls live
     * isn't touched. Once its stream ended, its reloads load segments past the end that 404 and stall again, 7 failures and 3 minutes in
     * FVNAAYZD #29–#35, and what the video's metadata said was only read again once a reload had loaded it, as in #9 and #10 (21 s).
     */
    private void checkLiveEnd(PlayerFailure failure) {
        Video video = getVideo();

        if (video == null || getPlayer() == null || !video.isLive || video.isLiveEnd) {
            return;
        }

        long durationMs = getPlayer().getDurationMs();

        // A live stream stalling at its head is a connection's, not an end: only the last minutes of its timeline are checked
        if (durationMs <= 0 || durationMs - getPlayer().getPositionMs() >= STREAM_END_THRESHOLD_MS) {
            return;
        }

        long nowMs = SystemClock.elapsedRealtime();

        if (Helpers.equals(video.videoId, mLiveEndCheckVideoId) && nowMs - mLiveEndCheckTimeMs < LIVE_END_CHECK_MS) {
            return;
        }

        mLiveEndCheckVideoId = video.videoId;
        mLiveEndCheckTimeMs = nowMs;
        failure.action("live_end_check");
        loadMetadata(video, metadata -> onLiveEndChecked(video, metadata));
    }

    /**
     * Not the player's own metadata load: a test's
     */
    void loadMetadata(Video video, MediaServiceManager.OnMetadata onMetadata) {
        MediaServiceManager.instance().loadMetadata(video, onMetadata);
    }

    /**
     * The metadata of a live stream that stalled near its end (see checkLiveEnd): not live any more, it ended, and the reload asked for
     * is of a stream with nothing left to play
     */
    private void onLiveEndChecked(Video video, @Nullable MediaItemMetadata metadata) {
        if (metadata == null || metadata.isLive() || getPlayer() == null || getVideo() != video) {
            return;
        }

        video.isLiveEnd = true;
        Log.d(TAG, "The live stream ended: " + video.videoId);

        if (mVideoLoaderController != null) {
            mVideoLoaderController.cancelPendingFixes();
        }

        getMainController().onPlayEnd();
    }

    /**
     * A video (not a live stream) that played, stalled or failed at its end: it ended. One that never played is stuck at its start
     *
     * @param thresholdMs what's left of it at most: of a short video, a tenth of it, VIDEO_END_THRESHOLD_MS at least (a 6 s clip that
     *                    failed 1.5 s in hasn't ended)
     */
    private boolean isAtVideoEnd(long thresholdMs) {
        if (getPlayer() == null || getVideo() == null || getVideo().isLive || !mBufferingDetector.isPlayable()) {
            return false;
        }

        long durationMs = getPlayer().getDurationMs();
        long endMs = Math.max(VIDEO_END_THRESHOLD_MS, Math.min(thresholdMs, durationMs / 10));

        return durationMs > 0 && durationMs - getPlayer().getPositionMs() < endMs;
    }

    /**
     * The server holds back the media for the attestation (see isAttestationPending): pending past its grace period, or the video's
     * wall lap goes on (see countWallSwitch), a reload's pending being new. Within the grace period it still sends: what fails then
     * isn't the wall, and a switch would be for every video after it, IOS's being always pending (PENDING_MISSING).
     */
    private boolean isAtAttestationWall() {
        Video video = getVideo();

        if (video == null || video.videoId == null || !isAttestationPending()) {
            return false;
        }

        boolean isInLap = video.videoId.equals(mWallVideoId) && SystemClock.elapsedRealtime() - mWallSwitchMs <= WALL_LAP_GAP_MS;

        return isInLap || ProtectionStatuses.getPendingMs(video.videoId, mSourceChangedMs) >= ATTESTATION_GRACE_MS;
    }

    /**
     * The video streams over SABR, and the server's last word on its poToken, since the source opened, is that the attestation is
     * pending (see ProtectionStatuses). Over another protocol, or told before, that word was of a stream before (e.g. the client
     * switched to VISIONOS's DASH, or to another SABR client whose requests got no answer yet).
     */
    private boolean isAttestationPending() {
        Video video = getVideo();

        return video != null && PlayerFailure.PROTOCOL_SABR.equals(mVideoLoaderController.getFormatProtocol())
                && ProtectionStatuses.isAttestationPending(video.videoId, mSourceChangedMs);
    }

    private boolean isOfflineVideo() {
        if (getPlayer() == null || getVideo() == null) {
            return false;
        }

        return !getVideo().isLive && !getVideo().isLiveEnd;
    }

    /**
     * NOTE: Applied with restartEngine(). reloadVideo() resets the temp formats, so a reload of the same video applies it again
     * (see onNewVideo).
     */
    private FormatItem applyDecoderErrorFallback(DecoderErrorException error) {
        FormatItem fallback = null;

        if (error.trackType == C.TRACK_TYPE_VIDEO) {
            fallback = DecoderErrorFallback.getVideoFormat(error.format);

            if (fallback != null) {
                mVideoFallback = fallback;
                getPlayerData().setTempVideoFormat(fallback);
            }
        } else if (error.trackType == C.TRACK_TYPE_AUDIO) {
            fallback = applyAudioFallback(error.format);
        }

        // Null: nothing left to fall back to, the restart is all there is
        Log.e(TAG, "Decoder error fallback: %s", fallback != null ? fallback.getTrack().format : null);

        return fallback;
    }

    /**
     * AAC in the language of the failed track for the video in mDecoderErrorVideoId, unless the failed format is AAC already.<br/>
     * NOTE: Applied with restartEngine() (see applyDecoderErrorFallback).
     */
    private FormatItem applyAudioFallback(Format failed) {
        FormatItem audioFormat = getPlayerData().getFormat(FormatItem.TYPE_AUDIO);
        // The language of the failed track: the format picked for this video isn't saved
        String language = failed != null && failed.language != null ? failed.language
                : audioFormat != null ? audioFormat.getLanguage() : null;
        FormatItem fallback = DecoderErrorFallback.getAudioFormat(failed, language);

        if (fallback != null) {
            mAudioFallback = fallback;
            getPlayerData().setTempAudioFormat(fallback);
        }

        return fallback;
    }

    /**
     * Refused again with no AudioTrack of the app's open, and no other audio format to try: it's the device's, not the app's memory.
     * A Shield refused every 44.1 kHz track for 20 minutes and more, a new process's first one too, and played again only once the
     * device was restarted (FARAJV5R #40–#41: a process 110 s old, the device booted at 07:53:57; #104: booted 23 s after it;
     * 9M6NBWN7: a process 18.7 s old). No restart of the player helps: the user is told once to restart the device, and it's tried
     * again with a wait growing across the videos, till a video's sound plays.
     *
     * @return the wait before the engine is restarted
     */
    private long applyDeviceAudioRefusal(PlayerFailure failure) {
        failure.action("device_audio_refused");

        if (!mIsAudioRefusedByDevice) {
            mIsAudioRefusedByDevice = true;
            MessageHelpers.showLongMessage(getContext(), R.string.msg_device_refuses_audio);
        }

        if (getPlayer() != null && getContext() != null) {
            // In place of the error's title: what the user can do about it
            getPlayer().setTitle(getContext().getString(R.string.msg_device_refuses_audio));
        }

        return mDeviceAudioRetries.next(DEVICE_AUDIO_RETRY_KEY);
    }

    /**
     * For the failure's actions, e.g. "avc1"
     */
    private static String getFallbackName(FormatItem fallback) {
        Format format = fallback != null && fallback.getTrack() != null ? fallback.getTrack().format : null;

        if (format == null) {
            return "none";
        }

        return format.codecs != null ? format.codecs : String.valueOf(format.sampleMimeType);
    }

    /**
     * The video, where the player is at, its formats and where they're from
     */
    private PlayerFailure newFailure(String kind) {
        Video video = getVideo();
        PlayerFailure failure = PlayerFailure.create(getContext(), kind).video(video).embed(isEmbedPlayer());

        if (video == null) {
            // None at hand, e.g. the player is gone: the one the formats tried are of (DMQEWB9B #1 had no video ID at all)
            failure.videoId(FormatAttempts.getVideoId());
        }

        if (mVideoLoaderController != null) {
            failure.source(mVideoLoaderController.getFormatClient(), mVideoLoaderController.getFormatProtocol());
            mVideoLoaderController.addPickedClient(failure);
        }

        if (getPlayer() != null) {
            failure.playback(getPlayer().getPositionMs(), getPlayer().getDurationMs(), mBufferingDetector.isPlayable());

            try {
                // The player's are the video before's till this one's reached it (e.g. its fetch failed)
                if (mVideoLoaderController == null || mVideoLoaderController.isFormatOfVideo()) {
                    failure.formats(getPlayer().getVideoFormat(), getPlayer().getAudioFormat());
                }
            } catch (RuntimeException e) {
                // The engine may be released already: the fix goes on without them
                Log.e(TAG, "Can't read the formats of the failure: %s", e.getMessage());
            }
        }

        return failure;
    }

    private void reapplyDecoderErrorFallback() {
        if (mVideoFallback != null) {
            getPlayerData().setTempVideoFormat(mVideoFallback);
        }

        if (mAudioFallback != null) {
            getPlayerData().setTempAudioFormat(mAudioFallback);
        }
    }

    /**
     * NOTE: The bug. Avoid calling reloadVideo() after lowering the quality.<br/>
     * This will change current format to 'Disabled'. Do reloadEngine() instead.
     */
    private boolean lowerVideoQuality() {
        return lowerVideoQuality(Integer.MAX_VALUE);
    }

    /**
     * See lowerVideoQuality(): the biggest size down that's no bigger than the one given (e.g. the display's), at once
     *
     * @param maxSize the longest side's, e.g. 1280 for 720p
     */
    private boolean lowerVideoQuality(int maxSize) {
        if (getPlayer() == null) {
            return false;
        }

        List<FormatItem> videoFormats = getPlayer().getVideoFormats();
        FormatItem videoFormat = getPlayer().getVideoFormat();

        if (videoFormats == null || videoFormat == null) {
            return false;
        }

        int size = Math.max(videoFormat.getWidth(), videoFormat.getHeight());

        // Limit by 720p
        if (size <= 1280) {
            return false;
        }

        // The next size down: another codec of the same size wouldn't help. Of it, the codec playing, else the one of the fewest bits a
        // second, not the list's first: the smaller is the point on a connection too slow for the format (Z6R2NPPX #6: AV1 1080p was
        // lowered to VP9 720p at 1213k, AV1 720p offered, usually the smaller). The list's first when none says its bitrate.
        int lowerSize = -1;

        for (FormatItem format : videoFormats) {
            int formatSize = Math.max(format.getWidth(), format.getHeight());

            if (formatSize > 0 && formatSize < size && formatSize <= maxSize && formatSize > lowerSize) {
                lowerSize = formatSize;
            }
        }

        // None below the one playing: a format whose size isn't known (-1) isn't it
        if (lowerSize <= 0) {
            return false;
        }

        String playingCodec = getCodecName(videoFormat);
        FormatItem lower = null;

        for (FormatItem format : videoFormats) {
            if (Math.max(format.getWidth(), format.getHeight()) != lowerSize) {
                continue;
            }

            if (playingCodec != null && playingCodec.equals(getCodecName(format))) {
                lower = format;
                break;
            }

            if (lower == null || isLowerBitrate(format, lower)) {
                lower = format;
            }
        }

        String codec = getCodecName(lower);

        if (codec == null) {
            return false;
        }

        // A limit, as the decoder error fallback's, not the track: the track list is the stream's, and a reload makes another one. The
        // fps is a limit too, so 30 fps videos match.
        FormatItem limit = ExoFormatItem.fromVideoSpec(String.format(Locale.US, "%d,%d,60,%s", lower.getWidth(), lower.getHeight(), codec),
                false);

        if (limit == null) {
            return false;
        }

        // This helps to persist the format between engine restart
        getPlayerData().setTempVideoFormat(limit);
        // And a reload of the video (see onNewVideo). Till another video.
        mVideoFallback = limit;

        return true;
    }

    /**
     * The longest side of the biggest picture the display shows, of the modes it can switch to (see DeviceCapabilities): -1 when it
     * can't be told. Not the device's: a test's.
     */
    int getDisplaySize() {
        Context context = getContext();

        return context != null ? DeviceCapabilities.getLargestModeSize(context) : -1;
    }

    /**
     * Of fewer bits a second than the other: its bitrate is known, and the other's isn't or is higher
     */
    private static boolean isLowerBitrate(FormatItem format, FormatItem other) {
        int bitrate = getBitrate(format);
        int otherBitrate = getBitrate(other);

        return bitrate > 0 && (otherBitrate <= 0 || bitrate < otherBitrate);
    }

    /**
     * Of the format's track, -1 when not known
     */
    private static int getBitrate(FormatItem format) {
        Format trackFormat = format.getTrack() != null ? format.getTrack().format : null;

        return trackFormat != null && trackFormat.bitrate > 0 ? trackFormat.bitrate : -1;
    }

    /**
     * Of the format's track, e.g. "audio/opus", null when not known
     */
    @Nullable
    private static String getMimeType(@Nullable FormatItem format) {
        Format trackFormat = format != null && format.getTrack() != null ? format.getTrack().format : null;

        return trackFormat != null ? trackFormat.sampleMimeType : null;
    }

    @Nullable
    private static String getCodecName(@Nullable FormatItem format) {
        // None (e.g. no size below the one playing, see lowerVideoQuality)
        if (format == null) {
            return null;
        }

        Format trackFormat = format.getTrack() != null ? format.getTrack().format : null;

        return trackFormat != null && trackFormat.codecs != null ? TrackSelectorUtil.codecNameShort(trackFormat.codecs) : null;
    }

    /**
     * The segments of the format came slower than it plays lately (see FormatAttempts#isTooSlow), and none of its loads failed on the
     * connection nor hangs on one: the connection keeps up with a lower one. One that stops answering isn't too slow (see the repeated
     * stall's, runLongBufferingAction), nor one with no segment lately (e.g. SABR's server asked it to back off).
     */
    private boolean isConnectionTooSlow(PlayerFailure failure) {
        boolean hasConnectionError = LoadErrors.hasConnectionErrorWithin(CONNECTION_ERROR_WINDOW_MS);
        String stuckConnection = Connections.getStuckConnection(STUCK_CONNECTION_MS);

        if (hasConnectionError || stuckConnection != null) {
            failure.detail("loadConnectionError", hasConnectionError);
            failure.detail("stuckConnection", stuckConnection);
            return false;
        }

        return FormatAttempts.isTooSlow();
    }

    /**
     * The video stalled long not long after the last time
     */
    private boolean isRepeatedStall() {
        String videoId = getVideo() != null ? getVideo().videoId : null;
        long nowMs = SystemClock.elapsedRealtime();
        boolean isRepeated = videoId != null && videoId.equals(mStallVideoId) && nowMs - mStallTimeMs <= REPEATED_STALL_MS;
        mStallVideoId = videoId;
        mStallTimeMs = nowMs;

        return isRepeated;
    }
}
