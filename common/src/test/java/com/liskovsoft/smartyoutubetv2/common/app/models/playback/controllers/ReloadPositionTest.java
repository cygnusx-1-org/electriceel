package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * A reload of a video stays where it was. Near its end it counted as ended, and the video played again from 0:00
 * (9lKpEOEbr0U, stalled at its end over DASH and reloaded)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ReloadPositionTest {
    private static final long DURATION_MS = 1_972_000;
    private static final String VIDEO_ID = "9lKpEOEbr0U";

    @Before
    public void setUp() {
        getStateService().clear();
    }

    @After
    public void tearDown() {
        getStateService().clear();
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Saved as watched to the end: a second before it, where a stopped video is left
     */
    @Test
    public void aReloadAtTheEndStaysASecondBeforeIt() {
        Video video = video();
        getStateService().save(new State(video, DURATION_MS, DURATION_MS, 1.0f));
        TestStateController controller = new TestStateController();

        controller.open(reloaded(video));

        assertEquals(DURATION_MS - VideoLoaderController.STOP_BEFORE_END_MS, controller.player.positionMs);
    }

    @Test
    public void aReloadNearTheEndStaysThere() {
        Video video = video();
        getStateService().save(new State(video, DURATION_MS - 2_500, DURATION_MS, 1.0f));
        TestStateController controller = new TestStateController();

        controller.open(reloaded(video));

        assertEquals(DURATION_MS - 2_500, controller.player.positionMs);
    }

    /**
     * Opened, a video watched to its end starts again, as before. The reload's mark is gone after it's opened
     */
    @Test
    public void aWatchedVideoOpenedStartsFromTheBeginning() {
        Video video = video();
        TestStateController controller = new TestStateController();
        getStateService().save(new State(video, DURATION_MS, DURATION_MS, 1.0f));
        controller.open(reloaded(video));
        assertFalse(video.fromReload);

        getStateService().save(new State(video, DURATION_MS, DURATION_MS, 1.0f));
        controller.open(video);

        assertEquals(0, controller.player.positionMs);
    }

    private static Video video() {
        Video video = new Video();
        video.videoId = VIDEO_ID;
        return video;
    }

    /**
     * What VideoLoaderController's reload does before it opens the video again
     */
    private static Video reloaded(Video video) {
        video.fromReload = true;
        return video;
    }

    private static VideoStateService getStateService() {
        return VideoStateService.instance(RuntimeEnvironment.getApplication());
    }

    private static class TestStateController extends VideoStateController {
        private final RecordingPlayer player = new RecordingPlayer();
        private Video mVideo;

        private TestStateController() {
            setAltContext(RuntimeEnvironment.getApplication());
        }

        @Override
        public PlaybackView getPlayer() {
            return player.view;
        }

        @Override
        public Video getVideo() {
            return mVideo;
        }

        /**
         * The player's calls when a video is opened: the new video, then the loaded stream
         */
        private void open(Video video) {
            onNewVideo(video);
            mVideo = video;
            player.positionMs = 0;
            onVideoLoaded(video);
        }
    }

    /**
     * A video of {@link #DURATION_MS}, the position at the last one set. Returns the defaults (false, 0, null) for every other
     * call.
     */
    private static class RecordingPlayer {
        private long positionMs;
        private final PlaybackView view = (PlaybackView) Proxy.newProxyInstance(PlaybackView.class.getClassLoader(),
                new Class<?>[]{PlaybackView.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setPositionMs":
                            positionMs = (Long) args[0];
                            return null;
                        case "getPositionMs":
                            return positionMs;
                        case "getDurationMs":
                            return DURATION_MS;
                    }

                    Class<?> type = method.getReturnType();

                    if (type == boolean.class) {
                        return false;
                    } else if (type == int.class) {
                        return 0;
                    } else if (type == long.class) {
                        return 0L;
                    } else if (type == float.class) {
                        return 0f;
                    } else if (type == double.class) {
                        return 0d;
                    }

                    return null;
                });
    }
}
