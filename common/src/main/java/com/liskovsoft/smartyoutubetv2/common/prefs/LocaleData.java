package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;

import com.liskovsoft.googlecommon.common.locale.LocaleManager;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.locale.LocaleUpdater;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;

/**
 * The language and the country of the Language/Country settings.
 * Each account has its own.<br/>
 * The ones in use stay in GlobalPreferences: the locale code of the shared modules reads them from there when the app starts.
 * This keeps a copy per account and puts the account's into GlobalPreferences when the account changes. They're in use at once, without
 * a restart: the content is loaded in them, and the activities are recreated in them (see MotherActivity).<br/>
 * None (signed out) has none of its own: it keeps the ones in use, so cycling through it from an account doesn't change them.
 */
public class LocaleData implements ProfileChangeListener {
    static final String LOCALE_DATA = "locale_data";
    @SuppressLint("StaticFieldLeak")
    private static LocaleData sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private final GlobalPreferences mGlobalPrefs;

    private LocaleData(Context context) {
        mContext = context;
        mPrefs = AppPrefs.instance(context);
        mGlobalPrefs = GlobalPreferences.instance(context);

        // They were for all the accounts before: kept as the shared ones (see AppPrefs.getProfileData)
        if (TextUtils.isEmpty(mPrefs.getData(LOCALE_DATA))) {
            mPrefs.setData(LOCALE_DATA, merge(getLanguage(), getCountry()));
        }

        mPrefs.addListener(this);
    }

    public static LocaleData instance(Context context) {
        if (sInstance == null) {
            sInstance = new LocaleData(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * @return e.g. "ru", "zh_TW" or empty for the system one
     */
    public String getLanguage() {
        return nonNull(mGlobalPrefs.getPreferredLanguage());
    }

    public void setLanguage(String language) {
        mGlobalPrefs.setPreferredLanguage(nonNull(language));
        persistState();
        reloadLocale();
    }

    /**
     * @return e.g. "US" or empty for the system one
     */
    public String getCountry() {
        return nonNull(mGlobalPrefs.getPreferredCountry());
    }

    public void setCountry(String country) {
        mGlobalPrefs.setPreferredCountry(nonNull(country));
        persistState();
        reloadLocale();
    }

    private void persistState() {
        // None keeps the ones in use (see onProfileChanged)
        if (mPrefs.isAnonymousProfile()) {
            return;
        }

        mPrefs.setProfileData(LOCALE_DATA, merge(getLanguage(), getCountry()));
    }

    /**
     * The account has changed: its language and country are the ones in use now. None keeps the ones in use.
     */
    @Override
    public void onProfileChanged() {
        if (mPrefs.isAnonymousProfile()) {
            return;
        }

        applyProfileData();
    }

    /**
     * The other account's language and country are the ones in use now, None's too
     */
    @Override
    public void onProfileDataCopied() {
        applyProfileData();
    }

    private void applyProfileData() {
        String[] split = Helpers.splitData(mPrefs.getProfileData(LOCALE_DATA));
        String language = nonNull(Helpers.parseStr(split, 0));
        String country = nonNull(Helpers.parseStr(split, 1));

        if (language.equals(getLanguage()) && country.equals(getCountry())) {
            return;
        }

        mGlobalPrefs.setPreferredLanguage(language);
        mGlobalPrefs.setPreferredCountry(country);
        reloadLocale();
    }

    /**
     * The ones in GlobalPreferences are in use now
     */
    private void reloadLocale() {
        // Both keep the ones they read first. Read in the system's configuration, like when the app starts: a language or country
        // not chosen is the system's, while the activities' configuration is the previous one's.
        LocaleUpdater.clearCache();
        LocaleUpdater.getSavedLocale(mContext.createConfigurationContext(Resources.getSystem().getConfiguration()));
        LocaleManager.unhold();
    }

    /**
     * Empty is saved as null: merging skips the delimiter after an empty first value
     */
    private static String merge(String language, String country) {
        return Helpers.mergeData(TextUtils.isEmpty(language) ? null : language, TextUtils.isEmpty(country) ? null : country);
    }

    private static String nonNull(String value) {
        return value != null ? value : "";
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
