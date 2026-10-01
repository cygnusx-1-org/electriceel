package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

public class AboutSimpleSettingsPresenter extends BasePresenter<Void> {
    public AboutSimpleSettingsPresenter(Context context) {
        super(context);
    }

    public static AboutSimpleSettingsPresenter instance(Context context) {
        return new AboutSimpleSettingsPresenter(context);
    }

    /**
     * The updates are in Updates and the global search in Search|General
     */
    public void show() {
        String mainTitle = String.format("%s %s",
                getContext().getString(R.string.app_name),
                AppInfoHelpers.getAppVersionName(getContext()));

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendRepository(settingsPresenter);

        // A single item would be opened right away instead of showing the panel with the name and the version
        settingsPresenter.enableExpandable(false);
        settingsPresenter.showDialog(mainTitle);
    }

    /**
     * A QR code of the address, in the panel. The code has the address, so it isn't written out.
     * Pre-rendered: it's there when the panel opens.
     */
    private void appendRepository(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendImage(getContext().getString(R.string.git_repository), R.drawable.qr_code_git_repository);
    }
}
