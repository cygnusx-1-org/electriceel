package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hide or mark content card of the Content Filtering settings: Collaborations and Watch later
 */
public class FilteringSettingsPresenter extends BasePresenter<Void> {
    private FilteringSettingsPresenter(Context context) {
        super(context);
    }

    public static FilteringSettingsPresenter instance(Context context) {
        return new FilteringSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());

        List<OptionItem> options = new ArrayList<>();
        options.add(createMenuItem(R.string.collaborations, R.string.collaborations_desc, () -> SectionFilterSettingsPresenter.collaborations(getContext()).show()));
        options.add(createMenuItem(R.string.watch_later, R.string.watch_later_desc, () -> SectionFilterSettingsPresenter.watchLater(getContext()).show()));
        options.add(createMenuItem(R.string.shows, R.string.shows_desc, () -> SectionFilterSettingsPresenter.shows(getContext()).show()));

        String title = getContext().getString(R.string.content_filtering_filtering);
        presenter.appendCheckedCategory(title, options);
        presenter.showDialog(title);
    }

    private OptionItem createMenuItem(int titleResId, int descriptionResId, Runnable onSelect) {
        OptionItem item = UiOptionItem.from(getContext().getString(titleResId), getContext().getString(descriptionResId), option -> onSelect.run());
        item.setMenu(true);
        return item;
    }
}
