package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;
import com.liskovsoft.smartyoutubetv2.common.prefs.OpenedVideosData;
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

/**
 * Hide videos you've opened (see OpenedVideosData): every video with progress on it, in the feeds
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoGroupOpenedVideosTest {
    private static final long DURATION_MS = 600_000;
    private OpenedVideosData mData;
    private VideoStateService mStateService;
    private final List<String> mPlayedIds = new ArrayList<>();
    private int mVideoCount;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        mData = OpenedVideosData.instance(context);
        mStateService = VideoStateService.instance(context);
    }

    @After
    public void tearDown() {
        // The default
        mData.setHidden(false);

        for (String videoId : mPlayedIds) {
            mStateService.removeByVideoId(videoId);
        }

        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void openedVideosAreShownByDefault() {
        assertEquals(2, createSection(MediaGroup.TYPE_SUBSCRIPTIONS, createVideo(50), createVideo(1)).getSize());
    }

    /**
     * However little: a video only just opened too. One without progress (0, or none on the card) stays.
     */
    @Test
    public void anyProgressHidesTheVideo() {
        mData.setHidden(true);

        VideoGroup subscriptions = createSection(MediaGroup.TYPE_SUBSCRIPTIONS,
                createVideo(1), createVideo(50), createVideo(100), createVideo(0), createVideo(-1));

        assertEquals(2, subscriptions.getSize());
        assertEquals(0, subscriptions.get(0).percentWatched, 0);
        assertEquals(-1, subscriptions.get(1).percentWatched, 0);
    }

    /**
     * The card doesn't say (e.g. incognito, or history paused): the app played it. One the app left at the start stays.
     */
    @Test
    public void videoOpenedInTheAppIsHidden() {
        mData.setHidden(true);

        Video played = createVideo(-1);
        Video atStart = createVideo(-1);
        savePlayed(played, 5_000);
        savePlayed(atStart, 0);

        VideoGroup subscriptions = createSection(MediaGroup.TYPE_SUBSCRIPTIONS, played, atStart);

        assertEquals(1, subscriptions.getSize());
        assertEquals(atStart.videoId, subscriptions.get(0).videoId);
    }

    @Test
    public void liveStreamIsLeftAlone() {
        mData.setHidden(true);

        Video live = createVideo(50);
        live.isLive = true;

        assertEquals(1, createSection(MediaGroup.TYPE_SUBSCRIPTIONS, live).getSize());
    }

    @Test
    public void openedVideosAreHiddenFromTheFeeds() {
        mData.setHidden(true);

        int[] feeds = {MediaGroup.TYPE_HOME, MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_GAMING, MediaGroup.TYPE_SHORTS,
                MediaGroup.TYPE_NOTIFICATIONS};

        for (int sectionId : feeds) {
            assertEquals("section " + sectionId, 0, createSection(sectionId, createVideo(50)).getSize());
        }

        assertEquals(0, createPage(MediaGroup.TYPE_SEARCH, createVideo(50)).getSize());
        assertEquals(0, createPage(MediaGroup.TYPE_CHANNEL, createVideo(50)).getSize());

        Video pinned = new Video();
        pinned.channelGroupId = "Climbing";
        VideoGroup group = VideoGroup.from(new BrowseSection("Climbing".hashCode(), "Climbing", BrowseSection.TYPE_GRID, 0, false, pinned));
        group.add(createVideo(50));

        assertEquals(0, group.getSize());
    }

    /**
     * The lists the user keeps, and the player's suggestions
     */
    @Test
    public void listsAreLeftAlone() {
        mData.setHidden(true);

        int[] lists = {MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_WATCH_LATER, MediaGroup.TYPE_USER_PLAYLISTS, MediaGroup.TYPE_MY_VIDEOS};

        for (int sectionId : lists) {
            assertEquals("section " + sectionId, 1, createSection(sectionId, createVideo(50)).getSize());
        }

        assertEquals(1, createPage(MediaGroup.TYPE_SUGGESTIONS, createVideo(50)).getSize());
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
    private static VideoGroup createPage(int type, Video... videos) {
        VideoGroup group = VideoGroup.from(new ArrayList<>());
        group.setType(type);

        for (Video video : videos) {
            group.add(video);
        }

        return group;
    }

    private void savePlayed(Video video, long positionMs) {
        mStateService.save(new State(video, positionMs, DURATION_MS));
        mPlayedIds.add(video.videoId);
    }

    private Video createVideo(float percentWatched) {
        mVideoCount++;
        Video video = new Video();
        video.itemType = MediaItem.TYPE_VIDEO;
        video.videoId = "openedVideo" + mVideoCount;
        video.title = "Video " + mVideoCount;
        video.percentWatched = percentWatched;
        return video;
    }
}
