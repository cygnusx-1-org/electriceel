package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
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
 * The videos the service filtered out of a row (see MediaGroup.getFilteredVideoCount), e.g. the watched songs of "Listen again"
 * with Hide watched videos from Home. A row left with its "More music" card only isn't shown (see BrowsePresenter).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoGroupFilteredVideosTest {
    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
    }

    @After
    public void tearDown() {
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void rowLeftWithCardsOnlyHasOnlyHiddenVideos() {
        VideoGroup listenAgain = createGroup(new TestMediaGroup(5, createCard()));

        assertEquals(1, listenAgain.getSize());
        assertTrue(listenAgain.hasOnlyHiddenVideos());
    }

    @Test
    public void rowWithAVideoLeftIsKept() {
        assertFalse(createGroup(new TestMediaGroup(4, createVideo(), createCard())).hasOnlyHiddenVideos());
    }

    /**
     * A row of cards that lost nothing (e.g. channels)
     */
    @Test
    public void rowOfCardsThatLostNothingIsKept() {
        assertFalse(createGroup(new TestMediaGroup(0, createCard())).hasOnlyHiddenVideos());
    }

    /**
     * The next pages of the row add theirs
     */
    @Test
    public void nextPagesAddTheirFilteredVideos() {
        VideoGroup row = createGroup(new TestMediaGroup(0, createCard()));

        VideoGroup.from(row, new TestMediaGroup(2));

        assertTrue(row.hasOnlyHiddenVideos());
    }

    private static VideoGroup createGroup(MediaGroup mediaGroup) {
        return VideoGroup.from(mediaGroup, new BrowseSection(MediaGroup.TYPE_HOME, "Home", BrowseSection.TYPE_ROW, 0), 1);
    }

    /**
     * Like "More music" of "Listen again": a mix, no video id
     */
    private static Video createCard() {
        Video card = new Video();
        card.itemType = MediaItem.TYPE_PLAYLIST;
        card.playlistId = "RDmore";
        card.title = "More music";
        return card;
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
        private final int mFilteredVideoCount;
        private final List<MediaItem> mItems = new ArrayList<>();

        TestMediaGroup(int filteredVideoCount, Video... videos) {
            mFilteredVideoCount = filteredVideoCount;

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
            return "Listen again";
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
        public int getFilteredVideoCount() {
            return mFilteredVideoCount;
        }
    }
}
