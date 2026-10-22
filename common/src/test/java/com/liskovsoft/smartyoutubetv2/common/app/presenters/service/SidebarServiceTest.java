package com.liskovsoft.smartyoutubetv2.common.app.presenters.service;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SidebarServiceTest {
    @Before
    public void setUp() {
        resetSidebar();
    }

    @After
    public void tearDown() {
        Utils.sHandler.removeCallbacksAndMessages(null);
        SidebarService.resetInstanceForTesting();
        AppPrefs.instance(RuntimeEnvironment.getApplication()).setSidebarData("");
    }

    /**
     * A new sidebar starts with Home: Notifications is after Watch later
     */
    @Test
    public void newSidebarHasNotificationsAfterWatchLater() {
        List<Integer> ids = getSectionIds();

        assertEquals(MediaGroup.TYPE_HOME, (int) ids.get(0));
        assertEquals(ids.indexOf(MediaGroup.TYPE_WATCH_LATER) + 1, ids.indexOf(MediaGroup.TYPE_NOTIFICATIONS));
    }

    /**
     * Put in order without Settings (see MainUISettingsPresenter.showSidebarOrder): the others keep theirs after them, Settings
     * last, and it's kept
     */
    @Test
    public void orderedItemsFirstOthersAfterThemSettingsLast() {
        List<Integer> before = getSectionIds();

        getService().setPinnedItemsOrder(Arrays.asList(section(MediaGroup.TYPE_SUBSCRIPTIONS), section(MediaGroup.TYPE_HISTORY)));

        List<Integer> expected = new ArrayList<>();
        expected.add(MediaGroup.TYPE_SUBSCRIPTIONS);
        expected.add(MediaGroup.TYPE_HISTORY);
        for (int id : before) {
            if (!expected.contains(id)) {
                expected.add(id);
            }
        }

        assertEquals(expected, getSectionIds());
        assertEquals(MediaGroup.TYPE_SETTINGS, (int) expected.get(expected.size() - 1));

        getService().onProfileChanged(); // read again

        assertEquals(expected, getSectionIds());
    }

    /**
     * Put in order with Settings first, and with a section that isn't in the sidebar (disabled while the order was open):
     * Settings stays last, the other isn't added
     */
    @Test
    public void settingsStaysLastAndMissingSectionNotAdded() {
        List<Integer> before = getSectionIds();

        getService().setPinnedItemsOrder(Arrays.asList(section(MediaGroup.TYPE_SETTINGS), section(MediaGroup.TYPE_PLAYBACK_QUEUE)));

        assertEquals(before, getSectionIds());
    }

    /**
     * First section isn't a section, but it's kept like one (not Home, as for a section that isn't there anymore)
     */
    @Test
    public void firstSectionBootKept() {
        getService().setBootSectionId(SidebarService.BOOT_SECTION_FIRST);
        getService().onProfileChanged(); // read again

        assertEquals(SidebarService.BOOT_SECTION_FIRST, getService().getBootSectionId());
    }

    private static Video section(int sectionId) {
        Video item = new Video();
        item.sectionId = sectionId;
        return item;
    }

    private static List<Integer> getSectionIds() {
        List<Integer> result = new ArrayList<>();

        for (Video item : getService().getPinnedItems()) {
            result.add(item.sectionId);
        }

        return result;
    }

    /**
     * A new sidebar (see SidebarService.initPinnedItems)
     */
    private static void resetSidebar() {
        AppPrefs.instance(RuntimeEnvironment.getApplication()).setSidebarData(""); // not null: it isn't written, the old data stays
        getService().onProfileChanged();
    }

    private static SidebarService getService() {
        return SidebarService.instance(RuntimeEnvironment.getApplication());
    }
}
