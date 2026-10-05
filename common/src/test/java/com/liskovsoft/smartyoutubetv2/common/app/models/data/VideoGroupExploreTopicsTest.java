package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.prefs.ExploreTopicsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.TopChannelsData;
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
 * The Explore more topics setting in the rows of the sections (see ExploreTopicsData)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoGroupExploreTopicsTest {
    private ExploreTopicsData mData;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        mData = ExploreTopicsData.instance(context);
        mData.setSectionEnabled(MediaGroup.TYPE_HOME, true);
        mData.setSectionEnabled(MediaGroup.TYPE_GAMING, false);
    }

    @After
    public void tearDown() {
        // The defaults
        mData.setMode(ExploreTopicsData.MODE_SHOW);
        mData.setSectionEnabled(MediaGroup.TYPE_GAMING, true);
        TopChannelsData.instance(RuntimeEnvironment.getApplication()).setMode(TopChannelsData.MODE_SHOW);
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void rowIsHiddenOnlyInPickedSections() {
        mData.setMode(ExploreTopicsData.MODE_HIDE);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME, createTopicRow());
        VideoGroup gaming = createGroup(MediaGroup.TYPE_GAMING, createTopicRow());

        assertTrue(home.isEmpty());
        assertTrue(home.isHiddenRow());
        assertEquals(3, gaming.getSize());
        assertFalse(gaming.isHiddenRow());
        assertFalse(gaming.get(0).isExploreTopicMarked);
    }

    /**
     * Unlike a row that lost its videos to Hide content, it isn't skipped as one with only hidden videos (see BrowsePresenter)
     */
    @Test
    public void hiddenRowHasNoHiddenVideos() {
        mData.setMode(ExploreTopicsData.MODE_HIDE);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME, createTopicRow());

        assertFalse(home.hasOnlyHiddenVideos());
        assertTrue(home.isHiddenRow());
    }

    @Test
    public void topicsAreMarkedInMarkMode() {
        mData.setMode(ExploreTopicsData.MODE_MARK);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME, createTopicRow());

        assertEquals(3, home.getSize());
        assertFalse(home.isHiddenRow());

        for (Video video : home.getVideos()) {
            assertTrue(video.isExploreTopicMarked);
            assertFalse(video.isTopChannelMarked);
        }
    }

    @Test
    public void showModeLeavesThemAlone() {
        mData.setMode(ExploreTopicsData.MODE_SHOW);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME, createTopicRow());

        assertEquals(3, home.getSize());
        assertFalse(home.isHiddenRow());
        assertFalse(home.get(0).isExploreTopicMarked);
    }

    /**
     * The row is found by the service, not by its cards: a topic in a row of videos stays
     */
    @Test
    public void topicOfAnotherRowIsLeftAlone() {
        mData.setMode(ExploreTopicsData.MODE_HIDE);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME, new TestMediaGroup(false, false, createTopic(), createVideo()));

        assertEquals(2, home.getSize());
        assertFalse(home.isHiddenRow());
        assertFalse(home.get(0).isExploreTopicMarked);
    }

    /**
     * The next page of the row isn't a row of topics by itself (e.g. a continuation), the row is
     */
    @Test
    public void nextPageOfTheRowIsMarked() {
        mData.setMode(ExploreTopicsData.MODE_MARK);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME, createTopicRow());
        VideoGroup.from(home, new TestMediaGroup(false, false, createTopic()));

        assertEquals(4, home.getSize());
        assertTrue(home.get(3).isExploreTopicMarked);
    }

    @Test
    public void markIsKeptByTheCopy() {
        mData.setMode(ExploreTopicsData.MODE_MARK);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME, createTopicRow());

        assertTrue(Video.from(home.get(0)).isExploreTopicMarked);
    }

    /**
     * Each setting has its own kind of row: hiding the topics leaves a row of channels, and hiding the channels leaves a row of topics
     */
    @Test
    public void topChannelsSettingIsApart() {
        mData.setMode(ExploreTopicsData.MODE_HIDE);
        TopChannelsData.instance(RuntimeEnvironment.getApplication()).setMode(TopChannelsData.MODE_MARK);

        VideoGroup channels = createGroup(MediaGroup.TYPE_HOME, new TestMediaGroup(true, false, createChannel()));

        assertFalse(channels.isHiddenRow());
        assertTrue(channels.get(0).isTopChannelMarked);

        mData.setMode(ExploreTopicsData.MODE_MARK);
        TopChannelsData.instance(RuntimeEnvironment.getApplication()).setMode(TopChannelsData.MODE_HIDE);

        VideoGroup topics = createGroup(MediaGroup.TYPE_HOME, createTopicRow());

        assertFalse(topics.isHiddenRow());
        assertTrue(topics.get(0).isExploreTopicMarked);
    }

    private static VideoGroup createGroup(int type, MediaGroup mediaGroup) {
        return VideoGroup.from(mediaGroup, new BrowseSection(type, "Section", BrowseSection.TYPE_ROW, 0), 0);
    }

    private static MediaGroup createTopicRow() {
        return new TestMediaGroup(false, true, createTopic(), createTopic(), createTopic());
    }

    private static int sTopicCount;

    /**
     * Like the tiles of Explore more topics: a title and a search query, no video id and no second title
     */
    private static Video createTopic() {
        sTopicCount++;
        Video video = new Video();
        video.itemType = MediaItem.TYPE_UNDEFINED;
        video.title = "Topic " + sTopicCount;
        video.searchQuery = "Topic " + sTopicCount;
        return video;
    }

    private static Video createChannel() {
        Video video = new Video();
        video.itemType = MediaItem.TYPE_CHANNEL;
        video.channelId = "UCchannel1";
        video.title = "Channel";
        return video;
    }

    private static Video createVideo() {
        Video video = new Video();
        video.itemType = MediaItem.TYPE_VIDEO;
        video.videoId = "video1";
        video.title = "Video";
        video.author = "Channel";
        video.secondTitle = "Channel • 5K views • 3 weeks ago";
        return video;
    }

    private static final class TestMediaGroup implements MediaGroup {
        private final boolean mIsChannelRow;
        private final boolean mIsSearchTopicRow;
        private final List<MediaItem> mItems = new ArrayList<>();

        TestMediaGroup(boolean isChannelRow, boolean isSearchTopicRow, Video... videos) {
            mIsChannelRow = isChannelRow;
            mIsSearchTopicRow = isSearchTopicRow;

            for (Video video : videos) {
                mItems.add(SimpleMediaItem.from(video));
            }
        }

        @Override
        public int getType() {
            return MediaGroup.TYPE_HOME;
        }

        @Override
        public List<MediaItem> getMediaItems() {
            return mItems;
        }

        @Override
        public String getTitle() {
            return "Explore more topics";
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
            return "next";
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
            return mIsChannelRow;
        }

        @Override
        public boolean isSearchTopicRow() {
            return mIsSearchTopicRow;
        }
    }
}
