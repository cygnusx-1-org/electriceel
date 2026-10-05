package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.misc.OldVideoFilter;
import com.liskovsoft.smartyoutubetv2.common.prefs.CollaborationsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SectionFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ShowsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLaterData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

/**
 * A card of the Content Filtering settings that shows, marks or hides a filter's videos, where, and the color of the label
 * (e.g. Collaborations, Watch later, Shows)
 */
public class SectionFilterSettingsPresenter extends BasePresenter<Void> {
    private final SectionFilterData mData;
    private final SidebarService mSidebarService;
    private final int mTitleResId;
    private final int[][] mModes; // the title of each mode, the mode
    private final int mMarkDescResId;
    private final int mSectionsResId;

    private SectionFilterSettingsPresenter(Context context, SectionFilterData data, int titleResId,
                                           int showResId, int markResId, int markDescResId, int hideResId, int sectionsResId) {
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
    }

    public static SectionFilterSettingsPresenter collaborations(Context context) {
        return new SectionFilterSettingsPresenter(context, CollaborationsData.instance(context), R.string.collaborations,
                R.string.collaborations_show, R.string.collaborations_mark, R.string.collaborations_mark_desc, R.string.collaborations_hide,
                R.string.collaborations_sections);
    }

    public static SectionFilterSettingsPresenter watchLater(Context context) {
        return new SectionFilterSettingsPresenter(context, WatchLaterData.instance(context), R.string.watch_later,
                R.string.watch_later_show, R.string.watch_later_mark, R.string.watch_later_mark_desc, R.string.watch_later_hide,
                R.string.watch_later_sections);
    }

    public static SectionFilterSettingsPresenter shows(Context context) {
        return new SectionFilterSettingsPresenter(context, ShowsData.instance(context), R.string.shows,
                R.string.shows_show, R.string.shows_mark, R.string.shows_mark_desc, R.string.shows_hide,
                R.string.shows_sections);
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
     * The same sections as Hide videos older than. All is first: it checks or unchecks the others, and it's checked while they all are.
     */
    private void appendSectionsCategory(AppDialogPresenter presenter) {
        List<OptionItem> sections = new ArrayList<>();
        boolean isEverySectionEnabled = true;

        for (Entry<Integer, Integer> section : mSidebarService.getDefaultSections().entrySet()) {
            int sectionId = section.getValue();

            if (!OldVideoFilter.isSupportedSection(sectionId)) {
                continue;
            }

            sections.add(UiOptionItem.from(getContext().getString(section.getKey()),
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
