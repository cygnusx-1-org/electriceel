package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The videos marked "Not interested" and the channels marked "Don't recommend channel" in the app.<br/>
 * YouTube stops recommending them, but the rows already loaded, and the pages read before it catches up, still have them.
 * They're hidden there too (see VideoGroup#isNotInterested) until the app restarts.<br/>
 * Only for the account they were marked with. Marking with another one forgets them.
 */
public class NotInterestedManager {
    // Not lazy: the groups ask for it in the background
    private static final NotInterestedManager sInstance = new NotInterestedManager();
    // Read in the background while the groups are created
    private final Set<String> mVideoIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final List<Channel> mChannels = new CopyOnWriteArrayList<>();
    private volatile String mAccount; // the one they were marked with
    private AccountSource mAccountSource;

    interface AccountSource {
        /**
         * @return the signed-in account or null
         */
        String getAccount();
    }

    private static final class Channel {
        final String channelId;
        final String channelName;

        Channel(String channelId, String channelName) {
            this.channelId = channelId;
            this.channelName = channelName;
        }

        /**
         * By the id when both have one. Most TV cards have only the channel name.
         */
        boolean matches(String channelId, String channelName) {
            if (this.channelId != null && channelId != null) {
                return this.channelId.equals(channelId);
            }

            return this.channelName != null && this.channelName.equals(channelName);
        }
    }

    private NotInterestedManager() {
        mAccountSource = () -> WatchLaterManager.getAccountKey(MediaServiceManager.instance().getSelectedAccount());
    }

    public static NotInterestedManager instance() {
        return sInstance;
    }

    /**
     * The video was marked "Not interested"
     */
    public void addVideo(Video video) {
        if (!isVideoCard(video)) {
            return;
        }

        checkAccount();
        mVideoIds.add(video.videoId);
    }

    /**
     * The channel of the video was marked "Don't recommend channel"
     */
    public void addChannel(Video video) {
        if (video == null) {
            return;
        }

        String channelName = video.getAuthor();

        if (video.channelId == null && channelName == null) {
            return;
        }

        checkAccount();
        mChannels.add(new Channel(video.channelId, channelName));
    }

    /**
     * Marked "Not interested", or its channel "Don't recommend channel", with the signed-in account
     */
    public boolean isHidden(Video video) {
        // Channel cards and chapters aren't hidden
        if (video == null || video.isChapter || video.videoId == null || (mVideoIds.isEmpty() && mChannels.isEmpty())) {
            return false;
        }

        if (!Helpers.equals(mAccount, mAccountSource.getAccount())) {
            return false;
        }

        if (isVideoCard(video) && mVideoIds.contains(video.videoId)) {
            return true;
        }

        String channelName = video.getAuthor();

        for (Channel channel : mChannels) {
            if (channel.matches(video.channelId, channelName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Not a playlist: its video id is the first video's
     */
    private static boolean isVideoCard(Video video) {
        return video != null && video.videoId != null && video.itemType != MediaItem.TYPE_PLAYLIST && video.itemType != MediaItem.TYPE_CHANNEL;
    }

    /**
     * Forgets the ones marked with another account
     */
    private void checkAccount() {
        String account = mAccountSource.getAccount();

        if (!Helpers.equals(account, mAccount)) {
            mVideoIds.clear();
            mChannels.clear();
            mAccount = account;
        }
    }

    /**
     * Reads the account with the given one from now on and forgets them
     */
    void resetForTesting(AccountSource accountSource) {
        mAccountSource = accountSource;
        mVideoIds.clear();
        mChannels.clear();
        mAccount = null;
    }
}
