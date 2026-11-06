package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The shared behavior of SectionFilterData is covered by CollaborationsDataTest
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CategoryFilterDataTest {
    private static final String[] KEYS = {
            CategoryFilterData.MUSIC_KEY, CategoryFilterData.GAMING_KEY, CategoryFilterData.SPORTS_KEY,
            CategoryFilterData.NEWS_KEY, CategoryFilterData.TECH_KEY};

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
    public void shownByDefaultInHomeOnly() {
        for (CategoryFilterData data : all()) {
            assertEquals(SectionFilterData.MODE_SHOW, data.getMode());
            assertEquals(AiSListFilterData.MARK_COLOR_OFF, data.getMarkColor());
            assertTrue(data.isSectionEnabled(MediaGroup.TYPE_HOME));
            assertFalse(data.isEnabled(MediaGroup.TYPE_HOME));

            // The categories are looked up in Home alone
            data.setMode(SectionFilterData.MODE_MARK);
            assertTrue(data.isMarkingEnabled(MediaGroup.TYPE_HOME));

            for (int sectionId : new int[] {MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_GAMING,
                    MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_SEARCH, MediaGroup.TYPE_CHANNEL, SectionFilterData.SECTION_CHANNEL_GROUPS}) {
                assertFalse(data.isFilteredSection(sectionId));
                assertFalse(data.isEnabled(sectionId));
            }
        }
    }

    @Test
    public void inTheOrderOfTheSettingsWithTheirLabels() {
        CategoryFilterData[] all = all();
        int[] titles = {R.string.categories_music, R.string.categories_gaming, R.string.categories_sports, R.string.categories_news,
                R.string.categories_tech};
        // The title, unless it's too long for a card
        int[] labels = {R.string.categories_music, R.string.categories_gaming, R.string.categories_sports, R.string.news_label,
                R.string.tech_label};

        assertEquals(5, all.length);

        for (int i = 0; i < all.length; i++) {
            assertEquals(titles[i], all[i].getTitleResId());
            assertEquals(labels[i], all[i].getLabelResId());
            // The same instances each time
            assertSame(all[i], all()[i]);
        }
    }

    /**
     * A category checked in Hide content before it could be marked stays hidden until a mode is picked
     */
    @Test
    public void hiddenWhileTheReplacedOptionIsChecked() {
        HideContentData hideContent = HideContentData.instance(RuntimeEnvironment.getApplication());
        hideContent.setContentHidden(MediaServiceData.CONTENT_GAMING_HOME, true);
        hideContent.setContentHidden(MediaServiceData.CONTENT_TECH_HOME, true);
        MediaServiceData media = MediaServiceData.instance();
        CategoryFilterData[] all = all();

        assertEquals(SectionFilterData.MODE_SHOW, all[0].getMode());
        assertEquals(SectionFilterData.MODE_HIDE, all[1].getMode());
        assertTrue(all[1].isHidingEnabled(MediaGroup.TYPE_HOME));
        assertEquals(SectionFilterData.MODE_SHOW, all[2].getMode());
        assertEquals(SectionFilterData.MODE_SHOW, all[3].getMode());
        assertEquals(SectionFilterData.MODE_HIDE, all[4].getMode());

        // A color picked first keeps the mode and the option
        all[1].setMarkColor(AiSListFilterData.MARK_COLOR_RED);
        assertEquals(SectionFilterData.MODE_HIDE, all[1].getMode());
        assertTrue(media.isContentHiddenInSettings(MediaServiceData.CONTENT_GAMING_HOME));

        // The picked mode wins, and unchecks the option it replaces, that one only
        all[1].setMode(SectionFilterData.MODE_MARK);
        assertEquals(SectionFilterData.MODE_MARK, all[1].getMode());
        assertFalse(media.isContentHiddenInSettings(MediaServiceData.CONTENT_GAMING_HOME));
        assertTrue(media.isContentHiddenInSettings(MediaServiceData.CONTENT_TECH_HOME));
        assertEquals(SectionFilterData.MODE_HIDE, all[4].getMode());

        all[4].setMode(SectionFilterData.MODE_SHOW);
        assertEquals(SectionFilterData.MODE_SHOW, all[4].getMode());
        assertFalse(media.isContentHiddenInSettings(MediaServiceData.CONTENT_TECH_HOME));

        // Kept after a restart
        restart();
        assertEquals(SectionFilterData.MODE_MARK, all()[1].getMode());
        assertEquals(SectionFilterData.MODE_SHOW, all()[4].getMode());
    }

    @Test
    public void eachAccountKeepsItsReplacedOption() {
        AppPrefs prefs = getPrefs();

        prefs.onAccountChanged(TestAccounts.FIRST);
        HideContentData.instance(RuntimeEnvironment.getApplication()).setContentHidden(MediaServiceData.CONTENT_MUSIC_HOME, true);
        ShadowLooper.shadowMainLooper().idle(); // saved before the account changes
        prefs.onAccountChanged(TestAccounts.SECOND);

        assertEquals(SectionFilterData.MODE_SHOW, getMusic().getMode());

        getMusic().setMode(SectionFilterData.MODE_MARK);
        ShadowLooper.shadowMainLooper().idle(); // saved before the account changes
        prefs.onAccountChanged(TestAccounts.FIRST);

        assertEquals(SectionFilterData.MODE_HIDE, getMusic().getMode());

        prefs.onAccountChanged(TestAccounts.SECOND);

        assertEquals(SectionFilterData.MODE_MARK, getMusic().getMode());
    }

    @Test
    public void eachCategoryIsSavedApart() {
        CategoryFilterData[] all = all();

        all[0].setMode(SectionFilterData.MODE_HIDE);
        all[2].setMode(SectionFilterData.MODE_MARK);
        all[2].setMarkColor(AiSListFilterData.MARK_COLOR_BLUE);
        restart();
        all = all();

        assertEquals(SectionFilterData.MODE_HIDE, all[0].getMode());
        assertEquals(SectionFilterData.MODE_SHOW, all[1].getMode());
        assertEquals(SectionFilterData.MODE_MARK, all[2].getMode());
        assertEquals(AiSListFilterData.MARK_COLOR_BLUE, all[2].getMarkColor());
        assertEquals(AiSListFilterData.MARK_COLOR_OFF, all[3].getMarkColor());
        assertEquals(SectionFilterData.MODE_SHOW, all[4].getMode());
    }

    /**
     * AppPrefs outlives the test, so do the saved values. So does MediaServiceData, which doesn't read its saved values in the tests.
     */
    private static void clearSaved() {
        for (String key : KEYS) {
            TestAccounts.clearSaved(getPrefs(), key);
        }

        TestAccounts.clearSaved(getPrefs(), HideContentData.HIDE_CONTENT_DATA);
        HideContentData.resetInstanceForTesting();
        MediaServiceData.instance().setHiddenContentInSettings(MediaServiceData.CONTENT_HIDDEN_DEFAULT);
        resetInstances();
    }

    /**
     * The saved values are read again
     */
    private static void restart() {
        for (CategoryFilterData data : all()) {
            data.persistNow();
        }

        ShadowLooper.shadowMainLooper().idle();
        resetInstances();
    }

    private static void resetInstances() {
        CategoryFilterData.resetInstancesForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static CategoryFilterData[] all() {
        return CategoryFilterData.all(RuntimeEnvironment.getApplication());
    }

    private static CategoryFilterData getMusic() {
        return all()[0];
    }
}
