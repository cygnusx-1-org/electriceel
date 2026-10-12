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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Settings that were for all the accounts become each account's own (see AppPrefs.migrateToProfileData)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AppPrefsMigrateToProfileDataTest {
    private static final String KEY = AiSListFilterData.class.getSimpleName();
    private static final String SIDEBAR_DATA = "sidebar_data"; // see AppPrefs
    private static final String LAST_PROFILE_NAME = "last_profile_name"; // see AppPrefs
    private static final Account ADDED = TestAccounts.named("Added"); // nothing saved under it

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
    public void eachUsedAccountKeepsTheSharedOnes() {
        AppPrefs prefs = getPrefs();
        prefs.onAccountChanged(TestAccounts.FIRST);
        prefs.setSidebarData("first's"); // used before
        prefs.onAccountChanged(TestAccounts.SECOND);
        prefs.setData(KEY, "shared");

        prefs.migrateToProfileData(KEY);

        assertEquals("shared", prefs.getProfileData(KEY));

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertEquals("shared", prefs.getProfileData(KEY));
    }

    @Test
    public void accountsOwnStayTheirs() {
        AppPrefs prefs = getPrefs();
        prefs.onAccountChanged(TestAccounts.FIRST);
        prefs.setProfileData(KEY, "first's");
        prefs.setData(KEY, "shared");

        prefs.migrateToProfileData(KEY);

        assertEquals("first's", prefs.getProfileData(KEY));
    }

    @Test
    public void accountAddedAfterStartsFromTheDefaults() {
        AppPrefs prefs = getPrefs();
        prefs.onAccountChanged(TestAccounts.FIRST);
        prefs.setData(KEY, "shared");

        prefs.migrateToProfileData(KEY);
        prefs.onAccountChanged(ADDED);

        assertTrue(TextUtils.isEmpty(prefs.getProfileData(KEY)));
    }

    /**
     * Copied once: the ones an account changes later aren't replaced at the next start
     */
    @Test
    public void onlyOnce() {
        AppPrefs prefs = getPrefs();
        prefs.onAccountChanged(TestAccounts.FIRST);
        prefs.setData(KEY, "shared");

        prefs.migrateToProfileData(KEY);

        assertTrue(TextUtils.isEmpty(prefs.getData(KEY)));
    }

    /**
     * No account selected yet: the shared ones are the ones in use (see AppPrefs.getProfileKey)
     */
    @Test
    public void keptUntilAnAccountIsSelected() {
        AppPrefs prefs = getPrefs();
        prefs.putString(LAST_PROFILE_NAME, null);
        prefs.setData(KEY, "shared");

        prefs.migrateToProfileData(KEY);

        assertEquals("shared", prefs.getProfileData(KEY));
    }

    private static void clearSaved() {
        AppPrefs prefs = getPrefs();
        TestAccounts.clearSaved(prefs, KEY);
        TestAccounts.clearSaved(prefs, SIDEBAR_DATA);
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }
}
