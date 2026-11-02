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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ChannelGroupFilterDataTest {
    private static final int SUBSCRIPTIONS_ROW = ChannelGroupFilterData.AREA_HOME_SUBSCRIPTIONS_ROW;
    private static final int[] SUPPORTED_AREAS = {
            MediaGroup.TYPE_HOME, SUBSCRIPTIONS_ROW, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_KIDS_HOME, MediaGroup.TYPE_SPORTS,
            MediaGroup.TYPE_LIVE, MediaGroup.TYPE_GAMING, MediaGroup.TYPE_NEWS, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_SUBSCRIPTIONS
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
    public void nothingIsHiddenByDefault() {
        ChannelGroupFilterData data = getData();

        for (int area : SUPPORTED_AREAS) {
            assertFalse(data.isHidden(area));
        }
    }

    @Test
    public void areasAreSetOneByOne() {
        ChannelGroupFilterData data = getData();

        data.setHidden(MediaGroup.TYPE_HOME, true);
        data.setHidden(SUBSCRIPTIONS_ROW, true);
        data.setHidden(MediaGroup.TYPE_GAMING, true);
        data.setHidden(MediaGroup.TYPE_HOME, false);

        assertFalse(data.isHidden(MediaGroup.TYPE_HOME));
        assertTrue(data.isHidden(SUBSCRIPTIONS_ROW));
        assertTrue(data.isHidden(MediaGroup.TYPE_GAMING));
        assertFalse(data.isHidden(MediaGroup.TYPE_SUBSCRIPTIONS));
    }

    @Test
    public void everyAreaIsSupported() {
        ChannelGroupFilterData data = getData();

        for (int area : SUPPORTED_AREAS) {
            data.setHidden(area, true);

            assertTrue(data.isHidden(area));
        }
    }

    /**
     * The lists the user keeps, a channel page, search, Channels and a pinned item (a hash id) aren't areas
     */
    @Test
    public void otherSectionsAreNotAreas() {
        ChannelGroupFilterData data = getData();
        int[] sections = {MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_WATCH_LATER, MediaGroup.TYPE_USER_PLAYLISTS, MediaGroup.TYPE_MY_VIDEOS,
                MediaGroup.TYPE_CHANNEL, MediaGroup.TYPE_SEARCH, MediaGroup.TYPE_CHANNEL_UPLOADS, "Pinned group".hashCode(), -1, 31};

        for (int sectionId : sections) {
            data.setHidden(sectionId, true);

            assertFalse(data.isHidden(sectionId));
        }
    }

    @Test
    public void valuesSurviveRestart() {
        ChannelGroupFilterData data = getData();

        data.setHidden(MediaGroup.TYPE_HOME, true);
        data.setHidden(SUBSCRIPTIONS_ROW, true);
        data.persistNow();
        ShadowLooper.shadowMainLooper().idle();
        ChannelGroupFilterData.resetInstanceForTesting();

        ChannelGroupFilterData restored = getData();

        assertTrue(restored.isHidden(MediaGroup.TYPE_HOME));
        assertTrue(restored.isHidden(SUBSCRIPTIONS_ROW));
        assertFalse(restored.isHidden(MediaGroup.TYPE_GAMING));
    }

    @Test
    public void watchedVideosAreShownByDefault() {
        assertFalse(getData().isWatchedHidden());
    }

    /**
     * The areas were never set: they're saved as "null" and come back as none
     */
    @Test
    public void hidingWatchedVideosSurvivesRestartAndHidesNoArea() {
        ChannelGroupFilterData data = getData();

        data.setWatchedHidden(true);
        data.persistNow();
        ShadowLooper.shadowMainLooper().idle();
        ChannelGroupFilterData.resetInstanceForTesting();

        ChannelGroupFilterData restored = getData();

        assertTrue(restored.isWatchedHidden());

        for (int area : SUPPORTED_AREAS) {
            assertFalse(restored.isHidden(area));
        }

        restored.setHidden(MediaGroup.TYPE_GAMING, true);

        assertTrue(restored.isHidden(MediaGroup.TYPE_GAMING));
        assertFalse(restored.isHidden(MediaGroup.TYPE_HOME));
    }

    @Test
    public void eachAccountHasItsOwnValues() {
        AppPrefs prefs = getPrefs();
        ChannelGroupFilterData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.setHidden(MediaGroup.TYPE_HOME, true);
        ShadowLooper.shadowMainLooper().idle(); // saved before the account changes

        prefs.onAccountChanged(TestAccounts.SECOND);

        // Starts from the defaults, not the other account's values
        assertFalse(data.isHidden(MediaGroup.TYPE_HOME));

        data.setHidden(MediaGroup.TYPE_GAMING, true);
        ShadowLooper.shadowMainLooper().idle();

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertTrue(data.isHidden(MediaGroup.TYPE_HOME));
        assertFalse(data.isHidden(MediaGroup.TYPE_GAMING));

        prefs.onAccountChanged(TestAccounts.SECOND);

        assertFalse(data.isHidden(MediaGroup.TYPE_HOME));
        assertTrue(data.isHidden(MediaGroup.TYPE_GAMING));
    }

    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), ChannelGroupFilterData.class.getSimpleName());
        ChannelGroupFilterData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static ChannelGroupFilterData getData() {
        return ChannelGroupFilterData.instance(RuntimeEnvironment.getApplication());
    }
}
