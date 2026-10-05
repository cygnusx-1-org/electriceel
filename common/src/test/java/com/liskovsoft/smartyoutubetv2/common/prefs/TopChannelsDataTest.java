package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
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

/**
 * The shared behavior of SectionFilterData is covered by CollaborationsDataTest
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TopChannelsDataTest {
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

    @Test
    public void shownByDefaultWithEverySectionPicked() {
        TopChannelsData data = getData();

        assertEquals(TopChannelsData.MODE_SHOW, data.getMode());
        assertEquals(AiSListFilterData.MARK_COLOR_OFF, data.getMarkColor());
        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
        assertFalse(data.isEnabled(MediaGroup.TYPE_HOME));
        assertFalse(data.isHidingEnabled(MediaGroup.TYPE_HOME));
    }

    @Test
    public void savedApartFromShows() {
        TopChannelsData topChannels = getData();
        ShowsData shows = ShowsData.instance(RuntimeEnvironment.getApplication());

        topChannels.setMode(TopChannelsData.MODE_HIDE);
        topChannels.setSectionEnabled(MediaGroup.TYPE_GAMING, false);
        topChannels.setMarkColor(AiSListFilterData.MARK_COLOR_RED);

        assertEquals(TopChannelsData.MODE_HIDE, topChannels.getMode());
        assertEquals(ShowsData.MODE_SHOW, shows.getMode());
        assertTrue(shows.isSectionEnabled(MediaGroup.TYPE_GAMING));
        assertEquals(AiSListFilterData.MARK_COLOR_OFF, shows.getMarkColor());
    }

    /**
     * AppPrefs outlives the test, so do the saved values
     */
    private static void clearSaved() {
        getPrefs().setData(TopChannelsData.class.getSimpleName(), "");
        getPrefs().setData("anonymous_" + TopChannelsData.class.getSimpleName(), "");
        getPrefs().setData(ShowsData.class.getSimpleName(), "");
        TopChannelsData.resetInstanceForTesting();
        ShowsData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static TopChannelsData getData() {
        return TopChannelsData.instance(RuntimeEnvironment.getApplication());
    }
}
