package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;
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
public class OldVideosDataTest {
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
    public void offByDefaultWithOneYearPeriod() {
        OldVideosData data = getData();

        assertFalse(data.isEnabled());
        assertFalse(data.isQuickToggleEnabled());
        assertEquals(12, data.getPeriodMonths());
        assertFalse(data.isHidingEnabled(MediaGroup.TYPE_HOME));
    }

    @Test
    public void homeAndSubscriptionsByDefault() {
        OldVideosData data = getData();

        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
        assertFalse(data.isSectionEnabled(MediaGroup.TYPE_GAMING));
        assertFalse(data.isSectionEnabled(MediaGroup.TYPE_HISTORY));
    }

    @Test
    public void sectionsAreSetOneByOne() {
        OldVideosData data = getData();

        data.setSectionEnabled(MediaGroup.TYPE_HOME, false);
        data.setSectionEnabled(MediaGroup.TYPE_KIDS_HOME, true);

        assertFalse(data.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_KIDS_HOME));
        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
    }

    @Test
    public void hidingNeedsTheFilterAndTheSection() {
        OldVideosData data = getData();

        data.setEnabled(true);

        assertTrue(data.isHidingEnabled(MediaGroup.TYPE_HOME));
        assertFalse(data.isHidingEnabled(MediaGroup.TYPE_GAMING));
    }

    @Test
    public void periodIsKeptWhileOff() {
        OldVideosData data = getData();

        data.setPeriodMonths(3);
        data.setEnabled(true);
        data.setEnabled(false);

        assertEquals(3, data.getPeriodMonths());
    }

    @Test
    public void pinnedItemsAreNeverFiltered() {
        OldVideosData data = getData();
        int pinnedItemId = "Pinned playlist".hashCode();

        data.setEnabled(true);
        data.setSectionEnabled(pinnedItemId, true);

        assertFalse(data.isHidingEnabled(pinnedItemId));
        assertFalse(data.isHidingEnabled(-1));
    }

    /**
     * The values not set yet are saved as "null" when a later one is set
     */
    @Test
    public void defaultsSurviveSavingLaterValues() {
        OldVideosData data = getData();

        data.setQuickToggleEnabled(true);
        data.persistNow();
        ShadowLooper.shadowMainLooper().idle();
        OldVideosData.resetInstanceForTesting();

        OldVideosData restored = getData();

        assertTrue(restored.isQuickToggleEnabled());
        assertFalse(restored.isEnabled());
        assertEquals(12, restored.getPeriodMonths());
        assertTrue(restored.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertTrue(restored.isSectionEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
    }

    @Test
    public void valuesSurviveRestart() {
        OldVideosData data = getData();

        data.setEnabled(true);
        data.setPeriodMonths(6);
        data.setSectionEnabled(MediaGroup.TYPE_HOME, false);
        data.setSectionEnabled(MediaGroup.TYPE_MUSIC, true);
        data.persistNow();
        ShadowLooper.shadowMainLooper().idle();
        OldVideosData.resetInstanceForTesting();

        OldVideosData restored = getData();

        assertTrue(restored.isEnabled());
        assertEquals(6, restored.getPeriodMonths());
        assertFalse(restored.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertTrue(restored.isSectionEnabled(MediaGroup.TYPE_MUSIC));
        assertTrue(restored.isSectionEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
    }

    @Test
    public void eachAccountHasItsOwnValues() {
        AppPrefs prefs = getPrefs();
        OldVideosData data = getData();

        prefs.onAccountChanged(FIRST_ACCOUNT);
        data.setPeriodMonths(3);
        data.setQuickToggleEnabled(true);
        ShadowLooper.shadowMainLooper().idle(); // saved before the account changes

        prefs.onAccountChanged(SECOND_ACCOUNT);

        // Starts from the defaults, not the other account's values
        assertEquals(12, data.getPeriodMonths());
        assertFalse(data.isQuickToggleEnabled());

        data.setPeriodMonths(6);
        ShadowLooper.shadowMainLooper().idle();

        prefs.onAccountChanged(FIRST_ACCOUNT);

        assertEquals(3, data.getPeriodMonths());
        assertTrue(data.isQuickToggleEnabled());

        prefs.onAccountChanged(SECOND_ACCOUNT);

        assertEquals(6, data.getPeriodMonths());
        assertFalse(data.isQuickToggleEnabled());
    }

    @Test
    public void copiedFromAnotherAccount() {
        AppPrefs prefs = getPrefs();
        OldVideosData data = getData();

        prefs.onAccountChanged(FIRST_ACCOUNT);
        data.setPeriodMonths(3);
        ShadowLooper.shadowMainLooper().idle();
        prefs.onAccountChanged(SECOND_ACCOUNT);

        assertEquals(12, data.getPeriodMonths());

        prefs.copyProfileData(FIRST_ACCOUNT);

        assertEquals(3, data.getPeriodMonths());
    }

    @Test
    public void accountChangeIsAnnounced() {
        OldVideosData data = getData();
        int[] changes = {0};
        OnDataChange listener = () -> changes[0]++;
        data.setOnChange(listener);

        getPrefs().onAccountChanged(FIRST_ACCOUNT);

        assertTrue(changes[0] > 0);
    }

    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), OldVideosData.class.getSimpleName());
        OldVideosData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static OldVideosData getData() {
        return OldVideosData.instance(RuntimeEnvironment.getApplication());
    }
}
