package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SettingsCardsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * The Search section of the settings, as cards on its own screen
 */
public class SearchSectionSettingsPresenter extends BasePresenter<Void> implements SettingsCardsPresenter.Cards {
    private SearchSectionSettingsPresenter(Context context) {
        super(context);
    }

    public static SearchSectionSettingsPresenter instance(Context context) {
        return new SearchSectionSettingsPresenter(context);
    }

    public void show() {
        SettingsCardsPresenter.instance(getContext()).show(this);
    }

    @Override
    public String getTitle(Context context) {
        return context.getString(R.string.settings_search);
    }

    @Override
    public List<SettingsItem> getItems(Context context) {
        List<SettingsItem> items = new ArrayList<>();
        items.add(new SettingsItem(context.getString(R.string.settings_general),
                () -> SearchSettingsPresenter.instance(context).showGeneral(), R.drawable.settings_general));
        items.add(new SettingsItem(context.getString(R.string.settings_voice_engine),
                () -> SearchSettingsPresenter.instance(context).showVoiceEngine(), R.drawable.settings_search));

        return items;
    }
}
