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
import com.liskovsoft.smartyoutubetv2.common.prefs.StreamedData;
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

/**
 * The live streams that ended among the History cards, which come without the date (see StreamedManager)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StreamedManagerTest {
    private static final String HISTORY_INFO = "World Climbing • 161K views"; // TV History gives no date
    private final Context mContext = RuntimeEnvironment.getApplication();
    private final List<List<String>> mLookups = new ArrayList<>();
    private final Map<String, Boolean> mStreams = new HashMap<>();
    private StreamedData mData;
    private int mCollaborationsMode;
    private int mWatchLaterMode;
    private StreamedManager mManager;

    @Before
    public void setUp() {
        GlobalPreferences.instance(mContext);
        mData = StreamedData.instance(mContext);
        mData.setMode(StreamedData.MODE_MARK);
        mData.setSectionEnabled(MediaGroup.TYPE_HISTORY, true);
        mData.setSectionEnabled(MediaGroup.TYPE_SUBSCRIPTIONS, true);
        disableOtherLookups();
        mManager = StreamedManager.instance(mContext);
        mManager.resetForTesting(this::lookup);
        deleteVideosFile();
        mStreams.put("stream", true);
        mStreams.put("video", false);
    }

    @After
    public void tearDown() {
        // The defaults
        mData.setMode(StreamedData.MODE_MARK);
        mData.setSectionEnabled(MediaGroup.TYPE_HISTORY, true);
        CollaborationsData.instance(mContext).setMode(mCollaborationsMode);
        WatchLaterData.instance(mContext).setMode(mWatchLaterMode);
        deleteVideosFile();
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void historyStreamNeverAppearsUnmarked() {
        PublishSubject<Map<String, Boolean>> lookup = PublishSubject.create(); // ends when the test says
        mManager.resetForTesting(videoIds -> lookup);
        BrowseSection history = createSection(MediaGroup.TYPE_HISTORY);
        List<MediaGroup> shown = new ArrayList<>();

        HiddenVideoResolver.resolveGroup(mContext, Observable.just(createGrid(createVideo("stream"), createVideo("video"))), history)
                .subscribe(shown::add);

        // Held while it's looked up
        assertTrue(shown.isEmpty());

        lookup.onNext(mStreams);
        lookup.onComplete();

        assertEquals(1, shown.size());
        VideoGroup group = VideoGroup.from(shown.get(0), history);
        assertEquals(2, group.getSize());
        assertTrue(group.get(0).isStreamedMarked);
        assertFalse(group.get(1).isStreamedMarked);
    }

    /**
     * Nothing is hidden from History: it's marked there instead
     */
    @Test
    public void historyStreamIsMarkedInHideMode() {
        mData.setMode(StreamedData.MODE_HIDE);
        MediaGroup grid = createGrid(createVideo("stream"));

        resolve(grid, MediaGroup.TYPE_HISTORY);
        VideoGroup group = VideoGroup.from(grid, createSection(MediaGroup.TYPE_HISTORY));

        assertEquals(1, group.getSize());
        assertTrue(group.get(0).isStreamedMarked);
    }

    /**
     * The other sections have the date on the cards
     */
    @Test
    public void onlyHistoryIsLookedUp() {
        resolve(createGrid(createVideo("stream")), MediaGroup.TYPE_SUBSCRIPTIONS);
        resolve(createGrid(createVideo("stream")), MediaGroup.TYPE_HOME);

        assertTrue(mLookups.isEmpty());
    }

    @Test
    public void nothingIsLookedUpWhileTheSettingIsOffInHistory() {
        mData.setSectionEnabled(MediaGroup.TYPE_HISTORY, false);
        MediaGroup grid = createGrid(createVideo("stream"));
        resolve(grid, MediaGroup.TYPE_HISTORY);

        mData.setSectionEnabled(MediaGroup.TYPE_HISTORY, true);
        mData.setMode(StreamedData.MODE_SHOW);
        resolve(grid, MediaGroup.TYPE_HISTORY);

        assertTrue(mLookups.isEmpty());
        assertFalse(VideoGroup.from(grid, createSection(MediaGroup.TYPE_HISTORY)).get(0).isStreamedMarked);
    }

    /**
     * A live or upcoming one isn't marked (see VideoGroup), a short is never one
     */
    @Test
    public void onlyVideosThatCanBeStreamsAreLookedUp() {
        Video live = createCard("live");
        live.isLive = true;
        Video upcoming = createCard("upcoming");
        upcoming.isUpcoming = true;

        resolve(createGrid(createVideo("stream"), SimpleMediaItem.from(live), SimpleMediaItem.from(upcoming), createShort("short")),
                MediaGroup.TYPE_HISTORY);

        assertEquals(Collections.singletonList(Collections.singletonList("stream")), mLookups);
    }

    @Test
    public void videoIsLookedUpOnce() {
        resolve(createGrid(createVideo("stream"), createVideo("video")), MediaGroup.TYPE_HISTORY);
        resolve(createGrid(createVideo("stream"), createVideo("video"), createVideo("new")), MediaGroup.TYPE_HISTORY);

        assertEquals(Arrays.asList(Arrays.asList("stream", "video"), Collections.singletonList("new")), mLookups);
    }

    /**
     * Not known while it's live or to come: it's tried again with the next cards
     */
    @Test
    public void videoLeftOutIsTriedAgain() {
        resolve(createGrid(createVideo("stream"), createVideo("unknown")), MediaGroup.TYPE_HISTORY);
        resolve(createGrid(createVideo("stream"), createVideo("unknown")), MediaGroup.TYPE_HISTORY);

        assertEquals(Arrays.asList(Arrays.asList("stream", "unknown"), Collections.singletonList("unknown")), mLookups);
    }

    @Test
    public void failedLookupShowsTheCardsAndIsTriedAgain() {
        mManager.resetForTesting(videoIds -> {
            mLookups.add(videoIds);
            return Observable.error(new IllegalStateException("No network"));
        });
        MediaGroup grid = createGrid(createVideo("stream"));

        List<MediaGroup> shown = resolve(grid, MediaGroup.TYPE_HISTORY);
        resolve(grid, MediaGroup.TYPE_HISTORY);

        assertEquals(1, shown.size());
        assertFalse(VideoGroup.from(grid, createSection(MediaGroup.TYPE_HISTORY)).get(0).isStreamedMarked);
        assertEquals(2, mLookups.size());
    }

    @Test
    public void videosAreKeptOnDisk() {
        resolve(createGrid(createVideo("stream"), createVideo("video")), MediaGroup.TYPE_HISTORY);

        mManager.saveVideosForTesting();
        mManager.restoreVideosForTesting();
        ShadowLooper.shadowMainLooper().idle();
        mLookups.clear();

        MediaGroup grid = createGrid(createVideo("stream"), createVideo("video"));
        resolve(grid, MediaGroup.TYPE_HISTORY);
        VideoGroup group = VideoGroup.from(grid, createSection(MediaGroup.TYPE_HISTORY));

        assertTrue(mLookups.isEmpty());
        assertTrue(group.get(0).isStreamedMarked);
        assertFalse(group.get(1).isStreamedMarked);
    }

    @Test
    public void brokenLinesOnDiskAreSkipped() {
        FileHelpers.stringToFile("stream\t1\nbroken\n\t1\nempty\t\nother\t2\n", mManager.getVideosFileForTesting());

        mManager.restoreVideosForTesting();
        ShadowLooper.shadowMainLooper().idle();
        resolve(createGrid(createVideo("stream"), createVideo("broken"), createVideo("empty"), createVideo("other")), MediaGroup.TYPE_HISTORY);

        assertEquals(Collections.singletonList(Arrays.asList("broken", "empty", "other")), mLookups);
    }

    private Observable<Map<String, Boolean>> lookup(List<String> videoIds) {
        mLookups.add(videoIds);
        Map<String, Boolean> result = new HashMap<>();

        for (String videoId : videoIds) {
            if (mStreams.containsKey(videoId)) {
                result.put(videoId, mStreams.get(videoId));
            }
        }

        return Observable.just(result);
    }

    private List<MediaGroup> resolve(MediaGroup grid, int sectionType) {
        List<MediaGroup> shown = new ArrayList<>();
        HiddenVideoResolver.resolveGroup(mContext, Observable.just(grid), createSection(sectionType)).subscribe(shown::add);
        return shown;
    }

    /**
     * Nothing else holds the cards (see HiddenVideoResolver)
     */
    private void disableOtherLookups() {
        CollaborationsData collaborationsData = CollaborationsData.instance(mContext);
        mCollaborationsMode = collaborationsData.getMode();
        collaborationsData.setMode(CollaborationsData.MODE_SHOW);
        WatchLaterData watchLaterData = WatchLaterData.instance(mContext);
        mWatchLaterMode = watchLaterData.getMode();
        watchLaterData.setMode(WatchLaterData.MODE_SHOW);
        AiSListFilterData aiSListData = AiSListFilterData.instance(mContext);

        for (int list : new int[] {AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.LIST_WARNLIST}) {
            aiSListData.setEverythingEnabled(list, false);

            for (int section = 0; section < AiSListFilterData.SECTION_COUNT; section++) {
                aiSListData.setHideEnabled(list, section, false);
            }
        }
    }

    private void deleteVideosFile() {
        File file = mManager.getVideosFileForTesting();

        if (file.exists()) {
            file.delete();
        }
    }

    private static BrowseSection createSection(int type) {
        return new BrowseSection(type, "Section", BrowseSection.TYPE_GRID, 0);
    }

    private static MediaGroup createGrid(MediaItem... items) {
        return new TestMediaGroup(Arrays.asList(items));
    }

    private static MediaItem createVideo(String videoId) {
        return SimpleMediaItem.from(createCard(videoId));
    }

    /**
     * Only what the cards read, the rest is empty
     */
    private static MediaItem createShort(String videoId) {
        Map<String, Object> values = new HashMap<>();
        values.put("getVideoId", videoId);
        values.put("getTitle", "Short " + videoId);
        values.put("getSecondTitle", HISTORY_INFO);
        values.put("isShorts", true);

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

    private static Video createCard(String videoId) {
        Video video = new Video();
        video.videoId = videoId;
        video.title = "Video " + videoId;
        video.secondTitle = HISTORY_INFO;
        return video;
    }

    private static final class TestMediaGroup implements MediaGroup {
        private final List<MediaItem> mItems;

        TestMediaGroup(List<MediaItem> items) {
            mItems = items;
        }

        @Override
        public int getType() {
            return MediaGroup.TYPE_HISTORY;
        }

        @Override
        public List<MediaItem> getMediaItems() {
            return mItems;
        }

        @Override
        public String getTitle() {
            return "History";
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
