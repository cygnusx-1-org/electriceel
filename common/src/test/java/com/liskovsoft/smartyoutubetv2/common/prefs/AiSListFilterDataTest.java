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

    @Test
    public void quickToggleIsDisabledAndOnByDefault() {
        AiSListFilterData data = getData();

        assertFalse(data.isQuickToggleEnabled());
        assertTrue(data.isQuickToggleHiding());
        assertFalse(data.isHidingPaused());

        // Off counts only while it's enabled
        data.setQuickToggleHiding(false);
        assertFalse(data.isHidingPaused());

        data.setQuickToggleEnabled(true);
        assertTrue(data.isHidingPaused());
    }

    @Test
    public void quickToggleSurvivesRestart() {
        getData().setQuickToggleEnabled(true);
        getData().setQuickToggleHiding(false);
        ShadowLooper.shadowMainLooper().idle(); // saved right away
        AiSListFilterData.resetInstanceForTesting();

        assertTrue(getData().isQuickToggleEnabled());
        assertFalse(getData().isQuickToggleHiding());
    }

    /**
     * Saved before the quick toggle came, or with only the switch set: it's on
     */
    @Test
    public void quickToggleIsOnUntilTurnedOff() {
        getData().setBlockOnReportEnabled(true);
        ShadowLooper.shadowMainLooper().idle();
        AiSListFilterData.resetInstanceForTesting();

        assertFalse(getData().isQuickToggleEnabled());
        assertTrue(getData().isQuickToggleHiding());

        getData().setQuickToggleEnabled(true);
        ShadowLooper.shadowMainLooper().idle();
        AiSListFilterData.resetInstanceForTesting();

        assertTrue(getData().isQuickToggleEnabled());
        assertTrue(getData().isQuickToggleHiding());
    }

    @Test
    public void setToHideNeedsAListThatHidesSomewhere() {
        AiSListFilterData data = getData();

        assertFalse(data.isSetToHide());

        // Only marks in the mark only sections
        data.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HISTORY, true);
        data.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_CHANNELS, true);
        assertFalse(data.isSetToHide());

        data.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_SUGGESTIONS, true);
        assertTrue(data.isSetToHide());

        // Marks everywhere
        data.setMarkMode(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.MARK_MODE_ALL);
        assertFalse(data.isSetToHide());

        data.setEverythingEnabled(AiSListFilterData.LIST_WARNLIST, true);
        assertTrue(data.isSetToHide());

        // The quick toggle doesn't change what the lists are set to
        data.setQuickToggleEnabled(true);
        data.setQuickToggleHiding(false);
        assertTrue(data.isSetToHide());
    }

    /**
     * The subscription groups section came after the values saved by index: it moves none of them
     */
    @Test
    public void eachSectionOfEachListHasItsOwnSwitch() {
        AiSListFilterData data = getData();
        int[] lists = {AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.LIST_WARNLIST};

        for (int list : lists) {
            for (int section = 0; section < AiSListFilterData.SECTION_COUNT; section++) {
                data.setHideEnabled(list, section, true);

                for (int otherList : lists) {
                    for (int other = 0; other < AiSListFilterData.SECTION_COUNT; other++) {
                        assertEquals(otherList == list && other == section, data.isSectionChecked(otherList, other));
                    }
                }

                assertFalse(data.isEverythingEnabled(AiSListFilterData.LIST_BLOCKLIST));
                assertFalse(data.isEverythingEnabled(AiSListFilterData.LIST_WARNLIST));
                assertFalse(data.isQuickToggleEnabled());
                assertTrue(data.isQuickToggleHiding());
                assertFalse(data.isBlockOnReportEnabled());

                data.setHideEnabled(list, section, false);
            }
        }
    }

    @Test
    public void subscriptionGroupsSectionIsMarkOnly() {
        AiSListFilterData data = getData();

        assertTrue(AiSListFilterData.isMarkOnly(AiSListFilterData.SECTION_CHANNEL_GROUPS));

        data.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_CHANNEL_GROUPS, true);
        assertFalse(data.isSetToHide());
        assertTrue(data.isHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_CHANNEL_GROUPS));

        ShadowLooper.shadowMainLooper().idle(); // saved right away
        AiSListFilterData.resetInstanceForTesting();

        assertTrue(getData().isSectionChecked(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_CHANNEL_GROUPS));
        assertFalse(getData().isSectionChecked(AiSListFilterData.LIST_WARNLIST, AiSListFilterData.SECTION_CHANNEL_GROUPS));
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
