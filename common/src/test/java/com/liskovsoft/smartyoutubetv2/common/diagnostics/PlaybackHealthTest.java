package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PlaybackHealthTest {
    private PlaybackHealth mHealth;
    private PlaybackHealth.Sample mSample;
    private final List<String> mFound = new ArrayList<>();

    @Before
    public void setUp() {
        mHealth = new PlaybackHealth();
        mSample = new PlaybackHealth.Sample();
        mSample.attempt = 1;
        mSample.isPlaying = true;
        mSample.hasVideo = true;
        mSample.hasAudio = true;
        mSample.renderedFrames = 0;
        mSample.droppedFrames = 0;
        mFound.clear();
    }

    @Test
    public void noFrameAfterPlayingAWhileIsNoVideo() {
        for (int i = 0; i < 3; i++) {
            tick(2_000, 2_000, 0, 0);
        }

        assertTrue(mFound.isEmpty());

        tick(2_000, 2_000, 0, 0);

        assertEquals(1, mFound.size());
        assertEquals(PlaybackHealth.KIND_NO_VIDEO, mFound.get(0));

        // Once
        tick(2_000, 2_000, 0, 0);
        assertEquals(1, mFound.size());
    }

    @Test
    public void pausedTimeDoesntCount() {
        tick(2_000, 2_000, 0, 0);
        mSample.isPlaying = false;

        for (int i = 0; i < 10; i++) {
            tick(2_000, 0, 0, 0);
        }

        assertTrue(mFound.isEmpty());
    }

    @Test
    public void framesThatStopWhileThePositionMovesAreFrozen() {
        mSample.isVideoRendered = true;
        playPastTheStart();
        // Stuck at 360 frames
        tick(2_000, 2_000, 360, 0);
        tick(2_000, 2_000, 360, 0);
        assertTrue(mFound.isEmpty());
        tick(2_000, 2_000, 360, 0);

        assertEquals(PlaybackHealth.KIND_VIDEO_FROZEN, mFound.get(0));
    }

    @Test
    public void manyDroppedFramesAreReported() {
        mSample.isVideoRendered = true;
        tick(2_000, 2_000, 60, 0);
        tick(2_000, 2_000, 100, 20);
        assertTrue(mFound.isEmpty());
        tick(2_000, 2_000, 140, 45);

        assertEquals(PlaybackHealth.KIND_FRAME_DROPS, mFound.get(0));
    }

    @Test
    public void stallsAgainAndAgainAreReported() {
        mSample.stalls = PlaybackHealth.STALLS;
        mSample.stallMs = PlaybackHealth.STALLS_MIN_MS;
        tick(2_000, 2_000, 0, 0);

        assertTrue(mFound.contains(PlaybackHealth.KIND_STALLS));
    }

    /**
     * 3 stalls of 9 ms in all: nothing anyone could see
     */
    @Test
    public void stallsTooShortToSeeAreNotReported() {
        mSample.stalls = PlaybackHealth.STALLS;
        mSample.stallMs = 9;
        tick(2_000, 2_000, 0, 0);

        assertTrue(mFound.isEmpty());
    }

    @Test
    public void silenceIsReportedUnlessMuted() {
        mSample.isVideoRendered = true;
        mSample.silentMs = PlaybackHealth.SILENT_MS;
        mSample.isMuted = true;
        tick(2_000, 2_000, 60, 0);
        assertTrue(mFound.isEmpty());

        mSample.isMuted = false;
        tick(2_000, 2_000, 120, 0);
        assertEquals(PlaybackHealth.KIND_AUDIO_SILENT, mFound.get(0));
    }

    @Test
    public void anotherFormatIsCheckedAgain() {
        mSample.stalls = PlaybackHealth.STALLS;
        mSample.stallMs = PlaybackHealth.STALLS_MIN_MS;
        tick(2_000, 2_000, 0, 0);
        mSample.attempt = 2;
        tick(2_000, 2_000, 0, 0);

        assertEquals(2, mFound.size());
    }

    /**
     * 16 s of a 43 minute video's end were silent: the video's, not the stream's
     */
    @Test
    public void aQuietPartOfTheVideoIsNotSilence() {
        mSample.playedMs = 2_578_000;
        mSample.silentMs = PlaybackHealth.SILENT_MS + 1_000;
        tick(2_000, 2_000, 0, 0);
        assertTrue(mFound.isEmpty());
        assertFalse(PlaybackHealth.isSilentFromStart(mSample));

        mSample.silentMs = PlaybackHealth.LONG_SILENT_MS;
        tick(2_000, 2_000, 0, 0);
        assertEquals(PlaybackHealth.KIND_AUDIO_SILENT, mFound.get(0));
        assertFalse(PlaybackHealth.isSilentFromStart(mSample));
    }

    @Test
    public void silenceFromTheStartIsTheStreams() {
        mSample.playedMs = PlaybackHealth.SILENT_MS + PlaybackHealth.SILENT_START_SLACK_MS;
        mSample.silentMs = PlaybackHealth.SILENT_MS;
        assertTrue(PlaybackHealth.isSilentFromStart(mSample));

        mSample.playedMs++;
        assertFalse(PlaybackHealth.isSilentFromStart(mSample));
    }

    /**
     * As a SHIELD's: the first frame, the sound goes on, about 5 s later the renderer drops all it's late for and goes on
     */
    @Test
    public void aFreezeOfTheStartIsReportedWhenItEnds() {
        mSample.isVideoRendered = true;
        tick(500, 500, 1, 0);

        for (int i = 0; i < 9; i++) {
            tick(500, 500, 1, 0);
        }

        tick(500, 500, 1, 120);
        assertTrue(mFound.isEmpty());

        tick(500, 500, 9, 120);
        assertEquals(Collections.singletonList(PlaybackHealth.KIND_START_FROZEN), mFound);
        assertEquals(5_000, mHealth.getStartFrozenMs());
    }

    /**
     * What it dropped to catch up isn't counted again, but it hides nothing dropped later
     */
    @Test
    public void framesDroppedAfterAFreezeOfTheStartAreStillReported() {
        aFreezeOfTheStartIsReportedWhenItEnds();
        mFound.clear();

        tick(500, 500, 21, 120);
        assertTrue(mFound.isEmpty());

        tick(500, 500, 22, 160);
        assertEquals(Collections.singletonList(PlaybackHealth.KIND_FRAME_DROPS), mFound);
    }

    @Test
    public void aShortStillPictureAtTheStartIsNothing() {
        mSample.isVideoRendered = true;
        tick(500, 500, 1, 0);
        tick(500, 500, 1, 0);
        tick(500, 500, 1, 0);
        tick(500, 500, 13, 0);

        assertTrue(mFound.isEmpty());
    }

    @Test
    public void aFreezeOfTheStartThatDoesntEndIsFrozen() {
        mSample.isVideoRendered = true;
        tick(500, 500, 1, 0);

        for (int i = 0; i < 15; i++) {
            tick(500, 500, 1, 0);
        }

        assertTrue(mFound.isEmpty());
        tick(500, 500, 1, 0);
        assertEquals(Collections.singletonList(PlaybackHealth.KIND_VIDEO_FROZEN), mFound);

        // Reported already: not again when it ends
        tick(500, 500, 9, 0);
        assertEquals(1, mFound.size());
    }

    @Test
    public void onlyTheStartIsSampledClosely() {
        assertEquals(500, mHealth.getTickMs());
        playPastTheStart();
        assertEquals(2_000, mHealth.getTickMs());
    }

    @Test
    public void theStartsFramesAreGivenOnceWhenItHasPlayed() {
        mSample.isVideoRendered = true;
        tick(2_000, 2_000, 1, 0);
        tick(2_000, 2_000, 1, 0);
        tick(2_000, 2_000, 1, 0);
        tick(2_000, 2_000, 40, 120);
        tick(2_000, 2_000, 65, 120);
        assertNull(mHealth.takeStartFrames());

        mSample.droppedToKeyframes = 1;
        tick(2_000, 2_000, 90, 120);

        PlaybackHealth.StartFrames frames = mHealth.takeStartFrames();
        assertEquals(PlaybackHealth.START_MS, frames.playedMs);
        assertEquals(90, frames.renderedFrames);
        assertEquals(120, frames.droppedFrames);
        assertEquals(1, frames.droppedToKeyframes);
        assertEquals(4_000, frames.longestFrozenMs);
        assertNull(mHealth.takeStartFrames());

        tick(2_000, 2_000, 140, 120);
        assertNull(mHealth.takeStartFrames());
    }

    /**
     * Left 4 s in: what it played of its start, once
     */
    @Test
    public void aStartLeftEarlyGivesWhatItPlayed() {
        mSample.videoId = "left";
        mSample.isVideoRendered = true;
        assertNull(mHealth.endStart());

        tick(2_000, 2_000, 1, 0);
        tick(2_000, 2_000, 50, 3);
        tick(2_000, 2_000, 100, 3);

        PlaybackHealth.StartFrames frames = mHealth.endStart();
        assertEquals("left", frames.videoId);
        assertEquals(4_000, frames.playedMs);
        assertEquals(100, frames.renderedFrames);
        assertEquals(3, frames.droppedFrames);
        assertNull(mHealth.endStart());
        assertNull(mHealth.takeStartFrames());
    }

    /**
     * The next video started 2 s into this one: its start goes out with the next one's first sample
     */
    @Test
    public void theNextFormatGivesWhatTheOneBeforePlayedOfItsStart() {
        mSample.videoId = "first";
        tick(2_000, 2_000, 1, 0);
        tick(2_000, 2_000, 50, 0);
        assertNull(mHealth.takeStartFrames());

        mSample.videoId = "next";
        mSample.attempt = 2;
        tick(500, 0, 0, 0);

        PlaybackHealth.StartFrames frames = mHealth.takeStartFrames();
        assertEquals("first", frames.videoId);
        assertEquals(2_000, frames.playedMs);
        assertEquals(50, frames.renderedFrames);
        assertNull(mHealth.takeStartFrames());
    }

    @Test
    public void aStartMeasuredWholeIsntGivenAgainWhenItEnds() {
        tick(2_000, 2_000, 1, 0);

        for (int i = 2; i <= 6; i++) {
            tick(2_000, 2_000, i * 50, 0);
        }

        assertEquals(PlaybackHealth.START_MS, mHealth.takeStartFrames().playedMs);
        assertNull(mHealth.endStart());
    }

    @Test
    public void theStartsTrailIsOnlyGivenDuringIt() {
        mSample.inputBuffers = 7;
        mSample.droppedToKeyframes = 1;
        tick(500, 500, 3, 2);

        assertEquals(1, mHealth.getStartTrail().size());
        assertArrayEquals(new long[]{0, 500, 3, 2, 7, 1}, mHealth.getStartTrail().get(0));

        playPastTheStart();
        assertTrue(mHealth.getStartTrail().isEmpty());
    }

    private void playPastTheStart() {
        for (int i = 1; i <= 6; i++) {
            tick(2_000, 2_000, i * 60, 0);
        }
    }

    private void tick(long elapsedMs, long movedMs, long rendered, long dropped) {
        mSample.nowMs += elapsedMs;
        mSample.positionMs += movedMs;
        mSample.renderedFrames = rendered;
        mSample.droppedFrames = dropped;
        mFound.addAll(mHealth.onSample(mSample));
    }
}
