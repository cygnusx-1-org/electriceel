package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * "Not interested" and "Don't recommend channel" in every row (see VideoGroup#isNotInterested)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NotInterestedManagerTest {
    private static final String ACCOUNT = "one@example.com";
    private static final String OTHER_ACCOUNT = "two@example.com";
    private NotInterestedManager mManager;
    private String mAccount = ACCOUNT;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        mManager = NotInterestedManager.instance();
        mManager.resetForTesting(() -> mAccount);
    }

    @After
    public void tearDown() {
        mManager.resetForTesting(() -> null);
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    /**
     * The rows already loaded have it too: they remove it (see VideoGroupObjectAdapter#removeNotInterested)
     */
    @Test
    public void videoIsNotInterestedInTheOtherRows() {
        VideoGroup recommended = createGroup(MediaGroup.TYPE_HOME);
        recommended.add(createVideo("video1", "UC1", "Channel"));
        VideoGroup topic = createGroup(MediaGroup.TYPE_HOME);
        topic.add(createVideo("video1", "UC1", "Channel"));
        topic.add(createVideo("video2", "UC1", "Channel"));

        mManager.addVideo(recommended.get(0));

        assertTrue(topic.isNotInterested(topic.get(0)));
        assertFalse(topic.isNotInterested(topic.get(1)));
    }

    @Test
    public void videoIsHiddenInTheRowsLoadedLater() {
        mManager.addVideo(createVideo("video1", "UC1", "Channel"));

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createVideo("video1", "UC1", "Channel"));
        home.add(createVideo("video2", "UC1", "Channel"));

        assertEquals(1, home.getSize());
        assertEquals("video2", home.get(0).videoId);
    }

    @Test
    public void hiddenOnlyWhereItsOffered() {
        mManager.addVideo(createVideo("video1", "UC1", "Channel"));

        int[] hiding = {MediaGroup.TYPE_HOME, MediaGroup.TYPE_GAMING, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_NEWS, MediaGroup.TYPE_SPORTS,
                MediaGroup.TYPE_LIVE, MediaGroup.TYPE_MOVIES, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_SUGGESTIONS};

        for (int type : hiding) {
            VideoGroup group = createGroup(type);
            group.add(createVideo("video1", "UC1", "Channel"));
            assertTrue("type " + type, group.isEmpty());
        }

        int[] showing = {MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_SEARCH, MediaGroup.TYPE_CHANNEL,
                MediaGroup.TYPE_CHANNEL_UPLOADS, MediaGroup.TYPE_USER_PLAYLISTS};

        for (int type : showing) {
            VideoGroup group = createGroup(type);
            group.add(createVideo("video1", "UC1", "Channel"));
            assertEquals("type " + type, 1, group.getSize());
        }
    }

    /**
     * The user's own list, not a recommendation
     */
    @Test
    public void playlistThatPlaysKeepsIt() {
        mManager.addVideo(createVideo("video1", "UC1", "Channel"));

        Video inPlaylist = createVideo("video1", "UC1", "Channel");
        inPlaylist.playlistId = "PL1";
        VideoGroup suggestions = createGroup(MediaGroup.TYPE_SUGGESTIONS);
        suggestions.add(inPlaylist);

        assertEquals(1, suggestions.getSize());
    }

    /**
     * A playlist card has the video id of its first video
     */
    @Test
    public void playlistCardIsNotItsFirstVideo() {
        mManager.addVideo(createVideo("video1", "UC1", "Channel"));

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createPlaylist("video1"));

        assertEquals(1, home.getSize());

        // Nor the other way round
        mManager.resetForTesting(() -> mAccount);
        mManager.addVideo(createPlaylist("video2"));

        VideoGroup gaming = createGroup(MediaGroup.TYPE_GAMING);
        gaming.add(createVideo("video2", "UC1", "Channel"));

        assertEquals(1, gaming.getSize());
    }

    @Test
    public void channelIsHiddenInEveryRow() {
        mManager.addChannel(createVideo("video1", "UC1", "Channel"));

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createVideo("video2", "UC1", "Channel"));
        home.add(createVideo("video3", null, "Channel")); // most TV cards have only the name
        home.add(createPlaylist("video4"));
        home.add(createVideo("video5", "UC2", "Channel")); // another channel, the same name
        home.add(createVideo("video6", "UC3", "Other"));
        home.add(createChannel("UC1", "Channel"));

        assertEquals(3, home.getSize());
        assertEquals("video5", home.get(0).videoId);
        assertEquals("video6", home.get(1).videoId);
        assertEquals("UC1", home.get(2).channelId);
    }

    @Test
    public void otherAccountDoesntHideThem() {
        mManager.addVideo(createVideo("video1", "UC1", "Channel"));
        mManager.addChannel(createVideo("video2", "UC2", "Channel 2"));

        mAccount = OTHER_ACCOUNT;

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createVideo("video1", "UC1", "Channel"));
        home.add(createVideo("video3", "UC2", "Channel 2"));

        assertEquals(2, home.getSize());

        // Marked with it: the ones before are forgotten
        mManager.addVideo(createVideo("video4", "UC4", "Channel 4"));
        mAccount = ACCOUNT;

        VideoGroup gaming = createGroup(MediaGroup.TYPE_GAMING);
        gaming.add(createVideo("video1", "UC1", "Channel"));

        assertEquals(1, gaming.getSize());
    }

    @Test
    public void chapterIsNotHidden() {
        Video video = createVideo("video1", "UC1", "Channel");
        mManager.addVideo(video);

        Video chapter = createVideo("video1", "UC1", "Channel");
        chapter.isChapter = true;

        assertTrue(mManager.isHidden(video));
        assertFalse(mManager.isHidden(chapter));
    }

    private static VideoGroup createGroup(int type) {
        return VideoGroup.from(new BrowseSection(type, "Section", BrowseSection.TYPE_GRID, 0));
    }

    private static Video createVideo(String videoId, String channelId, String author) {
        Video video = new Video();
        video.videoId = videoId;
        video.itemType = MediaItem.TYPE_VIDEO;
        video.channelId = channelId;
        video.title = "Video " + videoId;
        video.author = author;
        video.secondTitle = author + " • 5K views • 3 weeks ago";
        return video;
    }

    private static Video createPlaylist(String firstVideoId) {
        Video video = createVideo(firstVideoId, "UC1", "Channel");
        video.itemType = MediaItem.TYPE_PLAYLIST;
        video.playlistId = "PL" + firstVideoId;
        return video;
    }

    private static Video createChannel(String channelId, String author) {
        Video video = new Video();
        video.itemType = MediaItem.TYPE_CHANNEL;
        video.channelId = channelId;
        video.title = author;
        video.author = author;
        return video;
    }
}
