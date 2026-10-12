package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataSaverBase;

/**
 * Each account has its own
 */
public class DeArrowData extends DataSaverBase {
    private static DeArrowData sInstance;

    private DeArrowData(Context context) {
        // Saved right away: a save still pending when the account changes is dropped
        super(context, true, true);
    }

    public static DeArrowData instance(Context context) {
        if (sInstance == null) {
            sInstance = new DeArrowData(context);
        }

        return sInstance;
    }

    public boolean isDeArrowEnabled() {
        return getBoolean(0);
    }

    public void setDeArrowEnabled(boolean enable) {
        setBoolean(0, enable);
    }

    public boolean isReplaceTitlesEnabled() {
        return getBoolean(1);
    }

    public void setReplaceTitlesEnabled(boolean enable) {
        setBoolean(1, enable);
    }

    public boolean isReplaceThumbnailsEnabled() {
        return getBoolean(2);
    }

    public void setReplaceThumbnailsEnabled(boolean replace) {
        setBoolean(2, replace);
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
