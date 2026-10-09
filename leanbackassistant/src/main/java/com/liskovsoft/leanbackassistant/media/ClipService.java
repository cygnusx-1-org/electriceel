package com.liskovsoft.leanbackassistant.media;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.leanbackassistant.R;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

public class ClipService {
    /**
     * After the account's id, the start of the provider id of the channel of a playlist the account added (see getChannels):
     * the rest is the playlist id
     */
    private static final String USER_PLAYLIST_ID_PREFIX = "playlist_";
    /**
     * Between the account's id and the channel's in the provider id of each channel (see AccountChannels.getId)
     */
    private static final String PROVIDER_ID_DELIM = "/";
    private static final String CHANNEL_TITLE_DELIM = " - ";
    private static final int SUBSCRIPTIONS_ID = 1;
    private static final int HISTORY_ID = 2;
    private static final int RECOMMENDED_ID = 3;
    private static final int MY_VIDEOS_ID = 4;
    private static final int MY_SHORTS_ID = 5;
    /**
     * The app's own channels: each account chooses which it shows (see AccountChannels.isChannelEnabled)
     */
    public static final String SUBSCRIPTIONS_PROVIDER_ID = Integer.toString(SUBSCRIPTIONS_ID);
    public static final String HISTORY_PROVIDER_ID = Integer.toString(HISTORY_ID);
    public static final String RECOMMENDED_PROVIDER_ID = Integer.toString(RECOMMENDED_ID);
    public static final String MY_VIDEOS_PROVIDER_ID = Integer.toString(MY_VIDEOS_ID);
    public static final String MY_SHORTS_PROVIDER_ID = Integer.toString(MY_SHORTS_ID);
    private static final String MY_VIDEOS_CHANNEL_ID = "my_videos_channel_id";
    private static final String MY_VIDEOS_PROGRAMS_IDS = "my_videos_programs_ids";
    private static final String MY_SHORTS_CHANNEL_ID = "my_shorts_channel_id";
    private static final String MY_SHORTS_PROGRAMS_IDS = "my_shorts_programs_ids";
    private static final String SUBS_CHANNEL_ID = "subs_channel_id";
    private static final String SUBS_PROGRAMS_IDS = "subs_clips_ids";
    private static final String RECOMMENDED_CHANNEL_ID = "recommended_channel_id";
    private static final String RECOMMENDED_PROGRAMS_IDS = "recommended_programs_ids";
    private static final String HISTORY_CHANNEL_ID = "history_channel_id";
    private static final String HISTORY_PROGRAMS_IDS = "history_programs_ids";
    private static final String SUBSCRIPTIONS_URL = "https://www.youtube.com/tv#/zylon-surface?c=FEsubscriptions&resume";
    private static final String HISTORY_URL = "https://www.youtube.com/tv#/zylon-surface?c=FEmy_youtube&resume";
    private static final String RECOMMENDED_URL = "https://www.youtube.com/tv#/zylon-surface?c=default&resume";
    private static final String MY_VIDEOS_URL = "https://www.youtube.com/tv#/zylon-surface?c=FEmy_videos&resume";
    private static final String PLAYLIST_URL = "https://www.youtube.com/playlist?list=";
    @SuppressLint("StaticFieldLeak")
    private static ClipService mInstance;
    private static ChannelsSource sChannelsSource;
    private final Context mContext;

    /**
     * What the app knows of the accounts: the channels each chose to show
     */
    public interface ChannelsSource {
        /**
         * @return the channels of each account, and of the user signed out. Null: the accounts can't be read yet.
         */
        @Nullable
        List<AccountChannels> getAccountChannels();
    }

    public ClipService(Context context) {
        mContext = context;
    }

    public static ClipService instance(Context context) {
        if (mInstance == null) {
            mInstance = new ClipService(context.getApplicationContext());
        }

        return mInstance;
    }

    /**
     * Of the recommendations (before Android 8): the selected account's, named as they are
     */
    public Playlist getSubscriptionsPlaylist() {
        return getSubscriptionsPlaylist(null);
    }

    public Playlist getHistoryPlaylist() {
        return getHistoryPlaylist(null);
    }

    public Playlist getRecommendedPlaylist() {
        return getRecommendedPlaylist(null);
    }

    /**
     * Set by the app when it starts: the channels can be updated before anything else of the app runs
     */
    public static void setChannelsSource(ChannelsSource source) {
        sChannelsSource = source;
    }

    /**
     * @return null when the app hasn't said yet (see setChannelsSource): the channels are left as they are
     */
    @Nullable
    public List<AccountChannels> getAccountChannels() {
        ChannelsSource source = sChannelsSource;

        return source != null ? source.getAccountChannels() : null;
    }

    /**
     * The ones the account shows: the app's own it chose, then the playlists it added, each a channel of its own.
     * Their ids start with the account's: the channels of each account are its own.
     */
    public List<Playlist> getChannels(AccountChannels account) {
        List<Playlist> result = new ArrayList<>();

        if (account.isChannelEnabled(SUBSCRIPTIONS_PROVIDER_ID)) {
            result.add(getSubscriptionsPlaylist(account));
        }

        if (account.isChannelEnabled(RECOMMENDED_PROVIDER_ID)) {
            result.add(getRecommendedPlaylist(account));
        }

        if (account.isChannelEnabled(HISTORY_PROVIDER_ID)) {
            result.add(getHistoryPlaylist(account));
        }

        if (account.isChannelEnabled(MY_VIDEOS_PROVIDER_ID)) {
            result.add(getMyVideosPlaylist(account));
        }

        if (account.isChannelEnabled(MY_SHORTS_PROVIDER_ID)) {
            result.add(getMyShortsPlaylist(account));
        }

        for (Entry<String, String> playlist : account.getPlaylists().entrySet()) {
            result.add(createUserPlaylist(account, playlist.getKey(), playlist.getValue()));
        }

        return result;
    }

    /**
     * Its channels get their videos in it: the account's, whichever is selected (see SignInService.callAs)
     */
    public <T> T call(AccountChannels account, Callable<T> callable) throws Exception {
        return YouTubeServiceManager.instance().getSignInService().callAs(account.getAccount(), callable);
    }

    private Playlist getSubscriptionsPlaylist(@Nullable AccountChannels account) {
        return createPlaylist(
                account,
                R.string.header_subscriptions,
                SUBSCRIPTIONS_ID,
                SUBS_CHANNEL_ID,
                SUBS_PROGRAMS_IDS,
                SUBSCRIPTIONS_URL,
                R.drawable.generic_channels,
                ContentService::getSubscriptions,
                false
        );
    }

    private Playlist getHistoryPlaylist(@Nullable AccountChannels account) {
        return createPlaylist(
                account,
                R.string.header_history,
                HISTORY_ID,
                HISTORY_CHANNEL_ID,
                HISTORY_PROGRAMS_IDS,
                HISTORY_URL,
                R.drawable.generic_channels,
                ContentService::getHistory,
                false);
    }

    private Playlist getRecommendedPlaylist(@Nullable AccountChannels account) {
        return createPlaylist(
                account,
                R.string.recommended,
                RECOMMENDED_ID,
                RECOMMENDED_CHANNEL_ID,
                RECOMMENDED_PROGRAMS_IDS,
                RECOMMENDED_URL,
                R.drawable.generic_channels,
                ContentService::getRecommended,
                true);
    }

    /**
     * The videos of the account, its first row of My videos
     */
    private Playlist getMyVideosPlaylist(AccountChannels account) {
        return createPlaylist(
                account,
                R.string.my_videos,
                MY_VIDEOS_ID,
                MY_VIDEOS_CHANNEL_ID,
                MY_VIDEOS_PROGRAMS_IDS,
                MY_VIDEOS_URL,
                R.drawable.generic_channels,
                contentService -> getMyVideosGroup(contentService, 0),
                false);
    }

    /**
     * The shorts of the account, its second row of My videos
     */
    private Playlist getMyShortsPlaylist(AccountChannels account) {
        return createPlaylist(
                account,
                R.string.my_videos_shorts,
                MY_SHORTS_ID,
                MY_SHORTS_CHANNEL_ID,
                MY_SHORTS_PROGRAMS_IDS,
                MY_VIDEOS_URL,
                R.drawable.generic_channels,
                contentService -> getMyVideosGroup(contentService, 1),
                false);
    }

    /**
     * Read on this thread, the thread of the account's call (see call): the videos, then the shorts, found from the channel
     * of the videos (none without them)
     */
    @Nullable
    private static MediaGroup getMyVideosGroup(ContentService contentService, int index) {
        List<MediaGroup> rows = contentService.getMyVideos();

        return rows != null && rows.size() > index ? rows.get(index) : null;
    }

    private Playlist createUserPlaylist(AccountChannels account, String playlistId, String title) {
        String providerId = createProviderId(account, USER_PLAYLIST_ID_PREFIX + playlistId);
        Playlist playlist = new Playlist(createChannelTitle(account, title), providerId, contentService -> getPlaylistGroup(playlistId));
        playlist.setChannelKey(providerId);
        playlist.setProgramsKey(providerId);
        playlist.setPlaylistUrl(PLAYLIST_URL + playlistId);
        playlist.setLogoResId(R.drawable.generic_channels);

        return playlist;
    }

    private static String createProviderId(AccountChannels account, String channelId) {
        return account.getId() + PROVIDER_ID_DELIM + channelId;
    }

    /**
     * "Account name - Title": the launcher shows whose content a channel is. Signed out, the title alone.
     */
    private static String createChannelTitle(AccountChannels account, String title) {
        String accountName = account.getName();

        return accountName != null && !accountName.isEmpty() ? accountName + CHANNEL_TITLE_DELIM + title : title;
    }

    /**
     * The videos of the playlist, loaded the way the app opens it (YouTube's or one kept on the device)
     * @return null when it can't be loaded, so the channel keeps the videos it has
     */
    @Nullable
    private static MediaGroup getPlaylistGroup(String playlistId) {
        MediaItemMetadata metadata = YouTubeServiceManager.instance().getMediaItemService().getMetadata(null, playlistId, 0, null);
        List<MediaGroup> suggestions = metadata != null ? metadata.getSuggestions() : null;

        if (suggestions == null) {
            return null;
        }

        // Not a row of related videos: the playlist's own
        return Helpers.findFirst(suggestions, group -> {
            List<MediaItem> mediaItems = group != null ? group.getMediaItems() : null;
            MediaItem firstItem = Helpers.findFirst(mediaItems, item -> item != null);
            return firstItem != null && Helpers.equals(firstItem.getPlaylistId(), playlistId);
        });
    }

    /**
     * @param account null: of the recommendations, the selected account's (see getSubscriptionsPlaylist)
     */
    private Playlist createPlaylist(
            @Nullable AccountChannels account, int titleResId, int id, String channelId, String programId,
            String recommendedUrl, int logoResId, GroupCallback callback, boolean isDefault) {
        String title = mContext.getResources().getString(titleResId);
        String providerId = Integer.toString(id);

        if (account != null) {
            title = createChannelTitle(account, title);
            providerId = createProviderId(account, providerId);
            // Each account's channel is its own
            channelId = providerId;
            programId = providerId;
        }

        Playlist playlist = new Playlist(
                title,
                providerId,
                callback,
                isDefault);
        playlist.setChannelKey(channelId);
        playlist.setProgramsKey(programId);
        playlist.setPlaylistUrl(recommendedUrl);
        playlist.setLogoResId(logoResId);

        return playlist;
    }

    public interface GroupCallback {
        MediaGroup call(ContentService mediaTabManager);
    }
}
