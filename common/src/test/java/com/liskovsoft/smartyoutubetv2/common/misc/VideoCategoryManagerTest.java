package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SimpleMediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoCategoryManagerTest {
    private static final String MUSIC_ID = "music";
    private static final String GAMING_ID = "gaming";
    private static final String NO_CATEGORY_ID = "none";
    private static final String UNKNOWN_ID = "unknown";
    private static final String TECH_ID = "tech";
    private static final String SPORTS_ID = "sports";
    private static final String NEWS_ID = "news";

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        setHidden(MediaServiceData.CONTENT_MUSIC_HOME, false);
        setHidden(MediaServiceData.CONTENT_GAMING_HOME, false);
        setHidden(MediaServiceData.CONTENT_SPORTS_HOME, false);
        setHidden(MediaServiceData.CONTENT_NEWS_HOME, false);
        setHidden(MediaServiceData.CONTENT_TECH_HOME, false);

        VideoCategoryManager manager = VideoCategoryManager.instance(context);
        manager.setCategoryForTesting(MUSIC_ID, "Music");
        manager.setCategoryForTesting(GAMING_ID, "Gaming");
        manager.setCategoryForTesting(NO_CATEGORY_ID, "");
        manager.setCategoryForTesting(TECH_ID, "Science & Technology");
        manager.setCategoryForTesting(MUSIC_ID + 2, "Music");
        manager.setCategoryForTesting(GAMING_ID + 2, "Gaming");
        manager.setCategoryForTesting(SPORTS_ID, "Sports");
        manager.setCategoryForTesting(NEWS_ID, "News & Politics");
    }

    @After
    public void tearDown() {
        setHidden(MediaServiceData.CONTENT_MUSIC_HOME, false);
        setHidden(MediaServiceData.CONTENT_GAMING_HOME, false);
        setHidden(MediaServiceData.CONTENT_SPORTS_HOME, false);
        setHidden(MediaServiceData.CONTENT_NEWS_HOME, false);
        setHidden(MediaServiceData.CONTENT_TECH_HOME, false);
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void nothingIsHiddenWhileTheOptionIsOff() {
        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);

        addVideos(home);

        assertEquals(4, home.getSize());
    }

    @Test
    public void knownMusicVideoIsHiddenFromHome() {
        setHidden(MediaServiceData.CONTENT_MUSIC_HOME, true);
        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);

        addVideos(home);

        assertEquals(3, home.getSize());
        assertFalse(contains(home, MUSIC_ID));
        assertTrue(contains(home, GAMING_ID));
    }

    @Test
    public void knownGamingVideoIsHiddenFromHome() {
        setHidden(MediaServiceData.CONTENT_GAMING_HOME, true);
        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);

        addVideos(home);

        assertEquals(3, home.getSize());
        assertFalse(contains(home, GAMING_ID));
        assertTrue(contains(home, MUSIC_ID));
    }

    @Test
    public void bothCategoriesAreHiddenFromHome() {
        setHidden(MediaServiceData.CONTENT_MUSIC_HOME, true);
        setHidden(MediaServiceData.CONTENT_GAMING_HOME, true);
        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);

        addVideos(home);

        assertEquals(2, home.getSize());
    }

    @Test
    public void hiddenCategoriesStayOutsideHome() {
        setHidden(MediaServiceData.CONTENT_MUSIC_HOME, true);
        setHidden(MediaServiceData.CONTENT_GAMING_HOME, true);

        for (int type : new int[] {MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_GAMING, MediaGroup.TYPE_SEARCH, MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY}) {
            VideoGroup group = createGroup(type);
            addVideos(group);
            assertTrue("Group type " + type, contains(group, MUSIC_ID));
            assertTrue("Group type " + type, contains(group, GAMING_ID));
        }
    }

    @Test
    public void videoWithoutCategoryHasNoCachedCategory() {
        VideoCategoryManager manager = VideoCategoryManager.instance(RuntimeEnvironment.getApplication());

        assertEquals("Music", manager.getCachedCategory(MUSIC_ID));
        assertNull(manager.getCachedCategory(NO_CATEGORY_ID));
        assertNull(manager.getCachedCategory(UNKNOWN_ID));
    }

    @Test
    public void sportsAndNewsVideosAreHiddenFromHome() {
        setHidden(MediaServiceData.CONTENT_SPORTS_HOME, true);
        setHidden(MediaServiceData.CONTENT_NEWS_HOME, true);
        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);

        home.add(createVideo(SPORTS_ID));
        home.add(createVideo(NEWS_ID));
        home.add(createVideo(TECH_ID));

        assertEquals(1, home.getSize());
        assertTrue(contains(home, TECH_ID));
    }

    @Test
    public void techVideosAreHiddenWithoutHidingOrdinaryRows() {
        setHidden(MediaServiceData.CONTENT_TECH_HOME, true);
        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);

        home.add(createVideo(TECH_ID));
        home.add(createVideo(MUSIC_ID));

        assertEquals(1, home.getSize());
        assertFalse(contains(home, TECH_ID));
        // Tech has no row topic: an ordinary row must not match it
        assertFalse(getManager().isRowHidden(new TestMediaGroup(MediaGroup.TYPE_HOME, MediaGroup.TOPIC_NONE, TECH_ID, MUSIC_ID, GAMING_ID)));
        assertTrue(getManager().isRowHidden(new TestMediaGroup(MediaGroup.TYPE_HOME, MediaGroup.TOPIC_NONE, TECH_ID)));
    }

    @Test
    public void rowWithHiddenTopicIsHidden() {
        VideoCategoryManager manager = getManager();
        TestMediaGroup musicShelf = new TestMediaGroup(MediaGroup.TYPE_HOME, MediaGroup.TOPIC_MUSIC, TECH_ID);

        assertFalse(manager.isRowHidden(musicShelf));

        setHidden(MediaServiceData.CONTENT_MUSIC_HOME, true);

        assertTrue(manager.isRowHidden(musicShelf));
        assertFalse(manager.isRowHidden(new TestMediaGroup(MediaGroup.TYPE_HOME, MediaGroup.TOPIC_GAMING, TECH_ID)));
    }

    @Test
    public void rowWithOtherVideosStays() {
        setHidden(MediaServiceData.CONTENT_MUSIC_HOME, true);
        setHidden(MediaServiceData.CONTENT_GAMING_HOME, true);
        VideoCategoryManager manager = getManager();

        // Only the hidden videos go, however many there are, while two videos or more are left
        assertFalse(manager.isRowHidden(new TestMediaGroup(MediaGroup.TYPE_HOME, MediaGroup.TOPIC_NONE, MUSIC_ID, MUSIC_ID + 2, GAMING_ID, TECH_ID, UNKNOWN_ID)));
        assertFalse(manager.isRowHidden(new TestMediaGroup(MediaGroup.TYPE_HOME, MediaGroup.TOPIC_NONE, GAMING_ID, TECH_ID, UNKNOWN_ID, null)));
    }

    @Test
    public void rowLeftWithSingleVideoIsHidden() {
        setHidden(MediaServiceData.CONTENT_GAMING_HOME, true);
        VideoCategoryManager manager = getManager();
        // E.g. a game genre row left with one People & Blogs video
        TestMediaGroup row = new TestMediaGroup(MediaGroup.TYPE_HOME, MediaGroup.TOPIC_NONE, GAMING_ID, GAMING_ID + 2, TECH_ID);

        assertTrue(manager.isRowHidden(row));
        // The top row (Recommended) keeps it
        assertFalse(manager.isRowHidden(row, true));
        // A row that lost nothing stays with its single video
        assertFalse(manager.isRowHidden(new TestMediaGroup(MediaGroup.TYPE_HOME, MediaGroup.TOPIC_NONE, TECH_ID)));
    }

    @Test
    public void rowWithOnlyHiddenVideosIsHidden() {
        setHidden(MediaServiceData.CONTENT_GAMING_HOME, true);
        VideoCategoryManager manager = getManager();

        assertTrue(manager.isRowHidden(new TestMediaGroup(MediaGroup.TYPE_HOME, MediaGroup.TOPIC_NONE, GAMING_ID)));
        assertFalse(manager.isRowHidden(new TestMediaGroup(MediaGroup.TYPE_HOME, MediaGroup.TOPIC_NONE, MUSIC_ID)));
    }

    @Test
    public void rowWithoutVideosLeftIsHidden() {
        setHidden(MediaServiceData.CONTENT_MUSIC_HOME, true);
        VideoCategoryManager manager = getManager();

        // A music video and a "More music" card (no video id)
        assertTrue(manager.isRowHidden(new TestMediaGroup(MediaGroup.TYPE_HOME, MediaGroup.TOPIC_NONE, MUSIC_ID, null)));
        // Channels only
        assertFalse(manager.isRowHidden(new TestMediaGroup(MediaGroup.TYPE_HOME, MediaGroup.TOPIC_NONE, null, null)));
    }

    @Test
    public void rowOutsideHomeIsNeverHidden() {
        setHidden(MediaServiceData.CONTENT_MUSIC_HOME, true);

        assertFalse(getManager().isRowHidden(new TestMediaGroup(MediaGroup.TYPE_MUSIC, MediaGroup.TOPIC_MUSIC, MUSIC_ID)));
    }

    private static VideoCategoryManager getManager() {
        return VideoCategoryManager.instance(RuntimeEnvironment.getApplication());
    }

    private static void setHidden(int content, boolean hide) {
        MediaServiceData.instance().setContentHidden(content, hide);
    }

    private static VideoGroup createGroup(int type) {
        return VideoGroup.from(new BrowseSection(type, "Section", BrowseSection.TYPE_GRID, 0));
    }

    private static void addVideos(VideoGroup group) {
        group.add(createVideo(MUSIC_ID));
        group.add(createVideo(GAMING_ID));
        group.add(createVideo(NO_CATEGORY_ID));
        group.add(createVideo(UNKNOWN_ID));
    }

    private static Video createVideo(String videoId) {
        Video video = new Video();
        video.videoId = videoId;
        video.title = "Video " + videoId;
        return video;
    }

    private static final class TestMediaGroup implements MediaGroup {
        private final int mType;
        private final int mTopic;
        private final List<MediaItem> mItems = new ArrayList<>();

        TestMediaGroup(int type, int topic, String... videoIds) {
            mType = type;
            mTopic = topic;

            for (String videoId : videoIds) {
                mItems.add(SimpleMediaItem.from(createVideo(videoId)));
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
            return mTopic;
        }
    }

    private static boolean contains(VideoGroup group, String videoId) {
        for (Video video : group.getVideos()) {
            if (videoId.equals(video.videoId)) {
                return true;
            }
        }

        return false;
    }
}
