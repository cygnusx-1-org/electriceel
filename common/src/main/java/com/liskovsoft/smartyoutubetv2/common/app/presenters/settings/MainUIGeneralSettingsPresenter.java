package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SettingsCardsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.HomeScreenPlaylistsData;

import java.util.ArrayList;
import java.util.List;

/**
 * The General card of the User interface settings: the cards of the user interface settings on their own screen (User interface - General).
 * It was the Options card, the general settings panel was broken up into it.
 */
public class MainUIGeneralSettingsPresenter extends BasePresenter<Void> implements SettingsCardsPresenter.Cards {
    private MainUIGeneralSettingsPresenter(Context context) {
        super(context);
    }

    public static MainUIGeneralSettingsPresenter instance(Context context) {
        return new MainUIGeneralSettingsPresenter(context);
    }

    public void show() {
        SettingsCardsPresenter.instance(getContext()).show(this);
    }

    @Override
    public String getTitle(Context context) {
        return context.getString(R.string.settings_general);
    }

    @Override
    public List<SettingsItem> getItems(Context context) {
        List<SettingsItem> items = new ArrayList<>();
        items.add(new SettingsItem(context.getString(R.string.boot_to_section),
                () -> MainUISettingsPresenter.instance(context).showBootToSection(), R.drawable.settings_boot_to_section));
        items.add(new SettingsItem(context.getString(R.string.side_panel_sections),
                () -> MainUISettingsPresenter.instance(context).showEnabledSections(), R.drawable.settings_sections));
        items.add(new SettingsItem(context.getString(R.string.context_menu),
                () -> MainUISettingsPresenter.instance(context).showContextMenuItems(), R.drawable.settings_context_menu));
        items.add(new SettingsItem(context.getString(R.string.settings_buttons_options),
                () -> MainUISettingsPresenter.instance(context).showButtons(), R.drawable.settings_top_buttons));
        items.add(new SettingsItem(context.getString(R.string.settings_cards_options),
                () -> MainUISettingsPresenter.instance(context).showCards(), R.drawable.settings_cards));
        items.add(new SettingsItem(context.getString(R.string.settings_time_options),
                () -> MainUISettingsPresenter.instance(context).showTime(), R.drawable.settings_time));
        items.add(new SettingsItem(context.getString(R.string.settings_channels_options),
                () -> MainUISettingsPresenter.instance(context).showChannels(), R.drawable.settings_channels));
        if (HomeScreenPlaylistsData.isSupported(context)) {
            items.add(new SettingsItem(context.getString(R.string.home_screen),
                    () -> MainUISettingsPresenter.instance(context).showHomeScreenChannels(), R.drawable.settings_home_screen_channels));
        }
        items.add(new SettingsItem(context.getString(R.string.settings_color_scheme_options),
                () -> MainUISettingsPresenter.instance(context).showColors(), R.drawable.settings_color_scheme));
        items.add(new SettingsItem(context.getString(R.string.settings_misc_options),
                () -> MainUISettingsPresenter.instance(context).showMisc(), R.drawable.settings_misc));
        items.add(new SettingsItem(context.getString(R.string.settings_scale_options),
                () -> MainUISettingsPresenter.instance(context).showScale(), R.drawable.settings_ui_scale));

        return items;
    }
}
