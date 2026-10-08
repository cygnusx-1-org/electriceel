package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SimpleMediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.AiSListFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.CollaborationsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.OldVideosData;
import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLaterData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.lang.reflect.Proxy;
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
public class ShortsDateManagerTest {
    private static final String SHELF_INFO = "Some Channel • @somechannel"; // a TV shelf of shorts gives the channel and the handle
    private static final long HOUR_MS = 60 * 60 * 1_000;
    private static final long DAY_MS = 24 * HOUR_MS;
    private final Context mContext = RuntimeEnvironment.getApplication();
    private final List<List<String>> mLookups = new ArrayList<>();
    private final Map<String, Long> mDates = new HashMap<>();
    private MainUIData mMainUIData;
    private OldVideosData mOldVideosData;
    private ShortsDateManager mManager;

    @Before
    public void setUp() {
        GlobalPreferences.instance(mContext);
        mMainUIData = MainUIData.instance(mContext);
        mMainUIData.setShortsDateEnabled(true);
        mOldVideosData = OldVideosData.instance(mContext);
        mOldVideosData.setEnabled(false);
        mManager = ShortsDateManager.instance(mContext);
        mManager.resetForTesting(this::lookup);
        deleteDatesFile();
    }

    @After
    public void tearDown() {
        mMainUIData.setShortsDateEnabled(false);
        mOldVideosData.setEnabled(false);
        deleteDatesFile();
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void ageIsShownLikeOnTheOtherCards() {
        assertEquals("30 seconds ago", getDateText(30 * 1_000));
        assertEquals("1 minute ago", getDateText(60 * 1_000));
        assertEquals("59 minutes ago", getDateText(HOUR_MS - 1));
        assertEquals("1 hour ago", getDateText(HOUR_MS));
        assertEquals("23 hours ago", getDateText(DAY_MS - 1));
        assertEquals("1 day ago", getDateText(DAY_MS));
        assertEquals("6 days ago", getDateText(7 * DAY_MS - 1));
        assertEquals("1 week ago", getDateText(7 * DAY_MS));
        assertEquals("4 weeks ago", getDateText(30 * DAY_MS - 1));
        assertEquals("1 month ago", getDateText(30 * DAY_MS));
        assertEquals("12 months ago", getDateText(365 * DAY_MS - 1));
        assertEquals("1 year ago", getDateText(365 * DAY_MS));
        assertEquals("2 years ago", getDateText(800 * DAY_MS));
    }

    /**
     * A date from the future (e.g. a clock behind) is now
     */
    @Test
    public void dateAfterNowIsNow() {
        assertEquals("0 seconds ago", getDateText(-DAY_MS));
    }

    /**
     * Rounded down like YouTube's: Hide old videos reads the age at most as old as it is
     */
    @Test
    public void hideOldVideosReadsTheAge() {
        for (int days = 0; days <= 1_000; days++) {
            int ageDays = OldVideoFilter.getAgeDays(getDateText(days * DAY_MS));

            assertTrue("Age of " + days + " days: " + ageDays, ageDays >= 0 && ageDays <= days);
        }

        assertTrue(OldVideoFilter.isOlderThan(getDateText(30 * DAY_MS), 1));
        assertFalse(OldVideoFilter.isOlderThan(getDateText(30 * DAY_MS - 1), 1));
        assertTrue(OldVideoFilter.isOlderThan(getDateText(365 * DAY_MS), 12));
    }

    @Test
    public void dateGoesBeforeTheHandle() {
        assertEquals("Some Channel • 3 days ago • @somechannel", ShortsDateManager.addDate(SHELF_INFO, "3 days ago").toString());
        assertEquals("3 days ago • @somechannel", ShortsDateManager.addDate("@somechannel", "3 days ago").toString());
        // The web shelf gives the views
        assertEquals("1.2M views • 3 days ago", ShortsDateManager.addDate("1.2M views", "3 days ago").toString());
        assertEquals("3 days ago", ShortsDateManager.addDate(null, "3 days ago").toString());
        assertEquals("3 days ago", ShortsDateManager.addDate("", "3 days ago").toString());
    }

    /**
     * A channel name in Hebrew: the whole text is wrapped in bidi marks (see ServiceHelper.createInfo)
     */
    @Test
    public void dateGoesBeforeTheWrappedHandle() {
        String info = "‫ערוץ • @channel‬‎";

        assertEquals("‫ערוץ • 3 days ago • @channel‬‎", ShortsDateManager.addDate(info, "3 days ago").toString());
    }

    @Test
    public void shortNeverAppearsWithoutItsDate() {
        disableOtherLookups();
        PublishSubject<Map<String, Long>> lookup = PublishSubject.create(); // ends when the test says
        mManager.resetForTesting(videoIds -> lookup);
        List<MediaGroup> shown = new ArrayList<>();

        HiddenVideoResolver.resolveGroup(mContext, Observable.just(createRow(createShort("short"))), createSection(MediaGroup.TYPE_SUBSCRIPTIONS))
                .subscribe(shown::add);

        // Held while the date is looked up
        assertTrue(shown.isEmpty());

        lookup.onNext(Collections.singletonMap("short", daysAgo(3)));
        lookup.onComplete();

        assertEquals(1, shown.size());
        assertEquals("Some Channel • 3 days ago • @somechannel", getInfo(shown.get(0), MediaGroup.TYPE_SUBSCRIPTIONS));
    }

    /**
     * A short of a playlist has its date, a video has it too
     */
    @Test
    public void onlyShortsWithoutTheDateAreLookedUp() {
        mDates.put("short", daysAgo(3));

        resolve(createRow(createShort("short"), createShortWithDate("playlistShort"), createVideo("video")));

        assertEquals(Collections.singletonList(Collections.singletonList("short")), mLookups);
    }

    @Test
    public void dateIsLookedUpOnce() {
        mDates.put("short", daysAgo(3));
        mDates.put("private", 0L); // the video has none

        resolve(createRow(createShort("short"), createShort("private")));
        resolve(createRow(createShort("short"), createShort("private"), createShort("new")));

        assertEquals(Arrays.asList(Arrays.asList("short", "private"), Collections.singletonList("new")), mLookups);

        MediaGroup row = createRow(createShort("short"), createShort("private"));
        VideoGroup group = VideoGroup.from(row, createSection(MediaGroup.TYPE_HOME));

        assertEquals("Some Channel • 3 days ago • @somechannel", group.get(0).getSecondTitle().toString());
        assertEquals(SHELF_INFO, group.get(1).getSecondTitle().toString());
    }

    @Test
    public void cardsKeepTheirTextWhileOff() {
        mDates.put("short", daysAgo(3));
        resolve(createRow(createShort("short")));
        mLookups.clear();

        mMainUIData.setShortsDateEnabled(false);
        MediaGroup row = createRow(createShort("short"), createShort("other"));
        resolve(row);

        assertTrue(mLookups.isEmpty());
        assertEquals(SHELF_INFO, getInfo(row, MediaGroup.TYPE_HOME));
    }

    @Test
    public void failedLookupShowsTheCardsAndIsTriedAgain() {
        mManager.resetForTesting(videoIds -> {
            mLookups.add(videoIds);
            return Observable.error(new IllegalStateException("No network"));
        });
        MediaGroup row = createRow(createShort("short"));

        List<MediaGroup> shown = resolve(row);
        resolve(row);

        assertEquals(1, shown.size());
        assertEquals(SHELF_INFO, getInfo(row, MediaGroup.TYPE_HOME));
        assertEquals(2, mLookups.size());
    }

    /**
     * A group continued with the videos of another one adds them again
     */
    @Test
    public void dateIsAddedOnce() {
        mDates.put("short", daysAgo(3));
        MediaGroup row = createRow(createShort("short"));
        resolve(row);
        BrowseSection home = createSection(MediaGroup.TYPE_HOME);

        VideoGroup first = VideoGroup.from(row, home);
        VideoGroup merged = VideoGroup.from(VideoGroup.from(home), first);
        VideoGroup copied = VideoGroup.from(home);
        copied.add(Video.from(merged.get(0)));

        assertEquals("Some Channel • 3 days ago • @somechannel", merged.get(0).getSecondTitle().toString());
        assertEquals("Some Channel • 3 days ago • @somechannel", copied.get(0).getSecondTitle().toString());
    }

    @Test
    public void oldShortIsHiddenByHideOldVideos() {
        mOldVideosData.setPeriodMonths(12);
        mOldVideosData.setSectionEnabled(MediaGroup.TYPE_HOME, true);
        mOldVideosData.setEnabled(true);
        mDates.put("old", daysAgo(800));
        mDates.put("new", daysAgo(3));
        MediaGroup row = createRow(createShort("old"), createShort("new"));
        resolve(row);

        VideoGroup group = VideoGroup.from(row, createSection(MediaGroup.TYPE_HOME));

        assertEquals(1, group.getSize());
        assertEquals("new", group.get(0).videoId);

        // Without the date nothing tells its age
        mMainUIData.setShortsDateEnabled(false);

        assertEquals(2, VideoGroup.from(row, createSection(MediaGroup.TYPE_HOME)).getSize());
    }

    @Test
    public void datesAreKeptOnDisk() {
        mDates.put("short", daysAgo(3));
        mDates.put("private", 0L);
        resolve(createRow(createShort("short"), createShort("private")));

        mManager.saveDatesForTesting();
        mManager.restoreDatesForTesting();
        ShadowLooper.shadowMainLooper().idle();
        mLookups.clear();

        MediaGroup row = createRow(createShort("short"), createShort("private"));
        resolve(row);

        // The one without a date is tried again next session
        assertEquals(Collections.singletonList(Collections.singletonList("private")), mLookups);
        assertEquals("Some Channel • 3 days ago • @somechannel", getInfo(row, MediaGroup.TYPE_HOME));
    }

    @Test
    public void brokenLinesOnDiskAreSkipped() {
        FileHelpers.stringToFile("old\t" + daysAgo(3) + "\nbroken\n\t123\nempty\t\nnodate\t0\n", mManager.getDatesFileForTesting());

        mManager.restoreDatesForTesting();
        ShadowLooper.shadowMainLooper().idle();
        resolve(createRow(createShort("old"), createShort("broken"), createShort("empty"), createShort("nodate")));

        assertEquals(Collections.singletonList(Arrays.asList("broken", "empty", "nodate")), mLookups);
    }

    private Observable<Map<String, Long>> lookup(List<String> videoIds) {
        mLookups.add(videoIds);
        Map<String, Long> result = new HashMap<>();

        for (String videoId : videoIds) {
            if (mDates.containsKey(videoId)) {
                result.put(videoId, mDates.get(videoId));
            }
        }

        return Observable.just(result);
    }

    private List<MediaGroup> resolve(MediaGroup row) {
        List<MediaGroup> shown = new ArrayList<>();
        mManager.resolve(Collections.singletonList(row)).subscribe(unused -> shown.add(row));
        return shown;
    }

    private String getInfo(MediaGroup row, int sectionType) {
        return VideoGroup.from(row, createSection(sectionType)).get(0).getSecondTitle().toString();
    }

    private String getDateText(long ageMs) {
        long now = System.currentTimeMillis();
        return ShortsDateManager.getDateText(mContext, now - ageMs, now);
    }

    private static long daysAgo(int days) {
        // An hour more: the card text is made a moment later
        return System.currentTimeMillis() - days * DAY_MS - HOUR_MS;
    }

    /**
     * Nothing else holds the cards (see HiddenVideoResolver)
     */
    private void disableOtherLookups() {
        CollaborationsData.instance(mContext).setMode(CollaborationsData.MODE_SHOW);
        WatchLaterData.instance(mContext).setMode(WatchLaterData.MODE_SHOW);
        AiSListFilterData aiSListData = AiSListFilterData.instance(mContext);

        for (int list : new int[] {AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.LIST_WARNLIST}) {
            aiSListData.setEverythingEnabled(list, false);

            for (int section = 0; section < AiSListFilterData.SECTION_COUNT; section++) {
                aiSListData.setHideEnabled(list, section, false);
            }
        }
    }

    private void deleteDatesFile() {
        File file = mManager.getDatesFileForTesting();

        if (file.exists()) {
            file.delete();
        }
    }

    private static BrowseSection createSection(int type) {
        return new BrowseSection(type, "Section", BrowseSection.TYPE_GRID, 0);
    }

    private static MediaGroup createRow(MediaItem... items) {
        return new TestMediaGroup(Arrays.asList(items));
    }

    /**
     * As a shelf of shorts gives it: no date
     */
    private static MediaItem createShort(String videoId) {
        return createItem(videoId, SHELF_INFO, true, true);
    }

    /**
     * As a playlist gives it: the views and the date in the lines
     */
    private static MediaItem createShortWithDate(String videoId) {
        return createItem(videoId, "Some Channel • 5K views • 3 weeks ago", true, false);
    }

    private static MediaItem createVideo(String videoId) {
        Video video = new Video();
        video.videoId = videoId;
        video.title = "Video " + videoId;
        video.secondTitle = "Some Channel • 5K views • 3 weeks ago";
        return SimpleMediaItem.from(video);
    }

    /**
     * Only what the cards read, the rest is empty
     */
    private static MediaItem createItem(String videoId, String info, boolean isShorts, boolean isDateMissing) {
        Map<String, Object> values = new HashMap<>();
        values.put("getVideoId", videoId);
        values.put("getTitle", "Short " + videoId);
        values.put("getSecondTitle", info);
        values.put("isShorts", isShorts);
        values.put("isDateMissing", isDateMissing);

        return (MediaItem) Proxy.newProxyInstance(MediaItem.class.getClassLoader(), new Class<?>[] {MediaItem.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "Short " + videoId;
            }

            if (values.containsKey(method.getName())) {
                return values.get(method.getName());
            }

            Class<?> type = method.getReturnType();

            if (type == boolean.class) {
                return false;
            } else if (type == int.class) {
                return -1;
            } else if (type == long.class) {
                return -1L;
            } else if (type == double.class) {
                return 0.0;
            }

            return null;
        });
    }

    private static final class TestMediaGroup implements MediaGroup {
        private final List<MediaItem> mItems;

        TestMediaGroup(List<MediaItem> items) {
            mItems = items;
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
            return "Shorts";
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
        public int getFilteredVideoCount() {
            return 0;
        }
    }
}
