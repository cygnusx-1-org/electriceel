package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LoadHoldsTest {
    private final AtomicLong mNow = new AtomicLong(10_000);

    @Before
    public void setUp() {
        LoadHolds.resetForTesting(mNow::get);
    }

    @After
    public void tearDown() {
        LoadHolds.resetForTesting(null);
    }

    @Test
    public void aHoldLastsTillThePlayerLoadsAgain() {
        LoadHolds.onDecision(true, 0, 0);
        mNow.set(11_000);
        LoadHolds.onDecision(false, 30_000_000, 37_000_000);
        mNow.set(11_500);
        LoadHolds.onDecision(false, 29_500_000, 37_000_000); // the same hold
        mNow.set(12_000);
        LoadHolds.onDecision(true, 1_000_000, 2_000_000);

        List<VideoStart.Stage> holds = LoadHolds.take(10_000, 20_000);

        assertEquals(1, holds.size());
        assertEquals("loading held", holds.get(0).name);
        assertEquals("buffered 30.0 s, 37 MB", holds.get(0).detail);
        assertEquals(11_000, holds.get(0).startMs);
        assertEquals(12_000, holds.get(0).endMs);
    }

    @Test
    public void holdsAreCutToTheStart() {
        mNow.set(11_000);
        LoadHolds.onDecision(false, 0, 0);
        mNow.set(13_000);
        LoadHolds.onDecision(true, 0, 0);
        mNow.set(14_000);
        LoadHolds.onDecision(false, 0, 0); // still going on

        List<VideoStart.Stage> holds = LoadHolds.take(12_000, 15_000);

        assertEquals(2, holds.size());
        assertEquals(12_000, holds.get(0).startMs);
        assertEquals(13_000, holds.get(0).endMs);
        assertEquals(14_000, holds.get(1).startMs);
        assertEquals(15_000, holds.get(1).endMs);
        // Before the start, or too short to see
        assertTrue(LoadHolds.take(9_000, 11_000).isEmpty());
    }

    /**
     * Told the period ended, the player's buffered duration isn't a time
     */
    @Test
    public void aBufferToTheEndIsNotATime() {
        mNow.set(11_000);
        LoadHolds.onDecision(false, Long.MAX_VALUE - 1_000, 13_000_000);
        mNow.set(11_500);
        LoadHolds.onDecision(true, 0, 0);

        assertEquals("buffered to the end, 13 MB", LoadHolds.take(10_000, 12_000).get(0).detail);
    }

    @Test
    public void keepsTheLastOnes() {
        for (int i = 0; i < LoadHolds.MAX_HOLDS + 3; i++) {
            mNow.set(20_000 + i * 100);
            LoadHolds.onDecision(false, 0, 0);
            mNow.set(20_000 + i * 100 + 50);
            LoadHolds.onDecision(true, 0, 0);
        }

        List<VideoStart.Stage> holds = LoadHolds.take(0, 30_000);

        assertEquals(LoadHolds.MAX_HOLDS, holds.size());
        assertEquals(20_300, holds.get(0).startMs);
    }
}
