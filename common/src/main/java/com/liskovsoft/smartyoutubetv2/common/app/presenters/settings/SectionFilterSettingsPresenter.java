package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import android.util.Pair;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.prefs.CollaborationsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ExploreTopicsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.FilterExclusionData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SectionFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ShowsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.StreamedData;
import com.liskovsoft.smartyoutubetv2.common.prefs.TopChannelsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLaterData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

/**
 * A card of the Content Filtering settings that shows, marks or hides a filter's videos, where, the color of the label,
 * the channels excluded from the hiding and the quick toggle of the hiding
 * (e.g. Collaborations, Watch later, Shows, Streamed or Live, Top channels you watch, Explore more topics)
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
    private final int mExclusionFilter; // the FilterExclusionData.FILTER_* channels are excluded from, or 0
    private final boolean mHasQuickToggle; // the button on the main screen turns the hiding off and on (see QuickTogglePresenter)

    private SectionFilterSettingsPresenter(Context context, SectionFilterData data, int titleResId,
                                           int showResId, int markResId, int markDescResId, int hideResId, int sectionsResId,
                                           boolean hasPageSections, int exclusionFilter, boolean hasQuickToggle) {
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
        mExclusionFilter = exclusionFilter;
        mHasQuickToggle = hasQuickToggle;
    }

    public static SectionFilterSettingsPresenter collaborations(Context context) {
        return new SectionFilterSettingsPresenter(context, CollaborationsData.instance(context), R.string.collaborations,
                R.string.collaborations_show, R.string.collaborations_mark, R.string.collaborations_mark_desc, R.string.collaborations_hide,
                R.string.collaborations_sections, true, FilterExclusionData.FILTER_COLLABORATIONS, true);
    }

    public static SectionFilterSettingsPresenter watchLater(Context context) {
        return new SectionFilterSettingsPresenter(context, WatchLaterData.instance(context), R.string.watch_later,
                R.string.watch_later_show, R.string.watch_later_mark, R.string.watch_later_mark_desc, R.string.watch_later_hide,
                R.string.watch_later_sections, true, FilterExclusionData.FILTER_WATCH_LATER, true);
    }

    public static SectionFilterSettingsPresenter shows(Context context) {
        return new SectionFilterSettingsPresenter(context, ShowsData.instance(context), R.string.shows,
                R.string.shows_show, R.string.shows_mark, R.string.shows_mark_desc, R.string.shows_hide,
                R.string.shows_sections, true, 0, false);
    }

    public static SectionFilterSettingsPresenter streamed(Context context) {
        return new SectionFilterSettingsPresenter(context, StreamedData.instance(context), R.string.streamed,
                R.string.streamed_show, R.string.streamed_mark, R.string.streamed_mark_desc, R.string.streamed_hide,
                R.string.streamed_sections, true, FilterExclusionData.FILTER_STREAMED, true);
    }

    public static SectionFilterSettingsPresenter topChannels(Context context) {
        return new SectionFilterSettingsPresenter(context, TopChannelsData.instance(context), R.string.top_channels,
                R.string.top_channels_show, R.string.top_channels_mark, R.string.top_channels_mark_desc, R.string.top_channels_hide,
                R.string.top_channels_sections, false, 0, false);
    }

    public static SectionFilterSettingsPresenter exploreTopics(Context context) {
        return new SectionFilterSettingsPresenter(context, ExploreTopicsData.instance(context), R.string.explore_topics,
                R.string.explore_topics_show, R.string.explore_topics_mark, R.string.explore_topics_mark_desc, R.string.explore_topics_hide,
                R.string.explore_topics_sections, false, 0, false);
    }

    public void show() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());

        OptionItem[] modes = appendModeCategory(presenter);
        appendQuickToggleSwitch(presenter, modes[SectionFilterData.MODE_HIDE]);
        appendSectionsCategory(presenter);
        appendMarkColorCategory(presenter, modes[SectionFilterData.MODE_MARK]);
        appendExcludedChannelsCategory(presenter);

        presenter.showDialog(getContext().getString(mTitleResId));
    }

    /**
     * @return the modes, by their value (SectionFilterData.MODE_*)
     */
    private OptionItem[] appendModeCategory(AppDialogPresenter presenter) {
        List<OptionItem> options = new ArrayList<>();
        OptionItem[] modes = new OptionItem[mModes.length];

        for (int[] pair : mModes) {
            String description = pair[1] == SectionFilterData.MODE_MARK ? getContext().getString(mMarkDescResId) : null;
            OptionItem option = UiOptionItem.from(getContext().getString(pair[0]), description,
                    optionItem -> mData.setMode(pair[1]),
                    mData.getMode() == pair[1]);
            options.add(option);
            modes[pair[1]] = option;
        }

        presenter.appendRadioCategory(getContext().getString(mTitleResId), options);
        return modes;
    }

    /**
     * The button on the main screen that turns the hiding off and on, only for a filter that has one. Greyed out while the mode isn't Hide.
     */
    private void appendQuickToggleSwitch(AppDialogPresenter presenter, OptionItem hideMode) {
        if (!mHasQuickToggle) {
            return;
        }

        OptionItem quickToggle = UiOptionItem.from(getContext().getString(R.string.quick_toggle),
                option -> {
                    if (option.isSelected()) {
                        // Starts on: the videos are hidden as before
                        mData.setQuickToggleHiding(true);
                    }

                    mData.setQuickToggleEnabled(option.isSelected());
                },
                mData.isQuickToggleEnabled());
        quickToggle.setRequired(hideMode);
        presenter.appendSingleSwitch(quickToggle);
    }

    /**
     * The same sections as Hide videos older than, then Search and Channel pages for a filter of videos found there too.
     * All is first: it checks or unchecks the others, and it's checked while they all are.
     */
    private void appendSectionsCategory(AppDialogPresenter presenter) {
        List<int[]> titledSections = new ArrayList<>();

        for (Entry<Integer, Integer> section : mSidebarService.getDefaultSections().entrySet()) {
            if (mData.isFilteredSection(section.getValue())) {
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

    /**
     * The channels excluded from the hiding in the video context menu (see FilterExclusionSettingsPresenter), only when there's one.
     * Unchecking includes a channel again. It stays in the list until the list is opened again, so it can be checked back.
     */
    private void appendExcludedChannelsCategory(AppDialogPresenter presenter) {
        if (mExclusionFilter == 0) {
            return;
        }

        FilterExclusionData data = FilterExclusionData.instance(getContext());
        List<Pair<String, String>> channels = data.getChannels(mExclusionFilter);

        if (channels.isEmpty()) {
            return;
        }

        List<OptionItem> options = new ArrayList<>();

        for (Pair<String, String> channel : channels) {
            options.add(UiOptionItem.from(channel.second != null ? channel.second : channel.first,
                    option -> data.setExcluded(mExclusionFilter, channel.first, channel.second, option.isSelected()),
                    true));
        }

        presenter.appendCheckedCategory(getContext().getString(R.string.excluded_channels), options);
    }
}
