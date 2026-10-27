package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.autoframerate.internal.DisplayHolder.Mode;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AutoFrameRateControllerTest {
    private static final Mode MODE_50HZ = new Mode(2, 3840, 2160, 50f);
    private final List<Boolean> mPlayWhenReady = new ArrayList<>();
    private int mSavedPauseMs;

    @Before
    public void setUp() {
        mSavedPauseMs = getPlayerData().getAfrPauseMs();
        getPlayerData().setAfrPauseMs(0);
    }

    @After
    public void tearDown() {
        getPlayerData().setAfrPauseMs(mSavedPauseMs);
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void aModeSwitchHoldsThePlaybackUntilTheDisplayReportsIt() throws Exception {
        TestController controller = new TestController(true);

        controller.onModeStart(MODE_50HZ);

        assertEquals(Collections.singletonList(false), mPlayWhenReady);

        controller.onModeEnd();

        assertEquals(Arrays.asList(false, true), mPlayWhenReady);
    }

    @Test
    public void theHoldEndsWhenTheDisplayNeverReportsTheSwitch() throws Exception {
        TestController controller = new TestController(true);

        controller.onModeStart(MODE_50HZ);
        ShadowLooper.idleMainLooper(5, TimeUnit.SECONDS);

        assertEquals(Arrays.asList(false, true), mPlayWhenReady);
    }

    @Test
    public void aCanceledSwitchEndsTheHold() throws Exception {
        TestController controller = new TestController(true);

        controller.onModeStart(MODE_50HZ);
        controller.onModeCancel();

        assertEquals(Arrays.asList(false, true), mPlayWhenReady);
    }

    @Test
    public void aPausedVideoStaysPausedAfterTheSwitch() throws Exception {
        TestController controller = new TestController(false);

        controller.onModeStart(MODE_50HZ);
        controller.onModeEnd();

        assertEquals(Arrays.asList(false, false), mPlayWhenReady);
    }

    @Test
    public void aPauseChosenByTheUserIsKept() throws Exception {
        getPlayerData().setAfrPauseMs(1_000);
        TestController controller = new TestController(true);

        controller.onModeStart(MODE_50HZ);
        controller.onModeEnd();

        assertEquals(Collections.singletonList(false), mPlayWhenReady);
    }

    private static PlayerData getPlayerData() {
        return PlayerData.instance(RuntimeEnvironment.getApplication());
    }

    private class TestController extends AutoFrameRateController {
        private final PlaybackView mPlayer = recordingPlayer();

        private TestController(boolean playEnabled) throws Exception {
            setAltContext(RuntimeEnvironment.getApplication());

            VideoStateController stateController = new VideoStateController();
            stateController.setPlayEnabled(playEnabled);
            Field field = AutoFrameRateController.class.getDeclaredField("mStateController");
            field.setAccessible(true);
            field.set(this, stateController);
        }

        @Override
        public PlaybackView getPlayer() {
            return mPlayer;
        }
    }

    /**
     * Records setPlayWhenReady, returns the defaults (false, 0, null) for every call
     */
    private PlaybackView recordingPlayer() {
        return (PlaybackView) Proxy.newProxyInstance(PlaybackView.class.getClassLoader(), new Class<?>[]{PlaybackView.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setPlayWhenReady")) {
                        mPlayWhenReady.add((Boolean) args[0]);
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
