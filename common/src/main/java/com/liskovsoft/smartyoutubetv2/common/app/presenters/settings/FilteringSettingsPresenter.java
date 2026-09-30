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
 * The Filtering card of the Content Filtering settings: Hide videos older than, Keyword filtering and Collaborations
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
        options.add(createMenuItem(R.string.hide_old_videos, () -> OldVideosSettingsPresenter.instance(getContext()).show()));
        options.add(createMenuItem(R.string.keyword_filtering, () -> KeywordFilterSettingsPresenter.instance(getContext()).show()));
        options.add(createMenuItem(R.string.collaborations, R.string.collaborations_desc, () -> CollaborationsSettingsPresenter.instance(getContext()).show()));

        String title = getContext().getString(R.string.content_filtering_filtering);
        presenter.appendCheckedCategory(title, options);
        presenter.showDialog(title);
    }

    private OptionItem createMenuItem(int titleResId, Runnable onSelect) {
        OptionItem item = UiOptionItem.from(getContext().getString(titleResId), option -> onSelect.run());
        item.setMenu(true);
        return item;
    }

    private OptionItem createMenuItem(int titleResId, int descriptionResId, Runnable onSelect) {
        OptionItem item = UiOptionItem.from(getContext().getString(titleResId), getContext().getString(descriptionResId), option -> onSelect.run());
        item.setMenu(true);
        return item;
    }
}
