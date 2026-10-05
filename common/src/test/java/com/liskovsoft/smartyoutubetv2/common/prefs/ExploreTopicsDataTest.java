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
public class ExploreTopicsDataTest {
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
        ExploreTopicsData data = getData();

        assertEquals(ExploreTopicsData.MODE_SHOW, data.getMode());
        assertEquals(AiSListFilterData.MARK_COLOR_OFF, data.getMarkColor());
        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
        assertFalse(data.isEnabled(MediaGroup.TYPE_HOME));
        assertFalse(data.isHidingEnabled(MediaGroup.TYPE_HOME));
    }

    @Test
    public void savedApartFromTopChannels() {
        ExploreTopicsData exploreTopics = getData();
        TopChannelsData topChannels = TopChannelsData.instance(RuntimeEnvironment.getApplication());

        exploreTopics.setMode(ExploreTopicsData.MODE_HIDE);
        exploreTopics.setSectionEnabled(MediaGroup.TYPE_GAMING, false);
        exploreTopics.setMarkColor(AiSListFilterData.MARK_COLOR_RED);

        assertEquals(ExploreTopicsData.MODE_HIDE, exploreTopics.getMode());
        assertEquals(TopChannelsData.MODE_SHOW, topChannels.getMode());
        assertTrue(topChannels.isSectionEnabled(MediaGroup.TYPE_GAMING));
        assertEquals(AiSListFilterData.MARK_COLOR_OFF, topChannels.getMarkColor());
    }

    /**
     * AppPrefs outlives the test, so do the saved values
     */
    private static void clearSaved() {
        getPrefs().setData(ExploreTopicsData.class.getSimpleName(), "");
        getPrefs().setData("anonymous_" + ExploreTopicsData.class.getSimpleName(), "");
        getPrefs().setData(TopChannelsData.class.getSimpleName(), "");
        ExploreTopicsData.resetInstanceForTesting();
        TopChannelsData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static ExploreTopicsData getData() {
        return ExploreTopicsData.instance(RuntimeEnvironment.getApplication());
    }
}
