package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsPanel;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsRows;
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
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.content_filtering_filtering),
                this::appendRows, null);
    }

    /**
     * The rows of the panel show shows (see SettingsPanel)
     */
    public void appendRows(SettingsRows presenter) {
        List<OptionItem> options = new ArrayList<>();
        options.add(createMenuItem(R.string.collaborations, R.string.collaborations_desc, () -> SectionFilterSettingsPresenter.collaborations(getContext()).show(),
                rows -> SectionFilterSettingsPresenter.collaborations(getContext()).appendRows(rows)));
        options.add(createMenuItem(R.string.watch_later, R.string.watch_later_desc, () -> SectionFilterSettingsPresenter.watchLater(getContext()).show(),
                rows -> SectionFilterSettingsPresenter.watchLater(getContext()).appendRows(rows)));
        options.add(createMenuItem(R.string.shows, R.string.shows_desc, () -> SectionFilterSettingsPresenter.shows(getContext()).show(),
                rows -> SectionFilterSettingsPresenter.shows(getContext()).appendRows(rows)));
        options.add(createMenuItem(R.string.members, R.string.members_desc, () -> SectionFilterSettingsPresenter.members(getContext()).show(),
                rows -> SectionFilterSettingsPresenter.members(getContext()).appendRows(rows)));
        options.add(createMenuItem(R.string.streamed, R.string.streamed_desc, () -> SectionFilterSettingsPresenter.streamed(getContext()).show(),
                rows -> SectionFilterSettingsPresenter.streamed(getContext()).appendRows(rows)));
        options.add(createMenuItem(R.string.top_channels, R.string.top_channels_desc, () -> SectionFilterSettingsPresenter.topChannels(getContext()).show(),
                rows -> SectionFilterSettingsPresenter.topChannels(getContext()).appendRows(rows)));
        options.add(createMenuItem(R.string.explore_topics, R.string.explore_topics_desc, () -> SectionFilterSettingsPresenter.exploreTopics(getContext()).show(),
                rows -> SectionFilterSettingsPresenter.exploreTopics(getContext()).appendRows(rows)));

        OptionItem categories = createMenuItem(R.string.hide_content_categories, R.string.categories_desc, this::showCategoriesMenu, this::appendCategoriesMenuRows);
        options.add(categories);
        appendCategoriesKeyNote(getContext(), categories, options);

        String title = getContext().getString(R.string.content_filtering_filtering);
        presenter.appendCheckedCategory(title, options);
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
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.hide_content_categories),
                this::appendCategoriesMenuRows, null);
    }

    /**
     * The rows of the menu showCategoriesMenu shows (see SettingsPanel)
     */
    private void appendCategoriesMenuRows(SettingsRows presenter) {
        List<OptionItem> options = new ArrayList<>();

        for (CategoryFilterData data : CategoryFilterData.all(getContext())) {
            options.add(createMenuItem(data.getTitleResId(), data.getDescResId(), () -> SectionFilterSettingsPresenter.category(getContext(), data).show(),
                rows -> SectionFilterSettingsPresenter.category(getContext(), data).appendRows(rows)));
        }

        presenter.appendCheckedCategory(getContext().getString(R.string.hide_content_categories), options);
    }

    /**
     * @param panel the rows of the menu, for the settings search
     */
    private OptionItem createMenuItem(int titleResId, int descriptionResId, Runnable onSelect, SettingsPanel panel) {
        OptionItem item = UiOptionItem.from(getContext().getString(titleResId), getContext().getString(descriptionResId), option -> onSelect.run());
        item.setMenu(true);
        item.setPanel(panel);
        return item;
    }
}
