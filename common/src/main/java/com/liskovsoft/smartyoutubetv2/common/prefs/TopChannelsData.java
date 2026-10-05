package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

/**
 * Marks or hides the rows of channels in the chosen sections, e.g. Top channels you watch of Home (see MediaGroup.isChannelRow). Shown by default.
 */
public class TopChannelsData extends SectionFilterData {
    private static TopChannelsData sInstance;

    private TopChannelsData(Context context) {
        super(context);
    }

    public static TopChannelsData instance(Context context) {
        if (sInstance == null) {
            sInstance = new TopChannelsData(context);
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
