package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MusicAutoplayDataTest {
    private static final String ANONYMOUS_PROFILE_KEY = "anonymous_" + MusicAutoplayData.class.getSimpleName();

    @Before
    public void setUp() {
        getPrefs().enableMultiProfiles(false);
        clearSaved();
    }

    @After
    public void tearDown() {
        Utils.sHandler.removeCallbacksAndMessages(null);
        getPrefs().enableMultiProfiles(false);
        clearSaved();
    }

    @Test
    public void offByDefaultWithMusicPlayingContinuously() {
        assertFalse(getData().isEnabled());
        assertEquals(PlayerConstants.PLAYBACK_MODE_ALL, getData().getMusicPlaybackMode());
    }

    @Test
    public void valuesSurviveRestart() {
        getData().setEnabled(true);
        getData().setMusicPlaybackMode(PlayerConstants.PLAYBACK_MODE_PAUSE);
        ShadowLooper.shadowMainLooper().idle(); // saved right away
        MusicAutoplayData.resetInstanceForTesting();

        assertTrue(getData().isEnabled());
        assertEquals(PlayerConstants.PLAYBACK_MODE_PAUSE, getData().getMusicPlaybackMode());
    }

    /**
     * The values not set yet are saved as "null" when a later one is set
     */
    @Test
    public void enabledStaysOffWhenOnlyTheModeIsSaved() {
        getData().setMusicPlaybackMode(PlayerConstants.PLAYBACK_MODE_ONE);
        ShadowLooper.shadowMainLooper().idle();
        MusicAutoplayData.resetInstanceForTesting();

        assertFalse(getData().isEnabled());
        assertEquals(PlayerConstants.PLAYBACK_MODE_ONE, getData().getMusicPlaybackMode());
    }

    @Test
    public void unknownModeFallsBackToPlayingContinuously() {
        getData().setMusicPlaybackMode(42);

        assertEquals(PlayerConstants.PLAYBACK_MODE_ALL, getData().getMusicPlaybackMode());
    }

    @Test
    public void eachAccountHasItsOwnValue() {
        AppPrefs prefs = getPrefs();
        MusicAutoplayData data = getData();

        data.setEnabled(true);
        ShadowLooper.shadowMainLooper().idle(); // saved before the account changes

        prefs.enableMultiProfiles(true);
        prefs.onAccountChanged(null); // the anonymous account

        // Starts from the shared value
        assertTrue(data.isEnabled());

        data.setEnabled(false);
        ShadowLooper.shadowMainLooper().idle();

        prefs.enableMultiProfiles(false);

        assertTrue(data.isEnabled());

        prefs.enableMultiProfiles(true);

        assertFalse(data.isEnabled());
    }

    /**
     * AppPrefs outlives the test, so do the saved values
     */
    private static void clearSaved() {
        getPrefs().setData(MusicAutoplayData.class.getSimpleName(), "");
        getPrefs().setData(ANONYMOUS_PROFILE_KEY, "");
        MusicAutoplayData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static MusicAutoplayData getData() {
        return MusicAutoplayData.instance(RuntimeEnvironment.getApplication());
    }
}
