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
    private static final String ANONYMOUS_PROFILE_KEY = "anonymous_" + DATA_KEY;

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

    /**
     * The language and country were for all the accounts before
     */
    @Test
    public void earlierValuesAreTheSharedOnes() {
        getGlobalPrefs().setPreferredLanguage("de");
        getGlobalPrefs().setPreferredCountry("DE");

        LocaleData data = getData();

        getPrefs().enableMultiProfiles(true);
        getPrefs().onAccountChanged(null); // the anonymous account

        assertEquals("de", data.getLanguage());
        assertEquals("DE", data.getCountry());
    }

    @Test
    public void eachAccountHasItsOwnLanguageAndCountry() {
        AppPrefs prefs = getPrefs();
        LocaleData data = getData();

        data.setLanguage("fr");
        data.setCountry("FR");

        prefs.enableMultiProfiles(true);
        prefs.onAccountChanged(null); // the anonymous account

        // Starts from the shared ones
        assertEquals("fr", data.getLanguage());
        assertEquals("FR", data.getCountry());

        data.setLanguage("ja");
        data.setCountry("");

        prefs.enableMultiProfiles(false);

        assertEquals("fr", data.getLanguage());
        assertEquals("FR", data.getCountry());
        assertEquals("fr", getGlobalPrefs().getPreferredLanguage()); // the ones in use

        prefs.enableMultiProfiles(true);

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

        data.setLanguage("");
        data.setCountry("US");

        prefs.enableMultiProfiles(true);
        prefs.onAccountChanged(null);
        data.setCountry("GB");

        prefs.enableMultiProfiles(false);

        assertEquals("", data.getLanguage());
        assertEquals("US", data.getCountry());
    }

    /**
     * AppPrefs and GlobalPreferences outlive the test, so do the saved values
     */
    private static void clearSaved() {
        getPrefs().setData(DATA_KEY, "");
        getPrefs().setData(ANONYMOUS_PROFILE_KEY, "");
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
