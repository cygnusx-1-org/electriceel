package com.liskovsoft.leanbackassistant.media;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

import java.util.Map;
import java.util.Set;

/**
 * The channels an account shows in the launcher (see ClipService.ChannelsSource)
 */
public final class AccountChannels {
    @Nullable
    private final Account mAccount;
    private final String mId;
    @Nullable
    private final String mName;
    private final Map<String, String> mPlaylists;
    private final Set<String> mEnabledChannels;

    /**
     * @param account null: signed out
     * @param id unique among the accounts, the same each time: the ids of its channels start with it
     * @param name its channels are named after it, null signed out
     * @param playlists the id of each playlist it added to its title, in the order they were added
     * @param enabledChannels the app's own channels it shows, e.g. ClipService.HISTORY_PROVIDER_ID
     */
    public AccountChannels(@Nullable Account account, String id, @Nullable String name, Map<String, String> playlists,
                           Set<String> enabledChannels) {
        mAccount = account;
        mId = id;
        mName = name;
        mPlaylists = playlists;
        mEnabledChannels = enabledChannels;
    }

    @Nullable
    public Account getAccount() {
        return mAccount;
    }

    public String getId() {
        return mId;
    }

    @Nullable
    public String getName() {
        return mName;
    }

    public Map<String, String> getPlaylists() {
        return mPlaylists;
    }

    public boolean isChannelEnabled(String providerId) {
        return mEnabledChannels.contains(providerId);
    }
}
