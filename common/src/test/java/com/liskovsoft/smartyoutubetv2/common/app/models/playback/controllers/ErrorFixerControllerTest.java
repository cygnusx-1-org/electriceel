package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlayerFailureLog;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.videoinfo.V2.ClientAttempts;

import org.json.JSONArray;
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

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ErrorFixerControllerTest {
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();
    private PlayerFailureLog mLog;
    private int mSavedEngine;

    @Before
    public void setUp() {
        mLog = PlayerFailureLog.useForTesting(mFolder.getRoot());
        // Cronet can't load under Robolectric
        mSavedEngine = getTweaks().getPlayerDataSource();
        getTweaks().setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP);
    }

    @After
    public void tearDown() {
        PlayerFailureLog.useForTesting(null);
        getTweaks().setPlayerDataSource(mSavedEngine);
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    /**
     * A 403 of a stream that never played: the next client right away, then the video again
     */
    @Test
    public void aSourceErrorBeforePlayingRecordsTheClientSwitch() throws Exception {
        TestController controller = new TestController(video("dQw4w9WgXcQ"));

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new IOException("Response code: 403"));

        List<JSONObject> events = mLog.readAll();

        assertEquals(1, events.size());

        JSONObject event = events.get(0);
        assertEquals("engine_error", event.getString("kind"));
        assertEquals("dQw4w9WgXcQ", event.getString("videoId"));
        assertEquals(false, event.getBoolean("startedPlaying"));
        assertEquals("SOURCE", event.getJSONObject("error").getString("type"));
        assertEquals(-1, event.getJSONObject("error").getInt("rendererIndex"));
        assertEquals("IOException", event.getJSONObject("error").getString("class"));
        assertEquals("Response code: 403", event.getJSONObject("error").getString("message"));
        assertEquals("okhttp", event.getJSONObject("player").getString("engineSelected"));

        JSONArray actions = event.getJSONArray("actions");
        assertEquals(2, actions.length());
        assertEquals("switch_next_client_now", actions.getString(0));
        assertEquals("reload_video", actions.getString(1));
    }

    /**
     * Every client said it can't be played: unplayable with its why, tried again in 30 s (not each second)
     */
    @Test
    public void unplayableOnEveryClientIsUnplayable() throws Exception {
        ClientAttempts.clear("removed");
        ClientAttempts.record("removed", ClientAttempts.Attempt.unplayable("TV", "ERROR", "This video is unavailable"));
        ClientAttempts.record("removed", ClientAttempts.Attempt.unplayable("WEB", "ERROR", "This video is unavailable"));
        ClientAttempts.record("removed", ClientAttempts.Attempt.nullResponse("INNERTUBE"));
        TestController controller = new TestController(video("removed"));

        controller.runFormatErrorAction(new IllegalStateException("fromNullable result is null"));

        JSONObject event = mLog.readAll().get(0);
        assertEquals("unplayable", event.getString("kind"));
        assertEquals("ERROR", event.getJSONObject("playability").getString("status"));
        assertEquals("This video is unavailable", event.getJSONObject("playability").getString("reason"));
        assertEquals("reload_video_30s", event.getJSONArray("actions").getString(0));
    }

    /**
     * One client failed otherwise (e.g. no network): still a format error
     */
    @Test
    public void unplayableOnSomeClientsIsAFormatError() throws Exception {
        ClientAttempts.clear("offline");
        ClientAttempts.record("offline", ClientAttempts.Attempt.unplayable("TV", "ERROR", "This video is unavailable"));
        ClientAttempts.record("offline", ClientAttempts.Attempt.exception("WEB", new IllegalStateException("timeout")));
        TestController controller = new TestController(video("offline"));

        controller.runFormatErrorAction(new IllegalStateException("fromNullable result is null"));

        assertEquals("format_error", mLog.readAll().get(0).getString("kind"));
    }

    private static Video video(String videoId) {
        Video video = new Video();
        video.videoId = videoId;
        return video;
    }

    private static PlayerTweaksData getTweaks() {
        return PlayerTweaksData.instance(RuntimeEnvironment.getApplication());
    }

    /**
     * Returns the defaults (false, 0, null) for every call
     */
    private static PlaybackView playerStub() {
        return (PlaybackView) Proxy.newProxyInstance(PlaybackView.class.getClassLoader(), new Class<?>[]{PlaybackView.class},
                (proxy, method, args) -> {
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

    private static class TestController extends ErrorFixerController {
        private final PlaybackView mPlayer = playerStub();
        private final Video mVideo;

        private TestController(Video video) throws Exception {
            mVideo = video;
            setAltContext(RuntimeEnvironment.getApplication());

            // Without a player: reloads and restarts do nothing
            Field loader = ErrorFixerController.class.getDeclaredField("mVideoLoaderController");
            loader.setAccessible(true);
            loader.set(this, new VideoLoaderController());
        }

        @Override
        public PlaybackView getPlayer() {
            return mPlayer;
        }

        @Override
        public Video getVideo() {
            return mVideo;
        }
    }
}
