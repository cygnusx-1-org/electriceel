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
public class ShowsDataTest {
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
        ShowsData data = getData();

        assertEquals(ShowsData.MODE_SHOW, data.getMode());
        assertEquals(AiSListFilterData.MARK_COLOR_OFF, data.getMarkColor());
        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
        assertFalse(data.isEnabled(MediaGroup.TYPE_HOME));
        assertFalse(data.isMarkingEnabled(MediaGroup.TYPE_HOME));
        assertFalse(data.isHidingEnabled(MediaGroup.TYPE_HOME));
    }

    /**
     * Its own default doesn't change the one of the other filters
     */
    @Test
    public void otherFiltersStillMarkByDefault() {
        getData();

        assertEquals(WatchLaterData.MODE_MARK, WatchLaterData.instance(RuntimeEnvironment.getApplication()).getMode());
    }

    @Test
    public void savedApartFromWatchLater() {
        ShowsData shows = getData();
        WatchLaterData watchLater = WatchLaterData.instance(RuntimeEnvironment.getApplication());

        shows.setMode(ShowsData.MODE_HIDE);
        shows.setSectionEnabled(MediaGroup.TYPE_GAMING, false);
        shows.setMarkColor(AiSListFilterData.MARK_COLOR_RED);
        watchLater.setMode(WatchLaterData.MODE_MARK);

        assertEquals(ShowsData.MODE_HIDE, shows.getMode());
        assertEquals(WatchLaterData.MODE_MARK, watchLater.getMode());
        assertTrue(watchLater.isSectionEnabled(MediaGroup.TYPE_GAMING));
        assertEquals(AiSListFilterData.MARK_COLOR_OFF, watchLater.getMarkColor());
    }

    /**
     * AppPrefs outlives the test, so do the saved values
     */
    private static void clearSaved() {
        getPrefs().setData(ShowsData.class.getSimpleName(), "");
        getPrefs().setData("anonymous_" + ShowsData.class.getSimpleName(), "");
        getPrefs().setData(WatchLaterData.class.getSimpleName(), "");
        ShowsData.resetInstanceForTesting();
        WatchLaterData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static ShowsData getData() {
        return ShowsData.instance(RuntimeEnvironment.getApplication());
    }
}
