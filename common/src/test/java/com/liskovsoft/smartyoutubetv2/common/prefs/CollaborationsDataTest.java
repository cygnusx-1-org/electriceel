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
    public void shownByDefaultInHomeAndSubscriptions() {
        CollaborationsData data = getData();

        assertEquals(CollaborationsData.MODE_SHOW, data.getMode());
        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
        assertFalse(data.isSectionEnabled(MediaGroup.TYPE_GAMING));
        assertFalse(data.isEnabled(MediaGroup.TYPE_HOME));
    }

    @Test
    public void modeAppliesOnlyToPickedSections() {
        CollaborationsData data = getData();

        data.setMode(CollaborationsData.MODE_HIDE);

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
    public void unknownModeIsShow() {
        CollaborationsData data = getData();

        data.setMode(7);

        assertEquals(CollaborationsData.MODE_SHOW, data.getMode());
        assertFalse(data.isEnabled(MediaGroup.TYPE_HOME));
    }

    /**
     * The values not set yet are saved as "null" when a later one is set
     */
    @Test
    public void defaultsSurviveSavingLaterValues() {
        CollaborationsData data = getData();

        data.setSectionEnabled(MediaGroup.TYPE_MUSIC, true);
        data.persistNow();
        ShadowLooper.shadowMainLooper().idle();
        CollaborationsData.resetInstanceForTesting();

        CollaborationsData restored = getData();

        assertEquals(CollaborationsData.MODE_SHOW, restored.getMode());
        assertTrue(restored.isSectionEnabled(MediaGroup.TYPE_MUSIC));
        assertTrue(restored.isSectionEnabled(MediaGroup.TYPE_HOME));
    }

    @Test
    public void valuesSurviveRestart() {
        CollaborationsData data = getData();

        data.setMode(CollaborationsData.MODE_MARK);
        data.setSectionEnabled(MediaGroup.TYPE_HOME, false);
        data.persistNow();
        ShadowLooper.shadowMainLooper().idle();
        CollaborationsData.resetInstanceForTesting();

        CollaborationsData restored = getData();

        assertEquals(CollaborationsData.MODE_MARK, restored.getMode());
        assertFalse(restored.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertTrue(restored.isSectionEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
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
