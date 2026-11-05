package com.liskovsoft.smartyoutubetv2.common.app.presenters.service;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup.ChannelGroupServiceWrapper;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.HiddenVideosData;
import com.liskovsoft.smartyoutubetv2.common.prefs.HomeRowsData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.ServiceProfileData;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * What refers to a subscription group or a playlist kept on the device by its id: the Home rows, the pinned items (with the boot
 * section, the remembered positions and the hidden videos of their sections), the last playlist and the playlists' sort
 * orders. A group's id comes from its title, so the same group made on two devices of an account has one id; one made before
 * that had a random id, and has it as an alias now (see ItemGroupImpl.normalized). These are brought to the ids the groups
 * have now, the same on every device, as they're synced: one device's Home rows find the groups on the other.<br/>
 * The launcher's playlists keep their ids: its channel of a playlist is known by it (see ClipService.createUserPlaylist), and a
 * new channel would have to be enabled again. They find their playlists by the aliases.
 */
public final class GroupReferences implements ProfileChangeListener {
    private static final String TAG = GroupReferences.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static GroupReferences sInstance;
    private final Context mContext;
    private final Runnable mUpdate = this::update;

    private GroupReferences(Context context) {
        mContext = context;
        AppPrefs.instance(context).addListener(this);
    }

    public static GroupReferences instance(Context context) {
        if (sInstance == null) {
            sInstance = new GroupReferences(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * Only the data already read: a new HomeRowsData or SidebarService would read its data now, and write its defaults over the
     * data an account's sign-in copies (see AppPrefs). Browse reads both when it starts, then calls this (see BrowsePresenter).
     * On the main thread.
     */
    public void update() {
        try {
            updateInt();
        } catch (RuntimeException e) {
            // Upkeep, at the start of Browse too: the references still find their groups by the aliases (e.g. a group list
            // changed by another thread meanwhile), and they're brought up to date at the next start
            Log.e(TAG, "Updating the references to the groups: %s", e);
        }
    }

    private void updateInt() {
        Function<String, String> channelGroupId = id -> {
            ItemGroup group = ChannelGroupServiceWrapper.instance(mContext).findChannelGroupById(id);
            return group != null ? group.getId() : null;
        };
        Function<String, String> playlistId = ServiceProfileData::playlistGroupId;

        HomeRowsData homeRows = HomeRowsData.peekInstance();

        if (homeRows != null) {
            homeRows.normalizeChannelGroupIds(channelGroupId);
        }

        SidebarService sidebar = SidebarService.peekInstance();
        Map<Integer, Integer> sectionIds = sidebar != null ? sidebar.normalizeGroupIds(channelGroupId, playlistId) : null;

        GeneralData.instance(mContext).normalizeGroupIds(channelGroupId, playlistId,
                sectionIds != null ? sectionIds : Collections.emptyMap());

        if (sectionIds != null && !sectionIds.isEmpty()) {
            HiddenVideosData.instance(mContext).moveSections(sectionIds);
        }
    }

    /**
     * After the other listeners: the data is the new account's then
     */
    @Override
    public void onProfileChanged() {
        post();
    }

    /**
     * Another device's groups: one made there before the ids came from the titles has another alias
     */
    @Override
    public void onProfileDataSynced(Set<String> keys) {
        if (keys.contains(ServiceProfileData.SUBSCRIPTION_GROUPS) || keys.contains(ServiceProfileData.PLAYLIST_GROUPS)) {
            post();
        }
    }

    private void post() {
        Utils.removeCallbacks(mUpdate);
        Utils.post(mUpdate);
    }
}
