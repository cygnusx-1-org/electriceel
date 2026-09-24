package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataSaverBase;

/**
 * Where to hide videos from channels listed by AiSList.<br/>
 * Each list has its own set of sections. Everything is off by default.
 */
public class AiSListFilterData extends DataSaverBase {
    public static final int LIST_BLOCKLIST = 0;
    public static final int LIST_WARNLIST = 1;
    public static final int SECTION_HOME = 0;
    public static final int SECTION_SEARCH = 1;
    public static final int SECTION_SUBSCRIPTIONS = 2;
    public static final int SECTION_SUGGESTIONS = 3;
    // Mark only: a video is never hidden from these (see isMarkOnly)
    public static final int SECTION_CHANNELS = 4;
    public static final int SECTION_HISTORY = 5;
    public static final int SECTION_PLAYLISTS = 6;
    public static final int SECTION_WATCH_LATER = 7;
    public static final int SECTION_COUNT = 8;
    // Storage layout. Don't change: the values are saved by index.
    private static final int FIRST_SECTION_COUNT = 5; // sections 0-4 of both lists
    private static final int MARK_INDEX = 2 * FIRST_SECTION_COUNT; // the blocklist (was one switch for both lists)
    private static final int MARK_COLOR_INDEX = MARK_INDEX + 1;
    private static final int MORE_SECTIONS_INDEX = MARK_COLOR_INDEX + 1; // sections 5+ of both lists
    private static final int EVERYTHING_INDEX = MORE_SECTIONS_INDEX + 2 * (SECTION_COUNT - FIRST_SECTION_COUNT); // one per list
    private static final int WARNLIST_MARK_COLOR_INDEX = EVERYTHING_INDEX + 2; // the blocklist uses MARK_COLOR_INDEX
    private static final int WARNLIST_MARK_INDEX = WARNLIST_MARK_COLOR_INDEX + 1; // the blocklist uses MARK_INDEX
    private static final int MARK_MODE_INDEX = WARNLIST_MARK_INDEX + 1; // one per list, replaces the mark switches
    public static final int MARK_MODE_OFF = 0; // hide, mark only sections are left alone
    public static final int MARK_MODE_MARK_ONLY_SECTIONS = 1; // hide, mark in the mark only sections
    public static final int MARK_MODE_ALL = 2; // mark everywhere, never hide
    public static final int MARK_COLOR_OFF = 0; // the marker uses the card text color
    public static final int MARK_COLOR_RED = 0xFFFF0000;
    public static final int MARK_COLOR_ORANGE = 0xFFFF9800;
    public static final int MARK_COLOR_YELLOW = 0xFFFFEB3B;
    public static final int MARK_COLOR_GREEN = 0xFF4CAF50;
    public static final int MARK_COLOR_BLUE = 0xFF2196F3;
    /**
     * Saved color (the key, don't change) -> shade on an unselected (dark) card, shade on the selected (light) card.<br/>
     * Each shade has at least 4.5:1 contrast on its card in the default theme (teal #004B53 / white #FFFFFF),
     * except the red: it's fire engine red #CE2029 on both cards (1.8:1 on teal, 5.4:1 on white).
     */
    private static final int[][] MARK_SHADES = {
            {MARK_COLOR_RED, 0xFFCE2029, 0xFFCE2029}, // fire engine red
            {MARK_COLOR_ORANGE, 0xFFFFA000, 0xFFB85C00},
            {MARK_COLOR_YELLOW, 0xFFD1AE00, 0xFF8A7300},
            {MARK_COLOR_GREEN, 0xFF2BCA30, 0xFF1D8720},
            {MARK_COLOR_BLUE, 0xFF69B7F7, 0xFF0B78D0}
    };
    private static AiSListFilterData sInstance;

    private AiSListFilterData(Context context) {
        super(context);
    }

    public static AiSListFilterData instance(Context context) {
        if (sInstance == null) {
            sInstance = new AiSListFilterData(context);
        }

        return sInstance;
    }

    /**
     * The list applies to the section (its own switch or Everything)
     */
    public boolean isHideEnabled(int list, int section) {
        return isEverythingEnabled(list) || isSectionChecked(list, section);
    }

    /**
     * The section's own switch, regardless of Everything
     */
    public boolean isSectionChecked(int list, int section) {
        return getBoolean(getIndex(list, section));
    }

    public void setHideEnabled(int list, int section, boolean enable) {
        setBoolean(getIndex(list, section), enable);
    }

    /**
     * The list applies to every section
     */
    public boolean isEverythingEnabled(int list) {
        return getBoolean(EVERYTHING_INDEX + list);
    }

    public void setEverythingEnabled(int list, boolean enable) {
        setBoolean(EVERYTHING_INDEX + list, enable);
    }

    /**
     * Where listed videos get a label: {@link #MARK_MODE_OFF}, {@link #MARK_MODE_MARK_ONLY_SECTIONS} or {@link #MARK_MODE_ALL}
     */
    public int getMarkMode(int list) {
        // Until set, follows the old switch: on meant mark everywhere
        return getInt(MARK_MODE_INDEX + list, isOldMarkEnabled(list) ? MARK_MODE_ALL : MARK_MODE_OFF);
    }

    public void setMarkMode(int list, int mode) {
        setInt(MARK_MODE_INDEX + list, mode);
    }

    private boolean isOldMarkEnabled(int list) {
        if (list == LIST_WARNLIST) {
            // Until set, followed the old switch that was shared by both lists
            return getBoolean(WARNLIST_MARK_INDEX, getBoolean(MARK_INDEX));
        }

        return getBoolean(MARK_INDEX);
    }

    /**
     * ARGB color of the "AI" marker for the list or {@link #MARK_COLOR_OFF}
     */
    public int getMarkColor(int list) {
        return getInt(getMarkColorIndex(list), MARK_COLOR_OFF);
    }

    public void setMarkColor(int list, int color) {
        setInt(getMarkColorIndex(list), color);
    }

    /**
     * The shade of a saved marker color that reads well on the card
     * @param selected the card is selected (light background)
     */
    public static int getMarkShade(int color, boolean selected) {
        for (int[] shades : MARK_SHADES) {
            if (shades[0] == color) {
                return selected ? shades[2] : shades[1];
            }
        }

        return color;
    }

    private static int getMarkColorIndex(int list) {
        return list == LIST_WARNLIST ? WARNLIST_MARK_COLOR_INDEX : MARK_COLOR_INDEX;
    }

    /**
     * Listed videos in these sections are only marked (in the mark mode), never hidden
     */
    public static boolean isMarkOnly(int section) {
        return section == SECTION_CHANNELS || section >= FIRST_SECTION_COUNT;
    }

    private static int getIndex(int list, int section) {
        if (section < FIRST_SECTION_COUNT) {
            return list * FIRST_SECTION_COUNT + section;
        }

        return MORE_SECTIONS_INDEX + list * (SECTION_COUNT - FIRST_SECTION_COUNT) + section - FIRST_SECTION_COUNT;
    }
}
