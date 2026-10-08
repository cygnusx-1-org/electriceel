package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData.ColorScheme;
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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MainUIDataTest {
    private static final String DATA_KEY = "main_ui_data2";
    private static final Account FIRST_ACCOUNT = TestAccounts.FIRST;
    private static final Account SECOND_ACCOUNT = TestAccounts.SECOND;

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

    /**
     * The shared settings are the first account's (e.g. from before each account had its own): an added account doesn't get them
     */
    @Test
    public void addedAccountStartsFromTheDefaultColorScheme() {
        AppPrefs prefs = getPrefs();
        MainUIData data = getData();
        int defaultId = data.getColorScheme().id;

        prefs.setData(DATA_KEY, sharedSettings(getOtherScheme(data, defaultId)));
        prefs.onAccountChanged(SECOND_ACCOUNT);

        assertEquals(defaultId, data.getColorScheme().id);
    }

    /**
     * SmartTube's backup or a version before this one: the shared settings are the account's they were made with
     */
    @Test
    public void accountTheSharedSettingsWereMadeWithKeepsThem() {
        AppPrefs prefs = getPrefs();
        MainUIData data = getData();
        int defaultId = data.getColorScheme().id;
        ColorScheme picked = getOtherScheme(data, defaultId);

        prefs.setData(DATA_KEY, sharedSettings(picked));
        prefs.onAccountChanged(FIRST_ACCOUNT);
        prefs.initSharedSettingsProfile(); // the app starts with these settings
        prefs.onAccountChanged(FIRST_ACCOUNT);

        assertEquals(picked.id, data.getColorScheme().id);

        prefs.onAccountChanged(SECOND_ACCOUNT);

        assertEquals(defaultId, data.getColorScheme().id);

        // Copied to the account's own: a later change of the shared ones doesn't reach it
        prefs.setData(DATA_KEY, sharedSettings(getOtherScheme(data, defaultId, picked.id)));
        prefs.onAccountChanged(FIRST_ACCOUNT);

        assertEquals(picked.id, data.getColorScheme().id);
    }

    /**
     * "Use separate settings per each account" was off: every account used the shared settings, and keeps them
     */
    @Test
    public void sharedSettingsInUseStayWithEachAccount() {
        AppPrefs prefs = getPrefs();
        MainUIData data = getData();
        int defaultId = data.getColorScheme().id;
        ColorScheme picked = getOtherScheme(data, defaultId);
        String stale = sharedSettings(getOtherScheme(data, defaultId, picked.id));

        prefs.setData(DATA_KEY, sharedSettings(picked));
        prefs.setData(FIRST_ACCOUNT.getName() + "_" + DATA_KEY, stale); // from before the switch was turned off
        prefs.setData(SECOND_ACCOUNT.getName() + "_" + DATA_KEY, stale);
        prefs.putBoolean("multi_profiles", false);

        prefs.migrateSharedSettings(); // the app starts with these settings

        prefs.onAccountChanged(FIRST_ACCOUNT);

        assertEquals(picked.id, data.getColorScheme().id);

        prefs.onAccountChanged(SECOND_ACCOUNT);

        assertEquals(picked.id, data.getColorScheme().id);
    }

    @Test
    public void eachAccountHasItsOwnColorScheme() {
        AppPrefs prefs = getPrefs();
        MainUIData data = getData();
        int defaultId = data.getColorScheme().id;
        ColorScheme picked = getOtherScheme(data, defaultId);

        prefs.onAccountChanged(FIRST_ACCOUNT);
        data.setColorScheme(picked);
        ShadowLooper.shadowMainLooper().idle();

        prefs.onAccountChanged(SECOND_ACCOUNT);

        assertEquals(defaultId, data.getColorScheme().id);

        prefs.onAccountChanged(FIRST_ACCOUNT);

        assertEquals(picked.id, data.getColorScheme().id);
    }

    @Test
    public void copiedSettingsReplaceTheAccountsOwn() {
        AppPrefs prefs = getPrefs();
        MainUIData data = getData();
        int defaultId = data.getColorScheme().id;
        ColorScheme picked = getOtherScheme(data, defaultId);
        ColorScheme other = getOtherScheme(data, defaultId, picked.id);

        prefs.onAccountChanged(FIRST_ACCOUNT);
        data.setColorScheme(picked);
        ShadowLooper.shadowMainLooper().idle();
        prefs.onAccountChanged(SECOND_ACCOUNT);
        data.setColorScheme(other); // not saved yet

        prefs.copyProfileData(FIRST_ACCOUNT);
        ShadowLooper.shadowMainLooper().idle();

        assertEquals(picked.id, data.getColorScheme().id);

        prefs.onAccountChanged(FIRST_ACCOUNT);

        assertEquals(picked.id, data.getColorScheme().id);

        prefs.onAccountChanged(SECOND_ACCOUNT); // saved too

        assertEquals(picked.id, data.getColorScheme().id);
    }

    @Test
    public void copiedFromAccountWithoutItsOwnGivesTheDefaults() {
        AppPrefs prefs = getPrefs();
        MainUIData data = getData();
        int defaultId = data.getColorScheme().id;

        prefs.onAccountChanged(SECOND_ACCOUNT);
        data.setColorScheme(getOtherScheme(data, defaultId));
        ShadowLooper.shadowMainLooper().idle();

        prefs.copyProfileData(FIRST_ACCOUNT);

        assertEquals(defaultId, data.getColorScheme().id);
    }

    @Test
    public void copiedFromTheAccountTheSharedSettingsWereMadeWith() {
        AppPrefs prefs = getPrefs();
        MainUIData data = getData();
        ColorScheme picked = getOtherScheme(data, data.getColorScheme().id);

        prefs.setData(DATA_KEY, sharedSettings(picked));
        prefs.onAccountChanged(FIRST_ACCOUNT);
        prefs.initSharedSettingsProfile(); // the app starts with these settings

        // The first account never read its settings since: none of them copied to its own yet
        prefs.onAccountChanged(SECOND_ACCOUNT);
        prefs.copyProfileData(FIRST_ACCOUNT);

        assertEquals(picked.id, data.getColorScheme().id);
    }

    /**
     * Its settings are all the copied ones: none of the shared ones come back
     */
    @Test
    public void copiedIntoTheAccountTheSharedSettingsWereMadeWith() {
        AppPrefs prefs = getPrefs();
        MainUIData data = getData();
        int defaultId = data.getColorScheme().id;
        ColorScheme picked = getOtherScheme(data, defaultId);

        prefs.setData(DATA_KEY, sharedSettings(picked));
        prefs.onAccountChanged(SECOND_ACCOUNT);
        prefs.initSharedSettingsProfile(); // the app starts with these settings
        prefs.onAccountChanged(SECOND_ACCOUNT);

        assertEquals(picked.id, data.getColorScheme().id);

        prefs.copyProfileData(FIRST_ACCOUNT);

        assertEquals(defaultId, data.getColorScheme().id);
    }

    /**
     * Saved like MainUIData does, the values after the color scheme left out: the defaults
     */
    private static String sharedSettings(ColorScheme scheme) {
        return Helpers.mergeData(null, 1.0f, 1.0f, scheme.id);
    }

    private static ColorScheme getOtherScheme(MainUIData data, int... ids) {
        for (ColorScheme scheme : data.getColorSchemes()) {
            if (!contains(ids, scheme.id)) {
                return scheme;
            }
        }

        throw new AssertionError("No other color scheme");
    }

    private static boolean contains(int[] ids, int id) {
        for (int item : ids) {
            if (item == id) {
                return true;
            }
        }

        return false;
    }

    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), DATA_KEY);
        MainUIData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static MainUIData getData() {
        return MainUIData.instance(RuntimeEnvironment.getApplication());
    }
}
