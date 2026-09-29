package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;

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
        appendQrCodeButton(settingsPresenter, R.string.privacy_policy, R.string.privacy_policy_url);
        appendQrCodeButton(settingsPresenter, R.string.terms_of_service, R.string.terms_of_service_url);

        // A single item would be opened right away instead of showing the panel with the name and the version
        settingsPresenter.enableExpandable(false);
        settingsPresenter.showDialog(mainTitle);
    }

    /**
     * A QR code of the address, in the panel. The code has the address, so it isn't written out.
     */
    private void appendRepository(AppDialogPresenter settingsPresenter) {
        AppDialogUtil.appendQrCode(settingsPresenter, getContext().getString(R.string.git_repository),
                getContext().getString(R.string.git_repository_url));
    }

    /**
     * A button that opens the QR code of the address in a panel of its own: one code on the screen at a time
     */
    private void appendQrCodeButton(AppDialogPresenter settingsPresenter, int titleResId, int urlResId) {
        String title = getContext().getString(titleResId);

        settingsPresenter.appendSingleButton(UiOptionItem.from(title,
                option -> AppDialogUtil.showQrCodeDialog(getContext(), title, getContext().getString(urlResId))));
    }
}
