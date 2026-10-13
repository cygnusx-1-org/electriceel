package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;

/**
 * Marks or hides the videos of the account's Watch later in the chosen sections (see WatchLaterManager)
 */
public class WatchLaterData extends SectionFilterData {
    private static WatchLaterData sInstance;

    private WatchLaterData(Context context) {
        super(context);
    }

    public static WatchLaterData instance(Context context) {
        if (sInstance == null) {
            sInstance = new WatchLaterData(context);
        }

        return sInstance;
    }

    @Override
    protected int getDefaultMarkColor() {
        return AiSListFilterData.MARK_COLOR_BLUE;
    }

    /**
     * Not its own section, where every video is in Watch later
     */
    @Override
    public boolean isFilteredSection(int sectionId) {
        return sectionId != MediaGroup.TYPE_WATCH_LATER && super.isFilteredSection(sectionId);
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
