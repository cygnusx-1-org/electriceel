package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;
import com.liskovsoft.smartyoutubetv2.common.prefs.AiSListFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ChannelGroupFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.HomeRowsData.Row;
import com.liskovsoft.smartyoutubetv2.common.prefs.KeywordFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SectionFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ShowsData;
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

/**
 * The sidebar sections of the subscription groups: the marks of Hide or mark content, and their watched videos hidden
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoGroupSubscriptionGroupSectionsTest {
    private static final BrowseSection HOME = new BrowseSection(MediaGroup.TYPE_HOME, "Home", BrowseSection.TYPE_ROW, 0);
    private static final BrowseSection SUBSCRIPTIONS = new BrowseSection(MediaGroup.TYPE_SUBSCRIPTIONS, "Subscriptions", BrowseSection.TYPE_GRID, 0);
    private static final long DURATION_MS = 600_000;
    private ShowsData mShowsData;
    private ChannelGroupFilterData mChannelGroupData;
    private VideoStateService mStateService;
    private final List<String> mPlayedIds = new ArrayList<>();
    private int mVideoCount;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        mShowsData = ShowsData.instance(context);
        mChannelGroupData = ChannelGroupFilterData.instance(context);
        mStateService = VideoStateService.instance(context);
    }

    @After
    public void tearDown() {
        // The defaults
        mShowsData.setMode(ShowsData.MODE_SHOW);
        mShowsData.setSectionEnabled(SectionFilterData.SECTION_CHANNEL_GROUPS, true);
        mChannelGroupData.setWatchedHidden(false);

        for (String videoId : mPlayedIds) {
            mStateService.removeByVideoId(videoId);
        }

        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Their ids are hashes: they're one section of the settings, and one of AiSList
     */
    @Test
    public void groupSectionsAreOneSectionOfTheFilters() {
        VideoGroup climbing = createGroupGrid("Climbing");
        VideoGroup cooking = createGroupGrid("Cooking");

        assertEquals(SectionFilterData.SECTION_CHANNEL_GROUPS, climbing.getFilterSectionId());
        assertEquals(SectionFilterData.SECTION_CHANNEL_GROUPS, cooking.getFilterSectionId());
        assertEquals(AiSListFilterData.SECTION_CHANNEL_GROUPS, climbing.getAiSListSection());

        // Another pinned section isn't one
        VideoGroup pinnedChannel = VideoGroup.from(new BrowseSection("Channel".hashCode(), "Channel", BrowseSection.TYPE_GRID, 0, false, new Video()));

        assertEquals("Channel".hashCode(), pinnedChannel.getFilterSectionId());
        assertEquals(-1, pinnedChannel.getAiSListSection());
    }

    /**
     * The feed of a group has no type: as a row of Home it's Home's, for AiSList and the keywords too
     */
    @Test
    public void groupRowOfHomeIsHomeForAiSList() {
        VideoGroup groupRow = createRow(Row.channelGroup("group", "Climbing"), createVideo(-1));
        VideoGroup subscriptionsRow = VideoGroup.from(new TestMediaGroup(MediaGroup.TYPE_SUBSCRIPTIONS, createVideo(-1)), HOME, 0, null,
                Row.subscriptions());

        assertEquals(AiSListFilterData.SECTION_HOME, groupRow.getAiSListSection());
        assertEquals(MediaGroup.TYPE_HOME, groupRow.getFilterSectionId());
        assertEquals(AiSListFilterData.SECTION_SUBSCRIPTIONS, subscriptionsRow.getAiSListSection());
        // Out of Home, a feed without a type is still none of AiSList's
        assertEquals(-1, VideoGroup.getAiSListSection(MediaGroup.TYPE_UNDEFINED, SUBSCRIPTIONS, null));
        assertEquals(-1, VideoGroup.getAiSListSection(MediaGroup.TYPE_UNDEFINED, null, null));
    }

    @Test
    public void keywordsAreHiddenFromGroupRowsOfHome() {
        KeywordFilterData keywords = KeywordFilterData.instance(RuntimeEnvironment.getApplication());
        keywords.addKeyword("Rally");

        try {
            Video rally = createVideo(-1);
            rally.title = "Rally day";

            VideoGroup groupRow = createRow(Row.channelGroup("group", "Climbing"), rally, createVideo(-1));

            assertEquals(1, groupRow.getSize());
            assertFalse("Rally day".equals(groupRow.get(0).title));
        } finally {
            keywords.removeKeyword("Rally");
        }
    }

    @Test
    public void showIsMarkedInGroupSections() {
        mShowsData.setMode(ShowsData.MODE_MARK);

        VideoGroup group = createGroupGrid("Climbing");
        group.add(createShow());

        assertTrue(group.get(0).isShowMarked);

        mShowsData.setSectionEnabled(SectionFilterData.SECTION_CHANNEL_GROUPS, false);

        VideoGroup unpicked = createGroupGrid("Climbing");
        unpicked.add(createShow());

        assertFalse(unpicked.get(0).isShowMarked);
    }

    @Test
    public void watchedVideosAreShownByDefault() {
        VideoGroup group = createGroupGrid("Climbing", createVideo(100));

        assertEquals(1, group.getSize());
    }

    @Test
    public void watchedVideosAreHiddenFromGroupSections() {
        mChannelGroupData.setWatchedHidden(true);

        VideoGroup group = createGroupGrid("Climbing", createVideo(81), createVideo(80), createVideo(-1), createVideo(100));

        assertEquals(2, group.getSize());
        assertEquals(80, group.get(0).percentWatched, 0);
        assertEquals(-1, group.get(1).percentWatched, 0);
    }

    @Test
    public void liveStreamIsLeftAlone() {
        mChannelGroupData.setWatchedHidden(true);

        Video live = createVideo(100);
        live.isLive = true;

        assertEquals(1, createGroupGrid("Climbing", live).getSize());
    }

    /**
     * The card doesn't say (e.g. incognito, or history paused): the app played it
     */
    @Test
    public void videoWatchedInTheAppIsHidden() {
        mChannelGroupData.setWatchedHidden(true);

        Video played = createVideo(-1);
        Video started = createVideo(-1);
        savePlayed(played, DURATION_MS * 9 / 10);
        savePlayed(started, DURATION_MS / 10);

        VideoGroup group = createGroupGrid("Climbing", played, started);

        assertEquals(1, group.getSize());
        assertEquals(started.videoId, group.get(0).videoId);
    }

    @Test
    public void watchedVideosAreHiddenFromGroupRowsOfHome() {
        mChannelGroupData.setWatchedHidden(true);

        VideoGroup groupRow = createRow(Row.channelGroup("group", "Climbing"), createVideo(100), createVideo(10));
        VideoGroup subscriptionsRow = createRow(Row.subscriptions(), createVideo(100));
        VideoGroup youTubeRow = createRow(null, createVideo(100));

        assertEquals(1, groupRow.getSize());
        assertEquals(1, subscriptionsRow.getSize());
        assertEquals(1, youTubeRow.getSize());
    }

    @Test
    public void otherSectionsAreLeftAlone() {
        mChannelGroupData.setWatchedHidden(true);

        VideoGroup subscriptions = VideoGroup.from(SUBSCRIPTIONS);
        subscriptions.add(createVideo(100));

        assertEquals(1, subscriptions.getSize());
    }

    /**
     * A row left with only cards without a video isn't shown (see BrowsePresenter)
     */
    @Test
    public void hiddenWatchedVideosAreCounted() {
        mChannelGroupData.setWatchedHidden(true);

        Video channel = new Video();
        channel.itemType = MediaItem.TYPE_CHANNEL;
        channel.channelId = "UCclimbing";
        channel.title = "Climbing";

        VideoGroup row = createRow(Row.channelGroup("group", "Climbing"), createVideo(100), createVideo(100), channel);

        assertEquals(1, row.getSize());
        assertTrue(row.hasOnlyHiddenVideos());
    }

    private static VideoGroup createGroupGrid(String title, Video... videos) {
        Video pinned = new Video();
        pinned.channelGroupId = title;
        VideoGroup group = VideoGroup.from(new BrowseSection(title.hashCode(), title, BrowseSection.TYPE_GRID, 0, false, pinned));

        for (Video video : videos) {
            group.add(video);
        }

        return group;
    }

    private static VideoGroup createRow(Row addedHomeRow, Video... videos) {
        return VideoGroup.from(new TestMediaGroup(MediaGroup.TYPE_UNDEFINED, videos), HOME, 0, null, addedHomeRow);
    }

    private void savePlayed(Video video, long positionMs) {
        mStateService.save(new State(video, positionMs, DURATION_MS));
        mPlayedIds.add(video.videoId);
    }

    private Video createVideo(float percentWatched) {
        mVideoCount++;
        Video video = new Video();
        video.itemType = MediaItem.TYPE_VIDEO;
        video.videoId = "groupVideo" + mVideoCount;
        video.title = "Video " + mVideoCount;
        video.percentWatched = percentWatched;
        return video;
    }

    private Video createShow() {
        mVideoCount++;
        Video video = new Video();
        video.itemType = MediaItem.TYPE_PLAYLIST;
        video.isShow = true;
        video.channelId = "VLPLshow" + mVideoCount;
        video.title = "Show " + mVideoCount;
        return video;
    }

    /**
     * A feed, as the one of a subscription group (see ChannelFeedService of MediaServiceCore), which has no type
     */
    private static final class TestMediaGroup implements MediaGroup {
        private final int mType;
        private final List<MediaItem> mItems = new ArrayList<>();

        TestMediaGroup(int type, Video... videos) {
            mType = type;

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
