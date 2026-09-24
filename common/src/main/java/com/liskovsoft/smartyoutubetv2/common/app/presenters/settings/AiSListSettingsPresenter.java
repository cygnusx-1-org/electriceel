package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.AiSListManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AiSListFilterData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class AiSListSettingsPresenter extends BasePresenter<Void> {
    private static final int[][] SECTIONS = {
            {AiSListFilterData.SECTION_HOME, R.string.header_home},
            {AiSListFilterData.SECTION_SEARCH, R.string.title_search},
            {AiSListFilterData.SECTION_SUBSCRIPTIONS, R.string.header_subscriptions},
            {AiSListFilterData.SECTION_SUGGESTIONS, R.string.suggestions},
            {AiSListFilterData.SECTION_CHANNELS, R.string.header_channels},
            {AiSListFilterData.SECTION_HISTORY, R.string.header_history},
            {AiSListFilterData.SECTION_PLAYLISTS, R.string.header_playlists},
            {AiSListFilterData.SECTION_WATCH_LATER, R.string.aislist_watch_later}
    };
    private static final int[][] MARK_MODES = {
            {R.string.aislist_mark_off, AiSListFilterData.MARK_MODE_OFF},
            {R.string.aislist_mark_in_mark_only, AiSListFilterData.MARK_MODE_MARK_ONLY_SECTIONS},
            {R.string.aislist_mark_instead_of_hide, AiSListFilterData.MARK_MODE_ALL}
    };
    private static final int[][] MARK_COLORS = {
            {R.string.aislist_mark_color_off, AiSListFilterData.MARK_COLOR_OFF},
            {R.string.aislist_color_red, AiSListFilterData.MARK_COLOR_RED},
            {R.string.aislist_color_orange, AiSListFilterData.MARK_COLOR_ORANGE},
            {R.string.aislist_color_yellow, AiSListFilterData.MARK_COLOR_YELLOW},
            {R.string.aislist_color_green, AiSListFilterData.MARK_COLOR_GREEN},
            {R.string.aislist_color_blue, AiSListFilterData.MARK_COLOR_BLUE}
    };
    private final AiSListFilterData mFilterData;

    private AiSListSettingsPresenter(Context context) {
        super(context);
        mFilterData = AiSListFilterData.instance(context);
    }

    public static AiSListSettingsPresenter instance(Context context) {
        return new AiSListSettingsPresenter(context);
    }

    public void show(Runnable onFinish) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendListMenu(settingsPresenter, AiSListFilterData.LIST_BLOCKLIST, R.string.aislist_blocklist);
        appendListMenu(settingsPresenter, AiSListFilterData.LIST_WARNLIST, R.string.aislist_warnlist);
        appendStatus(settingsPresenter);
        appendLinks(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.aislist_provider), onFinish);
    }

    public void show() {
        show(null);
    }

    /**
     * A list's own menu: where it applies, whether it marks instead of hiding, and the marker color
     */
    private void appendListMenu(AppDialogPresenter settingsPresenter, int list, int titleResId) {
        String title = getContext().getString(titleResId);
        settingsPresenter.appendSingleButton(UiOptionItem.from(title, option -> showListMenu(list, title)));
    }

    private void showListMenu(int list, String title) {
        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());

        appendSectionsCategory(dialogPresenter, list);
        OptionItem[] markModes = appendMarkMode(dialogPresenter, list);
        appendMarkColor(dialogPresenter, list, markModes);

        dialogPresenter.showDialog(title);
    }

    private void appendSectionsCategory(AppDialogPresenter settingsPresenter, int list) {
        List<OptionItem> options = new ArrayList<>();

        // Everything is first. The other choices are greyed out while it's checked.
        OptionItem everything = UiOptionItem.from(getContext().getString(R.string.aislist_everything),
                optionItem -> mFilterData.setEverythingEnabled(list, optionItem.isSelected()),
                mFilterData.isEverythingEnabled(list));
        options.add(everything);

        for (int[] pair : SECTIONS) {
            OptionItem section = UiOptionItem.from(getSectionTitle(pair[0], pair[1]),
                    optionItem -> mFilterData.setHideEnabled(list, pair[0], optionItem.isSelected()),
                    mFilterData.isSectionChecked(list, pair[0]));
            section.setDisabledBy(everything);
            options.add(section);
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.aislist_sections), options);
    }

    private String getSectionTitle(int section, int titleResId) {
        String title = getContext().getString(titleResId);

        return AiSListFilterData.isMarkOnly(section) ? getContext().getString(R.string.aislist_mark_only, title) : title;
    }

    /**
     * @return the modes that mark videos
     */
    private OptionItem[] appendMarkMode(AppDialogPresenter settingsPresenter, int list) {
        List<OptionItem> options = new ArrayList<>();
        List<OptionItem> markingModes = new ArrayList<>();

        for (int[] pair : MARK_MODES) {
            String description = pair[1] == AiSListFilterData.MARK_MODE_ALL ? getContext().getString(R.string.aislist_mark_instead_of_hide_desc) : null;
            OptionItem option = UiOptionItem.from(getContext().getString(pair[0]), description,
                    optionItem -> mFilterData.setMarkMode(list, pair[1]),
                    mFilterData.getMarkMode(list) == pair[1]);
            options.add(option);

            if (pair[1] != AiSListFilterData.MARK_MODE_OFF) {
                markingModes.add(option);
            }
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.aislist_mark_videos), options);
        return markingModes.toArray(new OptionItem[0]);
    }

    /**
     * Greyed out while the list's marking is off
     */
    private void appendMarkColor(AppDialogPresenter settingsPresenter, int list, OptionItem[] markModes) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : MARK_COLORS) {
            OptionItem option = UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mFilterData.setMarkColor(list, pair[1]),
                    mFilterData.getMarkColor(list) == pair[1]);
            option.setRequired(markModes);
            options.add(option);
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.aislist_mark_color), options);
    }

    private void appendStatus(AppDialogPresenter settingsPresenter) {
        AiSListManager manager = AiSListManager.instance(getContext());
        manager.loadIfNeeded(); // e.g. the download at start failed

        String status = manager.getUpdatedTimeMs() > 0 ?
                getContext().getString(R.string.aislist_status, manager.getBlocklistSize(), manager.getWarnlistSize()) :
                getContext().getString(R.string.aislist_status_not_loaded);

        settingsPresenter.appendSingleButton(UiOptionItem.from(status, option -> {}));
    }

    private void appendLinks(AppDialogPresenter settingsPresenter) {
        OptionItem webSiteOption = UiOptionItem.from(getContext().getString(R.string.aislist_about),
                option -> Utils.openLink(getContext(), getContext().getString(R.string.aislist_provider_url)));

        settingsPresenter.appendSingleButton(webSiteOption);
    }
}
