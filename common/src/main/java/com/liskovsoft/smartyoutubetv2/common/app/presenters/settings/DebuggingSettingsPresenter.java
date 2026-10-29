package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.DataSubmitter;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.JsonHighlighter;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlayerFailureLog;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.VideoStartLog;
import com.liskovsoft.smartyoutubetv2.common.prefs.DebuggingData;

import java.text.DateFormat;
import java.util.Date;

import io.reactivex.disposables.Disposable;

/**
 * The Debugging section of the settings: Submit data sends the diagnostics (see DiagnosticsReport)
 */
public class DebuggingSettingsPresenter extends BasePresenter<Void> {
    /**
     * Of every panel: a second press while one is sent is ignored
     */
    private static Disposable sSubmitAction;
    private static Disposable sPreviewAction;
    private static final String TAG = DebuggingSettingsPresenter.class.getSimpleName();
    private static final int MAX_PREVIEW_CHARS = 20_000;
    private final DebuggingData mDebuggingData;

    private DebuggingSettingsPresenter(Context context) {
        super(context);

        mDebuggingData = DebuggingData.instance(context);
    }

    public static DebuggingSettingsPresenter instance(Context context) {
        return new DebuggingSettingsPresenter(context);
    }

    /**
     * Once the failures and the video starts are counted (the logs are read in the background)
     */
    public void show() {
        Context context = getContext();

        if (context == null) {
            return;
        }

        PlayerFailureLog log = PlayerFailureLog.instance(context);
        VideoStartLog videoStarts = VideoStartLog.instance(context);

        RxHelper.execute(RxHelper.fromCallable(() -> new int[] {log.readAll().size(), videoStarts.readAll().size()}),
                counts -> showPanel(counts[0], counts[1]), error -> showPanel(0, 0));
    }

    private void showPanel(int failureCount, int videoStartCount) {
        Context context = getContext();

        if (context == null) {
            return;
        }

        String host = Uri.parse(context.getString(R.string.submit_data_url)).getHost();
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(context);

        // One line for both counts: the summary shows 4 lines at most, and the last submission's is below
        String description = context.getString(R.string.submit_data_desc, host)
                + "\n" + context.getString(R.string.submit_data_recorded, failureCount, videoStartCount);
        String lastId = mDebuggingData.getLastSubmissionId();

        if (lastId != null) {
            String lastTime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(mDebuggingData.getLastSubmissionTime()));
            description += "\n" + context.getString(R.string.submit_data_last, lastId, lastTime);
        }

        settingsPresenter.appendSingleButton(UiOptionItem.from(context.getString(R.string.submit_data), description,
                option -> submit()));

        if (lastId != null) {
            settingsPresenter.appendSingleButton(UiOptionItem.from(context.getString(R.string.submit_data_last_qr),
                    context.getString(R.string.submit_data_last_qr_desc, lastId),
                    option -> showSubmitted(lastId)));
        }

        settingsPresenter.appendSingleButton(UiOptionItem.from(context.getString(R.string.submit_data_preview),
                context.getString(R.string.submit_data_preview_desc),
                option -> preview()));

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.submit_data_include_video_ids),
                context.getString(R.string.submit_data_include_video_ids_desc),
                option -> mDebuggingData.setVideoIdsIncluded(option.isSelected()),
                mDebuggingData.isVideoIdsIncluded()));

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.start_graph),
                context.getString(R.string.start_graph_desc),
                option -> mDebuggingData.setStartGraphShown(option.isSelected()),
                mDebuggingData.isStartGraphShown()));

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.submit_data_on_startup),
                context.getString(R.string.submit_data_on_startup_desc),
                option -> {
                    mDebuggingData.setAutoSubmitEnabled(option.isSelected());
                    DataSubmitter.autoSubmit(context); // scheduled, or cancelled
                },
                mDebuggingData.isAutoSubmitEnabled()));

        settingsPresenter.showDialog(context.getString(R.string.settings_debugging));
    }

    private void submit() {
        if (RxHelper.isAnyActionRunning(sSubmitAction)) {
            return;
        }

        Context context = getContext();

        if (context == null) {
            return;
        }

        MessageHelpers.showMessage(context, R.string.submit_data_sending);

        sSubmitAction = RxHelper.execute(DataSubmitter.submitObserve(context), this::showSubmitted,
                error -> {
                    Context current = getContext();

                    if (current != null) {
                        // E.g. a timeout's message is null
                        String reason = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
                        MessageHelpers.showLongMessage(current, current.getString(R.string.submit_data_failed, reason));
                    }
                });
    }

    private void preview() {
        Context context = getContext();

        if (context == null || RxHelper.isAnyActionRunning(sPreviewAction)) {
            return;
        }

        sPreviewAction = RxHelper.execute(DataSubmitter.previewObserve(context), this::showPreview,
                error -> Log.e(TAG, "Can't build the preview: %s", error.getMessage()));
    }

    /**
     * Cut short: a dialog this long is slow on a TV
     */
    private void showPreview(String json) {
        Context context = getContext();

        if (context == null) {
            return;
        }

        boolean isCut = json.length() > MAX_PREVIEW_CHARS;
        CharSequence text = JsonHighlighter.highlight(isCut ? json.substring(0, MAX_PREVIEW_CHARS) : json,
                ContextCompat.getColor(context, R.color.json_key), ContextCompat.getColor(context, R.color.json_string),
                ContextCompat.getColor(context, R.color.json_number), ContextCompat.getColor(context, R.color.json_literal));

        if (isCut) {
            // Not colored: it isn't JSON
            text = TextUtils.concat(text, "\n" + context.getResources().getQuantityString(R.plurals.submit_data_preview_more,
                    json.length() - MAX_PREVIEW_CHARS, json.length() - MAX_PREVIEW_CHARS));
        }

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(context);
        // No links: JSON is full of names that read as addresses (e.g. the decoder OMX.google.vp9.decoder)
        dialogPresenter.appendLongTextCategory(context.getString(R.string.submit_data_preview), UiOptionItem.from(text), false);
        dialogPresenter.showDialog(context.getString(R.string.submit_data_preview));
    }

    private void showSubmitted(String id) {
        Context context = getContext();

        if (context == null) {
            return;
        }

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(context);
        // The ID is the code's title: as a category of its own it would be a row to open
        String title = context.getString(R.string.submit_data_done, id) + "\n" + context.getString(R.string.submit_data_issue_qr);
        // Typing the ID on a TV is hard: a phone scans it into a new issue
        dialogPresenter.appendQrCode(title, getIssueUrl(context, id));
        // A single item would be opened right away instead of showing the panel with the picture
        dialogPresenter.enableExpandable(false);
        dialogPresenter.showDialog(context.getString(R.string.submit_data));
    }

    private static String getIssueUrl(Context context, String id) {
        return context.getString(R.string.submit_data_issue_url, Uri.encode(context.getString(R.string.submit_data_issue_body, id)));
    }
}
