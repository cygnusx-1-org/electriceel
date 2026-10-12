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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AiSListFilterDataTest {
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
    public void eachAccountHasItsOwnSettings() {
        AppPrefs prefs = getPrefs();
        AiSListFilterData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);
        data.setMarkColor(AiSListFilterData.LIST_WARNLIST, AiSListFilterData.MARK_COLOR_GREEN);
        ShadowLooper.shadowMainLooper().idle(); // saved before the account changes

        prefs.onAccountChanged(TestAccounts.SECOND);

        // Starts from the defaults, not the other account's
        assertFalse(data.isHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME));
        assertEquals(AiSListFilterData.MARK_COLOR_OFF, data.getMarkColor(AiSListFilterData.LIST_WARNLIST));

        data.setReportAs(AiSListFilterData.REPORT_AS_ACCOUNT_NAME);
        ShadowLooper.shadowMainLooper().idle();

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertTrue(data.isHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME));
        assertEquals(AiSListFilterData.MARK_COLOR_GREEN, data.getMarkColor(AiSListFilterData.LIST_WARNLIST));
        assertEquals(AiSListFilterData.REPORT_AS_ANONYMOUS, data.getReportAs());

        prefs.onAccountChanged(TestAccounts.SECOND);

        assertFalse(data.isHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME));
        assertEquals(AiSListFilterData.REPORT_AS_ACCOUNT_NAME, data.getReportAs());
    }

    @Test
    public void valuesSurviveRestart() {
        getData().setBlockOnReportEnabled(true);
        ShadowLooper.shadowMainLooper().idle(); // saved right away
        AiSListFilterData.resetInstanceForTesting();

        assertTrue(getData().isBlockOnReportEnabled());
    }

    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), AiSListFilterData.class.getSimpleName());
        AiSListFilterData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static AiSListFilterData getData() {
        return AiSListFilterData.instance(RuntimeEnvironment.getApplication());
    }
}
