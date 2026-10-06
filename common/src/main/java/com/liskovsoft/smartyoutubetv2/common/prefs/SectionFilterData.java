package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.misc.OldVideoFilter;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataSaverBase;

/**
 * A filter that shows, marks or hides its videos in the chosen sections (e.g. CollaborationsData, WatchLaterData).<br/>
 * The label of a marked video has the colors of the AiSList marker (see AiSListFilterData.getMarkShade).<br/>
 * Each subclass is saved apart, under its own name. Each account has its own values while "Use separate settings per each account" is on.
 */
public abstract class SectionFilterData extends DataSaverBase {
    public static final int MODE_SHOW = 0;
    public static final int MODE_MARK = 1;
    public static final int MODE_HIDE = 2;
    private static final int DEFAULT_MODE = MODE_MARK;
    // Storage layout. Don't change: the values are saved by index.
    private static final int MODE_INDEX = 0;
    private static final int SECTIONS_INDEX = 1; // a bit per section, by the section id (MediaGroup.TYPE_*)
    private static final int MARK_COLOR_INDEX = 2;
    private static final int MAX_SECTION_ID = 30; // the bits of an int. Pinned items use hash ids, they're never filtered.
    private static final int DEFAULT_SECTIONS = Integer.MAX_VALUE; // every section (bits 0-30), the ones added later too

    protected SectionFilterData(Context context) {
        // Saved right away: a save still pending when the account changes is dropped
        super(context, true, true);
    }

    /**
     * One of MODE_*. Marked by default, unless the filter has another default (see getDefaultMode).
     */
    public int getMode() {
        // A value never set is saved as "null" when a later one is set, and comes back as -1
        int mode = getInt(MODE_INDEX, getDefaultMode());

        return mode == MODE_SHOW || mode == MODE_MARK || mode == MODE_HIDE ? mode : getDefaultMode();
    }

    /**
     * The mode until one is picked
     */
    protected int getDefaultMode() {
        return DEFAULT_MODE;
    }

    public void setMode(int mode) {
        setInt(MODE_INDEX, mode);
    }

    /**
     * The mode applies to the section. Every section by default, except the ones never filtered (see OldVideoFilter.isSupportedSection).
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
        return sectionId >= 0 && sectionId <= MAX_SECTION_ID && OldVideoFilter.isSupportedSection(sectionId);
    }

    /**
     * ARGB color of the label or {@link AiSListFilterData#MARK_COLOR_OFF} (the card text color).
     * The card text color by default, unless the filter has another default (see getDefaultMarkColor).
     */
    public int getMarkColor() {
        // A value never set is saved as "null" when a later one is set, and comes back as -1 (not a color of the menu)
        int color = getInt(MARK_COLOR_INDEX, getDefaultMarkColor());

        return color == -1 ? getDefaultMarkColor() : color;
    }

    /**
     * The label color until one is picked
     */
    protected int getDefaultMarkColor() {
        return AiSListFilterData.MARK_COLOR_OFF;
    }

    public void setMarkColor(int color) {
        setInt(MARK_COLOR_INDEX, color);
    }

    /**
     * The filter's videos in the section are found now, to be marked or hidden
     */
    public boolean isEnabled(int sectionId) {
        return getMode() != MODE_SHOW && isSectionEnabled(sectionId);
    }

    public boolean isHidingEnabled(int sectionId) {
        return getMode() == MODE_HIDE && isSectionEnabled(sectionId);
    }

    public boolean isMarkingEnabled(int sectionId) {
        return getMode() == MODE_MARK && isSectionEnabled(sectionId);
    }
}
