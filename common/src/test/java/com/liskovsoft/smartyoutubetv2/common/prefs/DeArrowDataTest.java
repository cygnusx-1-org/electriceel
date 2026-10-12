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
public class DeArrowDataTest {
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
    public void eachAccountHasItsOwnValues() {
        AppPrefs prefs = getPrefs();
        DeArrowData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.setDeArrowEnabled(true);
        data.setReplaceTitlesEnabled(true);
        ShadowLooper.shadowMainLooper().idle(); // saved before the account changes

        prefs.onAccountChanged(TestAccounts.SECOND);

        // Starts from the defaults, not the other account's
        assertFalse(data.isDeArrowEnabled());
        assertFalse(data.isReplaceTitlesEnabled());

        data.setReplaceThumbnailsEnabled(true);
        ShadowLooper.shadowMainLooper().idle();

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertTrue(data.isDeArrowEnabled());
        assertTrue(data.isReplaceTitlesEnabled());
        assertFalse(data.isReplaceThumbnailsEnabled());

        prefs.onAccountChanged(TestAccounts.SECOND);

        assertTrue(data.isReplaceThumbnailsEnabled());
    }

    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), DeArrowData.class.getSimpleName());
        DeArrowData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static DeArrowData getData() {
        return DeArrowData.instance(RuntimeEnvironment.getApplication());
    }
}
