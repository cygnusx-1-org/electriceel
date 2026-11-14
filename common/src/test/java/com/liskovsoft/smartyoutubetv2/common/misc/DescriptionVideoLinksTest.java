package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.smartyoutubetv2.common.misc.DescriptionVideoLinks.VideoLink;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DescriptionVideoLinksTest {
    private static final String CURRENT = "currentVid1";

    @Test
    public void eachKindOfAddress() {
        List<VideoLink> links = DescriptionVideoLinks.parse(
                "Previous video: https://youtu.be/aaaaaaaaaa1\n" +
                "Part 1 - https://www.youtube.com/watch?v=aaaaaaaaaa2\n" +
                "Short: https://youtube.com/shorts/aaaaaaaaaa3\n" +
                "The stream: https://m.youtube.com/live/aaaaaaaaaa4?si=abc\n" +
                "Embedded: http://www.youtube.com/embed/aaaaaaaaaa5\n" +
                "Song: https://music.youtube.com/watch?v=aaaaaaaaaa6&list=RDAMVM\n" +
                "No scheme: www.youtube.com/watch?v=aaaaaaaaaa7\n" +
                "Shared: https://www.youtube.com/watch?feature=shared&v=aaaaaaaaaa8\n" +
                "Old: https://www.youtube.com/v/aaaa-aaa_a9", CURRENT);

        assertEquals(9, links.size());
        assertLink("aaaaaaaaaa1", -1, "Previous video: https://youtu.be/aaaaaaaaaa1", links.get(0));
        assertEquals("aaaaaaaaaa2", links.get(1).videoId);
        assertEquals("aaaaaaaaaa3", links.get(2).videoId);
        assertEquals("aaaaaaaaaa4", links.get(3).videoId);
        assertEquals("aaaaaaaaaa5", links.get(4).videoId);
        assertEquals("aaaaaaaaaa6", links.get(5).videoId);
        assertEquals("aaaaaaaaaa7", links.get(6).videoId);
        assertEquals("aaaaaaaaaa8", links.get(7).videoId);
        assertEquals("aaaa-aaa_a9", links.get(8).videoId);
    }

    /**
     * Not a video's: a channel, a playlist, a search, another site (e.g. a shortener whose path is 11 characters long, which
     * IntentExtractor takes for a video), an id of another length, another address that has youtube.com in it
     */
    @Test
    public void notVideoLinks() {
        String description = "Subscribe: https://www.youtube.com/@channel\n" +
                "Channel: https://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaaaa\n" +
                "Playlist: https://www.youtube.com/playlist?list=PLaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n" +
                "Search: https://www.youtube.com/results?search_query=aaaaaaaaaaa\n" +
                "Subscribe for more: http://bit.ly/DFSubscribe\n" +
                "Too long: https://youtu.be/aaaaaaaaaaaa\n" +
                "Too short: https://www.youtube.com/watch?v=aaaaaaaaaa\n" +
                "Other site: https://notyoutube.com/watch?v=aaaaaaaaaa1\n" +
                "Redirect: https://example.com/youtu.be/aaaaaaaaaa2\n" +
                "Clip: https://youtube.com/clip/Ugkxaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        assertTrue(DescriptionVideoLinks.parse(description, CURRENT).isEmpty());
    }

    @Test
    public void timeOfTheAddress() {
        List<VideoLink> links = DescriptionVideoLinks.parse(
                "https://youtu.be/aaaaaaaaaa1?t=90\n" +
                "https://youtu.be/aaaaaaaaaa2?si=abc&t=1m30s\n" +
                "https://www.youtube.com/watch?v=aaaaaaaaaa3&t=1h2m3s\n" +
                "https://www.youtube.com/watch?t=42s&v=aaaaaaaaaa4\n" +
                "https://www.youtube.com/embed/aaaaaaaaaa5?start=15\n" +
                "https://youtu.be/aaaaaaaaaa6?t=soon\n" +
                "https://youtu.be/aaaaaaaaaa7?t=0", CURRENT);

        assertEquals(90_000, links.get(0).positionMs);
        assertEquals(90_000, links.get(1).positionMs);
        assertEquals(3_723_000, links.get(2).positionMs);
        assertEquals(42_000, links.get(3).positionMs);
        assertEquals(15_000, links.get(4).positionMs);
        assertEquals(-1, links.get(5).positionMs);
        assertEquals(0, links.get(6).positionMs);
    }

    @Test
    public void textIsTheLineAsWritten() {
        List<VideoLink> links = DescriptionVideoLinks.parse("  Next part ▶ https://youtu.be/aaaaaaaaaa1 (out now)\t\r\n" +
                "[Part 2](https://youtu.be/aaaaaaaaaa2)", CURRENT);

        assertEquals("Next part ▶ https://youtu.be/aaaaaaaaaa1 (out now)", links.get(0).text);
        assertEquals("[Part 2](https://youtu.be/aaaaaaaaaa2)", links.get(1).text);
        assertEquals("aaaaaaaaaa2", links.get(1).videoId);
    }

    /**
     * A line of only a link gets the line without a link it's under in front, up to a blank line
     */
    @Test
    public void bareLinkLabeledByTheLineAbove() {
        List<VideoLink> links = DescriptionVideoLinks.parse("Previous video:\nhttps://youtu.be/aaaaaaaaaa1\n\n" +
                "Watch the series:\n▶ https://youtu.be/aaaaaaaaaa2\nhttps://youtu.be/aaaaaaaaaa3 ->\n\n" +
                "https://youtu.be/aaaaaaaaaa4", CURRENT);

        assertEquals("Previous video: https://youtu.be/aaaaaaaaaa1", links.get(0).text);
        assertEquals("Watch the series: ▶ https://youtu.be/aaaaaaaaaa2", links.get(1).text);
        assertEquals("Watch the series: https://youtu.be/aaaaaaaaaa3 ->", links.get(2).text);
        // A blank line before it: nothing over it
        assertEquals("https://youtu.be/aaaaaaaaaa4", links.get(3).text);
    }

    /**
     * Another address isn't a label (e.g. the channel's)
     */
    @Test
    public void addressNotALabel() {
        List<VideoLink> links = DescriptionVideoLinks.parse("Follow me: https://www.youtube.com/@channel\nhttps://youtu.be/aaaaaaaaaa1",
                CURRENT);

        assertEquals("https://youtu.be/aaaaaaaaaa1", links.get(0).text);
    }

    @Test
    public void everyLinkOfALine() {
        List<VideoLink> links = DescriptionVideoLinks.parse("Parts: https://youtu.be/aaaaaaaaaa1 https://youtu.be/aaaaaaaaaa2", CURRENT);

        assertEquals(2, links.size());
        assertEquals("aaaaaaaaaa1", links.get(0).videoId);
        assertEquals("aaaaaaaaaa2", links.get(1).videoId);
        assertEquals(links.get(0).text, links.get(1).text);
    }

    /**
     * The same video at the same time once, the first one written. The video of the description left out: not another video.
     */
    @Test
    public void onceEachAndNotTheVideoItself() {
        List<VideoLink> links = DescriptionVideoLinks.parse("First: https://youtu.be/aaaaaaaaaa1\n" +
                "Again: https://www.youtube.com/watch?v=aaaaaaaaaa1\n" +
                "Later in it: https://youtu.be/aaaaaaaaaa1?t=60\n" +
                "This one: https://youtu.be/" + CURRENT, CURRENT);

        assertEquals(2, links.size());
        assertLink("aaaaaaaaaa1", -1, "First: https://youtu.be/aaaaaaaaaa1", links.get(0));
        assertLink("aaaaaaaaaa1", 60_000, "Later in it: https://youtu.be/aaaaaaaaaa1?t=60", links.get(1));
        assertEquals(1, DescriptionVideoLinks.parse("https://youtu.be/" + CURRENT, null).size());
    }

    @Test
    public void noDescription() {
        assertTrue(DescriptionVideoLinks.parse(null, CURRENT).isEmpty());
        assertTrue(DescriptionVideoLinks.parse("", CURRENT).isEmpty());
        assertTrue(DescriptionVideoLinks.parse("No links here\n\n0:00 Intro", CURRENT).isEmpty());
    }

    private static void assertLink(String videoId, long positionMs, String text, VideoLink link) {
        assertEquals(videoId, link.videoId);
        assertEquals(positionMs, link.positionMs);
        assertEquals(text, link.text);
    }
}
