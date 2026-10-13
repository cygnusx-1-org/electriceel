package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.misc.DuplicateFilter;
import com.liskovsoft.smartyoutubetv2.common.prefs.KeywordFilterData;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The rows of a section share a DuplicateFilter: a video is kept by the first row that has it,
 * and left out of the rows below and of the pages a row gets later
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoGroupDuplicatesTest {
    private static final BrowseSection HOME = new BrowseSection(MediaGroup.TYPE_HOME, "Home", BrowseSection.TYPE_ROW, 0);
    private final DuplicateFilter mFilter = new DuplicateFilter();
    private KeywordFilterData mKeywordData;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        mKeywordData = KeywordFilterData.instance(context);
        clearKeywords();
    }

    @After
    public void tearDown() {
        clearKeywords();
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void firstRowKeepsTheVideo() {
        VideoGroup first = createRow(0, createVideo("a"), createVideo("b"));
        VideoGroup second = createRow(1, createVideo("b"), createVideo("c"));

        assertEquals(Arrays.asList("a", "b"), getVideoIds(first));
        assertEquals(Arrays.asList("c"), getVideoIds(second));
        assertEquals(0, first.getDuplicateCount());
        assertEquals(1, second.getDuplicateCount());
    }

    /**
     * A row's next page comes after the rows below it: its videos they have are left out before they're shown
     */
    @Test
    public void nextPageLeavesOutTheVideosOfTheRowsBelow() {
        VideoGroup first = createRow(0, createVideo("a"));
        VideoGroup second = createRow(1, createVideo("b"));

        VideoGroup.from(first, new TestMediaGroup(createVideo("b"), createVideo("c")));

        assertEquals(Arrays.asList("a", "c"), getVideoIds(first));
        assertEquals(Arrays.asList("b"), getVideoIds(second));
        assertEquals(1, first.getDuplicateCount());
    }

    @Test
    public void mixIsNotTakenForItsFirstVideo() {
        VideoGroup first = createRow(0, createMix("a"));
        VideoGroup second = createRow(1, createVideo("a"), createMix("a"));

        assertEquals(1, first.getSize());
        assertEquals(1, second.getSize());
        assertFalse(second.get(0).isMix());
        assertEquals(1, second.getDuplicateCount());
    }

    /**
     * Its badge has words and it has no duration, like a mix, but it has no playlist
     */
    @Test
    public void upcomingVideoIsTakenForTheVideo() {
        Video upcoming = createVideo("a");
        upcoming.badge = "UPCOMING";

        createRow(0, upcoming);
        VideoGroup second = createRow(1, createVideo("a"));

        assertTrue(second.isEmpty());
        assertEquals(1, second.getDuplicateCount());
    }

    /**
     * E.g. "More music": the row is left with it only, so it isn't shown (see BrowsePresenter.createRowGroups)
     */
    @Test
    public void cardsWithoutAVideoAreKept() {
        createRow(0, createVideo("a"), createCard());
        VideoGroup second = createRow(1, createVideo("a"), createCard());

        assertEquals(1, second.getSize());
        assertNull(second.get(0).videoId);
        assertTrue(second.hasOnlyHiddenVideos());
    }

    /**
     * A row that shows nothing of the video doesn't keep it from the rows below
     */
    @Test
    public void videoHiddenByAnotherFilterIsLeftForTheRowsBelow() {
        mKeywordData.addKeyword("reaction");
        Video reaction = createVideo("a");
        reaction.title = "Reaction";

        VideoGroup first = createRow(0, reaction);
        VideoGroup second = createRow(1, createVideo("a"));

        assertTrue(first.isEmpty());
        assertEquals(Arrays.asList("a"), getVideoIds(second));
        assertEquals(0, first.getDuplicateCount());
    }

    @Test
    public void rowsWithoutAFilterKeepTheirVideos() {
        VideoGroup first = VideoGroup.from(new TestMediaGroup(createVideo("a")), HOME, 0);
        VideoGroup second = VideoGroup.from(new TestMediaGroup(createVideo("a")), HOME, 1);

        assertEquals(Arrays.asList("a"), getVideoIds(first));
        assertEquals(Arrays.asList("a"), getVideoIds(second));
        assertEquals(0, second.getDuplicateCount());
    }

    @Test
    public void filterOfAnotherLoadDoesNotLeaveOutTheVideos() {
        createRow(0, createVideo("a"));
        VideoGroup reloaded = VideoGroup.from(new TestMediaGroup(createVideo("a")), HOME, 0, new DuplicateFilter());

        assertEquals(Arrays.asList("a"), getVideoIds(reloaded));
    }

    private VideoGroup createRow(int position, Video... videos) {
        return VideoGroup.from(new TestMediaGroup(videos), HOME, position, mFilter);
    }

    private void clearKeywords() {
        for (String keyword : mKeywordData.getKeywords()) {
            mKeywordData.removeKeyword(keyword);
        }
    }

    private static List<String> getVideoIds(VideoGroup group) {
        List<String> result = new ArrayList<>();

        for (Video video : group.getVideos()) {
            result.add(video.videoId);
        }

        return result;
    }

    private static Video createVideo(String videoId) {
        Video video = new Video();
        video.itemType = MediaItem.TYPE_VIDEO;
        video.videoId = videoId;
        video.title = "Video " + videoId;
        video.author = "Channel";
        return video;
    }

    /**
     * Starts with the video, has the same id
     */
    private static Video createMix(String videoId) {
        Video mix = new Video();
        mix.itemType = MediaItem.TYPE_PLAYLIST;
        mix.videoId = videoId;
        mix.playlistId = "RD" + videoId;
        mix.badge = "Mix";
        mix.title = "Mix - Video " + videoId;
        mix.author = "Channel";
        return mix;
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

    private static final class TestMediaGroup implements MediaGroup {
        private final List<MediaItem> mItems = new ArrayList<>();

        TestMediaGroup(Video... videos) {
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
            return 0;
        }
    }
}
