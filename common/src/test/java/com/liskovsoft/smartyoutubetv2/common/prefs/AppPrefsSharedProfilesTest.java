package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The accounts with the same name shared the profile named after them: each has its own now (see Account.getProfileName)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AppPrefsSharedProfilesTest {
    private static final String FIRST_PROFILE = "Nathan_Grennan";
    private static final String SECOND_PROFILE = "Nathan_Grennan#2";
    private static final String THIRD_PROFILE = "Nathan_Grennan#3";
    private static final Account FIRST = TestAccounts.withProfile("Nathan Grennan", "first@example.com", FIRST_PROFILE, null);
    /**
     * It shared the first one's
     */
    private static final Account SECOND = TestAccounts.withProfile("Nathan Grennan", "second@example.com", SECOND_PROFILE, FIRST_PROFILE);
    /**
     * Signed in after each account had its own
     */
    private static final Account THIRD = TestAccounts.withProfile("Nathan Grennan", "third@example.com", THIRD_PROFILE, null);
    private static final List<Account> ACCOUNTS = Arrays.asList(FIRST, SECOND, THIRD);
    private static final String[] KEYS = {
            GeneralData.GENERAL_DATA, "sidebar_data", "state_updater_data", "home_screen_playlists_data", "home_screen_channels_data"
    }; // see AppPrefs

    @Before
    public void setUp() {
        getPrefs().onAccountChanged(null); // the anonymous account
        getPrefs().resetSharedSettingsProfileForTesting();
        clearSaved();
    }

    @After
    public void tearDown() {
        Utils.sHandler.removeCallbacksAndMessages(null);
        getPrefs().onAccountChanged(null);
        getPrefs().resetSharedSettingsProfileForTesting();
        clearSaved();
    }

    @Test
    public void sharedProfileCopiedToTheAccountsOwn() {
        AppPrefs prefs = getPrefs();
        prefs.onAccountChanged(FIRST);
        prefs.setProfileData(GeneralData.GENERAL_DATA, "general");
        prefs.setSidebarData("sidebar");
        prefs.setData(prefs.getStateUpdaterKey(), "history");

        prefs.copySharedProfiles(ACCOUNTS);
        prefs.onAccountChanged(SECOND);

        assertEquals("general", prefs.getProfileData(GeneralData.GENERAL_DATA));
        assertEquals("sidebar", prefs.getSidebarData());
        assertEquals("history", prefs.getStateUpdaterData());
    }

    /**
     * Each has its own from then on
     */
    @Test
    public void copiedOnce() {
        AppPrefs prefs = getPrefs();
        prefs.onAccountChanged(FIRST);
        prefs.setSidebarData("before");

        prefs.copySharedProfiles(ACCOUNTS);
        prefs.setSidebarData("after");
        prefs.copySharedProfiles(ACCOUNTS);
        prefs.onAccountChanged(SECOND);

        assertEquals("before", prefs.getSidebarData());

        prefs.setSidebarData("second's");
        prefs.onAccountChanged(FIRST);

        assertEquals("after", prefs.getSidebarData());
    }

    /**
     * The launcher would show each of the channels twice: the first account keeps them
     */
    @Test
    public void homeScreenChannelsNotCopied() {
        AppPrefs prefs = getPrefs();
        prefs.onAccountChanged(FIRST);
        prefs.setHomeScreenPlaylistsData("playlists");
        prefs.setHomeScreenChannelsData("channels");

        prefs.copySharedProfiles(ACCOUNTS);
        prefs.onAccountChanged(SECOND);

        assertTrue(TextUtils.isEmpty(prefs.getHomeScreenPlaylistsData()));
        assertTrue(TextUtils.isEmpty(prefs.getHomeScreenChannelsData()));
        assertEquals("playlists", prefs.getHomeScreenPlaylistsData(FIRST));
    }

    /**
     * It never shared one: it starts from the defaults
     */
    @Test
    public void accountThatNeverSharedStartsFromTheDefaults() {
        AppPrefs prefs = getPrefs();
        prefs.onAccountChanged(FIRST);
        prefs.setSidebarData("first's");

        prefs.copySharedProfiles(ACCOUNTS);
        prefs.onAccountChanged(THIRD);

        assertTrue(TextUtils.isEmpty(prefs.getSidebarData()));
    }

    /**
     * The settings of each are kept under its own profile, not under the name they have in common
     */
    @Test
    public void sameNameAccountsHaveTheirOwn() {
        AppPrefs prefs = getPrefs();
        prefs.onAccountChanged(FIRST);
        prefs.setSidebarData("first's");
        prefs.onAccountChanged(THIRD);
        prefs.setSidebarData("third's");

        assertEquals("first's", prefs.getData(FIRST_PROFILE + "_sidebar_data"));
        assertEquals("third's", prefs.getData(THIRD_PROFILE + "_sidebar_data"));
    }

    private static void clearSaved() {
        AppPrefs prefs = getPrefs();
        prefs.putString("copied_profile_names", null); // see AppPrefs

        for (String profileName : Arrays.asList(FIRST_PROFILE, SECOND_PROFILE, THIRD_PROFILE)) {
            for (String key : KEYS) {
                prefs.setData(profileName + "_" + key, "");
            }
        }
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }
}
