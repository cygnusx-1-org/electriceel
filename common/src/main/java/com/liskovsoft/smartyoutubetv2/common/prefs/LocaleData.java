package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;

/**
 * The language and the country of the Language/Country settings.
 * Each account has its own.<br/>
 * The ones in use stay in GlobalPreferences: the locale code of the shared modules reads them from there when the app starts.
 * This keeps a copy per account and puts the account's into GlobalPreferences when the account changes. The app needs a restart then,
 * like after a change in the settings.
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
    }

    private void persistState() {
        mPrefs.setProfileData(LOCALE_DATA, merge(getLanguage(), getCountry()));
    }

    /**
     * The account (or the per account switch) has changed: its language and country are the ones in use now
     */
    @Override
    public void onProfileChanged() {
        String[] split = Helpers.splitData(mPrefs.getProfileData(LOCALE_DATA));
        String language = nonNull(Helpers.parseStr(split, 0));
        String country = nonNull(Helpers.parseStr(split, 1));

        if (language.equals(getLanguage()) && country.equals(getCountry())) {
            return;
        }

        mGlobalPrefs.setPreferredLanguage(language);
        mGlobalPrefs.setPreferredCountry(country);

        MessageHelpers.showLongMessage(mContext, R.string.msg_restart_app);
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
