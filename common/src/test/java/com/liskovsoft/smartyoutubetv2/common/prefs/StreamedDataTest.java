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
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The shared behavior of SectionFilterData is covered by CollaborationsDataTest
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StreamedDataTest {
    @Before
    public void setUp() {
        getPrefs().onAccountChanged(null); // the anonymous account
        clearSaved();
    }

    @After
    public void tearDown() {
        Utils.sHandler.removeCallbacksAndMessages(null);
        getPrefs().onAccountChanged(null); // the anonymous account
        clearSaved();
    }

    @Test
    public void markedInPurpleByDefaultWithEverySectionPicked() {
        StreamedData data = getData();

        assertEquals(StreamedData.MODE_MARK, data.getMode());
        assertEquals(AiSListFilterData.MARK_COLOR_PURPLE, data.getMarkColor());
        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_SEARCH));
        assertTrue(data.isMarkingEnabled(MediaGroup.TYPE_HOME));
        assertFalse(data.isHidingEnabled(MediaGroup.TYPE_HOME));
    }

    /**
     * The Live section, where every video is a stream, isn't one of the setting's sections
     */
    @Test
    public void liveSectionIsntFiltered() {
        StreamedData data = getData();

        data.setMode(StreamedData.MODE_HIDE);
        data.setSectionEnabled(MediaGroup.TYPE_LIVE, true);

        assertFalse(data.isFilteredSection(MediaGroup.TYPE_LIVE));
        assertFalse(data.isSectionEnabled(MediaGroup.TYPE_LIVE));
        assertFalse(data.isEnabled(MediaGroup.TYPE_LIVE));
        assertTrue(data.isFilteredSection(MediaGroup.TYPE_SUBSCRIPTIONS));
    }

    @Test
    public void savedApartFromShows() {
        StreamedData streamed = getData();
        ShowsData shows = ShowsData.instance(RuntimeEnvironment.getApplication());

        streamed.setMode(StreamedData.MODE_HIDE);
        streamed.setSectionEnabled(MediaGroup.TYPE_GAMING, false);
        streamed.setMarkColor(AiSListFilterData.MARK_COLOR_RED);

        assertEquals(StreamedData.MODE_HIDE, streamed.getMode());
        assertEquals(ShowsData.MODE_SHOW, shows.getMode());
        assertTrue(shows.isSectionEnabled(MediaGroup.TYPE_GAMING));
        assertEquals(AiSListFilterData.MARK_COLOR_OFF, shows.getMarkColor());
    }

    /**
     * A picked color wins over the purple default after a restart
     */
    @Test
    public void colorSurvivesRestart() {
        StreamedData data = getData();

        data.setMarkColor(AiSListFilterData.MARK_COLOR_BLUE);
        data.persistNow();
        ShadowLooper.shadowMainLooper().idle();
        StreamedData.resetInstanceForTesting();

        assertEquals(AiSListFilterData.MARK_COLOR_BLUE, getData().getMarkColor());
    }

    /**
     * AppPrefs outlives the test, so do the saved values
     */
    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), StreamedData.class.getSimpleName());
        TestAccounts.clearSaved(getPrefs(), ShowsData.class.getSimpleName());
        StreamedData.resetInstanceForTesting();
        ShowsData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static StreamedData getData() {
        return StreamedData.instance(RuntimeEnvironment.getApplication());
    }
}
