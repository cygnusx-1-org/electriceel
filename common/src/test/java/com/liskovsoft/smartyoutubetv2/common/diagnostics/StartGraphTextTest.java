package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StartGraphTextTest {
    @After
    public void tearDown() {
        VideoStarts.resetForTesting();
    }

    @Test
    public void drawsARowPerStageOnTheTimeAxis() {
        String expected = String.join("\n",
                "Video start 3420 ms dQw4w9WgXcQ: open, first of run, TV sabr",
                "                       0s            1s             2s            3s",
                "player screen         |#                                                 |   52",
                "formats               |###########################                       | 1840",
                "  queue               |#                                                 |    5",
                "  lock wait           |####                                              |  210 warm-up",
                "  sign-in             |   ##                                             |   62",
                "  TV attempt          |    ##############                                |  930 ok",
                "    poToken           |    ##########                                    |  610 TV",
                "    request           |             #####                                |  260",
                "  subtitles           |                 ######                           |  300 WEB_DUMMY",
                "  decipher            |                      ##                          |   70",
                "  to the player       |                       ####                       |  254",
                "open                  |                          ##                      |   15",
                "buffering             |                           #######################| 1561",
                "  first video chunk   |                           #############          |  830 2.1 MB",
                "  video decoder       |                                       ##         |   40 OMX.Nvidia.vp9.decode",
                "  display mode switch |                                        ##########|  620",
                "first frame           |                                             ^    | 3105");

        assertEquals(expected, StartGraphText.render(new VideoStartFixture().slowStart()));
    }

    /**
     * A long start: the seconds marked further apart, so their labels fit
     */
    @Test
    public void aLongerStartIsMarkedInLongerSteps() {
        VideoStart start = new VideoStart(0, "video", VideoStart.TRIGGER_NEXT, false, -1, null, null, true, 21_000,
                Collections.singletonList(new VideoStart.Stage("buffering", null, 0, 0, 21_000)));

        String[] lines = StartGraphText.render(start).split("\n");

        assertEquals(Arrays.asList("Video start 21000 ms video: next, cached",
                "           0s         5s          10s         15s         20s",
                "buffering |##################################################|21000"), Arrays.asList(lines));
    }

    /**
     * The formats came and it played in the same ms: their rows are at the end
     */
    @Test
    public void aStageAtTheEndIsInTheLastColumn() {
        VideoStart start = new VideoStart(0, "video", VideoStart.TRIGGER_OPEN, false, -1, "TV", "dash", false, 2_000,
                Arrays.asList(new VideoStart.Stage("formats", null, 0, 0, 2_000), new VideoStart.Stage("open", null, 0, 2_000, 2_000),
                        new VideoStart.Stage("first frame", null, 0, 2_000, 2_000)));

        String[] lines = StartGraphText.render(start).split("\n");

        assertEquals("open        |                                                 #|    0", lines[3]);
        assertEquals("first frame |                                                 ^| 2000", lines[4]);
    }

    /**
     * Never played: its header says so, and why
     */
    @Test
    public void aStartThatNeverPlayedSaysSo() {
        VideoStart start = new VideoStart(0, "video", VideoStart.TRIGGER_NEXT, false, -1, "TV", null, false, VideoStart.OUTCOME_GAVE_UP,
                15_000, Collections.singletonList(new VideoStart.Stage("formats", "still going", 0, 0, 15_000)));

        String[] lines = StartGraphText.render(start).split("\n");

        assertEquals("Video start not playing after 15000 ms video: next, TV, gave_up", lines[0]);
        assertEquals("formats |##################################################|15000 still going", lines[2]);
    }

    @Test
    public void ticksFitTheirLabels() {
        assertEquals(500, StartGraphText.getTickStepMs(2_000, 50, 8));
        assertEquals(1_000, StartGraphText.getTickStepMs(3_420, 50, 8));
        assertEquals(5_000, StartGraphText.getTickStepMs(21_000, 50, 8));
        assertEquals("1.5s", StartGraphText.formatTick(1_500));
        assertEquals("2s", StartGraphText.formatTick(2_000));
    }
}
