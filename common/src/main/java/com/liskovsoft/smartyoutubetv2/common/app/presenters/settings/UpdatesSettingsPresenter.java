package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.appupdatechecker2.AppUpdateChecker;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AppUpdatePresenter;

/**
 * The Updates section of the settings (was in About)
 */
public class UpdatesSettingsPresenter extends BasePresenter<Void> {
    private final AppUpdateChecker mUpdateChecker;

    private UpdatesSettingsPresenter(Context context) {
        super(context);

        mUpdateChecker = new AppUpdateChecker(getContext(), null);
    }

    public static UpdatesSettingsPresenter instance(Context context) {
        return new UpdatesSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.check_updates_auto),
                optionItem -> mUpdateChecker.setUpdateCheckEnabled(optionItem.isSelected()),
                mUpdateChecker.isUpdateCheckEnabled()));

        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.check_for_updates),
                option -> AppUpdatePresenter.instance(getContext()).start(true)));

        settingsPresenter.showDialog(getContext().getString(R.string.settings_updates));
    }
}
