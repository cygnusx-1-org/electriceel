package com.liskovsoft.leanbackassistant.channels;

import android.content.Context;

import androidx.annotation.RequiresApi;

import com.liskovsoft.leanbackassistant.media.AccountChannels;
import com.liskovsoft.leanbackassistant.media.ClipService;
import com.liskovsoft.leanbackassistant.media.Playlist;
import com.liskovsoft.leanbackassistant.recommendations.RecommendationsProvider;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RequiresApi(21)
public class UpdateChannelsTask {
    private static final String TAG = UpdateChannelsTask.class.getSimpleName();
    private static final Object RUN_LOCK = new Object();
    private final Context mContext;
    private final GlobalPreferences mPrefs;
    private final ClipService mService;

    public UpdateChannelsTask(Context context) {
        mContext = context;

        Log.d(TAG, "Creating GlobalPreferences...");
        mPrefs = GlobalPreferences.instance(context);
        mService = ClipService.instance(context);
    }

    /**
     * One run at a time: rescheduling (see UpdateChannelsWorker.schedule) starts a run while the last one is still going,
     * and two runs would both publish a new channel
     */
    public void run() {
        synchronized (RUN_LOCK) {
            updateChannels();
            updateRecommendations();
        }
    }

    /**
     * The channels every account chose, each with the videos of its account. The rest are deleted: the ones not chosen
     * anymore, and the ones of an account signed out of.
     */
    private void updateChannels() {
        if (!Helpers.isATVChannelsSupported(mContext)) {
            return;
        }

        List<AccountChannels> accounts = mService.getAccountChannels();

        if (accounts == null) { // not known yet: left as they are
            return;
        }

        Set<String> providerIds = new HashSet<>();

        for (AccountChannels account : accounts) {
            for (Playlist playlist : mService.getChannels(account)) {
                // Kept even when it can't be updated now (e.g. offline)
                providerIds.add(playlist.getPlaylistId());

                try {
                    mService.call(account, () -> {
                        updateOrPublishChannel(playlist);
                        return null;
                    });
                } catch (Exception e) { // the next channel is updated anyway
                    Log.e(TAG, e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        try {
            ChannelsProvider.deleteChannelsExcept(mContext, providerIds);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateRecommendations() {
        if (Helpers.isATVRecommendationsSupported(mContext)) {
            try {
                updateOrPublishRecommendations(getSinglePreferredPlaylist());
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private Playlist getSinglePreferredPlaylist() {
        Playlist playlist = null;

        switch (mPrefs.getRecommendedPlaylistType()) {
            case GlobalPreferences.PLAYLIST_TYPE_RECOMMENDATIONS:
                playlist = mService.getRecommendedPlaylist();
                break;
            case GlobalPreferences.PLAYLIST_TYPE_SUBSCRIPTIONS:
                playlist = mService.getSubscriptionsPlaylist();
                break;
            case GlobalPreferences.PLAYLIST_TYPE_HISTORY:
                playlist = mService.getHistoryPlaylist();
                break;
        }

        return playlist;
    }

    private void updateOrPublishRecommendations(Playlist playlist) {
        Log.d(TAG, "Syncing recommended: " + playlist.getName());
        RecommendationsProvider.createOrUpdateRecommendations(mContext, playlist);
    }

    private void updateOrPublishChannel(Playlist playlist) {
        Log.d(TAG, "Syncing channel: " + playlist.getName());
        ChannelsProvider.createOrUpdateChannel(mContext, playlist);
    }
}
