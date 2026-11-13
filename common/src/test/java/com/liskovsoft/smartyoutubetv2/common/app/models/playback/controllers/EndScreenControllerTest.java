package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.mediaserviceinterfaces.data.EndScreenItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.EndScreenPlacement;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * When the end screen cards show, which can be shown, what a click opens and where a card goes on the screen
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class EndScreenControllerTest {
    // A 20:42 video with cards over its last 20 s, as YouTube gives them (endMs about the media's end)
    private static final long DURATION_MS = 2_067_400;
    private static final long END_SCREEN_START_MS = 2_047_398;
    private static final long END_SCREEN_END_MS = 2_067_398;

    @Test
    public void noCardBeforeItsStart() {
        TestItem video = TestItem.video("v1", END_SCREEN_START_MS, END_SCREEN_END_MS);

        assertTrue(EndScreenController.findShown(Collections.singletonList(video), END_SCREEN_START_MS - 1, DURATION_MS).isEmpty());
        assertEquals(Collections.singletonList(video),
                EndScreenController.findShown(Collections.singletonList(video), END_SCREEN_START_MS, DURATION_MS));
    }

    /**
     * Each card has its own time: one that ends before the end goes then, the others stay
     */
    @Test
    public void cardThatEndsEarlierGoes() {
        TestItem early = TestItem.video("v1", END_SCREEN_START_MS, END_SCREEN_START_MS + 5_000);
        TestItem toTheEnd = TestItem.video("v2", END_SCREEN_START_MS, END_SCREEN_END_MS);
        List<EndScreenItem> items = Arrays.asList(early, toTheEnd);

        assertEquals(items, EndScreenController.findShown(items, END_SCREEN_START_MS + 4_999, DURATION_MS));
        assertEquals(Collections.singletonList(toTheEnd), EndScreenController.findShown(items, END_SCREEN_START_MS + 5_000, DURATION_MS));
    }

    /**
     * The video ended and the playback mode pauses there: the cards that last to the end are still there, past their end time
     */
    @Test
    public void cardsToTheEndStayAfterTheVideoEnded() {
        TestItem toTheEnd = TestItem.video("v1", END_SCREEN_START_MS, END_SCREEN_END_MS);

        assertEquals(Collections.singletonList(toTheEnd),
                EndScreenController.findShown(Collections.singletonList(toTheEnd), DURATION_MS, DURATION_MS));
    }

    /**
     * Seeking back before the end screen: the cards go
     */
    @Test
    public void seekingBackHidesThem() {
        TestItem toTheEnd = TestItem.video("v1", END_SCREEN_START_MS, END_SCREEN_END_MS);

        assertTrue(EndScreenController.findShown(Collections.singletonList(toTheEnd), 60_000, DURATION_MS).isEmpty());
    }

    @Test
    public void cardsWithoutTargetPlaceOrTimeAreDropped() {
        TestItem video = TestItem.video("v1", 0, 10_000);
        TestItem noVideoId = TestItem.video(null, 0, 10_000);
        TestItem unknownKind = TestItem.video("v2", 0, 10_000);
        unknownKind.type = -1;
        TestItem noWidth = TestItem.video("v3", 0, 10_000);
        noWidth.width = 0;
        TestItem noAspect = TestItem.video("v4", 0, 10_000);
        noAspect.aspectRatio = 0;
        TestItem noTime = TestItem.video("v5", -1, -1);
        TestItem endsBeforeStart = TestItem.video("v6", 10_000, 5_000);
        TestItem channel = TestItem.channel("UC1");
        TestItem channelWithoutId = TestItem.channel(null);
        TestItem link = TestItem.link("https://example.com/");
        TestItem linkWithoutUrl = TestItem.link(null);
        TestItem playlistPage = TestItem.playlist(null, "PL1");

        List<EndScreenItem> result = EndScreenController.filterCards(Arrays.asList(video, noVideoId, unknownKind, noWidth, noAspect,
                noTime, endsBeforeStart, channel, channelWithoutId, link, linkWithoutUrl, playlistPage, null));

        assertEquals(Arrays.asList(video, channel, link, playlistPage), result);
        assertTrue(EndScreenController.filterCards(null).isEmpty());
    }

    @Test
    public void videoCardOpensAsASuggestion() {
        Video video = EndScreenController.toVideo(TestItem.video("v1", 0, 10_000));

        assertEquals("v1", video.videoId);
        assertNull(video.playlistId);
        assertNull(video.channelId);
        assertEquals("Title", video.title);
        assertEquals("https://i.ytimg.com/vi/v1/sddefault.jpg", video.cardImageUrl);
    }

    /**
     * Its video plays in the playlist
     */
    @Test
    public void playlistCardPlaysInThePlaylist() {
        Video video = EndScreenController.toVideo(TestItem.playlist("v1", "PL1"));

        assertEquals("v1", video.videoId);
        assertEquals("PL1", video.playlistId);
    }

    @Test
    public void channelCardOpensTheChannel() {
        Video video = EndScreenController.toVideo(TestItem.channel("UC1"));

        assertEquals("UC1", video.channelId);
        assertNull(video.videoId);
        assertTrue(video.hasChannel());
    }

    /**
     * A link isn't a suggestion: it's shown as a QR code
     */
    @Test
    public void linkCardIsNoSuggestion() {
        assertNull(EndScreenController.toVideo(TestItem.link("https://example.com/")));
    }

    /**
     * On the whole 1080p screen, a card where YouTube's web player puts it
     */
    @Test
    public void placedOnTheFrame() {
        TestItem video = TestItem.video("v1", 0, 10_000);
        video.left = 0.022807017f;
        video.top = 0.52454501f;
        video.width = 0.32280701f;
        video.aspectRatio = 1.7777778f;

        assertArrayEquals(new int[] {44, 567, 44 + 620, 567 + 349}, EndScreenPlacement.place(video, 0, 0, 1920, 1080));
    }

    /**
     * The frame is smaller than the screen and moved (e.g. next to a dialog): so is the card
     */
    @Test
    public void placedOnAMovedFrame() {
        TestItem channel = TestItem.channel("UC1");
        channel.left = 0.5f;
        channel.top = 0.25f;
        channel.width = 0.1f;
        channel.aspectRatio = 1f;

        assertArrayEquals(new int[] {100 + 480, 50 + 135, 100 + 480 + 96, 50 + 135 + 96}, EndScreenPlacement.place(channel, 100, 50, 960, 540));
    }

    private static final class TestItem implements EndScreenItem {
        int type;
        String videoId;
        String playlistId;
        String channelId;
        String url;
        float left = 0.1f;
        float top = 0.1f;
        float width = 0.3f;
        float aspectRatio = 1.7777778f;
        long startMs;
        long endMs;

        static TestItem video(String videoId, long startMs, long endMs) {
            TestItem item = new TestItem();
            item.type = TYPE_VIDEO;
            item.videoId = videoId;
            item.startMs = startMs;
            item.endMs = endMs;
            return item;
        }

        static TestItem playlist(String videoId, String playlistId) {
            TestItem item = video(videoId, 0, 10_000);
            item.type = TYPE_PLAYLIST;
            item.playlistId = playlistId;
            return item;
        }

        static TestItem channel(String channelId) {
            TestItem item = video(null, 0, 10_000);
            item.type = TYPE_CHANNEL;
            item.channelId = channelId;
            item.aspectRatio = 1f;
            return item;
        }

        static TestItem link(String url) {
            TestItem item = video(null, 0, 10_000);
            item.type = TYPE_LINK;
            item.url = url;
            item.aspectRatio = 1f;
            return item;
        }

        @Override
        public int getType() {
            return type;
        }

        @Override
        public String getTitle() {
            return "Title";
        }

        @Override
        public String getMetadata() {
            return null;
        }

        @Override
        public String getBadge() {
            return null;
        }

        @Override
        public String getImageUrl() {
            return "https://i.ytimg.com/vi/" + videoId + "/sddefault.jpg";
        }

        @Override
        public String getVideoId() {
            return videoId;
        }

        @Override
        public String getPlaylistId() {
            return playlistId;
        }

        @Override
        public String getChannelId() {
            return channelId;
        }

        @Override
        public String getUrl() {
            return url;
        }

        @Override
        public boolean isSubscribe() {
            return false;
        }

        @Override
        public float getLeft() {
            return left;
        }

        @Override
        public float getTop() {
            return top;
        }

        @Override
        public float getWidth() {
            return width;
        }

        @Override
        public float getAspectRatio() {
            return aspectRatio;
        }

        @Override
        public long getStartMs() {
            return startMs;
        }

        @Override
        public long getEndMs() {
            return endMs;
        }
    }
}
