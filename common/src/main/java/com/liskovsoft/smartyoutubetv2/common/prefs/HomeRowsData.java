package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The order of the rows of Home and the rows added to it: the Subscriptions feed and channels (each a row of its uploads).<br/>
 * YouTube's rows come in YouTube's order as one entry, until the ones it shows on every load are put in an order of their own
 * (see {@link #orderYouTubeRows}). The rest of them stay in YouTube's order, where that entry is.<br/>
 * Each account has its own, as it has its own sidebar.
 */
public class HomeRowsData implements ProfileChangeListener {
    static final String HOME_ROWS_DATA = "home_rows_data";
    /**
     * The order of YouTube's rows that are on every load (see MediaGroup.getRowKind), once they're put in an order of their own
     */
    private static final int[] DEFAULT_KINDS = {
            MediaGroup.ROW_KIND_RECOMMENDED, MediaGroup.ROW_KIND_RECENTLY_UPLOADED, MediaGroup.ROW_KIND_CONTINUE_WATCHING,
            MediaGroup.ROW_KIND_WATCH_LATER, MediaGroup.ROW_KIND_SHOWS, MediaGroup.ROW_KIND_TOP_CHANNELS,
            MediaGroup.ROW_KIND_SEARCH_TOPICS, MediaGroup.ROW_KIND_SHORTS
    };
    @SuppressLint("StaticFieldLeak")
    private static HomeRowsData sInstance;
    private final AppPrefs mPrefs;
    private final List<Row> mRows = new ArrayList<>();
    // YouTube's rows taken out of Home, by their kind: MediaGroup.ROW_KIND_NONE is the rest of them (see isYouTubeRowShown)
    private final Set<Integer> mHiddenKinds = new HashSet<>();

    /**
     * A row of Home, or YouTube's rows that aren't in the list on their own
     */
    public static final class Row {
        /**
         * YouTube's rows that aren't in the list on their own, in YouTube's order
         */
        public static final int TYPE_YOUTUBE = 0;
        /**
         * One of YouTube's rows that are on every load, by its kind (MediaGroup.ROW_KIND_*)
         */
        public static final int TYPE_KIND = 1;
        public static final int TYPE_SUBSCRIPTIONS = 2;
        /**
         * The uploads of a channel
         */
        public static final int TYPE_CHANNEL = 3;
        public final int type;
        public final int kind;
        @Nullable
        public final String channelId;
        @Nullable
        public final String title;

        private Row(int type, int kind, @Nullable String channelId, @Nullable String title) {
            this.type = type;
            this.kind = kind;
            this.channelId = channelId;
            this.title = title;
        }

        public static Row youTube() {
            return new Row(TYPE_YOUTUBE, MediaGroup.ROW_KIND_NONE, null, null);
        }

        public static Row kind(int kind) {
            return new Row(TYPE_KIND, kind, null, null);
        }

        public static Row subscriptions() {
            return new Row(TYPE_SUBSCRIPTIONS, MediaGroup.ROW_KIND_NONE, null, null);
        }

        public static Row channel(@NonNull String channelId, @Nullable String title) {
            return new Row(TYPE_CHANNEL, MediaGroup.ROW_KIND_NONE, channelId, title);
        }

        /**
         * Added to Home: not one of YouTube's rows
         */
        public boolean isAdded() {
            return type == TYPE_SUBSCRIPTIONS || type == TYPE_CHANNEL;
        }

        @Nullable
        private static Row fromString(String spec) {
            String[] split = Helpers.splitObj(spec);

            if (split == null || split.length < 4) {
                return null;
            }

            int type = Helpers.parseInt(split[0]);
            String channelId = Helpers.parseStr(split[2]);

            if (type < TYPE_YOUTUBE || type > TYPE_CHANNEL || (type == TYPE_CHANNEL && channelId == null)) {
                return null;
            }

            return new Row(type, Helpers.parseInt(split[1]), channelId, Helpers.parseStr(split[3]));
        }

        /**
         * The same row: a channel under another title too
         */
        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof Row)) {
                return false;
            }

            Row row = (Row) obj;

            return type == row.type && kind == row.kind && Helpers.equals(channelId, row.channelId);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * type + kind) + (channelId != null ? channelId.hashCode() : 0);
        }

        @NonNull
        @Override
        public String toString() {
            return Helpers.mergeObj(type, kind, channelId, title);
        }
    }

    private HomeRowsData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    /**
     * YouTube's rows that are on every load, by their kind, in the order they get (see orderYouTubeRows)
     */
    public static List<Integer> getYouTubeKinds() {
        List<Integer> result = new ArrayList<>();

        for (int kind : DEFAULT_KINDS) {
            result.add(kind);
        }

        return result;
    }

    public static HomeRowsData instance(Context context) {
        if (sInstance == null) {
            sInstance = new HomeRowsData(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * In their order. YouTube's rows not in the list on their own are where {@link Row#youTube} is.
     */
    public synchronized List<Row> getRows() {
        return new ArrayList<>(mRows);
    }

    /**
     * Home is as YouTube shows it: no row is added and YouTube's rows are in YouTube's order
     */
    public synchronized boolean isDefault() {
        return mRows.size() == 1 && mRows.get(0).type == Row.TYPE_YOUTUBE && mHiddenKinds.isEmpty();
    }

    /**
     * The rows of YouTube of the kind are in Home (see MediaGroup.getRowKind). MediaGroup.ROW_KIND_NONE: the rest of them.
     */
    public synchronized boolean isYouTubeRowShown(int kind) {
        return !mHiddenKinds.contains(kind);
    }

    public synchronized void setYouTubeRowShown(int kind, boolean shown) {
        if (shown ? mHiddenKinds.remove(kind) : mHiddenKinds.add(kind)) {
            persistState();
        }
    }

    /**
     * YouTube's rows taken out of Home, by their kind (see isYouTubeRowShown)
     */
    public synchronized Set<Integer> getHiddenKinds() {
        return new HashSet<>(mHiddenKinds);
    }

    /**
     * In Home: an added row, or YouTube's not taken out (see isYouTubeRowShown)
     */
    public synchronized boolean isShown(Row row) {
        return row.isAdded() || isYouTubeRowShown(row.type == Row.TYPE_KIND ? row.kind : MediaGroup.ROW_KIND_NONE);
    }

    /**
     * YouTube's rows that are on every load are in an order of their own (see {@link #orderYouTubeRows})
     */
    public synchronized boolean isYouTubeOrdered() {
        for (Row row : mRows) {
            if (row.type == Row.TYPE_KIND) {
                return true;
            }
        }

        return false;
    }

    /**
     * YouTube's rows that are on every load get places of their own, where YouTube's rows were, before the rest of them
     */
    public synchronized void orderYouTubeRows() {
        if (isYouTubeOrdered()) {
            return;
        }

        int index = mRows.indexOf(Row.youTube());

        for (int kind : DEFAULT_KINDS) {
            mRows.add(index++, Row.kind(kind));
        }

        persistState();
    }

    /**
     * Back to YouTube's order: the added rows keep their places
     */
    public synchronized void resetYouTubeOrder() {
        if (!isYouTubeOrdered()) {
            return;
        }

        Helpers.removeIf(mRows, row -> row.type == Row.TYPE_KIND);

        persistState();
    }

    /**
     * To the place, the rows from there on go one down
     */
    public synchronized void moveRow(Row row, int index) {
        int from = mRows.indexOf(row);

        if (from == -1 || index < 0 || index >= mRows.size() || from == index) {
            return;
        }

        mRows.add(index, mRows.remove(from));

        persistState();
    }

    /**
     * Rows in a new order, e.g. the ones in Home (see isShown). The others come after them, in their order.
     * Left as they are when one isn't a row (e.g. it was removed since) or one is there twice.
     */
    public synchronized void setRows(List<Row> rows) {
        if (rows == null || !mRows.containsAll(rows) || new HashSet<>(rows).size() != rows.size()) {
            return;
        }

        List<Row> result = new ArrayList<>(rows);

        for (Row row : mRows) {
            if (!result.contains(row)) {
                result.add(row);
            }
        }

        if (result.equals(mRows)) {
            return;
        }

        mRows.clear();
        mRows.addAll(result);

        persistState();
    }

    public synchronized boolean isSubscriptionsAdded() {
        return mRows.contains(Row.subscriptions());
    }

    public synchronized void setSubscriptionsAdded(boolean added) {
        if (added) {
            add(Row.subscriptions());
        } else {
            remove(Row.subscriptions());
        }
    }

    public synchronized boolean isChannelAdded(String channelId) {
        return channelId != null && mRows.contains(Row.channel(channelId, null));
    }

    /**
     * After the rows added before it at the top. Added again, it keeps its place under the new title.
     */
    public synchronized void addChannel(String channelId, String title) {
        if (channelId == null) {
            return;
        }

        Row row = Row.channel(channelId, title);
        int index = mRows.indexOf(row);

        if (index != -1) {
            if (!Helpers.equals(title, mRows.get(index).title)) {
                mRows.set(index, row);
                persistState();
            }
            return;
        }

        add(row);
    }

    public synchronized void removeChannel(String channelId) {
        if (channelId != null) {
            remove(Row.channel(channelId, null));
        }
    }

    public synchronized void remove(Row row) {
        if (row.isAdded() && mRows.remove(row)) {
            persistState();
        }
    }

    /**
     * New rows go to the top, after the rows added before at the top
     */
    private void add(Row row) {
        if (mRows.contains(row)) {
            return;
        }

        int index = 0;

        while (index < mRows.size() && mRows.get(index).isAdded()) {
            index++;
        }

        mRows.add(index, row);

        persistState();
    }

    private synchronized void restoreState() {
        String[] split = Helpers.splitData(mPrefs.getProfileData(HOME_ROWS_DATA));

        mRows.clear();
        mHiddenKinds.clear();

        for (Row row : Helpers.parseList(split, 0, Row::fromString)) {
            if (!mRows.contains(row)) {
                mRows.add(row);
            }
        }

        for (String kind : Helpers.parseList(split, 1, value -> value)) {
            int value = Helpers.parseInt(kind, -1);

            if (value != -1) {
                mHiddenKinds.add(value);
            }
        }

        // YouTube's rows are always somewhere: after the others when they weren't
        if (!mRows.contains(Row.youTube())) {
            mRows.add(Row.youTube());
        }
    }

    /**
     * Right away: Home is loaded again with it
     */
    private void persistState() {
        mPrefs.setProfileData(HOME_ROWS_DATA, Helpers.mergeData(Helpers.mergeList(mRows), Helpers.mergeList(mHiddenKinds)));
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
