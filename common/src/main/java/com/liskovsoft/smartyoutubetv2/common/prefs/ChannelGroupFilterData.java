package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataSaverBase;

/**
 * The areas the channels of the subscription groups are hidden from, a channel of any group (see ChannelGroupFilter):
 * YouTube's rows of Home, the Subscriptions row added to Home (see HomeRowsData) and the sections of the feeds. None by default.<br/>
 * And whether the watched videos are hidden from the groups (see VideoGroup). Each account has its own values.
 */
public class ChannelGroupFilterData extends DataSaverBase {
    /**
     * The Subscriptions row added to Home (see HomeRowsData.Row.TYPE_SUBSCRIPTIONS). Not a section: the section ids
     * (MediaGroup.TYPE_*) are below it. Saved as a bit, as they are: don't change it.
     */
    public static final int AREA_HOME_SUBSCRIPTIONS_ROW = 30;
    // Storage layout. Don't change: the values are saved by index.
    private static final int AREAS_INDEX = 0; // a bit per area, by the section id or AREA_HOME_SUBSCRIPTIONS_ROW
    private static final int HIDE_WATCHED_INDEX = 1;
    private static final int DEFAULT_AREAS = 0;
    private static ChannelGroupFilterData sInstance;

    private ChannelGroupFilterData(Context context) {
        // Saved right away: a save still pending when the account changes is dropped
        super(context, true, true);
    }

    public static ChannelGroupFilterData instance(Context context) {
        if (sInstance == null) {
            sInstance = new ChannelGroupFilterData(context);
        }

        return sInstance;
    }

    /**
     * Home is YouTube's rows: the rows added to it are left alone, but for the Subscriptions row (see AREA_HOME_SUBSCRIPTIONS_ROW).
     * Then the sections of the feeds. Not History, Watch later, Playlists or My videos: the lists the user keeps.
     * Not Channels: its cards are channels. Pinned items use hash ids, they're never filtered.
     */
    public static boolean isSupportedArea(int area) {
        switch (area) {
            case MediaGroup.TYPE_HOME:
            case AREA_HOME_SUBSCRIPTIONS_ROW:
            case MediaGroup.TYPE_SHORTS:
            case MediaGroup.TYPE_KIDS_HOME:
            case MediaGroup.TYPE_SPORTS:
            case MediaGroup.TYPE_LIVE:
            case MediaGroup.TYPE_GAMING:
            case MediaGroup.TYPE_NEWS:
            case MediaGroup.TYPE_MUSIC:
            case MediaGroup.TYPE_SUBSCRIPTIONS:
                return true;
            default:
                return false;
        }
    }

    /**
     * The videos of the channels of the groups are left out of the area (see isSupportedArea)
     */
    public boolean isHidden(int area) {
        return isSupportedArea(area) && (getAreas() & (1 << area)) != 0;
    }

    public void setHidden(int area, boolean hide) {
        if (!isSupportedArea(area)) {
            return;
        }

        int areas = getAreas();
        setInt(AREAS_INDEX, hide ? areas | (1 << area) : areas & ~(1 << area));
    }

    private int getAreas() {
        // A value never set is saved as "null" when a later one is set, and comes back as -1 (a real value has no bit 31)
        int areas = getInt(AREAS_INDEX, DEFAULT_AREAS);

        return areas == -1 ? DEFAULT_AREAS : areas;
    }

    /**
     * The watched videos are left out of the subscription groups: their sections and their rows of Home. Off by default.
     */
    public boolean isWatchedHidden() {
        return getBoolean(HIDE_WATCHED_INDEX);
    }

    public void setWatchedHidden(boolean hide) {
        setBoolean(HIDE_WATCHED_INDEX, hide);
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
