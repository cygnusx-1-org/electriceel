package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OpenedVideosDataTest {
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
    public void openedVideosAreShownByDefault() {
        assertFalse(getData().isHidden());
    }

    @Test
    public void valueSurvivesRestart() {
        getData().setHidden(true);
        ShadowLooper.shadowMainLooper().idle(); // saved right away
        OpenedVideosData.resetInstanceForTesting();

        assertTrue(getData().isHidden());
    }

    @Test
    public void eachAccountHasItsOwnValue() {
        AppPrefs prefs = getPrefs();
        OpenedVideosData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.setHidden(true);
        ShadowLooper.shadowMainLooper().idle(); // saved before the account changes

        prefs.onAccountChanged(TestAccounts.SECOND);

        // Starts from the default, not the other account's value
        assertFalse(data.isHidden());

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertTrue(data.isHidden());
    }

    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), OpenedVideosData.class.getSimpleName());
        OpenedVideosData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static OpenedVideosData getData() {
        return OpenedVideosData.instance(RuntimeEnvironment.getApplication());
    }
}
