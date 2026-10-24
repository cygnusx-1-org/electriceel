package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The channels excluded from the hiding of the Hide or mark content filters, each from the ones picked
 * (Collaborations, Watch later, Streamed): their videos are marked instead (see VideoGroup).<br/>
 * A channel is known by its id, or by its name when a card has no id (most TV cards). Each account has its own channels.
 */
public class FilterExclusionData implements ProfileChangeListener {
    static final String FILTER_EXCLUSION_DATA = "filter_exclusion_data";
    // A bit per filter. Saved: don't change them.
    public static final int FILTER_COLLABORATIONS = 1;
    public static final int FILTER_WATCH_LATER = 1 << 1;
    public static final int FILTER_STREAMED = 1 << 2;
    /**
     * The filters a channel can be excluded from, in the order of the Hide or mark content settings.
     * Not Shows: a show card has the show, not its channel. Not Top channels and Explore topics: they hide whole rows.
     */
    public static final int[] FILTERS = {FILTER_COLLABORATIONS, FILTER_WATCH_LATER, FILTER_STREAMED};
    @SuppressLint("StaticFieldLeak")
    private static FilterExclusionData sInstance;
    private final AppPrefs mPrefs;
    // Read while the videos are added, maybe off the main thread. Replaced on each change.
    private volatile List<Channel> mChannels = Collections.emptyList();

    private static class Channel {
        final String channelId;
        final String channelName;
        final int filters;

        Channel(String channelId, String channelName, int filters) {
            this.channelId = channelId;
            this.channelName = channelName;
            this.filters = filters;
        }

        static Channel fromString(String specs) {
            String[] split = Helpers.splitObj(specs);

            if (split == null || split.length != 3) {
                return null;
            }

            Channel channel = new Channel(Helpers.parseStr(split[0]), Helpers.parseStr(split[1]), Helpers.parseInt(split, 2, 0));

            return channel.isEmpty() ? null : channel;
        }

        boolean isEmpty() {
            return filters == 0 || Helpers.allNulls(channelId, channelName);
        }

        /**
         * The same id when both have one, the same name otherwise
         */
        boolean isSame(String channelId, String channelName) {
            if (this.channelId != null && channelId != null) {
                return this.channelId.equals(channelId);
            }

            return this.channelName != null && this.channelName.equals(channelName);
        }

        @NonNull
        @Override
        public String toString() {
            return Helpers.mergeObj(channelId, channelName, filters);
        }
    }

    private FilterExclusionData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    public static FilterExclusionData instance(Context context) {
        if (sInstance == null) {
            sInstance = new FilterExclusionData(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * @param filter one of FILTER_*
     */
    public boolean isExcluded(int filter, String channelId, String channelName) {
        Channel channel = findChannel(mChannels, channelId, channelName);

        return channel != null && (channel.filters & filter) != 0;
    }

    /**
     * Keeps the id or the name the channel had when the card has none
     * @param filter one of FILTER_*
     */
    public void setExcluded(int filter, String channelId, String channelName, boolean exclude) {
        if (Helpers.allNulls(channelId, channelName)) {
            return;
        }

        List<Channel> channels = new ArrayList<>(mChannels);
        Channel channel = findChannel(channels, channelId, channelName);
        int filters = channel != null ? channel.filters : 0;
        int newFilters = exclude ? filters | filter : filters & ~filter;

        if (channel != null && newFilters == filters) {
            return;
        }

        int index = channels.indexOf(channel);

        if (channel != null) {
            channels.remove(channel);
            channelId = channelId != null ? channelId : channel.channelId;
            channelName = channelName != null ? channelName : channel.channelName;
        }

        Channel newChannel = new Channel(channelId, channelName, newFilters);

        if (!newChannel.isEmpty()) {
            channels.add(index != -1 ? index : channels.size(), newChannel);
        }

        mChannels = Collections.unmodifiableList(channels);
        persistState();
    }

    /**
     * @param filter one of FILTER_*
     * @return the id and the name of each channel excluded from the filter, by name
     */
    public List<Pair<String, String>> getChannels(int filter) {
        List<Channel> channels = new ArrayList<>();

        for (Channel channel : mChannels) {
            if ((channel.filters & filter) != 0) {
                channels.add(channel);
            }
        }

        Collections.sort(channels, (channel1, channel2) -> Helpers.compareToIgnoreCase(getTitle(channel1), getTitle(channel2)));

        List<Pair<String, String>> result = new ArrayList<>();

        for (Channel channel : channels) {
            result.add(new Pair<>(channel.channelId, channel.channelName));
        }

        return result;
    }

    private static String getTitle(Channel channel) {
        return channel.channelName != null ? channel.channelName : channel.channelId;
    }

    private static Channel findChannel(List<Channel> channels, String channelId, String channelName) {
        if (Helpers.allNulls(channelId, channelName)) {
            return null;
        }

        for (Channel channel : channels) {
            if (channel.isSame(channelId, channelName)) {
                return channel;
            }
        }

        return null;
    }

    private void restoreState() {
        String[] split = Helpers.splitData(mPrefs.getProfileData(FILTER_EXCLUSION_DATA));

        mChannels = Collections.unmodifiableList(Helpers.parseList(split, 0, Channel::fromString));
    }

    /**
     * Saved right away: a save still pending when the account changes would go to the new account
     */
    private void persistState() {
        mPrefs.setProfileData(FILTER_EXCLUSION_DATA, Helpers.mergeData(mChannels));
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
