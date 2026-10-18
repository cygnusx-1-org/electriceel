package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataSaverBase;

/**
 * Mutes the words YouTube's automatic captions censor (see CensoredWordsController). Off by default.<br/>
 * Each account has its own value.
 */
public class CensoredWordsData extends DataSaverBase {
    // Storage layout. Don't change: the values are saved by index.
    private static final int MUTE_INDEX = 0;
    private static CensoredWordsData sInstance;

    private CensoredWordsData(Context context) {
        // Saved right away: a save still pending when the account changes is dropped
        super(context, true, true);
    }

    public static CensoredWordsData instance(Context context) {
        if (sInstance == null) {
            sInstance = new CensoredWordsData(context);
        }

        return sInstance;
    }

    public boolean isMuteEnabled() {
        return getBoolean(MUTE_INDEX);
    }

    public void setMuteEnabled(boolean enable) {
        setBoolean(MUTE_INDEX, enable);
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
