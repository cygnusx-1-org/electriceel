package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;

import com.liskovsoft.mediaserviceinterfaces.ChannelGroupService;
import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup;
import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup.Item;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.prefs.HomeRowsData;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.Observable;

public class ChannelGroupServiceWrapper implements ProfileChangeListener {
    @SuppressLint("StaticFieldLeak")
    private static ChannelGroupServiceWrapper sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private ChannelGroupService mService;
    // The channels of every group, for the filter of the videos (see containsChannel), or null to read them again. Read off the main thread too.
    private GroupChannels mGroupChannels;

    /**
     * The ids and the names of the channels of the groups, as the groups were when they were read
     */
    private static final class GroupChannels {
        final List<ItemGroup> groups;
        final Set<String> ids = new HashSet<>();
        final Set<String> names = new HashSet<>();

        GroupChannels(List<ItemGroup> groups) {
            this.groups = groups;

            for (ItemGroup group : groups) {
                for (Item item : new ArrayList<>(group.getItems())) {
                    if (item.getChannelId() != null) {
                        ids.add(item.getChannelId());
                    }

                    if (item.getTitle() != null) {
                        names.add(item.getTitle());
                    }
                }
            }
        }

        /**
         * The same groups: the groups of another account, or restored ones, are other objects
         */
        boolean isOf(List<ItemGroup> groups) {
            if (groups.size() != this.groups.size()) {
                return false;
            }

            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i) != this.groups.get(i)) {
                    return false;
                }
            }

            return true;
        }
    }

    private ChannelGroupServiceWrapper(Context context) {
        mContext = context;
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    public static ChannelGroupServiceWrapper instance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new ChannelGroupServiceWrapper(context.getApplicationContext());
        }

        return sInstance;
    }

    public List<ItemGroup> getChannelGroups() {
        return getService().getChannelGroups();
    }

    /**
     * Also after its channels are changed: they're read again (see containsChannel)
     */
    public void addChannelGroup(ItemGroup group) {
        getService().addChannelGroup(group);
        onGroupsChanged();
    }

    /**
     * Its row of Home goes with it
     */
    public void removeChannelGroup(ItemGroup group) {
        getService().removeChannelGroup(group);
        onGroupsChanged();

        if (group != null) {
            HomeRowsData.instance(mContext).removeChannelGroup(group.getId());
        }
    }

    /**
     * The channel is in one of the groups: by the id when the card has one, otherwise by the name it had when it was added to the group
     * (most TV cards have only the name). Called for each video of the rows, maybe off the main thread.
     */
    public synchronized boolean containsChannel(String channelId, String channelName) {
        if (Helpers.allNulls(channelId, channelName)) {
            return false;
        }

        try {
            List<ItemGroup> groups = getChannelGroups();

            // Read again after a change here, or when they're other objects: the groups of another account, or restored
            if (mGroupChannels == null || !mGroupChannels.isOf(groups)) {
                mGroupChannels = new GroupChannels(groups);
            }

            return channelId != null ? mGroupChannels.ids.contains(channelId) : mGroupChannels.names.contains(channelName);
        } catch (ConcurrentModificationException e) {
            // Changed on the main thread meanwhile. Read again next time.
            mGroupChannels = null;
            return false;
        }
    }

    /**
     * The channels are read again
     */
    private synchronized void onGroupsChanged() {
        mGroupChannels = null;
    }

    public String[] findChannelIdsForGroup(String channelGroupId) {
        return getService().findChannelIdsForGroup(channelGroupId);
    }

    public ItemGroup findChannelGroupById(String channelGroupId) {
        return getService().findChannelGroupById(channelGroupId);
    }

    public ItemGroup findChannelGroupByTitle(String title) {
        return getService().findChannelGroupByTitle(title);
    }

    public ItemGroup createChannelGroup(String title, String iconUrl, List<Item> channels) {
        return getService().createChannelGroup(title, iconUrl, channels);
    }

    public Item createChannel(String title, String iconUrl, String channelId) {
        return getService().createChannel(channelId, title, iconUrl);
    }

    public void renameChannelGroup(ItemGroup channelGroup, String title) {
        getService().renameChannelGroup(channelGroup, title);
        onGroupsChanged();
    }

    public Observable<List<ItemGroup>> importGroupsObserve(Uri uri) {
        return getService().importGroupsObserve(uri).doOnNext(groups -> onGroupsChanged());
    }

    public Observable<List<ItemGroup>> importGroupsObserve(File file) {
        return getService().importGroupsObserve(file).doOnNext(groups -> onGroupsChanged());
    }

    public boolean isEmpty() {
        return getService().isEmpty();
    }

    private void restoreState() {
        String data = mPrefs.getChannelGroupData();
        if (data != null) {
            mPrefs.setChannelGroupData(null);
            getService().exportData(data);
        }

        onGroupsChanged();
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }

    private ChannelGroupService getService() {
        if (mService == null) {
            mService = YouTubeServiceManager.instance().getChannelGroupService();
        }

        return mService;
    }
}
