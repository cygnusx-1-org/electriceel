package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataSaverBase;

/**
 * Shows the end screen the creator put over the last seconds of a video (see EndScreenController). On by default.<br/>
 * Each account has its own value.
 */
public class EndScreenData extends DataSaverBase {
    // Storage layout. Don't change: the values are saved by index.
    private static final int SHOW_INDEX = 0;
    private static EndScreenData sInstance;

    private EndScreenData(Context context) {
        // Saved right away: a save still pending when the account changes is dropped
        super(context, true, true);
    }

    public static EndScreenData instance(Context context) {
        if (sInstance == null) {
            sInstance = new EndScreenData(context);
        }

        return sInstance;
    }

    public boolean isShowEnabled() {
        return getBoolean(SHOW_INDEX, true);
    }

    public void setShowEnabled(boolean enable) {
        setBoolean(SHOW_INDEX, enable);
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
