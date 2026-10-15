package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

/**
 * Marks or hides the live streams that ended in the chosen sections, e.g. "Streamed 5 hours ago" (see CompactInfo.isStreamed)
 */
public class StreamedData extends SectionFilterData {
    private static StreamedData sInstance;

    private StreamedData(Context context) {
        super(context);
    }

    public static StreamedData instance(Context context) {
        if (sInstance == null) {
            sInstance = new StreamedData(context);
        }

        return sInstance;
    }

    @Override
    protected int getDefaultMarkColor() {
        return AiSListFilterData.MARK_COLOR_PURPLE;
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
