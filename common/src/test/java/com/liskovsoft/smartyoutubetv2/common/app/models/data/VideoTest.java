package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoTest {
    /**
     * Loaded by the same API as Home, so their videos get the Home feedback ("Not interested", "Don't recommend channel")
     */
    @Test
    public void sectionsLoadedLikeHomeAreHomeLike() {
        int[] types = {MediaGroup.TYPE_HOME, MediaGroup.TYPE_GAMING, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_NEWS,
                MediaGroup.TYPE_SPORTS, MediaGroup.TYPE_LIVE, MediaGroup.TYPE_MOVIES};

        for (int type : types) {
            VideoGroup group = createGroup(type);
            assertTrue("type " + type, createVideo(group).belongsToHomeLikeSection());
        }
    }

    /**
     * Not loaded like Home. Their own feedback, if any, is another action (e.g. History: "Remove from watch history", Subscriptions: "Hide")
     */
    @Test
    public void otherSectionsAreNotHomeLike() {
        int[] types = {MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_SEARCH,
                MediaGroup.TYPE_SUGGESTIONS, MediaGroup.TYPE_TRENDING, MediaGroup.TYPE_KIDS_HOME, MediaGroup.TYPE_CHANNEL,
                MediaGroup.TYPE_CHANNEL_UPLOADS, MediaGroup.TYPE_USER_PLAYLISTS, MediaGroup.TYPE_NOTIFICATIONS, MediaGroup.TYPE_MY_VIDEOS};

        for (int type : types) {
            VideoGroup group = createGroup(type);
            assertFalse("type " + type, createVideo(group).belongsToHomeLikeSection());
        }
    }

    @Test
    public void videoWithoutGroupIsNotHomeLike() {
        assertFalse(new Video().belongsToHomeLikeSection());
    }

    private static VideoGroup createGroup(int type) {
        VideoGroup group = VideoGroup.from(new ArrayList<>());
        group.setType(type);
        return group;
    }

    private static Video createVideo(VideoGroup group) {
        Video video = new Video();
        video.videoId = "video";
        video.setGroup(group);
        return video;
    }
}
