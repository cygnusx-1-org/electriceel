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
import static org.junit.Assert.assertTrue;

/**
 * "Play from start" of a live stream opens the start of what the stream keeps, not the live edge.
 * https://github.com/yuliskov/SmartTube/issues/6166
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PlayFromStartTest {
    private static final long DURATION_MS = 2 * 60 * 60 * 1_000; // two hours of the stream kept
    private static final String LIVE_ID = "jfKfPfyJRdk";
    private static final String OTHER_LIVE_ID = "4xDzrJKXOOY";

    @Before
    public void setUp() {
        getStateService().clear();
    }

    @After
    public void tearDown() {
        getStateService().clear();
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void liveStream_opensAtTheLiveEdge() {
        Video live = live(LIVE_ID);
        TestStateController controller = new TestStateController();

        controller.open(live);

        assertTrue("position " + controller.player.positionMs, controller.player.positionMs > DURATION_MS - 60_000);
    }

    @Test
    public void liveStream_playFromStart_opensAtTheStart() {
        Video live = live(LIVE_ID);
        TestStateController controller = new TestStateController();

        controller.open(playFromStart(live));

        assertEquals(VideoStateController.START_POSITION_MS, controller.player.positionMs);
    }

    @Test
    public void liveStream_playFromStart_isntPulledBackToTheLiveEdgeOnPlay() {
        TestStateController controller = new TestStateController();
        // Another live stream watched at the live edge: playback keeps up with the live edge from now on
        controller.open(live(OTHER_LIVE_ID));
        controller.onBuffering();
        controller.onPlay();

        controller.open(playFromStart(live(LIVE_ID)));
        controller.onBuffering();
        controller.onPlay();

        assertEquals(VideoStateController.START_POSITION_MS, controller.player.positionMs);
    }

    @Test
    public void liveStream_playFromStart_isOneShot() {
        Video live = live(LIVE_ID);
        TestStateController controller = new TestStateController();
        controller.open(playFromStart(live));

        controller.open(live);

        assertTrue("position " + controller.player.positionMs, controller.player.positionMs > DURATION_MS - 60_000);
    }

    private static Video live(String videoId) {
        Video video = new Video();
        video.videoId = videoId;
        video.isLive = true;
        return video;
    }

    /**
     * What the "Play from start" item of the video menu does before it opens the video
     */
    private static Video playFromStart(Video video) {
        getStateService().save(new State(video, 0));
        video.pendingPosMs = VideoStateController.START_POSITION_MS;
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
     * A live stream of {@link #DURATION_MS}, the position at the last one set. Returns the defaults (false, 0, null) for
     * every other call.
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
