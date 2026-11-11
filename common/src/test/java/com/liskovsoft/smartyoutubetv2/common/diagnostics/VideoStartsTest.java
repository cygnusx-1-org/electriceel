package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.liskovsoft.smartyoutubetv2.common.diagnostics.VideoStartFixture.T0;
import static com.liskovsoft.smartyoutubetv2.common.diagnostics.VideoStartFixture.VIDEO_ID;
import static com.liskovsoft.smartyoutubetv2.common.diagnostics.VideoStartFixture.openStage;
import static com.liskovsoft.smartyoutubetv2.common.diagnostics.VideoStartFixture.stage;
import static com.liskovsoft.smartyoutubetv2.common.diagnostics.VideoStartFixture.stages;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoStartsTest {
    private VideoStartFixture mFixture;

    @Before
    public void setUp() {
        mFixture = new VideoStartFixture();
    }

    @After
    public void tearDown() {
        VideoStarts.resetForTesting();
    }

    /**
     * The engine the player was prepared with: a start's network time is that engine's
     */
    @Test
    public void aStartHasTheEngineItWasPreparedWith() throws Exception {
        VideoStart start = mFixture.slowStart();

        assertEquals("okhttp", start.engine);
        assertEquals("okhttp", start.toJson().getString("engine"));
    }

    /**
     * A resume 41 s in: 1.7 s on a Shield went by with no row, the frames from the keyframe before decoded first. Where it started,
     * with what, and that time are the start's.
     */
    @Test
    public void aResumeTimesTheFramesDecodedToItsPosition() throws Exception {
        mFixture.at(0);
        VideoStarts.start(VIDEO_ID, VideoStart.TRIGGER_OPEN);
        VideoStarts.onFetch(VIDEO_ID);
        mFixture.at(160);
        VideoStarts.onFormats(VIDEO_ID, "TV");
        VideoStarts.onPrepare();
        mFixture.at(1_000);
        VideoStarts.onLoad(false, true, false, T0 + 330, T0 + 1_000, 23_300_000);
        mFixture.at(2_740);
        VideoStarts.onFirstFrame(new VideoStarts.FirstFrame(41_200, "3840x2160 vp09 60fps", 25_000, 297, T0 + 1_020));
        VideoStarts.onPlaying(true);

        VideoStart start = last();

        assertStage(find(start, VideoStarts.STAGE_TO_POSITION), VideoStarts.STAGE_TO_POSITION, "297 frames", 1, 1_020, 2_740);
        assertEquals(VideoStart.Category.DECODER, find(start, VideoStarts.STAGE_TO_POSITION).getCategory());
        assertEquals(41_200, start.positionMs);
        assertEquals("open, first of run, TV, from 0:41", start.describe());

        JSONObject json = start.toJson();

        assertEquals(41_200, json.getLong("positionMs"));
        assertEquals("3840x2160 vp09 60fps", json.getString("video"));
        assertEquals(25_000, json.getInt("videoBitrateKbps"));
    }

    /**
     * From its start: nothing skipped, no row; what it started with still said
     */
    @Test
    public void aStartFromTheBeginningSkipsNothing() throws Exception {
        mFixture.at(0);
        VideoStarts.start(VIDEO_ID, VideoStart.TRIGGER_OPEN);
        VideoStarts.onFetch(VIDEO_ID);
        mFixture.at(160);
        VideoStarts.onFormats(VIDEO_ID, "TV");
        VideoStarts.onPrepare();
        mFixture.at(900);
        VideoStarts.onFirstFrame(new VideoStarts.FirstFrame(0, "1920x1080 vp09 30fps", -1, 0, -1));
        VideoStarts.onPlaying(true);

        VideoStart start = last();
        JSONObject json = start.toJson();

        assertTrue(start.stages.stream().noneMatch(stage -> VideoStarts.STAGE_TO_POSITION.equals(stage.name)));
        assertEquals("open, first of run, TV", start.describe());
        assertEquals(0, json.getLong("positionMs"));
        assertFalse(json.has("videoBitrateKbps"));
    }

    /**
     * The renderer counts since its position was set: skips told from before the source was prepared are another source's
     */
    @Test
    public void skipsOfTheSourceBeforeAreNotTheStarts() {
        mFixture.at(0);
        VideoStarts.start(VIDEO_ID, VideoStart.TRIGGER_OPEN);
        VideoStarts.onFetch(VIDEO_ID);
        mFixture.at(160);
        VideoStarts.onFormats(VIDEO_ID, "TV");
        VideoStarts.onPrepare();
        mFixture.at(900);
        VideoStarts.onFirstFrame(new VideoStarts.FirstFrame(5_000, null, -1, 40, T0 + 100));
        VideoStarts.onPlaying(true);

        assertTrue(last().stages.stream().noneMatch(stage -> VideoStarts.STAGE_TO_POSITION.equals(stage.name)));
    }

    /**
     * No frame (e.g. the sound in the background): where it started isn't known
     */
    @Test
    public void aStartWithNoFrameHasNoPosition() throws Exception {
        mFixture.at(0);
        VideoStarts.start(VIDEO_ID, VideoStart.TRIGGER_OPEN);
        VideoStarts.onFetch(VIDEO_ID);
        mFixture.at(160);
        VideoStarts.onFormats(VIDEO_ID, "TV");
        VideoStarts.onPrepare();
        mFixture.at(900);
        VideoStarts.onPlaying(false);

        JSONObject json = last().toJson();

        assertFalse(json.has("positionMs"));
        assertFalse(json.has("video"));
    }

    /**
     * Unknown (e.g. Cronet couldn't be checked): left out
     */
    @Test
    public void aStartWithoutAnEngineHasNone() {
        VideoStarts.sEngine = () -> null;

        assertFalse(mFixture.slowStart().toJson().has("engine"));
    }

    /**
     * The audio's first segments came, the video's init never did: that request hangs. Told after the start was given up too.
     */
    @Test
    public void oneTrackLoadedAndTheOtherNotIsStuck() {
        VideoStarts.onPrepare();
        VideoStarts.onLoadStarted(true, true, false, T0);
        VideoStarts.onLoadStarted(true, false, true, T0);
        VideoStarts.onLoad(true, false, true, T0, T0 + 300, 2_000);
        assertFalse(VideoStarts.isOneTrackStuck());

        VideoStarts.onLoadStarted(false, false, true, T0 + 300);
        VideoStarts.onLoad(false, false, true, T0 + 300, T0 + 500, 145_000);
        assertTrue(VideoStarts.isOneTrackStuck());

        // Prepared again (e.g. reloaded): nothing loading
        mFixture.at(1_000);
        VideoStarts.onPrepare();
        assertFalse(VideoStarts.isOneTrackStuck());
    }

    /**
     * The video's first segment came, the audio's is still loading: stuck the other way round
     */
    @Test
    public void theAudioStuckIsStuckToo() {
        VideoStarts.onPrepare();
        VideoStarts.onLoadStarted(true, true, false, T0);
        VideoStarts.onLoad(true, true, false, T0, T0 + 300, 2_000);
        VideoStarts.onLoadStarted(false, true, false, T0 + 300);
        VideoStarts.onLoad(false, true, false, T0 + 300, T0 + 500, 900_000);
        VideoStarts.onLoadStarted(true, false, true, T0);

        assertTrue(VideoStarts.isOneTrackStuck());
    }

    /**
     * No audio track loaded at all (e.g. a muxed URL list's): nothing waits on it
     */
    @Test
    public void aTrackNeverLoadedIsntStuck() {
        VideoStarts.onPrepare();
        VideoStarts.onLoadStarted(true, true, false, T0);
        VideoStarts.onLoad(true, true, false, T0, T0 + 300, 2_000);
        VideoStarts.onLoadStarted(false, true, false, T0 + 300);
        VideoStarts.onLoad(false, true, false, T0 + 300, T0 + 500, 900_000);

        assertFalse(VideoStarts.isOneTrackStuck());
    }

    /**
     * A load of the source before, told after the player was prepared again: not this source's
     */
    @Test
    public void aLoadOfTheSourceBeforeIsntStuck() {
        mFixture.at(1_000);
        VideoStarts.onPrepare();
        VideoStarts.onLoadStarted(true, true, false, T0 + 500);
        VideoStarts.onLoadStarted(true, false, true, T0 + 1_000);
        VideoStarts.onLoad(true, false, true, T0 + 1_000, T0 + 1_300, 2_000);
        VideoStarts.onLoadStarted(false, false, true, T0 + 1_300);
        VideoStarts.onLoad(false, false, true, T0 + 1_300, T0 + 1_500, 145_000);

        assertFalse(VideoStarts.isOneTrackStuck());
    }

    @Test
    public void theProcessAgeIsSinceItStarted() {
        mFixture.at(5_000);
        assertEquals(T0 + 5_000 - 4_000, VideoStarts.getSinceProcessStartMs());

        VideoStarts.sProcessStart = () -> 0;
        assertEquals(-1, VideoStarts.getSinceProcessStartMs());
    }

    /**
     * Its first seconds go to the start that played, once: the start itself ends at the first frame
     */
    @Test
    public void theFramesOfTheFirstSecondsGoToTheStart() {
        List<JSONObject> frames = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        VideoStarts.sFramesRecorder = (ts, result) -> {
            times.add(ts);
            frames.add(result);
        };
        VideoStart start = mFixture.slowStart();
        PlaybackHealth.StartFrames measured = new PlaybackHealth.StartFrames(VIDEO_ID, 10_000, 40, 120, 1, 4_500);

        VideoStarts.onFrames(new PlaybackHealth.StartFrames("another", 10_000, 40, 120, 1, 4_500));
        assertTrue(frames.isEmpty());

        VideoStarts.onFrames(measured);
        VideoStarts.onFrames(measured);

        assertEquals(1, frames.size());
        assertEquals(Long.valueOf(start.ts), times.get(0));
        assertEquals(40, frames.get(0).optInt("rendered"));
        assertEquals(120, frames.get(0).optInt("dropped"));
        assertEquals(1, frames.get(0).optInt("droppedToKeyframes"));
        assertEquals(4_500, frames.get(0).optInt("longestFrozenMs"));
        assertEquals(10_000, frames.get(0).optInt("playedMs"));
        // The player had no audio clock to tell of
        assertFalse(frames.get(0).has("audioHoldReleases"));
    }

    /**
     * Whether the picture waited on an audio timestamp that didn't advance: told even when too little was dropped for a failure
     */
    @Test
    public void theFramesSayWhatTheAudioClockDid() {
        List<JSONObject> frames = new ArrayList<>();
        VideoStarts.sFramesRecorder = (ts, result) -> frames.add(result);
        mFixture.slowStart();

        VideoStarts.onFrames(new PlaybackHealth.StartFrames(VIDEO_ID, 10_000, 250, 9, 0, 0,
                new PlaybackHealth.StartAudioClock(2, 0, 1, 1, 480)));

        JSONObject result = frames.get(0);
        assertEquals(2, result.optInt("audioTimestampState"));
        assertEquals(0, result.optInt("audioFramesMismatches"));
        assertEquals(1, result.optInt("audioClockMismatches"));
        assertEquals(1, result.optInt("audioHoldReleases"));
        assertEquals(480, result.optInt("audioLongestHoldMs"));
    }

    /**
     * The next video played before the one before was measured (it's measured when its format ends): each goes to its own start
     */
    @Test
    public void framesOfAStartLeftEarlyGoToItEvenAfterTheNextOnePlayed() {
        List<Long> times = new ArrayList<>();
        List<JSONObject> frames = new ArrayList<>();
        VideoStarts.sFramesRecorder = (ts, result) -> {
            times.add(ts);
            frames.add(result);
        };
        VideoStart first = mFixture.slowStart();
        mFixture.at(30_000);
        VideoStarts.start("next", VideoStart.TRIGGER_NEXT);
        VideoStarts.onFetch("next");
        VideoStarts.onFormats("next", "TV");
        VideoStarts.onPrepare();
        VideoStarts.onPlaying(false);

        VideoStarts.onFrames(new PlaybackHealth.StartFrames(VIDEO_ID, 4_000, 90, 0, 0, 0));
        VideoStarts.onFrames(new PlaybackHealth.StartFrames("next", 10_000, 240, 0, 0, 0));

        assertEquals(2, frames.size());
        assertEquals(Long.valueOf(first.ts), times.get(0));
        assertEquals(4_000, frames.get(0).optInt("playedMs"));
        assertEquals(240, frames.get(1).optInt("rendered"));
    }

    /**
     * A new connection shows in the buffering: a slow segment without one is the server's answer
     */
    @Test
    public void connectionsAreInTheBuffering() {
        VideoStarts.sConnections = (fromMs, toMs) -> Collections.singletonList(
                new VideoStart.Stage(VideoStarts.STAGE_CONNECT, null, 1, fromMs, fromMs + 40));
        VideoStart start = mFixture.slowStart();
        boolean isFound = false;

        for (VideoStart.Stage stage : start.stages) {
            isFound |= VideoStarts.STAGE_CONNECT.equals(stage.name) && stage.depth == 1 && stage.getDurationMs() == 40;
        }

        assertTrue(isFound);
    }

    /**
     * The waits for the server's answers and the backoffs it asked for are in the buffering, with the connections
     */
    @Test
    public void responsesAndBackoffsAreInTheBuffering() {
        VideoStarts.sConnections = (fromMs, toMs) -> Collections.singletonList(
                new VideoStart.Stage(VideoStarts.STAGE_RESPONSE, null, 1, fromMs, fromMs + 3_500));
        VideoStarts.sBackoffs = (fromMs, toMs) -> Collections.singletonList(
                new VideoStart.Stage(VideoStarts.STAGE_SERVER_BACKOFF, "3000 ms asked", 1, fromMs + 10, fromMs + 3_010));
        VideoStart start = mFixture.slowStart();
        boolean isResponseFound = false;
        boolean isBackoffFound = false;

        for (VideoStart.Stage stage : start.stages) {
            isResponseFound |= VideoStarts.STAGE_RESPONSE.equals(stage.name) && stage.depth == 1 && stage.getDurationMs() == 3_500;
            isBackoffFound |= VideoStarts.STAGE_SERVER_BACKOFF.equals(stage.name) && "3000 ms asked".equals(stage.detail)
                    && stage.getCategory() == VideoStart.Category.WAIT;
        }

        assertTrue(isResponseFound);
        assertTrue(isBackoffFound);
    }

    /**
     * What the server made of the poToken is a moment of the buffering, of the start's video
     */
    @Test
    public void theProtectionStatusIsAMomentOfTheBuffering() {
        String[] videoIds = new String[1];
        VideoStarts.sProtection = (videoId, fromMs, toMs) -> {
            videoIds[0] = videoId;
            return Collections.singletonList(new VideoStart.Stage(VideoStarts.STAGE_PROTECTION, "PENDING", 1, fromMs + 400, fromMs + 400));
        };
        VideoStart start = mFixture.slowStart();
        boolean isFound = false;

        for (VideoStart.Stage stage : start.stages) {
            isFound |= VideoStarts.STAGE_PROTECTION.equals(stage.name) && "PENDING".equals(stage.detail) && stage.depth == 1
                    && stage.startMs == 1859 + 400 && stage.getDurationMs() == 0 && stage.getCategory() == VideoStart.Category.MARK;
        }

        assertTrue(isFound);
        assertEquals(VIDEO_ID, videoIds[0]);
    }

    /**
     * A poToken taken at once is kept: how it was got (e.g. none, kept by a run before) is what it tells. Other stages that short
     * aren't.
     */
    @Test
    public void aPoTokenTakenAtOnceIsKept() {
        mFixture.at(0);
        VideoStarts.start("video", VideoStart.TRIGGER_OPEN);
        VideoStarts.onFetch("video");
        mFixture.fetchStages = stages(
                stage("fetch", null, 0, 1, 500),
                stage("TV attempt", "ok", 1, 1, 400),
                stage("poToken", "TV, none: no WebView", 2, 2, 2),
                stage("player JS", null, 2, 2, 2),
                stage("request", null, 2, 2, 400));
        mFixture.at(500);
        VideoStarts.onFormats("video", "TV");
        VideoStarts.onPrepare();
        VideoStarts.onPlaying(false);

        List<String> names = new ArrayList<>();

        for (VideoStart.Stage stage : last().stages) {
            names.add(stage.name);

            if ("poToken".equals(stage.name)) {
                assertEquals("TV, none: no WebView", stage.detail);
                assertEquals(0, stage.getDurationMs());
            }
        }

        assertTrue(names.toString(), names.contains("poToken"));
        assertFalse(names.toString(), names.contains("player JS"));
    }

    /**
     * The formats were fetched while the player started: both rows from the click, the fetch's stages in it
     */
    @Test
    public void aColdOpenTimesThePlayerAndTheFetchSideBySide() {
        VideoStart start = mFixture.slowStart();

        assertEquals(3420, start.totalMs);
        assertEquals(VIDEO_ID, start.videoId);
        assertEquals(VideoStart.TRIGGER_OPEN, start.trigger);
        assertTrue(start.isFirstOfRun);
        assertEquals("TV", start.client);
        assertEquals("sabr", start.protocol);
        assertFalse(start.isCached);
        assertEquals(1_792_100_000_000L + T0, start.ts);
        assertEquals(T0 - 4_000, start.sinceProcessStartMs);

        List<VideoStart.Stage> stages = start.stages;
        int i = 0;
        assertStage(stages.get(i++), "player screen", null, 0, 0, 52);
        assertStage(stages.get(i++), "formats", null, 0, 4, 1844);
        assertStage(stages.get(i++), "queue", null, 1, 4, 9);
        assertStage(stages.get(i++), "lock wait", "warm-up", 1, 9, 219);
        assertStage(stages.get(i++), "sign-in", null, 1, 219, 281);
        assertStage(stages.get(i++), "TV attempt", "ok", 1, 281, 1211);
        assertStage(stages.get(i++), "poToken", "TV", 2, 283, 893);
        // The player JS took no time: not shown
        assertStage(stages.get(i++), "request", null, 2, 951, 1211);
        assertStage(stages.get(i++), "subtitles", "WEB_DUMMY", 1, 1213, 1513);
        assertStage(stages.get(i++), "decipher", null, 1, 1515, 1585);
        assertStage(stages.get(i++), "to the player", null, 1, 1590, 1844);
        assertStage(stages.get(i++), "open", null, 0, 1844, 1859);
        assertStage(stages.get(i++), "buffering", null, 0, 1859, 3420);
        assertStage(stages.get(i++), "first video chunk", "2.1 MB", 1, 1870, 2700);
        assertStage(stages.get(i++), "video decoder", "OMX.Nvidia.vp9.decode", 1, 2731, 2771);
        assertStage(stages.get(i++), "display mode switch", null, 1, 2790, 3410);
        assertStage(stages.get(i++), "first frame", null, 0, 3105, 3105);
        assertEquals(i, stages.size());

        // Shown, and recorded once
        assertEquals(1, mFixture.recorded.size());
        assertEquals(1, mFixture.shown.size());
        assertEquals(1, mFixture.starts);
    }

    @Test
    public void nextInThePlayerHasNoPlayerScreen() {
        mFixture.slowStart();

        mFixture.at(10_000);
        VideoStarts.start("next", VideoStart.TRIGGER_NEXT);
        VideoStarts.onFetch("next");
        mFixture.at(10_600);
        VideoStarts.onFormats("next", "TV");
        VideoStarts.onPrepare();
        VideoStarts.onProtocol("dash");
        mFixture.at(12_000);
        VideoStarts.onFirstFrame();
        mFixture.at(12_100);
        VideoStarts.onPlaying(true);

        VideoStart start = last();
        assertEquals("next", start.videoId);
        assertEquals(VideoStart.TRIGGER_NEXT, start.trigger);
        assertFalse(start.isFirstOfRun);
        assertEquals(2100, start.totalMs);
        assertEquals("formats", start.stages.get(0).name);
        assertEquals(0, start.stages.get(0).startMs);
        assertEquals(600, start.stages.get(0).endMs);
    }

    /**
     * Reloaded before it played (e.g. the server rejected the poToken): the wait is the start's, the fetch again its own row
     */
    @Test
    public void aReloadBeforeItPlayedIsPartOfTheStart() {
        VideoStarts.start(VIDEO_ID, VideoStart.TRIGGER_OPEN);
        VideoStarts.onFetch(VIDEO_ID);
        mFixture.at(500);
        VideoStarts.onFormats(VIDEO_ID, "TV");
        VideoStarts.onPrepare();
        VideoStarts.onProtocol("sabr");
        mFixture.at(1500);
        VideoStarts.onFetch(VIDEO_ID);
        mFixture.at(1800);
        VideoStarts.onFormats(VIDEO_ID, "TV");
        VideoStarts.onPrepare();
        VideoStarts.onProtocol("sabr");
        mFixture.at(2400);
        VideoStarts.onFirstFrame();
        VideoStarts.onPlaying(true);

        VideoStart start = last();
        assertEquals(2400, start.totalMs);
        assertStage(start.stages.get(0), "formats", null, 0, 0, 500);
        assertStage(start.stages.get(1), "open", null, 0, 500, 500);
        assertStage(start.stages.get(2), "buffering", "replaced", 0, 500, 1800);
        assertStage(start.stages.get(3), "formats", "retry", 0, 1500, 1800);
        assertStage(start.stages.get(4), "open", null, 0, 1800, 1800);
        assertStage(start.stages.get(5), "buffering", null, 0, 1800, 2400);
    }

    @Test
    public void everyStartIsRecorded() {
        play("fast", 300);
        assertEquals(1, mFixture.recorded.size());
        assertEquals(1, mFixture.shown.size());
        assertEquals("fast", last().videoId);
        assertEquals(300, last().totalMs);

        play("slow", 2500);
        assertEquals(2, mFixture.recorded.size());
        assertEquals("slow", last().videoId);
    }

    @Test
    public void aNewStartReplacesTheOneGoingOn() {
        VideoStarts.start("first", VideoStart.TRIGGER_OPEN);
        VideoStarts.onFetch("first");
        mFixture.at(1000);
        VideoStarts.start("second", VideoStart.TRIGGER_NEXT);
        // The first's formats, late: not the second's
        VideoStarts.onFormats("first", "TV");
        VideoStarts.onFetch("second");
        mFixture.at(3500);
        VideoStarts.onFormats("second", "TV");
        VideoStarts.onPrepare();
        VideoStarts.onFirstFrame();
        VideoStarts.onPlaying(true);

        assertEquals(1, mFixture.recorded.size());
        assertEquals("second", last().videoId);
        assertEquals(2500, last().totalMs);
        assertEquals(2, mFixture.starts);
    }

    /**
     * Left at once (e.g. next pressed twice): skipped by the user, not stuck
     */
    @Test
    public void aStartLeftAtOnceIsNotRecorded() {
        VideoStarts.start(VIDEO_ID, VideoStart.TRIGGER_OPEN);
        VideoStarts.onFetch(VIDEO_ID);
        mFixture.at(VideoStarts.MIN_ABANDONED_MS - 1);
        VideoStarts.abandon(VideoStart.OUTCOME_LEFT);

        assertTrue(mFixture.recorded.isEmpty());
        // Nothing to give up any more
        assertNull(mFixture.giveUp);
    }

    /**
     * Found unplayable at once (e.g. a live stream's answer with nothing to play from): the app's outcome, not a skip, so recorded
     */
    @Test
    public void anUnplayableStartIsRecordedHoweverSoon() {
        VideoStarts.start(VIDEO_ID, VideoStart.TRIGGER_OPEN);
        VideoStarts.onFetch(VIDEO_ID);
        mFixture.at(600);
        VideoStarts.onFormats(VIDEO_ID, "TV");
        VideoStarts.onProtocol(null);

        assertEquals(1, mFixture.recorded.size());
        assertEquals(VideoStart.OUTCOME_UNPLAYABLE, last().outcome);
        assertEquals(600, last().totalMs);
    }

    /**
     * The user gave up on the spinner and left: what it was stuck in is recorded, the stages still going up to then
     */
    @Test
    public void aStartLeftWhileItHangsIsRecordedAsItIs() {
        VideoStarts.start(VIDEO_ID, VideoStart.TRIGGER_OPEN);
        mFixture.at(4);
        VideoStarts.onFetch(VIDEO_ID);
        mFixture.fetchStagesSoFar = stages(
                openStage("fetch", 0, 9),
                stage("sign-in", null, 1, 9, 300),
                openStage("TV attempt", 1, 300),
                openStage("poToken", 2, 302));
        mFixture.at(4950);
        VideoStarts.onEngineReleased(); // leaving the player releases it first
        mFixture.at(5000);
        VideoStarts.abandon(VideoStart.OUTCOME_LEFT);

        VideoStart start = last();
        assertEquals(VideoStart.OUTCOME_LEFT, start.outcome);
        assertFalse(start.isPlayed());
        assertEquals(5000, start.totalMs);
        assertStage(start.stages.get(0), "formats", "still going", 0, 4, 5000);
        assertStage(start.stages.get(1), "queue", null, 1, 4, 9);
        assertStage(start.stages.get(2), "sign-in", null, 1, 9, 300);
        assertStage(start.stages.get(3), "TV attempt", "still going", 1, 300, 5000);
        assertStage(start.stages.get(4), "poToken", "still going", 2, 302, 5000);
        assertEquals(5, start.stages.size());
        // Shown with the stats, as it ended
        assertEquals(1, mFixture.shown.size());
    }

    /**
     * Not playing in time, even when nothing happens any more (a request hangs): given up and recorded
     */
    @Test
    public void aStartNotPlayingInTimeIsGivenUp() {
        VideoStarts.start(VIDEO_ID, VideoStart.TRIGGER_NEXT);
        VideoStarts.onFetch(VIDEO_ID);
        assertEquals(VideoStarts.MAX_START_MS, mFixture.giveUpDelayMs);

        mFixture.at(VideoStarts.MAX_START_MS);
        mFixture.fireGiveUp();

        assertEquals(VideoStart.OUTCOME_GAVE_UP, last().outcome);
        assertEquals(VideoStarts.MAX_START_MS, last().totalMs);
        // It plays later: that's not this start any more
        VideoStarts.onFormats(VIDEO_ID, "TV");
        VideoStarts.onPrepare();
        VideoStarts.onFirstFrame();
        VideoStarts.onPlaying(true);
        assertEquals(1, mFixture.recorded.size());
    }

    /**
     * Timed from the start of the process, a link that started the app has that much less of its time left
     */
    @Test
    public void aColdLinksMinuteStartsWithItsProcess() {
        VideoStarts.start(VIDEO_ID, VideoStart.TRIGGER_COLD_INTENT);

        assertEquals(VideoStarts.MAX_START_MS - (T0 - 4_000), mFixture.giveUpDelayMs);
    }

    /**
     * Its formats said it can't be played: the next video is loaded instead
     */
    @Test
    public void anUnplayableStartIsRecorded() {
        VideoStarts.start(VIDEO_ID, VideoStart.TRIGGER_OPEN);
        VideoStarts.onFetch(VIDEO_ID);
        mFixture.at(3000);
        VideoStarts.onFormats(VIDEO_ID, "TV");
        VideoStarts.onProtocol(null);

        assertEquals(VideoStart.OUTCOME_UNPLAYABLE, last().outcome);
        assertEquals(3000, last().totalMs);
        // Its later events aren't a start's
        VideoStarts.onPrepare();
        VideoStarts.onFirstFrame();
        VideoStarts.onPlaying(true);
        assertEquals(1, mFixture.recorded.size());
    }

    @Test
    public void aStartReplacedBeforeItPlayedIsRecorded() {
        VideoStarts.start("stuck", VideoStart.TRIGGER_OPEN);
        VideoStarts.onFetch("stuck");
        mFixture.at(3000);
        VideoStarts.start("next", VideoStart.TRIGGER_NEXT);

        assertEquals("stuck", last().videoId);
        assertEquals(VideoStart.OUTCOME_REPLACED, last().outcome);
        // The new one goes on
        assertEquals(VideoStarts.MAX_START_MS, mFixture.giveUpDelayMs);
        VideoStarts.onFetch("next");
        mFixture.at(4000);
        VideoStarts.onFormats("next", "TV");
        VideoStarts.onPrepare();
        VideoStarts.onFirstFrame();
        VideoStarts.onPlaying(true);
        assertEquals("next", last().videoId);
        assertTrue(last().isPlayed());
    }

    /**
     * Reloaded while its segments hang: the buffering replaced keeps what it waited for
     */
    @Test
    public void aReplacedBufferingKeepsWhatItWaitedFor() {
        start(VIDEO_ID);
        VideoStarts.onLoadStarted(true, true, false, T0 + 50);
        mFixture.at(10_000);
        VideoStarts.onFetch(VIDEO_ID);
        VideoStarts.onFormats(VIDEO_ID, "TV");
        VideoStarts.onPrepare();
        mFixture.at(12_000);
        VideoStarts.onFirstFrame();
        VideoStarts.onPlaying(true);

        List<VideoStart.Stage> stages = last().stages;
        int buffering = -1;

        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).name.equals("buffering")) {
                buffering = i;
                break;
            }
        }

        assertStage(stages.get(buffering), "buffering", "replaced", 0, 0, 10_000);
        assertStage(stages.get(buffering + 1), "video init chunk", "still loading", 1, 50, 10_000);
    }

    /**
     * A crash's restore isn't the user's: forgotten, not recorded
     */
    @Test
    public void aCancelledStartIsForgotten() {
        VideoStarts.start(VIDEO_ID, VideoStart.TRIGGER_OPEN);
        mFixture.at(5000);
        VideoStarts.cancel();

        assertTrue(mFixture.recorded.isEmpty());
        assertNull(mFixture.giveUp);
    }

    /**
     * Looked at while it goes on: what it waits for now, not recorded, and the start goes on as it was
     */
    @Test
    public void aSnapshotShowsWhatTheStartWaitsFor() {
        assertNull(VideoStarts.snapshot());
        start(VIDEO_ID);
        VideoStarts.onLoadStarted(false, true, false, T0 + 100);
        mFixture.at(4000);

        VideoStart snapshot = VideoStarts.snapshot();

        assertEquals(VideoStart.OUTCOME_WAITING, snapshot.outcome);
        assertEquals(4000, snapshot.totalMs);
        assertStage(find(snapshot, "buffering"), "buffering", "still going", 0, 0, 4000);
        assertStage(find(snapshot, "first video chunk"), "first video chunk", "still loading", 1, 100, 4000);
        assertTrue(mFixture.recorded.isEmpty());

        mFixture.at(4500);
        VideoStarts.onFirstFrame();
        VideoStarts.onPlaying(true);

        assertTrue(last().isPlayed());
        assertEquals(4500, last().totalMs);
        assertStage(find(last(), "buffering"), "buffering", null, 0, 0, 4500);
        assertNull(mFixture.giveUp);
    }

    /**
     * Shown but paused (e.g. for the display mode to switch): it starts when it plays. Playing with no frame yet: when the frame is.
     */
    @Test
    public void itStartsWhenAFrameIsShownAndItPlays() {
        start(VIDEO_ID);
        mFixture.at(1000);
        VideoStarts.onFirstFrame();
        mFixture.at(2500);
        VideoStarts.onPlaying(true);
        assertEquals(2500, last().totalMs);
        assertEquals(1000, find(last(), "first frame").startMs);

        start("other");
        mFixture.at(2100);
        VideoStarts.onPlaying(true);
        assertEquals(1, mFixture.recorded.size());
        mFixture.at(2600);
        VideoStarts.onFirstFrame();
        assertEquals(2, mFixture.recorded.size());
        assertEquals(2600, last().totalMs);
    }

    /**
     * The sound plays in the background: no frame is ever shown
     */
    @Test
    public void withNoSurfaceItStartsWhenItPlays() {
        start(VIDEO_ID);
        mFixture.at(2300);
        VideoStarts.onPlaying(false);

        assertEquals(2300, last().totalMs);
        assertNull(find(last(), "first frame"));
    }

    /**
     * After a seek the first frame is rendered again
     */
    @Test
    public void aSecondFirstFrameIsIgnored() {
        start(VIDEO_ID);
        mFixture.at(1000);
        VideoStarts.onFirstFrame();
        mFixture.at(1500);
        VideoStarts.onFirstFrame();
        mFixture.at(2200);
        VideoStarts.onPlaying(true);

        assertEquals(1000, find(last(), "first frame").startMs);
    }

    /**
     * SABR streams a response for long: the video's first one is still loading when a frame from it is shown
     */
    @Test
    public void aFirstChunkStillLoadingIsShownTillItStarted() {
        start(VIDEO_ID);
        VideoStarts.onLoadStarted(false, true, false, T0 - 100); // of the source before
        VideoStarts.onLoadStarted(false, true, false, T0 + 50);
        VideoStarts.onLoadStarted(false, false, true, T0 + 60);
        VideoStarts.onLoadStarted(false, true, false, T0 + 70);
        VideoStarts.onLoad(false, false, true, T0 + 60, T0 + 160, 165_000);
        mFixture.at(2200);
        VideoStarts.onFirstFrame();
        VideoStarts.onPlaying(true);

        assertStage(find(last(), "first video chunk"), "first video chunk", "still loading", 1, 50, 2200);
        assertStage(find(last(), "first audio chunk"), "first audio chunk", "165 KB", 1, 60, 160);
    }

    /**
     * The format's header of each track is a segment of its own, loaded first
     */
    @Test
    public void theInitSegmentsHaveRowsOfTheirOwn() {
        start(VIDEO_ID);
        VideoStarts.onLoadStarted(true, true, false, T0 + 10);
        VideoStarts.onLoad(true, true, false, T0 + 10, T0 + 40, 2_000);
        VideoStarts.onLoad(true, false, true, T0 + 12, T0 + 30, 1_000);
        VideoStarts.onLoadStarted(true, false, false, T0 + 15); // a subtitle's: not timed
        VideoStarts.onLoad(true, true, false, T0 + 40, T0 + 50, 2_000); // not the first
        mFixture.at(2100);
        VideoStarts.onFirstFrame();
        VideoStarts.onPlaying(true);

        assertStage(find(last(), "video init chunk"), "video init chunk", "2 KB", 1, 10, 40);
        assertStage(find(last(), "audio init chunk"), "audio init chunk", "1 KB", 1, 12, 30);
        assertEquals(VideoStart.Category.BUFFER, find(last(), "video init chunk").getCategory());
    }

    /**
     * The player didn't ask for the video's segments (e.g. its buffer was full of the video before): the start waited for it
     */
    @Test
    public void theTimeThePlayerHeldItsLoadingIsARow() {
        mFixture.loadHolds.add(new long[] {-3000, 500}); // from the video before, into this one
        mFixture.loadHolds.add(new long[] {900, 4000}); // past the start's end
        start(VIDEO_ID);
        mFixture.at(2500);
        VideoStarts.onFirstFrame();
        VideoStarts.onPlaying(true);

        List<VideoStart.Stage> held = new java.util.ArrayList<>();

        for (VideoStart.Stage stage : last().stages) {
            if (stage.name.equals("loading held")) {
                held.add(stage);
            }
        }

        assertEquals(2, held.size());
        assertStage(held.get(0), "loading held", "buffered 0.0 s, 37 MB", 1, 0, 500);
        assertStage(held.get(1), "loading held", "buffered 0.0 s, 37 MB", 1, 900, 2500);
        assertEquals(VideoStart.Category.WAIT, held.get(0).getCategory());
    }

    @Test
    public void aLinkThatStartedTheAppIsTimedFromItsProcess() {
        // The process started at 4000: 6 s before
        VideoStarts.start(VIDEO_ID, VideoStart.TRIGGER_COLD_INTENT);
        VideoStarts.onEngineWait(VIDEO_ID);
        VideoStarts.onFetch(VIDEO_ID);
        playOn(1000);

        VideoStart start = last();
        assertEquals(VideoStart.TRIGGER_COLD_INTENT, start.trigger);
        assertEquals(7000, start.totalMs);
        assertEquals(0, start.sinceProcessStartMs);
        assertStage(start.stages.get(0), "app start", null, 0, 0, 6000);
    }

    @Test
    public void aLinkProcessedLongAfterTheProcessStartedIsTimedFromItself() {
        mFixture.at(VideoStarts.MAX_COLD_START_MS);
        VideoStarts.start(VIDEO_ID, VideoStart.TRIGGER_COLD_INTENT);
        VideoStarts.onFetch(VIDEO_ID);
        playOn(VideoStarts.MAX_COLD_START_MS + 2000);

        assertEquals(VideoStart.TRIGGER_INTENT, last().trigger);
        assertEquals(2000, last().totalMs);
        assertEquals("formats", last().stages.get(0).name);
    }

    /**
     * Of a fetch before this one (e.g. the history's), they aren't this start's
     */
    @Test
    public void theStagesOfAnEarlierFetchAreLeftOut() {
        mFixture.at(5000);
        VideoStarts.start(VIDEO_ID, VideoStart.TRIGGER_OPEN);
        VideoStarts.onFetch(VIDEO_ID);
        mFixture.fetchStages = stages(
                stage("fetch", null, 0, 100, 200),
                stage("cached", null, 1, 150, 150),
                stage("fetch", null, 0, 5010, 5020),
                stage("cached", null, 1, 5015, 5015));
        mFixture.at(5100);
        VideoStarts.onFormats(VIDEO_ID, "TV");
        VideoStarts.onPrepare();
        mFixture.at(7500);
        VideoStarts.onFirstFrame();
        VideoStarts.onPlaying(true);

        VideoStart start = last();
        assertTrue(start.isCached);
        assertStage(start.stages.get(1), "queue", null, 1, 0, 10);
        assertStage(start.stages.get(2), "cached", null, 1, 15, 15);
        assertStage(start.stages.get(3), "to the player", null, 1, 20, 100);
        assertEquals("open", start.stages.get(4).name);
    }

    @Test
    public void aJsonOfItHasEveryField() throws Exception {
        org.json.JSONObject json = mFixture.slowStart().toJson();

        assertEquals(VIDEO_ID, json.getString("videoId"));
        assertEquals("open", json.getString("trigger"));
        assertTrue(json.getBoolean("firstOfRun"));
        assertEquals(3420, json.getLong("totalMs"));
        assertEquals("TV", json.getString("client"));
        assertEquals("sabr", json.getString("protocol"));
        assertFalse(json.getBoolean("cached"));
        assertEquals(17, json.getJSONArray("stages").length());
        assertEquals("warm-up", json.getJSONArray("stages").getJSONObject(3).getString("detail"));
        assertFalse(json.getJSONArray("stages").getJSONObject(0).has("detail"));
    }

    /**
     * E3RZYG50: the SABR server asked for a reload 26 min in, and nothing told whether the video came back. Reloaded after it played,
     * it's timed from the reload.
     */
    @Test
    public void aReloadOfAVideoThatPlayed_isTimedFromTheReload() {
        play(VIDEO_ID, 1_200);
        int recorded = mFixture.recorded.size();

        mFixture.at(60_000);
        VideoStarts.onReload(VIDEO_ID);
        VideoStarts.onFetch(VIDEO_ID);
        playOn(61_500);

        assertEquals(recorded + 1, mFixture.recorded.size());
        VideoStart start = last();
        assertEquals(VideoStart.TRIGGER_RELOAD, start.trigger);
        assertEquals(VideoStart.OUTCOME_PLAYED, start.outcome);
        assertEquals(1_500, start.totalMs);
    }

    /**
     * Opened again after it played, a reload before it plays is that start's: its formats fetched again (see #onFetch), not a start of
     * its own
     */
    @Test
    public void aReloadBeforeItPlayed_goesOnWithTheStart() {
        play(VIDEO_ID, 1_200);

        start(VIDEO_ID);
        mFixture.at(2_000);
        VideoStarts.onReload(VIDEO_ID);
        VideoStarts.onFetch(VIDEO_ID);
        playOn(3_000);

        VideoStart start = last();
        assertEquals(VideoStart.TRIGGER_OPEN, start.trigger);
        assertEquals(3_000, start.totalMs);
    }

    /**
     * A premiere opened early, reloaded every 30 s, never played: no start for each reload
     */
    @Test
    public void aVideoThatNeverPlayed_isntTimedOnReload() {
        start(VIDEO_ID);
        VideoStarts.abandon(VideoStart.OUTCOME_UNPLAYABLE);
        int recorded = mFixture.recorded.size();

        mFixture.at(30_000);
        VideoStarts.onReload(VIDEO_ID);

        assertNull(VideoStarts.snapshot());
        assertEquals(recorded, mFixture.recorded.size());
    }

    /**
     * An ended live stream that played is reloaded every 30 s: the first reload is timed, the ones after it aren't till it plays again
     */
    @Test
    public void aReloadThatDidntPlay_isntTimedAgain() {
        play(VIDEO_ID, 1_200);

        mFixture.at(60_000);
        VideoStarts.onReload(VIDEO_ID);
        VideoStarts.onFetch(VIDEO_ID);
        VideoStarts.abandon(VideoStart.OUTCOME_UNPLAYABLE);
        int recorded = mFixture.recorded.size();
        assertEquals(VideoStart.TRIGGER_RELOAD, last().trigger);

        mFixture.at(90_000);
        VideoStarts.onReload(VIDEO_ID);

        assertNull(VideoStarts.snapshot());
        assertEquals(recorded, mFixture.recorded.size());
    }

    /**
     * Only the video that played last: another one's reload is for a video the user left
     */
    @Test
    public void anotherVideosReload_isntTimed() {
        play(VIDEO_ID, 1_200);

        VideoStarts.onReload("otherVideoId");

        assertNull(VideoStarts.snapshot());
    }

    private void start(String videoId) {
        mFixture.at(0);
        VideoStarts.start(videoId, VideoStart.TRIGGER_OPEN);
        VideoStarts.onFetch(videoId);
        VideoStarts.onFormats(videoId, "TV");
        VideoStarts.onPrepare();
    }

    private void play(String videoId, long totalMs) {
        start(videoId);
        mFixture.at(totalMs);
        VideoStarts.onFirstFrame();
        VideoStarts.onPlaying(true);
    }

    /**
     * The formats reach the player, and it plays at the time
     */
    private void playOn(long ms) {
        String videoId = VIDEO_ID;
        mFixture.at(ms);
        VideoStarts.onFormats(videoId, "TV");
        VideoStarts.onPrepare();
        VideoStarts.onFirstFrame();
        VideoStarts.onPlaying(true);
    }

    private VideoStart last() {
        return mFixture.recorded.get(mFixture.recorded.size() - 1);
    }

    private static VideoStart.Stage find(VideoStart start, String name) {
        for (VideoStart.Stage stage : start.stages) {
            if (stage.name.equals(name)) {
                return stage;
            }
        }

        return null;
    }

    private static void assertStage(VideoStart.Stage stage, String name, String detail, int depth, long startMs, long endMs) {
        assertEquals(name, stage.name);
        assertEquals(name, detail, stage.detail);
        assertEquals(name, depth, stage.depth);
        assertEquals(name, startMs, stage.startMs);
        assertEquals(name, endMs, stage.endMs);
    }
}
