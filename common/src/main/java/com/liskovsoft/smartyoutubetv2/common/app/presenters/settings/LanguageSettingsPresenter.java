package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.locale.LocaleUtility;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.LocaleData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class LanguageSettingsPresenter extends BasePresenter<Void> {
    private final LocaleData mLocaleData;

    public LanguageSettingsPresenter(Context context) {
        super(context);
        mLocaleData = LocaleData.instance(context);
    }

    public static LanguageSettingsPresenter instance(Context context) {
        return new LanguageSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendLanguageCategory(settingsPresenter);
        appendCountryCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_language_country));
    }

    /**
     * The Language card of the User interface settings
     */
    public void showLanguage() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendLanguageCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.dialog_select_language));
    }

    /**
     * The Country card of the User interface settings
     */
    public void showCountry() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendCountryCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.dialog_select_country));
    }

    private void appendLanguageCategory(AppDialogPresenter settingsPresenter) {
        Map<String, String> languages = getSupportedLanguages();
        String language = mLocaleData.getLanguage();
        String languageTitle = "";

        List<OptionItem> options = new ArrayList<>();

        for (Entry<String, String> entry : languages.entrySet()) {
            if (entry.getValue().equals(language)) {
                languageTitle = String.format(" (%s)", entry.getKey());
            }

            options.add(UiOptionItem.from(
                    entry.getKey(),
                    option -> {
                        // In use at once: the activities are recreated in it when the dialog is closed (see MotherActivity)
                        mLocaleData.setLanguage(entry.getValue());
                        //settingsPresenter.closeDialog(); // sometimes cause crashes
                    },
                    entry.getValue().equals(language)));
        }

        settingsPresenter.appendRadioCategory(
                getContext().getString(R.string.dialog_select_language) + languageTitle, options);
    }

    private void appendCountryCategory(AppDialogPresenter settingsPresenter) {
        Map<String, String> countries = getSupportedCountries();
        String country = mLocaleData.getCountry();
        String countryTitle = "";

        List<OptionItem> options = new ArrayList<>();

        for (Entry<String, String> entry : countries.entrySet()) {
            if (entry.getValue().equals(country)) {
                countryTitle = String.format(" (%s)", entry.getKey());
            }

            options.add(UiOptionItem.from(
                    entry.getKey(),
                    option -> {
                        mLocaleData.setCountry(entry.getValue());
                        //settingsPresenter.closeDialog(); // sometimes cause crashes
                    },
                    entry.getValue().equals(country)));
        }

        settingsPresenter.appendRadioCategory(
                getContext().getString(R.string.dialog_select_country) + countryTitle, options);
    }

    /**
     * Gets map of Human readable locale names and their respective lang codes
     * @return locale name/code map
     */
    private Map<String, String> getSupportedLanguages() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        String language = LocaleUtility.getCurrentLocale(getContext()).getDisplayLanguage();
        map.put(getContext().getResources().getString(R.string.default_lang) + " - " + language, "");
        return Helpers.getMap(Helpers.sortNatural(getContext().getResources().getStringArray(R.array.supported_languages)), "|", map);
    }

    private Map<String, String> getSupportedCountries() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        String country = LocaleUtility.getCurrentLocale(getContext()).getDisplayCountry();
        map.put(getContext().getResources().getString(R.string.default_lang) + " - " + country, "");
        return Helpers.getMap(Helpers.sortNatural(getContext().getResources().getStringArray(R.array.supported_countries)), "|", map);
    }
}
