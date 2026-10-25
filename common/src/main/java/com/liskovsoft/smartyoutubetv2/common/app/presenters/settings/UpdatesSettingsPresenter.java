package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.webkit.WebViewCompat;

import com.liskovsoft.appupdatechecker2.AppUpdateChecker;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.QrCodePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ReleaseNotesPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AppUpdatePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.GitHubReleases;
import com.liskovsoft.smartyoutubetv2.common.misc.GitHubReleases.Release;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.List;

import io.reactivex.disposables.Disposable;

/**
 * The Updates section of the settings (was in About)
 */
public class UpdatesSettingsPresenter extends BasePresenter<Void> {
    /**
     * Of every panel: a second press while the list loads is ignored
     */
    private static Disposable sReleasesAction;
    private final AppUpdateChecker mUpdateChecker;
    /**
     * Of the panel as the settings opened it, e.g. "Updates - Release notes": the same when it's opened again after the notes
     */
    private boolean mIsNestedTitles;

    private UpdatesSettingsPresenter(Context context) {
        super(context);

        mUpdateChecker = new AppUpdateChecker(getContext(), null);
    }

    public static UpdatesSettingsPresenter instance(Context context) {
        return new UpdatesSettingsPresenter(context);
    }

    public void show() {
        show(false);
    }

    /**
     * @param isReleaseNotesFocused its row has the focus, as when the list was opened from it (e.g. back from the notes)
     */
    private void show(boolean isReleaseNotesFocused) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.check_updates_auto),
                optionItem -> mUpdateChecker.setUpdateCheckEnabled(optionItem.isSelected()),
                mUpdateChecker.isUpdateCheckEnabled()));

        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.check_for_updates),
                option -> AppUpdatePresenter.instance(getContext()).start(true)));

        OptionItem releaseNotesItem = UiOptionItem.from(getContext().getString(R.string.release_notes), option -> loadReleases());
        settingsPresenter.appendSingleButton(releaseNotesItem);

        if (isReleaseNotesFocused) {
            settingsPresenter.setFocusedItem(releaseNotesItem);
        }

        settingsPresenter.showDialog(getContext().getString(R.string.settings_updates));
        mIsNestedTitles = settingsPresenter.isNestedTitlesEnabled();
    }

    private void loadReleases() {
        if (RxHelper.isAnyActionRunning(sReleasesAction)) {
            return;
        }

        sReleasesAction = RxHelper.execute(GitHubReleases.latestObserve(getContext()), releases -> showReleases(releases, null),
                error -> {
                    Context context = getContext();

                    if (context != null) {
                        // E.g. a timeout's message is null
                        String reason = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
                        MessageHelpers.showLongMessage(context, context.getString(R.string.release_notes_failed, reason));
                    }
                });
    }

    /**
     * Over the Updates panel: Back goes to it
     *
     * @param focused its row has the focus, e.g. the release whose notes were open: null for the first one
     */
    private void showReleases(List<Release> releases, @Nullable Release focused) {
        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());

        // Closed while loading
        if (!dialogPresenter.isDialogShown()) {
            return;
        }

        if (releases.isEmpty()) {
            MessageHelpers.showMessage(getContext(), R.string.release_notes_none);
            return;
        }

        for (Release release : releases) {
            OptionItem item = UiOptionItem.from(release.version, option -> showNotes(release, releases));
            dialogPresenter.appendSingleButton(item);

            if (release == focused) {
                dialogPresenter.setFocusedItem(item);
            }
        }

        dialogPresenter.showDialog(getContext().getString(R.string.release_notes));
    }

    private void showNotes(Release release, List<Release> releases) {
        // The notes are a web page: without a WebView, the release's page on a phone
        if (WebViewCompat.getCurrentWebViewPackage(getContext()) == null) {
            AppDialogPresenter.instance(getContext()).closeDialog();
            QrCodePresenter.instance(getContext()).show(release.pageUrl);
            return;
        }

        // Back to the list on this release, over the Updates panel
        ReleaseNotesPresenter.instance(getContext()).show(release, () -> showOverMenu(() -> showReleases(releases, release)));
    }

    private void showOverMenu(Runnable showList) {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());

        if (presenter.isDialogShown()) {
            showList.run();
            return;
        }

        // Before the menu is shown: a dialog that's still there shows it right away.
        // Posted: the menu is added to the stack first.
        presenter.setOnStart(() -> Utils.post(showList));
        presenter.enableNestedTitles(mIsNestedTitles);
        try {
            show(true);
        } finally {
            presenter.enableNestedTitles(false);
        }
    }
}
