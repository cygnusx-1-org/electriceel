package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererReadiness;
import com.google.android.exoplayer2.source.sabr.DefaultSabrChunkSource;
import com.google.android.exoplayer2.source.sabr.parser.SabrStream;
import com.liskovsoft.sharedutils.cronet.CronetManager;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.videoinfo.V2.FetchStages;
import com.liskovsoft.youtubeapi.videoinfo.V2.WarmUps;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Times each video started, from the user's action to the video playing: when a frame is shown and the playback runs. Each start is
 * recorded (see VideoStartLog), logged as a graph (see StartGraphText) and shown when the user wants it (see VideoStartController),
 * with where its time went: the player screen, the formats fetched (each stage of the fetch, see FetchStages), the source opened,
 * the buffering… Used on the main thread.
 */
public final class VideoStarts {
    private static final String TAG = VideoStarts.class.getSimpleName();
    /**
     * Of the graphs logged, for adb users: adb logcat -s VideoStart
     */
    static final String LOG_TAG = "VideoStart";
    /**
     * Not playing by then, the start is late: ready and paused, it's recorded as paused (see #giveUp); otherwise it's timed on, till
     * it plays, is left, or MAX_RECORDED_MS
     */
    static final long MAX_START_MS = 15_000;
    /**
     * Not playing by then, the start is given up and recorded as it is (see VideoStart#OUTCOME_GAVE_UP). Given up at 15 s, a start's
     * record stopped there though the video went on to play: 97Z1YWD3's start 92 was ready ~26 s after the click, its last ~11 s not
     * recorded, and R71TW3F4's oKgDcmHScsM (gave_up) played 0.7 s after. A player's first preprocess takes 22-27 s on a slow box
     * (H5B64KBE, Y1D66CNW); past a minute the user has most likely gone.
     */
    static final long MAX_RECORDED_MS = 60_000;
    /**
     * Ready and paused past its 15 s: paused from then if it still is this long after (see #onPlayerState). The app pauses too: auto
     * frame rate holds the playback for the display's mode switch, and its pause setting goes up to 15 s (see AutoFrameRateController).
     */
    static final long LATE_PAUSED_MS = 16_000;
    /**
     * A start that never played is recorded when it ran this long: one left sooner was skipped by the user (e.g. pressing next
     * twice), not stuck. One found unplayable is recorded however soon.
     */
    static final long MIN_ABANDONED_MS = 2_000;
    /**
     * A link processed this long after the process started didn't start it (something else did, e.g. a service)
     */
    static final long MAX_COLD_START_MS = 30_000;
    static final String STAGE_APP_START = "app start";
    /**
     * A moment at the start of a reload that played after one gave up (see onReload): how many reloads it took, and since when
     */
    static final String STAGE_RECOVERY = "recovery";
    /**
     * A reload this long after a start that gave up isn't its recovery any more
     */
    static final long MAX_RECOVERY_MS = 10 * 60 * 1_000;
    static final String STAGE_PLAYER_SCREEN = "player screen";
    /**
     * The player's screen was behind another and came back (see onViewPaused), as one row from when it went
     */
    static final String STAGE_SCREEN_BEHIND = "screen behind";
    /**
     * Behind another screen this long, the start was left (see onViewPaused)
     */
    static final long VIEW_PAUSED_MS = 5_000;
    static final String STAGE_ENGINE_RESTART = "engine restart";
    static final String STAGE_FORMATS = "formats";
    static final String STAGE_QUEUE = "queue";
    /**
     * A wait this long for the fetch to begin says what the threads were busy with (see #getQueueDetail)
     */
    private static final long QUEUE_LOAD_MS = 500;
    static final String STAGE_TO_PLAYER = "to the player";
    static final String STAGE_OPEN = "open";
    /**
     * Steps of the open stage, on the main thread (see timeOpenStep): the controllers' handling of the formats, the build of the
     * media source, and the controllers' handling of the source change, each of those that takes long
     */
    public static final String STAGE_CENSORED_WORDS = "censored words";
    public static final String STAGE_END_SCREEN = "end screen";
    public static final String STAGE_MEDIA_SOURCE = "media source";
    public static final String STAGE_SOURCE_CHANGED = "source changed";
    public static final String STAGE_CONTROLLER = "controller";
    /**
     * A step inside another (e.g. a controller's part of the source change) shorter than this isn't a stage of its own: some twenty
     * controllers take ~0 ms each
     */
    private static final long MIN_INNER_STEP_MS = 2;
    static final String STAGE_BUFFERING = "buffering";
    static final String STAGE_FIRST_VIDEO_CHUNK = "first video chunk";
    static final String STAGE_FIRST_AUDIO_CHUNK = "first audio chunk";
    static final String STAGE_VIDEO_INIT_CHUNK = "video init chunk";
    static final String STAGE_AUDIO_INIT_CHUNK = "audio init chunk";
    static final String STAGE_LOADING_HELD = "loading held";
    /**
     * A new connection of the player, its detail the load that made it first, e.g. "video init chunk" (see Connections)
     */
    static final String STAGE_CONNECT = "connect";
    /**
     * A request of the player, from being sent to the first byte of its answer, its detail the load it's of (see Connections)
     */
    static final String STAGE_RESPONSE = "response";
    /**
     * A request of the player on an engine other than OkHttp, from being made to its answer's headers, connect included (see
     * Connections#timeOpenings)
     */
    static final String STAGE_OPENING = "opening";
    /**
     * The wait SABR's server asked for, from when it said so (see ServerBackoffs)
     */
    static final String STAGE_SERVER_BACKOFF = "server backoff";
    /**
     * More waits of SABR's server than rows are shown for, as one: from the first told to the end of the last, its detail how many,
     * and the first and last asked (see ServerBackoffs)
     */
    static final String STAGE_SERVER_HOLD = "server hold";
    /**
     * A live stream at its head: its request for the next segment got none, and the next one waits, from then for as long (see
     * LiveHeadWaits)
     */
    static final String STAGE_LIVE_HEAD = "live head";
    /**
     * What SABR's server made of the poToken when it said so, a moment: the first, then each change (see ProtectionStatuses)
     */
    static final String STAGE_PROTECTION = "protection";
    /**
     * A load of the player that failed, from when it was asked for, its detail the load and why, e.g. "video chunk, timed out" (see
     * LoadErrors): the player tries it again after a wait
     */
    static final String STAGE_LOAD_ERROR = "load error";
    /**
     * A load of the player cancelled while it started (e.g. by a seek), from when it was asked for, its detail the load and the bytes
     * it got, e.g. "video chunk, 36 KB" (see #onLoadCanceled)
     */
    static final String STAGE_LOAD_CANCELLED = "load cancelled";
    /**
     * Of a start: a seek cancels a load of each track at most (see MAX_SEEKS)
     */
    static final int MAX_CANCELLED_LOADS = 20;
    /**
     * A moment of the formats stage: the formats the player got were made before this start's fetch began (e.g. cached, or the ones a
     * reload took), its detail how long before, e.g. "made 11h36m ago" (see #onFormats)
     */
    static final String STAGE_REUSED = "reused";
    /**
     * Made this much before the fetch began, formats are another fetch's
     */
    private static final long REUSED_MIN_MS = 1_000;
    /**
     * The first segments of a start, by kind: each its own row
     */
    private static final String[] CHUNK_STAGES = {STAGE_VIDEO_INIT_CHUNK, STAGE_AUDIO_INIT_CHUNK, STAGE_FIRST_VIDEO_CHUNK,
            STAGE_FIRST_AUDIO_CHUNK};
    static final String STAGE_VIDEO_INIT_AGAIN = "video init chunk again";
    static final String STAGE_AUDIO_INIT_AGAIN = "audio init chunk again";
    /**
     * The init segments' loads after the first one, by track (as CHUNK_STAGES' first two), as one row: from the first of them to the
     * end of the last, its detail how many and their bytes, e.g. "57 more, 9 KB". SABR asks again when an init request's answer had
     * no init segment (e.g. the server held the stream, KKSJMSQX: 0 KB, then seconds with no row), or the format changed.
     */
    private static final String[] INIT_AGAIN_STAGES = {STAGE_VIDEO_INIT_AGAIN, STAGE_AUDIO_INIT_AGAIN};
    static final String STAGE_MORE_VIDEO_CHUNKS = "more video chunks";
    static final String STAGE_MORE_AUDIO_CHUNKS = "more audio chunks";
    /**
     * The media segments loaded after the first one, by track (as CHUNK_STAGES' last two), as one row: from the first of them to the
     * end of the last, its detail how many and their bytes, e.g. "5, 12.3 MB". Of a start that didn't play at its first frame (see
     * AFTER_FRAME_MIN_MS): what the player went on loading while it waited, e.g. the audio asked for again after a seek back.
     */
    private static final String[] MORE_CHUNK_STAGES = {STAGE_MORE_VIDEO_CHUNKS, STAGE_MORE_AUDIO_CHUNKS};
    /**
     * A renderer that couldn't play from where it was after the start's first frame, till it could (see RendererWaits): the player is
     * ready only once the video's and the audio's both can
     */
    static final String STAGE_VIDEO_NOT_READY = "video not ready";
    static final String STAGE_AUDIO_NOT_READY = "audio not ready";
    /**
     * Played this long after its first frame, a start has rows for what it waited on meanwhile (see MORE_CHUNK_STAGES and
     * STAGE_AUDIO_NOT_READY). The frame was shown at 1226 ms and it played at 2882 ms, with 30.8 s buffered and no row between
     * (6EZTNXBF): sooner, the two are one moment.
     */
    static final long AFTER_FRAME_MIN_MS = 200;
    static final String STAGE_VIDEO_DECODER = "video decoder";
    /**
     * The frames decoded only to reach where the video starts (e.g. a resume's position), from the keyframe before it, till its first
     * frame: from the first of them (see DebugInfoMediaCodecVideoRenderer#getSkippedToPosition)
     */
    static final String STAGE_TO_POSITION = "to the position";
    /**
     * What a resume made of the keyframe before its position (see #onKeyframeResume), after the frames on its "to the position" row.
     * Told only to the log, a resume that decoded 72 4K frames in 1084 ms, not gone back to its keyframe, didn't say why (WVE7ZKDD
     * 11-24 22:43:55).
     */
    public static final String KEYFRAME_GONE_BACK = "gone back to the keyframe";
    public static final String KEYFRAME_DECODER_THERE = "decoder there already";
    public static final String KEYFRAME_AUDIO_LOADING = "audio still loading";
    public static final String KEYFRAME_OTHER_POSITION = "decoded to another position";
    static final String KEYFRAME_NOT_WORTH_IT = "not worth going back";
    /**
     * Before a decision made again once the audio came in (see KEYFRAME_AUDIO_LOADING)
     */
    static final String KEYFRAME_AFTER_AUDIO = "after the audio";
    static final String STAGE_MODE_SWITCH = "display mode switch";
    static final String STAGE_FIRST_FRAME = "first frame";
    /**
     * A moment: the playback was sent elsewhere while it started, its detail where to (see #onSeek)
     */
    static final String STAGE_SEEK = "seek";
    /**
     * Of a start: a user scrubbing past it isn't a row each
     */
    static final int MAX_SEEKS = 10;
    /**
     * How a mode switch the playback waited for ended (see onModeSwitchEnd)
     */
    public static final String MODE_SWITCH_REPORTED = "reported";
    public static final String MODE_SWITCH_TIMED_OUT = "timed out";
    private static final int MAX_MODE_REPORTS = 3;
    private static final String APP_PACKAGE = "com.liskovsoft.smartyoutubetv2.";
    /**
     * The methods that pass a seek on to the player (see describeSeekCaller): not what made it
     */
    private static final java.util.Set<String> SEEK_PASS_THROUGHS = new java.util.HashSet<>(java.util.Arrays.asList(
            "setPositionMs", "setResumePositionMs", "seekTo", "onSeek", "run", "handleMessage", "dispatchMessage", "dispatchSeekTo"));
    /**
     * The whole of a fetch (see FetchStages#open): its stages are shown, not it
     */
    private static final String FETCH = "fetch";
    private static final String DETAIL_RETRY = "retry";
    /**
     * Of the formats stage whose fetch failed (see onFormatsFailed)
     */
    static final String DETAIL_FAILED = "failed";
    /**
     * Of a segment not loaded whole when the video played
     */
    private static final String DETAIL_LOADING = "still loading";
    /**
     * Of a stage that hadn't ended when the start was looked at or given up
     */
    static final String DETAIL_GOING = "still going";
    /**
     * logcat cuts an entry at about 4 KB
     */
    private static final int MAX_LOG_ENTRY = 3_000;
    @VisibleForTesting
    static Clock sClock = SystemClock::elapsedRealtime;
    @VisibleForTesting
    static Clock sWallClock = System::currentTimeMillis;
    /**
     * In the clock's time
     */
    @VisibleForTesting
    static Clock sProcessStart = Process::getStartElapsedRealtime;
    @VisibleForTesting
    static FetchStageSource sFetchStages = new FetchStageSource() {
        @Override
        public List<FetchStages.Stage> take(String videoId) {
            return FetchStages.take(videoId);
        }

        @Override
        public List<FetchStages.Stage> peek(String videoId) {
            return FetchStages.peek(videoId);
        }
    };
    @VisibleForTesting
    static Scheduler sScheduler = new HandlerScheduler();
    /**
     * A start not playing 15 s after the user's action is late, then given up a minute after it, even when nothing happens any more
     * (e.g. a request hangs)
     */
    private static final Runnable sGiveUp = VideoStarts::giveUp;
    @VisibleForTesting
    static final Runnable sLeaveBehind = VideoStarts::leaveBehind;
    /**
     * The network engine the player uses (see Utils#getEffectiveNetworkEngine)
     */
    @VisibleForTesting
    static EngineSource sEngine = VideoStarts::getEffectiveEngine;
    @VisibleForTesting
    static LoadHoldSource sLoadHolds = LoadHolds::take;
    @VisibleForTesting
    static LoadHoldSource sConnections = Connections::take;
    @VisibleForTesting
    static LoadHoldSource sBackoffs = ServerBackoffs::take;
    @VisibleForTesting
    static LoadHoldSource sLiveHeadWaits = LiveHeadWaits::take;
    @VisibleForTesting
    static ProtectionSource sProtection = ProtectionStatuses::take;
    @VisibleForTesting
    static LoadHoldSource sLoadErrors = LoadErrors::take;
    @VisibleForTesting
    static LoadHoldSource sRendererWaits = RendererWaits::take;
    @VisibleForTesting
    static Recorder sRecorder = VideoStarts::save;
    @VisibleForTesting
    static FramesRecorder sFramesRecorder = VideoStarts::saveFrames;
    /**
     * The playback warm-ups of the run (see WarmUps in MediaServiceCore), for its first start
     */
    @VisibleForTesting
    static WarmUpSource sWarmUps = WarmUps::snapshot;
    /**
     * The starts after the run's first that have its warm-ups too, as they are then: one still going when it was recorded (e.g. a
     * player's first preprocess, still going 95 s on when QJVVSYS7's first start gave up at 15 s) ends in a later one's (see build).
     * At most this many: one that never ends isn't in every start.
     */
    private static final int MAX_WARM_UP_FOLLOW_UPS = 3;
    private static int sWarmUpFollowUps;
    /**
     * Where the device is at, for a start that never played (see DeviceCapabilities#buildStartState)
     */
    @VisibleForTesting
    static StateSource sState = VideoStarts::getState;
    /**
     * The application's (no activity kept), for the log of the starts
     */
    @SuppressLint("StaticFieldLeak")
    private static Context sContext;
    @Nullable
    private static Listener sListener;
    private static boolean sHasStarted;
    @Nullable
    private static Timer sTimer;
    /**
     * The last ones that played, by video, till their first seconds are measured (see #onFrames): when a video starts before the
     * one before is measured, the one before still is. Their time, see VideoStart#ts.
     */
    private static final LinkedHashMap<String, Long> sPlayed = new LinkedHashMap<>();
    private static final int MAX_PLAYED = 4;
    /**
     * The video that played last, till the app loads it again (see #onReload)
     */
    @Nullable
    private static String sReloadableId;
    /**
     * The video whose start gave up (see #abandon), and when that start began: its reloads are timed, only the one that plays recorded,
     * with how many it took (QYZPW0FN: four reloads in an outage left no start, the recovery's time not known). -1 for none.
     */
    @Nullable
    private static String sRecoveryId;
    private static long sRecoveryFromMs = -1;
    private static int sRecoveryReloads;
    /**
     * Of each kind of first segment (see CHUNK_STAGES) since the player was prepared, timed or not (a start is given up after 15 s):
     * it started to load, and it loaded (see #isOneTrackStuck)
     */
    private static final boolean[] sIsChunkStarted = new boolean[CHUNK_STAGES.length];
    private static final boolean[] sIsChunkDone = new boolean[CHUNK_STAGES.length];
    /**
     * When the player was prepared, in the clock's time: a load of the source before is told after it
     */
    private static long sPreparedMs = -1;
    /**
     * Of the open stage's steps running (see timeOpenValue): main thread only
     */
    private static int sOpenStepDepth;

    interface Clock {
        long now();
    }

    /**
     * Where the video starts and what with, at its first frame
     */
    public static final class FirstFrame {
        /**
         * In ms into the video, -1 when not known
         */
        final long positionMs;
        /**
         * E.g. "3840x2160 vp09 60fps"
         */
        @Nullable
        final String video;
        final int videoBitrateKbps;
        /**
         * The frames decoded only to reach the position, and when the first of them was, in the clock's time (-1 for none)
         */
        final int skippedFrames;
        final long skipStartMs;

        @VisibleForTesting
        FirstFrame(long positionMs, @Nullable String video, int videoBitrateKbps, int skippedFrames, long skipStartMs) {
            this.positionMs = positionMs;
            this.video = video;
            this.videoBitrateKbps = videoBitrateKbps;
            this.skippedFrames = skippedFrames;
            this.skipStartMs = skipStartMs;
        }

        /**
         * @param format the video's, null when not known
         * @param skipStartMs {@link SystemClock#elapsedRealtime()} of the first frame skipped, -1 for none
         */
        public static FirstFrame of(long positionMs, @Nullable Format format, int skippedFrames, long skipStartMs) {
            int bitrateKbps = format != null && format.bitrate > 0 ? format.bitrate / 1000 : -1;

            return new FirstFrame(positionMs, PlayerFailure.describeVideo(format), bitrateKbps, skippedFrames, skipStartMs);
        }
    }

    interface EngineSource {
        @Nullable
        String get();
    }

    interface LoadHoldSource {
        List<VideoStart.Stage> take(long fromMs, long toMs);
    }

    interface ProtectionSource {
        List<VideoStart.Stage> take(String videoId, long fromMs, long toMs);
    }

    interface FetchStageSource {
        List<FetchStages.Stage> take(String videoId);

        /**
         * The fetch going on: its stages so far, the ones still going too
         */
        List<FetchStages.Stage> peek(String videoId);
    }

    interface Scheduler {
        void schedule(Runnable task, long delayMs);

        void cancel(Runnable task);
    }

    private static final class HandlerScheduler implements Scheduler {
        private Handler mHandler;

        @Override
        public void schedule(Runnable task, long delayMs) {
            getHandler().postDelayed(task, delayMs);
        }

        @Override
        public void cancel(Runnable task) {
            getHandler().removeCallbacks(task);
        }

        private Handler getHandler() {
            if (mHandler == null) {
                mHandler = new Handler(Looper.getMainLooper());
            }

            return mHandler;
        }
    }

    interface Recorder {
        void record(VideoStart start);
    }

    interface FramesRecorder {
        void record(long ts, JSONObject frames);
    }

    interface WarmUpSource {
        List<WarmUps.Run> get();
    }

    interface StateSource {
        @Nullable
        JSONObject get();
    }

    public interface Listener {
        /**
         * The user started a video
         */
        void onStart();

        /**
         * The video plays, or never did and was given up: how long it took, and where the time went (see VideoStart#outcome)
         */
        void onTimed(VideoStart start);
    }

    /**
     * Of the start going on: its stages are kept in the clock's time till it ends
     */
    private static final class Timer {
        final String videoId;
        final String trigger;
        final long t0;
        final long ts;
        final boolean isFirstOfRun;
        final long sinceProcessStartMs;
        final List<Group> groups = new ArrayList<>();
        long fetchMs = -1;
        /**
         * What the threads of the tasks were busy with when the formats were asked for (see RxHelper#describeLoad), null when unknown
         */
        @Nullable
        String fetchLoad;
        int fetches;
        long prepareMs = -1;
        long firstFrameMs = -1;
        long playingMs = -1;
        /**
         * Since it's been ready and paused (see #onPlayerState), -1 when it isn't
         */
        long pausedMs = -1;
        /**
         * Since its screen has been behind another (see #onViewPaused), -1 when it isn't
         */
        long behindMs = -1;
        long modeSwitchMs = -1;
        /**
         * Of the mode switch going on: the mode asked for, and the ones the display reported (see onDisplayModeReported)
         */
        @Nullable
        String modeSwitchMode;
        final List<String> modeReports = new ArrayList<>(2);
        /**
         * Of its first frame (see FirstFrame)
         */
        long positionMs = -1;
        @Nullable
        String video;
        int videoBitrateKbps = -1;
        /**
         * Of each kind of first segment (see CHUNK_STAGES): loaded, and when it started to, -1 till then (SABR streams a response for
         * long, past the first frame)
         */
        final boolean[] isChunkLoaded = new boolean[CHUNK_STAGES.length];
        final long[] chunkLoadMs = new long[CHUNK_STAGES.length];
        /**
         * Of the init segments' loads after the first one, by track (see INIT_AGAIN_STAGES): how many, their bytes, and from the start
         * of the first to the end of the last
         */
        final int[] initAgainLoads = new int[INIT_AGAIN_STAGES.length];
        final long[] initAgainBytes = new long[INIT_AGAIN_STAGES.length];
        final long[] initAgainStartMs = new long[INIT_AGAIN_STAGES.length];
        final long[] initAgainEndMs = new long[INIT_AGAIN_STAGES.length];
        /**
         * Of the media segments' loads after the first one, by track (see MORE_CHUNK_STAGES): how many, their bytes, and from the
         * start of the first to the end of the last
         */
        final int[] moreChunkLoads = new int[MORE_CHUNK_STAGES.length];
        final long[] moreChunkBytes = new long[MORE_CHUNK_STAGES.length];
        final long[] moreChunkStartMs = new long[MORE_CHUNK_STAGES.length];
        final long[] moreChunkEndMs = new long[MORE_CHUNK_STAGES.length];
        boolean hasVideoDecoder;
        int seeks;
        /**
         * Of the loads cancelled (see #onLoadCanceled)
         */
        int cancelledLoads;
        /**
         * What the resume made of its keyframe (see #onKeyframeResume), null for nothing told
         */
        @Nullable
        String keyframeResume;
        /**
         * Not playing at MAX_START_MS: timed on (see #giveUp)
         */
        boolean isLate;
        boolean isCached;
        String client;
        String protocol;
        /**
         * Of the player, when it was prepared
         */
        @Nullable
        String engine;

        /**
         * Of a reload: why (see VideoStart#reason)
         */
        @Nullable
        String reason;
        /**
         * A reload after a start that gave up (see sRecoveryId): recorded only if it plays
         */
        boolean isRecovery;

        Timer(String videoId, String trigger, long t0, long ts, boolean isFirstOfRun, long sinceProcessStartMs) {
            Arrays.fill(chunkLoadMs, -1);
            this.videoId = videoId;
            this.trigger = trigger;
            this.t0 = t0;
            this.ts = ts;
            this.isFirstOfRun = isFirstOfRun;
            this.sinceProcessStartMs = sinceProcessStartMs;
        }

        Group open(String name, @Nullable String detail, long startMs) {
            Group group = new Group(name, detail, startMs);
            groups.add(group);
            return group;
        }

        /**
         * The last one of the name, ended or not
         */
        @Nullable
        Group findLast(String name) {
            for (int i = groups.size() - 1; i >= 0; i--) {
                Group group = groups.get(i);

                if (group.name.equals(name)) {
                    return group;
                }
            }

            return null;
        }

        @Nullable
        Group findOpen(String name) {
            for (int i = groups.size() - 1; i >= 0; i--) {
                Group group = groups.get(i);

                if (group.endMs < 0 && group.name.equals(name)) {
                    return group;
                }
            }

            return null;
        }

        void close(String name, long endMs) {
            Group group = findOpen(name);

            if (group != null) {
                group.endMs = endMs;
            }
        }
    }

    /**
     * A stage of the start and the ones in it
     */
    private static final class Group {
        final String name;
        @Nullable
        String detail;
        final long startMs;
        long endMs = -1;
        final List<VideoStart.Stage> children = new ArrayList<>();

        Group(String name, @Nullable String detail, long startMs) {
            this.name = name;
            this.detail = detail;
            this.startMs = startMs;
        }
    }

    private VideoStarts() {
    }

    /**
     * For the log of the starts
     */
    public static void init(Context context) {
        if (context != null) {
            sContext = context.getApplicationContext();
        }

        SabrStream.setBackoffListener(ServerBackoffs::onBackoff);
        DefaultSabrChunkSource.setLiveHeadWaitListener(LiveHeadWaits::onWait);
        SabrStream.setProtectionStatusListener(ProtectionStatuses::onStatus);
        RendererReadiness.setListener(RendererWaits::onReadiness);
        // Where a Cronet opening's time went
        CronetManager.setRequestFinishedListener(Connections::onRequestFinished);
        Plays.init(context);
    }

    public static void setListener(@Nullable Listener listener) {
        sListener = listener;
    }

    /**
     * The user started the video: a start before it is given up. One going on that hasn't played is replaced.
     *
     * @param trigger see VideoStart#TRIGGER_OPEN
     */
    public static void start(String videoId, String trigger) {
        abandon(VideoStart.OUTCOME_REPLACED);

        if (videoId == null) {
            return;
        }

        if (sRecoveryId != null && !sRecoveryId.equals(videoId)) {
            sRecoveryId = null; // another video: that one's reloads are over
        }

        long now = sClock.now();
        boolean isFirstOfRun = !sHasStarted;
        sHasStarted = true;
        long processStart = sProcessStart.now();
        long t0 = now;
        String actual = trigger;

        if (VideoStart.TRIGGER_COLD_INTENT.equals(trigger)) {
            if (isFirstOfRun && processStart > 0 && now >= processStart && now - processStart <= MAX_COLD_START_MS) {
                t0 = processStart;
            } else {
                actual = VideoStart.TRIGGER_INTENT;
            }
        }

        sTimer = new Timer(videoId, actual, t0, sWallClock.now() - (now - t0), isFirstOfRun,
                processStart > 0 && t0 >= processStart ? t0 - processStart : -1);

        if (t0 != now) {
            sTimer.open(STAGE_APP_START, null, t0).endMs = now;
        }

        sScheduler.cancel(sGiveUp);
        sScheduler.schedule(sGiveUp, Math.max(0, MAX_START_MS - (now - t0)));

        if (sListener != null) {
            sListener.onStart();
        }
    }

    /**
     * The app loads the video that played again (see VideoLoaderController#reloadVideo and #restartEngine), e.g. the error fixer after
     * the SABR server asked it to: timed as a start of its own, how long the picture was gone and whether it came back. One still
     * starting goes on (see #onFetch). Once per time it played: a reload that doesn't play isn't timed again (an ended live stream is
     * reloaded every 30 s).
     */
    public static void onReload(@Nullable String videoId) {
        onReload(videoId, null);
    }

    /**
     * @param reason why it's loaded again, e.g. the kind of failure the error fixer acted on (see VideoStart#reason), null when not known
     */
    public static void onReload(@Nullable String videoId, @Nullable String reason) {
        if (videoId == null || getTimer(videoId) != null) {
            return;
        }

        boolean isRecovery = !videoId.equals(sReloadableId) && videoId.equals(sRecoveryId)
                && sClock.now() - sRecoveryFromMs <= MAX_RECOVERY_MS;

        if (!videoId.equals(sReloadableId) && !isRecovery) {
            return;
        }

        sReloadableId = null;
        start(videoId, VideoStart.TRIGGER_RELOAD);

        if (sTimer != null) {
            sTimer.reason = reason;
            sTimer.isRecovery = isRecovery;
        }

        if (isRecovery) {
            sRecoveryReloads++;
        }
    }

    /**
     * The video waits for the player to start (see VideoLoaderController#onNewVideo)
     */
    public static void onEngineWait(String videoId) {
        Timer timer = getTimer(videoId);

        if (timer != null && timer.findOpen(STAGE_PLAYER_SCREEN) == null) {
            timer.open(STAGE_PLAYER_SCREEN, null, sClock.now());
        }
    }

    /**
     * Released while the video starts, it's restarted (e.g. by the error fixer). Released for good, the start is cancelled.
     */
    public static void onEngineReleased() {
        Timer timer = getTimer();

        if (timer != null && timer.findOpen(STAGE_PLAYER_SCREEN) == null) {
            timer.open(STAGE_ENGINE_RESTART, null, sClock.now());
        }
    }

    public static void onEngineInitialized() {
        Timer timer = getTimer();

        if (timer == null) {
            return;
        }

        long now = sClock.now();
        timer.close(STAGE_PLAYER_SCREEN, now);
        timer.close(STAGE_ENGINE_RESTART, now);
    }

    /**
     * The formats of the video are fetched (again, for a reload before it played)
     */
    public static void onFetch(String videoId) {
        Timer timer = getTimer(videoId);

        if (timer == null) {
            return;
        }

        long now = sClock.now();
        Group replaced = timer.findOpen(STAGE_FORMATS);

        if (replaced != null) {
            replaced.endMs = now;
            replaced.detail = "replaced";
        }

        timer.fetches++;
        timer.fetchMs = now;
        timer.fetchLoad = RxHelper.describeLoad();
        timer.open(STAGE_FORMATS, timer.fetches > 1 ? DETAIL_RETRY : null, now);
    }

    /**
     * The formats reached the player: the stages of their fetch are the start's
     *
     * @param client the one they're from, e.g. TV
     */
    public static void onFormats(String videoId, @Nullable String client) {
        onFormats(videoId, client, 0);
    }

    /**
     * @param createdTimeMs when the formats were made, on the wall clock (see MediaItemFormatInfo#getCreatedTimeMs), 0 for not known
     */
    public static void onFormats(String videoId, @Nullable String client, long createdTimeMs) {
        Timer timer = getTimer(videoId);

        if (timer == null || timer.fetchMs < 0) {
            return;
        }

        long now = sClock.now();
        long lastFetchEndMs = closeFormats(timer, videoId, now, null);

        if (lastFetchEndMs >= 0 && now > lastFetchEndMs) {
            timer.findLast(STAGE_FORMATS).children.add(new VideoStart.Stage(STAGE_TO_PLAYER, null, 1, lastFetchEndMs, now));
        }

        // Made before the fetch began: how old they are. A reload 11.5 h after a pause got formats in 13 ms with no row to say where
        // from, and the server sent it no media (S4PGCB6X #1).
        long ageMs = createdTimeMs > 0 ? sWallClock.now() - createdTimeMs : -1;

        if (ageMs - (now - timer.fetchMs) >= REUSED_MIN_MS) {
            timer.findLast(STAGE_FORMATS).children.add(new VideoStart.Stage(STAGE_REUSED, "made " + formatAge(ageMs) + " ago", 1, now,
                    now));
        }

        timer.client = client;
        timer.open(STAGE_OPEN, null, now);
    }

    /**
     * E.g. "11h36m", "4m05s", "35s"
     */
    @VisibleForTesting
    static String formatAge(long ageMs) {
        long seconds = ageMs / 1_000;

        if (seconds < 60) {
            return seconds + "s";
        }

        long minutes = seconds / 60;

        if (minutes < 60) {
            return String.format(Locale.US, "%dm%02ds", minutes, seconds % 60);
        }

        return String.format(Locale.US, "%dh%02dm", minutes / 60, minutes % 60);
    }

    /**
     * Of a step's result (see timeOpenValue)
     */
    public interface Step<T> {
        T run();
    }

    /**
     * A step of the open stage (see STAGE_OPEN), on the main thread: its time is a stage in it, inside the step it runs in. On the
     * first start after a launch, open took 1374 ms (AWHPECZK NWQCGxvVVeo) with nothing to say where the main thread went.
     *
     * @param detail e.g. a controller's name, null for none
     */
    public static void timeOpenStep(String name, @Nullable String detail, Runnable step) {
        timeOpenValue(name, detail, () -> {
            step.run();
            return null;
        });
    }

    /**
     * A step of the open stage that gives a result, e.g. the media source (see timeOpenStep)
     */
    public static <T> T timeOpenValue(String name, @Nullable String detail, Step<T> step) {
        Timer timer = getTimer();
        Group open = timer != null ? timer.findOpen(STAGE_OPEN) : null;

        if (open == null) {
            return step.run();
        }

        long startMs = sClock.now();
        sOpenStepDepth++;

        try {
            return step.run();
        } finally {
            sOpenStepDepth--;
            long endMs = sClock.now();
            int depth = sOpenStepDepth + 1;

            if (depth == 1 || endMs - startMs >= MIN_INNER_STEP_MS) {
                open.children.add(new VideoStart.Stage(name, detail, depth, startMs, endMs));
            }
        }
    }

    /**
     * The fetch failed (see ErrorFixerController.runFormatErrorAction): the formats stage ends there with the fetch's stages, not when the
     * user leaves. No client plays it: the start ends, unplayable. Another error (e.g. no network) is fetched again (see onFetch).
     *
     * @param isUnplayable every client said it can't be played (e.g. members only)
     */
    public static void onFormatsFailed(String videoId, boolean isUnplayable) {
        Timer timer = getTimer(videoId);

        if (timer == null || timer.fetchMs < 0) {
            return;
        }

        closeFormats(timer, videoId, sClock.now(), DETAIL_FAILED);

        if (isUnplayable) {
            abandon(VideoStart.OUTCOME_UNPLAYABLE);
        }
    }

    /**
     * The formats stage ends now, with the stages of its fetch
     *
     * @param detail of the stage, e.g. DETAIL_FAILED, null for none
     * @return when the last fetch ended, -1 when not known
     */
    private static long closeFormats(Timer timer, String videoId, long now, @Nullable String detail) {
        Group formats = timer.findOpen(STAGE_FORMATS);

        if (formats == null) {
            formats = timer.open(STAGE_FORMATS, null, timer.fetchMs);
        }

        formats.endMs = now;

        if (detail != null) {
            formats.detail = detail;
        }

        long firstFetchMs = -1;
        long lastFetchEndMs = -1;
        List<FetchStages.Stage> fetched = new ArrayList<>();

        for (FetchStages.Stage stage : sFetchStages.take(videoId)) {
            // Of this fetch, not of one before (e.g. the history's)
            if (stage.startMs < timer.fetchMs || stage.endMs > now) {
                continue;
            }

            if (stage.depth == 0 && FETCH.equals(stage.name)) {
                firstFetchMs = firstFetchMs < 0 ? stage.startMs : Math.min(firstFetchMs, stage.startMs);
                lastFetchEndMs = Math.max(lastFetchEndMs, stage.endMs);
            } else {
                fetched.add(stage);
            }
        }

        if (firstFetchMs > timer.fetchMs) {
            formats.children.add(new VideoStart.Stage(STAGE_QUEUE, getQueueDetail(timer, firstFetchMs, null), 1, timer.fetchMs,
                    firstFetchMs));
        }

        for (FetchStages.Stage stage : fetched) {
            formats.children.add(new VideoStart.Stage(stage.name, stage.detail, Math.max(1, stage.depth), stage.startMs, stage.endMs));
            timer.isCached |= "cached".equals(stage.name);
        }

        return lastFetchEndMs;
    }

    /**
     * The formats are of a live stream or a premiere that hasn't begun (see VideoLoaderController#isUpcoming): the start ends, with
     * nothing to play yet
     */
    public static void onUpcoming(String videoId) {
        if (getTimer(videoId) != null) {
            abandon(VideoStart.OUTCOME_UPCOMING);
        }
    }

    /**
     * How the formats are played, e.g. sabr (see PlayerFailure#PROTOCOL_SABR). Null for none: the video can't be played.
     */
    public static void onProtocol(@Nullable String protocol) {
        Timer timer = getTimer();

        if (timer == null) {
            return;
        }

        if (protocol == null) {
            abandon(VideoStart.OUTCOME_UNPLAYABLE); // It loads another one, or reloads later
            return;
        }

        timer.protocol = protocol;
    }

    /**
     * The player buffers the formats
     */
    public static void onPrepare() {
        onPrepare(null);
    }

    /**
     * @param engine the network engine the source's requests go over (see ExoMediaSourceFactory#getBuiltEngine), null when not known:
     *               the one the settings give then
     */
    public static void onPrepare(@Nullable String engine) {
        Arrays.fill(sIsChunkStarted, false);
        Arrays.fill(sIsChunkDone, false);
        sPreparedMs = sClock.now();
        Timer timer = getTimer();

        if (timer == null) {
            return;
        }

        timer.engine = engine != null ? engine : sEngine.get();
        long now = sClock.now();
        timer.close(STAGE_OPEN, now);
        Group replaced = timer.findOpen(STAGE_BUFFERING);

        if (replaced != null) {
            // What it waited for when it was replaced (e.g. reloaded by the error fixer): a segment that never came
            replaced.children.addAll(getBufferingExtras(timer, now));
            replaced.endMs = now;
            replaced.detail = "replaced";
        }

        timer.open(STAGE_BUFFERING, null, now);
        timer.prepareMs = now;
        timer.firstFrameMs = -1;
        timer.playingMs = -1;

        // A late one's pause is over with its player: given up at its minute again (see #onPlayerState)
        if (timer.isLate && timer.pausedMs >= 0) {
            sScheduler.cancel(sGiveUp);
            sScheduler.schedule(sGiveUp, Math.max(0, MAX_RECORDED_MS - (now - timer.t0)));
        }

        timer.pausedMs = -1;
        timer.positionMs = -1;
        timer.video = null;
        timer.videoBitrateKbps = -1;
        Arrays.fill(timer.isChunkLoaded, false);
        Arrays.fill(timer.chunkLoadMs, -1);
        Arrays.fill(timer.initAgainLoads, 0);
        Arrays.fill(timer.initAgainBytes, 0);
        Arrays.fill(timer.moreChunkLoads, 0);
        Arrays.fill(timer.moreChunkBytes, 0);
        timer.hasVideoDecoder = false;
        timer.keyframeResume = null;
    }

    /**
     * A segment starts loading, in the clock's time
     *
     * @param isInit its initialization (the format's header), not its media
     */
    public static void onLoadStarted(boolean isInit, boolean isVideo, boolean isAudio, long startMs) {
        Timer timer = getTimer();
        int chunk = chunkOf(isInit, isVideo, isAudio);

        if (chunk >= 0 && startMs >= sPreparedMs) {
            sIsChunkStarted[chunk] = true;
        }

        if (timer == null || chunk < 0 || timer.prepareMs < 0 || startMs < timer.prepareMs) {
            return;
        }

        if (timer.chunkLoadMs[chunk] < 0) {
            timer.chunkLoadMs[chunk] = startMs;
        }
    }

    /**
     * A segment loaded, in the clock's time
     *
     * @param isInit its initialization (the format's header), not its media
     */
    public static void onLoad(boolean isInit, boolean isVideo, boolean isAudio, long startMs, long endMs, long bytes) {
        Timer timer = getTimer();
        int chunk = chunkOf(isInit, isVideo, isAudio);

        if (chunk >= 0 && startMs >= sPreparedMs) {
            sIsChunkDone[chunk] = true;
        }

        if (timer == null || chunk < 0 || timer.prepareMs < 0 || startMs < timer.prepareMs) {
            return;
        }

        if (timer.isChunkLoaded[chunk]) {
            if (isInit) {
                addInitAgain(timer, chunk, startMs, endMs, bytes);
            } else {
                // After the init segments in CHUNK_STAGES: the video's, then the audio's
                addMoreChunk(timer, chunk - INIT_AGAIN_STAGES.length, startMs, endMs, bytes);
            }

            return;
        }

        timer.isChunkLoaded[chunk] = true;
        addToBuffering(timer, CHUNK_STAGES[chunk], formatBytes(bytes), startMs, endMs);
    }

    /**
     * A segment's load was cancelled (e.g. by a seek), in the clock's time: a row of its own, from when it was asked for, with the
     * bytes it got. It left nothing before (2M1DY5CD -BwLZBMKW4g: no load from 0.96 s to a jump to the live position at 1.76 s, then
     * the first chunks took 2.3-2.6 s on new connections, as loads cut off mid-response do).
     *
     * @param isInit its initialization (the format's header), not its media
     */
    public static void onLoadCanceled(boolean isInit, boolean isVideo, boolean isAudio, long startMs, long endMs, long bytes) {
        Timer timer = getTimer();
        int chunk = chunkOf(isInit, isVideo, isAudio);

        if (timer == null || chunk < 0 || timer.prepareMs < 0 || startMs < timer.prepareMs) {
            return;
        }

        // Its kind's first not loaded yet: the next one's load is the one still loading (see getBufferingExtras)
        if (!timer.isChunkLoaded[chunk]) {
            timer.chunkLoadMs[chunk] = -1;
        }

        if (timer.cancelledLoads++ < MAX_CANCELLED_LOADS) {
            String load = (isVideo ? "video " : "audio ") + (isInit ? Connections.LOAD_INIT_CHUNK : Connections.LOAD_CHUNK);
            addToBuffering(timer, STAGE_LOAD_CANCELLED, load + ", " + formatBytes(bytes), startMs, endMs);
        }
    }

    /**
     * A media segment's load after the first one of its track (see MORE_CHUNK_STAGES)
     *
     * @param track of the segment, in MORE_CHUNK_STAGES
     */
    private static void addMoreChunk(Timer timer, int track, long startMs, long endMs, long bytes) {
        if (timer.moreChunkLoads[track]++ == 0) {
            timer.moreChunkStartMs[track] = startMs;
            timer.moreChunkEndMs[track] = endMs;
        } else {
            timer.moreChunkStartMs[track] = Math.min(timer.moreChunkStartMs[track], startMs);
            timer.moreChunkEndMs[track] = Math.max(timer.moreChunkEndMs[track], endMs);
        }

        timer.moreChunkBytes[track] += Math.max(bytes, 0);
    }

    /**
     * An init segment's load after the first one of its track (see INIT_AGAIN_STAGES)
     *
     * @param chunk of the init segment, in CHUNK_STAGES
     */
    private static void addInitAgain(Timer timer, int chunk, long startMs, long endMs, long bytes) {
        if (timer.initAgainLoads[chunk]++ == 0) {
            timer.initAgainStartMs[chunk] = startMs;
            timer.initAgainEndMs[chunk] = endMs;
        } else {
            timer.initAgainStartMs[chunk] = Math.min(timer.initAgainStartMs[chunk], startMs);
            timer.initAgainEndMs[chunk] = Math.max(timer.initAgainEndMs[chunk], endMs);
        }

        timer.initAgainBytes[chunk] += Math.max(bytes, 0);
    }

    /**
     * Since the player was prepared, one track's first segment came and the other's started to load and never did: a request that
     * hangs, not formats that can't be played (see ErrorFixerController#onLongBuffering)
     */
    public static boolean isOneTrackStuck() {
        return isTrackStuck(true) && sIsChunkDone[chunkOf(false, false, true)]
                || isTrackStuck(false) && sIsChunkDone[chunkOf(false, true, false)];
    }

    private static boolean isTrackStuck(boolean isVideo) {
        int init = chunkOf(true, isVideo, !isVideo);
        int media = chunkOf(false, isVideo, !isVideo);

        return sIsChunkStarted[init] && !sIsChunkDone[init] || sIsChunkStarted[media] && !sIsChunkDone[media];
    }

    /**
     * Its index in CHUNK_STAGES, -1 for one not timed (e.g. a subtitle's)
     */
    private static int chunkOf(boolean isInit, boolean isVideo, boolean isAudio) {
        if (isVideo) {
            return isInit ? 0 : 2;
        }

        if (isAudio) {
            return isInit ? 1 : 3;
        }

        return -1;
    }

    /**
     * Its decoder took that long to get ready, till now
     */
    public static void onDecoder(boolean isVideo, String name, long initializationMs) {
        Timer timer = getTimer();

        if (timer == null || timer.prepareMs < 0 || !isVideo || timer.hasVideoDecoder) {
            return;
        }

        long now = sClock.now();
        timer.hasVideoDecoder = true;
        addToBuffering(timer, STAGE_VIDEO_DECODER, name, Math.max(timer.prepareMs, now - Math.max(0, initializationMs)), now);
    }

    /**
     * The display switches to the video's refresh rate: the playback waits for it (see AutoFrameRateController)
     */
    public static void onModeSwitchStart() {
        Timer timer = getTimer();

        if (timer != null && timer.modeSwitchMs < 0) {
            timer.modeSwitchMs = sClock.now();
            timer.modeSwitchMode = null;
            timer.modeReports.clear();
        }
    }

    /**
     * The mode the display is asked for, e.g. "3840x2160@48.0"
     */
    public static void onModeSwitchRequested(@Nullable String mode) {
        Timer timer = getTimer();

        if (timer != null && timer.modeSwitchMs >= 0) {
            timer.modeSwitchMode = mode;
        }
    }

    /**
     * The display told of a change, e.g. "3840x2160@48.0 id 7" (see UhdHelper): the one asked for or not. A switch only ended by its
     * timeout said nothing of why (DBRPJG76: 12 holds of 5.5 s).
     */
    public static void onDisplayModeReported(String mode, boolean isExpected) {
        Timer timer = getTimer();

        if (timer != null && timer.modeSwitchMs >= 0 && timer.modeReports.size() < MAX_MODE_REPORTS) {
            timer.modeReports.add("display said " + mode + (isExpected ? "" : ", not it"));
        }
    }

    /**
     * @param how e.g. MODE_SWITCH_REPORTED, MODE_SWITCH_TIMED_OUT
     */
    public static void onModeSwitchEnd(@Nullable String how) {
        Timer timer = getTimer();

        if (timer == null || timer.modeSwitchMs < 0) {
            return;
        }

        addToBuffering(timer, STAGE_MODE_SWITCH, describeModeSwitch(timer, how), timer.modeSwitchMs, sClock.now());
        timer.modeSwitchMs = -1;
    }

    /**
     * E.g. "to 3840x2160@48.0, timed out, display said 3840x2160@48.0 id 9, not it", null for nothing known
     */
    @Nullable
    private static String describeModeSwitch(Timer timer, @Nullable String how) {
        List<String> parts = new ArrayList<>(timer.modeReports.size() + 2);

        if (timer.modeSwitchMode != null) {
            parts.add("to " + timer.modeSwitchMode);
        }

        if (how != null) {
            parts.add(how);
        }

        parts.addAll(timer.modeReports);

        return parts.isEmpty() ? null : TextUtils.join(", ", parts);
    }

    /**
     * The playback is sent elsewhere while it starts (e.g. back to 0:00 from a resume at its end, then skipped ahead): what its first
     * frame waited for is the last one's
     */
    public static void onSeek(long positionMs) {
        onSeek(positionMs, -1, -1);
    }

    /**
     * Of a live stream, where it was and where the live end is: its jump to the live position is made when it's within 1 s of it (see
     * VideoStateController#isLiveEnd). 2M1DY5CD -BwLZBMKW4g jumped there at 1.76 s, 15 s behind it since 0.74 s, by what wasn't known.
     *
     * @param fromMs where it was, -1 for not told
     * @param liveEndMs where the live stream's window ends, -1 for not told (e.g. not live)
     */
    public static void onSeek(long positionMs, long fromMs, long liveEndMs) {
        Timer timer = getTimer();

        // Till it's recorded, its first frame or not (a tunneled one plays before it): the play's own seeks are kept from then (see
        // Plays#onSeek)
        if (timer == null || timer.prepareMs < 0) {
            return;
        }

        // Sent elsewhere, its decoder's way to the position is another's
        timer.keyframeResume = null;

        if (timer.seeks >= MAX_SEEKS) {
            return;
        }

        long now = sClock.now();
        timer.seeks++;
        // What sent it there (0J159Z0Z: the tunneled resumes that froze had one seek, the ones that played two, made by what wasn't known)
        String caller = describeSeekCaller(Thread.currentThread().getStackTrace());
        String detail = describeSeek(positionMs, fromMs, liveEndMs);
        addToBuffering(timer, STAGE_SEEK, caller != null ? detail + ", " + caller : detail, now, now);
    }

    /**
     * E.g. "to 0:30", "to 9:11, from 9:10, 850 ms before the live end", "to 9:11, live end 9:12"
     */
    @VisibleForTesting
    static String describeSeek(long positionMs, long fromMs, long liveEndMs) {
        StringBuilder result = new StringBuilder("to ").append(VideoStart.formatPosition(positionMs));

        if (fromMs >= 0) {
            result.append(", from ").append(VideoStart.formatPosition(fromMs));
        }

        if (liveEndMs >= 0 && fromMs >= 0) {
            long toEndMs = liveEndMs - fromMs;
            result.append(", ").append(Math.abs(toEndMs)).append(toEndMs >= 0 ? " ms before the live end" : " ms past the live end");
        } else if (liveEndMs >= 0) {
            result.append(", live end ").append(VideoStart.formatPosition(liveEndMs));
        }

        return result.toString();
    }

    /**
     * What the resume made of the keyframe before its position (e.g. KEYFRAME_GONE_BACK, see ExoPlayerController#maybeResumeFromKeyframe):
     * said after the frames on its "to the position" row. The last told counts.
     */
    public static void onKeyframeResume(String decision) {
        Timer timer = getTimer();

        if (timer != null && timer.prepareMs >= 0 && timer.firstFrameMs < 0) {
            timer.keyframeResume = decision;
        }
    }

    /**
     * E.g. "not worth going back, 380 ms to go": the decoder gets to the position sooner than going back costs, as it was estimated
     * (see KeyframeResume)
     *
     * @param isAfterAudio decided again once the audio came in (see KEYFRAME_AUDIO_LOADING)
     */
    public static String describeKeyframeNotWorthIt(long remainingMs, boolean isAfterAudio) {
        return describeKeyframeDecision(KEYFRAME_NOT_WORTH_IT + ", " + remainingMs + " ms to go", isAfterAudio);
    }

    /**
     * E.g. "decoder there already", "after the audio, gone back to the keyframe"
     *
     * @param isAfterAudio decided again once the audio came in (see KEYFRAME_AUDIO_LOADING)
     */
    public static String describeKeyframeDecision(String decision, boolean isAfterAudio) {
        return isAfterAudio ? KEYFRAME_AFTER_AUDIO + ", " + decision : decision;
    }

    /**
     * Of a start's seek, the app's code that made it: the first of the app's frames past the ones that pass it on (e.g.
     * "VideoStateController.restoreState, resume", "ExoPlayerController.maybeResumeFromKeyframe"), null when none is the app's (e.g. a
     * test's)
     */
    @Nullable
    static String describeSeekCaller(StackTraceElement[] stack) {
        boolean isResume = false;

        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            String method = frame.getMethodName();

            if (!className.startsWith(APP_PACKAGE) || className.startsWith(VideoStarts.class.getName())) {
                continue;
            }

            String simpleName = className.substring(className.lastIndexOf('.') + 1);
            int inner = simpleName.indexOf('$');
            simpleName = inner > 0 ? simpleName.substring(0, inner) : simpleName;

            if (simpleName.endsWith("Test")) {
                return null;
            }

            if (SEEK_PASS_THROUGHS.contains(method) || method.startsWith("lambda$")) {
                isResume |= "setResumePositionMs".equals(method);
                continue;
            }

            return simpleName + "." + method + (isResume ? ", resume" : "");
        }

        return isResume ? "resume" : null;
    }

    public static void onFirstFrame() {
        onFirstFrame(null);
    }

    /**
     * @param frame where it starts and what with, null when not known
     */
    public static void onFirstFrame(@Nullable FirstFrame frame) {
        Timer timer = getTimer();

        if (timer == null || timer.prepareMs < 0 || timer.firstFrameMs >= 0) {
            return;
        }

        timer.firstFrameMs = sClock.now();

        if (frame != null) {
            timer.positionMs = frame.positionMs;
            timer.video = frame.video;
            timer.videoBitrateKbps = frame.videoBitrateKbps;

            // Of this source: the renderer counts since its position was set. Gone back to the keyframe, it has none: its seek says so.
            if (frame.skippedFrames > 0 && frame.skipStartMs >= timer.prepareMs) {
                String frames = frame.skippedFrames + " frames";
                addToBuffering(timer, STAGE_TO_POSITION, timer.keyframeResume != null ? frames + ", " + timer.keyframeResume : frames,
                        frame.skipStartMs, timer.firstFrameMs);
            }
        }

        maybeFinish(timer, true);
    }

    /**
     * The player's state changed: ready and paused (by the user, or prepared so) still when its time is up, the start is paused, not
     * stuck (see #giveUp). Before #onPlaying.
     *
     * @param isReady it can play from where it is (ExoPlayer's STATE_READY)
     */
    public static void onPlayerState(boolean isReady, boolean playWhenReady) {
        Timer timer = getTimer();

        if (timer == null || timer.prepareMs < 0) {
            return;
        }

        if (!isReady || playWhenReady) {
            // A late one's pause is over: given up at its minute again, not at the pause's end (see below)
            if (timer.isLate && timer.pausedMs >= 0) {
                sScheduler.cancel(sGiveUp);
                sScheduler.schedule(sGiveUp, Math.max(0, MAX_RECORDED_MS - (sClock.now() - timer.t0)));
            }

            timer.pausedMs = -1;
        } else if (timer.pausedMs < 0) {
            timer.pausedMs = sClock.now();

            // Past its 15 s (see #giveUp): still paused a while after, paused from now, as it would have been then. Else a play long
            // after would count the pause as start time, and a leave would read as never ready.
            if (timer.isLate) {
                sScheduler.cancel(sGiveUp);
                sScheduler.schedule(sGiveUp, LATE_PAUSED_MS);
            }
        }
    }

    /**
     * The player is ready and plays
     *
     * @param hasSurface the video is shown: with none, no frame is
     */
    public static void onPlaying(boolean hasSurface) {
        Timer timer = getTimer();

        if (timer == null || timer.prepareMs < 0) {
            return;
        }

        if (timer.playingMs < 0) {
            timer.playingMs = sClock.now();
        }

        maybeFinish(timer, hasSurface);
    }

    /**
     * How the first seconds of a video that played went, or what it played of them when it was left sooner: added to its start, which
     * says nothing of them (it ends at the first frame)
     */
    public static void onFrames(PlaybackHealth.StartFrames frames) {
        Long ts = frames.videoId != null ? sPlayed.remove(frames.videoId) : null;

        if (ts == null) {
            return;
        }

        JSONObject result = new JSONObject();

        try {
            result.put("playedMs", frames.playedMs);
            // A 50 fps video's first 10 s at 1.5x are 75 frames a second: its drops are of that rate (0HCC641V)
            result.put("speed", Double.parseDouble(Float.toString(PlaybackSpeed.get())));
            result.put("rendered", frames.renderedFrames);
            result.put("dropped", frames.droppedFrames);
            result.put("droppedToKeyframes", frames.droppedToKeyframes);
            result.put("longestFrozenMs", frames.longestFrozenMs);

            // Whether the picture waited on an audio timestamp that didn't advance, and for how long: below what's a failure too
            PlaybackHealth.StartAudioClock audio = frames.audioClock;

            if (audio != null) {
                result.put("audioTimestampState", audio.timestampState);
                result.put("audioFramesMismatches", audio.framesMismatches);
                result.put("audioClockMismatches", audio.clockMismatches);
                result.put("audioHoldReleases", audio.holdReleases);
                result.put("audioLongestHoldMs", audio.longestHoldMs);

                if (audio.holdSkips >= 0) {
                    result.put("audioHoldSkips", audio.holdSkips);
                }
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }

        sFramesRecorder.record(ts, result);
    }

    /**
     * Forgotten, not recorded: it wasn't the user's (e.g. a crash's restore)
     */
    public static void cancel() {
        if (sTimer != null) {
            Log.d(TAG, "Video start cancelled: %s", sTimer.videoId);
        }

        sTimer = null;
        sScheduler.cancel(sGiveUp);
    }

    /**
     * The player's screen went behind another while the video starts (not a dialog of its own, nor PIP, see
     * VideoStartController#onViewPaused): left, unless it's back within VIEW_PAUSED_MS, as of when it went. A remote's start into a
     * process just begun was recorded as left at 2.2 s, and the video played on to its end: the next start of that process, 15.6 min
     * later, was the autoplay of a video prefetched while it played (0HCC641V gN3LBlDbUjs). Its screen came up, was behind something
     * for a moment as the app came up, and the start was over for good.
     */
    public static void onViewPaused() {
        Timer timer = sTimer;

        if (timer == null || timer.behindMs >= 0) {
            return;
        }

        timer.behindMs = sClock.now();
        sScheduler.cancel(sLeaveBehind);
        sScheduler.schedule(sLeaveBehind, VIEW_PAUSED_MS);
    }

    /**
     * The player's screen is in front again: its start goes on, the time behind as a row of its own
     */
    public static void onViewResumed() {
        sScheduler.cancel(sLeaveBehind);
        Timer timer = sTimer;

        if (timer == null || timer.behindMs < 0) {
            return;
        }

        timer.open(STAGE_SCREEN_BEHIND, null, timer.behindMs).endMs = sClock.now();
        timer.behindMs = -1;
    }

    private static void leaveBehind() {
        Timer timer = sTimer;

        if (timer != null && timer.behindMs >= 0) {
            abandon(VideoStart.OUTCOME_LEFT, timer.behindMs);
        }
    }

    /**
     * The start going on never played: it's recorded as it is, with why (see VideoStart#OUTCOME_LEFT), unless it was left at once
     */
    public static void abandon(String outcome) {
        Timer timer = sTimer;
        // Left with its screen behind another (e.g. the player closed there): as of when it went. The app's own verdicts (unplayable,
        // upcoming) and another video's start are as of now.
        boolean isLeftBehind = timer != null && timer.behindMs >= 0 && VideoStart.OUTCOME_LEFT.equals(outcome);
        abandon(outcome, isLeftBehind ? timer.behindMs : -1);
    }

    /**
     * @param atMs when it was left, in the clock's time (e.g. when its screen went behind another, see #onViewPaused), -1 for now
     */
    private static void abandon(String outcome, long atMs) {
        Timer timer = sTimer;

        if (timer == null) {
            return;
        }

        sTimer = null;
        sScheduler.cancel(sGiveUp);
        sScheduler.cancel(sLeaveBehind);
        long now = atMs >= 0 ? atMs : sClock.now();

        // A reload after a start that gave up, that didn't play either: counted, not recorded (see #onReload)
        if (timer.isRecovery) {
            Log.d(TAG, "Recovery reload %s after %d ms: %s", outcome, now - timer.t0, timer.videoId);
            return;
        }

        // Unplayable (or not begun) is the app's own outcome, not the user skipping it
        if (now - timer.t0 < MIN_ABANDONED_MS && !Helpers.equalsAny(outcome, VideoStart.OUTCOME_UNPLAYABLE, VideoStart.OUTCOME_UPCOMING)) {
            Log.d(TAG, "Video start %s after %d ms: %s", outcome, now - timer.t0, timer.videoId);
            return;
        }

        // Late, ready and paused when it was left: paused, timed till it was ready, as it would have been then (see #onPlayerState).
        // Not finished as a play: the player is closing.
        boolean isLatePaused = timer.isLate && timer.pausedMs >= 0
                && Helpers.equalsAny(outcome, VideoStart.OUTCOME_LEFT, VideoStart.OUTCOME_REPLACED);
        record(isLatePaused ? build(timer, Math.max(timer.pausedMs, timer.firstFrameMs), VideoStart.OUTCOME_PAUSED)
                : build(timer, now, outcome));

        if (VideoStart.OUTCOME_GAVE_UP.equals(outcome)) {
            // Its reloads are timed till one plays (see #onReload)
            sRecoveryId = timer.videoId;
            sRecoveryFromMs = timer.t0;
            sRecoveryReloads = 0;
        }
    }

    /**
     * The start going on, as it is now: what it's waiting for. Null with none.
     */
    @Nullable
    public static VideoStart snapshot() {
        Timer timer = getTimer();

        return timer != null ? build(timer, sClock.now(), VideoStart.OUTCOME_WAITING) : null;
    }

    private static void maybeFinish(Timer timer, boolean hasSurface) {
        if (timer.playingMs < 0 || (timer.firstFrameMs < 0 && hasSurface)) {
            return;
        }

        finish(timer, Math.max(timer.playingMs, timer.firstFrameMs), VideoStart.OUTCOME_PLAYED);
    }

    /**
     * Not playing 15 s after the user's action: recorded as paused if it's been ready and paused since (by the user, or started so),
     * timed till it was ready. A paused start was recorded as given up, as if it were stuck: one that showed its first frame with 54 s
     * buffered, then played 97 s once the user played it. Otherwise it's late: timed on till it plays or is left, given up at
     * MAX_RECORDED_MS.
     */
    private static void giveUp() {
        Timer timer = sTimer;

        if (timer == null) {
            return;
        }

        if (timer.pausedMs >= 0) {
            finish(timer, Math.max(timer.pausedMs, timer.firstFrameMs), VideoStart.OUTCOME_PAUSED);
            return;
        }

        long leftMs = MAX_RECORDED_MS - (sClock.now() - timer.t0);

        if (leftMs > 0) {
            timer.isLate = true;
            sScheduler.cancel(sGiveUp);
            sScheduler.schedule(sGiveUp, leftMs);
            return;
        }

        // Its screen behind another before its time was up (see #onViewPaused): the user left it then, it didn't give up
        abandon(timer.behindMs >= 0 ? VideoStart.OUTCOME_LEFT : VideoStart.OUTCOME_GAVE_UP);
    }

    /**
     * It got ready (see VideoStart#isReady): the video plays from it, its first seconds are added to it once they're played
     */
    private static void finish(Timer timer, long endMs, String outcome) {
        sTimer = null;
        sScheduler.cancel(sGiveUp);
        sScheduler.cancel(sLeaveBehind);
        sReloadableId = timer.videoId;

        if (timer.isRecovery) {
            // E.g. "reload 4 since a start gave up 67 s before it"
            timer.open(STAGE_RECOVERY, "reload " + sRecoveryReloads + " since a start gave up " + (timer.t0 - sRecoveryFromMs) / 1_000
                    + " s before it", timer.t0).endMs = timer.t0;
        }

        if (timer.videoId.equals(sRecoveryId)) {
            sRecoveryId = null;
        }
        // The same video again: its new start
        sPlayed.remove(timer.videoId);
        sPlayed.put(timer.videoId, timer.ts);

        for (Iterator<String> oldest = sPlayed.keySet().iterator(); sPlayed.size() > MAX_PLAYED; ) {
            oldest.next();
            oldest.remove();
        }
        record(build(timer, endMs, outcome));
        // After it's recorded: how its play goes on from here is added to it (see Plays)
        Plays.onStarted(timer.videoId, timer.ts, VideoStart.OUTCOME_PLAYED.equals(outcome));
    }

    private static void record(VideoStart start) {
        logGraph(StartGraphText.render(start));
        sRecorder.record(start);

        if (sListener != null) {
            sListener.onTimed(start);
        }
    }

    /**
     * In entries under the size logcat cuts them at, whole lines each
     */
    private static void logGraph(String graph) {
        StringBuilder entry = new StringBuilder();

        for (String line : graph.split("\n")) {
            if (entry.length() > 0 && entry.length() + line.length() + 1 > MAX_LOG_ENTRY) {
                android.util.Log.i(LOG_TAG, entry.toString());
                entry.setLength(0);
            }

            if (entry.length() > 0) {
                entry.append('\n');
            }

            entry.append(line);
        }

        if (entry.length() > 0) {
            android.util.Log.i(LOG_TAG, entry.toString());
        }
    }

    /**
     * The timer isn't changed: a start going on is built again each time it's looked at (see #snapshot)
     *
     * @param endMs when it played, or was given up or looked at
     */
    private static VideoStart build(Timer timer, long endMs, String outcome) {
        // Ready to play (played, or paused then): its stages ended by then
        boolean isReady = VideoStart.OUTCOME_PLAYED.equals(outcome) || VideoStart.OUTCOME_PAUSED.equals(outcome);
        boolean isLookedAt = VideoStart.OUTCOME_WAITING.equals(outcome);
        List<VideoStart.Stage> stages = new ArrayList<>();
        List<Group> groups = new ArrayList<>(timer.groups);
        Collections.sort(groups, (a, b) -> Long.compare(a.startMs, b.startMs));
        Comparator<VideoStart.Stage> byStart =
                (a, b) -> a.startMs != b.startMs ? Long.compare(a.startMs, b.startMs) : Integer.compare(a.depth, b.depth);
        Group buffering = timer.findOpen(STAGE_BUFFERING);
        Group formats = timer.findOpen(STAGE_FORMATS);
        List<VideoStart.Stage> bufferingExtras = getBufferingExtras(timer, endMs);

        if (buffering == null) {
            // E.g. a display mode switch before the player buffers: rows of their own
            for (VideoStart.Stage extra : bufferingExtras) {
                Group group = new Group(extra.name, extra.detail, extra.startMs);
                group.endMs = extra.endMs;
                groups.add(group);
            }

            Collections.sort(groups, (a, b) -> Long.compare(a.startMs, b.startMs));
        }

        for (Group group : groups) {
            boolean isOpen = group.endMs < 0;

            // Begun after the record's end, e.g. the engine released by leaving a late start recorded paused at its pause (see #abandon):
            // not of it
            if (group.startMs > endMs) {
                continue;
            }

            // Leaving the player released its engine: no restart
            if (isOpen && STAGE_ENGINE_RESTART.equals(group.name) && VideoStart.OUTCOME_LEFT.equals(outcome)) {
                continue;
            }

            long groupEndMs = isOpen ? endMs : group.endMs;
            // Not played, it hadn't ended
            String detail = isOpen && !isReady ? (group.detail != null ? group.detail + ", " + DETAIL_GOING : DETAIL_GOING)
                    : group.detail;
            stages.add(new VideoStart.Stage(group.name, detail, 0, group.startMs - timer.t0, groupEndMs - timer.t0));
            List<VideoStart.Stage> children = new ArrayList<>(group.children);

            if (group == buffering) {
                children.addAll(bufferingExtras);
            } else if (group == formats) {
                children.addAll(getFetchSoFar(timer, endMs));
            }

            Collections.sort(children, byStart); // stable: a fetch's stages keep their order

            for (VideoStart.Stage child : children) {
                // Too short to see, unless it's a moment, or how a poToken was got at once (e.g. none, see PoTokenGate#getPoToken)
                if (child.getDurationMs() < 1 && child.getCategory() != VideoStart.Category.MARK
                        && child.getCategory() != VideoStart.Category.POTOKEN) {
                    continue;
                }

                stages.add(new VideoStart.Stage(child.name, child.detail, child.depth, child.startMs - timer.t0, child.endMs - timer.t0));
            }
        }

        if (timer.firstFrameMs >= 0) {
            long frameMs = timer.firstFrameMs - timer.t0;
            stages.add(new VideoStart.Stage(STAGE_FIRST_FRAME, null, 0, frameMs, frameMs));
        }

        // Whether the run's first video found ready what the warm-up was to get ready, or the warm-up never ran. Still going then, when
        // it ended is in the next starts'.
        boolean isWarmUpFollowUp = !timer.isFirstOfRun && sWarmUpFollowUps > 0;
        List<VideoStart.WarmUp> warmUps = (timer.isFirstOfRun || isWarmUpFollowUp) && !isLookedAt ? buildWarmUps(timer, endMs) : null;

        if (warmUps != null) {
            sWarmUpFollowUps = !hasOpen(warmUps) ? 0 : timer.isFirstOfRun ? MAX_WARM_UP_FOLLOW_UPS : sWarmUpFollowUps - 1;
        }
        // E.g. starved of memory
        JSONObject state = !isReady && !isLookedAt ? sState.get() : null;

        return new VideoStart(timer.ts, timer.videoId, timer.trigger, timer.isFirstOfRun, timer.sinceProcessStartMs, timer.client,
                timer.protocol, timer.engine, timer.isCached, outcome, endMs - timer.t0, stages, timer.positionMs, timer.video,
                timer.videoBitrateKbps, warmUps, state, timer.reason);
    }

    /**
     * The warm-ups that began before the start's end: as they are now, a step that ended after it too
     */
    private static List<VideoStart.WarmUp> buildWarmUps(Timer timer, long endMs) {
        List<VideoStart.WarmUp> result = new ArrayList<>();

        try {
            for (WarmUps.Run run : sWarmUps.get()) {
                if (run.startMs > endMs) {
                    continue;
                }

                List<VideoStart.WarmUpStep> steps = new ArrayList<>();

                for (WarmUps.Step step : run.steps) {
                    steps.add(new VideoStart.WarmUpStep(step.name, step.startMs - timer.t0,
                            step.isOpen() ? VideoStart.WarmUp.OPEN : step.endMs - timer.t0, step.result, step.detail));
                }

                result.add(new VideoStart.WarmUp(run.startMs - timer.t0, run.isOpen() ? VideoStart.WarmUp.OPEN : run.endMs - timer.t0,
                        steps));
            }
        } catch (RuntimeException e) {
            // Recorded without them
            Log.e(TAG, "Can't read the warm-ups: %s", e.getMessage());
        }

        return result;
    }

    private static boolean hasOpen(List<VideoStart.WarmUp> warmUps) {
        for (VideoStart.WarmUp warmUp : warmUps) {
            if (warmUp.isOpen()) {
                return true;
            }

            for (VideoStart.WarmUpStep step : warmUp.steps) {
                if (step.isOpen()) {
                    return true;
                }
            }
        }

        return false;
    }

    @Nullable
    private static JSONObject getState() {
        return sContext != null ? DeviceCapabilities.buildStartState(sContext) : null;
    }

    /**
     * What the buffering waits for at the time, not ended yet: the first segments still loading, a display mode switch, the player
     * holding its loading back
     */
    private static List<VideoStart.Stage> getBufferingExtras(Timer timer, long endMs) {
        List<VideoStart.Stage> result = new ArrayList<>();

        for (int chunk = 0; chunk < CHUNK_STAGES.length; chunk++) {
            // Still loading: it played from what had come, or it's what doesn't come
            if (!timer.isChunkLoaded[chunk] && timer.chunkLoadMs[chunk] >= 0) {
                result.add(new VideoStart.Stage(CHUNK_STAGES[chunk], DETAIL_LOADING, 1, timer.chunkLoadMs[chunk], endMs));
            }
        }

        for (int chunk = 0; chunk < INIT_AGAIN_STAGES.length; chunk++) {
            // Asked again: the first one's answer had no init segment, or the format changed
            if (timer.initAgainLoads[chunk] > 0) {
                String detail = String.format(Locale.US, "%d more, %s", timer.initAgainLoads[chunk],
                        formatBytes(timer.initAgainBytes[chunk]));
                result.add(new VideoStart.Stage(INIT_AGAIN_STAGES[chunk], detail, 1, timer.initAgainStartMs[chunk],
                        Math.min(timer.initAgainEndMs[chunk], endMs)));
            }
        }

        if (timer.modeSwitchMs >= 0) {
            result.add(new VideoStart.Stage(STAGE_MODE_SWITCH, DETAIL_GOING, 1, timer.modeSwitchMs, endMs));
        }

        if (timer.prepareMs >= 0) {
            // The player didn't load (e.g. its buffer was full): what a segment asked for late waited for
            result.addAll(sLoadHolds.take(timer.prepareMs, endMs));
            // A new connection to the server, and each request's wait for the answer: without a long one, a slow segment is slow to
            // come
            result.addAll(sConnections.take(timer.prepareMs, endMs));
            // A wait asked for by the server and not waited: what's asked for in it may be answered late
            result.addAll(sBackoffs.take(timer.prepareMs, endMs));
            // A live stream at its head: the next segment wasn't there yet, and its request waits
            result.addAll(sLiveHeadWaits.take(timer.prepareMs, endMs));
            // Whether the server took the poToken: past its grace period, it sends no media with one it hasn't
            result.addAll(sProtection.take(timer.videoId, timer.prepareMs, endMs));
            // A load that failed: the player waits before it tries again, a gap with no row of its own
            result.addAll(sLoadErrors.take(timer.prepareMs, endMs));
        }

        // Its first frame was shown and it didn't play: what it waited on from there
        if (timer.firstFrameMs >= 0 && endMs - timer.firstFrameMs >= AFTER_FRAME_MIN_MS) {
            for (int track = 0; track < MORE_CHUNK_STAGES.length; track++) {
                if (timer.moreChunkLoads[track] > 0 && timer.moreChunkStartMs[track] < endMs) {
                    String detail = String.format(Locale.US, "%d, %s", timer.moreChunkLoads[track],
                            formatBytes(timer.moreChunkBytes[track]));
                    result.add(new VideoStart.Stage(MORE_CHUNK_STAGES[track], detail, 1, timer.moreChunkStartMs[track],
                            Math.min(timer.moreChunkEndMs[track], endMs)));
                }
            }

            // The renderer that couldn't play from where it was: the player is ready only once both can
            result.addAll(sRendererWaits.take(timer.firstFrameMs, endMs));
        }

        return result;
    }

    /**
     * Of the fetch of the formats going on: its stages so far, the ones still going up to now. Before it runs, it waits in the queue.
     */
    private static List<VideoStart.Stage> getFetchSoFar(Timer timer, long endMs) {
        List<VideoStart.Stage> result = new ArrayList<>();
        long fetchStartMs = -1;

        for (FetchStages.Stage stage : sFetchStages.peek(timer.videoId)) {
            // Of this fetch, not of one before (e.g. the history's)
            if (stage.startMs < timer.fetchMs) {
                continue;
            }

            if (stage.depth == 0 && FETCH.equals(stage.name)) {
                fetchStartMs = fetchStartMs < 0 ? stage.startMs : Math.min(fetchStartMs, stage.startMs);
            } else if (stage.isOpen()) {
                // What it's at too, e.g. the lock wait's holder, the warm-up's stage it waits through (C4JB8CZX)
                String detail = stage.detail != null ? stage.detail + ", " + DETAIL_GOING : DETAIL_GOING;
                result.add(new VideoStart.Stage(stage.name, detail, Math.max(1, stage.depth), stage.startMs, endMs));
            } else {
                result.add(new VideoStart.Stage(stage.name, stage.detail, Math.max(1, stage.depth), stage.startMs, stage.endMs));
            }
        }

        if (timer.fetchMs >= 0) {
            long queueEndMs = fetchStartMs >= 0 ? fetchStartMs : endMs;

            if (queueEndMs > timer.fetchMs) {
                result.add(new VideoStart.Stage(STAGE_QUEUE, getQueueDetail(timer, queueEndMs, fetchStartMs >= 0 ? null : DETAIL_GOING), 1,
                        timer.fetchMs, queueEndMs));
            }
        }

        return result;
    }

    /**
     * Of a long wait for the fetch to begin, what the threads of the tasks were busy with when the formats were asked for: a video
     * opened as the app started waited 7.4-13.4 s and nothing told for what (CGHFJ3BZ)
     */
    @Nullable
    private static String getQueueDetail(Timer timer, long queueEndMs, @Nullable String detail) {
        if (timer.fetchLoad == null || queueEndMs - timer.fetchMs < QUEUE_LOAD_MS) {
            return detail;
        }

        return detail != null ? timer.fetchLoad + ", " + detail : timer.fetchLoad;
    }

    private static void addToBuffering(Timer timer, String name, @Nullable String detail, long startMs, long endMs) {
        Group buffering = timer.findOpen(STAGE_BUFFERING);

        if (buffering != null) {
            buffering.children.add(new VideoStart.Stage(name, detail, 1, startMs, endMs));
        } else {
            timer.open(name, detail, startMs).endMs = endMs;
        }
    }

    /**
     * The start going on. One that's run too long is late or given up (see #giveUp), its give-up not run yet (e.g. the main thread busy).
     */
    @Nullable
    private static Timer getTimer() {
        Timer timer = sTimer;

        if (timer != null && sClock.now() - timer.t0 > (timer.isLate ? MAX_RECORDED_MS : MAX_START_MS)) {
            giveUp();
            return sTimer == timer ? timer : null;
        }

        return timer;
    }

    /**
     * The start going on, if it's the video's: none for a video with no ID
     */
    @Nullable
    private static Timer getTimer(@Nullable String videoId) {
        Timer timer = getTimer();

        return timer != null && videoId != null && videoId.equals(timer.videoId) ? timer : null;
    }

    /**
     * E.g. 2.1 MB, 640 KB
     */
    static String formatBytes(long bytes) {
        if (bytes >= 1_000_000) {
            return String.format(Locale.US, "%.1f MB", bytes / 1_000_000f);
        }

        return String.format(Locale.US, "%d KB", Math.round(bytes / 1_000f));
    }

    private static void save(VideoStart start) {
        if (sContext != null) {
            VideoStartLog.instance(sContext).record(start);
        }
    }

    private static void saveFrames(long ts, JSONObject frames) {
        if (sContext != null) {
            VideoStartLog.instance(sContext).addFrames(ts, frames);
        }
    }

    /**
     * Prepared, the player has its engine: Cronet's is made by then
     */
    @Nullable
    private static String getEffectiveEngine() {
        if (sContext == null) {
            return null;
        }

        try {
            return Utils.getEffectiveNetworkEngine(sContext);
        } catch (RuntimeException e) {
            // Cronet couldn't be checked: recorded without it
            return null;
        }
    }

    /**
     * Of the app's process, now. -1 when not known.
     */
    static long getSinceProcessStartMs() {
        long processStart = sProcessStart.now();
        long now = sClock.now();

        return processStart > 0 && now >= processStart ? now - processStart : -1;
    }

    @VisibleForTesting
    static void resetForTesting() {
        sTimer = null;
        sOpenStepDepth = 0;
        sPlayed.clear();
        Plays.resetForTesting();
        sReloadableId = null;
        sRecoveryId = null;
        sRecoveryFromMs = -1;
        sRecoveryReloads = 0;
        Arrays.fill(sIsChunkStarted, false);
        Arrays.fill(sIsChunkDone, false);
        sPreparedMs = -1;
        sEngine = VideoStarts::getEffectiveEngine;
        sHasStarted = false;
        sListener = null;
        sClock = SystemClock::elapsedRealtime;
        sWallClock = System::currentTimeMillis;
        sProcessStart = Process::getStartElapsedRealtime;
        sFetchStages = new FetchStageSource() {
            @Override
            public List<FetchStages.Stage> take(String videoId) {
                return FetchStages.take(videoId);
            }

            @Override
            public List<FetchStages.Stage> peek(String videoId) {
                return FetchStages.peek(videoId);
            }
        };
        sScheduler = new HandlerScheduler();
        sLoadHolds = LoadHolds::take;
        sConnections = Connections::take;
        sBackoffs = ServerBackoffs::take;
        sLiveHeadWaits = LiveHeadWaits::take;
        sProtection = ProtectionStatuses::take;
        sLoadErrors = LoadErrors::take;
        sRendererWaits = RendererWaits::take;
        sRecorder = VideoStarts::save;
        sFramesRecorder = VideoStarts::saveFrames;
        sWarmUps = WarmUps::snapshot;
        sWarmUpFollowUps = 0;
        sState = VideoStarts::getState;
    }
}
