package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.AiSListFilterData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AiSListManagerTest {
    private static final String AI_HANDLE = "@AiSlopChannel";
    private static final String WARN_HANDLE = "@MaybeAi";
    private static final String HUMAN_HANDLE = "@RealPerson";
    private AiSListFilterData mFilterData;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        mFilterData = AiSListFilterData.instance(context);
        disableAll();

        // Lists are stored lowercased
        Set<String> blocklist = new HashSet<>(Collections.singletonList(AI_HANDLE.toLowerCase()));
        Set<String> warnlist = new HashSet<>(Collections.singletonList(WARN_HANDLE.toLowerCase()));
        AiSListManager.instance(context).setListsForTesting(blocklist, warnlist);
    }

    @After
    public void tearDown() {
        disableAll();
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void nothingIsHiddenWhileFiltersAreOff() {
        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);

        addVideos(home);

        assertEquals(3, home.getSize());
    }

    @Test
    public void blocklistedChannelIsHiddenOnlyInEnabledSection() {
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        VideoGroup search = createGroup(MediaGroup.TYPE_SEARCH);
        addVideos(home);
        addVideos(search);

        assertEquals(2, home.getSize());
        assertFalse(containsHandle(home, AI_HANDLE));
        assertTrue(containsHandle(home, WARN_HANDLE));
        assertEquals(3, search.getSize());
    }

    @Test
    public void warnlistIsIndependentOfBlocklist() {
        mFilterData.setHideEnabled(AiSListFilterData.LIST_WARNLIST, AiSListFilterData.SECTION_SUBSCRIPTIONS, true);

        VideoGroup subscriptions = createGroup(MediaGroup.TYPE_SUBSCRIPTIONS);
        addVideos(subscriptions);

        assertEquals(2, subscriptions.getSize());
        assertTrue(containsHandle(subscriptions, AI_HANDLE));
        assertFalse(containsHandle(subscriptions, WARN_HANDLE));
    }

    @Test
    public void channelPageIsNeverHidden() {
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_CHANNELS, true);

        // Sidebar Channels section is mark only too
        VideoGroup channelsSection = createGroup(MediaGroup.TYPE_CHANNEL_UPLOADS);
        addVideos(channelsSection);
        assertEquals(3, channelsSection.getSize());

        // The channel page has no browse section
        VideoGroup channelPage = VideoGroup.from(new ArrayList<>());
        channelPage.setType(MediaGroup.TYPE_CHANNEL_UPLOADS);
        addVideos(channelPage);
        assertEquals(3, channelPage.getSize());
    }

    @Test
    public void channelPageIsMarkedInMarkMode() {
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_CHANNELS, true);
        setMarkEnabled(true);

        VideoGroup channelPage = VideoGroup.from(new ArrayList<>());
        channelPage.setType(MediaGroup.TYPE_CHANNEL_UPLOADS);
        addVideos(channelPage);

        assertEquals(3, channelPage.getSize());
        for (Video video : channelPage.getVideos()) {
            assertEquals(AI_HANDLE.equals(video.channelHandle) ? AiSListFilterData.LIST_BLOCKLIST : -1, video.aiMarkList);
        }
        // Marked, not blocked
        assertTrue(AiSListManager.instance(RuntimeEnvironment.getApplication()).getBlockedChannels().isEmpty());
    }

    @Test
    public void historyPlaylistsAndWatchLaterAreMarkOnly() {
        assertEquals(AiSListFilterData.SECTION_HISTORY, AiSListManager.getSection(MediaGroup.TYPE_HISTORY, true));
        assertEquals(AiSListFilterData.SECTION_PLAYLISTS, AiSListManager.getSection(MediaGroup.TYPE_USER_PLAYLISTS, true));
        // A playlist page is opened like a channel page
        assertEquals(AiSListFilterData.SECTION_PLAYLISTS, AiSListManager.getSection(MediaGroup.TYPE_CHANNEL_UPLOADS, false, "VLPLxxxx"));
        assertEquals(AiSListFilterData.SECTION_WATCH_LATER, AiSListManager.getSection(MediaGroup.TYPE_CHANNEL_UPLOADS, false, "VLWL"));
        assertEquals(AiSListManager.SECTION_CHANNEL_PAGE, AiSListManager.getSection(MediaGroup.TYPE_CHANNEL_UPLOADS, false, "UCxxxx"));
        assertEquals(AiSListFilterData.SECTION_WATCH_LATER, AiSListManager.getSection(MediaGroup.TYPE_USER_PLAYLISTS, true, "VLWL"));
        // The Home row "From your Watch later playlist"
        assertEquals(AiSListFilterData.SECTION_HOME, AiSListManager.getSection(MediaGroup.TYPE_HOME, true, "VLWL"));

        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HISTORY, true);

        // Never hidden, marked only in the mark mode
        VideoGroup history = createGroup(MediaGroup.TYPE_HISTORY);
        addVideos(history);
        assertEquals(3, history.getSize());
        assertFalse(containsMarked(history));

        setMarkEnabled(true);
        VideoGroup markedHistory = createGroup(MediaGroup.TYPE_HISTORY);
        addVideos(markedHistory);
        assertEquals(3, markedHistory.getSize());
        assertTrue(containsMarked(markedHistory));
    }

    @Test
    public void newSectionsDontShiftSavedSettings() {
        mFilterData.setHideEnabled(AiSListFilterData.LIST_WARNLIST, AiSListFilterData.SECTION_WATCH_LATER, true);

        assertEquals(AiSListFilterData.MARK_MODE_OFF, mFilterData.getMarkMode(AiSListFilterData.LIST_BLOCKLIST));
        assertEquals(AiSListFilterData.MARK_MODE_OFF, mFilterData.getMarkMode(AiSListFilterData.LIST_WARNLIST));
        assertEquals(AiSListFilterData.MARK_COLOR_OFF, mFilterData.getMarkColor(AiSListFilterData.LIST_BLOCKLIST));
        assertEquals(AiSListFilterData.MARK_COLOR_OFF, mFilterData.getMarkColor(AiSListFilterData.LIST_WARNLIST));
        for (int section = 0; section < AiSListFilterData.SECTION_COUNT; section++) {
            assertEquals(section == AiSListFilterData.SECTION_WATCH_LATER, mFilterData.isHideEnabled(AiSListFilterData.LIST_WARNLIST, section));
            assertFalse(mFilterData.isHideEnabled(AiSListFilterData.LIST_BLOCKLIST, section));
        }
    }

    @Test
    public void everythingCoversEverySectionWithoutTouchingItsSwitches() {
        mFilterData.setEverythingEnabled(AiSListFilterData.LIST_BLOCKLIST, true);

        VideoGroup search = createGroup(MediaGroup.TYPE_SEARCH);
        addVideos(search);
        assertFalse(containsHandle(search, AI_HANDLE));
        // The warnlist is still off
        assertTrue(containsHandle(search, WARN_HANDLE));

        for (int section = 0; section < AiSListFilterData.SECTION_COUNT; section++) {
            assertTrue(mFilterData.isHideEnabled(AiSListFilterData.LIST_BLOCKLIST, section));
            assertFalse(mFilterData.isSectionChecked(AiSListFilterData.LIST_BLOCKLIST, section));
        }

        mFilterData.setEverythingEnabled(AiSListFilterData.LIST_BLOCKLIST, false);
        VideoGroup search2 = createGroup(MediaGroup.TYPE_SEARCH);
        addVideos(search2);
        assertTrue(containsHandle(search2, AI_HANDLE));
    }

    @Test
    public void eachListHasItsOwnMarkerColor() {
        mFilterData.setMarkColor(AiSListFilterData.LIST_BLOCKLIST, 0xFFFF0000);
        mFilterData.setMarkColor(AiSListFilterData.LIST_WARNLIST, 0xFF2196F3);
        assertEquals(0xFFFF0000, mFilterData.getMarkColor(AiSListFilterData.LIST_BLOCKLIST));
        assertEquals(0xFF2196F3, mFilterData.getMarkColor(AiSListFilterData.LIST_WARNLIST));

        // The same color for both is fine
        mFilterData.setMarkColor(AiSListFilterData.LIST_WARNLIST, 0xFFFF0000);
        assertEquals(0xFFFF0000, mFilterData.getMarkColor(AiSListFilterData.LIST_WARNLIST));

        // A warnlisted channel is marked for the warnlist
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);
        mFilterData.setHideEnabled(AiSListFilterData.LIST_WARNLIST, AiSListFilterData.SECTION_HOME, true);
        setMarkEnabled(true);
        AiSListManager manager = AiSListManager.instance(RuntimeEnvironment.getApplication());
        assertEquals(AiSListFilterData.LIST_BLOCKLIST, manager.getMarkedList(AI_HANDLE, AiSListFilterData.SECTION_HOME));
        assertEquals(AiSListFilterData.LIST_WARNLIST, manager.getMarkedList(WARN_HANDLE, AiSListFilterData.SECTION_HOME));

        mFilterData.setMarkColor(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.MARK_COLOR_OFF);
        mFilterData.setMarkColor(AiSListFilterData.LIST_WARNLIST, AiSListFilterData.MARK_COLOR_OFF);
    }

    @Test
    public void markerShadeDependsOnTheCardState() {
        // Red is fire engine red on both cards
        assertEquals(0xFFCE2029, AiSListFilterData.getMarkShade(AiSListFilterData.MARK_COLOR_RED, false));
        assertEquals(0xFFCE2029, AiSListFilterData.getMarkShade(AiSListFilterData.MARK_COLOR_RED, true));

        int[] colors = {AiSListFilterData.MARK_COLOR_ORANGE, AiSListFilterData.MARK_COLOR_YELLOW,
                AiSListFilterData.MARK_COLOR_GREEN, AiSListFilterData.MARK_COLOR_BLUE};

        for (int color : colors) {
            int normal = AiSListFilterData.getMarkShade(color, false);
            int selected = AiSListFilterData.getMarkShade(color, true);
            assertTrue(normal != selected);
            // The selected card is light, so its shade is darker
            assertTrue(luminance(selected) < luminance(normal));
        }

        // Unknown colors are used as is
        assertEquals(0xFF123456, AiSListFilterData.getMarkShade(0xFF123456, true));
    }

    private static double luminance(int color) {
        return 0.2126 * ((color >> 16) & 0xFF) + 0.7152 * ((color >> 8) & 0xFF) + 0.0722 * (color & 0xFF);
    }

    @Test
    public void eachListMarksOrHidesOnItsOwn() {
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);
        mFilterData.setHideEnabled(AiSListFilterData.LIST_WARNLIST, AiSListFilterData.SECTION_HOME, true);
        mFilterData.setMarkMode(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.MARK_MODE_ALL);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        addVideos(home);

        // The blocklisted channel is marked, the warnlisted one is hidden
        assertEquals(2, home.getSize());
        assertTrue(containsHandle(home, AI_HANDLE));
        assertFalse(containsHandle(home, WARN_HANDLE));

        // History is mark only: only the list with marking applies there
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HISTORY, true);
        mFilterData.setHideEnabled(AiSListFilterData.LIST_WARNLIST, AiSListFilterData.SECTION_HISTORY, true);
        VideoGroup history = createGroup(MediaGroup.TYPE_HISTORY);
        addVideos(history);
        assertEquals(3, history.getSize());
        for (Video video : history.getVideos()) {
            assertEquals(AI_HANDLE.equals(video.channelHandle) ? AiSListFilterData.LIST_BLOCKLIST : -1, video.aiMarkList);
        }
    }

    @Test
    public void markOnlySectionsModeStillHidesElsewhere() {
        setMarkMode(AiSListFilterData.MARK_MODE_MARK_ONLY_SECTIONS);
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HISTORY, true);

        // Home hides and collects the channel
        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        addVideos(home);
        assertEquals(2, home.getSize());
        assertFalse(containsHandle(home, AI_HANDLE));
        assertFalse(containsMarked(home));
        assertEquals(1, AiSListManager.instance(RuntimeEnvironment.getApplication()).getBlockedChannels().size());

        // History marks
        VideoGroup history = createGroup(MediaGroup.TYPE_HISTORY);
        addVideos(history);
        assertEquals(3, history.getSize());
        assertTrue(containsMarked(history));
    }

    @Test
    public void handleMatchIsCaseInsensitive() {
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);

        assertTrue(AiSListManager.instance(RuntimeEnvironment.getApplication()).isHidden("@AISLOPCHANNEL", AiSListFilterData.SECTION_HOME));
        assertFalse(AiSListManager.instance(RuntimeEnvironment.getApplication()).isHidden(null, AiSListFilterData.SECTION_HOME));
    }

    @Test
    public void unrelatedGroupsAreNeverFiltered() {
        assertEquals(-1, AiSListManager.getSection(MediaGroup.TYPE_NOTIFICATIONS, true));
        assertEquals(-1, AiSListManager.getSection(MediaGroup.TYPE_MY_VIDEOS, true));
        assertEquals(-1, AiSListManager.getSection(MediaGroup.TYPE_PLAYBACK_QUEUE, false));
        assertEquals(AiSListFilterData.SECTION_HOME, AiSListManager.getSection(MediaGroup.TYPE_GAMING, true));
        assertEquals(AiSListManager.SECTION_CHANNEL_PAGE, AiSListManager.getSection(MediaGroup.TYPE_CHANNEL, false));
    }

    @Test
    public void hiddenChannelsAreCollectedForTheSidebarSection() {
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);

        addVideos(createGroup(MediaGroup.TYPE_HOME));
        addVideos(createGroup(MediaGroup.TYPE_HOME)); // the same channel isn't added twice

        List<Video> blocked = AiSListManager.instance(RuntimeEnvironment.getApplication()).getBlockedChannels();
        assertEquals(1, blocked.size());
        assertEquals(AI_HANDLE, blocked.get(0).channelHandle);
    }

    @Test
    public void markModeKeepsVideosAndDoesNotCountThemAsBlocked() {
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);
        setMarkEnabled(true);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        addVideos(home);

        assertEquals(3, home.getSize());
        for (Video video : home.getVideos()) {
            assertEquals(AI_HANDLE.equals(video.channelHandle) ? AiSListFilterData.LIST_BLOCKLIST : -1, video.aiMarkList);
        }
        assertTrue(AiSListManager.instance(RuntimeEnvironment.getApplication()).getBlockedChannels().isEmpty());
    }

    @Test
    public void enablingMarkModeClearsBlockedChannels() {
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);
        addVideos(createGroup(MediaGroup.TYPE_HOME));

        setMarkEnabled(true);

        assertTrue(AiSListManager.instance(RuntimeEnvironment.getApplication()).getBlockedChannels().isEmpty());
    }

    private void setMarkEnabled(boolean enable) {
        setMarkMode(enable ? AiSListFilterData.MARK_MODE_ALL : AiSListFilterData.MARK_MODE_OFF);
    }

    private void setMarkMode(int mode) {
        mFilterData.setMarkMode(AiSListFilterData.LIST_BLOCKLIST, mode);
        mFilterData.setMarkMode(AiSListFilterData.LIST_WARNLIST, mode);
    }

    private void disableAll() {
        // Toggling the mark mode also clears the collected channels
        setMarkEnabled(true);
        setMarkEnabled(false);
        for (int list : new int[] {AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.LIST_WARNLIST}) {
            mFilterData.setEverythingEnabled(list, false);
            for (int section = 0; section < AiSListFilterData.SECTION_COUNT; section++) {
                mFilterData.setHideEnabled(list, section, false);
            }
        }
    }

    private static VideoGroup createGroup(int type) {
        return VideoGroup.from(new BrowseSection(type, "Section", BrowseSection.TYPE_GRID, 0));
    }

    private static void addVideos(VideoGroup group) {
        group.add(createVideo("1", AI_HANDLE));
        group.add(createVideo("2", WARN_HANDLE));
        group.add(createVideo("3", HUMAN_HANDLE));
    }

    private static Video createVideo(String videoId, String handle) {
        Video video = new Video();
        video.videoId = videoId;
        video.title = "Video " + videoId;
        video.channelHandle = handle;
        return video;
    }

    private static boolean containsMarked(VideoGroup group) {
        for (Video video : group.getVideos()) {
            if (video.aiMarkList != -1) {
                return true;
            }
        }

        return false;
    }

    private static boolean containsHandle(VideoGroup group, String handle) {
        for (Video video : group.getVideos()) {
            if (handle.equals(video.channelHandle)) {
                return true;
            }
        }

        return false;
    }
}
