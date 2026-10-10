package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Child mode keeps to the subscribed channels (#6310)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ChildModeManagerTest {
    private static final String ACCOUNT = "one@example.com";
    private static final String OTHER_ACCOUNT = "two@example.com";
    private static final String SUBSCRIBED = "UCsubscribed";
    private static final String NOT_SUBSCRIBED = "UCnotsubscribed";
    private static final long MINUTE_MS = 60 * 1_000;
    // What each account is subscribed to
    private final Map<String, List<String>> mSubscriptions = new HashMap<>();
    private final List<String> mLoads = new ArrayList<>(); // the account of each load
    private Context mContext;
    private ChildModeManager mManager;
    private GeneralData mGeneralData;
    private String mAccount = ACCOUNT;
    private boolean mIsNetworkDown;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(mContext);
        mGeneralData = GeneralData.instance(mContext);
        mGeneralData.setChildModeEnabled(true);
        mSubscriptions.put(ACCOUNT, Collections.singletonList(SUBSCRIBED));
        mSubscriptions.put(OTHER_ACCOUNT, Collections.singletonList(NOT_SUBSCRIBED));
        mManager = ChildModeManager.instance(mContext);
        mManager.resetForTesting(this::load, () -> mAccount);
    }

    @After
    public void tearDown() {
        mGeneralData.setChildModeEnabled(false);
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void everyChannelOpensWithoutChildMode() {
        mGeneralData.setChildModeEnabled(false);

        assertTrue(opens(NOT_SUBSCRIBED));
        assertFalse(mManager.isHidden(NOT_SUBSCRIBED));
        assertTrue(mLoads.isEmpty());
    }

    @Test
    public void onlySubscribedChannelsOpen() {
        assertTrue(opens(SUBSCRIBED));
        assertFalse(opens(NOT_SUBSCRIBED));

        assertFalse(mManager.isHidden(SUBSCRIBED));
        assertTrue(mManager.isHidden(NOT_SUBSCRIBED));
    }

    /**
     * A handle from a link can't be told to be one of them
     */
    @Test
    public void handleDoesNotOpen() {
        assertFalse(opens("@subscribed"));
    }

    @Test
    public void playlistOpenedAsChannelIsNotChecked() {
        assertTrue(opens("VLPLplaylist"));
        assertTrue(opens(null));
        assertTrue(mLoads.isEmpty());
    }

    @Test
    public void subscriptionsAreReadAgainOnceOld() {
        assertTrue(opens(SUBSCRIBED));
        assertTrue(opens(SUBSCRIBED));
        assertEquals(1, mLoads.size());

        mManager.setLoadedTimeForTesting(5 * MINUTE_MS);

        assertTrue(opens(SUBSCRIBED));
        assertEquals(2, mLoads.size());
    }

    /**
     * It might have just been subscribed to (e.g. on the phone), but a channel tried again and again isn't read every time
     */
    @Test
    public void channelSubscribedToMeanwhileOpensAfterRetryPeriod() {
        assertFalse(opens(NOT_SUBSCRIBED));
        mSubscriptions.put(ACCOUNT, Arrays.asList(SUBSCRIBED, NOT_SUBSCRIBED));

        assertFalse(opens(NOT_SUBSCRIBED));
        assertEquals(1, mLoads.size());

        mManager.setLoadedTimeForTesting(MINUTE_MS / 2);

        assertTrue(opens(NOT_SUBSCRIBED));
        assertEquals(2, mLoads.size());
    }

    @Test
    public void channelUnsubscribedFromMeanwhileDoesNotOpenOnceOld() {
        assertTrue(opens(SUBSCRIBED));
        mSubscriptions.put(ACCOUNT, Collections.emptyList());
        mManager.setLoadedTimeForTesting(5 * MINUTE_MS);

        assertFalse(opens(SUBSCRIBED));
    }

    @Test
    public void nothingOpensWhenSubscriptionsCantBeRead() {
        mIsNetworkDown = true;

        assertFalse(opens(SUBSCRIBED));
        assertTrue(mManager.isHidden(SUBSCRIBED));
    }

    @Test
    public void knownSubscriptionsStayWhenTheyCantBeReadAgain() {
        assertTrue(opens(SUBSCRIBED));
        mIsNetworkDown = true;
        mManager.setLoadedTimeForTesting(5 * MINUTE_MS);

        assertTrue(opens(SUBSCRIBED));
        assertFalse(opens(NOT_SUBSCRIBED));
        assertEquals(3, mLoads.size());
    }

    @Test
    public void subscriptionsAreTheSelectedAccounts() {
        assertTrue(opens(SUBSCRIBED));
        mAccount = OTHER_ACCOUNT;

        assertTrue(mManager.isHidden(NOT_SUBSCRIBED));
        assertTrue(opens(NOT_SUBSCRIBED));
        assertFalse(opens(SUBSCRIBED));
        assertEquals(Arrays.asList(ACCOUNT, OTHER_ACCOUNT), mLoads);
    }

    @Test
    public void signedOutUsesTheChannelsOnTheDevice() {
        mAccount = null;
        mSubscriptions.put(null, Collections.singletonList(SUBSCRIBED));

        assertTrue(opens(SUBSCRIBED));
        assertFalse(opens(NOT_SUBSCRIBED));
    }

    @Test
    public void unsubscribedChannelCardsAreHiddenOnChannelPage() {
        assertTrue(opens(SUBSCRIBED));

        VideoGroup channelPage = createGroup(MediaGroup.TYPE_CHANNEL);
        channelPage.add(createChannel(SUBSCRIBED));
        channelPage.add(createChannel(NOT_SUBSCRIBED));
        channelPage.add(createVideo());

        assertEquals(2, channelPage.getSize());
        assertEquals(SUBSCRIBED, channelPage.get(0).channelId);
        assertEquals("video1", channelPage.get(1).videoId);
    }

    /**
     * The channel cards of a featured channels shelf only: the rest of the page stays as it is
     */
    @Test
    public void featuredChannelsRowIsEmptied() {
        VideoGroup featured = createGroup(MediaGroup.TYPE_CHANNEL);
        featured.add(createChannel(NOT_SUBSCRIBED));
        featured.add(createChannel("UCother"));

        assertTrue(featured.isEmpty());
    }

    @Test
    public void channelCardsStayInOtherSections() {
        VideoGroup channels = createGroup(MediaGroup.TYPE_CHANNEL_UPLOADS);
        channels.add(createChannel(NOT_SUBSCRIBED));

        assertEquals(1, channels.getSize());
    }

    @Test
    public void channelCardsStayWithoutChildMode() {
        mGeneralData.setChildModeEnabled(false);

        VideoGroup channelPage = createGroup(MediaGroup.TYPE_CHANNEL);
        channelPage.add(createChannel(NOT_SUBSCRIBED));

        assertEquals(1, channelPage.getSize());
    }

    @Test
    public void playlistCardsStayOnChannelPage() {
        Video playlist = new Video();
        playlist.itemType = MediaItem.TYPE_PLAYLIST;
        playlist.playlistId = "PLplaylist";
        playlist.channelId = NOT_SUBSCRIBED;
        playlist.title = "Playlist";

        VideoGroup channelPage = createGroup(MediaGroup.TYPE_CHANNEL);
        channelPage.add(playlist);

        assertEquals(1, channelPage.getSize());
    }

    private Observable<List<String>> load() {
        mLoads.add(mAccount);

        if (mIsNetworkDown) {
            return Observable.error(new IllegalStateException("No network"));
        }

        return Observable.just(mSubscriptions.get(mAccount));
    }

    /**
     * @return the channel opened (the loads end at once here)
     */
    private boolean opens(String channelId) {
        boolean[] isOpened = new boolean[1];
        mManager.checkChannel(mContext, channelId, () -> isOpened[0] = true);
        return isOpened[0];
    }

    private static VideoGroup createGroup(int type) {
        return VideoGroup.from(new BrowseSection(type, "Section", BrowseSection.TYPE_ROW, 0));
    }

    /**
     * Like the tiles of a featured channels shelf: a channel id, no video id
     */
    private static Video createChannel(String channelId) {
        Video video = new Video();
        video.itemType = MediaItem.TYPE_CHANNEL;
        video.channelId = channelId;
        video.title = "Channel " + channelId;
        return video;
    }

    private static Video createVideo() {
        Video video = new Video();
        video.itemType = MediaItem.TYPE_VIDEO;
        video.videoId = "video1";
        video.channelId = NOT_SUBSCRIBED;
        video.title = "Video";
        video.author = "Channel";
        video.secondTitle = "Channel • 5K views • 3 weeks ago";
        return video;
    }
}
