package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.youtubeapi.videoinfo.V2.FetchStages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
     * Not playing by then, the start is given up and recorded as it is (see VideoStart#OUTCOME_GAVE_UP)
     */
    static final long MAX_START_MS = 15_000;
    /**
     * A start that never played is recorded when it ran this long: one left sooner was skipped by the user (e.g. pressing next
     * twice), not stuck
     */
    static final long MIN_ABANDONED_MS = 2_000;
    /**
     * A link processed this long after the process started didn't start it (something else did, e.g. a service)
     */
    static final long MAX_COLD_START_MS = 30_000;
    static final String STAGE_APP_START = "app start";
    static final String STAGE_PLAYER_SCREEN = "player screen";
    static final String STAGE_ENGINE_RESTART = "engine restart";
    static final String STAGE_FORMATS = "formats";
    static final String STAGE_QUEUE = "queue";
    static final String STAGE_TO_PLAYER = "to the player";
    static final String STAGE_OPEN = "open";
    static final String STAGE_BUFFERING = "buffering";
    static final String STAGE_FIRST_VIDEO_CHUNK = "first video chunk";
    static final String STAGE_FIRST_AUDIO_CHUNK = "first audio chunk";
    static final String STAGE_VIDEO_INIT_CHUNK = "video init chunk";
    static final String STAGE_AUDIO_INIT_CHUNK = "audio init chunk";
    static final String STAGE_LOADING_HELD = "loading held";
    /**
     * The first segments of a start, by kind: each its own row
     */
    private static final String[] CHUNK_STAGES = {STAGE_VIDEO_INIT_CHUNK, STAGE_AUDIO_INIT_CHUNK, STAGE_FIRST_VIDEO_CHUNK,
            STAGE_FIRST_AUDIO_CHUNK};
    static final String STAGE_VIDEO_DECODER = "video decoder";
    static final String STAGE_MODE_SWITCH = "display mode switch";
    static final String STAGE_FIRST_FRAME = "first frame";
    /**
     * The whole of a fetch (see FetchStages#open): its stages are shown, not it
     */
    private static final String FETCH = "fetch";
    private static final String DETAIL_RETRY = "retry";
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
     * A start not playing 15 s after the user's action is given up, even when nothing happens any more (e.g. a request hangs)
     */
    private static final Runnable sGiveUp = () -> abandon(VideoStart.OUTCOME_GAVE_UP);
    @VisibleForTesting
    static LoadHoldSource sLoadHolds = LoadHolds::take;
    @VisibleForTesting
    static Recorder sRecorder = VideoStarts::save;
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

    interface Clock {
        long now();
    }

    interface LoadHoldSource {
        List<VideoStart.Stage> take(long fromMs, long toMs);
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
        int fetches;
        long prepareMs = -1;
        long firstFrameMs = -1;
        long playingMs = -1;
        long modeSwitchMs = -1;
        /**
         * Of each kind of first segment (see CHUNK_STAGES): loaded, and when it started to, -1 till then (SABR streams a response for
         * long, past the first frame)
         */
        final boolean[] isChunkLoaded = new boolean[CHUNK_STAGES.length];
        final long[] chunkLoadMs = new long[CHUNK_STAGES.length];
        boolean hasVideoDecoder;
        boolean isCached;
        String client;
        String protocol;

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
        timer.open(STAGE_FORMATS, timer.fetches > 1 ? DETAIL_RETRY : null, now);
    }

    /**
     * The formats reached the player: the stages of their fetch are the start's
     *
     * @param client the one they're from, e.g. TV
     */
    public static void onFormats(String videoId, @Nullable String client) {
        Timer timer = getTimer(videoId);

        if (timer == null || timer.fetchMs < 0) {
            return;
        }

        long now = sClock.now();
        Group formats = timer.findOpen(STAGE_FORMATS);

        if (formats == null) {
            formats = timer.open(STAGE_FORMATS, null, timer.fetchMs);
        }

        formats.endMs = now;
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
            formats.children.add(new VideoStart.Stage(STAGE_QUEUE, null, 1, timer.fetchMs, firstFetchMs));
        }

        for (FetchStages.Stage stage : fetched) {
            formats.children.add(new VideoStart.Stage(stage.name, stage.detail, Math.max(1, stage.depth), stage.startMs, stage.endMs));
            timer.isCached |= "cached".equals(stage.name);
        }

        if (lastFetchEndMs >= 0 && now > lastFetchEndMs) {
            formats.children.add(new VideoStart.Stage(STAGE_TO_PLAYER, null, 1, lastFetchEndMs, now));
        }

        timer.client = client;
        timer.open(STAGE_OPEN, null, now);
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
        Timer timer = getTimer();

        if (timer == null) {
            return;
        }

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
        Arrays.fill(timer.isChunkLoaded, false);
        Arrays.fill(timer.chunkLoadMs, -1);
        timer.hasVideoDecoder = false;
    }

    /**
     * A segment starts loading, in the clock's time
     *
     * @param isInit its initialization (the format's header), not its media
     */
    public static void onLoadStarted(boolean isInit, boolean isVideo, boolean isAudio, long startMs) {
        Timer timer = getTimer();
        int chunk = chunkOf(isInit, isVideo, isAudio);

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

        if (timer == null || chunk < 0 || timer.prepareMs < 0 || startMs < timer.prepareMs || timer.isChunkLoaded[chunk]) {
            return;
        }

        timer.isChunkLoaded[chunk] = true;
        addToBuffering(timer, CHUNK_STAGES[chunk], formatBytes(bytes), startMs, endMs);
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
        }
    }

    public static void onModeSwitchEnd() {
        Timer timer = getTimer();

        if (timer == null || timer.modeSwitchMs < 0) {
            return;
        }

        addToBuffering(timer, STAGE_MODE_SWITCH, null, timer.modeSwitchMs, sClock.now());
        timer.modeSwitchMs = -1;
    }

    public static void onFirstFrame() {
        Timer timer = getTimer();

        if (timer == null || timer.prepareMs < 0 || timer.firstFrameMs >= 0) {
            return;
        }

        timer.firstFrameMs = sClock.now();
        maybeFinish(timer, true);
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
     * The start going on never played: it's recorded as it is, with why (see VideoStart#OUTCOME_LEFT), unless it was left at once
     */
    public static void abandon(String outcome) {
        Timer timer = sTimer;

        if (timer == null) {
            return;
        }

        sTimer = null;
        sScheduler.cancel(sGiveUp);
        long now = sClock.now();

        if (now - timer.t0 < MIN_ABANDONED_MS) {
            Log.d(TAG, "Video start %s after %d ms: %s", outcome, now - timer.t0, timer.videoId);
            return;
        }

        record(build(timer, now, outcome));
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

        sTimer = null;
        sScheduler.cancel(sGiveUp);
        record(build(timer, Math.max(timer.playingMs, timer.firstFrameMs), VideoStart.OUTCOME_PLAYED));
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
        boolean isPlayed = VideoStart.OUTCOME_PLAYED.equals(outcome);
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

            // Leaving the player released its engine: no restart
            if (isOpen && STAGE_ENGINE_RESTART.equals(group.name) && VideoStart.OUTCOME_LEFT.equals(outcome)) {
                continue;
            }

            long groupEndMs = isOpen ? endMs : group.endMs;
            // Not played, it hadn't ended
            String detail = isOpen && !isPlayed ? (group.detail != null ? group.detail + ", " + DETAIL_GOING : DETAIL_GOING)
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
                // Too short to see, unless it's a moment
                if (child.getDurationMs() < 1 && child.getCategory() != VideoStart.Category.MARK) {
                    continue;
                }

                stages.add(new VideoStart.Stage(child.name, child.detail, child.depth, child.startMs - timer.t0, child.endMs - timer.t0));
            }
        }

        if (timer.firstFrameMs >= 0) {
            long frameMs = timer.firstFrameMs - timer.t0;
            stages.add(new VideoStart.Stage(STAGE_FIRST_FRAME, null, 0, frameMs, frameMs));
        }

        return new VideoStart(timer.ts, timer.videoId, timer.trigger, timer.isFirstOfRun, timer.sinceProcessStartMs, timer.client,
                timer.protocol, timer.isCached, outcome, endMs - timer.t0, stages);
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

        if (timer.modeSwitchMs >= 0) {
            result.add(new VideoStart.Stage(STAGE_MODE_SWITCH, DETAIL_GOING, 1, timer.modeSwitchMs, endMs));
        }

        if (timer.prepareMs >= 0) {
            // The player didn't load (e.g. its buffer was full): what a segment asked for late waited for
            result.addAll(sLoadHolds.take(timer.prepareMs, endMs));
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
                result.add(new VideoStart.Stage(stage.name, DETAIL_GOING, Math.max(1, stage.depth), stage.startMs, endMs));
            } else {
                result.add(new VideoStart.Stage(stage.name, stage.detail, Math.max(1, stage.depth), stage.startMs, stage.endMs));
            }
        }

        if (timer.fetchMs >= 0) {
            long queueEndMs = fetchStartMs >= 0 ? fetchStartMs : endMs;

            if (queueEndMs > timer.fetchMs) {
                result.add(new VideoStart.Stage(STAGE_QUEUE, fetchStartMs >= 0 ? null : DETAIL_GOING, 1, timer.fetchMs, queueEndMs));
            }
        }

        return result;
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
     * The start going on. One that's run too long is given up.
     */
    @Nullable
    private static Timer getTimer() {
        Timer timer = sTimer;

        if (timer != null && sClock.now() - timer.t0 > MAX_START_MS) {
            abandon(VideoStart.OUTCOME_GAVE_UP);
            return null;
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

    @VisibleForTesting
    static void resetForTesting() {
        sTimer = null;
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
        sRecorder = VideoStarts::save;
    }
}
