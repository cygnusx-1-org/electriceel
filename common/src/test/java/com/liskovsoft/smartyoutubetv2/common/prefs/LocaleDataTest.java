package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LocaleDataTest {
    private static final String DATA_KEY = "locale_data";

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
     * The language and country were for all the accounts before: kept as the shared ones, for the account they were made with
     */
    @Test
    public void earlierValuesAreTheSharedOnes() {
        AppPrefs prefs = getPrefs();
        getGlobalPrefs().setPreferredLanguage("de");
        getGlobalPrefs().setPreferredCountry("DE");

        LocaleData data = getData();
        prefs.initSharedSettingsProfile(); // the app starts with these settings: the anonymous account's

        prefs.onAccountChanged(null);

        assertEquals("de", data.getLanguage());
        assertEquals("DE", data.getCountry());

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertEquals("", data.getLanguage());
        assertEquals("", data.getCountry());
    }

    @Test
    public void eachAccountHasItsOwnLanguageAndCountry() {
        AppPrefs prefs = getPrefs();
        LocaleData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.setLanguage("fr");
        data.setCountry("FR");

        prefs.onAccountChanged(TestAccounts.SECOND);

        // Starts from the system ones, not the other account's
        assertEquals("", data.getLanguage());
        assertEquals("", data.getCountry());

        data.setLanguage("ja");
        data.setCountry("");

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertEquals("fr", data.getLanguage());
        assertEquals("FR", data.getCountry());
        assertEquals("fr", getGlobalPrefs().getPreferredLanguage()); // the ones in use

        prefs.onAccountChanged(TestAccounts.SECOND);

        assertEquals("ja", data.getLanguage());
        assertEquals("", data.getCountry());
        assertEquals("ja", getGlobalPrefs().getPreferredLanguage());
    }

    /**
     * Empty is the system one, it survives saving next to a set value
     */
    @Test
    public void systemLanguageIsKept() {
        AppPrefs prefs = getPrefs();
        LocaleData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.setLanguage("");
        data.setCountry("US");

        prefs.onAccountChanged(TestAccounts.SECOND);
        data.setCountry("GB");

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertEquals("", data.getLanguage());
        assertEquals("US", data.getCountry());
    }

    /**
     * AppPrefs and GlobalPreferences outlive the test, so do the saved values
     */
    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), DATA_KEY);
        getGlobalPrefs().setPreferredLanguage("");
        getGlobalPrefs().setPreferredCountry("");
        LocaleData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static GlobalPreferences getGlobalPrefs() {
        return GlobalPreferences.instance(RuntimeEnvironment.getApplication());
    }

    private static LocaleData getData() {
        return LocaleData.instance(RuntimeEnvironment.getApplication());
    }
}
