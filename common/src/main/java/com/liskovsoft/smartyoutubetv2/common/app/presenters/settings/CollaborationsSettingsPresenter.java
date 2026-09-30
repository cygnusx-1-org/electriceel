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

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

/**
 * The Collaborations card of the Content Filtering settings: show, mark or hide them, and where
 */
public class CollaborationsSettingsPresenter extends BasePresenter<Void> {
    private static final int[][] MODES = {
            {R.string.collaborations_show, CollaborationsData.MODE_SHOW},
            {R.string.collaborations_mark, CollaborationsData.MODE_MARK},
            {R.string.collaborations_hide, CollaborationsData.MODE_HIDE}
    };
    private final CollaborationsData mData;
    private final SidebarService mSidebarService;

    private CollaborationsSettingsPresenter(Context context) {
        super(context);
        mData = CollaborationsData.instance(context);
        mSidebarService = SidebarService.instance(context);
    }

    public static CollaborationsSettingsPresenter instance(Context context) {
        return new CollaborationsSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());

        appendModeCategory(presenter);
        appendSectionsCategory(presenter);

        presenter.showDialog(getContext().getString(R.string.collaborations));
    }

    private void appendModeCategory(AppDialogPresenter presenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : MODES) {
            String description = pair[1] == CollaborationsData.MODE_MARK ? getContext().getString(R.string.collaborations_mark_desc) : null;
            options.add(UiOptionItem.from(getContext().getString(pair[0]), description,
                    option -> mData.setMode(pair[1]),
                    mData.getMode() == pair[1]));
        }

        presenter.appendRadioCategory(getContext().getString(R.string.collaborations), options);
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

        presenter.appendCheckedCategory(getContext().getString(R.string.collaborations_sections), options);
    }
}
