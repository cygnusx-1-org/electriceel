package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
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
public class EndScreenDataTest {
    private static final Account FIRST_ACCOUNT = TestAccounts.FIRST;
    private static final Account SECOND_ACCOUNT = TestAccounts.SECOND;

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
    public void onByDefault() {
        assertTrue(getData().isShowEnabled());
    }

    @Test
    public void valueSurvivesRestart() {
        getData().setShowEnabled(false);
        getData().persistNow();
        ShadowLooper.shadowMainLooper().idle();
        EndScreenData.resetInstanceForTesting();

        assertFalse(getData().isShowEnabled());
    }

    @Test
    public void eachAccountHasItsOwnValue() {
        AppPrefs prefs = getPrefs();
        EndScreenData data = getData();

        prefs.onAccountChanged(FIRST_ACCOUNT);
        data.setShowEnabled(false);
        ShadowLooper.shadowMainLooper().idle(); // saved before the account changes

        prefs.onAccountChanged(SECOND_ACCOUNT);

        // Starts from the default, not the other account's value
        assertTrue(data.isShowEnabled());

        prefs.onAccountChanged(FIRST_ACCOUNT);

        assertFalse(data.isShowEnabled());
    }

    /**
     * The player hides the cards as soon as the switch is turned off, and the account's value applies after a switch of accounts
     */
    @Test
    public void listenersHearChangesAndAccountSwitches() {
        AppPrefs prefs = getPrefs();
        EndScreenData data = getData();
        int[] changes = {0};
        EndScreenData.OnDataChange listener = () -> changes[0]++;
        data.setOnChange(listener);

        data.setShowEnabled(false);
        prefs.onAccountChanged(FIRST_ACCOUNT);

        assertEquals(2, changes[0]);

        data.removeOnChange(listener);
    }

    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), EndScreenData.class.getSimpleName());
        EndScreenData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static EndScreenData getData() {
        return EndScreenData.instance(RuntimeEnvironment.getApplication());
    }
}
