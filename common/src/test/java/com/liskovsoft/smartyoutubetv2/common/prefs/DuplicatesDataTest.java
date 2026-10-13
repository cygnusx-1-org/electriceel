package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DuplicatesDataTest {
    private static final Account FIRST_ACCOUNT = TestAccounts.FIRST;
    private static final Account SECOND_ACCOUNT = TestAccounts.SECOND;
    private static final int[] SUPPORTED_SECTIONS = {
            MediaGroup.TYPE_HOME, MediaGroup.TYPE_KIDS_HOME, MediaGroup.TYPE_SPORTS, MediaGroup.TYPE_LIVE,
            MediaGroup.TYPE_GAMING, MediaGroup.TYPE_NEWS, MediaGroup.TYPE_MUSIC
    };

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
    public void offByDefault() {
        DuplicatesData data = getData();

        for (int sectionId : SUPPORTED_SECTIONS) {
            assertFalse(data.isSectionEnabled(sectionId));
        }
    }

    @Test
    public void sectionsAreSetOneByOne() {
        DuplicatesData data = getData();

        data.setSectionEnabled(MediaGroup.TYPE_HOME, true);
        data.setSectionEnabled(MediaGroup.TYPE_GAMING, true);
        data.setSectionEnabled(MediaGroup.TYPE_HOME, false);

        assertFalse(data.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_GAMING));
        assertFalse(data.isSectionEnabled(MediaGroup.TYPE_SPORTS));
    }

    @Test
    public void everyRowSectionOfTheFeedsIsSupported() {
        DuplicatesData data = getData();

        for (int sectionId : SUPPORTED_SECTIONS) {
            data.setSectionEnabled(sectionId, true);

            assertTrue(data.isSectionEnabled(sectionId));
        }
    }

    /**
     * The lists the user made may share their videos, a grid is a single group
     */
    @Test
    public void otherSectionsAreNeverFiltered() {
        DuplicatesData data = getData();
        int pinnedItemId = "Pinned playlist".hashCode();
        int[] sections = {MediaGroup.TYPE_USER_PLAYLISTS, MediaGroup.TYPE_MY_VIDEOS, MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY,
                MediaGroup.TYPE_SHORTS, pinnedItemId, -1};

        for (int sectionId : sections) {
            data.setSectionEnabled(sectionId, true);

            assertFalse(data.isSectionEnabled(sectionId));
        }
    }

    @Test
    public void valuesSurviveRestart() {
        DuplicatesData data = getData();

        data.setSectionEnabled(MediaGroup.TYPE_HOME, true);
        data.setSectionEnabled(MediaGroup.TYPE_LIVE, true);
        data.persistNow();
        ShadowLooper.shadowMainLooper().idle();
        DuplicatesData.resetInstanceForTesting();

        DuplicatesData restored = getData();

        assertTrue(restored.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertTrue(restored.isSectionEnabled(MediaGroup.TYPE_LIVE));
        assertFalse(restored.isSectionEnabled(MediaGroup.TYPE_GAMING));
    }

    @Test
    public void eachAccountHasItsOwnValues() {
        AppPrefs prefs = getPrefs();
        DuplicatesData data = getData();

        prefs.onAccountChanged(FIRST_ACCOUNT);
        data.setSectionEnabled(MediaGroup.TYPE_HOME, true);
        ShadowLooper.shadowMainLooper().idle(); // saved before the account changes

        prefs.onAccountChanged(SECOND_ACCOUNT);

        // Starts from the defaults, not the other account's values
        assertFalse(data.isSectionEnabled(MediaGroup.TYPE_HOME));

        data.setSectionEnabled(MediaGroup.TYPE_GAMING, true);
        ShadowLooper.shadowMainLooper().idle();

        prefs.onAccountChanged(FIRST_ACCOUNT);

        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertFalse(data.isSectionEnabled(MediaGroup.TYPE_GAMING));

        prefs.onAccountChanged(SECOND_ACCOUNT);

        assertFalse(data.isSectionEnabled(MediaGroup.TYPE_HOME));
        assertTrue(data.isSectionEnabled(MediaGroup.TYPE_GAMING));
    }

    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), DuplicatesData.class.getSimpleName());
        DuplicatesData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static DuplicatesData getData() {
        return DuplicatesData.instance(RuntimeEnvironment.getApplication());
    }
}
