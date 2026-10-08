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
    @Before
    public void setUp() {
        getPrefs().onAccountChanged(null); // the anonymous account
        clearSaved();
    }

    @After
    public void tearDown() {
        Utils.sHandler.removeCallbacksAndMessages(null);
        getPrefs().onAccountChanged(null);
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

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.setEnabled(true);
        ShadowLooper.shadowMainLooper().idle(); // saved before the account changes

        prefs.onAccountChanged(TestAccounts.SECOND);

        // Starts from the default, not the other account's value
        assertFalse(data.isEnabled());

        data.setMusicPlaybackMode(PlayerConstants.PLAYBACK_MODE_PAUSE);
        ShadowLooper.shadowMainLooper().idle();

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertTrue(data.isEnabled());
        assertEquals(PlayerConstants.PLAYBACK_MODE_ALL, data.getMusicPlaybackMode());

        prefs.onAccountChanged(TestAccounts.SECOND);

        assertFalse(data.isEnabled());
        assertEquals(PlayerConstants.PLAYBACK_MODE_PAUSE, data.getMusicPlaybackMode());
    }

    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), MusicAutoplayData.class.getSimpleName());
        MusicAutoplayData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static MusicAutoplayData getData() {
        return MusicAutoplayData.instance(RuntimeEnvironment.getApplication());
    }
}
