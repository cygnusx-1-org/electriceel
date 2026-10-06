package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SimpleMediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.AiSListFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.CollaborationsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLaterData;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HiddenVideoResolverTest {
    private static final String COLLABORATION = "Sidemen and Jesser";
    private static final String DUO = "Dan and Phil"; // one channel
    private static final String AI_CHANNEL = "Slop Studio";
    private static final String AI_HANDLE = "@SlopStudio";
    private static final String HUMAN_CHANNEL = "Real Person";
    private static final String HUMAN_HANDLE = "@RealPerson";
    private static final String AI_COLLABORATION = HUMAN_CHANNEL + " and " + AI_CHANNEL; // the AI channel is the guest
    private static final Map<String, List<String>> SEARCH_RESULTS = new HashMap<>();
    private static final Map<String, Map<String, String>> HANDLES = new HashMap<>(); // video id -> its owners' handles -> their channel ids

    static {
        SEARCH_RESULTS.put(COLLABORATION, Arrays.asList("Sidemen", "Jesser"));
        SEARCH_RESULTS.put("Sidemen", Arrays.asList("Sidemen", "MoreSidemen"));
        SEARCH_RESULTS.put("Jesser", Arrays.asList("Jesser", "Team Jesser"));
        SEARCH_RESULTS.put(DUO, Collections.singletonList(DUO));
        HANDLES.put("ai", Collections.singletonMap(AI_HANDLE, "UCai"));
        HANDLES.put("ai2", Collections.singletonMap(AI_HANDLE, "UCai"));
        HANDLES.put("human", Collections.singletonMap(HUMAN_HANDLE, "UChuman"));
        Map<String, String> collaborators = new LinkedHashMap<>(); // the uploader first
        collaborators.put(HUMAN_HANDLE, "UChuman");
        collaborators.put(AI_HANDLE, "UCai");
        HANDLES.put("aicollab", collaborators);
    }

    private final Context mContext = RuntimeEnvironment.getApplication();
    private final List<String> mSearches = new ArrayList<>();
    private final List<String> mHandleLookups = new ArrayList<>();
    private int mWatchLaterLoads;
    private CollaborationsData mCollaborationsData;
    private WatchLaterData mWatchLaterData;
    private AiSListFilterData mAiSListData;
    private boolean mIsNetworkDown;

    @Before
    public void setUp() {
        GlobalPreferences.instance(mContext);
        mCollaborationsData = CollaborationsData.instance(mContext);
        mCollaborationsData.setMode(CollaborationsData.MODE_SHOW);
        mCollaborationsData.setSectionEnabled(MediaGroup.TYPE_HOME, true);
        mCollaborationsData.setSectionEnabled(MediaGroup.TYPE_GAMING, false);
        CollaborationManager.instance(mContext).resetForTesting(this::search);

        mWatchLaterData = WatchLaterData.instance(mContext);
        mWatchLaterData.setMode(WatchLaterData.MODE_SHOW);
        mWatchLaterData.setSectionEnabled(MediaGroup.TYPE_HOME, true);
        mWatchLaterData.setSectionEnabled(MediaGroup.TYPE_GAMING, false);
        WatchLaterManager.instance(mContext).resetForTesting(this::loadWatchLater, () -> "one@example.com");

        mAiSListData = AiSListFilterData.instance(mContext);
        disableAiSList();
        // Toggling the mark mode also clears the channels collected for Blocked AI Channels
        mAiSListData.setMarkMode(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.MARK_MODE_ALL);
        mAiSListData.setMarkMode(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.MARK_MODE_OFF);
        AiSListManager aiSListManager = AiSListManager.instance(mContext);
        aiSListManager.setListsForTesting(new HashSet<>(Collections.singletonList(AI_HANDLE.toLowerCase())), Collections.emptySet());
        aiSListManager.setHandleLookupForTesting(this::lookupHandle);
    }

    @After
    public void tearDown() {
        mCollaborationsData.setMode(CollaborationsData.MODE_SHOW);
        mWatchLaterData.setMode(WatchLaterData.MODE_SHOW);
        disableAiSList();
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void collaborationNeverAppears() {
        PublishSubject<List<String>> firstSearch = PublishSubject.create(); // ends when the test says
        CollaborationManager.instance(mContext).resetForTesting(query -> COLLABORATION.equals(query) ? firstSearch : search(query));
        mCollaborationsData.setMode(CollaborationsData.MODE_HIDE);
        BrowseSection home = createSection(MediaGroup.TYPE_HOME);
        List<MediaGroup> shown = new ArrayList<>();

        HiddenVideoResolver.resolveGroup(mContext, Observable.just(createRow(createVideo("collab", COLLABORATION), createVideo("solo", "Sidemen"))), home)
                .subscribe(shown::add);

        // Held while the names are searched
        assertTrue(shown.isEmpty());

        firstSearch.onNext(SEARCH_RESULTS.get(COLLABORATION));
        firstSearch.onComplete();

        assertEquals(1, shown.size());
        VideoGroup group = VideoGroup.from(shown.get(0), home);
        assertEquals(1, group.getSize());
        assertEquals("solo", group.get(0).videoId);
    }

    @Test
    public void collaborationIsMarkedWhenShown() {
        mCollaborationsData.setMode(CollaborationsData.MODE_MARK);
        BrowseSection home = createSection(MediaGroup.TYPE_HOME);
        List<MediaGroup> shown = new ArrayList<>();

        HiddenVideoResolver.resolveGroup(mContext, Observable.just(createRow(createVideo("collab", COLLABORATION), createVideo("duo", DUO))), home)
                .subscribe(shown::add);

        VideoGroup group = VideoGroup.from(shown.get(0), home);
        assertEquals(2, group.getSize());
        assertTrue(group.get(0).isCollaboration);
        assertFalse(group.get(1).isCollaboration);
    }

    @Test
    public void watchLaterVideoNeverAppears() {
        PublishSubject<List<String>> load = PublishSubject.create(); // ends when the test says
        WatchLaterManager.instance(mContext).resetForTesting(() -> load, () -> "one@example.com");
        mWatchLaterData.setMode(WatchLaterData.MODE_HIDE);
        BrowseSection home = createSection(MediaGroup.TYPE_HOME);
        List<MediaGroup> shown = new ArrayList<>();

        HiddenVideoResolver.resolveGroup(mContext, Observable.just(createRow(createVideo("later", HUMAN_CHANNEL), createVideo("fresh", HUMAN_CHANNEL))), home)
                .subscribe(shown::add);

        // Held while Watch later is read
        assertTrue(shown.isEmpty());

        load.onNext(Collections.singletonList("later"));
        load.onComplete();

        assertEquals(1, shown.size());
        VideoGroup group = VideoGroup.from(shown.get(0), home);
        assertEquals(1, group.getSize());
        assertEquals("fresh", group.get(0).videoId);
    }

    @Test
    public void watchLaterVideoIsMarkedWhenShown() {
        mWatchLaterData.setMode(WatchLaterData.MODE_MARK);
        BrowseSection home = createSection(MediaGroup.TYPE_HOME);
        List<MediaGroup> shown = new ArrayList<>();

        HiddenVideoResolver.resolveGroup(mContext, Observable.just(createRow(createVideo("later", HUMAN_CHANNEL), createVideo("fresh", HUMAN_CHANNEL))), home)
                .subscribe(shown::add);

        VideoGroup group = VideoGroup.from(shown.get(0), home);
        assertEquals(2, group.getSize());
        assertTrue(group.get(0).isInWatchLater);
        assertFalse(group.get(1).isInWatchLater);
    }

    @Test
    public void listedChannelNeverAppears() {
        PublishSubject<Map<String, String>> aiLookup = PublishSubject.create();
        AiSListManager.instance(mContext).setHandleLookupForTesting(videoId -> "ai".equals(videoId) ? aiLookup : lookupHandle(videoId));
        mAiSListData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);
        BrowseSection home = createSection(MediaGroup.TYPE_HOME);
        List<MediaGroup> shown = new ArrayList<>();

        HiddenVideoResolver.resolveGroup(mContext, Observable.just(createRow(createVideo("ai", AI_CHANNEL), createVideo("human", HUMAN_CHANNEL))), home)
                .subscribe(shown::add);

        // Held while the handles are looked up
        assertTrue(shown.isEmpty());

        aiLookup.onNext(HANDLES.get("ai"));
        aiLookup.onComplete();

        assertEquals(1, shown.size());
        VideoGroup group = VideoGroup.from(shown.get(0), home);
        assertEquals(1, group.getSize());
        assertEquals("human", group.get(0).videoId);
    }

    @Test
    public void listedChannelNeverAppearsInSuggestions() {
        PublishSubject<Map<String, String>> aiLookup = PublishSubject.create();
        AiSListManager.instance(mContext).setHandleLookupForTesting(videoId -> "ai".equals(videoId) ? aiLookup : lookupHandle(videoId));
        mAiSListData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_SUGGESTIONS, true);
        MediaGroup suggestions = new TestMediaGroup(MediaGroup.TYPE_SUGGESTIONS, createVideo("ai", AI_CHANNEL), createVideo("human", HUMAN_CHANNEL));
        List<List<MediaGroup>> shown = new ArrayList<>();

        // The player's rows have no sidebar section (see SuggestionsController)
        HiddenVideoResolver.resolveGroups(mContext, Observable.just(Collections.singletonList(suggestions)), null).subscribe(shown::add);

        assertTrue(shown.isEmpty());

        aiLookup.onNext(HANDLES.get("ai"));
        aiLookup.onComplete();

        VideoGroup group = VideoGroup.from(shown.get(0).get(0));
        assertEquals(1, group.getSize());
        assertEquals("human", group.get(0).videoId);
    }

    @Test
    public void eachChannelIsLookedUpOnce() {
        mAiSListData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);
        mCollaborationsData.setMode(CollaborationsData.MODE_HIDE);
        BrowseSection home = createSection(MediaGroup.TYPE_HOME);
        MediaGroup row = createRow(createVideo("ai", AI_CHANNEL), createVideo("ai2", AI_CHANNEL), createVideo("human", HUMAN_CHANNEL));

        HiddenVideoResolver.resolveGroup(mContext, Observable.just(row), home).subscribe();
        HiddenVideoResolver.resolveGroup(mContext, Observable.just(row), home).subscribe();

        // One lookup per channel name, not per card, and none the second time
        assertEquals(Arrays.asList("ai", "human"), mHandleLookups);
        // Channels without "and" are never searched
        assertTrue(mSearches.isEmpty());
    }

    @Test
    public void failedLookupsShowTheGroup() {
        mIsNetworkDown = true;
        mAiSListData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);
        mCollaborationsData.setMode(CollaborationsData.MODE_HIDE);
        mWatchLaterData.setMode(WatchLaterData.MODE_HIDE);
        BrowseSection home = createSection(MediaGroup.TYPE_HOME);
        List<MediaGroup> shown = new ArrayList<>();

        HiddenVideoResolver.resolveGroup(mContext, Observable.just(createRow(createVideo("ai", AI_CHANNEL), createVideo("collab", COLLABORATION),
                createVideo("later", HUMAN_CHANNEL))), home)
                .subscribe(shown::add);

        assertEquals(1, shown.size());
        assertEquals(3, VideoGroup.from(shown.get(0), home).getSize());
    }

    @Test
    public void groupIsNotHeldWhereNothingApplies() {
        MediaGroup row = createRow(createVideo("ai", AI_CHANNEL), createVideo("collab", COLLABORATION));
        List<MediaGroup> shown = new ArrayList<>();

        // Both are off
        HiddenVideoResolver.resolveGroup(mContext, Observable.just(row), createSection(MediaGroup.TYPE_HOME)).subscribe(shown::add);

        // Not picked there: collaborations and Watch later only in Home, AiSList only in Search
        mCollaborationsData.setMode(CollaborationsData.MODE_HIDE);
        mWatchLaterData.setMode(WatchLaterData.MODE_HIDE);
        mAiSListData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_SEARCH, true);
        HiddenVideoResolver.resolveGroup(mContext, Observable.just(row), createSection(MediaGroup.TYPE_GAMING)).subscribe(shown::add);

        // No sidebar section, and neither search nor a channel page
        HiddenVideoResolver.resolveGroup(mContext, Observable.just(row), null).subscribe(shown::add);

        assertEquals(3, shown.size());
        assertTrue(mSearches.isEmpty());
        assertTrue(mHandleLookups.isEmpty());
        assertEquals(0, mWatchLaterLoads);
    }

    @Test
    public void rowsKeepTheirOrder() {
        PublishSubject<List<String>> firstSearch = PublishSubject.create();
        CollaborationManager.instance(mContext).resetForTesting(query -> COLLABORATION.equals(query) ? firstSearch : search(query));
        mCollaborationsData.setMode(CollaborationsData.MODE_HIDE);
        List<MediaGroup> first = Collections.singletonList(createRow(createVideo("collab", COLLABORATION)));
        List<MediaGroup> second = Collections.singletonList(createRow(createVideo("solo", "Sidemen")));
        List<List<MediaGroup>> shown = new ArrayList<>();

        HiddenVideoResolver.resolveGroups(mContext, Observable.just(first, second), createSection(MediaGroup.TYPE_HOME)).subscribe(shown::add);

        // The second needs nothing but waits for the first
        assertTrue(shown.isEmpty());

        firstSearch.onNext(SEARCH_RESULTS.get(COLLABORATION));
        firstSearch.onComplete();

        assertEquals(Arrays.asList(first, second), shown);
    }

    /**
     * Search and channel pages have no sidebar section: they're picked in the setting as their own sections
     */
    @Test
    public void collaborationIsMarkedInSearchAndOnChannelPages() {
        mCollaborationsData.setMode(CollaborationsData.MODE_MARK);
        List<List<MediaGroup>> shown = new ArrayList<>();
        MediaGroup search = createGroup(MediaGroup.TYPE_SEARCH, null, createVideo("collab", COLLABORATION), createVideo("duo", DUO));
        MediaGroup channel = createGroup(MediaGroup.TYPE_CHANNEL, "UCsidemen", createVideo("collab", COLLABORATION));

        HiddenVideoResolver.resolveGroups(mContext, Observable.just(Arrays.asList(search, channel)), null).subscribe(shown::add);

        VideoGroup searchGroup = VideoGroup.from(shown.get(0).get(0));
        assertEquals(2, searchGroup.getSize());
        assertTrue(searchGroup.get(0).isCollaboration);
        assertFalse(searchGroup.get(1).isCollaboration);
        assertTrue(VideoGroup.from(shown.get(0).get(1)).get(0).isCollaboration);
    }

    @Test
    public void collaborationNeverAppearsOnChannelPage() {
        mCollaborationsData.setMode(CollaborationsData.MODE_HIDE);
        List<MediaGroup> shown = new ArrayList<>();

        HiddenVideoResolver.resolveGroup(mContext, Observable.just(createGroup(MediaGroup.TYPE_CHANNEL_UPLOADS, "UCsidemen",
                createVideo("collab", COLLABORATION), createVideo("solo", "Sidemen"))), null).subscribe(shown::add);

        VideoGroup group = VideoGroup.from(shown.get(0));
        assertEquals(1, group.getSize());
        assertEquals("solo", group.get(0).videoId);
    }

    @Test
    public void searchAndChannelPagesFollowTheirSwitches() {
        mCollaborationsData.setMode(CollaborationsData.MODE_MARK);
        mCollaborationsData.setSectionEnabled(MediaGroup.TYPE_SEARCH, false);
        mCollaborationsData.setSectionEnabled(MediaGroup.TYPE_CHANNEL, false);
        List<MediaGroup> shown = new ArrayList<>();

        try {
            HiddenVideoResolver.resolveGroup(mContext, Observable.just(createGroup(MediaGroup.TYPE_SEARCH, null, createVideo("collab", COLLABORATION))), null)
                    .subscribe(shown::add);
            HiddenVideoResolver.resolveGroup(mContext, Observable.just(createGroup(MediaGroup.TYPE_CHANNEL, "UCsidemen", createVideo("collab", COLLABORATION))), null)
                    .subscribe(shown::add);

            assertTrue(mSearches.isEmpty());
            assertFalse(VideoGroup.from(shown.get(0)).get(0).isCollaboration);
            assertFalse(VideoGroup.from(shown.get(1)).get(0).isCollaboration);
        } finally {
            mCollaborationsData.setSectionEnabled(MediaGroup.TYPE_SEARCH, true);
            mCollaborationsData.setSectionEnabled(MediaGroup.TYPE_CHANNEL, true);
        }
    }

    /**
     * A playlist page opens like a channel page, and the player's suggestions have no section either: neither is a section of the setting
     */
    @Test
    public void playlistPagesAndSuggestionsAreNotChannelPages() {
        mCollaborationsData.setMode(CollaborationsData.MODE_HIDE);
        mWatchLaterData.setMode(WatchLaterData.MODE_HIDE);
        List<MediaGroup> shown = new ArrayList<>();

        HiddenVideoResolver.resolveGroup(mContext, Observable.just(createGroup(MediaGroup.TYPE_CHANNEL_UPLOADS, "VLPLxxxx",
                createVideo("collab", COLLABORATION), createVideo("later", HUMAN_CHANNEL))), null).subscribe(shown::add);
        HiddenVideoResolver.resolveGroup(mContext, Observable.just(createGroup(MediaGroup.TYPE_SUGGESTIONS, null,
                createVideo("collab", COLLABORATION), createVideo("later", HUMAN_CHANNEL))), null).subscribe(shown::add);

        assertTrue(mSearches.isEmpty());
        assertEquals(0, mWatchLaterLoads);
        assertEquals(2, VideoGroup.from(shown.get(0)).getSize());
        assertEquals(2, VideoGroup.from(shown.get(1)).getSize());
    }

    @Test
    public void watchLaterVideoIsMarkedInSearch() {
        mWatchLaterData.setMode(WatchLaterData.MODE_MARK);
        List<MediaGroup> shown = new ArrayList<>();

        HiddenVideoResolver.resolveGroup(mContext, Observable.just(createGroup(MediaGroup.TYPE_SEARCH, null,
                createVideo("later", HUMAN_CHANNEL), createVideo("fresh", HUMAN_CHANNEL))), null).subscribe(shown::add);

        VideoGroup group = VideoGroup.from(shown.get(0));
        assertTrue(group.get(0).isInWatchLater);
        assertFalse(group.get(1).isInWatchLater);
    }

    /**
     * A collaboration's owner links to no channel: each of its channels counts, the guest too
     */
    @Test
    public void collaborationWithListedGuestNeverAppears() {
        mAiSListData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);
        BrowseSection home = createSection(MediaGroup.TYPE_HOME);
        Video collaboration = createVideo("aicollab", AI_COLLABORATION);
        collaboration.channelId = "UChuman"; // the card has the uploader's
        List<MediaGroup> shown = new ArrayList<>();

        HiddenVideoResolver.resolveGroup(mContext, Observable.just(createRow(collaboration, createVideo("human", HUMAN_CHANNEL))), home)
                .subscribe(shown::add);

        VideoGroup group = VideoGroup.from(shown.get(0), home);
        assertEquals(1, group.getSize());
        assertEquals("human", group.get(0).videoId);

        // Blocked AI Channels opens the guest, not the uploader of the card
        Video blocked = AiSListManager.instance(mContext).getBlockedChannels().get(0);
        assertEquals(AI_HANDLE, blocked.channelHandle);
        assertEquals("UCai", blocked.channelId);
        assertEquals(AI_HANDLE, blocked.title);
    }

    @Test
    public void collaborationWithListedGuestIsMarkedInSearch() {
        mAiSListData.setHideEnabled(AiSListFilterData.LIST_WARNLIST, AiSListFilterData.SECTION_SEARCH, true);
        mAiSListData.setMarkMode(AiSListFilterData.LIST_WARNLIST, AiSListFilterData.MARK_MODE_ALL);
        AiSListManager.instance(mContext).setListsForTesting(Collections.emptySet(), new HashSet<>(Collections.singletonList(AI_HANDLE.toLowerCase())));
        List<MediaGroup> shown = new ArrayList<>();

        try {
            HiddenVideoResolver.resolveGroup(mContext, Observable.just(createGroup(MediaGroup.TYPE_SEARCH, null,
                    createVideo("aicollab", AI_COLLABORATION), createVideo("human", HUMAN_CHANNEL))), null).subscribe(shown::add);

            VideoGroup group = VideoGroup.from(shown.get(0));
            assertEquals(2, group.getSize());
            assertEquals(AiSListFilterData.LIST_WARNLIST, group.get(0).aiMarkList);
            assertEquals(AI_HANDLE, group.get(0).channelHandle);
            assertEquals(-1, group.get(1).aiMarkList);
        } finally {
            mAiSListData.setMarkMode(AiSListFilterData.LIST_WARNLIST, AiSListFilterData.MARK_MODE_OFF);
        }
    }

    /**
     * A channel card has no video to look its handle up by, but its name was seen on a video
     */
    @Test
    public void channelCardIsMarkedByTheHandleOfItsName() {
        mAiSListData.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);
        BrowseSection home = createSection(MediaGroup.TYPE_HOME);
        Video channel = new Video();
        channel.channelId = "UCai";
        channel.title = AI_CHANNEL;
        List<MediaGroup> shown = new ArrayList<>();

        HiddenVideoResolver.resolveGroup(mContext, Observable.just(createRow(createVideo("ai", AI_CHANNEL), channel)), home).subscribe(shown::add);

        // The video is hidden, the channel only marked, though the list hides there
        mAiSListData.setMarkMode(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.MARK_MODE_MARK_ONLY_SECTIONS);

        try {
            VideoGroup group = VideoGroup.from(shown.get(0), home);
            assertEquals(1, group.getSize());
            assertEquals("UCai", group.get(0).channelId);
            assertEquals(AiSListFilterData.LIST_BLOCKLIST, group.get(0).aiMarkList);
            assertEquals(Collections.singletonList("ai"), mHandleLookups);
        } finally {
            mAiSListData.setMarkMode(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.MARK_MODE_OFF);
        }
    }

    private Observable<List<String>> search(String query) {
        mSearches.add(query);

        if (mIsNetworkDown) {
            return Observable.error(new IllegalStateException("No network"));
        }

        List<String> names = SEARCH_RESULTS.get(query);

        return Observable.just(names != null ? names : Collections.emptyList());
    }

    private Observable<List<String>> loadWatchLater() {
        mWatchLaterLoads++;

        if (mIsNetworkDown) {
            return Observable.error(new IllegalStateException("No network"));
        }

        return Observable.just(Collections.singletonList("later"));
    }

    private Observable<Map<String, String>> lookupHandle(String videoId) {
        mHandleLookups.add(videoId);

        if (mIsNetworkDown || !HANDLES.containsKey(videoId)) {
            return Observable.error(new IllegalStateException("No handle"));
        }

        return Observable.just(HANDLES.get(videoId));
    }

    private void disableAiSList() {
        for (int list : new int[] {AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.LIST_WARNLIST}) {
            mAiSListData.setEverythingEnabled(list, false);

            for (int section = 0; section < AiSListFilterData.SECTION_COUNT; section++) {
                mAiSListData.setHideEnabled(list, section, false);
            }
        }
    }

    private static BrowseSection createSection(int type) {
        return new BrowseSection(type, "Section", BrowseSection.TYPE_GRID, 0);
    }

    private static MediaGroup createRow(Video... videos) {
        return new TestMediaGroup(MediaGroup.TYPE_HOME, videos);
    }

    /**
     * A search row or a channel page: no sidebar section
     */
    private static MediaGroup createGroup(int type, String channelId, Video... videos) {
        return new TestMediaGroup(type, channelId, videos);
    }

    /**
     * A name-only card, as TV Home has: no handle
     */
    private static Video createVideo(String videoId, String author) {
        Video video = new Video();
        video.videoId = videoId;
        video.title = "Video " + videoId;
        video.author = author;
        video.secondTitle = author + " • 5K views • 3 weeks ago";
        return video;
    }

    private static final class TestMediaGroup implements MediaGroup {
        private final int mType;
        private final String mChannelId;
        private final List<MediaItem> mItems = new ArrayList<>();

        TestMediaGroup(int type, Video... videos) {
            this(type, null, videos);
        }

        TestMediaGroup(int type, String channelId, Video... videos) {
            mType = type;
            mChannelId = channelId;

            for (Video video : videos) {
                mItems.add(SimpleMediaItem.from(video));
            }
        }

        @Override
        public int getType() {
            return mType;
        }

        @Override
        public List<MediaItem> getMediaItems() {
            return mItems;
        }

        @Override
        public String getTitle() {
            return "Row";
        }

        @Override
        public String getChannelId() {
            return mChannelId;
        }

        @Override
        public String getParams() {
            return null;
        }

        @Override
        public String getReloadPageKey() {
            return null;
        }

        @Override
        public String getNextPageKey() {
            return null;
        }

        @Override
        public String getChannelUrl() {
            return null;
        }

        @Override
        public boolean isEmpty() {
            return mItems.isEmpty();
        }

        @Override
        public int getTopic() {
            return MediaGroup.TOPIC_NONE;
        }

        @Override
        public boolean isChannelRow() {
            return false;
        }

        @Override
        public boolean isSearchTopicRow() {
            return false;
        }
    }
}
