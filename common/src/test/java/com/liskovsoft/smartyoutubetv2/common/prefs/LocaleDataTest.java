package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.TextUtils;

import com.liskovsoft.googlecommon.common.locale.LocaleManager;
import com.liskovsoft.sharedutils.locale.LocaleUpdater;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
     * The account button cycles through None: with the same ones on both accounts nothing changes, and no restart is asked for (#48)
     */
    @Test
    public void noneKeepsTheOnesInUse() {
        AppPrefs prefs = getPrefs();
        LocaleData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.setLanguage("en_US");
        data.setCountry("GB-SCT");

        prefs.onAccountChanged(TestAccounts.SECOND);
        data.setLanguage("en_US");
        data.setCountry("GB-SCT");

        prefs.onAccountChanged(null);

        assertEquals("en_US", data.getLanguage());
        assertEquals("GB-SCT", data.getCountry());

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertEquals("en_US", data.getLanguage());
        assertEquals("GB-SCT", data.getCountry());
    }

    /**
     * Another account's are in use at once, without a restart: the locale is read again, for the activities and for the content
     */
    @Test
    public void anotherAccountsAreInUseAtOnce() {
        AppPrefs prefs = getPrefs();
        LocaleData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.setLanguage("fr");
        data.setCountry("FR");

        prefs.onAccountChanged(TestAccounts.SECOND);
        data.setLanguage("ja");
        data.setCountry("JP");

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertEquals(new Locale("fr", "FR"), LocaleUpdater.getSavedLocale(RuntimeEnvironment.getApplication()));
        assertEquals("FR", LocaleManager.instance().getCountry());

        int toasts = ShadowToast.shownToastCount();
        prefs.onAccountChanged(TestAccounts.SECOND);

        assertEquals(new Locale("ja", "JP"), LocaleUpdater.getSavedLocale(RuntimeEnvironment.getApplication()));
        assertEquals("JP", LocaleManager.instance().getCountry());
        assertEquals(toasts, ShadowToast.shownToastCount());
    }

    /**
     * Chosen in the settings: in use at once, without a restart, like another account's
     */
    @Test
    public void chosenOnesAreInUseAtOnce() {
        AppPrefs prefs = getPrefs();
        LocaleData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.setLanguage("fr");
        data.setCountry("FR");

        assertEquals(new Locale("fr", "FR"), LocaleUpdater.getSavedLocale(RuntimeEnvironment.getApplication()));
        assertEquals("FR", LocaleManager.instance().getCountry());

        data.setLanguage("ja");
        data.setCountry("JP");

        assertEquals(new Locale("ja", "JP"), LocaleUpdater.getSavedLocale(RuntimeEnvironment.getApplication()));
        assertEquals("JP", LocaleManager.instance().getCountry());

        prefs.onAccountChanged(null); // None keeps the ones in use, a choice with it too
        data.setCountry("DE");

        assertEquals(new Locale("ja", "DE"), LocaleUpdater.getSavedLocale(RuntimeEnvironment.getApplication()));
        assertEquals("DE", LocaleManager.instance().getCountry());
    }

    /**
     * Only the country chosen: the language is the system's, not the previous account's, which the activities still have
     */
    @Test
    public void languageNotChosenIsTheSystems() {
        AppPrefs prefs = getPrefs();
        LocaleData data = getData();

        prefs.onAccountChanged(TestAccounts.SECOND);
        data.setLanguage("");
        data.setCountry("GB");

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.setLanguage("de_DE");
        data.setCountry("DE");

        Configuration previous = new Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());
        previous.setLocale(new Locale("de", "DE"));
        Context previousActivity = RuntimeEnvironment.getApplication().createConfigurationContext(previous);

        prefs.onAccountChanged(TestAccounts.SECOND);

        String systemLanguage = Resources.getSystem().getConfiguration().getLocales().get(0).getLanguage();
        assertEquals(new Locale(systemLanguage, "GB"), LocaleUpdater.getSavedLocale(previousActivity));
    }

    /**
     * One chosen with None selected is in use until an account is selected: it isn't kept for None
     */
    @Test
    public void noneHasNoneOfItsOwn() {
        AppPrefs prefs = getPrefs();
        LocaleData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.setLanguage("fr");
        data.setCountry("FR");

        prefs.onAccountChanged(null);
        data.setLanguage("ja");
        data.setCountry("JP");

        assertTrue(TextUtils.isEmpty(prefs.getData("anonymous_" + DATA_KEY)));

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertEquals("fr", data.getLanguage());
        assertEquals("FR", data.getCountry());

        prefs.onAccountChanged(null);

        assertEquals("fr", data.getLanguage());
        assertEquals("FR", data.getCountry());
    }

    /**
     * Copied from another account with None selected: they're the ones in use
     */
    @Test
    public void copiedWithNoneAreInUse() {
        AppPrefs prefs = getPrefs();
        LocaleData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.setLanguage("de");
        data.setCountry("DE");

        prefs.onAccountChanged(TestAccounts.SECOND);
        data.setLanguage("fr");
        data.setCountry("FR");

        prefs.onAccountChanged(null);
        prefs.copyProfileData(TestAccounts.FIRST);

        assertEquals("de", data.getLanguage());
        assertEquals("DE", data.getCountry());
    }

    /**
     * AppPrefs and GlobalPreferences outlive the test, so do the saved values
     */
    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), DATA_KEY);
        getGlobalPrefs().setPreferredLanguage("");
        getGlobalPrefs().setPreferredCountry("");
        LocaleData.resetInstanceForTesting();
        LocaleUpdater.clearCache();
        LocaleManager.unhold();
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
