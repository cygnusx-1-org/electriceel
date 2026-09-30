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
import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLaterData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

/**
 * A card of the Content Filtering settings that shows, marks or hides a filter's videos, and where (e.g. Collaborations, Watch later)
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

    public void show() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());

        appendModeCategory(presenter);
        appendSectionsCategory(presenter);

        presenter.showDialog(getContext().getString(mTitleResId));
    }

    private void appendModeCategory(AppDialogPresenter presenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : mModes) {
            String description = pair[1] == SectionFilterData.MODE_MARK ? getContext().getString(mMarkDescResId) : null;
            options.add(UiOptionItem.from(getContext().getString(pair[0]), description,
                    option -> mData.setMode(pair[1]),
                    mData.getMode() == pair[1]));
        }

        presenter.appendRadioCategory(getContext().getString(mTitleResId), options);
    }

    /**
     * The same sections as Hide videos older than
     */
    private void appendSectionsCategory(AppDialogPresenter presenter) {
        List<OptionItem> options = new ArrayList<>();

        for (Entry<Integer, Integer> section : mSidebarService.getDefaultSections().entrySet()) {
            int sectionId = section.getValue();

            if (!OldVideoFilter.isSupportedSection(sectionId)) {
                continue;
            }

            options.add(UiOptionItem.from(getContext().getString(section.getKey()),
                    option -> mData.setSectionEnabled(sectionId, option.isSelected()),
                    mData.isSectionEnabled(sectionId)));
        }

        presenter.appendCheckedCategory(getContext().getString(mSectionsResId), options);
    }
}
