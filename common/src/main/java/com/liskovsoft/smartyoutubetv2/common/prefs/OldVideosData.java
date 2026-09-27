package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataSaverBase;

/**
 * Hides videos older than the chosen period from the chosen sections (see OldVideoFilter).<br/>
 * The period is kept while the filter is off, so the quick toggle on the main screen turns it back on as it was.<br/>
 * Each account has its own values while "Use separate settings per each account" is on.
 */
public class OldVideosData extends DataSaverBase {
    public static final int[] PERIODS_MONTHS = {1, 3, 6, 12, 24};
    private static final int DEFAULT_PERIOD_MONTHS = 12;
    // Storage layout. Don't change: the values are saved by index.
    private static final int ENABLED_INDEX = 0;
    private static final int PERIOD_INDEX = 1;
    private static final int QUICK_TOGGLE_INDEX = 2;
    private static final int SECTIONS_INDEX = 3; // a bit per section, by the section id (MediaGroup.TYPE_*)
    private static final int MAX_SECTION_ID = 30; // the bits of an int. Pinned items use hash ids, they're never filtered.
    private static final int DEFAULT_SECTIONS = (1 << MediaGroup.TYPE_HOME) | (1 << MediaGroup.TYPE_SUBSCRIPTIONS);
    private static OldVideosData sInstance;

    private OldVideosData(Context context) {
        // Saved right away: a save still pending when the account changes is dropped
        super(context, true, true);
    }

    public static OldVideosData instance(Context context) {
        if (sInstance == null) {
            sInstance = new OldVideosData(context);
        }

        return sInstance;
    }

    public boolean isEnabled() {
        return getBoolean(ENABLED_INDEX);
    }

    public void setEnabled(boolean enable) {
        setBoolean(ENABLED_INDEX, enable);
    }

    /**
     * One of {@link #PERIODS_MONTHS}
     */
    public int getPeriodMonths() {
        // A value never set is saved as "null" when a later one is set, and comes back as -1
        int months = getInt(PERIOD_INDEX, DEFAULT_PERIOD_MONTHS);

        for (int period : PERIODS_MONTHS) {
            if (period == months) {
                return months;
            }
        }

        return DEFAULT_PERIOD_MONTHS;
    }

    public void setPeriodMonths(int months) {
        setInt(PERIOD_INDEX, months);
    }

    /**
     * The button on the main screen that turns the filter on and off
     */
    public boolean isQuickToggleEnabled() {
        return getBoolean(QUICK_TOGGLE_INDEX);
    }

    public void setQuickToggleEnabled(boolean enable) {
        setBoolean(QUICK_TOGGLE_INDEX, enable);
    }

    /**
     * The filter applies to the section, whether it's on or not. Home and Subscriptions by default.
     */
    public boolean isSectionEnabled(int sectionId) {
        return isSection(sectionId) && (getSections() & (1 << sectionId)) != 0;
    }

    public void setSectionEnabled(int sectionId, boolean enable) {
        if (!isSection(sectionId)) {
            return;
        }

        int sections = getSections();
        setInt(SECTIONS_INDEX, enable ? sections | (1 << sectionId) : sections & ~(1 << sectionId));
    }

    private int getSections() {
        // A value never set is saved as "null" when a later one is set, and comes back as -1 (a real value has no bit 31)
        int sections = getInt(SECTIONS_INDEX, DEFAULT_SECTIONS);

        return sections == -1 ? DEFAULT_SECTIONS : sections;
    }

    private static boolean isSection(int sectionId) {
        return sectionId >= 0 && sectionId <= MAX_SECTION_ID;
    }

    /**
     * Videos of the section older than the period are hidden now
     */
    public boolean isHidingEnabled(int sectionId) {
        return isEnabled() && isSectionEnabled(sectionId);
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
