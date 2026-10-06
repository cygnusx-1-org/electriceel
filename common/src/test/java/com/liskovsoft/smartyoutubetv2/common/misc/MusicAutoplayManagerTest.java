package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.prefs.MusicAutoplayData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MusicAutoplayManagerTest {
    private static final String MUSIC_ID = "autoplayMusic";
    private static final String MUSIC_MIX_ID = "autoplayMusicMix"; // People & Blogs, YouTube's topics say music
    private static final String VLOG_ID = "autoplayVlog";
    private static final String UNKNOWN_ID = "autoplayUnknown";
    private int mPlaybackMode;
    private boolean mIsEnabled;
    private int mMusicPlaybackMode;

    @Before
    public void setUp() {
        Context context = getContext();
        GlobalPreferences.instance(context);
        // AppPrefs outlives the test, so do the saved values
        mPlaybackMode = PlayerData.instance(context).getPlaybackMode();
        mIsEnabled = getData().isEnabled();
        mMusicPlaybackMode = getData().getMusicPlaybackMode();

        MediaServiceData.instance().setDataApiKey("key");
        PlayerData.instance(context).setPlaybackMode(PlayerConstants.PLAYBACK_MODE_PAUSE);
        getData().setEnabled(true);
        getData().setMusicPlaybackMode(PlayerConstants.PLAYBACK_MODE_ALL);

        VideoCategoryManager manager = VideoCategoryManager.instance(context);
        manager.setCategoryForTesting(MUSIC_ID, "Music");
        manager.setCategoryForTesting(MUSIC_MIX_ID, "People & Blogs", Arrays.asList("Electronic_music", "Music"));
        manager.setCategoryForTesting(VLOG_ID, "People & Blogs", Arrays.asList("Lifestyle_(sociology)"));
    }

    @After
    public void tearDown() {
        PlayerData.instance(getContext()).setPlaybackMode(mPlaybackMode);
        getData().setEnabled(mIsEnabled);
        getData().setMusicPlaybackMode(mMusicPlaybackMode);
        MediaServiceData.instance().setDataApiKey(null);
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void musicVideosHaveTheirOwnMode() {
        assertEquals(PlayerConstants.PLAYBACK_MODE_ALL, getMode(MUSIC_ID));
        assertEquals(PlayerConstants.PLAYBACK_MODE_ALL, getMode(MUSIC_MIX_ID));
        assertEquals(PlayerConstants.PLAYBACK_MODE_PAUSE, getMode(VLOG_ID));
        // Until its category is known
        assertEquals(PlayerConstants.PLAYBACK_MODE_PAUSE, getMode(UNKNOWN_ID));
        // E.g. the Playback mode card of the settings
        assertEquals(PlayerConstants.PLAYBACK_MODE_PAUSE, MusicAutoplayManager.getPlaybackMode(getContext(), null));
    }

    @Test
    public void oneModeForEveryVideoWhileOff() {
        getData().setEnabled(false);

        assertFalse(MusicAutoplayManager.isMusicVideo(getContext(), createVideo(MUSIC_ID)));
        assertEquals(PlayerConstants.PLAYBACK_MODE_PAUSE, getMode(MUSIC_ID));

        setMode(MUSIC_ID, PlayerConstants.PLAYBACK_MODE_ONE);

        assertEquals(PlayerConstants.PLAYBACK_MODE_ONE, getMode(VLOG_ID));
        assertEquals(PlayerConstants.PLAYBACK_MODE_ALL, getData().getMusicPlaybackMode());
    }

    @Test
    public void oneModeForEveryVideoWithoutKey() {
        MediaServiceData.instance().setDataApiKey(null);

        assertEquals(PlayerConstants.PLAYBACK_MODE_PAUSE, getMode(MUSIC_ID));

        // The values are kept: entering a key brings them back
        MediaServiceData.instance().setDataApiKey("key");

        assertEquals(PlayerConstants.PLAYBACK_MODE_ALL, getMode(MUSIC_ID));
    }

    @Test
    public void buttonChangesTheModeOfItsVideoType() {
        setMode(MUSIC_ID, PlayerConstants.PLAYBACK_MODE_ONE);

        assertEquals(PlayerConstants.PLAYBACK_MODE_ONE, getMode(MUSIC_MIX_ID));
        assertEquals(PlayerConstants.PLAYBACK_MODE_PAUSE, getMode(VLOG_ID));

        setMode(VLOG_ID, PlayerConstants.PLAYBACK_MODE_CLOSE);

        assertEquals(PlayerConstants.PLAYBACK_MODE_ONE, getMode(MUSIC_ID));
        assertEquals(PlayerConstants.PLAYBACK_MODE_CLOSE, getMode(VLOG_ID));
        assertTrue(MusicAutoplayManager.isMusicVideo(getContext(), createVideo(MUSIC_ID)));
    }

    private static int getMode(String videoId) {
        return MusicAutoplayManager.getPlaybackMode(getContext(), createVideo(videoId));
    }

    private static void setMode(String videoId, int mode) {
        MusicAutoplayManager.setPlaybackMode(getContext(), createVideo(videoId), mode);
    }

    private static Video createVideo(String videoId) {
        Video video = new Video();
        video.videoId = videoId;
        return video;
    }

    private static MusicAutoplayData getData() {
        return MusicAutoplayData.instance(getContext());
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }
}
