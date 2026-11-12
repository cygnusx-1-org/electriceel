package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
                MediaGroup.TYPE_SUGGESTIONS, MediaGroup.TYPE_KIDS_HOME, MediaGroup.TYPE_CHANNEL,
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

    @Test
    public void playlistIdFoundFromItsOwn() {
        Video video = new Video();
        video.videoId = "video";
        video.playlistId = "PLplaylist";
        video.channelId = "UCchannel";

        assertEquals("PLplaylist", video.findPlaylistId());
    }

    /**
     * A card of the Playlists section is the playlist opened as a channel
     */
    @Test
    public void playlistIdFoundFromPlaylistOpenedAsChannel() {
        Video video = new Video();
        video.channelId = "VLPLplaylist";

        assertEquals("PLplaylist", video.findPlaylistId());
    }

    @Test
    public void noPlaylistIdForChannel() {
        Video channel = new Video();
        channel.channelId = "UCchannel";
        Video prefixOnly = new Video();
        prefixOnly.channelId = "VL";

        assertNull(channel.findPlaylistId());
        assertNull(prefixOnly.findPlaylistId());
        assertNull(new Video().findPlaylistId());
    }

    /**
     * The playability reason is the title of an unplayable video: the metadata that comes after (see SuggestionsController) keeps it
     */
    @Test
    public void unplayableVideoKeepsItsReasonAsTitle() {
        Video video = createVideo(createGroup(MediaGroup.TYPE_HOME));
        video.title = "Video";

        video.sync(formatInfo(true, "Video unavailable"));
        video.sync(metadata("Title of the video"));

        assertEquals("Video unavailable", video.getTitleFull());
    }

    @Test
    public void playableVideoTakesTheTitleOfItsMetadata() {
        Video video = createVideo(createGroup(MediaGroup.TYPE_HOME));
        video.title = "Video";

        video.sync(formatInfo(false, null));
        video.sync(metadata("Title of the video"));

        assertEquals("Title of the video", video.getTitleFull());
    }

    /**
     * Unplayable without a reason: nothing to keep
     */
    @Test
    public void unplayableVideoWithoutReasonTakesTheTitleOfItsMetadata() {
        Video video = createVideo(createGroup(MediaGroup.TYPE_HOME));
        video.title = "Video";

        video.sync(formatInfo(true, null));
        video.sync(metadata("Title of the video"));

        assertEquals("Title of the video", video.getTitleFull());
    }

    private static MediaItemFormatInfo formatInfo(boolean isUnplayable, String reason) {
        Map<String, Object> values = new HashMap<>();
        values.put("isUnplayable", isUnplayable);
        values.put("getPlayabilityReason", reason);

        return stub(MediaItemFormatInfo.class, values);
    }

    private static MediaItemMetadata metadata(String title) {
        Map<String, Object> values = new HashMap<>();
        values.put("getTitle", title);

        return stub(MediaItemMetadata.class, values);
    }

    /**
     * By method name; the defaults (false, 0, null) for the others
     */
    private static <T> T stub(Class<T> type, Map<String, Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (values.containsKey(method.getName())) {
                return values.get(method.getName());
            }

            Class<?> returned = method.getReturnType();

            if (returned == boolean.class) {
                return false;
            } else if (returned == int.class) {
                return 0;
            } else if (returned == long.class) {
                return 0L;
            } else if (returned == float.class) {
                return 0f;
            }

            return null;
        }));
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
