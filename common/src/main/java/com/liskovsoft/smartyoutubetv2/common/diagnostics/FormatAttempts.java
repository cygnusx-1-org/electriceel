package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.Format;

import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
     * Why an attempt began: the video's first formats
     */
    static final String REASON_FIRST = "first";
    /**
     * Other formats than the last attempt's (e.g. another quality, or a fallback)
     */
    static final String REASON_FORMATS_CHANGED = "formats_changed";
    /**
     * The same formats again, after the last attempt failed or a failure of the video was recorded (e.g. a stall)
     */
    static final String REASON_AFTER_FAILURE = "after_failure";
    /**
     * Other formats the user picked in the player's quality menu (see #onSelected): a change with nothing before it read as a fallback
     * of the app's, or a guess (0HCC641V #1, #2: AV1 to AVC 1080p50 at 0:45 and 0:20, no failure nor command before either)
     */
    public static final String REASON_USER_SELECTED = "user_selected";
    /**
     * Another codec the app picked for the playback speed: the one playing dropped frames at its rate on other videos (see
     * ErrorFixerController#applySpeedCodec)
     */
    public static final String REASON_SPEED_CODEC = "speed_codec";
    /**
     * A reason told before the tracks change (see #onSelected) is theirs this long: a pick of the formats playing changes nothing
     */
    private static final long SELECTED_REASON_MS = 10_000;
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
     * E.g. 1920x1080 vp09 30fps, 3840x2160 vp09 hdr 60fps (see PlayerFailure#describeVideo)
     */
    private static final Pattern VIDEO = Pattern.compile("^(\\d+)x(\\d+) (\\S+)( hdr)?(?: (\\d+)fps)?$");
    private static String sVideoId;
    private static String sClient;
    private static String sProtocol;
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
     * Since the video last failed, it tried another one, found out whether one worked, or played or stalled again: the failure's
     * formats are written again at the end (see #end)
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
    private static final long RECENT_LOADS_MS = 30_000;
    /**
     * Of the video segments loaded in the last RECENT_LOADS_MS, at least this much of the video: fewer say too little of its bitrate
     * there (see getRecentVideoKbps)
     */
    private static final long MIN_RECENT_VIDEO_MS = 4_000;
    /**
     * Of an attempt: a server more is a redirect or another reload
     */
    @VisibleForTesting
    static final int MAX_HOSTS = 4;
    /**
     * The servers the video's segments were asked of, in the order first asked: a server's number is its place + 1. Of the video, not
     * of an attempt: the reload's server is told apart from the one before's. Only their numbers are recorded, not their names.
     */
    private static final List<String> sHosts = new ArrayList<>();
    /**
     * The numbers of the servers asked since the player was prepared with its source (see #onPrepare), and that source's: an attempt
     * is made when its tracks are known, after the first requests (see #onTracks)
     */
    private static List<Integer> sSourceHosts = new ArrayList<>();
    private static long sSource;
    private static final Set<String> sOfferedVideo = new LinkedHashSet<>();
    private static final Set<String> sOfferedAudio = new LinkedHashSet<>();
    private static long sLastSeekMs = -1;
    /**
     * Another for each format tried (see #getAttemptId)
     */
    private static long sAttemptId;
    /**
     * Of the video, whatever the formats: its last stretch of playing, from when (-1 till it played) to when (-1 while it goes on), and
     * how long it hadn't played before it (-1 when it's the first)
     */
    private static long sStretchStart = -1;
    private static long sStretchEnd = -1;
    private static long sStoppedBeforeMs = -1;
    /**
     * Of the formats the stream played was made of, by their id (the itag): their average bitrate, from their size and length (see
     * #onFormats). The stream's, the Format's, is YouTube's "bitrate", nearer the peak: 104.7 Mbps for a 4K60 format the connection
     * kept up with at 52.6 Mbps (CX17FXZW #15).
     */
    private static final Map<String, Integer> sAverageKbps = new HashMap<>();
    /**
     * Why the next other formats are played (see #onSelected), and when it was told: null for none
     */
    @Nullable
    private static String sSelectedReason;
    private static long sSelectedAt;

    interface Clock {
        long now();
    }

    private static final class Attempt {
        /**
         * As the player's tracks told it (see #onTracks), null when they didn't: what tells another attempt from this one
         */
        @Nullable
        final String trackVideo;
        /**
         * The one reported: the tracks', else the video decoder's input (see #onVideoFormat)
         */
        @Nullable
        String video;
        final String audio;
        /**
         * Where its formats are from and how they're played, when it was tried (see #onSource): after a switch, another client's
         */
        @Nullable
        final String client;
        @Nullable
        final String protocol;
        /**
         * Of the video format, as the stream gives it: -1 when it doesn't
         */
        int videoBitrateKbps = -1;
        /**
         * Of the video format, from its size and length (see #onFormats): -1 when not known. What the throughput is to keep up with.
         */
        int videoAverageKbps = -1;
        String videoStatus = STATUS_UNKNOWN;
        String audioStatus = STATUS_UNKNOWN;
        String videoDecoder;
        String audioDecoder;
        int droppedFrames;
        long playedMs;
        /**
         * Reported silent (see PlaybackHealth#KIND_AUDIO_SILENT), how long it had played when the sound came back: -1 for one that
         * wasn't, or whose sound didn't come back
         */
        long soundBackMs = -1;
        int stalls;
        long stallMs;
        long loadedBytes;
        long loadMs;
        /**
         * Finished in the last RECENT_LOADS_MS, oldest first: {when, bytes, load time}
         */
        final ArrayDeque<long[]> recentLoads = new ArrayDeque<>();
        /**
         * The video's segments of them whose place in the video is known: {when, bytes, ms of the video}
         */
        final ArrayDeque<long[]> recentVideoLoads = new ArrayDeque<>();
        long videoLoadedAt = -1;
        long audioLoadedAt = -1;
        long videoLoadedToMs = -1;
        long audioLoadedToMs = -1;
        boolean hasPlayed;
        boolean isVideoRendered;
        /**
         * Counted for good: done with (see #count)
         */
        boolean isCounted;
        /**
         * Counted so far while it played on (see #countSoFar): how long it had played, and its statuses then. -1 till it's counted.
         */
        long countedMs = -1;
        @Nullable
        String countedVideoStatus;
        @Nullable
        String countedAudioStatus;
        long playingSince = -1;
        /**
         * How long it played at each speed (see PlaybackSpeed): the drops of a 50 fps format at 1.5x are 75 frames a second's
         */
        final SpeedTimes speeds = new SpeedTimes();
        long stallSince = -1;
        /**
         * The numbers of the servers its segments were asked of (see sHosts)
         */
        final List<Integer> hosts = new ArrayList<>();
        /**
         * Its requests are its source's (see sSource) from when its tracks were known
         */
        long source = -1;
        /**
         * Why it began (see REASON_FIRST)
         */
        String reason = REASON_FIRST;

        Attempt(@Nullable String video, @Nullable String audio) {
            this.trackVideo = video;
            this.video = video;
            this.audio = audio;
            client = sClient;
            protocol = sProtocol;
        }

        boolean isSameFormats(Attempt other) {
            return Helpers.equals(trackVideo, other.trackVideo) && Helpers.equals(audio, other.audio);
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
        attempt.videoBitrateKbps = getBitrateKbps(video);
        attempt.videoAverageKbps = getAverageKbps(video);
        Attempt last = getLast();

        if (last != null && last.isSameFormats(attempt) && !sIsLastFailed) {
            // Goes on, in this source too (e.g. the engine restarted)
            useSource(last);
            return;
        }

        useSource(attempt);

        if (last != null) {
            attempt.reason = last.isSameFormats(attempt) ? REASON_AFTER_FAILURE : takeSelectedReason(REASON_FORMATS_CHANGED);
        }

        // Switched while playing (e.g. another quality): this one plays from now
        boolean isPlaying = last != null && last.playingSince >= 0;
        stopTimers(last);
        count(last);

        if (last != null && REASON_AFTER_FAILURE.equals(attempt.reason) && last.countedMs >= 0) {
            // The same formats again after it failed (e.g. a restart, in a loop of them): the play it goes on with, not another. Each
            // restart was a play whose sound failed (FARAJV5R: 885 of 997 plays of vp9 720p24 + mp4a). Its time and how it ends count.
            attempt.countedMs = 0;
            attempt.countedVideoStatus = last.countedVideoStatus;
            attempt.countedAudioStatus = last.countedAudioStatus;
        }

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
     * Other formats are asked for, for this reason (see REASON_USER_SELECTED, REASON_SPEED_CODEC): the next attempt's, when its tracks
     * change within SELECTED_REASON_MS
     */
    public static void onSelected(String reason) {
        sSelectedReason = reason;
        sSelectedAt = sClock.now();
    }

    private static String takeSelectedReason(String otherwise) {
        String reason = sSelectedReason != null && sClock.now() - sSelectedAt <= SELECTED_REASON_MS ? sSelectedReason : otherwise;
        sSelectedReason = null;

        return reason;
    }

    /**
     * The player's speed changes (see PlaybackSpeed#onChanged): the time played till now was at the speed before
     */
    static void onSpeedChanged() {
        Attempt last = getLast();

        if (last != null && last.playingSince >= 0) {
            long now = sClock.now();
            addPlayed(last, now - last.playingSince);
            last.playingSince = now;
        }
    }

    /**
     * Played this long at the speed it plays at now (see #onSpeedChanged)
     */
    private static void addPlayed(Attempt attempt, long playedMs) {
        attempt.playedMs += playedMs;
        attempt.speeds.add(PlaybackSpeed.get(), playedMs);
    }

    /**
     * The player was prepared with a source of the video (e.g. again, by a reload): its requests are of the attempt its tracks make
     */
    public static void onPrepare() {
        sSource++;
        sSourceHosts = new ArrayList<>();
    }

    /**
     * A segment of the video is asked of the server (see Uri#getHost)
     */
    public static void onRequest(@Nullable String host) {
        if (sVideoId == null || host == null) {
            return;
        }

        int index = sHosts.indexOf(host);

        if (index < 0) {
            sHosts.add(host);
            index = sHosts.size() - 1;
        }

        addHost(sSourceHosts, index + 1);
        Attempt last = getLast();

        if (last != null && last.source == sSource) {
            addHost(last.hosts, index + 1);
        }
    }

    /**
     * The number of a server among the ones the video's segments were asked of (see sHosts), -1 for another (e.g. not asked yet).
     * On the player's thread, as the requests are.
     */
    public static int getHostNumber(@Nullable String host) {
        int index = host != null ? sHosts.indexOf(host) : -1;

        return index >= 0 ? index + 1 : -1;
    }

    private static void useSource(Attempt attempt) {
        attempt.source = sSource;

        for (int host : sSourceHosts) {
            addHost(attempt.hosts, host);
        }
    }

    private static void addHost(List<Integer> hosts, int host) {
        if (!hosts.contains(host) && hosts.size() < MAX_HOSTS) {
            hosts.add(host);
        }
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

    /**
     * The format the video decoder was given: the one tried last's, when the player's tracks didn't tell it (C4JB8CZX: a VISIONOS DASH
     * play recorded "video -" next to its VP9 decoder, and 3 plays counted as "- + mp4a")
     */
    public static void onVideoFormat(@Nullable Format format) {
        Attempt last = getLast();

        if (last == null || last.video != null || format == null) {
            return;
        }

        last.video = PlayerFailure.describeVideo(format);

        if (last.videoBitrateKbps <= 0 && format.bitrate > 0) {
            last.videoBitrateKbps = format.bitrate / 1_000;
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
            long now = sClock.now();
            last.recentLoads.addLast(new long[]{now, bytes, durationMs});

            while (now - last.recentLoads.peekFirst()[0] > RECENT_LOADS_MS) {
                last.recentLoads.removeFirst();
            }
        }
    }

    /**
     * @param mediaEndMs where the segment ends in the video, negative when unknown
     */
    public static void onLoad(long bytes, long durationMs, boolean isVideo, boolean isAudio, long mediaEndMs) {
        onLoad(bytes, durationMs, isVideo, isAudio, -1, mediaEndMs);
    }

    /**
     * @param mediaStartMs where the segment starts in the video, negative when unknown
     * @param mediaEndMs where it ends, negative when unknown
     */
    public static void onLoad(long bytes, long durationMs, boolean isVideo, boolean isAudio, long mediaStartMs, long mediaEndMs) {
        onLoad(bytes, durationMs);
        Attempt last = getLast();

        if (last == null) {
            return;
        }

        if (isVideo) {
            long now = sClock.now();
            last.videoLoadedAt = now;
            last.videoLoadedToMs = mediaEndMs;

            if (bytes > 0 && mediaStartMs >= 0 && mediaEndMs > mediaStartMs) {
                last.recentVideoLoads.addLast(new long[]{now, bytes, mediaEndMs - mediaStartMs});

                while (now - last.recentVideoLoads.peekFirst()[0] > RECENT_LOADS_MS) {
                    last.recentVideoLoads.removeFirst();
                }
            }
        } else if (isAudio) {
            last.audioLoadedAt = sClock.now();
            last.audioLoadedToMs = mediaEndMs;
        }
    }

    /**
     * What a long stall (see ErrorFixerController#onLongBuffering) came from: which track ran out, and whether the connection is too slow
     * for the format or its request hangs. Of the format tried. A health "stalls" failure and a source's engine error have them too (see
     * ErrorFixerController#onHealthFailure, #addSourceErrorDetails).
     */
    public static void addStallDetails(PlayerFailure failure, long positionMs) {
        Attempt last = getLast();

        if (last == null) {
            return;
        }

        long now = sClock.now();
        long throughputKbps = getRecentThroughputKbps(last, now);

        // Where the last segment loaded of each track ends, past the position: not what's buffered, a segment loaded before a seek
        // ends wherever it did (negative behind it). The player's buffer is its own detail (see ErrorFixerController#onLongBuffering).
        failure.detail("videoLoadedAheadMs", last.videoLoadedToMs >= 0 ? last.videoLoadedToMs - positionMs : null)
                .detail("audioLoadedAheadMs", last.audioLoadedToMs >= 0 ? last.audioLoadedToMs - positionMs : null)
                .detail("videoLoadAgoMs", last.videoLoadedAt >= 0 ? now - last.videoLoadedAt : null)
                .detail("audioLoadAgoMs", last.audioLoadedAt >= 0 ? now - last.audioLoadedAt : null)
                .detail("recentThroughputKbps", throughputKbps >= 0 ? throughputKbps : null)
                .detail("videoBitrateKbps", last.videoBitrateKbps > 0 ? last.videoBitrateKbps : null)
                .detail("videoAverageBitrateKbps", last.videoAverageKbps > 0 ? last.videoAverageKbps : null);
        long recentVideoKbps = getRecentVideoKbps(last, now);
        failure.detail("recentVideoBitrateKbps", recentVideoKbps > 0 ? recentVideoKbps : null);
    }

    /**
     * The segments of the format tried came faster than it plays, as far as the last 30 s tell: a stall then is the connection that
     * stops answering, not one too slow for the format (see ErrorFixerController#runLongBufferingAction). False when unknown.
     */
    public static boolean isFastEnough() {
        Attempt last = getLast();
        int neededKbps = getNeededKbps(last);

        if (neededKbps <= 0) {
            return false;
        }

        long throughputKbps = getRecentThroughputKbps(last, sClock.now());

        return throughputKbps >= neededKbps;
    }

    /**
     * The segments of the format tried came slower than it plays, as far as the last 30 s tell: a lower one may keep up (see
     * ErrorFixerController#onHealthFailure). False when unknown, e.g. no segment came in the last 30 s: a connection that stops
     * answering isn't one too slow for the format.
     */
    public static boolean isTooSlow() {
        Attempt last = getLast();
        int neededKbps = getNeededKbps(last);

        if (neededKbps <= 0) {
            return false;
        }

        long throughputKbps = getRecentThroughputKbps(last, sClock.now());

        return throughputKbps >= 0 && throughputKbps < neededKbps;
    }

    /**
     * What the throughput is to keep up with to play the attempt's video: its average bitrate, or the stream's when not known; the
     * bitrate of its segments loaded lately when that's higher, the part being played needing more than the video does on average.
     * H6ZWKZTQ #1: a 4K60 format of 45.3 Mbps on average (198.8 Mbps the stream's, a high-bitrate one) stalled 4 times in 2 minutes
     * at 55.5 Mbps, with no fix: faster than its average, not than what it played then. -1 when none is known.
     */
    private static int getNeededKbps(@Nullable Attempt attempt) {
        if (attempt == null) {
            return -1;
        }

        int neededKbps = attempt.videoAverageKbps > 0 ? attempt.videoAverageKbps : attempt.videoBitrateKbps;
        long recentKbps = getRecentVideoKbps(attempt, sClock.now());

        return recentKbps > neededKbps ? (int) recentKbps : neededKbps;
    }

    /**
     * Of the video segments that finished loading in the last RECENT_LOADS_MS, their bitrate: their bytes over the length of the video
     * they hold. -1 for less than MIN_RECENT_VIDEO_MS of it.
     */
    private static long getRecentVideoKbps(Attempt attempt, long now) {
        long bytes = 0;
        long mediaMs = 0;

        for (long[] load : attempt.recentVideoLoads) {
            if (now - load[0] <= RECENT_LOADS_MS) {
                bytes += load[1];
                mediaMs += load[2];
            }
        }

        return mediaMs >= MIN_RECENT_VIDEO_MS ? bytes * 8 / mediaMs : -1;
    }

    /**
     * Of the segments that finished loading in the last RECENT_LOADS_MS, while they loaded: -1 for none
     */
    private static long getRecentThroughputKbps(Attempt attempt, long now) {
        long bytes = 0;
        long loadMs = 0;

        for (long[] load : attempt.recentLoads) {
            if (now - load[0] <= RECENT_LOADS_MS) {
                bytes += load[1];
                loadMs += load[2];
            }
        }

        return loadMs > 0 ? bytes * 8 / loadMs : -1;
    }

    /**
     * Playing, or not anymore (paused, buffering, failed)
     */
    public static void onPlaying(boolean isPlaying) {
        long now = sClock.now();
        onStretch(isPlaying, now);
        Attempt last = getLast();

        if (last == null) {
            return;
        }

        if (isPlaying) {
            if (last.stallSince >= 0) {
                last.stallMs += now - last.stallSince;
                last.stallSince = -1;
            }

            if (last.playingSince < 0) {
                last.playingSince = now;
                // Its time played goes on
                sIsChangedSinceFailure = true;
            }

            last.hasPlayed = true;
        } else if (last.playingSince >= 0) {
            addPlayed(last, now - last.playingSince);
            last.playingSince = -1;
            sIsChangedSinceFailure = true;
        }
    }

    private static void onStretch(boolean isPlaying, long now) {
        if (sVideoId == null) {
            return;
        }

        boolean isGoingOn = sStretchStart >= 0 && sStretchEnd < 0;

        if (isPlaying && !isGoingOn) {
            sStoppedBeforeMs = sStretchEnd >= 0 ? now - sStretchEnd : -1;
            sStretchStart = now;
            sStretchEnd = -1;
        } else if (!isPlaying && isGoingOn) {
            sStretchEnd = now;
        }
    }

    /**
     * For a failure of the video: {"forMs": its last stretch of playing so far, "stoppedForMs": since it ended (null while it goes on),
     * "stoppedBeforeMs": how long it hadn't played before it (null when it's the first)}. A failure right after a long pause has a
     * short stretch and a long stop before it. Null when another video is playing, or it hasn't played.
     */
    @Nullable
    static JSONObject getPlaying(@Nullable String videoId) {
        if (videoId == null || !videoId.equals(sVideoId) || sStretchStart < 0) {
            return null;
        }

        long now = sClock.now();
        JSONObject result = new JSONObject();

        try {
            result.put("forMs", (sStretchEnd >= 0 ? sStretchEnd : now) - sStretchStart);
            result.put("stoppedForMs", sStretchEnd >= 0 ? now - sStretchEnd : JSONObject.NULL);
            result.put("stoppedBeforeMs", sStoppedBeforeMs >= 0 ? sStoppedBeforeMs : JSONObject.NULL);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }

        return result;
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
        sIsChangedSinceFailure = true;
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
     * Of the one tried last, up to now
     */
    public static long getStallMs() {
        Attempt last = getLast();

        return last != null ? last.stallMs + (last.stallSince >= 0 ? sClock.now() - last.stallSince : 0) : 0;
    }

    /**
     * Of the one tried last, up to now
     */
    public static long getPlayedMs() {
        Attempt last = getLast();

        return last != null ? last.playedMs + (last.playingSince >= 0 ? sClock.now() - last.playingSince : 0) : 0;
    }

    /**
     * The formats the player's stream is made of (e.g. the adaptive ones of SABR or DASH), null for none: their average bitrates, by
     * itag. A format's id in the stream is its itag (see SabrManifestParser, YouTubeMPDBuilder#createRepresentationId).
     */
    public static void onFormats(@Nullable List<MediaFormat> formats) {
        sAverageKbps.clear();

        if (formats == null) {
            return;
        }

        for (MediaFormat format : formats) {
            long bytes = format != null ? Helpers.parseLong(format.getClen()) : -1;
            int durationMs = format != null ? format.getApproxDurationMs() : 0;

            if (format != null && format.getITag() != null && bytes > 0 && durationMs > 0) {
                // Bytes per ms * 8 is kbit/s
                sAverageKbps.put(format.getITag(), (int) (bytes * 8 / durationMs));
            }
        }
    }

    /**
     * Where the formats of the video are from, and how they're played (see PlayerFailure#source)
     */
    public static void onSource(@Nullable String client, @Nullable String protocol) {
        sClient = client;
        sProtocol = protocol;
    }

    @Nullable
    public static String getClient() {
        return sClient;
    }

    @Nullable
    public static String getProtocol() {
        return sProtocol;
    }

    /**
     * The sound of the one tried last came back after it was reported silent (see PlaybackHealth#takeSoundBackMs): a silent intro
     * comes back in seconds, a silence that lasts till another format is tried doesn't
     *
     * @param playedMs how long it had played then
     */
    public static void onSoundBack(long playedMs) {
        Attempt last = getLast();

        if (last == null || last.audio == null || last.soundBackMs >= 0) {
            return;
        }

        last.soundBackMs = playedMs;
        sIsChangedSinceFailure = true;
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
        countSoFar(getLast());

        if (sVideoId != null && sHasFailed && isChangedSinceFailure()) {
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
        if (sVideoId != null && sHasFailed && isChangedSinceFailure()) {
            PlayerFailureLog.instance(context).recordFormats(sVideoId, sSessionStart, toJson(context), false);
        }

        reset();
    }

    /**
     * Changed since the failure (see #sIsChangedSinceFailure), or it plays or stalls still: its time has gone on (see #toJson)
     */
    private static boolean isChangedSinceFailure() {
        Attempt last = getLast();

        return sIsChangedSinceFailure || (last != null && (last.playingSince >= 0 || last.stallSince >= 0));
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
        sStretchStart = -1;
        sStretchEnd = -1;
        sStoppedBeforeMs = -1;
        sClient = null;
        sProtocol = null;
        sAttempts.clear();
        sHosts.clear();
        sSourceHosts = new ArrayList<>();
        sHasFailed = false;
        sIsChangedSinceFailure = false;
        sIsLastFailed = false;
        sSelectedReason = null;
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
     * A format tried is done with: how it played is counted (see PlayerFailureLog#countPlayback), what's left of it when it was
     * counted so far (see #countSoFar). Not one switched from right away.
     */
    private static void count(@Nullable Attempt attempt) {
        if (attempt == null || sContext == null || attempt.isCounted) {
            return;
        }

        if (countPlayed(attempt, attempt.playedMs)) {
            attempt.isCounted = true;
        }
    }

    /**
     * The one playing, in the background, where the app may be killed: how it played up to now is counted, the rest when it's done
     * with (see #count). Before, a video left to the background and killed there was never counted (AWHPECZK: ZJMM3fSIykY).
     */
    private static void countSoFar(@Nullable Attempt attempt) {
        if (attempt == null || sContext == null || attempt.isCounted) {
            return;
        }

        countPlayed(attempt, attempt.playedMs + (attempt.playingSince >= 0 ? sClock.now() - attempt.playingSince : 0));
    }

    /**
     * Not known yet (e.g. a retry that goes on with the play before, see onTracks): what was counted stands
     */
    @Nullable
    private static String known(String status, @Nullable String counted) {
        return STATUS_UNKNOWN.equals(status) && counted != null ? counted : status;
    }

    /**
     * What it played since it was last counted, once it played long enough or failed: a play the first time, then the time added and
     * its statuses' change
     *
     * @return whether it's counted (now or before)
     */
    private static boolean countPlayed(Attempt attempt, long playedMs) {
        String videoStatus = attempt.video != null ? known(attempt.videoStatus, attempt.countedVideoStatus) : null;
        String audioStatus = attempt.audio != null ? known(attempt.audioStatus, attempt.countedAudioStatus) : null;
        boolean isFirst = attempt.countedMs < 0;
        boolean hasFailed = STATUS_FAILED.equals(attempt.videoStatus) || STATUS_FAILED.equals(attempt.audioStatus);

        if (isFirst && playedMs < COUNTED_PLAYED_MS && !hasFailed) {
            return false;
        }

        long addedMs = playedMs - Math.max(attempt.countedMs, 0);
        int videoWorked = PlayerFailureLog.is(videoStatus, STATUS_WORKED) - PlayerFailureLog.is(attempt.countedVideoStatus, STATUS_WORKED);
        int videoFailed = PlayerFailureLog.is(videoStatus, STATUS_FAILED) - PlayerFailureLog.is(attempt.countedVideoStatus, STATUS_FAILED);
        int audioWorked = PlayerFailureLog.is(audioStatus, STATUS_WORKED) - PlayerFailureLog.is(attempt.countedAudioStatus, STATUS_WORKED);
        int audioFailed = PlayerFailureLog.is(audioStatus, STATUS_FAILED) - PlayerFailureLog.is(attempt.countedAudioStatus, STATUS_FAILED);
        attempt.countedMs = playedMs;
        attempt.countedVideoStatus = videoStatus;
        attempt.countedAudioStatus = audioStatus;

        if (!isFirst && addedMs == 0 && videoWorked == 0 && videoFailed == 0 && audioWorked == 0 && audioFailed == 0) {
            return true; // nothing new
        }

        try {
            PlayerFailureLog.instance(sContext).countPlayback(getStatsVideo(attempt.video), getStatsAudio(attempt.audio),
                    isFirst ? 1 : 0, addedMs, videoWorked, videoFailed, audioWorked, audioFailed);
        } catch (RuntimeException e) {
            // Not counted
        }

        return true;
    }

    /**
     * E.g. "vp9 1080p30" of "1920x1080 vp09 30fps": the codec, the shorter side and the frame rate. "vp9 hdr 2160p60" of
     * "3840x2160 vp09 hdr 60fps".
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
        String hdr = matcher.group(4) != null ? " hdr" : "";
        String fps = matcher.group(5) != null ? matcher.group(5) : "";

        return getCodecFamily(matcher.group(3)) + hdr + " " + side + "p" + fps;
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
            addPlayed(attempt, now - attempt.playingSince);
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

    private static int getBitrateKbps(@Nullable FormatItem item) {
        int bitrate = item != null && item.getTrack() != null && item.getTrack().format != null ? item.getTrack().format.bitrate : -1;

        return bitrate > 0 ? bitrate / 1000 : -1;
    }

    /**
     * Of the format the stream was made from (see #onFormats): -1 when not known, e.g. an HLS one
     */
    private static int getAverageKbps(@Nullable FormatItem item) {
        String id = item != null && item.getTrack() != null && item.getTrack().format != null ? item.getTrack().format.id : null;
        Integer averageKbps = id != null ? sAverageKbps.get(id) : null;

        return averageKbps != null ? averageKbps : -1;
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
        result.put("speeds", attempt.speeds.toJson(attempt.playingSince >= 0 ? now - attempt.playingSince : 0));

        if (attempt.soundBackMs >= 0) {
            result.put("soundBackMs", attempt.soundBackMs);
        }

        result.put("stalls", attempt.stalls);
        result.put("stallMs", stallMs);
        // Bits per millisecond are kilobits per second
        result.put("throughputKbps", attempt.loadMs > 0 ? attempt.loadedBytes * 8 / attempt.loadMs : JSONObject.NULL);
        result.put("videoBitrateKbps", attempt.videoBitrateKbps > 0 ? attempt.videoBitrateKbps : JSONObject.NULL);
        result.put("hosts", new JSONArray(attempt.hosts));
        result.put("client", nullable(attempt.client));
        result.put("protocol", nullable(attempt.protocol));
        result.put("reason", attempt.reason);

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
     * The longer side (1920 of 1920x800 or 1080x1920 is 1080p), the codec, HDR or not, and the frame rate (when both have one)
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
        boolean isSameHdr = (a.group(4) != null) == (b.group(4) != null);
        boolean isSameFps = a.group(5) == null || b.group(5) == null || Math.abs(Integer.parseInt(a.group(5)) - Integer.parseInt(b.group(5))) <= 1;

        return sideA == sideB && getCodecFamily(a.group(3)).equals(getCodecFamily(b.group(3))) && isSameHdr && isSameFps;
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
