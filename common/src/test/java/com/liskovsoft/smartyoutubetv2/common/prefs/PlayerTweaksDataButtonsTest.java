package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;
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

/**
 * The Timestamps button is on by default: for the buttons saved before it came too, but only once
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PlayerTweaksDataButtonsTest {
    private static final Account FIRST_ACCOUNT = TestAccounts.FIRST;
    private static final Account SECOND_ACCOUNT = TestAccounts.SECOND;
    /**
     * Saved by a version before the Timestamps button: the defaults of then, without the button
     */
    private static final int OLD_BUTTONS = PlayerTweaksData.PLAYER_BUTTON_DEFAULT & ~PlayerTweaksData.PLAYER_BUTTON_TIMESTAMPS;
    private static final int BUTTONS_INDEX = 13;

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
    public void onForANewUser() {
        assertTrue(getData().isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_TIMESTAMPS));
        // Only the defaults: nothing to save (the storage sync would take the defaults for changes made here)
        assertTrue(TextUtils.isEmpty(getPrefs().getProfileData(PlayerTweaksData.VIDEO_PLAYER_TWEAKS_DATA)));
    }

    @Test
    public void turnedOnForTheSavedButtons() {
        saveButtons(OLD_BUTTONS | PlayerTweaksData.PLAYER_BUTTON_NEXT_FRAME);

        PlayerTweaksData data = getData();

        assertTrue(data.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_TIMESTAMPS));
        // The others stay as they were
        assertTrue(data.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_NEXT_FRAME));
        assertFalse(data.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_PLAYBACK_QUEUE));
        // Saved right away
        assertEquals(OLD_BUTTONS | PlayerTweaksData.PLAYER_BUTTON_NEXT_FRAME | PlayerTweaksData.PLAYER_BUTTON_TIMESTAMPS, savedButtons());
    }

    @Test
    public void turnedOffStaysOff() {
        saveButtons(OLD_BUTTONS);
        getData().setPlayerButtonDisabled(PlayerTweaksData.PLAYER_BUTTON_TIMESTAMPS);
        restart();

        assertFalse(getData().isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_TIMESTAMPS));
    }

    /**
     * A new user that turns it off: the defaults had it, so it was never turned on, but it's marked as added all the same
     */
    @Test
    public void turnedOffByANewUserStaysOff() {
        getData().setPlayerButtonDisabled(PlayerTweaksData.PLAYER_BUTTON_TIMESTAMPS);
        restart();

        assertFalse(getData().isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_TIMESTAMPS));
    }

    @Test
    public void eachAccountTurnsItOnOnce() {
        AppPrefs prefs = getPrefs();

        prefs.onAccountChanged(FIRST_ACCOUNT);
        saveButtons(OLD_BUTTONS);
        getData().setPlayerButtonDisabled(PlayerTweaksData.PLAYER_BUTTON_TIMESTAMPS);
        ShadowLooper.shadowMainLooper().idle(); // saved before the account changes

        prefs.onAccountChanged(SECOND_ACCOUNT);
        saveButtons(OLD_BUTTONS);

        // Turned on for this one too, though the other one turned it off
        assertTrue(getData().isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_TIMESTAMPS));

        prefs.onAccountChanged(FIRST_ACCOUNT);

        assertFalse(getData().isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_TIMESTAMPS));
    }

    /**
     * Copying another account's settings copies whether it was turned on already: turned off there, it stays off, though this
     * account's settings are from a version before the button
     */
    @Test
    public void copiedSettingsKeepItOff() {
        AppPrefs prefs = getPrefs();

        prefs.onAccountChanged(FIRST_ACCOUNT);
        getData().setPlayerButtonDisabled(PlayerTweaksData.PLAYER_BUTTON_TIMESTAMPS);
        ShadowLooper.shadowMainLooper().idle(); // saved before the account changes

        prefs.onAccountChanged(SECOND_ACCOUNT);
        saveButtons(OLD_BUTTONS);
        prefs.setProfileData(PlayerTweaksData.PLAYER_BUTTONS_ADDED_DATA, "");
        prefs.copyProfileData(FIRST_ACCOUNT);

        assertFalse(getData().isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_TIMESTAMPS));
    }

    /**
     * The buttons of the current account, as a version before the button saved them: not marked as added
     */
    private static void saveButtons(int buttons) {
        String[] values = new String[BUTTONS_INDEX + 1];
        values[BUTTONS_INDEX] = String.valueOf(buttons);
        getPrefs().setProfileData(PlayerTweaksData.VIDEO_PLAYER_TWEAKS_DATA, Helpers.mergeData((Object[]) values));
        getPrefs().setProfileData(PlayerTweaksData.PLAYER_BUTTONS_ADDED_DATA, "");
        restart();
    }

    private static int savedButtons() {
        String[] split = Helpers.splitData(getPrefs().getProfileData(PlayerTweaksData.VIDEO_PLAYER_TWEAKS_DATA));
        return Helpers.parseInt(split, BUTTONS_INDEX, -1);
    }

    private static void restart() {
        ShadowLooper.shadowMainLooper().idle();
        PlayerTweaksData.resetInstanceForTesting();
    }

    private static void clearSaved() {
        AppPrefs prefs = getPrefs();
        TestAccounts.clearSaved(prefs, PlayerTweaksData.VIDEO_PLAYER_TWEAKS_DATA);
        TestAccounts.clearSaved(prefs, PlayerTweaksData.PLAYER_BUTTONS_ADDED_DATA);
        PlayerTweaksData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static PlayerTweaksData getData() {
        return PlayerTweaksData.instance(RuntimeEnvironment.getApplication());
    }
}
