package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The video and audio formats the player tried for the video playing, in order, and whether each worked, video and audio apart.
 * A failure of the video gets the ones tried so far (see PlayerFailure#record). When the video is left, the ones tried and found
 * out about after its last failure are recorded too, so that its failures end with the last one tried, whether it played or not
 * (see PlayerFailureLog#readAll). Used on the main thread.
 */
public final class FormatAttempts {
    /**
     * Played: the video rendered a frame, the audio's clock moved on while playing
     */
    public static final String STATUS_WORKED = "worked";
    /**
     * An error of the stream (its decoder, renderer or segments)
     */
    public static final String STATUS_FAILED = "failed";
    /**
     * Neither: e.g. the whole player failed before it played (a 403), or it's just started
     */
    public static final String STATUS_UNKNOWN = "unknown";
    /**
     * Of the requested one: no format like it was tried
     */
    public static final String STATUS_NOT_ATTEMPTED = "not_attempted";
    /**
     * The last one is replaced past it: it's the one that matters most
     */
    @VisibleForTesting
    static final int MAX_ATTEMPTS = 20;
    private static final String AUTO = "auto";
    /**
     * E.g. 1920x1080 vp09 30fps (see PlayerFailure#describeVideo)
     */
    private static final Pattern VIDEO = Pattern.compile("^(\\d+)x(\\d+) (\\S+)(?: (\\d+)fps)?$");
    private static String sVideoId;
    private static long sSessionStart;
    /**
     * The application's (no activity kept), for the counts of how the formats played (see #count)
     */
    @SuppressLint("StaticFieldLeak")
    private static Context sContext;
    /**
     * Played this long, or failed, to be counted
     */
    private static final long COUNTED_PLAYED_MS = 10_000;
    private static final List<Attempt> sAttempts = new ArrayList<>();
    /**
     * The video failed (see #snapshot)
     */
    private static boolean sHasFailed;
    /**
     * Since the video last failed, it tried another one or found out whether one worked
     */
    private static boolean sIsChangedSinceFailure;
    /**
     * The one tried last failed: the same one again is another attempt
     */
    private static boolean sIsLastFailed;

    /**
     * Of the stream: past it the list is cut
     */
    private static final int MAX_OFFERED = 30;
    /**
     * Buffering this soon after a seek is the seek's, not a stall
     */
    private static final long SEEK_BUFFERING_MS = 2_000;
    @VisibleForTesting
    static Clock sClock = System::currentTimeMillis;
    private static final Set<String> sOfferedVideo = new LinkedHashSet<>();
    private static final Set<String> sOfferedAudio = new LinkedHashSet<>();
    private static long sLastSeekMs = -1;
    /**
     * Another for each format tried (see #getAttemptId)
     */
    private static long sAttemptId;

    interface Clock {
        long now();
    }

    private static final class Attempt {
        final String video;
        final String audio;
        String videoStatus = STATUS_UNKNOWN;
        String audioStatus = STATUS_UNKNOWN;
        String videoDecoder;
        String audioDecoder;
        int droppedFrames;
        long playedMs;
        int stalls;
        long stallMs;
        long loadedBytes;
        long loadMs;
        boolean hasPlayed;
        boolean isVideoRendered;
        boolean isCounted;
        long playingSince = -1;
        long stallSince = -1;

        Attempt(@Nullable String video, @Nullable String audio) {
            this.video = video;
            this.audio = audio;
        }

        boolean isSameFormats(Attempt other) {
            return Helpers.equals(video, other.video) && Helpers.equals(audio, other.audio);
        }
    }

    private FormatAttempts() {
    }

    /**
     * The same video again (a reload after a failure) goes on with its attempts
     */
    public static void onNewVideo(Context context, @Nullable String videoId) {
        if (videoId == null || videoId.equals(sVideoId)) {
            return;
        }

        end(context);

        sContext = context.getApplicationContext();
        sVideoId = videoId;
        sSessionStart = PlayerFailureLog.instance(context).now();
    }

    /**
     * The player selected these for the video
     */
    public static void onTracks(@Nullable FormatItem video, @Nullable FormatItem audio) {
        if (sVideoId == null) {
            return;
        }

        Attempt attempt = new Attempt(PlayerFailure.describeVideo(video), PlayerFailure.describeAudio(audio));
        Attempt last = getLast();

        if (last != null && last.isSameFormats(attempt) && !sIsLastFailed) {
            return;
        }

        // Switched while playing (e.g. another quality): this one plays from now
        boolean isPlaying = last != null && last.playingSince >= 0;
        stopTimers(last);
        count(last);

        if (isPlaying) {
            attempt.playingSince = sClock.now();
        }

        if (sAttempts.size() >= MAX_ATTEMPTS) {
            sAttempts.set(sAttempts.size() - 1, attempt);
        } else {
            sAttempts.add(attempt);
        }

        sAttemptId++;

        sIsLastFailed = false;
        sIsChangedSinceFailure = true;
    }

    /**
     * The tracks the stream has, to see whether the requested one was there
     */
    public static void onOffered(@Nullable List<FormatItem> video, @Nullable List<FormatItem> audio) {
        if (sVideoId == null) {
            return;
        }

        addOffered(sOfferedVideo, video, true);
        addOffered(sOfferedAudio, audio, false);
    }

    /**
     * The decoder that took the stream of the one tried last (or failed to)
     */
    public static void onDecoder(boolean isVideo, @Nullable String name) {
        Attempt last = getLast();

        if (last == null || name == null) {
            return;
        }

        if (isVideo) {
            last.videoDecoder = name;
        } else {
            last.audioDecoder = name;
        }
    }

    public static void onDroppedFrames(int count) {
        Attempt last = getLast();

        if (last != null) {
            last.droppedFrames += count;
        }
    }

    /**
     * A segment loaded: the throughput of the one tried last
     */
    public static void onLoad(long bytes, long durationMs) {
        Attempt last = getLast();

        if (last != null && bytes > 0 && durationMs >= 0) {
            last.loadedBytes += bytes;
            last.loadMs += durationMs;
        }
    }

    /**
     * Playing, or not anymore (paused, buffering, failed)
     */
    public static void onPlaying(boolean isPlaying) {
        Attempt last = getLast();

        if (last == null) {
            return;
        }

        long now = sClock.now();

        if (isPlaying) {
            if (last.stallSince >= 0) {
                last.stallMs += now - last.stallSince;
                last.stallSince = -1;
            }

            if (last.playingSince < 0) {
                last.playingSince = now;
            }

            if (!last.hasPlayed) {
                last.hasPlayed = true;
                sIsChangedSinceFailure = true;
            }
        } else if (last.playingSince >= 0) {
            last.playedMs += now - last.playingSince;
            last.playingSince = -1;
        }
    }

    /**
     * Waiting for data: a stall once it has played, unless it's a seek's
     */
    public static void onBuffering() {
        onPlaying(false);
        Attempt last = getLast();
        long now = sClock.now();

        if (last == null || !last.hasPlayed || last.stallSince >= 0 || (sLastSeekMs >= 0 && now - sLastSeekMs < SEEK_BUFFERING_MS)) {
            return;
        }

        last.stalls++;
        last.stallSince = now;
    }

    public static void onSeek() {
        sLastSeekMs = sClock.now();
        Attempt last = getLast();

        // Its buffering, if any, isn't a stall
        if (last != null && last.stallSince >= 0) {
            last.stallMs += sLastSeekMs - last.stallSince;
            last.stallSince = -1;
        }
    }

    /**
     * The video rendered a frame of the one tried last
     */
    public static void onVideoRendered() {
        Attempt last = getLast();

        if (last != null) {
            last.isVideoRendered = true;
        }

        setStatus(true, STATUS_WORKED);
    }

    /**
     * Of the format tried now: another one, another ID (see PlaybackHealth)
     */
    public static long getAttemptId() {
        return sAttemptId;
    }

    /**
     * A frame of the format tried now was shown
     */
    public static boolean isVideoRendered() {
        Attempt last = getLast();

        return last != null && last.isVideoRendered;
    }

    public static int getStalls() {
        Attempt last = getLast();

        return last != null ? last.stalls : 0;
    }

    /**
     * The audio's clock moved on while it played (it's the player's clock): its sound is output
     */
    public static void onAudioPlayed() {
        setStatus(false, STATUS_WORKED);
    }

    /**
     * An error of the stream of the one tried last
     */
    public static void onStreamFailed(boolean isVideo) {
        setStatus(isVideo, STATUS_FAILED);
        sIsLastFailed = true;
    }

    /**
     * Written now what end() would write, and the video goes on: the app goes to the background, where it may be killed
     *
     * @param isNow before it returns: the app is about to die (a crash)
     */
    public static void flush(Context context, boolean isNow) {
        if (sVideoId != null && sHasFailed && sIsChangedSinceFailure) {
            PlayerFailureLog.instance(context).recordFormats(sVideoId, sSessionStart, toJson(context), isNow);
            sIsChangedSinceFailure = false;
        }
    }

    /**
     * Of the video playing, null when none is
     */
    @Nullable
    public static String getVideoId() {
        return sVideoId;
    }

    /**
     * The video is left (another one, or the player is closed)
     */
    public static void end(Context context) {
        if (sVideoId != null && sHasFailed && sIsChangedSinceFailure) {
            PlayerFailureLog.instance(context).recordFormats(sVideoId, sSessionStart, toJson(context), false);
        }

        reset();
    }

    /**
     * For a failure of the video: the formats asked for in the settings, the ones tried so far and the last one, with whether each
     * worked. Null when another video is playing.
     */
    @Nullable
    static JSONObject snapshot(Context context, @Nullable String videoId) {
        if (videoId == null || !videoId.equals(sVideoId)) {
            return null;
        }

        sHasFailed = true;
        sIsChangedSinceFailure = false;
        sIsLastFailed = true;

        return toJson(context);
    }

    @VisibleForTesting
    static void resetForTesting() {
        sContext = null; // nothing counted
        reset();
    }

    private static void reset() {
        stopTimers(getLast());
        count(getLast());
        sOfferedVideo.clear();
        sOfferedAudio.clear();
        sLastSeekMs = -1;
        sAttemptId++;
        sVideoId = null;
        sAttempts.clear();
        sHasFailed = false;
        sIsChangedSinceFailure = false;
        sIsLastFailed = false;
    }

    private static void setStatus(boolean isVideo, String status) {
        Attempt last = getLast();

        if (last == null || (isVideo ? last.video : last.audio) == null) {
            return;
        }

        String current = isVideo ? last.videoStatus : last.audioStatus;

        if (status.equals(current)) {
            return;
        }

        if (isVideo) {
            last.videoStatus = status;
        } else {
            last.audioStatus = status;
        }

        sIsChangedSinceFailure = true;
    }

    /**
     * A format tried is done with: how it played is counted (see PlayerFailureLog#countPlayback). Not one switched from right away.
     */
    private static void count(@Nullable Attempt attempt) {
        if (attempt == null || sContext == null || attempt.isCounted) {
            return;
        }

        boolean hasFailed = STATUS_FAILED.equals(attempt.videoStatus) || STATUS_FAILED.equals(attempt.audioStatus);

        if (attempt.playedMs < COUNTED_PLAYED_MS && !hasFailed) {
            return;
        }

        attempt.isCounted = true;

        try {
            PlayerFailureLog.instance(sContext).countPlayback(getStatsVideo(attempt.video), getStatsAudio(attempt.audio),
                    attempt.video != null ? attempt.videoStatus : null, attempt.audio != null ? attempt.audioStatus : null,
                    attempt.playedMs);
        } catch (RuntimeException e) {
            // Not counted
        }
    }

    /**
     * E.g. "vp9 1080p30" of "1920x1080 vp09 30fps": the codec, the shorter side and the frame rate
     */
    @Nullable
    @VisibleForTesting
    static String getStatsVideo(@Nullable String video) {
        if (video == null) {
            return null;
        }

        Matcher matcher = VIDEO.matcher(video);

        if (!matcher.matches()) {
            return video;
        }

        int side = Math.min(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        String fps = matcher.group(4) != null ? matcher.group(4) : "";

        return getCodecFamily(matcher.group(3)) + " " + side + "p" + fps;
    }

    /**
     * E.g. "opus" of "opus 160k"
     */
    @Nullable
    @VisibleForTesting
    static String getStatsAudio(@Nullable String audio) {
        return audio != null ? getCodecFamily(audio.split(" ")[0]) : null;
    }

    private static void stopTimers(@Nullable Attempt attempt) {
        if (attempt == null) {
            return;
        }

        long now = sClock.now();

        if (attempt.playingSince >= 0) {
            attempt.playedMs += now - attempt.playingSince;
            attempt.playingSince = -1;
        }

        if (attempt.stallSince >= 0) {
            attempt.stallMs += now - attempt.stallSince;
            attempt.stallSince = -1;
        }
    }

    private static void addOffered(Set<String> offered, @Nullable List<FormatItem> formats, boolean isVideo) {
        if (formats == null) {
            return;
        }

        for (FormatItem format : formats) {
            String description = isVideo ? PlayerFailure.describeVideo(format) : PlayerFailure.describeAudio(format);

            if (description != null && offered.size() < MAX_OFFERED) {
                offered.add(description);
            }
        }
    }

    @Nullable
    private static Attempt getLast() {
        return sAttempts.isEmpty() ? null : sAttempts.get(sAttempts.size() - 1);
    }

    /**
     * {"requested": {…}, "attempted": [{…}, …], "final": {…}}
     */
    private static JSONObject toJson(Context context) {
        PlayerData playerData = PlayerData.instance(context);
        String requestedVideo = PlayerFailure.describeVideo(playerData.getFormat(FormatItem.TYPE_VIDEO));
        String requestedAudio = PlayerFailure.describeAudio(playerData.getFormat(FormatItem.TYPE_AUDIO));
        JSONObject result = new JSONObject();

        try {
            // Not set (auto): whatever the player picks
            JSONObject requested = new JSONObject();
            requested.put("video", requestedVideo != null ? requestedVideo : AUTO);
            requested.put("videoStatus", getRequestedStatus(requestedVideo, true));
            requested.put("audio", requestedAudio != null ? requestedAudio : AUTO);
            requested.put("audioStatus", getRequestedStatus(requestedAudio, false));
            result.put("requested", requested);

            JSONArray attempted = new JSONArray();

            for (Attempt attempt : sAttempts) {
                attempted.put(toJson(attempt));
            }

            result.put("attempted", attempted);

            Attempt last = getLast();
            result.put("final", last != null ? toJson(last) : JSONObject.NULL);

            JSONObject offered = new JSONObject();
            offered.put("video", new JSONArray(sOfferedVideo));
            offered.put("audio", new JSONArray(sOfferedAudio));
            result.put("offered", offered);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }

        return result;
    }

    private static JSONObject toJson(Attempt attempt) throws JSONException {
        JSONObject result = new JSONObject();
        long now = sClock.now();
        // Still going: up to now
        long playedMs = attempt.playedMs + (attempt.playingSince >= 0 ? now - attempt.playingSince : 0);
        long stallMs = attempt.stallMs + (attempt.stallSince >= 0 ? now - attempt.stallSince : 0);

        result.put("video", nullable(attempt.video));
        result.put("videoStatus", attempt.video != null ? attempt.videoStatus : JSONObject.NULL);
        result.put("videoDecoder", nullable(attempt.videoDecoder));
        result.put("audio", nullable(attempt.audio));
        result.put("audioStatus", attempt.audio != null ? attempt.audioStatus : JSONObject.NULL);
        result.put("audioDecoder", nullable(attempt.audioDecoder));
        result.put("droppedFrames", attempt.droppedFrames);
        result.put("playedMs", playedMs);
        result.put("stalls", attempt.stalls);
        result.put("stallMs", stallMs);
        // Bits per millisecond are kilobits per second
        result.put("throughputKbps", attempt.loadMs > 0 ? attempt.loadedBytes * 8 / attempt.loadMs : JSONObject.NULL);

        return result;
    }

    /**
     * Of the first one tried like it (the same size, codec and frame rate; for audio, the codec). Not set: the first one tried.
     */
    private static String getRequestedStatus(@Nullable String requested, boolean isVideo) {
        for (Attempt attempt : sAttempts) {
            String tried = isVideo ? attempt.video : attempt.audio;

            if (tried != null && (requested == null || (isVideo ? isSameVideo(requested, tried) : isSameAudio(requested, tried)))) {
                return isVideo ? attempt.videoStatus : attempt.audioStatus;
            }
        }

        return STATUS_NOT_ATTEMPTED;
    }

    /**
     * The longer side (1920 of 1920x800 or 1080x1920 is 1080p), the codec and the frame rate (when both have one)
     */
    @VisibleForTesting
    static boolean isSameVideo(String requested, String tried) {
        Matcher a = VIDEO.matcher(requested);
        Matcher b = VIDEO.matcher(tried);

        if (!a.matches() || !b.matches()) {
            return requested.equals(tried);
        }

        int sideA = Math.max(Integer.parseInt(a.group(1)), Integer.parseInt(a.group(2)));
        int sideB = Math.max(Integer.parseInt(b.group(1)), Integer.parseInt(b.group(2)));
        boolean isSameFps = a.group(4) == null || b.group(4) == null || Math.abs(Integer.parseInt(a.group(4)) - Integer.parseInt(b.group(4))) <= 1;

        return sideA == sideB && getCodecFamily(a.group(3)).equals(getCodecFamily(b.group(3))) && isSameFps;
    }

    /**
     * The codec: "opus 160k" is opus
     */
    @VisibleForTesting
    static boolean isSameAudio(String requested, String tried) {
        return getCodecFamily(requested.split(" ")[0]).equals(getCodecFamily(tried.split(" ")[0]));
    }

    /**
     * vp09 and vp9 are vp9, avc1 and avc are avc…
     */
    private static String getCodecFamily(String codec) {
        String result = codec.toLowerCase(Locale.ROOT);

        switch (result) {
            case "vp09":
                return "vp9";
            case "avc1":
            case "avc3":
                return "avc";
            case "av01":
                return "av1";
            case "hev1":
            case "hvc1":
                return "hevc";
            default:
                return result;
        }
    }

    private static Object nullable(@Nullable Object value) {
        return value != null ? value : JSONObject.NULL;
    }
}
