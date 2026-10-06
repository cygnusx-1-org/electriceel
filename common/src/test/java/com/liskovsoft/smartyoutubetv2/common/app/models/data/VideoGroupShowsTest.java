package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The Shows setting in the groups of the sections (see ShowsData)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoGroupShowsTest {
    private ShowsData mData;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        mData = ShowsData.instance(context);
        mData.setSectionEnabled(MediaGroup.TYPE_HOME, true);
        mData.setSectionEnabled(MediaGroup.TYPE_GAMING, false);
    }

    @After
    public void tearDown() {
        // The defaults
        mData.setMode(ShowsData.MODE_SHOW);
        mData.setSectionEnabled(MediaGroup.TYPE_GAMING, true);
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void showIsHiddenOnlyInPickedSections() {
        mData.setMode(ShowsData.MODE_HIDE);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createShow());
        home.add(createVideo());

        VideoGroup gaming = createGroup(MediaGroup.TYPE_GAMING);
        gaming.add(createShow());

        assertEquals(1, home.getSize());
        assertEquals("video1", home.get(0).videoId);
        assertEquals(1, gaming.getSize());
        assertFalse(gaming.get(0).isShowMarked);
    }

    @Test
    public void showIsMarkedInMarkMode() {
        mData.setMode(ShowsData.MODE_MARK);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createShow());
        home.add(createVideo());

        assertEquals(2, home.getSize());
        assertTrue(home.get(0).isShowMarked);
        assertFalse(home.get(1).isShowMarked);
    }

    /**
     * Search and channel pages have no sidebar section: they're picked in the setting as their own sections
     */
    @Test
    public void showIsMarkedInSearchAndOnChannelPages() {
        mData.setMode(ShowsData.MODE_MARK);

        VideoGroup search = VideoGroup.from(new ArrayList<>());
        search.setType(MediaGroup.TYPE_SEARCH);
        search.add(createShow());
        VideoGroup channel = VideoGroup.from(new ArrayList<>());
        channel.setType(MediaGroup.TYPE_CHANNEL);
        channel.add(createShow());
        // The player's suggestions aren't a section of the setting
        VideoGroup suggestions = VideoGroup.from(new ArrayList<>());
        suggestions.setType(MediaGroup.TYPE_SUGGESTIONS);
        suggestions.add(createShow());

        assertTrue(search.get(0).isShowMarked);
        assertTrue(channel.get(0).isShowMarked);
        assertFalse(suggestions.get(0).isShowMarked);

        mData.setSectionEnabled(MediaGroup.TYPE_SEARCH, false);

        try {
            VideoGroup search2 = VideoGroup.from(new ArrayList<>());
            search2.setType(MediaGroup.TYPE_SEARCH);
            search2.add(createShow());
            assertFalse(search2.get(0).isShowMarked);
        } finally {
            mData.setSectionEnabled(MediaGroup.TYPE_SEARCH, true);
        }
    }

    @Test
    public void showModeLeavesThemAlone() {
        mData.setMode(ShowsData.MODE_SHOW);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createShow());

        assertEquals(1, home.getSize());
        assertFalse(home.get(0).isShowMarked);
    }

    /**
     * Recommended shows has nothing else, and an empty row isn't added (see MultipleRowsFragment)
     */
    @Test
    public void rowOfHiddenShowsIsEmpty() {
        mData.setMode(ShowsData.MODE_HIDE);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createShow());
        home.add(createShow());

        assertTrue(home.isEmpty());
        assertFalse(home.hasOnlyHiddenVideos());
    }

    @Test
    public void markIsKeptByTheCopy() {
        mData.setMode(ShowsData.MODE_MARK);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createShow());

        Video copy = Video.from(home.get(0));

        assertTrue(copy.isShow);
        assertTrue(copy.isShowMarked);
    }

    private static VideoGroup createGroup(int type) {
        return VideoGroup.from(new BrowseSection(type, "Section", BrowseSection.TYPE_GRID, 0));
    }

    private static int sShowCount;

    /**
     * Like the cards of Recommended shows: a playlist that opens as a show page, with no video id
     */
    private static Video createShow() {
        sShowCount++;
        Video video = new Video();
        video.itemType = MediaItem.TYPE_PLAYLIST;
        video.isShow = true;
        video.channelId = "VLPLshow" + sShowCount;
        video.title = "Show " + sShowCount;
        video.badge = "434 episodes";
        video.secondTitle = "Latest episode • Channel";
        return video;
    }

    private static Video createVideo() {
        Video video = new Video();
        video.videoId = "video1";
        video.title = "Video";
        video.author = "Channel";
        video.secondTitle = "Channel • 5K views • 3 weeks ago";
        return video;
    }
}
