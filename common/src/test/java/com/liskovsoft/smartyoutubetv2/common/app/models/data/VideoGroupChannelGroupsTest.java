package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup.ChannelGroupServiceWrapper;
import com.liskovsoft.smartyoutubetv2.common.prefs.ChannelGroupFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.HomeRowsData.Row;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The channels in the subscription groups, hidden from the areas picked for them (see ChannelGroupFilterData)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoGroupChannelGroupsTest {
    private static final BrowseSection HOME = createSection(MediaGroup.TYPE_HOME);
    private static final BrowseSection GAMING = createSection(MediaGroup.TYPE_GAMING);
    private static final BrowseSection SUBSCRIPTIONS = createSection(MediaGroup.TYPE_SUBSCRIPTIONS);
    private ChannelGroupServiceWrapper mService;
    private ChannelGroupFilterData mData;
    private final List<ItemGroup> mGroups = new ArrayList<>();
    private ItemGroup mClimbing;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        mService = ChannelGroupServiceWrapper.instance(context);
        mData = ChannelGroupFilterData.instance(context);
        mClimbing = createGroup("Climbing", "UCclimbing", "World Climbing", "UCifsc", "IFSC");
    }

    @After
    public void tearDown() {
        for (ItemGroup group : mGroups) {
            mService.removeChannelGroup(group);
        }

        // The defaults
        for (int area = 0; area <= ChannelGroupFilterData.AREA_HOME_SUBSCRIPTIONS_ROW; area++) {
            mData.setHidden(area, false);
        }

        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void nothingIsHiddenByDefault() {
        VideoGroup home = createRow(HOME, null, createVideo("UCclimbing", "World Climbing"));

        assertEquals(1, home.getSize());
    }

    @Test
    public void channelsAreHiddenOnlyFromThePickedSections() {
        mData.setHidden(MediaGroup.TYPE_HOME, true);

        VideoGroup home = createRow(HOME, null,
                createVideo("UCclimbing", "World Climbing"), createVideo("UCifsc", "IFSC"), createVideo("UCcooking", "Cooking"));
        VideoGroup gaming = createRow(GAMING, null, createVideo("UCclimbing", "World Climbing"));
        VideoGroup subscriptions = createRow(SUBSCRIPTIONS, null, createVideo("UCclimbing", "World Climbing"));

        assertEquals(Collections.singletonList("Cooking"), getAuthors(home));
        assertEquals(1, gaming.getSize());
        assertEquals(1, subscriptions.getSize());
    }

    @Test
    public void channelsAreHiddenFromTheSubscriptionsSection() {
        mData.setHidden(MediaGroup.TYPE_SUBSCRIPTIONS, true);

        VideoGroup subscriptions = createGrid(SUBSCRIPTIONS, createVideo("UCclimbing", "World Climbing"), createVideo("UCcooking", "Cooking"));
        VideoGroup home = createRow(HOME, null, createVideo("UCclimbing", "World Climbing"));

        assertEquals(Collections.singletonList("Cooking"), getAuthors(subscriptions));
        assertEquals(1, home.getSize());
    }

    /**
     * Most TV cards have only the channel name: the name the channel had when it was added to the group
     */
    @Test
    public void channelIsFoundByItsNameWhenTheCardHasNoId() {
        mData.setHidden(MediaGroup.TYPE_HOME, true);

        VideoGroup home = createRow(HOME, null, createVideo(null, "World Climbing"), createVideo(null, "world climbing"));

        assertEquals(Collections.singletonList("world climbing"), getAuthors(home));
    }

    /**
     * Another channel of the same name, or the channel after it was renamed
     */
    @Test
    public void idWinsOverTheName() {
        mData.setHidden(MediaGroup.TYPE_HOME, true);

        VideoGroup home = createRow(HOME, null, createVideo("UCimpostor", "World Climbing"), createVideo("UCclimbing", "Climbing TV"));

        assertEquals(Collections.singletonList("World Climbing"), getAuthors(home));
    }

    /**
     * The card has only "X and Y": hidden by either channel
     */
    @Test
    public void collaborationIsHiddenByEitherChannel() {
        mData.setHidden(MediaGroup.TYPE_HOME, true);

        VideoGroup home = createRow(HOME, null, createVideo(null, "Cooking and IFSC"), createVideo(null, "Cooking and Baking"));

        assertEquals(Collections.singletonList("Cooking and Baking"), getAuthors(home));
    }

    /**
     * The Subscriptions row added to Home is an area of its own: Home is YouTube's rows
     */
    @Test
    public void subscriptionsRowOfHomeIsAnAreaOfItsOwn() {
        mData.setHidden(ChannelGroupFilterData.AREA_HOME_SUBSCRIPTIONS_ROW, true);

        VideoGroup subscriptionsRow = createRow(HOME, Row.subscriptions(), createVideo("UCclimbing", "World Climbing"), createVideo("UCcooking", "Cooking"));
        VideoGroup youTubeRow = createRow(HOME, null, createVideo("UCclimbing", "World Climbing"));

        assertEquals(Collections.singletonList("Cooking"), getAuthors(subscriptionsRow));
        assertEquals(1, youTubeRow.getSize());

        mData.setHidden(ChannelGroupFilterData.AREA_HOME_SUBSCRIPTIONS_ROW, false);
        mData.setHidden(MediaGroup.TYPE_HOME, true);

        assertEquals(1, createRow(HOME, Row.subscriptions(), createVideo("UCclimbing", "World Climbing")).getSize());
    }

    /**
     * Added to Home on purpose: the row of a group would hide its own channels
     */
    @Test
    public void otherRowsAddedToHomeKeepTheirVideos() {
        mData.setHidden(MediaGroup.TYPE_HOME, true);
        mData.setHidden(ChannelGroupFilterData.AREA_HOME_SUBSCRIPTIONS_ROW, true);

        VideoGroup groupRow = createRow(HOME, Row.channelGroup(mClimbing.getId(), "Climbing"), createVideo("UCclimbing", "World Climbing"));
        VideoGroup channelRow = createRow(HOME, Row.channel("UCifsc", "IFSC"), createVideo("UCifsc", "IFSC"));

        assertEquals(1, groupRow.getSize());
        assertEquals(1, channelRow.getSize());
    }

    @Test
    public void pagesAddedLaterAreFilteredToo() {
        mData.setHidden(ChannelGroupFilterData.AREA_HOME_SUBSCRIPTIONS_ROW, true);

        VideoGroup subscriptionsRow = createRow(HOME, Row.subscriptions(), createVideo("UCcooking", "Cooking"));
        VideoGroup.from(subscriptionsRow, new TestMediaGroup(createVideo("UCclimbing", "World Climbing"), createVideo("UCbaking", "Baking")));

        assertEquals(Arrays.asList("Cooking", "Baking"), getAuthors(subscriptionsRow));
    }

    /**
     * Read again after the group is changed (see ChannelGroupServiceWrapper.addChannelGroup)
     */
    @Test
    public void channelAddedToTheGroupLaterIsHidden() {
        mData.setHidden(MediaGroup.TYPE_HOME, true);

        assertEquals(1, createRow(HOME, null, createVideo("UCbouldering", "Bouldering")).getSize());

        mClimbing.add(mService.createChannel("Bouldering", null, "UCbouldering"));
        mService.addChannelGroup(mClimbing);

        assertEquals(0, createRow(HOME, null, createVideo("UCbouldering", "Bouldering")).getSize());
        assertEquals(0, createRow(HOME, null, createVideo(null, "Bouldering")).getSize());

        mClimbing.remove("UCbouldering");
        mService.addChannelGroup(mClimbing);

        assertEquals(1, createRow(HOME, null, createVideo("UCbouldering", "Bouldering")).getSize());
    }

    @Test
    public void channelsOfEveryGroupAreHidden() {
        createGroup("Cooking", "UCcooking", "Cooking");
        mData.setHidden(MediaGroup.TYPE_HOME, true);

        VideoGroup home = createRow(HOME, null,
                createVideo("UCclimbing", "World Climbing"), createVideo("UCcooking", "Cooking"), createVideo("UCbaking", "Baking"));

        assertEquals(Collections.singletonList("Baking"), getAuthors(home));
    }

    /**
     * Read again after a group is gone (see ChannelGroupServiceWrapper.removeChannelGroup): the areas stay, for the other groups
     */
    @Test
    public void channelsOfARemovedGroupAreShownAgain() {
        createGroup("Cooking", "UCcooking", "Cooking");
        mData.setHidden(MediaGroup.TYPE_HOME, true);

        assertEquals(0, createRow(HOME, null, createVideo("UCclimbing", "World Climbing")).getSize());

        mService.removeChannelGroup(mClimbing);

        assertTrue(mData.isHidden(MediaGroup.TYPE_HOME));
        assertNull(mService.findChannelGroupById(mClimbing.getId()));
        assertEquals(1, createRow(HOME, null, createVideo("UCclimbing", "World Climbing")).getSize());
        assertEquals(0, createRow(HOME, null, createVideo("UCcooking", "Cooking")).getSize());
    }

    /**
     * Only videos: a channel card (e.g. a row of channels) and a playlist are left alone
     */
    @Test
    public void cardsWithoutAVideoAreLeftAlone() {
        mData.setHidden(MediaGroup.TYPE_HOME, true);

        Video channel = createVideo("UCclimbing", "World Climbing");
        channel.videoId = null;
        channel.itemType = MediaItem.TYPE_CHANNEL;

        VideoGroup home = createRow(HOME, null, channel);

        assertEquals(1, home.getSize());
        assertEquals("UCclimbing", home.get(0).channelId);
    }

    /**
     * A pinned item (its section has a hash id), the player's suggestions and search aren't areas
     */
    @Test
    public void groupsOutOfTheSectionsKeepTheirVideos() {
        for (int area = 0; area <= ChannelGroupFilterData.AREA_HOME_SUBSCRIPTIONS_ROW; area++) {
            mData.setHidden(area, true);
        }

        VideoGroup pinned = createGrid(createSection("Climbing".hashCode()), createVideo("UCclimbing", "World Climbing"));
        VideoGroup suggestions = VideoGroup.from(new ArrayList<>());
        suggestions.setType(MediaGroup.TYPE_SUGGESTIONS);
        suggestions.add(createVideo("UCclimbing", "World Climbing"));
        VideoGroup search = VideoGroup.from(new ArrayList<>());
        search.setType(MediaGroup.TYPE_SEARCH);
        search.add(createVideo("UCclimbing", "World Climbing"));

        assertEquals(1, pinned.getSize());
        assertEquals(1, suggestions.getSize());
        assertEquals(1, search.getSize());
    }

    private ItemGroup createGroup(String title, String... channels) {
        List<ItemGroup.Item> items = new ArrayList<>();

        for (int i = 0; i < channels.length; i += 2) {
            items.add(mService.createChannel(channels[i + 1], null, channels[i]));
        }

        ItemGroup group = mService.createChannelGroup(title, null, items);
        mService.addChannelGroup(group);
        mGroups.add(group);

        return group;
    }

    private static VideoGroup createRow(BrowseSection section, Row addedHomeRow, Video... videos) {
        return VideoGroup.from(new TestMediaGroup(videos), section, 0, null, addedHomeRow);
    }

    private static VideoGroup createGrid(BrowseSection section, Video... videos) {
        VideoGroup group = VideoGroup.from(section);

        for (Video video : videos) {
            group.add(video);
        }

        return group;
    }

    private static BrowseSection createSection(int id) {
        return new BrowseSection(id, "Section", BrowseSection.TYPE_ROW, 0);
    }

    private static List<String> getAuthors(VideoGroup group) {
        List<String> result = new ArrayList<>();

        for (Video video : group.getVideos()) {
            result.add(video.author);
        }

        return result;
    }

    private static int sVideoCount;

    private static Video createVideo(String channelId, String author) {
        sVideoCount++;
        Video video = new Video();
        video.itemType = MediaItem.TYPE_VIDEO;
        video.videoId = "video" + sVideoCount;
        video.title = "Video " + sVideoCount;
        video.channelId = channelId;
        video.author = author;
        return video;
    }

    private static final class TestMediaGroup implements MediaGroup {
        private final List<MediaItem> mItems = new ArrayList<>();

        TestMediaGroup(Video... videos) {
            for (Video video : videos) {
                mItems.add(SimpleMediaItem.from(video));
            }
        }

        @Override
        public int getType() {
            return MediaGroup.TYPE_HOME;
        }

        @Override
        public List<MediaItem> getMediaItems() {
            return mItems;
        }

        @Override
        public String getTitle() {
            return "Row";
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
            return mItems.isEmpty();
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
