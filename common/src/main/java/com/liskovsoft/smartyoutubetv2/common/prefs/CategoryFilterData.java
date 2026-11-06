package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.util.Arrays;

/**
 * Marks or hides the videos of Home in a category: music, gaming, sports, news or tech, by the category the uploader picked
 * or a topic YouTube finds in the video (see VideoCategoryManager). Only with the user's own Data API key.<br/>
 * Home only: the categories are looked up there alone. Shown by default, hidden while the Hide content option it replaces
 * is checked. Picking a mode unchecks it.<br/>
 * Each category is saved apart, under its own name. Each account has its own values.
 */
public final class CategoryFilterData extends SectionFilterData {
    // The names the categories are saved under. Don't change them.
    public static final String MUSIC_KEY = "MusicCategoryData";
    public static final String GAMING_KEY = "GamingCategoryData";
    public static final String SPORTS_KEY = "SportsCategoryData";
    public static final String NEWS_KEY = "NewsCategoryData";
    public static final String TECH_KEY = "TechCategoryData";
    // By the index of the category: music, gaming, sports, news and tech, in the order of the settings.
    // A video's category marks are bits by this index (see Video.categoryMarks).
    private static final String[] KEYS = {MUSIC_KEY, GAMING_KEY, SPORTS_KEY, NEWS_KEY, TECH_KEY};
    // The Hide content option each category replaces
    private static final int[] HIDDEN_CONTENT = {
            MediaServiceData.CONTENT_MUSIC_HOME, MediaServiceData.CONTENT_GAMING_HOME, MediaServiceData.CONTENT_SPORTS_HOME,
            MediaServiceData.CONTENT_NEWS_HOME, MediaServiceData.CONTENT_TECH_HOME};
    private static final int[] TITLES = {
            R.string.categories_music, R.string.categories_gaming, R.string.categories_sports, R.string.categories_news, R.string.categories_tech};
    private static final int[] DESCRIPTIONS = {
            R.string.categories_music_desc, R.string.categories_gaming_desc, R.string.categories_sports_desc,
            R.string.categories_news_desc, R.string.categories_tech_desc};
    private static final int[] MARK_DESCRIPTIONS = {
            R.string.categories_music_mark_desc, R.string.categories_gaming_mark_desc, R.string.categories_sports_mark_desc,
            R.string.categories_news_mark_desc, R.string.categories_tech_mark_desc};
    // The label of a marked video: the title, unless it's too long for a card
    private static final int[] LABELS = {
            R.string.categories_music, R.string.categories_gaming, R.string.categories_sports, R.string.news_label, R.string.tech_label};
    @SuppressLint("StaticFieldLeak")
    private static final CategoryFilterData[] sInstances = new CategoryFilterData[KEYS.length];
    private final Context mContext;
    private final int mIndex;

    private CategoryFilterData(Context context, int index) {
        super(context, KEYS[index]);
        mContext = context.getApplicationContext();
        mIndex = index;
    }

    /**
     * Music, gaming, sports, news and tech, in the order of the settings. A video's category marks are bits by this index (see Video.categoryMarks).
     */
    public static CategoryFilterData[] all(Context context) {
        CategoryFilterData[] all = new CategoryFilterData[KEYS.length];

        for (int i = 0; i < KEYS.length; i++) {
            if (sInstances[i] == null) {
                sInstances[i] = new CategoryFilterData(context, i);
            }

            all[i] = sInstances[i];
        }

        return all;
    }

    /**
     * Hidden while the Hide content option it replaces is checked (each account has its own),
     * shown otherwise: a category is a kind of content someone may want as it is
     */
    @Override
    protected int getDefaultMode() {
        return MediaServiceData.instance().isContentHiddenInSettings(HIDDEN_CONTENT[mIndex]) ? MODE_HIDE : MODE_SHOW;
    }

    /**
     * The picked mode is the one in use from now on: the Hide content option it replaces is unchecked
     */
    @Override
    public void setMode(int mode) {
        super.setMode(mode);

        if (MediaServiceData.instance().isContentHiddenInSettings(HIDDEN_CONTENT[mIndex])) {
            HideContentData.instance(mContext).setContentHidden(HIDDEN_CONTENT[mIndex], false);
        }
    }

    @Override
    public boolean isFilteredSection(int sectionId) {
        return sectionId == MediaGroup.TYPE_HOME;
    }

    public int getTitleResId() {
        return TITLES[mIndex];
    }

    public int getDescResId() {
        return DESCRIPTIONS[mIndex];
    }

    public int getMarkDescResId() {
        return MARK_DESCRIPTIONS[mIndex];
    }

    /**
     * The label of a marked video
     */
    public int getLabelResId() {
        return LABELS[mIndex];
    }

    /**
     * The next instances read the saved values again
     */
    static void resetInstancesForTesting() {
        Arrays.fill(sInstances, null);
    }
}
