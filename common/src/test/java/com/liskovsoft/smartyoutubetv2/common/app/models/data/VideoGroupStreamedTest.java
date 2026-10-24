package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.prefs.FilterExclusionData;
import com.liskovsoft.smartyoutubetv2.common.prefs.StreamedData;
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
 * The Streamed or Live setting in the groups of the sections (see StreamedData)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoGroupStreamedTest {
    private StreamedData mData;
    private FilterExclusionData mExclusions;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        mData = StreamedData.instance(context);
        mExclusions = FilterExclusionData.instance(context);
        mData.setSectionEnabled(MediaGroup.TYPE_HOME, true);
        mData.setSectionEnabled(MediaGroup.TYPE_GAMING, false);
    }

    @After
    public void tearDown() {
        // The defaults
        mData.setMode(StreamedData.MODE_MARK);
        mData.setSectionEnabled(MediaGroup.TYPE_GAMING, true);
        mExclusions.setExcluded(FilterExclusionData.FILTER_STREAMED, "UCclimbing", "World Climbing", false);
        mExclusions.setExcluded(FilterExclusionData.FILTER_WATCH_LATER, "UCclimbing", "World Climbing", false);
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void streamedIsMarkedByDefault() {
        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createStreamed("Craft Computing • 3.8K views • Streamed 5 hours ago"));
        home.add(createVideo());

        assertEquals(2, home.getSize());
        assertTrue(home.get(0).isStreamedMarked);
        assertFalse(home.get(1).isStreamedMarked);
    }

    @Test
    public void streamedIsHiddenOnlyInPickedSections() {
        mData.setMode(StreamedData.MODE_HIDE);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createStreamed("Channel • 11K views • Streamed live on Oct 6, 2026"));
        home.add(createVideo());

        VideoGroup gaming = createGroup(MediaGroup.TYPE_GAMING);
        gaming.add(createStreamed("Channel • 11K views • Streamed 7 days ago"));

        assertEquals(1, home.getSize());
        assertEquals("video1", home.get(0).videoId);
        assertEquals(1, gaming.getSize());
        assertFalse(gaming.get(0).isStreamedMarked);
    }

    /**
     * Nothing is hidden from History: it's marked there instead
     */
    @Test
    public void streamedIsMarkedInHistoryInHideMode() {
        mData.setMode(StreamedData.MODE_HIDE);

        VideoGroup history = createGroup(MediaGroup.TYPE_HISTORY);
        history.add(createStreamed("Channel • 11K views • Streamed 7 days ago"));

        assertEquals(1, history.getSize());
        assertTrue(history.get(0).isStreamedMarked);
    }

    /**
     * Search and channel pages have no sidebar section: they're picked in the setting as their own sections
     */
    @Test
    public void streamedIsMarkedInSearchAndOnChannelPages() {
        VideoGroup search = VideoGroup.from(new ArrayList<>());
        search.setType(MediaGroup.TYPE_SEARCH);
        search.add(createStreamed("Channel • Streamed 2 weeks ago"));
        VideoGroup channel = VideoGroup.from(new ArrayList<>());
        channel.setType(MediaGroup.TYPE_CHANNEL);
        channel.add(createStreamed("Channel • Streamed 2 weeks ago"));
        // The player's suggestions aren't a section of the setting
        VideoGroup suggestions = VideoGroup.from(new ArrayList<>());
        suggestions.setType(MediaGroup.TYPE_SUGGESTIONS);
        suggestions.add(createStreamed("Channel • Streamed 2 weeks ago"));

        assertTrue(search.get(0).isStreamedMarked);
        assertTrue(channel.get(0).isStreamedMarked);
        assertFalse(suggestions.get(0).isStreamedMarked);
    }

    @Test
    public void showModeLeavesThemAlone() {
        mData.setMode(StreamedData.MODE_SHOW);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createStreamed("Channel • Streamed 2 weeks ago"));

        assertEquals(1, home.getSize());
        assertFalse(home.get(0).isStreamedMarked);
    }

    /**
     * A stream that's live now is marked "Live", not "Streamed", whatever its line says
     */
    @Test
    public void liveIsMarkedLive() {
        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createLive("Channel • 1.2K watching"));
        home.add(createLive("Channel • Streamed 2 hours ago"));

        assertEquals(2, home.getSize());
        assertTrue(home.get(0).isLiveMarked);
        assertFalse(home.get(0).isStreamedMarked);
        assertTrue(home.get(1).isLiveMarked);
        assertFalse(home.get(1).isStreamedMarked);
    }

    /**
     * Only a stream that ended or that's live now: one to come isn't marked or hidden, whatever its line says
     */
    @Test
    public void liveIsHiddenAndUpcomingIsLeftAlone() {
        mData.setMode(StreamedData.MODE_HIDE);

        Video upcoming = createStreamed("Channel • Streamed 2 hours ago");
        upcoming.isUpcoming = true;

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createLive("Channel • 1.2K watching"));
        home.add(upcoming);

        assertEquals(1, home.getSize());
        assertEquals(upcoming.videoId, home.get(0).videoId);
        assertFalse(home.get(0).isStreamedMarked);
        assertFalse(home.get(0).isLiveMarked);
    }

    /**
     * Nothing is hidden from History: it's marked there instead
     */
    @Test
    public void liveIsMarkedInHistoryInHideMode() {
        mData.setMode(StreamedData.MODE_HIDE);

        VideoGroup history = createGroup(MediaGroup.TYPE_HISTORY);
        history.add(createLive("Channel • 1.2K watching"));

        assertEquals(1, history.getSize());
        assertTrue(history.get(0).isLiveMarked);
    }

    /**
     * The Live section, where every video is a stream, isn't one of the setting's sections
     */
    @Test
    public void liveSectionIsLeftAlone() {
        mData.setMode(StreamedData.MODE_HIDE);
        mData.setSectionEnabled(MediaGroup.TYPE_LIVE, true);

        VideoGroup live = createGroup(MediaGroup.TYPE_LIVE);
        live.add(createLive("Channel • 1.2K watching"));
        live.add(createStreamed("Channel • Streamed 2 hours ago"));

        assertEquals(2, live.getSize());
        assertFalse(live.get(0).isLiveMarked);
        assertFalse(live.get(1).isStreamedMarked);
    }

    /**
     * Its channel is excluded from the hiding in the context menu (see FilterExclusionData). Found by the name, which is
     * all most TV cards have.
     */
    @Test
    public void streamedOfAnExcludedChannelIsMarkedInHideMode() {
        mData.setMode(StreamedData.MODE_HIDE);
        mExclusions.setExcluded(FilterExclusionData.FILTER_STREAMED, "UCclimbing", "World Climbing", true);

        Video climbing = createStreamed("World Climbing • 40K views • Streamed 2 days ago");
        climbing.author = "World Climbing";

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(climbing);
        home.add(createStreamed("Channel • 11K views • Streamed 7 days ago"));

        assertEquals(1, home.getSize());
        assertEquals(climbing.videoId, home.get(0).videoId);
        assertTrue(home.get(0).isStreamedMarked);
    }

    @Test
    public void channelExcludedFromAnotherFilterIsHidden() {
        mData.setMode(StreamedData.MODE_HIDE);
        mExclusions.setExcluded(FilterExclusionData.FILTER_WATCH_LATER, "UCclimbing", "World Climbing", true);

        Video climbing = createStreamed("World Climbing • 40K views • Streamed 2 days ago");
        climbing.author = "World Climbing";

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(climbing);

        assertEquals(0, home.getSize());
    }

    @Test
    public void markIsKeptByTheCopy() {
        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createStreamed("Channel • Streamed 2 weeks ago"));
        home.add(createLive("Channel • 1.2K watching"));

        assertTrue(Video.from(home.get(0)).isStreamedMarked);
        assertTrue(Video.from(home.get(1)).isLiveMarked);
    }

    private static VideoGroup createGroup(int type) {
        return VideoGroup.from(new BrowseSection(type, "Section", BrowseSection.TYPE_GRID, 0));
    }

    private static int sStreamedCount;

    private static Video createStreamed(String secondTitle) {
        sStreamedCount++;
        Video video = new Video();
        video.videoId = "streamed" + sStreamedCount;
        video.title = "Stream " + sStreamedCount;
        video.author = "Channel";
        video.badge = "2:15:39";
        video.secondTitle = secondTitle;
        return video;
    }

    private static Video createLive(String secondTitle) {
        Video video = createStreamed(secondTitle);
        video.badge = null;
        video.isLive = true;
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
