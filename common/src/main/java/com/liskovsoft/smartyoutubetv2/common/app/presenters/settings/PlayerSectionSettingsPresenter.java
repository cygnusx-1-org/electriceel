package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SettingsCardsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * The Player section of the settings, as cards on its own screen. General is a screen of cards too: the player settings panel, broken up.
 */
public class PlayerSectionSettingsPresenter extends BasePresenter<Void> implements SettingsCardsPresenter.Cards {
    private PlayerSectionSettingsPresenter(Context context) {
        super(context);
    }

    public static PlayerSectionSettingsPresenter instance(Context context) {
        return new PlayerSectionSettingsPresenter(context);
    }

    public void show() {
        SettingsCardsPresenter.instance(getContext()).show(this);
    }

    @Override
    public String getTitle(Context context) {
        return context.getString(R.string.settings_player);
    }

    @Override
    public List<SettingsItem> getItems(Context context) {
        List<SettingsItem> items = new ArrayList<>();
        items.add(new SettingsItem(context.getString(R.string.settings_general),
                () -> PlayerGeneralSettingsPresenter.instance(context).show(), R.drawable.settings_general));
        items.add(new SettingsItem(context.getString(R.string.settings_video),
                () -> PlayerSettingsPresenter.instance(context).showVideo(), R.drawable.settings_video));
        items.add(new SettingsItem(context.getString(R.string.settings_audio),
                () -> PlayerSettingsPresenter.instance(context).showAudio(), R.drawable.settings_audio));
        // Don't add afr support check here.
        // Users want even fake afr settings.
        items.add(new SettingsItem(context.getString(R.string.auto_frame_rate),
                () -> AutoFrameRateSettingsPresenter.instance(context).show(), R.drawable.settings_afr));
        items.add(new SettingsItem(context.getString(R.string.subtitle_category_title),
                () -> SubtitleSettingsPresenter.instance(context).show(), R.drawable.settings_subtitles));

        return items;
    }
}
