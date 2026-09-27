package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SettingsCardsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * The System section of the settings (was General), as cards on its own screen. General is a screen of cards too: the general settings panel,
 * broken up.
 */
public class SystemSectionSettingsPresenter extends BasePresenter<Void> implements SettingsCardsPresenter.Cards {
    private SystemSectionSettingsPresenter(Context context) {
        super(context);
    }

    public static SystemSectionSettingsPresenter instance(Context context) {
        return new SystemSectionSettingsPresenter(context);
    }

    public void show() {
        SettingsCardsPresenter.instance(getContext()).show(this);
    }

    @Override
    public String getTitle(Context context) {
        return context.getString(R.string.settings_system);
    }

    @Override
    public List<SettingsItem> getItems(Context context) {
        List<SettingsItem> items = new ArrayList<>();
        items.add(new SettingsItem(context.getString(R.string.settings_general),
                () -> SystemGeneralSettingsPresenter.instance(context).show(), R.drawable.settings_general));
        items.add(new SettingsItem(context.getString(R.string.settings_dns),
                () -> GeneralSettingsPresenter.instance(context).showDns(), R.drawable.settings_dns));
        items.add(new SettingsItem(context.getString(R.string.settings_remote_control),
                () -> RemoteControlSettingsPresenter.instance(context).show(), R.drawable.settings_cast));
        items.add(new SettingsItem(context.getString(R.string.app_backup_restore),
                () -> BackupSettingsPresenter.instance(context).show(), R.drawable.settings_backup));

        return items;
    }
}
