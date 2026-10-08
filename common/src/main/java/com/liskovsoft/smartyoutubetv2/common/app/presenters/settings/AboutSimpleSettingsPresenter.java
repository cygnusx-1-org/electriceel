package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
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

        // In the order of the full About
        appendQrCode(settingsPresenter, R.string.donation, R.drawable.qr_code_donation);
        appendQrCode(settingsPresenter, R.string.feedback, R.drawable.qr_code_feedback);
        appendQrCode(settingsPresenter, R.string.releases, R.drawable.qr_code_releases);
        appendQrCode(settingsPresenter, R.string.git_repository, R.drawable.qr_code_git_repository);

        settingsPresenter.showDialog(mainTitle);
    }

    /**
     * A row that opens a panel with a QR code of the address
     */
    private void appendQrCode(AppDialogPresenter settingsPresenter, int titleResId, int qrCodeResId) {
        String title = getContext().getString(titleResId);

        settingsPresenter.appendSingleButton(UiOptionItem.from(title, option -> showQrCode(title, qrCodeResId)));
    }

    /**
     * The code has the address, so it isn't written out. Pre-rendered: it's there when the panel opens.
     */
    private void showQrCode(String title, int qrCodeResId) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        settingsPresenter.appendImage(title, qrCodeResId);

        // A single item would be opened right away instead of showing the panel with the picture
        settingsPresenter.enableExpandable(false);
        settingsPresenter.showDialog(title);
    }
}
