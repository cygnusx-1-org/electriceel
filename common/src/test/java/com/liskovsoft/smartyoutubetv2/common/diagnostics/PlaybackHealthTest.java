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

    /**
     * VP4G86E9 #1: silent for its first 16 s, then 11 min more of it before another format was tried, which had sound. When the sound
     * came back is what tells a silent intro from a stream with none.
     */
    @Test
    public void theSoundComingBackAfterSilenceIsTold() {
        mSample.playedMs = 16_300;
        mSample.silentMs = PlaybackHealth.SILENT_MS + 800;
        mSample.soundBuffers = 4;
        tick(2_000, 2_000, 480, 0);
        assertEquals(PlaybackHealth.KIND_AUDIO_SILENT, mFound.get(0));
        assertEquals(-1, mHealth.takeSoundBackMs());

        // Still silent
        mSample.playedMs = 18_300;
        mSample.silentMs += 2_000;
        tick(2_000, 2_000, 540, 0);
        assertEquals(-1, mHealth.takeSoundBackMs());

        // A sound decoded
        mSample.playedMs = 20_300;
        mSample.silentMs = 300;
        mSample.soundBuffers = 5;
        tick(2_000, 2_000, 600, 0);
        assertEquals(20_300, mHealth.takeSoundBackMs());

        // Once
        assertEquals(-1, mHealth.takeSoundBackMs());
        mSample.soundBuffers = 9;
        tick(2_000, 2_000, 660, 0);
        assertEquals(-1, mHealth.takeSoundBackMs());
    }

    /**
     * A seek, or another track, starts the silence's count over: no sound was decoded
     */
    @Test
    public void aSeekAfterSilenceIsntTheSoundComingBack() {
        mSample.playedMs = 16_300;
        mSample.silentMs = PlaybackHealth.SILENT_MS + 800;
        mSample.soundBuffers = 4;
        tick(2_000, 2_000, 480, 0);
        assertEquals(PlaybackHealth.KIND_AUDIO_SILENT, mFound.get(0));

        mSample.playedMs = 18_300;
        mSample.silentMs = 0;
        tick(2_000, 2_000, 540, 0);

        assertEquals(-1, mHealth.takeSoundBackMs());
    }

    /**
     * Not reported silent: sound decoded isn't news
     */
    @Test
    public void soundWithoutSilenceReportedIsntTold() {
        mSample.playedMs = 2_000;
        mSample.soundBuffers = 10;
        tick(2_000, 2_000, 60, 0);
        mSample.soundBuffers = 20;
        tick(2_000, 2_000, 120, 0);

        assertEquals(-1, mHealth.takeSoundBackMs());
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
        assertFalse(mHealth.isStartClockStill());
        mFound.clear();

        tick(500, 500, 21, 120);
        assertTrue(mFound.isEmpty());

        tick(500, 500, 22, 160);
        assertEquals(Collections.singletonList(PlaybackHealth.KIND_FRAME_DROPS), mFound);
    }

    /**
     * As a SHIELD's: the audio clock holds the position and the picture about 5 s, then jumps, and the renderer drops what it's late for
     */
    @Test
    public void aClockStillAtTheStartIsAFreezeNotFrameDrops() {
        mSample.isVideoRendered = true;
        tick(500, 0, 1, 0);

        for (int i = 0; i < 8; i++) {
            tick(500, 0, 1, 0);
        }

        assertTrue(mFound.isEmpty());

        tick(500, 4_977, 2, 11);
        assertEquals(Collections.singletonList(PlaybackHealth.KIND_START_FROZEN), mFound);
        assertEquals(4_000, mHealth.getStartFrozenMs());
        assertTrue(mHealth.isStartClockStill());

        tick(500, 500, 7, 115);
        tick(500, 500, 9, 132);
        tick(500, 500, 21, 132);
        assertEquals(1, mFound.size());
    }

    @Test
    public void aClockStillAtTheStartIsThePicturesLongestStill() {
        aClockStillAtTheStartIsAFreezeNotFrameDrops();

        for (int i = 0; i < 12; i++) {
            tick(500, 500, 21 + i * 12, 132);
        }

        PlaybackHealth.StartFrames frames = mHealth.takeStartFrames();

        assertEquals(4_000, frames.longestFrozenMs);
    }

    @Test
    public void framesDroppedAfterAClockStillAreStillReported() {
        aClockStillAtTheStartIsAFreezeNotFrameDrops();
        mFound.clear();

        tick(500, 500, 33, 132);
        tick(500, 500, 34, 172);
        assertEquals(Collections.singletonList(PlaybackHealth.KIND_FRAME_DROPS), mFound);
    }

    /**
     * The renderer dropping on and on past what it's late for is frame drops too
     */
    @Test
    public void dropsThatGoOnPastTheCatchUpAreReported() {
        mSample.isVideoRendered = true;
        tick(500, 0, 1, 0);

        for (int i = 0; i < 8; i++) {
            tick(500, 0, 1, 0);
        }

        tick(500, 4_977, 2, 20);
        mFound.clear();

        // The catch up, all of it dropping
        for (long playedMs = 500; playedMs <= PlaybackHealth.CATCH_UP_MS; playedMs += 500) {
            tick(500, 500, 2 + playedMs / 500, 20 + playedMs / 500 * 20);
        }

        assertTrue(mFound.isEmpty());

        tick(500, 500, 7, 120);
        tick(500, 500, 8, 140);
        assertEquals(Collections.singletonList(PlaybackHealth.KIND_FRAME_DROPS), mFound);
    }

    /**
     * K067TC3S #19: 1440p60 VP9 dropped 44 frames and showed 32 in the 1.35 s after a seek's resume, 12 in the 47 s before. That's its
     * decoder catching up from the new position, not frames it can't keep up with.
     */
    @Test
    public void dropsOfASeeksResumeAreItsCatchUp() {
        resumeAfterAPause(1_500);

        assertTrue(mFound.isEmpty());

        // Dropping on past it is frame drops again
        for (int i = 1; i <= 4; i++) {
            tick(500, 500, 536 + i * 10, 56 + i * 20);
        }

        assertEquals(Collections.singletonList(PlaybackHealth.KIND_FRAME_DROPS), mFound);
    }

    /**
     * The same drops after a pause with no seek: the decoder had nothing to catch up on
     */
    @Test
    public void dropsOfAPausesResumeAreFrameDrops() {
        resumeAfterAPause(-1);

        assertEquals(Collections.singletonList(PlaybackHealth.KIND_FRAME_DROPS), mFound);
    }

    /**
     * Resumed long after the seek (e.g. paused there a while): not its catch-up
     */
    @Test
    public void aResumeLongAfterASeekIsntItsCatchUp() {
        resumeAfterAPause(PlaybackHealth.SEEK_RESUME_MS + 1);

        assertEquals(Collections.singletonList(PlaybackHealth.KIND_FRAME_DROPS), mFound);
    }

    /**
     * As #19: paused 7.5 s (the window holds little playing), sent elsewhere the given time before it plays again (-1 for not), then
     * 44 dropped and 26 shown in 1.5 s, then none dropped
     */
    private void resumeAfterAPause(long seekBeforeMs) {
        mSample.isVideoRendered = true;
        playPastTheStart();
        tick(2_000, 2_000, 480, 12);

        mHealth.onInterrupted(PlaybackHealth.RESUMED_BY_PAUSE);
        mSample.isPlaying = false;
        long pausedMs = 7_500;

        if (seekBeforeMs >= 0) {
            tick(pausedMs - seekBeforeMs, 0, 480, 12);
            mHealth.onSeek(mSample.nowMs);
            tick(seekBeforeMs, 0, 480, 12);
        } else {
            tick(pausedMs, 0, 480, 12);
        }

        mSample.isPlaying = true;
        mHealth.onResumed(mSample.nowMs);
        tick(500, 500, 492, 24);
        tick(500, 500, 500, 44);
        tick(500, 500, 506, 56);
        tick(500, 500, 536, 56);
    }

    @Test
    public void aClockStillPastTheStartIsNotAFreezeOfIt() {
        mSample.isVideoRendered = true;
        playPastTheStart();
        tick(2_000, 0, 360, 0);
        tick(2_000, 0, 360, 0);
        tick(2_000, 6_000, 420, 0);

        assertTrue(mFound.isEmpty());
    }

    @Test
    public void aShortClockStillAtTheStartIsNothing() {
        mSample.isVideoRendered = true;
        tick(500, 0, 1, 0);
        tick(500, 0, 1, 0);
        tick(500, 0, 1, 0);
        tick(500, 1_500, 13, 0);

        assertTrue(mFound.isEmpty());
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
    public void framesNotCountedAreNeitherFrozenNorMeasured() {
        // Tunneled: the first frame was shown, the decoder reports no more (see DebugInfoMediaCodecVideoRenderer#isRenderedCountKnown)
        mSample.isVideoRendered = true;

        for (int i = 0; i < 40; i++) {
            tick(500, 500, -1, -1);
        }

        assertTrue(mFound.isEmpty());
        assertNull(mHealth.takeStartFrames());
        assertTrue(mHealth.getStartTrail().isEmpty());
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
        mSample.audioTimestampState = 2;
        mSample.audioPlayheadMs = 480;
        mSample.audioLatencyMs = 20;
        tick(500, 500, 3, 2);

        assertEquals(1, mHealth.getStartTrail().size());
        assertArrayEquals(new long[]{0, 500, 3, 2, 7, 1, 2, 480, 20}, mHealth.getStartTrail().get(0));

        playPastTheStart();
        assertTrue(mHealth.getStartTrail().isEmpty());
    }

    /**
     * The counters are of all the AudioTracks: the start has what they went up by in it, not the video's before
     */
    @Test
    public void theStartsAudioClockIsWhatItWentThroughInIt() {
        mSample.videoId = "video";
        setAudioTotals(2, 0, 5);
        mSample.audioTimestampState = 1;
        mSample.audioLongestHoldMs = 0;
        tick(500, 0, 1, 0);

        mSample.audioLongestHoldMs = 480;
        setAudioTotals(2, 0, 6);
        tick(500, 500, 2, 0);
        mSample.audioTimestampState = 2;
        playPastTheStart();

        PlaybackHealth.StartAudioClock audio = mHealth.takeStartFrames().audioClock;
        assertEquals(2, audio.timestampState);
        assertEquals(0, audio.framesMismatches);
        assertEquals(0, audio.clockMismatches);
        assertEquals(1, audio.holdReleases);
        assertEquals(480, audio.longestHoldMs);
    }

    /**
     * The engine restarted in the start: the new player's counters start over from 0
     */
    @Test
    public void anotherPlayersCountersAddToTheStart() {
        setAudioTotals(3, 1, 2);
        tick(500, 0, 1, 0);
        setAudioTotals(4, 1, 3);
        tick(500, 500, 2, 0);
        setAudioTotals(1, 0, 1);
        tick(500, 500, 3, 0);

        PlaybackHealth.StartAudioClock audio = mHealth.endStart().audioClock;
        assertEquals(2, audio.framesMismatches);
        assertEquals(0, audio.clockMismatches);
        assertEquals(2, audio.holdReleases);
    }

    @Test
    public void aStartWithoutTheAudioClockHasNone() {
        tick(2_000, 2_000, 1, 0);
        tick(2_000, 2_000, 50, 0);

        assertNull(mHealth.endStart().audioClock);
    }

    @Test
    public void playingAgainIsSampledCloselyWithItsTrail() {
        playPastTheStart();
        mHealth.onInterrupted(PlaybackHealth.RESUMED_BY_SEEK);
        mSample.isPlaying = false;
        tick(2_000, 0, 360, 0);
        assertEquals(2_000, mHealth.getTickMs());

        mSample.isPlaying = true;
        mHealth.onResumed(mSample.nowMs + 100);
        mSample.positionMs = 60_000;
        mSample.audioTimestampState = 1;
        tick(100, 0, 361, 0);
        tick(500, 0, 361, 0);

        assertEquals(500, mHealth.getTickMs());
        assertEquals(PlaybackHealth.RESUMED_BY_SEEK, mHealth.getResumedBy());
        assertEquals(2, mHealth.getResumeTrail().size());
        assertArrayEquals(new long[]{0, 60_000, 361, 0, -1, -1, 1, -1, -1}, mHealth.getResumeTrail().get(0));
        assertEquals(500, mHealth.getResumeTrail().get(1)[0]);
        // Past the start, it has none
        assertTrue(mHealth.getStartTrail().isEmpty());

        for (int i = 0; i < 19; i++) {
            tick(500, 500, 362 + i * 12, 0);
        }

        assertEquals(PlaybackHealth.START_MS, mHealth.getResumeTrail().get(mHealth.getResumeTrail().size() - 1)[0]);
        tick(500, 500, 600, 0);
        assertTrue(mHealth.getResumeTrail().isEmpty());
        assertEquals(2_000, mHealth.getTickMs());
    }

    /**
     * A seek makes another AudioTrack: it's what it plays again after, whatever else stopped it meanwhile. A pause goes before buffering.
     */
    @Test
    public void whatItPlaysAgainAfterIsTheMostLikelyToRestartTheClock() {
        mHealth.onInterrupted(PlaybackHealth.RESUMED_BY_PAUSE);
        mHealth.onInterrupted(PlaybackHealth.RESUMED_BY_SEEK);
        mHealth.onInterrupted(PlaybackHealth.RESUMED_BY_BUFFERING);
        mHealth.onResumed(0);
        assertEquals(PlaybackHealth.RESUMED_BY_SEEK, mHealth.getResumedBy());

        mHealth.onInterrupted(PlaybackHealth.RESUMED_BY_BUFFERING);
        mHealth.onInterrupted(PlaybackHealth.RESUMED_BY_PAUSE);
        mHealth.onResumed(0);
        assertEquals(PlaybackHealth.RESUMED_BY_PAUSE, mHealth.getResumedBy());

        mHealth.onResumed(0);
        assertNull(mHealth.getResumedBy());
    }

    /**
     * The first play since the player was prepared is the start, not a resume: what stopped the video before is forgotten
     */
    @Test
    public void theStartEndsTheResumeBefore() {
        tick(500, 500, 1, 0);
        mHealth.onResumed(mSample.nowMs);
        tick(500, 500, 2, 0);
        assertEquals(1, mHealth.getResumeTrail().size());

        mHealth.onInterrupted(PlaybackHealth.RESUMED_BY_PAUSE);
        mHealth.onStarted();
        tick(500, 500, 3, 0);
        assertTrue(mHealth.getResumeTrail().isEmpty());

        mHealth.onResumed(mSample.nowMs);
        assertNull(mHealth.getResumedBy());
    }

    /**
     * Another format of the video (e.g. another quality) plays on the same clock: its trail goes on. Another video's doesn't.
     */
    @Test
    public void anotherFormatGoesOnWithTheTrailAnotherVideoDoesnt() {
        mSample.videoId = "video";
        tick(500, 500, 1, 0);
        mHealth.onResumed(mSample.nowMs);
        tick(500, 500, 2, 0);

        mSample.attempt = 2;
        tick(500, 500, 3, 0);
        assertEquals(2, mHealth.getResumeTrail().size());
        assertEquals(1_000, mHealth.getResumeTrail().get(1)[0]);

        mSample.attempt = 3;
        mSample.videoId = "next";
        tick(500, 500, 0, 0);
        assertTrue(mHealth.getResumeTrail().isEmpty());
    }

    private void setAudioTotals(long framesMismatches, long clockMismatches, long holdReleases) {
        mSample.audioTotalFramesMismatches = framesMismatches;
        mSample.audioTotalClockMismatches = clockMismatches;
        mSample.audioTotalHoldReleases = holdReleases;
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
