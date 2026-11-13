package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.HiddenVideosData;
import com.liskovsoft.smartyoutubetv2.common.prefs.HomeRowsData.Row;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SectionFilterData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoMenuPresenterTest {
    private static final BrowseSection HOME = new BrowseSection(MediaGroup.TYPE_HOME, "Home", BrowseSection.TYPE_ROW, 0);
    private Context mContext;
    private HiddenVideosData mHiddenVideosData;
    // A card has a weak reference to its group: held until the test is done
    private final List<VideoGroup> mGroups = new ArrayList<>();
    private final List<String> mHiddenIds = new ArrayList<>();

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(mContext);
        mHiddenVideosData = HiddenVideosData.instance(mContext);
    }

    @After
    public void tearDown() {
        // Kept by the instance past the test
        for (String videoId : mHiddenIds) {
            mHiddenVideosData.unhide(videoId);
        }

        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    /**
     * The row of Subscriptions added to Home is the same group as the section (see BrowsePresenter.createAddedHomeRowObserve)
     */
    @Test
    public void subscriptionsAndNotificationsHaveTheSubscriptionsMenu() {
        assertEquals(MainUIData.MENU_SUBSCRIPTIONS, VideoMenuPresenter.getCardMenu(createVideo(MediaGroup.TYPE_SUBSCRIPTIONS), false, false));
        assertEquals(MainUIData.MENU_SUBSCRIPTIONS, VideoMenuPresenter.getCardMenu(createVideo(MediaGroup.TYPE_NOTIFICATIONS), false, false));
    }

    @Test
    public void historyHasTheHistoryMenu() {
        assertEquals(MainUIData.MENU_HISTORY, VideoMenuPresenter.getCardMenu(createVideo(MediaGroup.TYPE_HISTORY), false, false));
        assertEquals(MainUIData.MENU_HISTORY, VideoMenuPresenter.getCardMenu(createVideo(MediaGroup.TYPE_HOME), true, false));
    }

    @Test
    public void playlistsSectionHasThePlaylistsMenu() {
        assertEquals(MainUIData.MENU_PLAYLISTS, VideoMenuPresenter.getCardMenu(createVideo(MediaGroup.TYPE_USER_PLAYLISTS), false, true));
    }

    /**
     * Home, Search, the channels, the player...
     */
    @Test
    public void otherPlacesHaveTheVideosMenu() {
        assertEquals(MainUIData.MENU_VIDEOS, VideoMenuPresenter.getCardMenu(createVideo(MediaGroup.TYPE_HOME), false, false));
        assertEquals(MainUIData.MENU_VIDEOS, VideoMenuPresenter.getCardMenu(createVideo(MediaGroup.TYPE_SEARCH), false, false));
        assertEquals(MainUIData.MENU_VIDEOS, VideoMenuPresenter.getCardMenu(createVideo(MediaGroup.TYPE_SUGGESTIONS), false, false));
        assertEquals(MainUIData.MENU_VIDEOS, VideoMenuPresenter.getCardMenu(createVideo(MediaGroup.TYPE_USER_PLAYLISTS), false, false));
        assertEquals(MainUIData.MENU_VIDEOS, VideoMenuPresenter.getCardMenu(new Video(), false, false));
    }

    /**
     * Where it is wins over the section shown
     */
    @Test
    public void subscriptionsWinOverTheSectionShown() {
        assertEquals(MainUIData.MENU_SUBSCRIPTIONS, VideoMenuPresenter.getCardMenu(createVideo(MediaGroup.TYPE_SUBSCRIPTIONS), true, true));
    }

    /**
     * The section of a subscription group and its row added to Home: their feed has no type (see ChannelFeedService.getFeed)
     */
    @Test
    public void subscriptionGroupsHaveTheSubscriptionsMenu() {
        assertEquals(MainUIData.MENU_SUBSCRIPTIONS, VideoMenuPresenter.getCardMenu(createGroupGridVideo("Climbing"), false, false));
        assertEquals(MainUIData.MENU_SUBSCRIPTIONS, VideoMenuPresenter.getCardMenu(createRowVideo(Row.channelGroup("group", "Cooking")), false, false));
        assertEquals(MainUIData.MENU_SUBSCRIPTIONS, VideoMenuPresenter.getCardMenu(createRowVideo(Row.subscriptions()), false, false));
    }

    /**
     * A channel added to Home is a channel's uploads, not a subscription group
     */
    @Test
    public void channelRowOfHomeHasTheVideosMenu() {
        assertEquals(MainUIData.MENU_VIDEOS, VideoMenuPresenter.getCardMenu(createRowVideo(Row.channel("UCchannel", "Channel")), false, false));
    }

    /**
     * Marked "Not interested" in a subscription group, it stays gone from all of them, sections and rows of Home
     */
    @Test
    public void notInterestedHidesFromTheSubscriptionGroups() {
        Video grid = createGroupGridVideo("Climbing");
        Video row = createRowVideo(Row.channelGroup("group", "Cooking"));

        assertTrue(VideoMenuPresenter.hideFromSubscriptionGroups(mContext, grid));
        assertTrue(VideoMenuPresenter.hideFromSubscriptionGroups(mContext, row));

        assertTrue(mHiddenVideosData.isHidden(SectionFilterData.SECTION_CHANNEL_GROUPS, grid.videoId));
        assertTrue(mHiddenVideosData.isHidden(SectionFilterData.SECTION_CHANNEL_GROUPS, row.videoId));
    }

    /**
     * Subscriptions has YouTube take it out of its feed instead (see VideoMenuPresenter.removeFromSubscriptionsFeed)
     */
    @Test
    public void notInterestedHidesNothingOutOfTheSubscriptionGroups() {
        Video subscriptions = createVideo(MediaGroup.TYPE_SUBSCRIPTIONS);
        Video subscriptionsRow = createRowVideo(Row.subscriptions());
        Video home = createVideo(MediaGroup.TYPE_HOME);

        assertFalse(VideoMenuPresenter.hideFromSubscriptionGroups(mContext, subscriptions));
        assertFalse(VideoMenuPresenter.hideFromSubscriptionGroups(mContext, subscriptionsRow));
        assertFalse(VideoMenuPresenter.hideFromSubscriptionGroups(mContext, home));
        assertFalse(VideoMenuPresenter.hideFromSubscriptionGroups(mContext, new Video()));
        assertFalse(VideoMenuPresenter.hideFromSubscriptionGroups(mContext, null));

        for (Video video : new Video[] {subscriptions, subscriptionsRow, home}) {
            assertFalse(mHiddenVideosData.isHidden(SectionFilterData.SECTION_CHANNEL_GROUPS, video.videoId));
            assertFalse(mHiddenVideosData.isHidden(MediaGroup.TYPE_SUBSCRIPTIONS, video.videoId));
            assertFalse(mHiddenVideosData.isHidden(MediaGroup.TYPE_HOME, video.videoId));
        }
    }

    private Video createVideo(int type) {
        VideoGroup group = VideoGroup.from(new ArrayList<>());
        group.setType(type);
        return createVideo(group);
    }

    private Video createGroupGridVideo(String title) {
        Video pinned = new Video();
        pinned.channelGroupId = title;
        return createVideo(VideoGroup.from(new BrowseSection(title.hashCode(), title, BrowseSection.TYPE_GRID, 0, false, pinned)));
    }

    /**
     * A row added to Home (see BrowsePresenter.createAddedHomeRowObserve)
     */
    private Video createRowVideo(Row addedHomeRow) {
        int type = addedHomeRow.type == Row.TYPE_SUBSCRIPTIONS ? MediaGroup.TYPE_SUBSCRIPTIONS : MediaGroup.TYPE_UNDEFINED;
        return createVideo(VideoGroup.from(new EmptyMediaGroup(type), HOME, 0, null, addedHomeRow));
    }

    /**
     * An id of its own: a hidden one outlives the test, unless unhidden
     */
    private Video createVideo(VideoGroup group) {
        mGroups.add(group);
        Video video = new Video();
        video.itemType = MediaItem.TYPE_VIDEO;
        video.videoId = "menuVideo" + mGroups.size();
        video.setGroup(group);
        mHiddenIds.add(video.videoId);
        return video;
    }

    /**
     * A feed of the given type: the cards are added after
     */
    private static final class EmptyMediaGroup implements MediaGroup {
        private final int mType;

        EmptyMediaGroup(int type) {
            mType = type;
        }

        @Override
        public int getType() {
            return mType;
        }

        @Override
        public List<MediaItem> getMediaItems() {
            return new ArrayList<>();
        }

        @Override
        public String getTitle() {
            return null;
        }

        @Override
        public String getChannelId() {
            return null;
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
            return true;
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

        @Override
        public int getRowKind() {
            return ROW_KIND_NONE;
        }

        @Override
        public int getFilteredVideoCount() {
            return 0;
        }
    }
}
