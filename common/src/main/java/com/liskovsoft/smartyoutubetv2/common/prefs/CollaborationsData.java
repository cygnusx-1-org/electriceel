package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

/**
 * Marks or hides collaborations in the chosen sections (see CollaborationManager)
 */
public class CollaborationsData extends SectionFilterData {
    private static CollaborationsData sInstance;

    private CollaborationsData(Context context) {
        super(context);
    }

    public static CollaborationsData instance(Context context) {
        if (sInstance == null) {
            sInstance = new CollaborationsData(context);
        }

        return sInstance;
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
