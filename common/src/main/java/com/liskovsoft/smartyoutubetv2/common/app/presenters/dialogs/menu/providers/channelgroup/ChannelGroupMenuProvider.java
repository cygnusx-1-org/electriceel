package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ChannelGroupMenuProvider extends ContextMenuProvider {
    private final ChannelGroupServiceWrapper mService;

    public ChannelGroupMenuProvider(@NonNull Context context, int idx) {
        super(context, idx);
        mService = ChannelGroupServiceWrapper.instance(context);
    }

    @Override
    public int getTitleResId() {
        return R.string.add_to_subscriptions_group;
    }

    @Override
    public void onClicked(Video item, VideoMenuCallback callback) {
        showGroupDialogAndFetchChannel(item, callback);
    }

    @Override
    public boolean isEnabled(Video item) {
        return item != null && (item.channelId != null || item.videoId != null);
    }

    @Override
    public int getMenuType() {
        return MENU_TYPE_VIDEO;
    }

    private void showGroupDialogAndFetchChannel(Video item, VideoMenuCallback callback) {
        if (item.hasChannel()) {
            showGroupDialog(item, callback);
        } else {
            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);

            MediaServiceManager.instance().loadMetadata(item, metadata -> {
                item.sync(metadata);
                showGroupDialog(item, callback);
            });
        }
    }

    /**
     * The groups of a channel card of the Channels section. The card has no channel id: one would make it open the channel
     * instead of its uploads (see TabMediaItem of MediaServiceCore). The id is found by the card's name among the subscribed
     * channels: loading its uploads would mark the channel as watched (see ChannelUploadsMenuPresenter.appendMarkAsWatched).
     */
    public void showGroupDialogForChannelsCard(Video card) {
        if (card == null || card.title == null) {
            return;
        }

        MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);

        RxHelper.execute(YouTubeServiceManager.instance().getContentService().getSubscribedChannelsWithIdsObserve(),
                channels -> {
                    // The activity is gone (see ContextMenuProvider)
                    if (getContext() == null) {
                        return;
                    }

                    String channelId = findChannelId(channels, card.title);

                    if (channelId == null) {
                        MessageHelpers.showMessage(getContext(), R.string.subscription_group_channel_not_found);
                        return;
                    }

                    // Not the card: it stays one of the Channels section
                    Video channel = new Video();
                    channel.channelId = channelId;
                    channel.title = card.title;
                    channel.author = card.title;
                    channel.cardImageUrl = card.cardImageUrl;
                    showGroupDialog(channel, null);
                },
                error -> MessageHelpers.showLongMessage(getContext(), error.getMessage()));
    }

    /**
     * The id of the subscribed channel of the name, or null
     */
    private static String findChannelId(MediaGroup channels, String title) {
        List<MediaItem> items = channels != null ? channels.getMediaItems() : null;

        if (items == null) {
            return null;
        }

        for (MediaItem item : items) {
            if (item != null && item.getChannelId() != null && title.equals(item.getTitle())) {
                return item.getChannelId();
            }
        }

        return null;
    }

    private void showGroupDialog(Video item, VideoMenuCallback callback) {
        if (item == null || item.channelId == null) {
            return;
        }

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());

        List<ItemGroup> groups = mService.getChannelGroups();

        List<OptionItem> options = new ArrayList<>();

        // Create new group or enter url
        String editDialogTitle = getContext().getString(R.string.new_subscriptions_group);
        options.add(UiOptionItem.from(editDialogTitle, optionItem -> {
            dialogPresenter.closeDialog();
            SimpleEditDialog.showName(getContext(), editDialogTitle,
                    null,
                    newValue -> {
                        if (mService.findChannelGroupByTitle(newValue) != null) {
                            return false;
                        }

                        if (Helpers.contains(newValue, "/") && !isUrl(newValue)) {
                            newValue = String.format("https://%s", newValue);
                        }

                        if (isUrl(newValue)) {
                            newValue = toLowerCaseScheme(newValue);
                            RxHelper.execute(mService.importGroupsObserve(Uri.parse(newValue)), this::pinGroups,
                                    error -> MessageHelpers.showLongMessage(getContext(), error.getMessage()));
                        } else {
                            ItemGroup group = mService.createChannelGroup(newValue, null,
                                    Collections.singletonList(mService.createChannel(item.getAuthor(), item.cardImageUrl, item.channelId)));
                            mService.addChannelGroup(group);
                            BrowsePresenter.instance(getContext()).pinItem(Video.from(group));
                            MessageHelpers.showMessage(getContext(), getContext().getString(R.string.pinned_to_sidebar));
                        }
                        return true;
                    });
        }, false));

        for (ItemGroup group : groups) {
            options.add(UiOptionItem.from(group.getTitle(), optionItem -> {
                BrowsePresenter presenter = BrowsePresenter.instance(getContext());

                if (optionItem.isSelected()) {
                    group.add(mService.createChannel(item.getAuthor(), item.cardImageUrl, item.channelId));
                } else {
                    group.remove(item.channelId);
                    Object data = presenter.getCurrentSection() != null ? presenter.getCurrentSection().getData() : null;
                    if (callback != null && (data instanceof Video) && Helpers.equals(((Video) data).channelGroupId, group.getId())) {
                        callback.onItemAction(item, VideoMenuCallback.ACTION_REMOVE_AUTHOR);
                    }
                }

                if (!group.isEmpty()) {
                    mService.addChannelGroup(group);

                    // Its grid gets the channels of now. One left out of the sidebar stays out (see Set-up sections).
                    if (SidebarService.instance(getContext()).isPinned(Video.from(group))) {
                        presenter.pinItem(Video.from(group));
                    }
                } else {
                    mService.removeChannelGroup(group);
                    presenter.unpinItem(Video.from(group));
                }
            }, group.contains(item.channelId)));
        }

        dialogPresenter.appendCheckedCategory(getContext().getString(getTitleResId()), options);
        dialogPresenter.showDialog(getContext().getString(getTitleResId()));
    }

    /**
     * A link to import groups from. The keyboard starts with a capital letter (see SimpleEditDialog.showName): "Https://..." too.
     * Not a name that starts with the word (e.g. "HTTP tips").
     */
    private static boolean isUrl(String value) {
        return value.regionMatches(true, 0, "http://", 0, 7) || value.regionMatches(true, 0, "https://", 0, 8);
    }

    private static String toLowerCaseScheme(String url) {
        int schemeEnd = url.indexOf("://");

        return schemeEnd != -1 ? url.substring(0, schemeEnd).toLowerCase(Locale.ROOT) + url.substring(schemeEnd) : url;
    }

    private void pinGroups(@NonNull List<ItemGroup> newGroups) {
        if (newGroups.isEmpty()) {
            return;
        }

        for (ItemGroup group : newGroups) {
            BrowsePresenter.instance(getContext()).pinItem(Video.from(group));
        }
        MessageHelpers.showMessage(getContext(), getContext().getString(R.string.pinned_to_sidebar));
    }
}
