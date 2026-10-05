package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

/**
 * Marks or hides the shows (podcasts) in the chosen sections, e.g. the cards of Recommended shows (see MediaItem.isShow). Shown by default.
 */
public class ShowsData extends SectionFilterData {
    private static ShowsData sInstance;

    private ShowsData(Context context) {
        super(context);
    }

    public static ShowsData instance(Context context) {
        if (sInstance == null) {
            sInstance = new ShowsData(context);
        }

        return sInstance;
    }

    /**
     * Shown: unlike a collaboration or a video in Watch later, a show is a kind of card someone may want as it is
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
