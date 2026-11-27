package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.settingssearch.SettingEntry;
import com.liskovsoft.smartyoutubetv2.common.settingssearch.SettingsCatalog;
import com.liskovsoft.smartyoutubetv2.common.settingssearch.SettingsSearch;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Find a setting" card of the Settings section: a question, then the settings that answer it, as rows that open them (the
 * panel with the row focused, see SettingsCatalog.open). Found on the device (SettingsSearch): the best ones on top.
 */
public class SettingsSearchPresenter extends BasePresenter<Void> {
    private static final String TAG = SettingsSearchPresenter.class.getSimpleName();
    public static final int SEARCH_ID = 153;
    private static final int MAX_MATCHES = 25;
    /**
     * The best matches, on top of the others
     */
    private static final int MAX_PICKS = 3;
    @SuppressLint("StaticFieldLeak")
    private static SettingsSearchPresenter sInstance;
    private String mQuestion;
    private SettingsCatalog mCatalog;
    private List<SettingEntry> mMatches;

    private SettingsSearchPresenter(Context context) {
        super(context);
    }

    public static SettingsSearchPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new SettingsSearchPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void show() {
        askQuestion(null);
    }

    /**
     * @param previous shown again when the question is cancelled
     */
    private void askQuestion(@Nullable String previous) {
        Context context = getContext();

        if (context == null) {
            return;
        }

        boolean[] isSearched = {false};
        SimpleEditDialog.showSearch(context, context.getString(R.string.find_setting), context.getString(R.string.find_setting_hint),
                previous != null ? previous : "", question -> {
                    if (question.trim().isEmpty()) {
                        return false;
                    }

                    isSearched[0] = true;
                    search(question.trim());
                    return true;
                }, () -> {
                    if (!isSearched[0] && previous != null && mQuestion != null) {
                        showResults();
                    }
                });
    }

    private void search(String question) {
        Context context = getContext();

        if (context == null) {
            return;
        }

        mQuestion = question;

        try {
            mCatalog = SettingsCatalog.get(context);
        } catch (RuntimeException e) {
            Log.e(TAG, "Can't list the settings: " + e);
            MessageHelpers.showMessage(context, e.getMessage());
            return;
        }

        mMatches = SettingsSearch.find(mCatalog.getEntries(), question, MAX_MATCHES);

        showResults();
    }

    private void showResults() {
        Context context = getContext();

        if (context == null || mQuestion == null || mCatalog == null) {
            return;
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        OptionItem focused = null;

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.find_setting_new), mQuestion, option -> {
            // The text field is behind the dialog otherwise, and the dialog keeps the keys
            dialog.closeDialog();
            askQuestion(mQuestion);
        }));

        List<SettingEntry> picks = new ArrayList<>(mMatches.subList(0, Math.min(MAX_PICKS, mMatches.size())));

        for (SettingEntry entry : picks) {
            OptionItem row = createRow(context, entry);
            dialog.appendSingleButton(row);

            if (focused == null) {
                focused = row;
            }
        }

        List<SettingEntry> matches = new ArrayList<>(mMatches);
        matches.removeAll(picks);

        if (picks.isEmpty()) {
            dialog.appendSingleButton(note(context.getString(R.string.find_setting_none, mQuestion)));
        } else if (!matches.isEmpty()) {
            dialog.appendSingleButton(note(context.getString(R.string.find_setting_more)));

            for (SettingEntry entry : matches) {
                dialog.appendSingleButton(createRow(context, entry));
            }
        }

        if (focused != null) {
            dialog.setFocusedItem(focused);
        }

        dialog.enableExpandable(false);
        dialog.setId(SEARCH_ID);
        dialog.showDialog(context.getString(R.string.find_setting));
    }

    private OptionItem createRow(Context context, SettingEntry entry) {
        // Where it is only: the list is kept between searches, the values in it could be old
        String where = TextUtils.join(" › ", entry.path);

        return UiOptionItem.from(entry.title, where, option -> {
            AppDialogPresenter dialog = AppDialogPresenter.instance(context);

            if (!SettingsCatalog.open(context, entry, dialog::closeDialog)) {
                // The settings changed since they were listed
                SettingsCatalog.clearCache();
                MessageHelpers.showMessage(context, R.string.find_setting_gone);
            }
        });
    }

    private static OptionItem note(String text) {
        UiOptionItem item = (UiOptionItem) UiOptionItem.from(text);
        item.setNote(true);
        return item;
    }
}
