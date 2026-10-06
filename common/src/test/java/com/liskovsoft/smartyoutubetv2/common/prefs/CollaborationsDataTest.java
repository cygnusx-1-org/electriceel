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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CollaborationsDataTest {
    private static final String ANONYMOUS_PROFILE_KEY = "anonymous_" + CollaborationsData.class.getSimpleName();

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
        CollaborationsData data = getData();

        assertEquals(CollaborationsData.MODE_MARK, data.getMode());
        assertEquals(AiSListFilterData.MARK_COLOR_GREEN, data.getMarkColor());

        for (int sectionId : new int[] {MediaGroup.TYPE_HOME, MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_GAMING,
                MediaGroup.TYPE_USER_PLAYLISTS, MediaGroup.TYPE_CHANNEL_UPLOADS, MediaGroup.TYPE_MY_VIDEOS,
                MediaGroup.TYPE_SEARCH, MediaGroup.TYPE_CHANNEL}) {
            assertTrue(data.isSectionEnabled(sectionId));
            assertTrue(data.isMarkingEnabled(sectionId));
            assertFalse(data.isHidingEnabled(sectionId));
        }
    }

    /**
     * Not in the Sections menu, so they can't be turned off there
     */
    @Test
    public void historyAndLocalListsAreNeverFiltered() {
        CollaborationsData data = getData();

        data.setMode(CollaborationsData.MODE_HIDE);

        for (int sectionId : new int[] {MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_SETTINGS, MediaGroup.TYPE_PLAYBACK_QUEUE,
                MediaGroup.TYPE_BLOCKED_CHANNELS, MediaGroup.TYPE_BLOCKED_AI_CHANNELS}) {
            data.setSectionEnabled(sectionId, true);

            assertFalse(data.isSectionEnabled(sectionId));
            assertFalse(data.isEnabled(sectionId));
        }
    }

    @Test
    public void modeAppliesOnlyToPickedSections() {
        CollaborationsData data = getData();

        data.setMode(CollaborationsData.MODE_HIDE);
        data.setSectionEnabled(MediaGroup.TYPE_GAMING, false);

        assertTrue(data.isEnabled(MediaGroup.TYPE_HOME));
        assertTrue(data.isHidingEnabled(MediaGroup.TYPE_HOME));
        assertFalse(data.isMarkingEnabled(MediaGroup.TYPE_HOME));
        assertFalse(data.isEnabled(MediaGroup.TYPE_GAMING));

        data.setMode(CollaborationsData.MODE_MARK);
        data.setSectionEnabled(MediaGroup.TYPE_GAMING, true);

        assertTrue(data.isMarkingEnabled(MediaGroup.TYPE_GAMING));
        assertFalse(data.isHidingEnabled(MediaGroup.TYPE_GAMING));
    }

    @Test
    public void pinnedItemsAreNeverFiltered() {
        CollaborationsData data = getData();
        int pinnedItemId = "Pinned playlist".hashCode();

        data.setMode(CollaborationsData.MODE_HIDE);
        data.setSectionEnabled(pinnedItemId, true);

        assertFalse(data.isEnabled(pinnedItemId));
        assertFalse(data.isEnabled(-1));
    }

    @Test
    public void unknownModeIsTheDefault() {
        CollaborationsData data = getData();

        data.setMode(7);

        assertEquals(CollaborationsData.MODE_MARK, data.getMode());
    }

    /**
     * The values not set yet are saved as "null" when a later one is set
     */
    @Test
    public void defaultsSurviveSavingLaterValues() {
        CollaborationsData data = getData();

        data.setMarkColor(AiSListFilterData.MARK_COLOR_RED);
        data.persistNow();
        ShadowLooper.shadowMainLooper().idle();
        CollaborationsData.resetInstanceForTesting();

        CollaborationsData restored = getData();

        assertEquals(CollaborationsData.MODE_MARK, restored.getMode());
        assertEquals(AiSListFilterData.MARK_COLOR_RED, restored.getMarkColor());
        assertTrue(restored.isSectionEnabled(MediaGroup.TYPE_MUSIC));
        assertTrue(restored.isSectionEnabled(MediaGroup.TYPE_HOME));
    }

    /**
     * Unchecked one by one (e.g. by All), the sections stay off after a restart
     */
    @Test
    public void uncheckedSectionsSurviveRestart() {
        CollaborationsData data = getData();

        for (int sectionId = 0; sectionId <= MediaGroup.TYPE_BLOCKED_AI_CHANNELS; sectionId++) {
            data.setSectionEnabled(sectionId, false);
        }

        data.persistNow();
        ShadowLooper.shadowMainLooper().idle();
        CollaborationsData.resetInstanceForTesting();

        CollaborationsData restored = getData();

        assertFalse(restored.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertFalse(restored.isSectionEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
    }

    /**
     * Search and Channel pages came after the sidebar sections: the sections saved before have them checked
     */
    @Test
    public void searchAndChannelPagesAreCheckedInSectionsSavedBefore() {
        CollaborationsData data = getData();

        data.setSectionEnabled(MediaGroup.TYPE_HOME, false);
        data.persistNow();
        ShadowLooper.shadowMainLooper().idle();
        CollaborationsData.resetInstanceForTesting();

        CollaborationsData restored = getData();

        assertFalse(restored.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertTrue(restored.isSectionEnabled(MediaGroup.TYPE_SEARCH));
        assertTrue(restored.isSectionEnabled(MediaGroup.TYPE_CHANNEL));

        restored.setSectionEnabled(MediaGroup.TYPE_SEARCH, false);
        restored.setSectionEnabled(MediaGroup.TYPE_CHANNEL, false);
        assertFalse(restored.isSectionEnabled(MediaGroup.TYPE_SEARCH));
        assertFalse(restored.isSectionEnabled(MediaGroup.TYPE_CHANNEL));
        assertFalse(restored.isSectionEnabled(MediaGroup.TYPE_HOME));
    }

    @Test
    public void valuesSurviveRestart() {
        CollaborationsData data = getData();

        data.setMode(CollaborationsData.MODE_MARK);
        data.setSectionEnabled(MediaGroup.TYPE_HOME, false);
        data.setMarkColor(AiSListFilterData.MARK_COLOR_BLUE);
        data.persistNow();
        ShadowLooper.shadowMainLooper().idle();
        CollaborationsData.resetInstanceForTesting();

        CollaborationsData restored = getData();

        assertEquals(CollaborationsData.MODE_MARK, restored.getMode());
        assertFalse(restored.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertTrue(restored.isSectionEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
        assertEquals(AiSListFilterData.MARK_COLOR_BLUE, restored.getMarkColor());
    }

    /**
     * Show is saved as 0, apart from the default
     */
    @Test
    public void showSurvivesRestart() {
        CollaborationsData data = getData();

        data.setMode(CollaborationsData.MODE_SHOW);
        data.persistNow();
        ShadowLooper.shadowMainLooper().idle();
        CollaborationsData.resetInstanceForTesting();

        CollaborationsData restored = getData();

        assertEquals(CollaborationsData.MODE_SHOW, restored.getMode());
        assertFalse(restored.isEnabled(MediaGroup.TYPE_HOME));
    }

    /**
     * Off is saved as 0, apart from the default green
     */
    @Test
    public void markColorOffSurvivesRestart() {
        CollaborationsData data = getData();

        data.setMarkColor(AiSListFilterData.MARK_COLOR_OFF);
        data.persistNow();
        ShadowLooper.shadowMainLooper().idle();
        CollaborationsData.resetInstanceForTesting();

        assertEquals(AiSListFilterData.MARK_COLOR_OFF, getData().getMarkColor());
    }

    @Test
    public void eachAccountHasItsOwnValues() {
        AppPrefs prefs = getPrefs();
        CollaborationsData data = getData();

        data.setMode(CollaborationsData.MODE_MARK);
        ShadowLooper.shadowMainLooper().idle(); // saved before the account changes

        prefs.enableMultiProfiles(true);
        prefs.onAccountChanged(null); // the anonymous account

        // Starts from the shared values
        assertEquals(CollaborationsData.MODE_MARK, data.getMode());

        data.setMode(CollaborationsData.MODE_HIDE);
        ShadowLooper.shadowMainLooper().idle();

        prefs.enableMultiProfiles(false);

        assertEquals(CollaborationsData.MODE_MARK, data.getMode());

        prefs.enableMultiProfiles(true);

        assertEquals(CollaborationsData.MODE_HIDE, data.getMode());
    }

    /**
     * AppPrefs outlives the test, so do the saved values
     */
    private static void clearSaved() {
        getPrefs().setData(CollaborationsData.class.getSimpleName(), "");
        getPrefs().setData(ANONYMOUS_PROFILE_KEY, "");
        CollaborationsData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static CollaborationsData getData() {
        return CollaborationsData.instance(RuntimeEnvironment.getApplication());
    }
}
