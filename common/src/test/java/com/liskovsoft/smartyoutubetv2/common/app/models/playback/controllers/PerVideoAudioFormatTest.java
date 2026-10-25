package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The audio format picked in the player is for that video only. The other videos play the one of the settings.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PerVideoAudioFormatTest {
    private static final FormatItem OPUS_DRC = audio("251", "opus", 160_000, true);
    private static final FormatItem AAC = audio("140", "mp4a.40.2", 128_000, false);

    @Before
    public void setUp() {
        reset();
    }

    @After
    public void tearDown() {
        reset();
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void formatPicked_isNotSaved() throws Exception {
        getPlayerData().setFormat(AAC);
        TestHQController controller = new TestHQController();

        controller.select(OPUS_DRC);

        assertSame(OPUS_DRC, controller.player.audioFormat);
        assertSame(OPUS_DRC, getPlayerData().getTempAudioFormat());
        assertEquals(AAC, getPlayerData().getFormat(FormatItem.TYPE_AUDIO));
    }

    @Test
    public void formatPicked_turnsTheSoundOn() throws Exception {
        getPlayerData().setSoundOff(true);
        TestHQController controller = new TestHQController();

        controller.select(OPUS_DRC);

        assertFalse(getPlayerData().isSoundOff());
    }

    @Test
    public void reload_keepsTheFormatPicked() {
        TestStateController controller = new TestStateController(video("dQw4w9WgXcQ"));
        getPlayerData().setTempAudioFormat(OPUS_DRC);

        controller.onNewVideo(video("dQw4w9WgXcQ"));

        assertSame(OPUS_DRC, getPlayerData().getTempAudioFormat());
    }

    @Test
    public void anotherVideo_dropsTheFormatPicked() {
        TestStateController controller = new TestStateController(video("dQw4w9WgXcQ"));
        getPlayerData().setTempAudioFormat(OPUS_DRC);

        controller.onNewVideo(video("jNQXAC9IVRw"));

        assertNull(getPlayerData().getTempAudioFormat());
    }

    @Test
    public void engineRestart_restoresTheFormatPicked() {
        getPlayerData().setFormat(AAC);
        TestStateController controller = new TestStateController(video("dQw4w9WgXcQ"));
        getPlayerData().setTempAudioFormat(OPUS_DRC);

        controller.onSourceChanged(controller.getVideo());

        assertSame(OPUS_DRC, controller.player.audioFormat);
    }

    @Test
    public void soundOff_winsOverTheFormatPicked() {
        TestStateController controller = new TestStateController(video("dQw4w9WgXcQ"));
        getPlayerData().setTempAudioFormat(OPUS_DRC);
        getPlayerData().setSoundOff(true);

        controller.onSourceChanged(controller.getVideo());

        assertTrue(controller.player.audioFormat.isDefault());
    }

    @Test
    public void nothingPicked_playsTheSavedFormat() {
        getPlayerData().setFormat(AAC);
        TestStateController controller = new TestStateController(video("dQw4w9WgXcQ"));

        controller.onSourceChanged(controller.getVideo());

        assertEquals(AAC, controller.player.audioFormat);
    }

    private static FormatItem audio(String id, String codecs, int bitrate, boolean isDrc) {
        return ExoFormatItem.from(FormatItem.TYPE_AUDIO, TrackSelectorManager.RENDERER_INDEX_AUDIO, id, codecs,
                -1, -1, -1, "en", false, bitrate, isDrc, false);
    }

    private static Video video(String videoId) {
        Video video = new Video();
        video.videoId = videoId;
        return video;
    }

    private static void reset() {
        PlayerData data = getPlayerData();
        data.setSoundOff(false);
        data.setFormat(data.getDefaultAudioFormat());
        data.setTempAudioFormat(null);
    }

    private static PlayerData getPlayerData() {
        return PlayerData.instance(RuntimeEnvironment.getApplication());
    }

    private static class TestHQController extends HQDialogController {
        private final RecordingPlayer player = new RecordingPlayer();

        private TestHQController() {
            setAltContext(RuntimeEnvironment.getApplication());
        }

        @Override
        public PlaybackView getPlayer() {
            return player.view;
        }

        /**
         * Picked in the Audio formats of the player
         */
        private void select(FormatItem format) throws Exception {
            Method select = HQDialogController.class.getDeclaredMethod("selectFormatOption", OptionItem.class);
            select.setAccessible(true);
            select.invoke(this, UiOptionItem.from(format, option -> {}));
        }
    }

    private static class TestStateController extends VideoStateController {
        private final RecordingPlayer player = new RecordingPlayer();
        private final Video mVideo;

        private TestStateController(Video video) {
            mVideo = video;
            player.video = video;
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
    }

    /**
     * Keeps the last audio format set. Returns the defaults (false, 0, null) for every other call.
     */
    private static class RecordingPlayer {
        private FormatItem audioFormat;
        private Video video;
        private final PlaybackView view = (PlaybackView) Proxy.newProxyInstance(PlaybackView.class.getClassLoader(),
                new Class<?>[]{PlaybackView.class}, (proxy, method, args) -> {
                    if ("setFormat".equals(method.getName()) && args[0] instanceof FormatItem
                            && ((FormatItem) args[0]).getType() == FormatItem.TYPE_AUDIO) {
                        audioFormat = (FormatItem) args[0];
                    } else if ("getVideo".equals(method.getName())) {
                        return video;
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
