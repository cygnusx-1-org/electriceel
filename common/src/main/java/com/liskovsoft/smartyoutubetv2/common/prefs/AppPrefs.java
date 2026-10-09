package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.misc.WeakHashSet;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.sharedutils.prefs.SharedPreferencesBase;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.AccountChangeListener;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AppPrefs extends SharedPreferencesBase implements AccountChangeListener {
    private static final String TAG = AppPrefs.class.getSimpleName();
    private static final String PREFS_DIR = "app_prefs";
    @SuppressLint("StaticFieldLeak")
    private static AppPrefs sInstance;
    private static final String ANONYMOUS_PROFILE_NAME = "anonymous";
    /**
     * The removed "Use separate settings per each account": off, every account used the shared settings
     */
    private static final String MULTI_PROFILES = "multi_profiles";
    private static final String STATE_UPDATER_DATA = "state_updater_data";
    private static final String CHANNEL_GROUP_DATA = "channel_group_data";
    private static final String SIDEBAR_DATA = "sidebar_data";
    private static final String HOME_SCREEN_PLAYLISTS_DATA = "home_screen_playlists_data";
    private static final String HOME_SCREEN_CHANNELS_DATA = "home_screen_channels_data";
    private static final String VIEW_MANAGER_DATA = "view_manager_data";
    private static final String WEB_PROXY_URI = "web_proxy_uri";
    private static final String WEB_PROXY_ENABLED = "web_proxy_enabled";
    private static final String LAST_PROFILE_NAME = "last_profile_name";
    private static final String SHARED_SETTINGS_PROFILE_NAME = "shared_settings_profile_name";
    /**
     * The profiles that got a copy of the one they shared (see copySharedProfiles)
     */
    private static final String COPIED_PROFILE_NAMES = "copied_profile_names";
    /**
     * The settings each account has its own of (see getProfileData): a new kind of them goes here too, to be copied
     */
    private static final String[] PROFILE_DATA_KEYS = {
            MainUIData.MAIN_UI_DATA, GeneralData.GENERAL_DATA, PlayerData.VIDEO_PLAYER_DATA, PlayerTweaksData.VIDEO_PLAYER_TWEAKS_DATA,
            LocaleData.LOCALE_DATA, BlockedChannelData.BLOCKED_CHANNEL_DATA, KeywordFilterData.KEYWORD_FILTER_DATA,
            CollaborationsData.class.getSimpleName(), TopChannelsData.class.getSimpleName(), ExploreTopicsData.class.getSimpleName(),
            ShowsData.class.getSimpleName(), WatchLaterData.class.getSimpleName(), MusicAutoplayData.class.getSimpleName(),
            OldVideosData.class.getSimpleName()
    };
    private String mBootResolution;
    private final WeakHashSet<ProfileChangeListener> mListeners = new WeakHashSet<>();

    public interface ProfileChangeListener {
        void onProfileChanged();

        /**
         * The settings of another account were copied to the current one (see copyProfileData)
         */
        default void onProfileDataCopied() {
            onProfileChanged();
        }
    }

    private AppPrefs(Context context) {
        //super(context, R.xml.app_prefs); // IndexOutOfBoundsException on Google Streamer
        super(context);

        initProfiles();
    }

    private void initProfiles() {
        migrateSharedSettings();
        initSharedSettingsProfile();
        MediaServiceManager.instance().addAccountListener(this);
        initAccountProfile();
    }

    /**
     * The accounts with the same name shared the profile named after them: each has its own now (see Account.getProfileName),
     * the selected account's from the start
     */
    private void initAccountProfile() {
        // The accounts are kept in it
        GlobalPreferences.instance(getContext());

        copySharedProfiles(MediaServiceManager.instance().getAccounts());

        Account account = MediaServiceManager.instance().getSelectedAccount();

        if (account != null) {
            selectProfile(account);
        }
    }

    /**
     * An account that shared the profile named after it with the others of its name gets a copy of it, once
     * (see Account.getSharedProfileName). Not its home screen channels: the launcher would show each channel twice.
     */
    void copySharedProfiles(@Nullable List<Account> accounts) {
        if (accounts == null) {
            return;
        }

        Set<String> copied = new LinkedHashSet<>();
        String[] copiedNames = Helpers.splitArray(getString(COPIED_PROFILE_NAMES, null));

        if (copiedNames != null) {
            Collections.addAll(copied, copiedNames);
        }

        for (Account account : accounts) {
            String from = account != null ? account.getSharedProfileName() : null;
            String to = account != null ? account.getProfileName() : null;

            if (from == null || to == null || from.equals(to) || copied.contains(to)) {
                continue;
            }

            boolean isFromSharedSettingsProfile = from.equals(getString(SHARED_SETTINGS_PROFILE_NAME, null));

            for (String key : getProfileKeys()) {
                if (HOME_SCREEN_PLAYLISTS_DATA.equals(key) || HOME_SCREEN_CHANNELS_DATA.equals(key)) {
                    continue;
                }

                String data = getData(from + "_" + key);

                // The shared settings are its own too (see getProfileData)
                if (TextUtils.isEmpty(data) && isFromSharedSettingsProfile && isProfileDataKey(key)) {
                    data = getData(key);
                }

                if (data != null) {
                    setData(to + "_" + key, data);
                }
            }

            copied.add(to);
            putString(COPIED_PROFILE_NAMES, Helpers.mergeList(copied));
        }
    }

    private static boolean isProfileDataKey(String key) {
        for (String profileDataKey : PROFILE_DATA_KEYS) {
            if (profileDataKey.equals(key)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Each account has its own settings now: with "Use separate settings per each account" off, every account used the shared ones,
     * so each account that has been used gets them as its own
     */
    void migrateSharedSettings() {
        if (getBoolean(MULTI_PROFILES, true)) {
            return;
        }

        for (String profileName : getUsedProfileNames()) {
            for (String key : PROFILE_DATA_KEYS) {
                String data = getData(key);
                // Empty: the defaults
                setData(profileName + "_" + key, data != null ? data : "");
            }
        }

        // All of them have their own now
        putString(SHARED_SETTINGS_PROFILE_NAME, "");
        putString(MULTI_PROFILES, null);
    }

    /**
     * Found by the data each account has its own of: the history and the sidebar are kept per account even with the shared settings
     */
    private Set<String> getUsedProfileNames() {
        Set<String> result = new LinkedHashSet<>();
        String profileName = getProfileName();

        if (!TextUtils.isEmpty(profileName)) {
            result.add(profileName);
        }

        String[] names = new File(FileHelpers.getFilesDir(getContext()), PREFS_DIR).list();

        if (names == null) {
            return result;
        }

        for (String name : names) {
            for (String key : getProfileKeys()) {
                String suffix = "_" + key;

                if (name.length() > suffix.length() && name.endsWith(suffix)) {
                    result.add(name.substring(0, name.length() - suffix.length()));
                }
            }
        }

        return result;
    }

    private static Set<String> getProfileKeys() {
        Set<String> result = new LinkedHashSet<>();

        result.add(STATE_UPDATER_DATA);
        result.add(SIDEBAR_DATA);
        result.add(CHANNEL_GROUP_DATA);
        result.add(HOME_SCREEN_PLAYLISTS_DATA);
        result.add(HOME_SCREEN_CHANNELS_DATA);
        Collections.addAll(result, PROFILE_DATA_KEYS);

        return result;
    }

    /**
     * Settings without the name are from SmartTube's backup or from a version before this one: all of them are shared,
     * and they're the current account's. Kept for that account only (see getProfileData). Empty: no account had any yet.
     */
    void initSharedSettingsProfile() {
        if (getString(SHARED_SETTINGS_PROFILE_NAME, null) == null) {
            String profileName = getProfileName();
            putString(SHARED_SETTINGS_PROFILE_NAME, profileName != null ? profileName : "");
        }
    }

    @Override
    public void onAccountChanged(Account account) {
        copySharedProfiles(MediaServiceManager.instance().getAccounts());
        selectProfile(account);
        onProfileChanged();
    }

    public static AppPrefs instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppPrefs(context);
        }

        return sInstance;
    }

    public String getBootResolution() {
        return mBootResolution;
    }

    public void setBootResolution(String resolution) {
        mBootResolution = resolution;
    }

    public String getStateUpdaterData() {
        return getData(getStateUpdaterKey());
    }

    /**
     * Not decoded: thousands of states are decoded in parts, at once
     */
    public byte[] getStateUpdaterBytes() {
        return getDataBytes(getStateUpdaterKey());
    }

    /**
     * The key of the history of the current profile. Taken before a write off the main thread, so the history goes to its own
     * profile even if the profile changes before the write.
     */
    public String getStateUpdaterKey() {
        return getProfileKey(STATE_UPDATER_DATA);
    }

    public String getChannelGroupData() {
        return getData(getProfileKey(CHANNEL_GROUP_DATA));
    }

    public void setChannelGroupData(String data) {
        setData(getProfileKey(CHANNEL_GROUP_DATA), data);
    }

    public String getSidebarData() {
        return getData(getProfileKey(SIDEBAR_DATA));
    }

    public void setSidebarData(String data) {
        setData(getProfileKey(SIDEBAR_DATA), data);
    }

    public String getHomeScreenPlaylistsData() {
        return getData(getProfileKey(HOME_SCREEN_PLAYLISTS_DATA));
    }

    public void setHomeScreenPlaylistsData(String data) {
        setData(getProfileKey(HOME_SCREEN_PLAYLISTS_DATA), data);
    }

    public String getHomeScreenChannelsData() {
        return getData(getProfileKey(HOME_SCREEN_CHANNELS_DATA));
    }

    public void setHomeScreenChannelsData(String data) {
        setData(getProfileKey(HOME_SCREEN_CHANNELS_DATA), data);
    }

    /**
     * The given account's, whichever is selected
     * @param account null: signed out
     */
    public String getHomeScreenPlaylistsData(@Nullable Account account) {
        return getData(getProfileKey(account, HOME_SCREEN_PLAYLISTS_DATA));
    }

    /**
     * The given account's, whichever is selected
     * @param account null: signed out
     */
    public String getHomeScreenChannelsData(@Nullable Account account) {
        return getData(getProfileKey(account, HOME_SCREEN_CHANNELS_DATA));
    }

    /**
     * An account without its own settings starts from the defaults, not from the shared ones: they're the first account's, so
     * every account added after it got that account's theme and the rest.<br/>
     * The account the shared settings were made with keeps them (see initSharedSettingsProfile): copied to its own when first read.
     */
    public String getProfileData(String key) {
        String profileKey = getProfileKey(key);
        String data = getData(profileKey);

        if (TextUtils.isEmpty(data) && !profileKey.equals(key) && isSharedSettingsProfile()) {
            data = getData(key);

            if (!TextUtils.isEmpty(data)) {
                setData(profileKey, data);
            }
        }

        return data;
    }

    public void setProfileData(String key, String data) {
        setData(getProfileKey(key), data);
    }

    /**
     * Replaces the settings of the current account with the ones the given account has in use (see getProfileData).
     * The history, channel groups, sidebar and home screen channels stay the current account's.
     */
    public void copyProfileData(Account from) {
        String fromName = getProfileName(from);
        String toName = getProfileName();

        if (TextUtils.isEmpty(toName) || fromName.equals(toName)) {
            return;
        }

        boolean isFromSharedSettingsProfile = fromName.equals(getString(SHARED_SETTINGS_PROFILE_NAME, null));

        for (String key : PROFILE_DATA_KEYS) {
            String data = getData(fromName + "_" + key);

            if (TextUtils.isEmpty(data) && isFromSharedSettingsProfile) {
                data = getData(key);
            }

            // Empty: the defaults
            setData(getProfileKey(key), data != null ? data : "");
        }

        // All its settings are its own now: the shared ones aren't kept for it anymore
        if (isSharedSettingsProfile()) {
            putString(SHARED_SETTINGS_PROFILE_NAME, "");
        }

        mListeners.forEach(ProfileChangeListener::onProfileDataCopied);
    }

    //public String getData(String key) {
    //    // Don't sync hash here. Hashes won't match.
    //    return getString(key, null);
    //}
    //
    //public void setData(String key, String data) {
    //    if (checkData(key, data)) {
    //        putString(key, data);
    //    }
    //}

    public String getWebProxyUri() {
        return getString(WEB_PROXY_URI, "");
    }

    public void setWebProxyUri(String uri) {
        putString(WEB_PROXY_URI, uri);
    }

    public boolean isWebProxyEnabled() {
        return getBoolean(WEB_PROXY_ENABLED, false);
    }

    public void setWebProxyEnabled(boolean enabled) {
        putBoolean(WEB_PROXY_ENABLED, enabled);
    }

    private String getProfileName() {
        return getString(LAST_PROFILE_NAME, null);
    }

    private void setProfileName(String profileName) {
        putString(LAST_PROFILE_NAME, profileName);
    }

    private boolean isSharedSettingsProfile() {
        String profileName = getString(SHARED_SETTINGS_PROFILE_NAME, null);

        return !TextUtils.isEmpty(profileName) && profileName.equals(getProfileName());
    }

    /**
     * The settings are from SmartTube's backup or from a version before this one again (see initSharedSettingsProfile)
     */
    void resetSharedSettingsProfileForTesting() {
        putString(SHARED_SETTINGS_PROFILE_NAME, null);
    }

    private void selectProfile(Account account) {
        setProfileName(getProfileName(account));
    }

    private static String getProfileName(Account account) {
        String profileName = account != null ? account.getProfileName() : null;

        return profileName != null ? profileName : ANONYMOUS_PROFILE_NAME;
    }

    private void onProfileChanged() {
        mListeners.forEach(ProfileChangeListener::onProfileChanged);
    }

    public void addListener(ProfileChangeListener listener) {
        if (!mListeners.contains(listener)) {
            if (listener instanceof GeneralData) {
                mListeners.add(0, listener); // data classes should be called before regular listeners
            } else if (listener instanceof SidebarService) {
                mListeners.add(mListeners.isEmpty() ? 0 : 1, listener); // data classes should be called before regular listeners
            } else {
                mListeners.add(listener);
            }
        }
    }

    public void removeListener(ProfileChangeListener listener) {
        mListeners.remove(listener);
    }

    /**
     * The shared key until the first account (or none) is selected
     */
    private String getProfileKey(String key) {
        String profileName = getProfileName();
        if (!TextUtils.isEmpty(profileName)) {
            key = profileName + "_" + key;
        }

        return key;
    }

    /**
     * The selected one's is the key above: signed out, it's the shared one until an account is selected
     */
    private String getProfileKey(@Nullable Account account, String key) {
        String profileName = getProfileName(account);
        String selectedProfileName = getProfileName();

        if (profileName.equals(selectedProfileName) || (account == null && TextUtils.isEmpty(selectedProfileName))) {
            return getProfileKey(key);
        }

        return profileName + "_" + key;
    }
    
    @Override
    protected String getPrefsDir() {
        return PREFS_DIR;
    }
}
