package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.content.Context;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FormatAttemptsTest {
    private static final FormatItem VP9_4K = ExoFormatItem.fromVideoSpec("3840,2160,60,vp9", false);
    private static final FormatItem AVC_FHD = ExoFormatItem.fromVideoSpec("1920,1080,30,avc", false);
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();
    private PlayerFailureLog mLog;
    private int mSavedEngine;

    @Before
    public void setUp() {
        mLog = PlayerFailureLog.useForTesting(mFolder.getRoot());
        FormatAttempts.resetForTesting();
        // Cronet can't load under Robolectric
        mSavedEngine = PlayerTweaksData.instance(getContext()).getPlayerDataSource();
        PlayerTweaksData.instance(getContext()).setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP);
    }

    @After
    public void tearDown() {
        FormatAttempts.sClock = System::currentTimeMillis;
        FormatAttempts.resetForTesting();
        PlayerFailureLog.useForTesting(null);
        PlayerTweaksData.instance(getContext()).setPlayerDataSource(mSavedEngine);
    }

    /**
     * The fix's fallback is tried after the failure is recorded: the failure still ends with it
     */
    /**
     * Which track ran out and how fast it loads lately, not over all the format played. Of the format tried.
     */
    @Test
    public void aLongStallSaysWhatItCameFrom() throws Exception {
        long[] now = {1_000_000};
        FormatAttempts.sClock = () -> now[0];
        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onLoad(100_000_000, 1_000, true, false, 60_000); // fast, long ago
        now[0] += 40_000;
        FormatAttempts.onLoad(1_000_000, 4_000, false, true, 70_000); // 2,000 kbps
        now[0] += 5_000;
        FormatAttempts.onLoad(1_000_000, 4_000, true, false, 65_000);
        now[0] += 7_000;

        PlayerFailure failure = PlayerFailure.create(getContext(), PlayerFailure.KIND_STALL);
        FormatAttempts.addStallDetails(failure, 64_000);
        failure.record();

        org.json.JSONObject details = mLog.readAll().get(0).getJSONObject("details");
        assertEquals(1_000, details.getLong("videoBufferedMs"));
        assertEquals(6_000, details.getLong("audioBufferedMs"));
        assertEquals(7_000, details.getLong("videoLoadAgoMs"));
        assertEquals(12_000, details.getLong("audioLoadAgoMs"));
        assertEquals(2_000, details.getLong("recentThroughputKbps"));
    }

    /**
     * Nothing known yet isn't a buffer of 0
     */
    @Test
    public void aLongStallBeforeAnySegmentSaysNothingOfTheBuffers() throws Exception {
        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_HQ_MP4A);

        PlayerFailure failure = PlayerFailure.create(getContext(), PlayerFailure.KIND_STALL);
        FormatAttempts.addStallDetails(failure, 64_000);
        failure.record();

        org.json.JSONObject details = mLog.readAll().get(0).getJSONObject("details");
        assertTrue(details.isNull("videoBufferedMs"));
        assertTrue(details.isNull("audioBufferedMs"));
        assertTrue(details.isNull("recentThroughputKbps"));
    }

    @Test
    public void aFailureEndsWithTheLastFormatTried() throws Exception {
        FormatAttempts.onNewVideo(getContext(), "first");
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_51_EC3);
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_51_EC3); // the same again: not another attempt
        failure("first").record();

        // The fallback, then another video
        FormatAttempts.onTracks(AVC_FHD, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onNewVideo(getContext(), "second");

        List<JSONObject> events = mLog.readAll();

        assertEquals(1, events.size());

        JSONObject formats = events.get(0).getJSONObject("formats");
        JSONArray attempted = formats.getJSONArray("attempted");

        assertEquals(2, attempted.length());
        assertEquals(PlayerFailure.describeVideo(VP9_4K), attempted.getJSONObject(0).getString("video"));
        assertEquals(PlayerFailure.describeVideo(AVC_FHD), attempted.getJSONObject(1).getString("video"));
        assertEquals(attempted.getJSONObject(1).toString(), formats.getJSONObject("final").toString());
        assertNotNull(formats.getJSONObject("requested"));

        // Where the device was at: its memory, network and display
        JSONObject state = events.get(0).getJSONObject("state");
        assertTrue(state.has("heapMaxMb"));
        assertTrue(state.has("network"));
        assertTrue(state.has("displayMode"));
    }

    /**
     * The same format reloaded after a stall: which servers each try's segments were asked of, by number. A try's first requests
     * come before its tracks are known: they're its own, not the try's before.
     */
    @Test
    public void eachAttemptSaysWhichServersItsSegmentsCameFrom() throws Exception {
        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onPrepare();
        FormatAttempts.onRequest("rr1---sn-a.googlevideo.com"); // before the tracks are known
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onRequest("rr1---sn-a.googlevideo.com");
        failure("video").record();

        // The reload's source, from another server
        FormatAttempts.onPrepare();
        FormatAttempts.onRequest("rr4---sn-b.googlevideo.com");
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onRequest("rr1---sn-a.googlevideo.com"); // the first one again: its number again
        FormatAttempts.end(getContext());

        JSONObject event = mLog.readAll().get(0);
        JSONArray attempted = event.getJSONObject("formats").getJSONArray("attempted");

        assertEquals(2, attempted.length());
        assertEquals("[1]", attempted.getJSONObject(0).getJSONArray("hosts").toString());
        assertEquals("[2,1]", attempted.getJSONObject(1).getJSONArray("hosts").toString());
        // Their numbers only
        assertFalse(event.toString().contains("googlevideo"));
    }

    /**
     * The engine restarted with the same format and nothing failed: the try goes on, with the new source's servers
     */
    @Test
    public void anAttemptThatGoesOnTakesTheNewSourcesServers() throws Exception {
        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onPrepare();
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onRequest("a.example");
        FormatAttempts.onPrepare();
        FormatAttempts.onRequest("b.example");
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onRequest("c.example");
        failure("video").record();

        JSONArray attempted = mLog.readAll().get(0).getJSONObject("formats").getJSONArray("attempted");

        assertEquals(1, attempted.length());
        assertEquals("[1,2,3]", attempted.getJSONObject(0).getJSONArray("hosts").toString());
    }

    @Test
    public void aFailureWithNothingTriedAfterItKeepsItsOwn() throws Exception {
        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_51_EC3);
        failure("video").record();
        FormatAttempts.end(getContext());

        List<JSONObject> events = mLog.readAll();

        assertEquals(1, events.size());
        assertEquals(1, events.get(0).getJSONObject("formats").getJSONArray("attempted").length());
        assertEquals(PlayerFailure.describeVideo(VP9_4K), events.get(0).getJSONObject("formats").getJSONObject("final").getString("video"));
    }

    @Test
    public void aFailureBeforeAnyTracksHasNoFinalUntilTheyCome() throws Exception {
        FormatAttempts.onNewVideo(getContext(), "video");
        failure("video").record();

        assertTrue(mLog.readAll().get(0).getJSONObject("formats").isNull("final"));

        FormatAttempts.onTracks(AVC_FHD, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.end(getContext());

        JSONObject formats = mLog.readAll().get(0).getJSONObject("formats");

        assertEquals(PlayerFailure.describeVideo(AVC_FHD), formats.getJSONObject("final").getString("video"));
    }

    @Test
    public void theLastOneIsKeptPastTheCap() throws Exception {
        FormatAttempts.onNewVideo(getContext(), "video");
        failure("video").record();

        for (int i = 0; i <= FormatAttempts.MAX_ATTEMPTS + 4; i++) {
            FormatAttempts.onTracks(ExoFormatItem.fromVideoSpec((1000 + i) + ",720,30,avc", false), FormatItem.AUDIO_HQ_MP4A);
        }

        FormatAttempts.end(getContext());

        JSONObject formats = mLog.readAll().get(0).getJSONObject("formats");

        assertEquals(FormatAttempts.MAX_ATTEMPTS, formats.getJSONArray("attempted").length());
        assertTrue(formats.getJSONObject("final").getString("video").startsWith((1000 + FormatAttempts.MAX_ATTEMPTS + 4) + "x720"));
    }

    @Test
    public void anotherVideosFailureGetsNone() throws Exception {
        FormatAttempts.onNewVideo(getContext(), "playing");
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_51_EC3);
        failure("other").record();

        assertTrue(mLog.readAll().get(0).isNull("formats"));
    }

    /**
     * The video plays, the audio's decoder fails: then the fallback audio works with the same video
     */
    @Test
    public void videoAndAudioWorkOrFailApart() throws Exception {
        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_51_EC3);
        FormatAttempts.onVideoRendered();
        FormatAttempts.onStreamFailed(false);
        failure("video").record();

        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onVideoRendered();
        FormatAttempts.onAudioPlayed();
        FormatAttempts.end(getContext());

        JSONObject formats = mLog.readAll().get(0).getJSONObject("formats");
        JSONObject first = formats.getJSONArray("attempted").getJSONObject(0);
        JSONObject last = formats.getJSONObject("final");

        assertEquals(FormatAttempts.STATUS_WORKED, first.getString("videoStatus"));
        assertEquals(FormatAttempts.STATUS_FAILED, first.getString("audioStatus"));
        assertEquals(PlayerFailure.describeAudio(FormatItem.AUDIO_HQ_MP4A), last.getString("audio"));
        assertEquals(FormatAttempts.STATUS_WORKED, last.getString("videoStatus"));
        assertEquals(FormatAttempts.STATUS_WORKED, last.getString("audioStatus"));
    }

    /**
     * The audio plays, the video's decoder fails, and its fallback fails too: the last one tried, not working
     */
    @Test
    public void theFinalOneIsRecordedEvenIfItFailed() throws Exception {
        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onAudioPlayed();
        FormatAttempts.onStreamFailed(true);
        failure("video").record();

        FormatAttempts.onTracks(AVC_FHD, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onStreamFailed(true);
        FormatAttempts.end(getContext());

        JSONObject formats = mLog.readAll().get(0).getJSONObject("formats");
        JSONObject first = formats.getJSONArray("attempted").getJSONObject(0);
        JSONObject last = formats.getJSONObject("final");

        assertEquals(FormatAttempts.STATUS_FAILED, first.getString("videoStatus"));
        assertEquals(FormatAttempts.STATUS_WORKED, first.getString("audioStatus"));
        assertEquals(PlayerFailure.describeVideo(AVC_FHD), last.getString("video"));
        assertEquals(FormatAttempts.STATUS_FAILED, last.getString("videoStatus"));
        assertEquals(FormatAttempts.STATUS_UNKNOWN, last.getString("audioStatus"));
    }

    /**
     * Reloaded with the same formats after a failure (e.g. a 403 of the whole stream): another attempt, and it works
     */
    @Test
    public void theSameFormatsAfterAFailureAreAnotherAttempt() throws Exception {
        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onTracks(AVC_FHD, FormatItem.AUDIO_HQ_MP4A);
        failure("video").record();

        FormatAttempts.onTracks(AVC_FHD, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onVideoRendered();
        FormatAttempts.onAudioPlayed();
        FormatAttempts.end(getContext());

        JSONObject formats = mLog.readAll().get(0).getJSONObject("formats");
        JSONArray attempted = formats.getJSONArray("attempted");

        assertEquals(2, attempted.length());
        assertEquals(FormatAttempts.STATUS_UNKNOWN, attempted.getJSONObject(0).getString("videoStatus"));
        assertEquals(FormatAttempts.STATUS_WORKED, attempted.getJSONObject(1).getString("videoStatus"));
        assertEquals(FormatAttempts.STATUS_WORKED, formats.getJSONObject("final").getString("audioStatus"));
    }

    /**
     * The client was switched after the failure: the format that played says it's the new one's
     */
    @Test
    public void eachAttemptSaysWhichClientItsFormatsCameFrom() throws Exception {
        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onSource("TV", PlayerFailure.PROTOCOL_SABR);
        FormatAttempts.onTracks(AVC_FHD, FormatItem.AUDIO_HQ_MP4A);
        failure("video").record();

        FormatAttempts.onSource("WEB", PlayerFailure.PROTOCOL_DASH);
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onVideoRendered();
        FormatAttempts.end(getContext());

        JSONObject formats = mLog.readAll().get(0).getJSONObject("formats");
        JSONArray attempted = formats.getJSONArray("attempted");

        assertEquals("TV", attempted.getJSONObject(0).getString("client"));
        assertEquals(PlayerFailure.PROTOCOL_SABR, attempted.getJSONObject(0).getString("protocol"));
        assertEquals("WEB", attempted.getJSONObject(1).getString("client"));
        assertEquals(PlayerFailure.PROTOCOL_DASH, formats.getJSONObject("final").getString("protocol"));
    }

    /**
     * Played again after a night's pause, it failed at once (K3F6GAY6 #15): a short stretch, a long stop before it
     */
    @Test
    public void aFailureSaysHowLongItHadntPlayedBeforeIt() throws Exception {
        long[] now = {1_000_000};
        FormatAttempts.sClock = () -> now[0];
        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onPlaying(true);
        now[0] += 900_000;
        FormatAttempts.onPlaying(false);
        now[0] += 34_200_000;
        FormatAttempts.onPlaying(true);
        // Playing again while it plays is the same stretch
        FormatAttempts.onPlaying(true);
        now[0] += 120;
        // The error ends it before the failure is recorded
        FormatAttempts.onPlaying(false);
        now[0] += 30;

        failure("video").record();

        JSONObject playing = mLog.readAll().get(0).getJSONObject("playing");
        assertEquals(120, playing.getLong("forMs"));
        assertEquals(30, playing.getLong("stoppedForMs"));
        assertEquals(34_200_000, playing.getLong("stoppedBeforeMs"));
    }

    /**
     * Its first stretch, going on. A reload of the video goes on with it; another video starts over.
     */
    @Test
    public void aFailureInItsFirstStretchHasNoStopBeforeIt() throws Exception {
        long[] now = {1_000_000};
        FormatAttempts.sClock = () -> now[0];
        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onPlaying(true);
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onNewVideo(getContext(), "video");
        now[0] += 5_000;

        failure("video").record();

        JSONObject playing = mLog.readAll().get(0).getJSONObject("playing");
        assertEquals(5_000, playing.getLong("forMs"));
        assertTrue(playing.isNull("stoppedForMs"));
        assertTrue(playing.isNull("stoppedBeforeMs"));

        FormatAttempts.onNewVideo(getContext(), "next");
        failure("next").record();
        assertFalse(mLog.readAll().get(1).has("playing"));
    }

    @Test
    public void anAttemptBeforeItsSourceIsKnownHasNoClient() throws Exception {
        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onTracks(AVC_FHD, FormatItem.AUDIO_HQ_MP4A);

        failure("video").record();

        JSONObject attempt = mLog.readAll().get(0).getJSONObject("formats").getJSONObject("final");
        assertTrue(attempt.isNull("client"));
        assertTrue(attempt.isNull("protocol"));
    }

    @Test
    public void theRequestedOnesStatusIsOfTheFirstOneLikeIt() throws Exception {
        PlayerData playerData = PlayerData.instance(getContext());
        FormatItem savedVideo = playerData.getFormat(FormatItem.TYPE_VIDEO);
        FormatItem savedAudio = playerData.getFormat(FormatItem.TYPE_AUDIO);

        try {
            playerData.setFormat(AVC_FHD);
            playerData.setFormat(FormatItem.AUDIO_51_EC3);

            FormatAttempts.onNewVideo(getContext(), "video");
            FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_HQ_MP4A);
            FormatAttempts.onStreamFailed(true);
            failure("video").record();
            FormatAttempts.onTracks(ExoFormatItem.fromVideoSpec("1920,800,30,avc", false), FormatItem.AUDIO_HQ_MP4A);
            FormatAttempts.onVideoRendered();
            FormatAttempts.end(getContext());

            JSONObject requested = mLog.readAll().get(0).getJSONObject("formats").getJSONObject("requested");

            assertEquals(PlayerFailure.describeVideo(AVC_FHD), requested.getString("video"));
            // A 1920x800 avc one is 1080p avc
            assertEquals(FormatAttempts.STATUS_WORKED, requested.getString("videoStatus"));
            // No ec-3 was tried
            assertEquals(FormatAttempts.STATUS_NOT_ATTEMPTED, requested.getString("audioStatus"));
        } finally {
            playerData.setFormat(savedVideo);
            playerData.setFormat(savedAudio);
        }
    }

    @Test
    public void eachAttemptHasItsDecodersFramesStallsAndThroughput() throws Exception {
        long[] now = {1_000_000};
        FormatAttempts.sClock = () -> now[0];

        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onOffered(java.util.Arrays.asList(VP9_4K, AVC_FHD), java.util.Collections.singletonList(FormatItem.AUDIO_HQ_MP4A));
        FormatAttempts.onDecoder(true, "OMX.vendor.vp9.decoder");
        FormatAttempts.onDecoder(false, "c2.android.aac.decoder");
        FormatAttempts.onLoad(1_000_000, 1_000); // 8,000 kbps

        // Buffering before it ever played isn't a stall
        FormatAttempts.onBuffering();
        now[0] += 3_000;
        FormatAttempts.onPlaying(true);
        now[0] += 10_000;
        FormatAttempts.onDroppedFrames(12);

        // A seek's buffering isn't a stall
        FormatAttempts.onSeek();
        FormatAttempts.onBuffering();
        now[0] += 1_000;
        FormatAttempts.onPlaying(true);
        now[0] += 5_000;

        // A stall: 4 s
        now[0] += 3_000;
        FormatAttempts.onBuffering();
        now[0] += 4_000;
        FormatAttempts.onPlaying(true);
        now[0] += 2_000;

        failure("video").record();

        JSONObject formats = mLog.readAll().get(0).getJSONObject("formats");
        JSONObject attempt = formats.getJSONObject("final");

        assertEquals("OMX.vendor.vp9.decoder", attempt.getString("videoDecoder"));
        assertEquals("c2.android.aac.decoder", attempt.getString("audioDecoder"));
        assertEquals(12, attempt.getInt("droppedFrames"));
        assertEquals(1, attempt.getInt("stalls"));
        assertEquals(4_000, attempt.getLong("stallMs"));
        // 10 + 5 + 3 + 2 s playing
        assertEquals(20_000, attempt.getLong("playedMs"));
        assertEquals(8_000, attempt.getLong("throughputKbps"));
        assertEquals(2, formats.getJSONObject("offered").getJSONArray("video").length());
        assertEquals(1, formats.getJSONObject("offered").getJSONArray("audio").length());
    }

    @Test
    public void aFormatThatPlayedIsCountedWhenItsDoneWith() throws Exception {
        long[] now = {1_000_000};
        FormatAttempts.sClock = () -> now[0];

        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onTracks(AVC_FHD, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onVideoRendered();
        FormatAttempts.onPlaying(true);
        now[0] += 30_000;
        // Switched to right away: not counted
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onTracks(AVC_FHD, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.end(getContext());

        org.json.JSONArray formats = mLog.readStats().getJSONArray("formats");

        assertEquals(1, formats.length());
        assertEquals("avc 1080p30", formats.getJSONObject(0).getString("video"));
        assertEquals("mp4a", formats.getJSONObject(0).getString("audio"));
        assertEquals(1, formats.getJSONObject(0).getInt("videoWorked"));
    }

    @Test
    public void statsNamesAreShort() {
        assertEquals("vp9 1080p60", FormatAttempts.getStatsVideo("1920x1080 vp09 60fps"));
        assertEquals("avc 1080p30", FormatAttempts.getStatsVideo("1080x1920 avc1 30fps"));
        assertEquals("opus", FormatAttempts.getStatsAudio("opus 160k"));
    }

    @Test
    public void formatsAreLikeByTheirLongerSideCodecAndFrameRate() {
        assertTrue(FormatAttempts.isSameVideo("1920x1080 avc 30fps", "1920x800 avc1 30fps"));
        assertTrue(FormatAttempts.isSameVideo("1920x1080 vp9 60fps", "1080x1920 vp09 60fps"));
        assertTrue(FormatAttempts.isSameVideo("3840x2160 av01 60fps", "3840x2160 av01 59fps"));
        assertFalse(FormatAttempts.isSameVideo("1920x1080 avc 30fps", "1920x1080 vp09 30fps"));
        assertFalse(FormatAttempts.isSameVideo("1920x1080 avc 60fps", "1920x1080 avc1 30fps"));
        assertFalse(FormatAttempts.isSameVideo("3840x2160 vp9 60fps", "1920x1080 vp09 60fps"));
        assertTrue(FormatAttempts.isSameAudio("mp4a", "mp4a 128k"));
        assertFalse(FormatAttempts.isSameAudio("ec-3", "opus 160k"));
    }

    /**
     * The video's bitrate next to the throughput it loaded at: whether the network kept up with the format. Null when the stream
     * doesn't give it.
     */
    @Test
    public void anAttemptHasItsVideosBitrate() throws Exception {
        MediaTrack track = MediaTrack.forRendererIndex(TrackSelectorManager.RENDERER_INDEX_VIDEO);
        track.format = Format.createVideoSampleFormat("313", MimeTypes.VIDEO_VP9, "vp9", 18_000_000, Format.NO_VALUE, 3840, 2160, 30f,
                null, null);
        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onTracks(ExoFormatItem.from(track), FormatItem.AUDIO_HQ_MP4A);

        failure("video").record();

        JSONObject attempt = mLog.readAll().get(0).getJSONObject("formats").getJSONObject("final");
        assertEquals(18_000, attempt.getInt("videoBitrateKbps"));
    }

    @Test
    public void anAttemptWithoutTheVideosBitrateHasNone() throws Exception {
        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onTracks(null, FormatItem.AUDIO_HQ_MP4A);

        failure("video").record();

        JSONObject attempt = mLog.readAll().get(0).getJSONObject("formats").getJSONObject("final");
        assertTrue(attempt.isNull("videoBitrateKbps"));
    }

    private static PlayerFailure failure(String videoId) {
        Video video = new Video();
        video.videoId = videoId;

        return PlayerFailure.create(getContext(), PlayerFailure.KIND_ENGINE_ERROR).video(video)
                .error(new IllegalStateException("Decoder init failed"));
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }
}
