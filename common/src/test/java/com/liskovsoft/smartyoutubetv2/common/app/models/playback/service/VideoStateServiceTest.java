package com.liskovsoft.smartyoutubetv2.common.app.models.playback.service;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoStateServiceTest {
    private static final String DELIM = "&si;"; // VideoStateService.DELIM
    private static final long DURATION_MS = 60_000;

    @Before
    public void setUp() throws InterruptedException {
        clearSaved();
    }

    @After
    public void tearDown() throws InterruptedException {
        Utils.sHandler.removeCallbacksAndMessages(null);
        clearSaved();
    }

    @Test
    public void statesSurviveRestart() throws InterruptedException {
        VideoStateService service = getService();
        service.save(createState("a", 1_000));
        service.save(createState("b", 2_000));
        service.save(createState("c", 3_000));

        restart();

        List<State> restored = getService().getStates();

        assertEquals(Arrays.asList("a", "b", "c"), getVideoIds(restored));
        assertEquals(2_000, restored.get(1).positionMs);
        assertEquals("Title b", restored.get(1).video.title);
    }

    @Test
    public void brokenHistorySurvivesRestart() throws InterruptedException {
        VideoStateService service = getService();
        service.setHistoryBroken(true);
        service.save(createState("a", 1_000));

        restart();

        assertTrue(getService().isHistoryBroken());
        assertEquals(Arrays.asList("a"), getVideoIds(getService().getStates()));
    }

    /**
     * Written off the main thread, 10 seconds after the change
     */
    @Test
    public void changeWrittenAfterDelay() throws InterruptedException {
        getService().save(createState("delayed", 1_000));
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(9));

        assertFalse(isSaved("delayed"));

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(1));
        long deadlineMs = System.currentTimeMillis() + 5_000;

        while (!isSaved("delayed") && System.currentTimeMillis() < deadlineMs) {
            Thread.sleep(10);
        }

        assertTrue(isSaved("delayed"));
    }

    @Test
    public void restoreKeepsLaterCopyOfEqualStates() throws InterruptedException {
        setSaved(Arrays.asList(createState("a", 1_000), createState("b", 2_000), createState("a", 5_000), createState("c", 3_000)));

        List<State> restored = getService().getStates();

        assertEquals(Arrays.asList("b", "a", "c"), getVideoIds(restored));
        assertEquals(5_000, restored.get(1).positionMs);
    }

    /**
     * Restored at once, not one by one, but the same states in the same order
     */
    @Test
    public void restoreKeepsWhatAddingOneByOneKeeps() throws InterruptedException {
        int maxSize = Utils.isEnoughRam() ? 5_000 : 500;
        List<State> saved = new ArrayList<>();

        for (int i = 0; i < maxSize + 50; i++) {
            saved.add(createState("video" + i, i));

            if (i > 0 && i % 100 == 0) {
                saved.add(createState("video" + (i - 50), i)); // watched again
            }
        }

        List<State> oneByOne = Helpers.createSafeLRUList(maxSize);

        for (State state : saved) {
            oneByOne.add(state);
        }

        setSaved(saved);

        List<State> restored = getService().getStates();

        assertEquals(maxSize + 1, restored.size());
        assertEquals(getVideoIds(oneByOne), getVideoIds(restored));
        assertEquals(getPositions(oneByOne), getPositions(restored));
    }

    /**
     * Read from the bytes, but as the text was read: Helpers.splitData, Helpers.split and State.from
     */
    @Test
    public void restoreReadsTheBytesAsTheText() throws InterruptedException {
        String a = createState("a", 1_000).toString();
        String b = createState("b", 2_000).toString();
        String c = createState("c", 3_000).toString();
        String unicode = createState("ünï • 日本 😀", 4_000).toString();
        String[] cases = {
                a + DELIM + b + DELIM + c,
                DELIM + a + DELIM + b,
                a + DELIM + DELIM + b,
                a + DELIM + b + DELIM + DELIM,
                a + DELIM + " " + DELIM + b,
                unicode + DELIM + a,
                a + DELIM + b + "%OB%true",
                a + DELIM + b + "%OB%false",
                a + "%OB%",
                a + "%OB%true%OB%false",
                a + "%OB%%OB%true",
                "%OB%true",
                "null%OB%true",
                "   ",
                "old_video_id&sf;1000",
                "x&sf;5&sf;&sf;",
                " &sf;7",
                "&sf;&sf;",
        };

        for (String data : cases) {
            String[] split = Helpers.splitData(data);
            String states = Helpers.parseStr(split, 0);
            List<String> expected = new ArrayList<>();

            if (states != null) {
                for (String spec : Helpers.split(states, DELIM)) {
                    State state = State.from(spec);

                    if (state != null) {
                        expected.add(state.toString());
                    }
                }
            }

            VideoStateService.resetInstanceForTesting();
            getPrefs().setData(getPrefs().getStateUpdaterKey(), data);
            List<String> restored = new ArrayList<>();

            for (State state : getService().getStates()) {
                restored.add(state.toString());
            }

            assertEquals(data, expected, restored);
            assertEquals(data, Helpers.parseBoolean(split, 1), getService().isHistoryBroken());
        }
    }

    private static State createState(String videoId, long positionMs) {
        Video video = new Video();
        video.videoId = videoId;
        video.title = "Title " + videoId;

        return new State(video, positionMs, DURATION_MS);
    }

    private static void restart() throws InterruptedException {
        getService().persistNow();
        ShadowLooper.shadowMainLooper().idle();
        VideoStateService.resetInstanceForTesting();
    }

    private static void setSaved(List<State> states) throws InterruptedException {
        VideoStateService.resetInstanceForTesting();
        getPrefs().setData(getPrefs().getStateUpdaterKey(), Helpers.merge(DELIM, states.toArray()));
    }

    private static boolean isSaved(String videoId) {
        String data = getPrefs().getStateUpdaterData();
        return data != null && data.contains(videoId);
    }

    private static List<String> getVideoIds(List<State> states) {
        List<String> result = new ArrayList<>();

        for (State state : states) {
            result.add(state.video.videoId);
        }

        return result;
    }

    private static List<Long> getPositions(List<State> states) {
        List<Long> result = new ArrayList<>();

        for (State state : states) {
            result.add(state.positionMs);
        }

        return result;
    }

    /**
     * AppPrefs outlives the test, so do the saved states
     */
    private static void clearSaved() throws InterruptedException {
        VideoStateService.resetInstanceForTesting();
        getPrefs().setData(getPrefs().getStateUpdaterKey(), "");
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static VideoStateService getService() {
        return VideoStateService.instance(RuntimeEnvironment.getApplication());
    }
}
