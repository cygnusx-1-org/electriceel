package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SettingsCardsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * The User interface section of the settings, as cards on its own screen. General is a screen of cards too: the user interface settings panel,
 * broken up.<br/>
 * Language and Country were the Language/Country section.
 */
public class MainUISectionSettingsPresenter extends BasePresenter<Void> implements SettingsCardsPresenter.Cards {
    private MainUISectionSettingsPresenter(Context context) {
        super(context);
    }

    public static MainUISectionSettingsPresenter instance(Context context) {
        return new MainUISectionSettingsPresenter(context);
    }

    public void show() {
        SettingsCardsPresenter.instance(getContext()).show(this);
    }

    @Override
    public String getTitle(Context context) {
        return context.getString(R.string.settings_main_ui);
    }

    @Override
    public List<SettingsItem> getItems(Context context) {
        List<SettingsItem> items = new ArrayList<>();
        items.add(new SettingsItem(context.getString(R.string.settings_general),
                () -> MainUIGeneralSettingsPresenter.instance(context).show(), R.drawable.settings_general));
        items.add(new SettingsItem(context.getString(R.string.dialog_select_language),
                () -> LanguageSettingsPresenter.instance(context).showLanguage(), R.drawable.settings_language));
        items.add(new SettingsItem(context.getString(R.string.dialog_select_country),
                () -> LanguageSettingsPresenter.instance(context).showCountry(), R.drawable.settings_language));
        items.add(new SettingsItem(context.getString(R.string.dearrow_provider),
                () -> DeArrowSettingsPresenter.instance(context).show(), R.drawable.settings_dearrow));

        return items;
    }
}
