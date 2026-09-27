package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SettingsCardsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * The General card of the Player settings: the cards of the player settings on their own screen (Player - General).
 * It was the Options card, the general settings panel was broken up into it.
 */
public class PlayerGeneralSettingsPresenter extends BasePresenter<Void> implements SettingsCardsPresenter.Cards {
    private PlayerGeneralSettingsPresenter(Context context) {
        super(context);
    }

    public static PlayerGeneralSettingsPresenter instance(Context context) {
        return new PlayerGeneralSettingsPresenter(context);
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
        items.add(new SettingsItem(context.getString(R.string.player_ui_hide_behavior),
                () -> PlayerSettingsPresenter.instance(context).showUIAutoHide(), R.drawable.settings_auto_hide_ui));
        items.add(new SettingsItem(context.getString(R.string.settings_button_options),
                () -> PlayerSettingsPresenter.instance(context).showButtons(), R.drawable.settings_player_buttons));
        items.add(new SettingsItem(context.getString(R.string.settings_show_options),
                () -> PlayerSettingsPresenter.instance(context).showShows(), R.drawable.settings_show));
        items.add(new SettingsItem(context.getString(R.string.settings_places),
                () -> PlayerSettingsPresenter.instance(context).showPlaces(), R.drawable.settings_place_left));
        items.add(new SettingsItem(context.getString(R.string.settings_seek_options),
                () -> PlayerSettingsPresenter.instance(context).showSeeks(), R.drawable.settings_seek));
        items.add(new SettingsItem(context.getString(R.string.settings_misc_options),
                () -> PlayerSettingsPresenter.instance(context).showMisc(), R.drawable.settings_misc));
        items.add(new SettingsItem(context.getString(R.string.player_network_stack),
                () -> PlayerSettingsPresenter.instance(context).showNetworkEngine(), R.drawable.settings_network_engine));
        items.add(new SettingsItem(context.getString(R.string.player_pixel_ratio),
                () -> PlayerSettingsPresenter.instance(context).showPixelRatio(), R.drawable.settings_pixel_ratio));
        items.add(new SettingsItem(context.getString(R.string.action_repeat_mode),
                () -> PlayerSettingsPresenter.instance(context).showPlaybackMode(), R.drawable.settings_playback_mode));
        items.add(new SettingsItem(context.getString(R.string.player_sleep_timer),
                () -> PlayerSettingsPresenter.instance(context).showSleepTimer(), R.drawable.settings_sleep_timer));
        items.add(new SettingsItem(context.getString(R.string.player_tweaks),
                () -> PlayerSettingsPresenter.instance(context).showDeveloper(), R.drawable.settings_developer));
        items.add(new SettingsItem(context.getString(R.string.settings_disable_options),
                () -> PlayerSettingsPresenter.instance(context).showDisables(), R.drawable.settings_disable));
        items.add(new SettingsItem(context.getString(R.string.settings_unlock_options),
                () -> PlayerSettingsPresenter.instance(context).showUnlocks(), R.drawable.settings_unlock));
        items.add(new SettingsItem(context.getString(R.string.settings_frame_options),
                () -> PlayerSettingsPresenter.instance(context).showFrameDrops(), R.drawable.settings_frame));
        items.add(new SettingsItem(context.getString(R.string.settings_fix_options),
                () -> PlayerSettingsPresenter.instance(context).showFixes(), R.drawable.settings_fix));

        return items;
    }
}
