package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The button at the end of the rows of Home is a channel without an image. A row left with it alone is empty: it isn't shown
 * (see MultipleRowsFragment#update).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoGroupContinuationTest {
    @Test
    public void rowWithTheContinuationButtonOnlyIsEmpty() {
        assertTrue(row(MediaGroup.TYPE_HOME, createContinuation()).isEmpty());
    }

    @Test
    public void rowWithAVideoBeforeTheContinuationButtonIsNotEmpty() {
        assertFalse(row(MediaGroup.TYPE_HOME, createVideo(), createContinuation()).isEmpty());
    }

    /**
     * Gaming and the other sections loaded by the same API as Home
     */
    @Test
    public void continuationButtonOfAHomeLikeSectionIsEmptyToo() {
        assertTrue(row(MediaGroup.TYPE_GAMING, createContinuation()).isEmpty());
    }

    /**
     * Elsewhere a channel without an avatar is still a channel
     */
    @Test
    public void channelWithoutAnImageOutsideHomeIsNotEmpty() {
        assertFalse(row(MediaGroup.TYPE_SUBSCRIPTIONS, createContinuation()).isEmpty());
    }

    @Test
    public void channelWithAnImageIsNotEmpty() {
        Video channel = createContinuation();
        channel.cardImageUrl = "https://yt3.ggpht.com/avatar=s240";

        assertFalse(row(MediaGroup.TYPE_HOME, channel).isEmpty());
    }

    private static VideoGroup row(int sectionId, Video... videos) {
        return VideoGroup.from(new ArrayList<>(Arrays.asList(videos)), new BrowseSection(sectionId, "Section", BrowseSection.TYPE_ROW, 0));
    }

    /**
     * A channel id and a title, no image
     */
    static Video createContinuation() {
        Video continuation = new Video();
        continuation.itemType = MediaItem.TYPE_CHANNEL;
        continuation.channelId = "FEwhat_to_watch";
        continuation.title = "More";
        return continuation;
    }

    private static Video createVideo() {
        Video video = new Video();
        video.itemType = MediaItem.TYPE_VIDEO;
        video.videoId = "video1";
        video.title = "Video";
        video.cardImageUrl = "https://i.ytimg.com/vi/video1/hqdefault.jpg";
        return video;
    }
}
