package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlayerFailure;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlayerFailureLog;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A live stream a client answered OK for with nothing to play it from (e.g. TV's for some music channels, SmartTube #6393)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoLoaderControllerTest {
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();
    private PlayerFailureLog mLog;
    private int mSavedEngine;

    @Before
    public void setUp() {
        mLog = PlayerFailureLog.useForTesting(mFolder.getRoot());
        // Cronet can't load under Robolectric
        mSavedEngine = PlayerTweaksData.instance(RuntimeEnvironment.getApplication()).getPlayerDataSource();
        PlayerTweaksData.instance(RuntimeEnvironment.getApplication()).setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP);
    }

    @After
    public void tearDown() {
        PlayerFailureLog.useForTesting(null);
        PlayerTweaksData.instance(RuntimeEnvironment.getApplication()).setPlayerDataSource(mSavedEngine);
    }

    /**
     * The clients after it are tried, a few: then it's only reloaded, as before
     */
    @Test
    public void aLiveStreamWithNothingToPlaySwitchesTheClientAFewTimes() throws Exception {
        TestController controller = new TestController(video("WsDyRAPFBC8"));
        MediaItemFormatInfo live = formatInfo(true, "OK");

        assertTrue(isLiveClientToSwitch(controller, live));
        assertTrue(isLiveClientToSwitch(controller, live));
        assertTrue(isLiveClientToSwitch(controller, live));
        assertFalse(isLiveClientToSwitch(controller, live));

        // Another live stream: its own tries
        assertTrue(isLiveClientToSwitch(new TestController(video("d8Oc90QevaI")), live));
    }

    /**
     * Not live (e.g. a premiere yet to start), or not playable as the client says: no other client has it either
     */
    @Test
    public void onlyALiveStreamThatPlaysSwitchesTheClient() throws Exception {
        TestController controller = new TestController(video("upcoming"));

        assertFalse(isLiveClientToSwitch(controller, formatInfo(false, "OK")));
        assertFalse(isLiveClientToSwitch(controller, formatInfo(true, "LIVE_STREAM_OFFLINE")));
    }

    /**
     * What the answer had, of what the player plays from: tells TV's SABR-only answer from an empty one
     */
    @Test
    public void anEmptyFormatsFailureSaysWhatTheFormatsHad() throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put("isLive", true);
        values.put("getStartTimeMs", 0L);
        values.put("containsSabrFormats", true);
        values.put("getServerAbrStreamingUrl", "https://rr1---sn-test.googlevideo.com/videoplayback");
        PlayerFailure failure = PlayerFailure.create(RuntimeEnvironment.getApplication(), PlayerFailure.KIND_EMPTY_FORMATS);

        Method method = VideoLoaderController.class.getDeclaredMethod("addMediaDetails", PlayerFailure.class, MediaItemFormatInfo.class);
        method.setAccessible(true);
        method.invoke(null, failure, formatInfo(values));
        failure.record();

        JSONObject details = mLog.readAll().get(0).getJSONObject("details");
        assertTrue(details.getBoolean("live"));
        assertEquals(0, details.getLong("startTimeMs"));
        assertTrue(details.getBoolean("sabrFormats"));
        assertFalse(details.getBoolean("dashFormats"));
        assertFalse(details.getBoolean("hlsManifest"));
        assertFalse(details.getBoolean("dashManifest"));
        assertFalse(details.getBoolean("urlFormats"));
        assertTrue(details.getBoolean("serverAbrUrl"));
    }

    private static boolean isLiveClientToSwitch(VideoLoaderController controller, MediaItemFormatInfo formatInfo) throws Exception {
        Method method = VideoLoaderController.class.getDeclaredMethod("isLiveClientToSwitch", MediaItemFormatInfo.class);
        method.setAccessible(true);

        return (boolean) method.invoke(controller, formatInfo);
    }

    private static MediaItemFormatInfo formatInfo(boolean isLive, String status) {
        Map<String, Object> values = new HashMap<>();
        values.put("isLive", isLive);
        values.put("getPlayabilityStatus", status);

        return formatInfo(values);
    }

    /**
     * By method name; the defaults (false, 0, null) for the others
     */
    private static MediaItemFormatInfo formatInfo(Map<String, Object> values) {
        return (MediaItemFormatInfo) Proxy.newProxyInstance(MediaItemFormatInfo.class.getClassLoader(),
                new Class<?>[]{MediaItemFormatInfo.class}, (proxy, method, args) -> {
                    if (values.containsKey(method.getName())) {
                        return values.get(method.getName());
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
                    }

                    return null;
                });
    }

    private static Video video(String videoId) {
        Video video = new Video();
        video.videoId = videoId;
        return video;
    }

    private static class TestController extends VideoLoaderController {
        private final Video mVideo;

        private TestController(Video video) {
            mVideo = video;
        }

        @Override
        public Video getVideo() {
            return mVideo;
        }
    }
}
