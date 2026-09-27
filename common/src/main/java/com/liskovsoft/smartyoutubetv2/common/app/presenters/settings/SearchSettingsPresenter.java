package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.ATVBridgePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AmazonBridgePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;

import java.util.ArrayList;
import java.util.List;

public class SearchSettingsPresenter extends BasePresenter<Void> {
    private final SearchData mSearchData;

    public SearchSettingsPresenter(Context context) {
        super(context);
        mSearchData = SearchData.instance(context);
    }

    public static SearchSettingsPresenter instance(Context context) {
        return new SearchSettingsPresenter(context);
    }

    /**
     * The General card of the Search settings (was Misc). The disables were the Disables card.
     */
    public void showGeneral() {
        List<OptionItem> options = new ArrayList<>();

        //options.add(UiOptionItem.from(getContext().getString(R.string.disable_popular_searches),
        //        option -> mSearchData.disablePopularSearches(option.isSelected()),
        //        mSearchData.isPopularSearchesDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.search_background_playback),
                option -> mSearchData.setTempBackgroundModeEnabled(option.isSelected()),
                mSearchData.isTempBackgroundModeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.instant_voice_search),
                option -> mSearchData.setInstantVoiceSearchEnabled(option.isSelected()),
                mSearchData.isInstantVoiceSearchEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.focus_on_search_results),
                option -> mSearchData.setFocusOnResultsEnabled(option.isSelected()),
                mSearchData.isFocusOnResultsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.keyboard_auto_show),
                option -> mSearchData.setKeyboardAutoShowEnabled(option.isSelected()),
                mSearchData.isKeyboardAutoShowEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.keyboard_fix),
                option -> mSearchData.setKeyboardFixEnabled(option.isSelected()),
                mSearchData.isKeyboardFixEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.typing_corrections),
                option -> mSearchData.setTypingCorrectionDisabled(option.isSelected()),
                mSearchData.isTypingCorrectionDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.disable_search_history),
                option -> mSearchData.setSearchHistoryDisabled(option.isSelected()),
                mSearchData.isSearchHistoryDisabled()));

        // Was in About
        OptionItem globalSearch = UiOptionItem.from(getContext().getString(R.string.enable_voice_search), option -> startBridgePresenter());
        globalSearch.setMenu(true);
        options.add(globalSearch);

        showCheckedList(R.string.settings_general, options, () -> {
            if (mSearchData.isSearchHistoryDisabled()) {
                MediaServiceManager.instance().clearSearchHistory();
            }
        });
    }

    private void startBridgePresenter() {
        MessageHelpers.showLongMessage(getContext(), R.string.enable_voice_search_desc);

        ATVBridgePresenter atvPresenter = ATVBridgePresenter.instance(getContext());
        atvPresenter.runBridgeInstaller(true);
        atvPresenter.unhold();

        AmazonBridgePresenter amazonPresenter = AmazonBridgePresenter.instance(getContext());
        amazonPresenter.runBridgeInstaller(true);
        amazonPresenter.unhold();
    }

    /**
     * The Voice engine card of the Search settings
     */
    public void showVoiceEngine() {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.speech_recognizer_system, SearchData.SPEECH_RECOGNIZER_SYSTEM},
                {R.string.speech_recognizer_external_1, SearchData.SPEECH_RECOGNIZER_INTENT},
                {R.string.speech_recognizer_external_2, SearchData.SPEECH_RECOGNIZER_GOTEV}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mSearchData.setSpeechRecognizerType(pair[1]),
                    mSearchData.getSpeechRecognizerType() == pair[1]));
        }

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        String title = getContext().getString(R.string.settings_voice_engine);
        settingsPresenter.appendRadioCategory(title, options);
        settingsPresenter.showDialog(title);
    }

    private void showCheckedList(int titleResId, List<OptionItem> options, Runnable onFinish) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        String title = getContext().getString(titleResId);
        settingsPresenter.appendCheckedCategory(title, options);
        settingsPresenter.showDialog(title, onFinish);
    }
}
