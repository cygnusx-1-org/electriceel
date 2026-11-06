package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.VideoCategoryManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.CategoryFilterData;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hide or mark content card of the Content Filtering settings: Collaborations, Watch later, Shows, Streamed or Live,
 * Top channels you watch, Explore more topics and the categories of Home
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
        options.add(createMenuItem(R.string.streamed, R.string.streamed_desc, () -> SectionFilterSettingsPresenter.streamed(getContext()).show()));
        options.add(createMenuItem(R.string.top_channels, R.string.top_channels_desc, () -> SectionFilterSettingsPresenter.topChannels(getContext()).show()));
        options.add(createMenuItem(R.string.explore_topics, R.string.explore_topics_desc, () -> SectionFilterSettingsPresenter.exploreTopics(getContext()).show()));

        OptionItem categories = createMenuItem(R.string.hide_content_categories, R.string.categories_desc, this::showCategoriesMenu);
        options.add(categories);
        appendCategoriesKeyNote(getContext(), categories, options);

        String title = getContext().getString(R.string.content_filtering_filtering);
        presenter.appendCheckedCategory(title, options);
        presenter.showDialog(title);
    }

    /**
     * Without the user's own Data API key (see VideoCategoryManager), greys out the categories and adds the note below them.
     * They're last in the list: the rows above stay where they are. Also the Categories of Hide content.
     */
    static void appendCategoriesKeyNote(Context context, OptionItem categories, List<OptionItem> options) {
        if (VideoCategoryManager.isAvailable()) {
            return;
        }

        categories.setEnabled(false);
        OptionItem note = UiOptionItem.from(context.getString(R.string.hide_content_categories_needs_key));
        note.setNote(true);
        options.add(note);
    }

    /**
     * Music, gaming, sports, news and tech in Home. Only with the user's own Data API key (see VideoCategoryManager).
     */
    private void showCategoriesMenu() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());

        List<OptionItem> options = new ArrayList<>();

        for (CategoryFilterData data : CategoryFilterData.all(getContext())) {
            options.add(createMenuItem(data.getTitleResId(), data.getDescResId(), () -> SectionFilterSettingsPresenter.category(getContext(), data).show()));
        }

        String title = getContext().getString(R.string.hide_content_categories);
        presenter.appendCheckedCategory(title, options);
        presenter.showDialog(title);
    }

    private OptionItem createMenuItem(int titleResId, int descriptionResId, Runnable onSelect) {
        OptionItem item = UiOptionItem.from(getContext().getString(titleResId), getContext().getString(descriptionResId), option -> onSelect.run());
        item.setMenu(true);
        return item;
    }
}
