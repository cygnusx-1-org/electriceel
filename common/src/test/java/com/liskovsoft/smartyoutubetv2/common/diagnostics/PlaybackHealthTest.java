package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
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
        tick(2_000, 2_000, 60, 0);
        tick(2_000, 2_000, 120, 0);
        // Stuck at 120 frames
        tick(2_000, 2_000, 120, 0);
        tick(2_000, 2_000, 120, 0);
        assertTrue(mFound.isEmpty());
        tick(2_000, 2_000, 120, 0);

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
        tick(2_000, 2_000, 0, 0);

        assertTrue(mFound.contains(PlaybackHealth.KIND_STALLS));
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
        tick(2_000, 2_000, 0, 0);
        mSample.attempt = 2;
        tick(2_000, 2_000, 0, 0);

        assertEquals(2, mFound.size());
    }

    private void tick(long elapsedMs, long movedMs, long rendered, long dropped) {
        mSample.nowMs += elapsedMs;
        mSample.positionMs += movedMs;
        mSample.renderedFrames = rendered;
        mSample.droppedFrames = dropped;
        mFound.addAll(mHealth.onSample(mSample));
    }
}
