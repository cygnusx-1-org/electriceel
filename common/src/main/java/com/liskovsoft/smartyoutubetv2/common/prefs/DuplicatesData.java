package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataSaverBase;

/**
 * Shows each video once in the rows of the chosen sections (see DuplicateFilter). None by default.<br/>
 * Each account has its own values.
 */
public class DuplicatesData extends DataSaverBase {
    // Storage layout. Don't change: the values are saved by index.
    private static final int SECTIONS_INDEX = 0; // a bit per section, by the section id (MediaGroup.TYPE_*)
    private static final int DEFAULT_SECTIONS = 0;
    private static DuplicatesData sInstance;

    private DuplicatesData(Context context) {
        // Saved right away: a save still pending when the account changes is dropped
        super(context, true, true);
    }

    public static DuplicatesData instance(Context context) {
        if (sInstance == null) {
            sInstance = new DuplicatesData(context);
        }

        return sInstance;
    }

    /**
     * The videos of the section are shown once now
     */
    public boolean isSectionEnabled(int sectionId) {
        return isSupportedSection(sectionId) && (getInt(SECTIONS_INDEX, DEFAULT_SECTIONS) & (1 << sectionId)) != 0;
    }

    public void setSectionEnabled(int sectionId, boolean enable) {
        if (!isSupportedSection(sectionId)) {
            return;
        }

        int sections = getInt(SECTIONS_INDEX, DEFAULT_SECTIONS);
        setInt(SECTIONS_INDEX, enable ? sections | (1 << sectionId) : sections & ~(1 << sectionId));
    }

    /**
     * The sections of rows from the feeds. Not Playlists or My videos: the lists the user made may share their videos.
     * Pinned items use hash ids, they're never filtered.
     */
    public static boolean isSupportedSection(int sectionId) {
        switch (sectionId) {
            case MediaGroup.TYPE_HOME:
            case MediaGroup.TYPE_KIDS_HOME:
            case MediaGroup.TYPE_SPORTS:
            case MediaGroup.TYPE_LIVE:
            case MediaGroup.TYPE_GAMING:
            case MediaGroup.TYPE_NEWS:
            case MediaGroup.TYPE_MUSIC:
                return true;
            default:
                return false;
        }
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
