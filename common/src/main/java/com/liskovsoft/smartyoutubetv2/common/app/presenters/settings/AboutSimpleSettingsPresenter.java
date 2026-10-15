package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

public class AboutSimpleSettingsPresenter extends BasePresenter<Void> {
    private static final String DONATION_URL = "https://buymeacoffee.com/edgan";
    private static final String FEEDBACK_URL = "https://github.com/cygnusx-1-org/electriceel/issues";
    private static final String RELEASES_URL = "https://github.com/cygnusx-1-org/electriceel/releases";
    private static final String GIT_REPOSITORY_URL = "https://github.com/cygnusx-1-org/electriceel";

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
        appendQrCode(settingsPresenter, R.string.donation, DONATION_URL);
        appendQrCode(settingsPresenter, R.string.feedback, FEEDBACK_URL);
        appendQrCode(settingsPresenter, R.string.releases, RELEASES_URL);
        appendQrCode(settingsPresenter, R.string.git_repository, GIT_REPOSITORY_URL);

        settingsPresenter.showDialog(mainTitle);
    }

    /**
     * A row that opens a panel with a QR code of the address
     */
    private void appendQrCode(AppDialogPresenter settingsPresenter, int titleResId, String url) {
        String title = getContext().getString(titleResId);

        settingsPresenter.appendSingleButton(UiOptionItem.from(title, option -> showQrCode(title, url)));
    }

    /**
     * The code has the address, so it isn't written out. Made on the device: it's there when the panel opens.
     */
    private void showQrCode(String title, String url) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        settingsPresenter.appendQrCode(title, url);

        // A single item would be opened right away instead of showing the panel with the picture
        settingsPresenter.enableExpandable(false);
        settingsPresenter.showDialog(title);
    }
}
