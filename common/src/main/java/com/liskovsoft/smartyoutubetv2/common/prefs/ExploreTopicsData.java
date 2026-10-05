package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

/**
 * Marks or hides the rows of topics that open a search in the chosen sections, e.g. Explore more topics of Home
 * (see MediaGroup.isSearchTopicRow). Shown by default.
 */
public class ExploreTopicsData extends SectionFilterData {
    private static ExploreTopicsData sInstance;

    private ExploreTopicsData(Context context) {
        super(context);
    }

    public static ExploreTopicsData instance(Context context) {
        if (sInstance == null) {
            sInstance = new ExploreTopicsData(context);
        }

        return sInstance;
    }

    /**
     * Shown: unlike a collaboration or a video in Watch later, the row is a kind of content someone may want as it is
     */
    @Override
    protected int getDefaultMode() {
        return MODE_SHOW;
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
