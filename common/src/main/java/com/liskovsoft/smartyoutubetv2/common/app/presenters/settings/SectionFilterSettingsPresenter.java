package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.misc.OldVideoFilter;
import com.liskovsoft.smartyoutubetv2.common.prefs.CollaborationsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ExploreTopicsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SectionFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ShowsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.TopChannelsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLaterData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

/**
 * A card of the Content Filtering settings that shows, marks or hides a filter's videos, where, and the color of the label
 * (e.g. Collaborations, Watch later, Shows, Top channels you watch, Explore more topics)
 */
public class SectionFilterSettingsPresenter extends BasePresenter<Void> {
    // Outside the sidebar: the title, the section id the filter knows it by (see VideoGroup.getFilterSectionId)
    private static final int[][] PAGE_SECTIONS = {
            {R.string.title_search, MediaGroup.TYPE_SEARCH},
            {R.string.channel_pages, MediaGroup.TYPE_CHANNEL}
    };
    private final SectionFilterData mData;
    private final SidebarService mSidebarService;
    private final int mTitleResId;
    private final int[][] mModes; // the title of each mode, the mode
    private final int mMarkDescResId;
    private final int mSectionsResId;
    private final boolean mHasPageSections; // also in search and on channel pages

    private SectionFilterSettingsPresenter(Context context, SectionFilterData data, int titleResId,
                                           int showResId, int markResId, int markDescResId, int hideResId, int sectionsResId,
                                           boolean hasPageSections) {
        super(context);
        mData = data;
        mSidebarService = SidebarService.instance(context);
        mTitleResId = titleResId;
        mModes = new int[][] {
                {showResId, SectionFilterData.MODE_SHOW},
                {markResId, SectionFilterData.MODE_MARK},
                {hideResId, SectionFilterData.MODE_HIDE}
        };
        mMarkDescResId = markDescResId;
        mSectionsResId = sectionsResId;
        mHasPageSections = hasPageSections;
    }

    public static SectionFilterSettingsPresenter collaborations(Context context) {
        return new SectionFilterSettingsPresenter(context, CollaborationsData.instance(context), R.string.collaborations,
                R.string.collaborations_show, R.string.collaborations_mark, R.string.collaborations_mark_desc, R.string.collaborations_hide,
                R.string.collaborations_sections, true);
    }

    public static SectionFilterSettingsPresenter watchLater(Context context) {
        return new SectionFilterSettingsPresenter(context, WatchLaterData.instance(context), R.string.watch_later,
                R.string.watch_later_show, R.string.watch_later_mark, R.string.watch_later_mark_desc, R.string.watch_later_hide,
                R.string.watch_later_sections, true);
    }

    public static SectionFilterSettingsPresenter shows(Context context) {
        return new SectionFilterSettingsPresenter(context, ShowsData.instance(context), R.string.shows,
                R.string.shows_show, R.string.shows_mark, R.string.shows_mark_desc, R.string.shows_hide,
                R.string.shows_sections, true);
    }

    public static SectionFilterSettingsPresenter topChannels(Context context) {
        return new SectionFilterSettingsPresenter(context, TopChannelsData.instance(context), R.string.top_channels,
                R.string.top_channels_show, R.string.top_channels_mark, R.string.top_channels_mark_desc, R.string.top_channels_hide,
                R.string.top_channels_sections, false);
    }

    public static SectionFilterSettingsPresenter exploreTopics(Context context) {
        return new SectionFilterSettingsPresenter(context, ExploreTopicsData.instance(context), R.string.explore_topics,
                R.string.explore_topics_show, R.string.explore_topics_mark, R.string.explore_topics_mark_desc, R.string.explore_topics_hide,
                R.string.explore_topics_sections, false);
    }

    public void show() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());

        OptionItem markMode = appendModeCategory(presenter);
        appendSectionsCategory(presenter);
        appendMarkColorCategory(presenter, markMode);

        presenter.showDialog(getContext().getString(mTitleResId));
    }

    /**
     * @return the mode that marks videos
     */
    private OptionItem appendModeCategory(AppDialogPresenter presenter) {
        List<OptionItem> options = new ArrayList<>();
        OptionItem markMode = null;

        for (int[] pair : mModes) {
            String description = pair[1] == SectionFilterData.MODE_MARK ? getContext().getString(mMarkDescResId) : null;
            OptionItem option = UiOptionItem.from(getContext().getString(pair[0]), description,
                    optionItem -> mData.setMode(pair[1]),
                    mData.getMode() == pair[1]);
            options.add(option);

            if (pair[1] == SectionFilterData.MODE_MARK) {
                markMode = option;
            }
        }

        presenter.appendRadioCategory(getContext().getString(mTitleResId), options);
        return markMode;
    }

    /**
     * The same sections as Hide videos older than, then Search and Channel pages for a filter of videos found there too.
     * All is first: it checks or unchecks the others, and it's checked while they all are.
     */
    private void appendSectionsCategory(AppDialogPresenter presenter) {
        List<int[]> titledSections = new ArrayList<>();

        for (Entry<Integer, Integer> section : mSidebarService.getDefaultSections().entrySet()) {
            if (OldVideoFilter.isSupportedSection(section.getValue())) {
                titledSections.add(new int[] {section.getKey(), section.getValue()});
            }
        }

        if (mHasPageSections) {
            titledSections.addAll(Arrays.asList(PAGE_SECTIONS));
        }

        List<OptionItem> sections = new ArrayList<>();
        boolean isEverySectionEnabled = true;

        for (int[] section : titledSections) {
            int sectionId = section[1];

            sections.add(UiOptionItem.from(getContext().getString(section[0]),
                    option -> mData.setSectionEnabled(sectionId, option.isSelected()),
                    mData.isSectionEnabled(sectionId)));
            isEverySectionEnabled &= mData.isSectionEnabled(sectionId);
        }

        // The sections save themselves as All checks or unchecks them
        OptionItem all = UiOptionItem.from(getContext().getString(R.string.sections_all), null, isEverySectionEnabled);
        all.setSelectAll(sections.toArray(new OptionItem[0]));

        List<OptionItem> options = new ArrayList<>();
        options.add(all);
        options.addAll(sections);

        presenter.appendCheckedCategory(getContext().getString(mSectionsResId), options);
    }

    /**
     * The colors of the AiSList marker. Greyed out while the mode isn't Mark.
     */
    private void appendMarkColorCategory(AppDialogPresenter presenter, OptionItem markMode) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : AiSListSettingsPresenter.MARK_COLORS) {
            OptionItem option = UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mData.setMarkColor(pair[1]),
                    mData.getMarkColor() == pair[1]);
            option.setRequired(markMode);
            options.add(option);
        }

        presenter.appendRadioCategory(getContext().getString(R.string.aislist_mark_color), options);
    }
}
