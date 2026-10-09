package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.leanbackassistant.media.AccountChannels;
import com.liskovsoft.leanbackassistant.media.ClipService;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * The playlists each account has added to the launcher's home screen, each shown as a channel of its own, and which of the
 * app's own channels (History, My videos, Recommended, Subscriptions...) it shows. The launcher shows the channels of every
 * account.<br/>
 * Read again at every update of the channels (see UpdateChannelsTask): they can be updated before anything else of the app runs,
 * and after the account changes.
 */
public class HomeScreenPlaylistsData {
    /**
     * Of the channels chosen signed out (see AccountChannels.getId)
     */
    private static final String ANONYMOUS_ID = "anonymous";
    /**
     * The first ones of a profile name (see Account.getProfileName): the profile named after the account comes before the
     * numbered ones, and the numbers in their order
     */
    private static final Comparator<Account> PROFILE_ORDER = (first, second) -> {
        String firstName = first.getProfileName() != null ? first.getProfileName() : "";
        String secondName = second.getProfileName() != null ? second.getProfileName() : "";

        return firstName.length() != secondName.length() ? firstName.length() - secondName.length() : firstName.compareTo(secondName);
    };
    @SuppressLint("StaticFieldLeak")
    private static HomeScreenPlaylistsData sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private AccountsSource mAccountsSource = new AccountsSource() {
        @Override
        public List<Account> getAccounts() {
            return MediaServiceManager.instance().getAccounts();
        }

        @Override
        public Account getSelectedAccount() {
            return MediaServiceManager.instance().getSelectedAccount();
        }
    };

    interface AccountsSource {
        /**
         * @return the signed-in accounts
         */
        @Nullable
        List<Account> getAccounts();

        /**
         * @return null signed out
         */
        @Nullable
        Account getSelectedAccount();
    }

    private HomeScreenPlaylistsData(Context context) {
        mContext = context;
        mPrefs = AppPrefs.instance(context);
    }

    public static HomeScreenPlaylistsData instance(Context context) {
        if (sInstance == null) {
            sInstance = new HomeScreenPlaylistsData(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * Lets the channels read what each account chose. Called when the app starts.
     */
    public static void registerChannelsSource(Context context) {
        Context appContext = context.getApplicationContext();
        ClipService.setChannelsSource(() -> instance(appContext).getAccountChannels());
    }

    /**
     * The launcher shows the channels of the app, and they're updated
     */
    public static boolean isSupported(Context context) {
        return Helpers.isATVChannelsSupported(context) && GlobalPreferences.instance(context).isChannelsServiceEnabled();
    }

    /**
     * @return the current account's: the id of each playlist to its title, in the order they were added
     */
    public synchronized Map<String, String> getPlaylists() {
        return parsePlaylists(mPrefs.getHomeScreenPlaylistsData());
    }

    private static Map<String, String> parsePlaylists(String data) {
        Map<String, String> result = new LinkedHashMap<>();
        String[] playlists = Helpers.splitArray(data);

        if (playlists == null) {
            return result;
        }

        for (String playlist : playlists) {
            String[] fields = Helpers.splitObj(playlist);
            String playlistId = Helpers.parseStr(fields, 0);

            if (playlistId != null) {
                result.put(playlistId, Helpers.parseStr(fields, 1, playlistId));
            }
        }

        return result;
    }

    /**
     * The channels are named after it (see ClipService.createChannelTitle)
     * @return the signed-in account's name, numbered when another account has it (see getAccountName(Account, List)),
     * or null signed out
     */
    @Nullable
    public String getAccountName() {
        Account account = mAccountsSource.getSelectedAccount();

        return account != null ? getAccountName(account, getAccounts()) : null;
    }

    /**
     * The channels of every account, and the ones chosen signed out
     */
    public synchronized List<AccountChannels> getAccountChannels() {
        // The accounts are kept in it: read once it's there (see YouTubeAccountManager)
        GlobalPreferences.instance(mContext);

        List<Account> accounts = getAccounts();
        List<AccountChannels> result = new ArrayList<>();

        for (Account account : accounts) {
            result.add(createAccountChannels(account, getAccountName(account, accounts)));
        }

        // Signed out
        result.add(createAccountChannels(null, null));

        return result;
    }

    /**
     * Read by the account's profile, not by whether it's selected: the profile changes after the account does
     * (see AppPrefs.onAccountChanged)
     */
    private AccountChannels createAccountChannels(@Nullable Account account, @Nullable String name) {
        String playlistsData = mPrefs.getHomeScreenPlaylistsData(account);
        String channelsData = mPrefs.getHomeScreenChannelsData(account);
        Set<String> enabledChannels = new HashSet<>();

        for (Entry<String, Boolean> channel : parseChannelStates(channelsData).entrySet()) {
            if (Boolean.TRUE.equals(channel.getValue())) {
                enabledChannels.add(channel.getKey());
            }
        }

        String profileName = account != null ? account.getProfileName() : null;

        return new AccountChannels(
                account, profileName != null ? profileName : ANONYMOUS_ID, name, parsePlaylists(playlistsData), enabledChannels);
    }

    private List<Account> getAccounts() {
        List<Account> result = new ArrayList<>();
        List<Account> accounts = mAccountsSource.getAccounts();

        if (accounts != null) {
            for (Account account : accounts) {
                // Not the one with only a token while signing in (see YouTubeAccountManager.persistRefreshToken)
                if (account != null && !account.isEmpty()) {
                    result.add(account);
                }
            }
        }

        return result;
    }

    private static boolean isSameProfile(Account account, @Nullable Account other) {
        return other != null && Helpers.equals(account.getProfileName(), other.getProfileName());
    }

    /**
     * @param providerId one of the app's own channels, e.g. ClipService.HISTORY_PROVIDER_ID
     * @return the account chose to show it: none is shown until chosen
     */
    public boolean isChannelEnabled(String providerId) {
        return Boolean.TRUE.equals(getChannelStates().get(providerId));
    }

    public synchronized void setChannelEnabled(String providerId, boolean enabled) {
        Map<String, Boolean> channelStates = getChannelStates();
        channelStates.put(providerId, enabled);
        mPrefs.setHomeScreenChannelsData(Helpers.mergeMap(channelStates));
    }

    private synchronized Map<String, Boolean> getChannelStates() {
        return parseChannelStates(mPrefs.getHomeScreenChannelsData());
    }

    private static Map<String, Boolean> parseChannelStates(String data) {
        return Helpers.parseMap(data, Helpers::parseStr, Helpers::parseBoolean);
    }

    public boolean contains(String playlistId) {
        return playlistId != null && getPlaylists().containsKey(playlistId);
    }

    /**
     * Kept where it was if it's added already, under the new title
     */
    public synchronized void add(String playlistId, String title) {
        if (playlistId == null) {
            return;
        }

        Map<String, String> playlists = getPlaylists();
        playlists.put(playlistId, title != null ? title : playlistId);
        persist(playlists);
    }

    public synchronized void remove(String playlistId) {
        Map<String, String> playlists = getPlaylists();

        if (playlists.remove(playlistId) != null) {
            persist(playlists);
        }
    }

    /**
     * The channel of a playlist renamed in the app gets the new title
     * @return the playlist is on the home screen
     */
    public synchronized boolean rename(String playlistId, String title) {
        if (!contains(playlistId)) {
            return false;
        }

        add(playlistId, title);

        return true;
    }

    /**
     * The name, else the email
     */
    @Nullable
    static String getAccountName(@Nullable Account account) {
        if (account == null) {
            return null;
        }

        return account.getName() != null ? account.getName() : account.getEmail();
    }

    /**
     * Numbered when other accounts have the name ("Name 1", "Name 2"...): in the order of their profiles, so each keeps its
     * number as long as the accounts with the name are the same (see PROFILE_ORDER)
     */
    @Nullable
    static String getAccountName(Account account, List<Account> accounts) {
        String name = getAccountName(account);

        if (name == null) {
            return null;
        }

        List<Account> sameName = new ArrayList<>();

        for (Account other : accounts) {
            if (name.equals(getAccountName(other))) {
                sameName.add(other);
            }
        }

        if (sameName.size() < 2) {
            return name;
        }

        Collections.sort(sameName, PROFILE_ORDER);

        for (int i = 0; i < sameName.size(); i++) {
            if (isSameProfile(account, sameName.get(i))) {
                return name + " " + (i + 1);
            }
        }

        return name;
    }

    private void persist(Map<String, String> playlists) {
        List<String> result = new ArrayList<>();

        for (Entry<String, String> playlist : playlists.entrySet()) {
            result.add(Helpers.mergeObj(playlist.getKey(), playlist.getValue()));
        }

        mPrefs.setHomeScreenPlaylistsData(Helpers.mergeList(result));
    }

    void setAccountsSourceForTesting(AccountsSource accountsSource) {
        mAccountsSource = accountsSource;
    }

    /**
     * The next instance reads the signed-in account again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
