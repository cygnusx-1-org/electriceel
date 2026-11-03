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
public class ServerBackoffsTest {
    private final AtomicLong mNow = new AtomicLong(10_000);

    @Before
    public void setUp() {
        ServerBackoffs.resetForTesting(mNow::get);
    }

    @After
    public void tearDown() {
        ServerBackoffs.resetForTesting(null);
    }

    /**
     * From when the server said so, for as long as it asked, cut to the start's end
     */
    @Test
    public void aBackoffLastsAsLongAsAsked() {
        mNow.set(11_000);
        ServerBackoffs.onBackoff(3_000);
        mNow.set(11_100);
        ServerBackoffs.onBackoff(200);

        List<VideoStart.Stage> stages = ServerBackoffs.take(10_500, 13_000);

        assertEquals(2, stages.size());
        assertEquals(VideoStarts.STAGE_SERVER_BACKOFF, stages.get(0).name);
        assertEquals("3000 ms asked", stages.get(0).detail);
        assertEquals(1, stages.get(0).depth);
        assertEquals(11_000, stages.get(0).startMs);
        assertEquals(13_000, stages.get(0).endMs);
        assertEquals(11_100, stages.get(1).startMs);
        assertEquals(11_300, stages.get(1).endMs);
    }

    /**
     * Told before the start, it's of the stream before
     */
    @Test
    public void oneToldBeforeTheStartIsLeftOut() {
        mNow.set(9_000);
        ServerBackoffs.onBackoff(5_000);

        assertTrue(ServerBackoffs.take(10_000, 13_000).isEmpty());
    }

    @Test
    public void keepsTheLastOnes() {
        for (int i = 0; i < ServerBackoffs.MAX_BACKOFFS + 2; i++) {
            mNow.set(20_000 + i * 100);
            ServerBackoffs.onBackoff(50);
        }

        List<VideoStart.Stage> stages = ServerBackoffs.take(0, 30_000);

        assertEquals(ServerBackoffs.MAX_BACKOFFS, stages.size());
        assertEquals(20_200, stages.get(0).startMs);
    }
}
