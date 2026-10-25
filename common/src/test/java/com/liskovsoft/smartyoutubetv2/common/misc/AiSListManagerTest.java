package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.VideoChannel;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.AiSListFilterData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
        MediaServiceData.instance().setDataApiKey(null);
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
        // The sidebar section, as the playlist opened as a channel
        assertEquals(AiSListFilterData.SECTION_WATCH_LATER, AiSListManager.getSection(MediaGroup.TYPE_WATCH_LATER, true));
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
                AiSListFilterData.MARK_COLOR_GREEN, AiSListFilterData.MARK_COLOR_BLUE, AiSListFilterData.MARK_COLOR_PURPLE};

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

    /**
     * A channel card is never hidden, only marked where the list marks anything
     */
    @Test
    public void channelCardIsOnlyMarked() {
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_SEARCH, true);

        // The list hides and marks nothing: the card is left alone
        VideoGroup search = createGroup(MediaGroup.TYPE_SEARCH);
        search.add(createChannel(AI_HANDLE));
        assertEquals(1, search.getSize());
        assertEquals(-1, search.get(0).aiMarkList);

        // The list hides here and marks in the mark only sections
        setMarkMode(AiSListFilterData.MARK_MODE_MARK_ONLY_SECTIONS);
        VideoGroup search2 = createGroup(MediaGroup.TYPE_SEARCH);
        search2.add(createChannel(AI_HANDLE));
        search2.add(createChannel(HUMAN_HANDLE));
        addVideos(search2);
        assertEquals(4, search2.getSize());
        assertEquals(AiSListFilterData.LIST_BLOCKLIST, search2.get(0).aiMarkList);
        assertEquals(-1, search2.get(1).aiMarkList);
        assertFalse(containsVideo(search2, "1"));

        // Not where the list is off
        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createChannel(AI_HANDLE));
        assertEquals(-1, home.get(0).aiMarkList);
    }

    /**
     * Off, the quick toggle marks the listed videos where they'd be hidden. A mark only section stays as the lists are set.
     */
    @Test
    public void quickToggleOffMarksInsteadOfHiding() {
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HISTORY, true);
        mFilterData.setQuickToggleEnabled(true);
        mFilterData.setQuickToggleHiding(false);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        addVideos(home);

        assertEquals(3, home.getSize());
        for (Video video : home.getVideos()) {
            assertEquals(AI_HANDLE.equals(video.channelHandle) ? AiSListFilterData.LIST_BLOCKLIST : -1, video.aiMarkList);
        }
        // Marked, not blocked
        assertTrue(AiSListManager.instance(RuntimeEnvironment.getApplication()).getBlockedChannels().isEmpty());

        // The lists mark nothing in History (mark mode Off), the quick toggle doesn't either
        VideoGroup history = createGroup(MediaGroup.TYPE_HISTORY);
        addVideos(history);
        assertEquals(3, history.getSize());
        assertFalse(containsMarked(history));
    }

    @Test
    public void quickToggleOnHidesAsTheListsAreSet() {
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);
        mFilterData.setQuickToggleEnabled(true);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        addVideos(home);
        assertEquals(2, home.getSize());
        assertFalse(containsHandle(home, AI_HANDLE));

        // Disabled, the quick toggle's state doesn't count
        mFilterData.setQuickToggleHiding(false);
        mFilterData.setQuickToggleEnabled(false);

        VideoGroup home2 = createGroup(MediaGroup.TYPE_HOME);
        addVideos(home2);
        assertEquals(2, home2.getSize());
        assertFalse(containsHandle(home2, AI_HANDLE));
    }

    @Test
    public void turningTheQuickToggleOffClearsBlockedChannels() {
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);
        mFilterData.setQuickToggleEnabled(true);
        addVideos(createGroup(MediaGroup.TYPE_HOME));
        assertEquals(1, AiSListManager.instance(RuntimeEnvironment.getApplication()).getBlockedChannels().size());

        mFilterData.setQuickToggleHiding(false);

        assertTrue(AiSListManager.instance(RuntimeEnvironment.getApplication()).getBlockedChannels().isEmpty());
    }

    /**
     * The list marks where it hid, so a channel card there is marked too
     */
    @Test
    public void channelCardIsMarkedWhileTheQuickToggleIsOff() {
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_SEARCH, true);
        mFilterData.setQuickToggleEnabled(true);
        mFilterData.setQuickToggleHiding(false);

        VideoGroup search = createGroup(MediaGroup.TYPE_SEARCH);
        search.add(createChannel(AI_HANDLE));
        search.add(createChannel(HUMAN_HANDLE));

        assertEquals(AiSListFilterData.LIST_BLOCKLIST, search.get(0).aiMarkList);
        assertEquals(-1, search.get(1).aiMarkList);
    }

    /**
     * Where the quick toggle changes what a section shows
     */
    @Test
    public void setToHideWhateverTheQuickToggle() {
        AiSListManager manager = AiSListManager.instance(RuntimeEnvironment.getApplication());

        assertFalse(manager.isSetToHide(AiSListFilterData.SECTION_HOME));

        mFilterData.setHideEnabled(AiSListFilterData.LIST_WARNLIST, AiSListFilterData.SECTION_HOME, true);
        assertTrue(manager.isSetToHide(AiSListFilterData.SECTION_HOME));
        assertFalse(manager.isSetToHide(AiSListFilterData.SECTION_SEARCH));

        mFilterData.setQuickToggleEnabled(true);
        mFilterData.setQuickToggleHiding(false);
        assertTrue(manager.isSetToHide(AiSListFilterData.SECTION_HOME));

        // Nothing is hidden from a mark only section
        mFilterData.setHideEnabled(AiSListFilterData.LIST_WARNLIST, AiSListFilterData.SECTION_HISTORY, true);
        setMarkMode(AiSListFilterData.MARK_MODE_MARK_ONLY_SECTIONS);
        assertFalse(manager.isSetToHide(AiSListFilterData.SECTION_HISTORY));
        assertFalse(manager.isSetToHide(AiSListManager.SECTION_CHANNEL_PAGE));
        assertFalse(manager.isSetToHide(-1));

        // Nor by a list that marks everywhere
        setMarkEnabled(true);
        assertFalse(manager.isSetToHide(AiSListFilterData.SECTION_HOME));
    }

    /**
     * A playlist or a show opens like a channel, but it isn't one
     */
    @Test
    public void playlistAndShowCardsAreNotChannelCards() {
        mFilterData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_SEARCH, true);
        setMarkEnabled(true);

        Video playlist = createChannel(AI_HANDLE);
        playlist.itemType = MediaItem.TYPE_PLAYLIST;
        playlist.channelId = "VLPLplaylist";
        Video show = createChannel(AI_HANDLE);
        show.isShow = true;
        show.channelId = "VLPLshow";
        VideoGroup search = createGroup(MediaGroup.TYPE_SEARCH);
        search.add(playlist);
        search.add(show);

        assertEquals(2, search.getSize());
        assertEquals(-1, search.get(0).aiMarkList);
        assertEquals(-1, search.get(1).aiMarkList);
    }

    @Test
    public void savedHandlesKeepEveryChannelInOrder() {
        Map<String, String> handles = new LinkedHashMap<>();
        handles.put("@uploader", "UCuploader");
        handles.put("@guest", null);

        assertEquals("@uploader:UCuploader,@guest", AiSListManager.formatHandles(handles));
        assertEquals(handles, AiSListManager.parseHandles("@uploader:UCuploader,@guest"));
        assertEquals(Arrays.asList("@uploader", "@guest"), new ArrayList<>(AiSListManager.parseHandles("@uploader:UCuploader,@guest").keySet()));
        // Saved before collaborations had them all
        assertEquals(Collections.singletonMap("@handle", null), AiSListManager.parseHandles("@handle"));
        // Not handles
        assertNull(AiSListManager.parseHandles("Channel Name"));
        assertNull(AiSListManager.parseHandles("@handle,Channel"));
        assertNull(AiSListManager.parseHandles("@"));
        assertNull(AiSListManager.parseHandles(""));
    }

    @Test
    public void blocklistedWhateverTheSections() {
        AiSListManager manager = AiSListManager.instance(RuntimeEnvironment.getApplication());

        // Every filter is off
        assertTrue(manager.isBlocklisted(AI_HANDLE));
        assertTrue(manager.isBlocklisted(AI_HANDLE.toUpperCase()));
        assertFalse(manager.isBlocklisted(WARN_HANDLE));
        assertFalse(manager.isBlocklisted(HUMAN_HANDLE));
        assertFalse(manager.isBlocklisted(null));
    }

    @Test
    public void reportedChannelIsRemembered() {
        AiSListManager manager = AiSListManager.instance(RuntimeEnvironment.getApplication());

        assertFalse(manager.isReported("@ReportedOnce"));

        manager.onReported("@ReportedOnce");

        assertTrue(manager.isReported("@reportedonce"));

        // Not a handle
        manager.onReported("Channel Name");

        assertFalse(manager.isReported("Channel Name"));
        assertFalse(manager.isReported(null));
    }

    @Test
    public void onlyTheLastReportedChannelsAreRemembered() {
        AiSListManager manager = AiSListManager.instance(RuntimeEnvironment.getApplication());

        manager.onReported("@ReportedFirst");
        manager.onReported("@ReportedSecond");
        manager.onReported("@ReportedFirst"); // again: the most recent now

        for (int i = 0; i < 149; i++) {
            manager.onReported("@Report" + i);
        }

        assertTrue(manager.isReported("@ReportedFirst"));
        assertFalse(manager.isReported("@ReportedSecond"));
        assertTrue(manager.isReported("@Report0"));
        assertTrue(manager.isReported("@Report148"));
    }

    @Test
    public void handleOfTheCardIsUsedWithoutLookup() {
        AiSListManager manager = AiSListManager.instance(RuntimeEnvironment.getApplication());
        List<String> lookedUp = new ArrayList<>();
        manager.setHandleLookupForTesting(videoId -> {
            lookedUp.add(videoId);
            return Observable.just(Collections.singletonMap("@Other", null));
        });
        Video video = createVideo("1", HUMAN_HANDLE);
        video.channelId = "UCreal";

        assertEquals(Collections.singletonMap(HUMAN_HANDLE, "UCreal"), manager.getChannelHandles(video).blockingFirst());
        assertTrue(lookedUp.isEmpty());
    }

    @Test
    public void everyChannelOfCollaborationIsFound() {
        AiSListManager manager = AiSListManager.instance(RuntimeEnvironment.getApplication());
        Map<String, String> handles = new LinkedHashMap<>();
        handles.put("@fexl", "UCFlnKDqG512KkbDvPa2ybRw");
        handles.put("@Frequensyt", null);
        List<String> lookedUp = new ArrayList<>();
        manager.setHandleLookupForTesting(videoId -> {
            lookedUp.add(videoId);
            return Observable.just(handles);
        });
        Video video = createVideo("collab", null);
        video.author = "Fexl and Frequensi";

        assertEquals(handles, manager.getChannelHandles(video).blockingFirst());

        // Kept: the card that has the handle of one of them gets both, without another lookup
        video.channelHandle = "@fexl";

        assertEquals(handles, manager.getChannelHandles(video).blockingFirst());
        assertEquals(Collections.singletonList("collab"), lookedUp);
    }

    @Test
    public void cardWithoutChannelNameIsLookedUpByVideo() {
        AiSListManager manager = AiSListManager.instance(RuntimeEnvironment.getApplication());
        manager.setHandleLookupForTesting(videoId -> Observable.just(Collections.singletonMap("@" + videoId, null)));

        assertEquals(Collections.singletonMap("@nameless", null), manager.getChannelHandles(createVideo("nameless", null)).blockingFirst());
    }

    @Test
    public void noHandleWhenLookupFails() {
        AiSListManager manager = AiSListManager.instance(RuntimeEnvironment.getApplication());
        manager.setHandleLookupForTesting(videoId -> Observable.error(new IllegalStateException("Offline")));
        Video video = createVideo("1", null);
        video.author = "Someone";

        assertTrue(manager.getChannelHandles(video).blockingFirst().isEmpty());

        // A card without a video has nothing to look up with
        Video channel = new Video();
        channel.author = "Someone Else";

        assertTrue(manager.getChannelHandles(channel).blockingFirst().isEmpty());
    }

    @Test
    public void channelsAreLookedUpTogetherWithDataApiKey() {
        MediaServiceData.instance().setDataApiKey("key");
        AiSListManager manager = AiSListManager.instance(RuntimeEnvironment.getApplication());
        List<String> lookedUp = new ArrayList<>();
        manager.setHandleLookupForTesting(videoId -> {
            lookedUp.add(videoId);
            return Observable.just(Collections.singletonMap("@" + videoId, null));
        });
        List<List<String>> batches = new ArrayList<>();
        manager.setBatchHandleLookupForTesting(videoIds -> {
            batches.add(videoIds);
            Map<String, VideoChannel> found = new LinkedHashMap<>();
            found.put("a", createChannel("UCalpha", "@Alpha", "Alpha"));
            found.put("b", createChannel("UCbeta", "@Beta", "Beta"));
            return Observable.just(found);
        });
        Map<String, String> videoIdByKey = new LinkedHashMap<>();
        videoIdByKey.put("Alpha", "a");
        videoIdByKey.put("Beta", "b");
        videoIdByKey.put("Gamma", "g"); // its channel has no handle the Data API knows

        manager.resolveHandles(videoIdByKey).blockingFirst();

        assertEquals(Collections.singletonList(Arrays.asList("a", "b", "g")), batches);
        // The one the batch didn't find is looked up on its own
        assertEquals(Collections.singletonList("g"), lookedUp);
        assertEquals("@Alpha", manager.getCachedHandle("Alpha", -1));
        assertEquals("@Beta", manager.getCachedHandle("Beta", -1));
        assertEquals("@g", manager.getCachedHandle("Gamma", -1));

        // Known now: no lookup at all
        manager.resolveHandles(videoIdByKey).blockingFirst();

        assertEquals(1, batches.size());
        assertEquals(1, lookedUp.size());
    }

    /**
     * "Dan and Phil" is one channel: the uploader is called that
     */
    @Test
    public void channelWithAndInItsNameIsLookedUpTogether() {
        MediaServiceData.instance().setDataApiKey("key");
        AiSListManager manager = AiSListManager.instance(RuntimeEnvironment.getApplication());
        List<String> lookedUp = new ArrayList<>();
        manager.setHandleLookupForTesting(videoId -> {
            lookedUp.add(videoId);
            return Observable.just(Collections.singletonMap("@" + videoId, null));
        });
        manager.setBatchHandleLookupForTesting(videoIds ->
                Observable.just(Collections.singletonMap("duo", createChannel("UCdanandphil", "@danandphil", "Dan and Phil"))));

        manager.resolveHandles(Collections.singletonMap("Dan and Phil", "duo")).blockingFirst();

        assertTrue(lookedUp.isEmpty());
        assertEquals("@danandphil", manager.getCachedHandle("Dan and Phil", -1));
    }

    /**
     * The uploader is one side of the "and", the other side's handle was found on its own cards
     */
    @Test
    public void collaborationOfKnownChannelNeedsNoWatchNext() {
        MediaServiceData.instance().setDataApiKey("key");
        AiSListManager manager = AiSListManager.instance(RuntimeEnvironment.getApplication());
        List<String> lookedUp = new ArrayList<>();
        manager.setHandleLookupForTesting(videoId -> {
            lookedUp.add(videoId);
            return Observable.just(Collections.singletonMap("@" + videoId, null));
        });
        Map<String, VideoChannel> found = new LinkedHashMap<>();
        found.put("solo", createChannel("UCfrequensi", "@Frequensyt", "Frequensi"));
        found.put("collab", createChannel("UCfexl", "@fexl", "Fexl"));
        manager.setBatchHandleLookupForTesting(videoIds -> Observable.just(found));

        manager.resolveHandles(Collections.singletonMap("Frequensi", "solo")).blockingFirst();
        manager.resolveHandles(Collections.singletonMap("Fexl and Frequensi", "collab")).blockingFirst();

        Map<String, String> handles = new LinkedHashMap<>(); // the uploader first
        handles.put("@fexl", "UCfexl");
        handles.put("@Frequensyt", "UCfrequensi");
        Video video = createVideo("collab", null);
        video.author = "Fexl and Frequensi";

        assertEquals(handles, manager.getChannelHandles(video).blockingFirst());
        assertTrue(lookedUp.isEmpty());
    }

    /**
     * Only watch-next has the channel the uploader collaborated with
     */
    @Test
    public void collaborationOfUnknownChannelIsLookedUpByWatchNext() {
        MediaServiceData.instance().setDataApiKey("key");
        AiSListManager manager = AiSListManager.instance(RuntimeEnvironment.getApplication());
        List<String> lookedUp = new ArrayList<>();
        Map<String, String> collaborators = new LinkedHashMap<>();
        collaborators.put("@fexl", "UCfexl");
        collaborators.put("@Frequensyt", null);
        manager.setHandleLookupForTesting(videoId -> {
            lookedUp.add(videoId);
            return Observable.just(collaborators);
        });
        manager.setBatchHandleLookupForTesting(videoIds ->
                Observable.just(Collections.singletonMap("collab", createChannel("UCfexl", "@fexl", "Fexl"))));

        manager.resolveHandles(Collections.singletonMap("Fexl and Frequensi", "collab")).blockingFirst();

        assertEquals(Collections.singletonList("collab"), lookedUp);
        Video video = createVideo("collab", null);
        video.author = "Fexl and Frequensi";
        assertEquals(collaborators, manager.getChannelHandles(video).blockingFirst());
    }

    @Test
    public void channelsAreLookedUpOneByOneWithoutDataApiKey() {
        MediaServiceData.instance().setDataApiKey(null);
        AiSListManager manager = AiSListManager.instance(RuntimeEnvironment.getApplication());
        List<String> lookedUp = new ArrayList<>();
        manager.setHandleLookupForTesting(videoId -> {
            lookedUp.add(videoId);
            return Observable.just(Collections.singletonMap("@" + videoId, null));
        });
        List<List<String>> batches = new ArrayList<>();
        manager.setBatchHandleLookupForTesting(videoIds -> {
            batches.add(videoIds);
            return Observable.just(Collections.<String, VideoChannel>emptyMap());
        });
        Map<String, String> videoIdByKey = new LinkedHashMap<>();
        videoIdByKey.put("Alpha", "a");
        videoIdByKey.put("Beta", "b");

        manager.resolveHandles(videoIdByKey).blockingFirst();

        assertTrue(batches.isEmpty());
        assertEquals(Arrays.asList("a", "b"), lookedUp);
    }

    @Test
    public void failedBatchFallsBackToOneLookupPerChannel() {
        MediaServiceData.instance().setDataApiKey("key");
        AiSListManager manager = AiSListManager.instance(RuntimeEnvironment.getApplication());
        List<String> lookedUp = new ArrayList<>();
        manager.setHandleLookupForTesting(videoId -> {
            lookedUp.add(videoId);
            return Observable.just(Collections.singletonMap("@" + videoId, null));
        });
        manager.setBatchHandleLookupForTesting(videoIds -> Observable.error(new IllegalStateException("Offline")));
        Map<String, String> videoIdByKey = new LinkedHashMap<>();
        videoIdByKey.put("Alpha", "a");
        videoIdByKey.put("Beta", "b");

        manager.resolveHandles(videoIdByKey).blockingFirst();

        assertEquals(Arrays.asList("a", "b"), lookedUp);
        assertEquals("@a", manager.getCachedHandle("Alpha", -1));
    }

    @Test
    public void channelBeingLookedUpIsNotBatchedAgain() {
        MediaServiceData.instance().setDataApiKey("key");
        AiSListManager manager = AiSListManager.instance(RuntimeEnvironment.getApplication());
        manager.setHandleLookupForTesting(videoId -> Observable.just(Collections.singletonMap("@" + videoId, null)));
        PublishSubject<Map<String, VideoChannel>> firstBatch = PublishSubject.create(); // ends when the test says
        List<List<String>> batches = new ArrayList<>();
        manager.setBatchHandleLookupForTesting(videoIds -> {
            batches.add(videoIds);
            return batches.size() == 1 ? firstBatch : Observable.just(Collections.<String, VideoChannel>emptyMap());
        });
        List<Boolean> resolved = new ArrayList<>();

        manager.resolveHandles(Collections.singletonMap("Alpha", "a")).subscribe(resolved::add);

        Map<String, String> videoIdByKey = new LinkedHashMap<>();
        videoIdByKey.put("Alpha", "a");
        videoIdByKey.put("Beta", "b");
        manager.resolveHandles(videoIdByKey).subscribe(resolved::add);

        // The second waits for the first's lookup of Alpha
        assertEquals(Arrays.asList(Collections.singletonList("a"), Collections.singletonList("b")), batches);
        assertTrue(resolved.isEmpty());

        firstBatch.onNext(Collections.singletonMap("a", createChannel("UCalpha", "@Alpha", "Alpha")));
        firstBatch.onComplete();

        assertEquals(Arrays.asList(true, true), resolved);
        assertEquals("@Alpha", manager.getCachedHandle("Alpha", -1));
    }

    private static VideoChannel createChannel(String channelId, String handle, String title) {
        return new VideoChannel() {
            @Override
            public String getChannelId() {
                return channelId;
            }

            @Override
            public String getHandle() {
                return handle;
            }

            @Override
            public String getTitle() {
                return title;
            }
        };
    }

    private void setMarkEnabled(boolean enable) {
        setMarkMode(enable ? AiSListFilterData.MARK_MODE_ALL : AiSListFilterData.MARK_MODE_OFF);
    }

    private void setMarkMode(int mode) {
        mFilterData.setMarkMode(AiSListFilterData.LIST_BLOCKLIST, mode);
        mFilterData.setMarkMode(AiSListFilterData.LIST_WARNLIST, mode);
    }

    private void disableAll() {
        mFilterData.setQuickToggleEnabled(false);
        mFilterData.setQuickToggleHiding(true);
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

    /**
     * A channel search result: no video, the channel's handle
     */
    private static Video createChannel(String handle) {
        Video video = new Video();
        video.channelId = "UC" + handle.substring(1);
        video.title = handle.substring(1);
        video.channelHandle = handle;
        return video;
    }

    private static boolean containsVideo(VideoGroup group, String videoId) {
        for (Video video : group.getVideos()) {
            if (videoId.equals(video.videoId)) {
                return true;
            }
        }

        return false;
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
