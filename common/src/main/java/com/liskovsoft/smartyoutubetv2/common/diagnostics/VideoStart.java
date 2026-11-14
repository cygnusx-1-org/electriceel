package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * How long a video took to start, from the user's action to it playing, and where the time went (see VideoStarts): its stages, in
 * ms since the action, nested like the work they time
 */
public final class VideoStart {
    /**
     * A card, search, a menu (the start isn't one of the others)
     */
    public static final String TRIGGER_OPEN = "open";
    /**
     * A link from another app, to the app running
     */
    public static final String TRIGGER_INTENT = "intent";
    /**
     * A link from another app that started the app: the time is from the start of its process
     */
    public static final String TRIGGER_COLD_INTENT = "cold_intent";
    /**
     * Cast from a phone
     */
    public static final String TRIGGER_REMOTE = "remote";
    public static final String TRIGGER_NEXT = "next";
    public static final String TRIGGER_PREVIOUS = "previous";
    public static final String TRIGGER_SUGGESTION = "suggestion";
    /**
     * The video before it ended, or couldn't be played
     */
    public static final String TRIGGER_AUTOPLAY = "autoplay";
    /**
     * Picked from the playback queue in the player
     */
    public static final String TRIGGER_QUEUE = "queue";
    /**
     * The app loaded the video that played again (see VideoStarts#onReload): the error fixer, its streams expired. Timed from the reload.
     */
    public static final String TRIGGER_RELOAD = "reload";

    /**
     * It played: a frame was shown and the playback ran
     */
    public static final String OUTCOME_PLAYED = "played";
    /**
     * Going on, not playing yet: a look at it, not recorded (see VideoStarts#snapshot)
     */
    public static final String OUTCOME_WAITING = "waiting";
    /**
     * Not playing 15 s after the user's action (see VideoStarts#MAX_START_MS)
     */
    public static final String OUTCOME_GAVE_UP = "gave_up";
    /**
     * The user left the player before it played
     */
    public static final String OUTCOME_LEFT = "left";
    /**
     * Another video was started before it played (e.g. the user pressed next)
     */
    public static final String OUTCOME_REPLACED = "replaced";
    /**
     * Its formats said it can't be played
     */
    public static final String OUTCOME_UNPLAYABLE = "unplayable";
    /**
     * Ready to play and paused (by the user, or started so) 15 s after the user's action, not played: timed till it was ready. It was
     * recorded as given up, as if it were stuck.
     */
    public static final String OUTCOME_PAUSED = "paused";

    /**
     * Of a stage, by the work it times: the graph's colors
     */
    public enum Category {
        /**
         * The app's own work, on the main thread mostly
         */
        APP,
        /**
         * Nothing done but waiting: for a thread, a lock
         */
        WAIT,
        /**
         * The formats fetched: all that's in it
         */
        FORMATS,
        NETWORK,
        POTOKEN,
        /**
         * The player's JavaScript, got and run (the decipher)
         */
        PLAYER_JS,
        BUFFER,
        DECODER,
        /**
         * The display's refresh rate switched to the video's (see AutoFrameRateController)
         */
        MODE_SWITCH,
        /**
         * A moment, not a time: the first frame, formats that were cached, what the server made of the poToken
         */
        MARK
    }

    public static final class Stage {
        public final String name;
        @Nullable
        public final String detail;
        /**
         * 0 for a stage of the start, 1 for one of those's, …
         */
        public final int depth;
        public final long startMs;
        public final long endMs;

        public Stage(String name, @Nullable String detail, int depth, long startMs, long endMs) {
            this.name = name;
            this.detail = detail;
            this.depth = depth;
            this.startMs = startMs;
            this.endMs = endMs;
        }

        public long getDurationMs() {
            return endMs - startMs;
        }

        public Category getCategory() {
            return categoryOf(name);
        }

        JSONObject toJson() throws JSONException {
            JSONObject result = new JSONObject();
            result.put("name", name);

            if (detail != null) {
                result.put("detail", detail);
            }

            result.put("depth", depth);
            result.put("startMs", startMs);
            result.put("endMs", endMs);

            return result;
        }
    }

    /**
     * A playback warm-up of the run (see FormatInfoWrapper#warmUp in MediaServiceCore): what the run's first video found ready. Its
     * times are in ms since the user's action, before it negative.
     */
    public static final class WarmUp {
        /**
         * Of a warm-up or a step still going when the start was recorded
         */
        public static final long OPEN = Long.MIN_VALUE;
        public final long startMs;
        public final long endMs;
        public final List<WarmUpStep> steps;

        public WarmUp(long startMs, long endMs, List<WarmUpStep> steps) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        }

        public boolean isOpen() {
            return endMs == OPEN;
        }

        JSONObject toJson() throws JSONException {
            JSONObject result = new JSONObject();
            result.put("startMs", startMs);

            if (!isOpen()) {
                result.put("endMs", endMs);
            }

            JSONArray stepArray = new JSONArray();

            for (WarmUpStep step : steps) {
                stepArray.put(step.toJson());
            }

            result.put("steps", stepArray);

            return result;
        }
    }

    /**
     * E.g. the player data, "ready"
     */
    public static final class WarmUpStep {
        public final String name;
        public final long startMs;
        /**
         * {@link WarmUp#OPEN} for one still going
         */
        public final long endMs;
        /**
         * Null while it runs
         */
        @Nullable
        public final String result;

        public WarmUpStep(String name, long startMs, long endMs, @Nullable String result) {
            this.name = name;
            this.startMs = startMs;
            this.endMs = endMs;
            this.result = result;
        }

        public boolean isOpen() {
            return endMs == WarmUp.OPEN;
        }

        JSONObject toJson() throws JSONException {
            JSONObject result = new JSONObject();
            result.put("name", name);
            result.put("startMs", startMs);

            if (!isOpen()) {
                result.put("endMs", endMs);
            }

            if (this.result != null) {
                result.put("result", this.result);
            }

            return result;
        }
    }

    /**
     * Wall clock, when the user acted
     */
    public final long ts;
    public final String videoId;
    public final String trigger;
    /**
     * The first video started since the app's process started
     */
    public final boolean isFirstOfRun;
    /**
     * Of the user's action. -1 when not known.
     */
    public final long sinceProcessStartMs;
    @Nullable
    public final String client;
    @Nullable
    public final String protocol;
    /**
     * The network engine the player used (see Utils#getEffectiveNetworkEngine), null when not known
     */
    @Nullable
    public final String engine;
    /**
     * The formats were cached (fetched before, e.g. for the same video)
     */
    public final boolean isCached;
    /**
     * Played, or why not (see OUTCOME_PLAYED)
     */
    public final String outcome;
    /**
     * Till it played, or till the outcome (e.g. it was given up)
     */
    public final long totalMs;
    /**
     * In the order they started, an outer one before the ones it holds
     */
    public final List<Stage> stages;
    /**
     * Where it started, in ms into the video, at its first frame (e.g. a resume's position). -1 when not known (e.g. no frame).
     */
    public final long positionMs;
    /**
     * The video format it started with, e.g. "3840x2160 vp09 60fps", null when not known
     */
    @Nullable
    public final String video;
    /**
     * Its bitrate, -1 when not known
     */
    public final int videoBitrateKbps;
    /**
     * Of the run's first start: the warm-ups that began before it ended, the oldest first, empty for none. Null for the other starts.
     */
    @Nullable
    public final List<WarmUp> warmUps;
    /**
     * Of a start that never played: where the device was at then (its memory, see DeviceCapabilities#buildStartState). Null for one that
     * played, or when not known.
     */
    @Nullable
    public final JSONObject state;

    /**
     * One that played
     */
    public VideoStart(long ts, String videoId, String trigger, boolean isFirstOfRun, long sinceProcessStartMs, @Nullable String client,
                      @Nullable String protocol, boolean isCached, long totalMs, List<Stage> stages) {
        this(ts, videoId, trigger, isFirstOfRun, sinceProcessStartMs, client, protocol, isCached, OUTCOME_PLAYED, totalMs, stages);
    }

    public VideoStart(long ts, String videoId, String trigger, boolean isFirstOfRun, long sinceProcessStartMs, @Nullable String client,
                      @Nullable String protocol, boolean isCached, String outcome, long totalMs, List<Stage> stages) {
        this(ts, videoId, trigger, isFirstOfRun, sinceProcessStartMs, client, protocol, null, isCached, outcome, totalMs, stages);
    }

    public VideoStart(long ts, String videoId, String trigger, boolean isFirstOfRun, long sinceProcessStartMs, @Nullable String client,
                      @Nullable String protocol, @Nullable String engine, boolean isCached, String outcome, long totalMs,
                      List<Stage> stages) {
        this(ts, videoId, trigger, isFirstOfRun, sinceProcessStartMs, client, protocol, engine, isCached, outcome, totalMs, stages, -1,
                null, -1);
    }

    public VideoStart(long ts, String videoId, String trigger, boolean isFirstOfRun, long sinceProcessStartMs, @Nullable String client,
                      @Nullable String protocol, @Nullable String engine, boolean isCached, String outcome, long totalMs,
                      List<Stage> stages, long positionMs, @Nullable String video, int videoBitrateKbps) {
        this(ts, videoId, trigger, isFirstOfRun, sinceProcessStartMs, client, protocol, engine, isCached, outcome, totalMs, stages,
                positionMs, video, videoBitrateKbps, null, null);
    }

    public VideoStart(long ts, String videoId, String trigger, boolean isFirstOfRun, long sinceProcessStartMs, @Nullable String client,
                      @Nullable String protocol, @Nullable String engine, boolean isCached, String outcome, long totalMs,
                      List<Stage> stages, long positionMs, @Nullable String video, int videoBitrateKbps, @Nullable List<WarmUp> warmUps,
                      @Nullable JSONObject state) {
        this.ts = ts;
        this.videoId = videoId;
        this.trigger = trigger;
        this.isFirstOfRun = isFirstOfRun;
        this.sinceProcessStartMs = sinceProcessStartMs;
        this.client = client;
        this.protocol = protocol;
        this.engine = engine;
        this.isCached = isCached;
        this.outcome = outcome;
        this.totalMs = totalMs;
        this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
        this.positionMs = positionMs;
        this.video = video;
        this.videoBitrateKbps = videoBitrateKbps;
        this.warmUps = warmUps != null ? Collections.unmodifiableList(new ArrayList<>(warmUps)) : null;
        this.state = state;
    }

    public boolean isPlayed() {
        return OUTCOME_PLAYED.equals(outcome);
    }

    /**
     * It got ready to play: played, or paused then (see OUTCOME_PAUSED). Its stages ended by its end.
     */
    public boolean isReady() {
        return isPlayed() || OUTCOME_PAUSED.equals(outcome);
    }

    /**
     * E.g. "intent, first of run, TV sabr", "next, TV sabr, gave_up", "open, cached, TV sabr, from 0:41"
     */
    public String describe() {
        StringBuilder result = new StringBuilder(trigger);

        if (isFirstOfRun) {
            result.append(", first of run");
        }

        if (isCached) {
            result.append(", cached");
        }

        String source = (client != null ? client : "") + (protocol != null ? " " + protocol : "");

        if (!source.trim().isEmpty()) {
            result.append(", ").append(source.trim());
        }

        // Going on, it's said by the graph's title
        if (!isPlayed() && !OUTCOME_WAITING.equals(outcome)) {
            result.append(", ").append(outcome);
        }

        // Not from the start (e.g. a resume): its frames from the keyframe before were decoded first (see VideoStarts#STAGE_TO_POSITION)
        if (positionMs >= 1_000) {
            result.append(", from ").append(formatPosition(positionMs));
        }

        return result.toString();
    }

    /**
     * E.g. 0:41, 1:02:05
     */
    static String formatPosition(long positionMs) {
        long seconds = positionMs / 1_000;
        long hours = seconds / 3_600;

        return hours > 0 ? String.format(Locale.US, "%d:%02d:%02d", hours, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    public JSONObject toJson() {
        JSONObject result = new JSONObject();

        try {
            result.put("ts", ts);
            result.put("time", formatUtc(ts));
            result.put("videoId", videoId);
            result.put("trigger", trigger);
            result.put("firstOfRun", isFirstOfRun);

            if (sinceProcessStartMs >= 0) {
                result.put("sinceProcessStartMs", sinceProcessStartMs);
            }

            if (client != null) {
                result.put("client", client);
            }

            if (protocol != null) {
                result.put("protocol", protocol);
            }

            if (engine != null) {
                result.put("engine", engine);
            }

            // Of the app that recorded it
            if (BuildConfig.BUILD_ID != null) {
                result.put("build", BuildConfig.BUILD_ID);
            }

            result.put("cached", isCached);
            result.put("outcome", outcome);
            result.put("totalMs", totalMs);

            if (positionMs >= 0) {
                result.put("positionMs", positionMs);
            }

            if (video != null) {
                result.put("video", video);
            }

            if (videoBitrateKbps > 0) {
                result.put("videoBitrateKbps", videoBitrateKbps);
            }

            JSONArray stageArray = new JSONArray();

            for (Stage stage : stages) {
                stageArray.put(stage.toJson());
            }

            result.put("stages", stageArray);

            if (warmUps != null) {
                JSONArray warmUpArray = new JSONArray();

                for (WarmUp warmUp : warmUps) {
                    warmUpArray.put(warmUp.toJson());
                }

                result.put("warmUps", warmUpArray);
            }

            if (state != null) {
                result.put("state", state);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }

        return result;
    }

    static Category categoryOf(String name) {
        switch (name) {
            case VideoStarts.STAGE_APP_START:
            case VideoStarts.STAGE_PLAYER_SCREEN:
            case VideoStarts.STAGE_ENGINE_RESTART:
            case VideoStarts.STAGE_TO_PLAYER:
            case VideoStarts.STAGE_OPEN:
                return Category.APP;
            case VideoStarts.STAGE_QUEUE:
            case VideoStarts.STAGE_LOADING_HELD:
            case VideoStarts.STAGE_SERVER_BACKOFF:
            case "lock wait":
                return Category.WAIT;
            case VideoStarts.STAGE_FORMATS:
                return Category.FORMATS;
            case "poToken":
                return Category.POTOKEN;
            case "player JS":
            case "player JS load":
            case "player JS check":
            case "player download":
            case "V8 start":
            case "player preprocess":
            case "n-function load":
            case "decipher":
                return Category.PLAYER_JS;
            case VideoStarts.STAGE_BUFFERING:
            case VideoStarts.STAGE_FIRST_VIDEO_CHUNK:
            case VideoStarts.STAGE_FIRST_AUDIO_CHUNK:
            case VideoStarts.STAGE_VIDEO_INIT_CHUNK:
            case VideoStarts.STAGE_AUDIO_INIT_CHUNK:
                return Category.BUFFER;
            case VideoStarts.STAGE_VIDEO_DECODER:
            case VideoStarts.STAGE_TO_POSITION:
                return Category.DECODER;
            case VideoStarts.STAGE_MODE_SWITCH:
                return Category.MODE_SWITCH;
            case VideoStarts.STAGE_FIRST_FRAME:
            case VideoStarts.STAGE_PROTECTION:
            case VideoStarts.STAGE_SEEK:
            case "cached":
                return Category.MARK;
            default:
                // The fetch's: each client's attempt, its request, sign-in, the subtitles, the HLS fix…
                return Category.NETWORK;
        }
    }

    /**
     * E.g. 2026-10-23T07:31:53.013Z
     */
    private static String formatUtc(long ms) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));

        return format.format(new Date(ms));
    }
}
