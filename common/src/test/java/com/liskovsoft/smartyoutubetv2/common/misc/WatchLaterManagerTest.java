package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLaterData;
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
import io.reactivex.subjects.PublishSubject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class WatchLaterManagerTest {
    private static final String ACCOUNT = "one@example.com";
    private static final String OTHER_ACCOUNT = "two@example.com";
    private static final long MINUTE_MS = 60 * 1_000;
    // What each account's Watch later holds
    private final Map<String, List<String>> mWatchLater = new HashMap<>();
    private final List<String> mLoads = new ArrayList<>(); // the account of each load
    private WatchLaterManager mManager;
    private WatchLaterData mData;
    private String mAccount = ACCOUNT;
    private boolean mIsNetworkDown;
    private PublishSubject<List<String>> mPendingLoad; // the next loads end when the test says

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        mData = WatchLaterData.instance(context);
        mData.setMode(WatchLaterData.MODE_SHOW);
        mData.setSectionEnabled(MediaGroup.TYPE_HOME, true);
        mData.setSectionEnabled(MediaGroup.TYPE_SUBSCRIPTIONS, true);
        mData.setSectionEnabled(MediaGroup.TYPE_GAMING, false);
        mWatchLater.put(ACCOUNT, Arrays.asList("later1", "later2"));
        mWatchLater.put(OTHER_ACCOUNT, Collections.singletonList("other1"));
        mManager = WatchLaterManager.instance(context);
        mManager.resetForTesting(this::load, () -> mAccount);
    }

    @After
    public void tearDown() {
        mData.setMode(WatchLaterData.MODE_SHOW);
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void listIsReadOnce() {
        assertTrue(resolve());
        assertTrue(mManager.contains("later1"));
        assertTrue(mManager.contains("later2"));
        assertFalse(mManager.contains("other1"));

        // Known now
        assertTrue(resolve());
        assertEquals(Collections.singletonList(ACCOUNT), mLoads);
    }

    @Test
    public void signedOutHasNoWatchLater() {
        mAccount = null;

        assertTrue(resolve());

        assertTrue(mLoads.isEmpty());
        assertFalse(mManager.contains("later1"));
    }

    @Test
    public void runningLoadIsShared() {
        PublishSubject<List<String>> load = PublishSubject.create();
        mPendingLoad = load;

        List<Boolean> done = new ArrayList<>();
        mManager.resolve().subscribe(done::add);
        mManager.resolve().subscribe(done::add);

        // Held while it's read
        assertTrue(done.isEmpty());

        load.onNext(mWatchLater.get(ACCOUNT));
        load.onComplete();

        assertEquals(Arrays.asList(true, true), done);
        assertEquals(1, mLoads.size());
        assertTrue(mManager.contains("later1"));
    }

    @Test
    public void failedLoadShowsTheCardsAndIsNotRepeatedAtOnce() {
        mIsNetworkDown = true;

        // Completes all the same: the cards are shown
        assertTrue(resolve());
        assertTrue(resolve());

        assertEquals(1, mLoads.size());
        assertFalse(mManager.contains("later1"));
    }

    @Test
    public void oldListIsUsedWhileItsReadAgain() {
        assertTrue(resolve());
        mManager.setLoadedTimeForTesting(16 * MINUTE_MS);
        PublishSubject<List<String>> refresh = PublishSubject.create();
        mPendingLoad = refresh;

        // Not held by the refresh
        assertTrue(resolve());
        assertTrue(mManager.contains("later1"));
        assertEquals(2, mLoads.size());

        refresh.onNext(Collections.singletonList("later3"));
        refresh.onComplete();

        assertTrue(mManager.contains("later3"));
        assertFalse(mManager.contains("later1"));
    }

    @Test
    public void freshListIsNotReadAgain() {
        assertTrue(resolve());
        mManager.setLoadedTimeForTesting(14 * MINUTE_MS);

        assertTrue(resolve());

        assertEquals(1, mLoads.size());
    }

    @Test
    public void eachAccountHasItsList() {
        assertTrue(resolve());

        mAccount = OTHER_ACCOUNT;

        // The first account's list isn't the second's
        assertFalse(mManager.contains("later1"));

        assertTrue(resolve());

        assertTrue(mManager.contains("other1"));
        assertFalse(mManager.contains("later1"));
        assertEquals(Arrays.asList(ACCOUNT, OTHER_ACCOUNT), mLoads);
    }

    @Test
    public void listOfAnAccountLeftWhileItLoadedIsDropped() {
        PublishSubject<List<String>> load = PublishSubject.create();
        mPendingLoad = load;
        mManager.resolve().subscribe();

        mAccount = OTHER_ACCOUNT;
        load.onNext(mWatchLater.get(ACCOUNT));
        load.onComplete();

        mAccount = ACCOUNT;
        assertFalse(mManager.contains("later1"));
    }

    @Test
    public void editsInTheAppCountAtOnce() {
        assertTrue(resolve());

        edit(WatchLaterManager.WATCH_LATER_PLAYLIST_ID, "new", true);
        edit(WatchLaterManager.WATCH_LATER_PLAYLIST_ID, "later1", false);
        // Another playlist
        edit("PLother", "later2", false);

        assertTrue(mManager.contains("new"));
        assertFalse(mManager.contains("later1"));
        assertTrue(mManager.contains("later2"));
        assertEquals(1, mLoads.size());
    }

    @Test
    public void editsAreReportedForTheCards() {
        List<String> reported = new ArrayList<>();
        mManager.setOnEdited((video, isAdded) -> reported.add(video.videoId + (isAdded ? "+" : "-")));

        edit(WatchLaterManager.WATCH_LATER_PLAYLIST_ID, "new", true);
        edit(WatchLaterManager.WATCH_LATER_PLAYLIST_ID, "later1", false);
        edit("PLother", "later2", true);

        mManager.setOnEdited(null);
        assertEquals(Arrays.asList("new+", "later1-"), reported);
    }

    @Test
    public void editsDuringALoadAreKept() {
        PublishSubject<List<String>> load = PublishSubject.create();
        mPendingLoad = load;
        mManager.resolve().subscribe();

        // Saved after the list was read on the server
        edit(WatchLaterManager.WATCH_LATER_PLAYLIST_ID, "new", true);
        edit(WatchLaterManager.WATCH_LATER_PLAYLIST_ID, "later1", false);

        load.onNext(mWatchLater.get(ACCOUNT));
        load.onComplete();

        assertTrue(mManager.contains("new"));
        assertFalse(mManager.contains("later1"));
        assertTrue(mManager.contains("later2"));
    }

    @Test
    public void menusKnowItOnlyWhileTheSettingIsOn() {
        assertTrue(resolve());

        // The list isn't kept fresh while the setting is off
        assertFalse(mManager.isInWatchLater("later1"));

        mData.setMode(WatchLaterData.MODE_MARK);

        assertTrue(mManager.isInWatchLater("later1"));
        assertFalse(mManager.isInWatchLater("other1"));
    }

    @Test
    public void videoIsHiddenOnlyInPickedSections() {
        assertTrue(resolve());
        mData.setMode(WatchLaterData.MODE_HIDE);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createVideo("later1"));
        home.add(createVideo("fresh"));

        VideoGroup gaming = createGroup(MediaGroup.TYPE_GAMING);
        gaming.add(createVideo("later1"));

        assertEquals(1, home.getSize());
        assertEquals("fresh", home.get(0).videoId);
        assertEquals(1, gaming.getSize());
        assertFalse(gaming.get(0).isInWatchLater);
    }

    @Test
    public void videoIsMarkedInMarkMode() {
        assertTrue(resolve());
        mData.setMode(WatchLaterData.MODE_MARK);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createVideo("later1"));
        home.add(createVideo("fresh"));

        assertEquals(2, home.getSize());
        assertTrue(home.get(0).isInWatchLater);
        assertFalse(home.get(1).isInWatchLater);
    }

    @Test
    public void showModeLeavesThemAlone() {
        assertTrue(resolve());

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createVideo("later1"));

        assertEquals(1, home.getSize());
        assertFalse(home.get(0).isInWatchLater);
    }

    @Test
    public void watchLaterPlaylistItselfIsLeftAlone() {
        assertTrue(resolve());
        mData.setMode(WatchLaterData.MODE_HIDE);

        // e.g. the Watch later row of Home: its cards play in the playlist
        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        Video inPlaylist = createVideo("later1");
        inPlaylist.playlistId = WatchLaterManager.WATCH_LATER_PLAYLIST_ID;
        home.add(inPlaylist);

        assertEquals(1, home.getSize());
        assertFalse(home.get(0).isInWatchLater);
        Video inOtherPlaylist = createVideo("later1");
        inOtherPlaylist.playlistId = "PLother";
        assertFalse(WatchLaterManager.isWatchLaterPlaylist(home, inOtherPlaylist));
    }

    /**
     * The list is read before the group is created (see HiddenVideoResolverTest)
     */
    @Test
    public void groupOnlyReadsTheList() {
        mData.setMode(WatchLaterData.MODE_HIDE);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createVideo("later1"));

        assertEquals(1, home.getSize());
        assertTrue(mLoads.isEmpty());
    }

    private Observable<List<String>> load() {
        mLoads.add(mAccount);

        if (mPendingLoad != null) {
            return mPendingLoad;
        }

        if (mIsNetworkDown) {
            return Observable.error(new IllegalStateException("No network"));
        }

        return Observable.just(mWatchLater.get(mAccount));
    }

    /**
     * @return the list is known or can't be read
     */
    private boolean resolve() {
        List<Boolean> done = new ArrayList<>();
        mManager.resolve().subscribe(done::add);
        return done.equals(Collections.singletonList(true));
    }

    private static void edit(String playlistId, String videoId, boolean isAdded) {
        WatchLaterManager.onPlaylistEdited(RuntimeEnvironment.getApplication(), playlistId, createVideo(videoId), isAdded);
    }

    private static VideoGroup createGroup(int type) {
        return VideoGroup.from(new BrowseSection(type, "Section", BrowseSection.TYPE_GRID, 0));
    }

    private static Video createVideo(String videoId) {
        Video video = new Video();
        video.videoId = videoId;
        video.title = "Video " + videoId;
        video.author = "Channel";
        video.secondTitle = "Channel • 5K views • 3 weeks ago";
        return video;
    }
}
