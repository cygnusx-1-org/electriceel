package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SponsorBlockDataTest {
    private static final String CHANNEL_ID = "UCchannel";

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
        SponsorBlockData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.setAction(SponsorSegment.CATEGORY_SPONSOR, SponsorBlockData.ACTION_SHOW_DIALOG);
        data.excludeChannel(CHANNEL_ID);

        prefs.onAccountChanged(TestAccounts.SECOND);

        // Starts from the defaults, not the other account's
        assertEquals(SponsorBlockData.ACTION_SKIP_WITH_TOAST, data.getAction(SponsorSegment.CATEGORY_SPONSOR));
        assertFalse(data.isChannelExcluded(CHANNEL_ID));

        data.setIgnoredDurationMs(10_000);

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertEquals(SponsorBlockData.ACTION_SHOW_DIALOG, data.getAction(SponsorSegment.CATEGORY_SPONSOR));
        assertTrue(data.isChannelExcluded(CHANNEL_ID));
        assertEquals(5_000, data.getIgnoredDurationMs());

        prefs.onAccountChanged(TestAccounts.SECOND);

        assertEquals(10_000, data.getIgnoredDurationMs());
    }

    @Test
    public void actionsSurviveRestart() {
        getData().setAction(SponsorSegment.CATEGORY_INTRO, SponsorBlockData.ACTION_DO_NOTHING);
        SponsorBlockData.resetInstanceForTesting();

        assertEquals(SponsorBlockData.ACTION_DO_NOTHING, getData().getAction(SponsorSegment.CATEGORY_INTRO));
    }

    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), SponsorBlockData.SPONSOR_BLOCK_DATA);
        SponsorBlockData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static SponsorBlockData getData() {
        return SponsorBlockData.instance(RuntimeEnvironment.getApplication());
    }
}
