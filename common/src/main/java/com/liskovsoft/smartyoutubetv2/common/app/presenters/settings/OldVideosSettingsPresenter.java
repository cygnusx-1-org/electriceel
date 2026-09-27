package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.misc.OldVideoFilter;
import com.liskovsoft.smartyoutubetv2.common.prefs.OldVideosData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

/**
 * The Hide videos older than card of the Content Filtering settings
 */
public class OldVideosSettingsPresenter extends BasePresenter<Void> {
    private final SidebarService mSidebarService;

    private OldVideosSettingsPresenter(Context context) {
        super(context);
        mSidebarService = SidebarService.instance(context);
    }

    public static OldVideosSettingsPresenter instance(Context context) {
        return new OldVideosSettingsPresenter(context);
    }

    /**
     * The periods are radio buttons. Pressing the checked one turns the filter off and keeps the period for the quick toggle.
     */
    public void show() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        OldVideosData data = OldVideosData.instance(getContext());

        List<OptionItem> periods = new ArrayList<>();

        for (int months : OldVideosData.PERIODS_MONTHS) {
            periods.add(UiOptionItem.from(OldVideoFilter.getPeriodTitle(getContext(), months),
                    option -> {
                        if (option.isSelected()) {
                            data.setPeriodMonths(months);
                            data.setEnabled(true);
                        } else if (data.getPeriodMonths() == months) {
                            // Unchecked, or another period is being checked (it turns the filter back on)
                            data.setEnabled(false);
                        }
                    },
                    data.isEnabled() && data.getPeriodMonths() == months));
        }

        for (OptionItem period : periods) {
            List<OptionItem> others = new ArrayList<>(periods);
            others.remove(period);
            period.setRadio(others.toArray(new OptionItem[0]));
        }

        List<OptionItem> options = new ArrayList<>();

        OptionItem quickToggle = UiOptionItem.from(getContext().getString(R.string.quick_toggle),
                option -> data.setQuickToggleEnabled(option.isSelected()),
                data.isQuickToggleEnabled());
        quickToggle.setToggle(true);
        options.add(quickToggle);

        OptionItem sections = UiOptionItem.from(getContext().getString(R.string.hide_old_videos_sections), option -> showOldVideosSectionsMenu());
        sections.setMenu(true);
        options.add(sections);

        options.addAll(periods);

        String title = getContext().getString(R.string.hide_old_videos);
        presenter.appendCheckedCategory(title, options);
        presenter.showDialog(title);
    }

    private void showOldVideosSectionsMenu() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        OldVideosData data = OldVideosData.instance(getContext());

        List<OptionItem> options = new ArrayList<>();

        for (Entry<Integer, Integer> section : mSidebarService.getDefaultSections().entrySet()) {
            int sectionId = section.getValue();

            if (!OldVideoFilter.isSupportedSection(sectionId)) {
                continue;
            }

            options.add(UiOptionItem.from(getContext().getString(section.getKey()),
                    option -> data.setSectionEnabled(sectionId, option.isSelected()),
                    data.isSectionEnabled(sectionId)));
        }

        String title = getContext().getString(R.string.hide_old_videos_sections);
        presenter.appendCheckedCategory(title, options);
        presenter.showDialog(title);
    }
}
