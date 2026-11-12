package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.prefs.HiddenVideosData;
import com.liskovsoft.smartyoutubetv2.common.prefs.HomeRowsData.Row;
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

/**
 * The videos hidden with the Hide (local) of the context menu: from every section, whichever it was hidden in (see HiddenVideosData)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoGroupHiddenVideosTest {
    private static final BrowseSection HOME = new BrowseSection(MediaGroup.TYPE_HOME, "Home", BrowseSection.TYPE_ROW, 0);
    private HiddenVideosData mData;
    private final List<String> mHiddenIds = new ArrayList<>();
    private int mVideoCount;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        mData = HiddenVideosData.instance(context);
    }

    @After
    public void tearDown() {
        // Kept by the instance past the test
        for (String videoId : mHiddenIds) {
            mData.unhide(videoId);
        }

        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void hiddenVideoIsLeftOutOfEverySection() {
        Video video = createVideo();
        hide(MediaGroup.TYPE_HOME, video);

        assertEquals(0, createSection(MediaGroup.TYPE_HOME, copy(video)).getSize());
        assertEquals(0, createSection(MediaGroup.TYPE_SUBSCRIPTIONS, copy(video)).getSize());
        assertEquals(0, createPage(MediaGroup.TYPE_SEARCH, copy(video)).getSize());
    }

    @Test
    public void hiddenFromAnySectionIsLeftOutOfTheOthers() {
        int[] sections = {MediaGroup.TYPE_HOME, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_GAMING, MediaGroup.TYPE_SUBSCRIPTIONS,
                MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_WATCH_LATER, MediaGroup.TYPE_NOTIFICATIONS, MediaGroup.TYPE_USER_PLAYLISTS,
                MediaGroup.TYPE_CHANNEL_UPLOADS, "Pinned channel".hashCode()};

        for (int sectionId : sections) {
            Video video = createVideo();
            hide(sectionId, video);

            assertEquals("section " + sectionId, 0, createSection(sectionId, copy(video)).getSize());
            assertEquals("section " + sectionId, 0, createSection(MediaGroup.TYPE_MUSIC, copy(video)).getSize());
            assertEquals("section " + sectionId, 0, createPage(MediaGroup.TYPE_SUGGESTIONS, copy(video)).getSize());
        }
    }

    /**
     * Search, a channel page, the player's suggestions: hidden there, it's left out of the sections too
     */
    @Test
    public void pagesHideFromEverySection() {
        int[] types = {MediaGroup.TYPE_SEARCH, MediaGroup.TYPE_CHANNEL, MediaGroup.TYPE_SUGGESTIONS};

        for (int type : types) {
            VideoGroup page = createPage(type);

            assertEquals("type " + type, type, page.getHiddenVideosSection());

            Video video = createVideo();
            hide(type, video);
            page.add(copy(video));

            assertEquals("type " + type, 0, page.getSize());
            assertEquals("type " + type, 0, createSection(MediaGroup.TYPE_HOME, copy(video)).getSize());
        }
    }

    /**
     * E.g. a playlist page: no section to hide a video from, and the hidden ones are kept
     */
    @Test
    public void playlistPageIsNoSection() {
        assertEquals(MediaGroup.TYPE_UNDEFINED, createPage(MediaGroup.TYPE_USER_PLAYLISTS).getHiddenVideosSection());
        assertFalse(HiddenVideosData.isSupportedSection(createPage(MediaGroup.TYPE_USER_PLAYLISTS).getHiddenVideosSection()));

        Video video = createVideo();
        hide(MediaGroup.TYPE_HOME, video);

        assertEquals(1, createPage(MediaGroup.TYPE_USER_PLAYLISTS, copy(video)).getSize());
    }

    /**
     * The row of the Unhide section: the subscription groups are one, their sections and their rows of Home. The Subscriptions row of
     * Home is Subscriptions. The other rows of Home are Home's. Hidden from one, it's left out of all of them.
     */
    @Test
    public void rowsOfHomeAreTheirSections() {
        assertEquals(SectionFilterData.SECTION_CHANNEL_GROUPS, createGroupGrid("Climbing").getHiddenVideosSection());
        assertEquals(SectionFilterData.SECTION_CHANNEL_GROUPS, createRow(Row.channelGroup("group", "Cooking")).getHiddenVideosSection());
        assertEquals(MediaGroup.TYPE_SUBSCRIPTIONS, createRow(Row.subscriptions()).getHiddenVideosSection());
        assertEquals(MediaGroup.TYPE_HOME, createRow(null).getHiddenVideosSection());

        Video video = createVideo();
        hide(SectionFilterData.SECTION_CHANNEL_GROUPS, video);

        VideoGroup climbing = createGroupGrid("Climbing");
        climbing.add(copy(video));
        VideoGroup cookingRow = createRow(Row.channelGroup("group", "Cooking"));
        cookingRow.add(copy(video));
        VideoGroup youTubeRow = createRow(null);
        youTubeRow.add(copy(video));

        assertEquals(0, climbing.getSize());
        assertEquals(0, cookingRow.getSize());
        assertEquals(0, youTubeRow.getSize());
    }

    /**
     * A playlist's video id is its first video's, a channel card or a show isn't a video
     */
    @Test
    public void onlyVideoCardsAreHidden() {
        Video video = createVideo();
        hide(MediaGroup.TYPE_HOME, video);

        Video playlist = copy(video);
        playlist.itemType = MediaItem.TYPE_PLAYLIST;
        Video show = copy(video);
        show.isShow = true;
        Video chapter = copy(video);
        chapter.isChapter = true;

        // A row apart for each: the same video id twice is taken for a duplicate (see VideoGroup.add)
        assertEquals(1, createSection(MediaGroup.TYPE_HOME, playlist).getSize());
        assertEquals(1, createSection(MediaGroup.TYPE_HOME, show).getSize());
        assertEquals(1, createSection(MediaGroup.TYPE_HOME, chapter).getSize());
        assertFalse(VideoGroup.isHideableCard(playlist));
        assertFalse(VideoGroup.isHideableCard(show));
        assertFalse(VideoGroup.isHideableCard(chapter));
        assertTrue(VideoGroup.isHideableCard(video));
    }

    /**
     * The rows already shown let it go (see VideoGroupObjectAdapter.removeNotInterested), whichever section they're of
     */
    @Test
    public void shownVideoIsRemovedOnceHidden() {
        Video video = createVideo();
        VideoGroup home = createSection(MediaGroup.TYPE_HOME, video);
        VideoGroup subscriptions = createSection(MediaGroup.TYPE_SUBSCRIPTIONS, copy(video));

        assertFalse(home.isRemovedWithMenu(home.get(0)));
        assertFalse(subscriptions.isRemovedWithMenu(subscriptions.get(0)));

        hide(MediaGroup.TYPE_HOME, video);

        assertTrue(home.isRemovedWithMenu(home.get(0)));
        assertTrue(subscriptions.isRemovedWithMenu(subscriptions.get(0)));
    }

    /**
     * A row left with only cards without a video isn't shown (see BrowsePresenter)
     */
    @Test
    public void hiddenVideosAreCounted() {
        Video video = createVideo();
        hide(MediaGroup.TYPE_HOME, video);

        Video channel = new Video();
        channel.itemType = MediaItem.TYPE_CHANNEL;
        channel.channelId = "UCclimbing";
        channel.title = "Climbing";

        assertTrue(createSection(MediaGroup.TYPE_HOME, copy(video), channel).hasOnlyHiddenVideos());
    }

    /**
     * Its cards are the hidden ones: nothing hides them there
     */
    @Test
    public void unhideSectionShowsTheHiddenVideos() {
        Video video = createVideo();
        hide(MediaGroup.TYPE_HOME, video);

        BrowseSection unhide = new BrowseSection(MediaGroup.TYPE_HIDDEN_VIDEOS, "Unhide", BrowseSection.TYPE_ROW, 0);
        VideoGroup row = VideoGroup.from(mData.getSections().get(0).videos, unhide, -1);

        assertEquals(1, row.getSize());
        assertTrue(row.get(0).belongsToHiddenVideos());
        assertFalse(row.isRemovedWithMenu(row.get(0)));
    }

    private void hide(int sectionId, Video video) {
        mData.hide(sectionId, null, video);
        mHiddenIds.add(video.videoId);
    }

    private static VideoGroup createSection(int sectionId, Video... videos) {
        VideoGroup group = VideoGroup.from(new BrowseSection(sectionId, "Section", BrowseSection.TYPE_GRID, 0));

        for (Video video : videos) {
            group.add(video);
        }

        return group;
    }

    /**
     * Search, a channel page or the player's suggestions: no sidebar section
     */
    private static VideoGroup createPage(int type) {
        VideoGroup group = VideoGroup.from(new ArrayList<>());
        group.setType(type);
        return group;
    }

    private static VideoGroup createPage(int type, Video... videos) {
        VideoGroup group = createPage(type);

        for (Video video : videos) {
            group.add(video);
        }

        return group;
    }

    private static VideoGroup createGroupGrid(String title) {
        Video pinned = new Video();
        pinned.channelGroupId = title;
        return VideoGroup.from(new BrowseSection(title.hashCode(), title, BrowseSection.TYPE_GRID, 0, false, pinned));
    }

    private static VideoGroup createRow(Row addedHomeRow) {
        return VideoGroup.from(new EmptyMediaGroup(), HOME, 0, null, addedHomeRow);
    }

    /**
     * Ids of their own: the hidden ones outlive the test, unless unhidden
     */
    private Video createVideo() {
        mVideoCount++;
        Video video = new Video();
        video.itemType = MediaItem.TYPE_VIDEO;
        video.videoId = "hiddenVideo" + mVideoCount;
        video.title = "Video " + mVideoCount;
        return video;
    }

    /**
     * The same video in another row: a card belongs to one
     */
    private static Video copy(Video video) {
        Video copy = new Video();
        copy.itemType = video.itemType;
        copy.videoId = video.videoId;
        copy.title = video.title;
        return copy;
    }

    /**
     * A feed without a type, as the one of a subscription group: the cards are added after
     */
    private static final class EmptyMediaGroup implements MediaGroup {
        @Override
        public int getType() {
            return MediaGroup.TYPE_UNDEFINED;
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
