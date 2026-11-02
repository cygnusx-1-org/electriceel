package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataSaverBase;

/**
 * Hides every video with progress on it, however little, from the feeds (see VideoGroup). Off by default.<br/>
 * Each account has its own values.
 */
public class OpenedVideosData extends DataSaverBase {
    // Storage layout. Don't change: the values are saved by index.
    private static final int HIDDEN_INDEX = 0;
    private static OpenedVideosData sInstance;

    private OpenedVideosData(Context context) {
        // Saved right away: a save still pending when the account changes is dropped
        super(context, true, true);
    }

    public static OpenedVideosData instance(Context context) {
        if (sInstance == null) {
            sInstance = new OpenedVideosData(context);
        }

        return sInstance;
    }

    public boolean isHidden() {
        return getBoolean(HIDDEN_INDEX);
    }

    public void setHidden(boolean hide) {
        setBoolean(HIDDEN_INDEX, hide);
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
