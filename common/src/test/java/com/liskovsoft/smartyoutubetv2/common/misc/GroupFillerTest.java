package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SimpleMediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.KeywordFilterData;
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
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.schedulers.TestScheduler;
import io.reactivex.subjects.PublishSubject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GroupFillerTest {
    private static final int MIN_SIZE = 5;
    private static final BrowseSection HOME = new BrowseSection(MediaGroup.TYPE_HOME, "Home", BrowseSection.TYPE_ROW, 0);
    private final TestScheduler mScheduler = new TestScheduler();
    private final GroupFiller mFiller = new GroupFiller(mScheduler);
    private final List<VideoGroup> mContinued = new ArrayList<>();
    private KeywordFilterData mKeywordData;
    private int mVideoNum;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        mKeywordData = KeywordFilterData.instance(context);
        clearKeywords();
    }

    @After
    public void tearDown() {
        clearKeywords();
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void shortRowIsShownOnceItIsFilled() {
        PublishSubject<MediaGroup> page = PublishSubject.create(); // arrives when the test says
        VideoGroup row = createRow(2);
        List<List<VideoGroup>> shown = new ArrayList<>();

        mFiller.fill(Collections.singletonList(row), MIN_SIZE, group -> continueGroup(group, page)).subscribe(shown::add);

        // Not with its few cards
        assertTrue(shown.isEmpty());

        page.onNext(createPage(3));
        page.onComplete();

        assertEquals(1, shown.size());
        assertEquals(5, shown.get(0).get(0).getSize());
    }

    @Test
    public void rowGetsPagesUntilItIsFull() {
        VideoGroup row = createRow(2);
        List<List<VideoGroup>> shown = new ArrayList<>();

        mFiller.fill(Collections.singletonList(row), MIN_SIZE, group -> continueGroup(group, Observable.just(createPage(1)))).subscribe(shown::add);

        assertEquals(1, shown.size());
        assertEquals(5, row.getSize());
        assertEquals(3, mContinued.size());
    }

    @Test
    public void hiddenVideosAreReplaced() {
        mKeywordData.addKeyword("reaction");
        VideoGroup row = VideoGroup.from(createMediaGroup(createVideo("Reaction"), createVideo("Trailer"), createVideo("Reaction"),
                createVideo("Reaction"), createVideo("Review"), createVideo("Reaction")), HOME, 0);
        List<List<VideoGroup>> shown = new ArrayList<>();

        assertEquals(2, row.getSize());

        mFiller.fill(Collections.singletonList(row), MIN_SIZE,
                group -> continueGroup(group, Observable.just(createMediaGroup(createVideo("Reaction"), createVideo("Teaser"),
                        createVideo("Recap"), createVideo("Reaction"), createVideo("Interview"))))).subscribe(shown::add);

        assertEquals(1, shown.size());
        assertEquals(5, row.getSize());

        for (Video video : row.getVideos()) {
            assertFalse(video.title.contains("Reaction"));
        }
    }

    @Test
    public void fullRowIsShownAtOnce() {
        VideoGroup row = createRow(MIN_SIZE);
        List<List<VideoGroup>> shown = new ArrayList<>();

        mFiller.fill(Collections.singletonList(row), MIN_SIZE, group -> continueGroup(group, Observable.just(createPage(5)))).subscribe(shown::add);

        assertEquals(1, shown.size());
        assertEquals(MIN_SIZE, row.getSize());
        assertTrue(mContinued.isEmpty());
    }

    @Test
    public void rowAtTheEndOfItsFeedIsShownWithItsCards() {
        VideoGroup row = createRow(2);
        List<List<VideoGroup>> shown = new ArrayList<>();

        // As the continuation ends (see RxHelper.fromNullable)
        mFiller.fill(Collections.singletonList(row), MIN_SIZE,
                group -> continueGroup(group, Observable.error(new IllegalStateException("fromNullable result is null")))).subscribe(shown::add);

        assertEquals(1, shown.size());
        assertEquals(2, row.getSize());
    }

    @Test
    public void failedPageDoesNotHoldTheRowsBack() {
        VideoGroup row = createRow(2);
        List<List<VideoGroup>> shown = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();

        mFiller.fill(Collections.singletonList(row), MIN_SIZE, group -> continueGroup(group, Observable.error(new IllegalStateException("Unable to resolve host"))))
                .subscribe(shown::add, errors::add);

        assertEquals(1, shown.size());
        assertEquals(2, row.getSize());
        assertTrue(errors.isEmpty());
    }

    @Test
    public void slowPageHoldsTheRowsBackForALimitedTime() {
        PublishSubject<MediaGroup> page = PublishSubject.create(); // never arrives
        VideoGroup row = createRow(2);
        List<List<VideoGroup>> shown = new ArrayList<>();

        mFiller.fill(Collections.singletonList(row), MIN_SIZE, group -> continueGroup(group, page)).subscribe(shown::add);

        mScheduler.advanceTimeBy(GroupFiller.MAX_HOLD_MS - 1, TimeUnit.MILLISECONDS);

        assertTrue(shown.isEmpty());

        mScheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS);

        assertEquals(1, shown.size());
        assertEquals(2, row.getSize());
        assertFalse(page.hasObservers());
    }

    @Test
    public void pagesStopWhenHideContentHidesEveryVideo() {
        mKeywordData.addKeyword("reaction");
        VideoGroup row = createRow(2);
        List<List<VideoGroup>> shown = new ArrayList<>();

        mFiller.fill(Collections.singletonList(row), MIN_SIZE,
                group -> continueGroup(group, Observable.just(createMediaGroup(createVideo("Reaction"), createVideo("Reaction"))))).subscribe(shown::add);

        assertEquals(1, shown.size());
        assertEquals(2, row.getSize());
        assertEquals(GroupFiller.MAX_PAGES, mContinued.size());

        // Nor once it's shown
        assertFalse(mFiller.shouldContinue(row, MIN_SIZE));
    }

    @Test
    public void rowsAreFilledTogetherAndShownInTheirOrder() {
        PublishSubject<MediaGroup> firstPage = PublishSubject.create();
        PublishSubject<MediaGroup> secondPage = PublishSubject.create();
        VideoGroup first = createRow(2);
        VideoGroup full = createRow(MIN_SIZE);
        VideoGroup second = createRow(3);
        List<List<VideoGroup>> shown = new ArrayList<>();

        mFiller.fill(Arrays.asList(first, full, second), MIN_SIZE, group -> continueGroup(group, group == first ? firstPage : secondPage))
                .subscribe(shown::add);

        // The second doesn't wait for the first to ask for its page
        assertEquals(Arrays.asList(first, second), mContinued);
        assertTrue(firstPage.hasObservers());
        assertTrue(secondPage.hasObservers());

        secondPage.onNext(createPage(2));
        secondPage.onComplete();

        assertTrue(shown.isEmpty());

        firstPage.onNext(createPage(3));
        firstPage.onComplete();

        assertEquals(Collections.singletonList(Arrays.asList(first, full, second)), shown);
    }

    @Test
    public void cardsAreCountedOnce() {
        VideoGroup row = createRow(3);

        assertTrue(mFiller.shouldContinue(row, MIN_SIZE));

        // The group collects its pages: 4 cards, not 3 + 4
        VideoGroup.from(row, createPage(1));

        assertTrue(mFiller.shouldContinue(row, MIN_SIZE));

        VideoGroup.from(row, createPage(1));

        assertFalse(mFiller.shouldContinue(row, MIN_SIZE));
    }

    @Test
    public void pagesAreCountedForEachGroup() {
        VideoGroup row = createRow(2);
        VideoGroup sameIdRow = createRow(2);
        sameIdRow.setId(row.getId()); // e.g. the suggestions of the next video

        for (int i = 0; i < GroupFiller.MAX_PAGES; i++) {
            assertTrue(mFiller.shouldContinue(row, MIN_SIZE));
        }

        assertFalse(mFiller.shouldContinue(row, MIN_SIZE));
        assertTrue(mFiller.shouldContinue(sameIdRow, MIN_SIZE));
    }

    @Test
    public void groupWithoutContinuationIsNotContinued() {
        VideoGroup row = VideoGroup.from(Arrays.asList(createVideo("Trailer"), createVideo("Review")));

        assertFalse(mFiller.shouldContinue(row, MIN_SIZE));
        assertFalse(mFiller.shouldContinue(null, MIN_SIZE));
    }

    @Test
    public void noRowsAreShownAtOnce() {
        List<List<VideoGroup>> shown = new ArrayList<>();
        List<VideoGroup> rows = Collections.emptyList();

        mFiller.fill(rows, MIN_SIZE, group -> continueGroup(group, Observable.empty())).subscribe(shown::add);

        assertEquals(1, shown.size());
        assertSame(rows, shown.get(0));
    }

    private Observable<MediaGroup> continueGroup(VideoGroup group, Observable<MediaGroup> page) {
        mContinued.add(group);
        return page;
    }

    private void clearKeywords() {
        for (String keyword : mKeywordData.getKeywords()) {
            mKeywordData.removeKeyword(keyword);
        }
    }

    private VideoGroup createRow(int size) {
        return VideoGroup.from(createPage(size), HOME, 0);
    }

    private MediaGroup createPage(int size) {
        Video[] videos = new Video[size];

        for (int i = 0; i < size; i++) {
            videos[i] = createVideo("Video");
        }

        return createMediaGroup(videos);
    }

    private Video createVideo(String title) {
        Video video = new Video();
        video.videoId = "video" + mVideoNum++;
        video.title = title + " " + video.videoId;
        video.author = "Channel";
        return video;
    }

    private static MediaGroup createMediaGroup(Video... videos) {
        return new TestMediaGroup(videos);
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
    }
}
