package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
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
 * The Top channels you watch setting in the rows of the sections (see TopChannelsData)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoGroupTopChannelsTest {
    private TopChannelsData mData;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        mData = TopChannelsData.instance(context);
        mData.setSectionEnabled(MediaGroup.TYPE_HOME, true);
        mData.setSectionEnabled(MediaGroup.TYPE_GAMING, false);
    }

    @After
    public void tearDown() {
        // The defaults
        mData.setMode(TopChannelsData.MODE_SHOW);
        mData.setSectionEnabled(MediaGroup.TYPE_GAMING, true);
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void rowIsHiddenOnlyInPickedSections() {
        mData.setMode(TopChannelsData.MODE_HIDE);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME, createChannelRow());
        VideoGroup gaming = createGroup(MediaGroup.TYPE_GAMING, createChannelRow());

        assertTrue(home.isEmpty());
        assertTrue(home.isHiddenRow());
        assertEquals(3, gaming.getSize());
        assertFalse(gaming.isHiddenRow());
        assertFalse(gaming.get(0).isTopChannelMarked);
    }

    /**
     * Unlike a row that lost its videos to Hide content, it isn't skipped as one with only hidden videos (see BrowsePresenter)
     */
    @Test
    public void hiddenRowHasNoHiddenVideos() {
        mData.setMode(TopChannelsData.MODE_HIDE);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME, createChannelRow());

        assertFalse(home.hasOnlyHiddenVideos());
        assertTrue(home.isHiddenRow());
    }

    @Test
    public void channelsAreMarkedInMarkMode() {
        mData.setMode(TopChannelsData.MODE_MARK);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME, createChannelRow());

        assertEquals(3, home.getSize());
        assertFalse(home.isHiddenRow());

        for (Video video : home.getVideos()) {
            assertTrue(video.isTopChannelMarked);
        }
    }

    @Test
    public void showModeLeavesThemAlone() {
        mData.setMode(TopChannelsData.MODE_SHOW);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME, createChannelRow());

        assertEquals(3, home.getSize());
        assertFalse(home.isHiddenRow());
        assertFalse(home.get(0).isTopChannelMarked);
    }

    /**
     * The row is found by the service, not by its cards: a channel in a row of videos stays
     */
    @Test
    public void channelOfAnotherRowIsLeftAlone() {
        mData.setMode(TopChannelsData.MODE_HIDE);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME, new TestMediaGroup(false, createChannel(), createVideo()));

        assertEquals(2, home.getSize());
        assertFalse(home.isHiddenRow());
        assertFalse(home.get(0).isTopChannelMarked);
    }

    /**
     * The next page of the row isn't a row of channels by itself (e.g. a continuation), the row is
     */
    @Test
    public void nextPageOfTheRowIsMarked() {
        mData.setMode(TopChannelsData.MODE_MARK);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME, createChannelRow());
        VideoGroup.from(home, new TestMediaGroup(false, createChannel()));

        assertEquals(4, home.getSize());
        assertTrue(home.get(3).isTopChannelMarked);
    }

    @Test
    public void markIsKeptByTheCopy() {
        mData.setMode(TopChannelsData.MODE_MARK);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME, createChannelRow());

        assertTrue(Video.from(home.get(0)).isTopChannelMarked);
    }

    private static VideoGroup createGroup(int type, MediaGroup mediaGroup) {
        return VideoGroup.from(mediaGroup, new BrowseSection(type, "Section", BrowseSection.TYPE_ROW, 0), 0);
    }

    private static MediaGroup createChannelRow() {
        return new TestMediaGroup(true, createChannel(), createChannel(), createChannel());
    }

    private static int sChannelCount;

    /**
     * Like the round tiles of Top channels you watch: a channel id, no video id
     */
    private static Video createChannel() {
        sChannelCount++;
        Video video = new Video();
        video.itemType = MediaItem.TYPE_CHANNEL;
        video.channelId = "UCchannel" + sChannelCount;
        video.title = "Channel " + sChannelCount;
        video.secondTitle = "@channel" + sChannelCount + " • 19.9K subscribers";
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
        private final List<MediaItem> mItems = new ArrayList<>();

        TestMediaGroup(boolean isChannelRow, Video... videos) {
            mIsChannelRow = isChannelRow;

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
            return "Top channels you watch";
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
            return false;
        }

        @Override
        public int getFilteredVideoCount() {
            return 0;
        }
    }
}
