package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ConnectionsTest {
    @Before
    public void setUp() {
        Connections.resetForTesting();
    }

    @After
    public void tearDown() {
        Connections.resetForTesting();
    }

    @Test
    public void onlyTheOnesOfTheStartCutToIt() {
        Connections.add(1_000, 2_000);
        Connections.add(4_000, 6_000);
        Connections.add(9_000, 12_000);

        List<VideoStart.Stage> stages = Connections.take(5_000, 10_000);

        assertEquals(2, stages.size());
        assertEquals(VideoStarts.STAGE_CONNECT, stages.get(0).name);
        assertEquals(1, stages.get(0).depth);
        assertEquals(5_000, stages.get(0).startMs);
        assertEquals(6_000, stages.get(0).endMs);
        assertEquals(9_000, stages.get(1).startMs);
        assertEquals(10_000, stages.get(1).endMs);
    }

    @Test
    public void onlyTheLastOnesAreKept() {
        for (int i = 0; i < Connections.MAX_CONNECTIONS + 1; i++) {
            Connections.add(i * 10L, i * 10L + 5);
        }

        assertEquals(Connections.MAX_CONNECTIONS, Connections.take(0, 1_000).size());
        assertTrue(Connections.take(0, 5).isEmpty());
    }
}
