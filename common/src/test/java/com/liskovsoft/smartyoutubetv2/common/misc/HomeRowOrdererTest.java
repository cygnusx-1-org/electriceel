package com.liskovsoft.smartyoutubetv2.common.misc;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.HomeRowsData.Row;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import io.reactivex.subjects.PublishSubject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The rows of Home come in the order of HomeRowsData, and a row let through is never moved by one that comes later
 */
public class HomeRowOrdererTest {
    private static final Row SUBSCRIPTIONS = Row.subscriptions();
    private static final Row CHANNEL = Row.channel("UCchannel", "Channel");
    private static final Set<Integer> NONE_HIDDEN = Collections.emptySet();

    @Test
    public void youTubeRowsComeInYouTubesOrderAfterTheAddedOnes() {
        HomeRowOrderer orderer = new HomeRowOrderer(NONE_HIDDEN, Arrays.asList(SUBSCRIPTIONS, Row.youTube()));
        MediaGroup recommended = row("Recommended", MediaGroup.ROW_KIND_RECOMMENDED);
        MediaGroup topic = row("AI and more");
        MediaGroup subscriptions = row("Subscriptions");

        // Held until the added row above them is there
        assertTitles(orderer.addYouTubeRows(Arrays.asList(recommended, topic)));
        assertTitles(orderer.addRow(SUBSCRIPTIONS, subscriptions), "Subscriptions", "Recommended", "AI and more");
        assertTitles(orderer.addYouTubeRows(Collections.singletonList(row("New to you"))), "New to you");
        assertTitles(orderer.completeYouTube());
        assertTrue(orderer.isComplete());
    }

    @Test
    public void everyLoadRowsGoToTheirPlaces() {
        HomeRowOrderer orderer = new HomeRowOrderer(NONE_HIDDEN, Arrays.asList(
                Row.kind(MediaGroup.ROW_KIND_RECENTLY_UPLOADED), Row.kind(MediaGroup.ROW_KIND_RECOMMENDED), Row.youTube()));

        assertTitles(orderer.addYouTubeRows(Arrays.asList(
                        row("Recommended", MediaGroup.ROW_KIND_RECOMMENDED),
                        row("Recently uploaded", MediaGroup.ROW_KIND_RECENTLY_UPLOADED),
                        row("AI and more"))),
                "Recently uploaded", "Recommended", "AI and more");
    }

    /**
     * Watch later comes on a later page: the rows after it in the order wait for it
     */
    @Test
    public void rowsWaitForARowAboveThemOfALaterPage() {
        HomeRowOrderer orderer = new HomeRowOrderer(NONE_HIDDEN, Arrays.asList(
                Row.kind(MediaGroup.ROW_KIND_RECOMMENDED), Row.kind(MediaGroup.ROW_KIND_WATCH_LATER), Row.youTube()));

        assertTitles(orderer.addYouTubeRows(Arrays.asList(row("Recommended", MediaGroup.ROW_KIND_RECOMMENDED), row("AI and more"))),
                "Recommended");
        assertTitles(orderer.addYouTubeRows(Arrays.asList(row("Watch later", MediaGroup.ROW_KIND_WATCH_LATER), row("New to you"))),
                "Watch later", "AI and more", "New to you");
    }

    /**
     * YouTube didn't send Watch later this time: the rows after it come once YouTube's done
     */
    @Test
    public void rowsAfterAMissingRowComeWhenYouTubeIsDone() {
        HomeRowOrderer orderer = new HomeRowOrderer(NONE_HIDDEN, Arrays.asList(
                Row.kind(MediaGroup.ROW_KIND_RECOMMENDED), Row.kind(MediaGroup.ROW_KIND_WATCH_LATER), Row.youTube(), SUBSCRIPTIONS));

        assertTitles(orderer.addRow(SUBSCRIPTIONS, row("Subscriptions")));
        assertTitles(orderer.addYouTubeRows(Arrays.asList(row("Recommended", MediaGroup.ROW_KIND_RECOMMENDED), row("AI and more"))),
                "Recommended");
        assertFalse(orderer.isComplete());
        assertTitles(orderer.completeYouTube(), "AI and more", "Subscriptions");
        assertTrue(orderer.isComplete());
    }

    /**
     * Only the first one has the place of the kind: the next one is one of YouTube's rows, in YouTube's order
     */
    @Test
    public void secondRowOfAKindGoesWithYouTubesRows() {
        HomeRowOrderer orderer = new HomeRowOrderer(NONE_HIDDEN, Arrays.asList(Row.youTube(), Row.kind(MediaGroup.ROW_KIND_SHOWS)));

        assertTitles(orderer.addYouTubeRows(Arrays.asList(
                row("Recommended shows", MediaGroup.ROW_KIND_SHOWS), row("AI and more"), row("Podcasts", MediaGroup.ROW_KIND_SHOWS))),
                "AI and more", "Podcasts");
        assertTitles(orderer.completeYouTube(), "Recommended shows");
    }

    /**
     * Shorts can come more than once: each one goes to its place, the rows after it wait until YouTube's done
     */
    @Test
    public void everyShortsRowGoesToItsPlace() {
        HomeRowOrderer orderer = new HomeRowOrderer(NONE_HIDDEN, Arrays.asList(Row.kind(MediaGroup.ROW_KIND_SHORTS), Row.youTube()));

        assertTitles(orderer.addYouTubeRows(Arrays.asList(row("Shorts", MediaGroup.ROW_KIND_SHORTS), row("AI and more"))), "Shorts");
        assertTitles(orderer.addYouTubeRows(Collections.singletonList(row("More shorts", MediaGroup.ROW_KIND_SHORTS))), "More shorts");
        assertTitles(orderer.completeYouTube(), "AI and more");
    }

    /**
     * Taken out of Home (see HomeRowsData.isYouTubeRowShown): Shorts, and the rows of no kind
     */
    @Test
    public void hiddenRowsAreLeftOut() {
        Set<Integer> hidden = new java.util.HashSet<>(Arrays.asList(MediaGroup.ROW_KIND_SHORTS, MediaGroup.ROW_KIND_NONE));
        HomeRowOrderer orderer = new HomeRowOrderer(hidden, Collections.singletonList(Row.youTube()));

        assertTitles(orderer.addYouTubeRows(Arrays.asList(row("Recommended", MediaGroup.ROW_KIND_RECOMMENDED), row("Shorts", MediaGroup.ROW_KIND_SHORTS),
                row("AI and more"), row("Recommended shows", MediaGroup.ROW_KIND_SHOWS))), "Recommended", "Recommended shows");
        assertTitles(orderer.completeYouTube());
    }

    @Test
    public void addedRowWithoutVideosHoldsNothing() {
        HomeRowOrderer orderer = new HomeRowOrderer(NONE_HIDDEN, Arrays.asList(CHANNEL, Row.youTube()));

        assertTitles(orderer.addYouTubeRows(Collections.singletonList(row("Recommended", MediaGroup.ROW_KIND_RECOMMENDED))));
        assertTitles(orderer.addRow(CHANNEL, null), "Recommended");
        // Once only
        assertTitles(orderer.addRow(CHANNEL, row("Channel")));
    }

    @Test
    public void rowsOfAnAddedRowNotInTheOrderAreLeftOut() {
        HomeRowOrderer orderer = new HomeRowOrderer(NONE_HIDDEN, Collections.singletonList(Row.youTube()));

        assertTitles(orderer.addRow(CHANNEL, row("Channel")));
        assertTitles(orderer.addYouTubeRows(Collections.singletonList(row("Recommended"))), "Recommended");
    }

    @Test
    public void emptyPagesAndMissingRowsAreSkipped() {
        HomeRowOrderer orderer = new HomeRowOrderer(NONE_HIDDEN, Collections.singletonList(Row.youTube()));

        assertTitles(orderer.addYouTubeRows(null));
        assertTitles(orderer.addYouTubeRows(Arrays.asList(null, row("Recommended"))), "Recommended");
    }

    /**
     * Without YouTube's rows in the order, they're after the others
     */
    @Test
    public void youTubeRowsAreLastWhenNotInTheOrder() {
        HomeRowOrderer orderer = new HomeRowOrderer(NONE_HIDDEN, Collections.singletonList(SUBSCRIPTIONS));

        assertTitles(orderer.addYouTubeRows(Collections.singletonList(row("Recommended"))));
        assertTitles(orderer.addRow(SUBSCRIPTIONS, row("Subscriptions")), "Subscriptions", "Recommended");
    }

    @Test
    public void orderedAsTheyComeFromEachSource() {
        PublishSubject<List<MediaGroup>> youTube = PublishSubject.create();
        PublishSubject<MediaGroup> subscriptions = PublishSubject.create();
        TestObserver<List<MediaGroup>> observer = HomeRowOrderer.order(NONE_HIDDEN, Arrays.asList(SUBSCRIPTIONS, Row.youTube(), CHANNEL), youTube,
                row -> row.equals(SUBSCRIPTIONS) ? subscriptions : Observable.just(row("Channel"))).test();

        youTube.onNext(Collections.singletonList(row("Recommended")));
        observer.assertNoValues();

        subscriptions.onNext(row("Subscriptions"));
        youTube.onNext(Collections.singletonList(row("AI and more")));
        youTube.onComplete();

        observer.assertComplete();
        assertEquals(Arrays.asList(Arrays.asList("Subscriptions", "Recommended"), Collections.singletonList("AI and more"),
                Collections.singletonList("Channel")), titles(observer.values()));
    }

    /**
     * A channel that's gone, a row that's not there or Subscriptions signed out: the rows after them come
     */
    @Test
    public void addedRowsThatFailAreLeftOut() {
        Row empty = Row.channel("UCempty", "Empty");
        Row missing = Row.channel("UCmissing", "Missing");
        TestObserver<List<MediaGroup>> observer = HomeRowOrderer.order(NONE_HIDDEN, Arrays.asList(SUBSCRIPTIONS, CHANNEL, empty, missing, Row.youTube()),
                Observable.just(Collections.singletonList(row("Recommended"))),
                row -> row.equals(SUBSCRIPTIONS) ? Observable.error(new IllegalStateException("Signed out")) :
                        row.equals(empty) ? Observable.empty() : row.equals(missing) ? null : Observable.just(row("Channel"))).test();

        observer.assertComplete();
        assertEquals(Collections.singletonList("Channel"), titles(observer.values()).get(0));
        assertEquals(Arrays.asList("Channel", "Recommended"), flatTitles(observer.values()));
    }

    /**
     * YouTube's rows failed: the added rows still come, then the error
     */
    @Test
    public void youTubeErrorComesAfterTheAddedRows() {
        Exception error = new IllegalStateException("No Home");
        Observable<List<MediaGroup>> youTube = Observable.just(Collections.singletonList(row("Recommended")))
                .concatWith(Observable.error(error));
        PublishSubject<MediaGroup> subscriptions = PublishSubject.create();
        TestObserver<List<MediaGroup>> observer = HomeRowOrderer.order(NONE_HIDDEN, Arrays.asList(Row.youTube(), SUBSCRIPTIONS), youTube,
                row -> subscriptions).test();

        observer.assertNoErrors();
        assertEquals(Collections.singletonList("Recommended"), flatTitles(observer.values()));

        subscriptions.onNext(row("Subscriptions"));

        observer.assertError(error);
        assertEquals(Arrays.asList("Recommended", "Subscriptions"), flatTitles(observer.values()));
    }

    private static void assertTitles(List<MediaGroup> rows, String... titles) {
        assertEquals(Arrays.asList(titles), titles(rows));
    }

    private static List<String> titles(List<MediaGroup> rows) {
        List<String> result = new ArrayList<>();

        for (MediaGroup row : rows) {
            result.add(row.getTitle());
        }

        return result;
    }

    private static List<List<String>> titles(Iterable<List<MediaGroup>> emissions) {
        List<List<String>> result = new ArrayList<>();

        for (List<MediaGroup> rows : emissions) {
            result.add(titles(rows));
        }

        return result;
    }

    private static List<String> flatTitles(Iterable<List<MediaGroup>> emissions) {
        List<String> result = new ArrayList<>();

        for (List<MediaGroup> rows : emissions) {
            result.addAll(titles(rows));
        }

        return result;
    }

    private static MediaGroup row(String title) {
        return row(title, MediaGroup.ROW_KIND_NONE);
    }

    private static MediaGroup row(String title, int rowKind) {
        return new TestMediaGroup(title, rowKind);
    }

    private static final class TestMediaGroup implements MediaGroup {
        private final String mTitle;
        private final int mRowKind;

        TestMediaGroup(String title, int rowKind) {
            mTitle = title;
            mRowKind = rowKind;
        }

        @Override
        public int getType() {
            return TYPE_HOME;
        }

        @Nullable
        @Override
        public List<MediaItem> getMediaItems() {
            return Collections.emptyList();
        }

        @Override
        public String getTitle() {
            return mTitle;
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
            return false;
        }

        @Override
        public int getTopic() {
            return TOPIC_NONE;
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
            return mRowKind;
        }

        @Override
        public int getFilteredVideoCount() {
            return 0;
        }
    }
}
