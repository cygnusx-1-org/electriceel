package com.liskovsoft.smartyoutubetv2.common.misc;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.HomeRowsData.Row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.reactivex.Observable;

/**
 * Puts the rows of Home in the order of HomeRowsData as they come: YouTube's, page after page, and the added ones, each on its own.<br/>
 * A row is let through once the rows before it in the order are. One shown is never moved down by a row that comes later.
 * A row that's first in the order waits for nothing, one after a row of a later page of YouTube waits for that page.
 */
public class HomeRowOrderer {
    private final List<Place> mPlaces = new ArrayList<>();
    private final Map<Integer, Place> mPlacesByKind = new HashMap<>();
    private final Map<Row, Place> mPlacesByRow = new HashMap<>();
    private final Place mYouTubePlace;
    private final Set<Integer> mHiddenKinds;
    private int mCurrent; // the first place not let through whole

    /**
     * Where the rows of an entry of the order go
     */
    private static final class Place {
        private final List<MediaGroup> mRows = new ArrayList<>();
        private final boolean mIsSingle;
        private int mReleased;
        private boolean mIsClosed;

        private Place(boolean isSingle) {
            mIsSingle = isSingle;
        }

        private void add(MediaGroup row) {
            mRows.add(row);

            // The next one of the kind goes with the rest of YouTube's rows
            if (mIsSingle) {
                mIsClosed = true;
            }
        }
    }

    /**
     * The rows of a row added to Home
     */
    public interface AddedRowSource {
        /**
         * The row, nothing or an error when there's none (e.g. the channel's gone)
         */
        Observable<MediaGroup> getRow(Row row);
    }

    private interface Step {
        List<MediaGroup> run();
    }

    /**
     * @param hiddenKinds YouTube's rows taken out of Home, by their kind (see HomeRowsData.isYouTubeRowShown)
     * @param order the rows of HomeRowsData, it has YouTube's (Row.youTube)
     */
    public HomeRowOrderer(Set<Integer> hiddenKinds, List<Row> order) {
        mHiddenKinds = hiddenKinds;

        Place youTubePlace = null;

        for (Row row : order) {
            // There can be more than one Shorts row
            Place place = new Place(row.type == Row.TYPE_KIND && row.kind != MediaGroup.ROW_KIND_SHORTS);
            mPlaces.add(place);

            if (row.type == Row.TYPE_YOUTUBE) {
                youTubePlace = place;
            } else if (row.type == Row.TYPE_KIND) {
                // Taken out of Home, nothing comes to it (see addYouTubeRows): the rows after it don't wait for YouTube's last page
                place.mIsClosed = hiddenKinds.contains(row.kind);
                mPlacesByKind.put(row.kind, place);
            } else {
                mPlacesByRow.put(row, place);
            }
        }

        if (youTubePlace == null) {
            youTubePlace = new Place(false);
            mPlaces.add(youTubePlace);
        }

        mYouTubePlace = youTubePlace;
    }

    /**
     * YouTube's rows of Home and the added ones in the order. An error of YouTube's comes after the added rows.
     */
    public static Observable<List<MediaGroup>> order(Set<Integer> hiddenKinds, List<Row> order, Observable<List<MediaGroup>> youTubeRows,
                                                     AddedRowSource source) {
        HomeRowOrderer orderer = new HomeRowOrderer(hiddenKinds, order);
        List<Observable<Step>> steps = new ArrayList<>();

        Step completeYouTube = orderer::completeYouTube;
        steps.add(youTubeRows
                .map(rows -> (Step) () -> orderer.addYouTubeRows(rows))
                .onErrorResumeNext((Throwable error) -> Observable.just(completeYouTube).concatWith(Observable.error(error)))
                .concatWith(Observable.just(completeYouTube)));

        for (Row row : order) {
            if (!row.isAdded()) {
                continue;
            }

            Step noRow = () -> orderer.addRow(row, null);
            Observable<MediaGroup> rowSource = source.getRow(row);
            steps.add(rowSource == null ? Observable.just(noRow) : rowSource
                    .take(1)
                    .map(group -> (Step) () -> orderer.addRow(row, group))
                    .onErrorReturnItem(noRow)
                    .defaultIfEmpty(noRow));
        }

        // The steps after the merge: one at a time, in the order they come in
        return Observable.mergeDelayError(steps)
                .map(Step::run)
                .filter(rows -> !rows.isEmpty());
    }

    /**
     * A page of YouTube's rows. Each one that's on every load goes to its place, the rest to YouTube's.
     *
     * @return the rows let through
     */
    public List<MediaGroup> addYouTubeRows(List<MediaGroup> rows) {
        if (rows != null) {
            for (MediaGroup row : rows) {
                if (row == null) {
                    continue;
                }

                int kind = row.getRowKind();

                if (mHiddenKinds.contains(kind)) {
                    continue;
                }

                Place place = mPlacesByKind.get(kind);
                (place != null && !place.mIsClosed ? place : mYouTubePlace).add(row);
            }
        }

        return release();
    }

    /**
     * YouTube has no more rows: the ones it didn't send this time won't come
     *
     * @return the rows let through
     */
    public List<MediaGroup> completeYouTube() {
        mYouTubePlace.mIsClosed = true;

        for (Place place : mPlacesByKind.values()) {
            place.mIsClosed = true;
        }

        return release();
    }

    /**
     * @param group the rows of the added row, or null when there are none
     * @return the rows let through
     */
    public List<MediaGroup> addRow(Row row, @Nullable MediaGroup group) {
        Place place = mPlacesByRow.get(row);

        if (place == null || place.mIsClosed) {
            return Collections.emptyList();
        }

        if (group != null) {
            place.add(group);
        }

        place.mIsClosed = true;

        return release();
    }

    /**
     * Every row has been let through
     */
    public boolean isComplete() {
        return mCurrent == mPlaces.size();
    }

    private List<MediaGroup> release() {
        List<MediaGroup> result = new ArrayList<>();

        while (mCurrent < mPlaces.size()) {
            Place place = mPlaces.get(mCurrent);

            result.addAll(place.mRows.subList(place.mReleased, place.mRows.size()));
            place.mReleased = place.mRows.size();

            if (!place.mIsClosed) {
                break;
            }

            mCurrent++;
        }

        return result;
    }
}
