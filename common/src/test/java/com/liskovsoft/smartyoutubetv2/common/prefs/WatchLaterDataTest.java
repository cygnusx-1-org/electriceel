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
public class WatchLaterDataTest {
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
    public void markedByDefaultInEverySection() {
        WatchLaterData data = getData();

        assertEquals(WatchLaterData.MODE_MARK, data.getMode());
        assertEquals(AiSListFilterData.MARK_COLOR_OFF, data.getMarkColor());
        assertTrue(data.isMarkingEnabled(MediaGroup.TYPE_HOME));
        assertTrue(data.isMarkingEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
        assertTrue(data.isMarkingEnabled(MediaGroup.TYPE_GAMING));
        assertFalse(data.isHidingEnabled(MediaGroup.TYPE_HOME));
        assertFalse(data.isEnabled(MediaGroup.TYPE_HISTORY));
    }

    @Test
    public void savedApartFromCollaborations() {
        WatchLaterData watchLater = getData();
        CollaborationsData collaborations = CollaborationsData.instance(RuntimeEnvironment.getApplication());

        watchLater.setMode(WatchLaterData.MODE_HIDE);
        watchLater.setSectionEnabled(MediaGroup.TYPE_GAMING, false);
        watchLater.setMarkColor(AiSListFilterData.MARK_COLOR_RED);
        collaborations.setMode(CollaborationsData.MODE_MARK);

        assertEquals(WatchLaterData.MODE_HIDE, watchLater.getMode());
        assertEquals(CollaborationsData.MODE_MARK, collaborations.getMode());
        assertTrue(collaborations.isSectionEnabled(MediaGroup.TYPE_GAMING));
        assertEquals(AiSListFilterData.MARK_COLOR_OFF, collaborations.getMarkColor());
    }

    @Test
    public void valuesSurviveRestart() {
        WatchLaterData data = getData();

        data.setMode(WatchLaterData.MODE_MARK);
        data.setSectionEnabled(MediaGroup.TYPE_HOME, false);
        data.persistNow();
        ShadowLooper.shadowMainLooper().idle();
        WatchLaterData.resetInstanceForTesting();

        WatchLaterData restored = getData();

        assertEquals(WatchLaterData.MODE_MARK, restored.getMode());
        assertFalse(restored.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertTrue(restored.isSectionEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
    }

    /**
     * AppPrefs outlives the test, so do the saved values
     */
    private static void clearSaved() {
        getPrefs().setData(WatchLaterData.class.getSimpleName(), "");
        getPrefs().setData("anonymous_" + WatchLaterData.class.getSimpleName(), "");
        getPrefs().setData(CollaborationsData.class.getSimpleName(), "");
        WatchLaterData.resetInstanceForTesting();
        CollaborationsData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static WatchLaterData getData() {
        return WatchLaterData.instance(RuntimeEnvironment.getApplication());
    }
}
