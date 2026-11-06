package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;

/**
 * Marks or hides the live streams that ended in the chosen sections, e.g. "Streamed 5 hours ago" (see CompactInfo.isStreamed),
 * and the ones that are live now. The setting is called Streamed or Live.
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

    /**
     * Hide streams from Subscriptions of Hide content is part of this filter now: the account that had it checked hides streams
     * in Subscriptions with it, unless its mode was picked already (see AppPrefs.migrateHiddenStreams)
     *
     * @return the new values or null when the mode was picked
     */
    static String hideInSubscriptions(String data) {
        return setToHideIn(data, MediaGroup.TYPE_SUBSCRIPTIONS);
    }

    @Override
    protected int getDefaultMarkColor() {
        return AiSListFilterData.MARK_COLOR_PURPLE;
    }

    /**
     * Not the Live section, where every video is a stream
     */
    @Override
    public boolean isFilteredSection(int sectionId) {
        return sectionId != MediaGroup.TYPE_LIVE && super.isFilteredSection(sectionId);
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
