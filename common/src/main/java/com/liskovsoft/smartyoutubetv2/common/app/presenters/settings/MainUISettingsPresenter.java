package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import android.os.Build;

import com.liskovsoft.leanbackassistant.media.ClipService;
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
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuManager;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.prefs.DeArrowData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLaterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.TopChannelsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ShowsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.ExploreTopicsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.HomeRowsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.HomeRowsData.Row;
import com.liskovsoft.smartyoutubetv2.common.prefs.HomeScreenPlaylistsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData.ColorScheme;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class MainUISettingsPresenter extends BasePresenter<Void> {
    private final MainUIData mMainUIData;
    private final GeneralData mGeneralData;
    private final PlayerData mPlayerData;
    private final DeArrowData mDeArrowData;
    private final SidebarService mSidebarService;
    private boolean mRestartApp;
    private final Runnable mOnFinish = () -> {
        if (mRestartApp) {
            mRestartApp = false;
            MessageHelpers.showLongMessage(getContext(), R.string.msg_restart_app);
        }
    };

    private MainUISettingsPresenter(Context context) {
        super(context);
        mMainUIData = MainUIData.instance(context);
        mGeneralData = GeneralData.instance(context);
        mPlayerData = PlayerData.instance(context);
        mDeArrowData = DeArrowData.instance(context);
        mSidebarService = SidebarService.instance(context);
    }

    public static MainUISettingsPresenter instance(Context context) {
        return new MainUISettingsPresenter(context);
    }

    /**
     * The Buttons options card of the User interface settings, in General: the buttons at the top of the main window (were in General)
     */
    public void showButtons() {
        showCategory(R.string.settings_buttons_options, this::appendTopButtonsCategory);
    }

    /**
     * The Color scheme options card of the User interface settings, in General (was in General)
     */
    public void showColors() {
        showCategory(R.string.settings_color_scheme_options, this::appendColorScheme);
    }

    /**
     * The Scale options card of the User interface settings, in General: the UI scale and the video grid scale (were in General)
     */
    public void showScale() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendScaleUI(settingsPresenter);
        if (Build.VERSION.SDK_INT > 19) {
            appendVideoGridScale(settingsPresenter);
        }

        settingsPresenter.showDialog(getContext().getString(R.string.settings_scale_options), mOnFinish);
    }

    /**
     * The Cards options card of the User interface settings, in General: the cards and where their thumbnails come from (were in General)
     */
    public void showCards() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        if (Build.VERSION.SDK_INT > 19) {
            appendCardTextScrollSpeed(settingsPresenter);
        }
        appendCardPreviews(settingsPresenter);
        appendCardStyle(settingsPresenter);
        appendThumbSource(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.settings_cards_options), mOnFinish);
    }

    /**
     * The Time options card of the User interface settings, in General: the time format and the clocks (were in Misc)
     */
    public void showTime() {
        showCategory(R.string.settings_time_options, this::appendTimeCategory);
    }

    private void appendTimeCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.time_format_24) + " " + getContext().getString(R.string.time_format),
                option -> {
                    mGeneralData.set24HourLocaleEnabled(option.isSelected());
                    mRestartApp = true;
                },
                mGeneralData.is24HourLocaleEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.app_corner_clock),
                option -> {
                    mGeneralData.setGlobalClockEnabled(option.isSelected());
                    mRestartApp = true;
                },
                mGeneralData.isGlobalClockEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_corner_clock),
                option -> mPlayerData.setGlobalClockEnabled(option.isSelected()),
                mPlayerData.isGlobalClockEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_corner_ending_time),
                option -> mPlayerData.setGlobalEndingTimeEnabled(option.isSelected()),
                mPlayerData.isGlobalEndingTimeEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.settings_time_options), options);
    }

    /**
     * The Channels options card of the User interface settings, in General: the sorting of the Channels section and its options (were in General and Misc)
     */
    public void showChannels() {
        showCategory(R.string.settings_channels_options, this::appendChannelOptionsCategory);
    }

    /**
     * One list: the sorting opens from it, the options are in it
     */
    private void appendChannelOptionsCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        OptionItem sorting = UiOptionItem.from(getContext().getString(R.string.channels_section_sorting), option -> showChannelSortingMenu());
        sorting.setMenu(true);
        options.add(sorting);

        options.add(UiOptionItem.from(getContext().getString(R.string.channels_old_look),
                optionItem -> {
                    mMainUIData.setUploadsOldLookEnabled(optionItem.isSelected());
                    mRestartApp = true;
                },
                mMainUIData.isUploadsOldLookEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.channels_filter),
                optionItem -> mMainUIData.setChannelsFilterEnabled(optionItem.isSelected()),
                mMainUIData.isChannelsFilterEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.channel_search_bar),
                optionItem -> mMainUIData.setChannelSearchBarEnabled(optionItem.isSelected()),
                mMainUIData.isChannelSearchBarEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.channels_auto_load),
                optionItem -> mMainUIData.setUploadsAutoLoadEnabled(optionItem.isSelected()),
                mMainUIData.isUploadsAutoLoadEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.settings_channels_options), options);
    }

    /**
     * The Misc options card of the User interface settings, in General (was Misc in the General panel)
     */
    public void showMisc() {
        showCategory(R.string.settings_misc_options, this::appendMiscCategory);
    }

    /**
     * The Boot to section card of the User interface settings, in General (was in the General panel)
     */
    public void showBootToSection() {
        showCategory(R.string.boot_to_section, this::appendBootToSection);
    }

    /**
     * The Set-up sections card of the User interface settings, in General (was in the General panel)
     */
    public void showEnabledSections() {
        showCategory(R.string.side_panel_sections, this::appendEnabledSections);
    }

    /**
     * The Home card of the User interface settings: the rows of Home and their order (see HomeRowsData).
     * Home is loaded again once it's closed.
     */
    public void showHome() {
        HomeRowsData data = HomeRowsData.instance(getContext());
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.home_rows), option -> showHomeRows()));

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.home_rows_order_youtube),
                getContext().getString(R.string.home_rows_order_youtube_desc),
                option -> {
                    if (option.isSelected()) {
                        data.orderYouTubeRows();
                    } else {
                        data.resetYouTubeOrder();
                    }
                },
                data.isYouTubeOrdered()));

        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.home_rows_order), option -> showHomeRowsOrder()));

        settingsPresenter.showDialog(getContext().getString(R.string.header_home), () -> BrowsePresenter.instance(getContext()).updateHomeRows());
    }

    private void showHomeRows() {
        MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);

        RxHelper.execute(
                getContentService().getSubscribedChannelsWithIdsObserve(),
                this::showHomeRows,
                error -> showHomeRows(null) // the added ones still can be removed
        );
    }

    /**
     * YouTube's rows that are on every load and the rest of them, but the ones Hide content takes out of Home anyway. Then
     * Subscriptions and the subscribed channels by name. The channels added that aren't subscribed (e.g. from a video of
     * Recommended) come after them.
     */
    private void showHomeRows(MediaGroup subscribedChannels) {
        HomeRowsData data = HomeRowsData.instance(getContext());
        List<OptionItem> options = new ArrayList<>();
        Set<String> listedIds = new HashSet<>();
        List<MediaItem> channels = subscribedChannels != null ? subscribedChannels.getMediaItems() : null;
        List<Integer> kinds = new ArrayList<>(HomeRowsData.getYouTubeKinds());
        kinds.add(MediaGroup.ROW_KIND_NONE); // the rest of them

        for (int kind : kinds) {
            if (!isHiddenByHideContent(kind)) {
                options.add(UiOptionItem.from(getContext().getString(getHomeRowKindTitle(kind)),
                        option -> data.setYouTubeRowShown(kind, option.isSelected()), data.isYouTubeRowShown(kind)));
            }
        }

        options.add(UiOptionItem.from(getContext().getString(R.string.header_subscriptions),
                option -> data.setSubscriptionsAdded(option.isSelected()), data.isSubscriptionsAdded()));

        if (channels != null) {
            for (MediaItem channel : channels) {
                String channelId = channel != null ? channel.getChannelId() : null;

                if (channelId != null && listedIds.add(channelId)) {
                    options.add(createHomeRowChannelOption(data, channelId, channel.getTitle() != null ? channel.getTitle() : channelId));
                }
            }
        }

        for (Row row : data.getRows()) {
            if (row.type == Row.TYPE_CHANNEL && listedIds.add(row.channelId)) {
                options.add(createHomeRowChannelOption(data, row.channelId, getHomeRowTitle(row, data)));
            }
        }

        MessageHelpers.cancelToasts();

        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());
        dialog.appendCheckedCategory(getContext().getString(R.string.home_rows), options);
        dialog.showDialog();
    }

    /**
     * Hide content takes the rows of the kind out of Home: their videos, or the whole row
     */
    private boolean isHiddenByHideContent(int kind) {
        switch (kind) {
            case MediaGroup.ROW_KIND_SHORTS:
                return MediaServiceData.instance().isContentHidden(MediaServiceData.CONTENT_SHORTS_HOME);
            case MediaGroup.ROW_KIND_SHOWS:
                return ShowsData.instance(getContext()).isHidingEnabled(MediaGroup.TYPE_HOME);
            case MediaGroup.ROW_KIND_TOP_CHANNELS:
                return TopChannelsData.instance(getContext()).isHidingEnabled(MediaGroup.TYPE_HOME);
            case MediaGroup.ROW_KIND_SEARCH_TOPICS:
                return ExploreTopicsData.instance(getContext()).isHidingEnabled(MediaGroup.TYPE_HOME);
            case MediaGroup.ROW_KIND_WATCH_LATER:
                return WatchLaterData.instance(getContext()).isHidingEnabled(MediaGroup.TYPE_HOME);
            default:
                return false;
        }
    }

    /**
     * In Home: an added row, or YouTube's not taken out by Home rows or Hide content. YouTube's rows as one (in YouTube's order)
     * are there while one of them is.
     */
    private boolean isHomeRowShown(Row row, HomeRowsData data) {
        if (row.type == Row.TYPE_KIND) {
            return data.isShown(row) && !isHiddenByHideContent(row.kind);
        }

        if (row.type != Row.TYPE_YOUTUBE) {
            return true;
        }

        if (data.isShown(row)) {
            return true;
        }

        if (data.isYouTubeOrdered()) {
            return false;
        }

        for (int kind : HomeRowsData.getYouTubeKinds()) {
            if (data.isYouTubeRowShown(kind) && !isHiddenByHideContent(kind)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checked, it's added at the top
     */
    private OptionItem createHomeRowChannelOption(HomeRowsData data, String channelId, String title) {
        return UiOptionItem.from(title, option -> {
            if (option.isSelected()) {
                data.addChannel(channelId, title);
            } else {
                data.removeChannel(channelId);
            }
        }, data.isChannelAdded(channelId));
    }

    /**
     * The rows put in order with the remote (see OptionCategory.reorderList). Read again each time it's opened.
     */
    private void showHomeRowsOrder() {
        HomeRowsData data = HomeRowsData.instance(getContext());
        List<OptionItem> options = new ArrayList<>();

        // The rest come after them (see HomeRowsData.setRows)
        for (Row row : data.getRows()) {
            if (isHomeRowShown(row, data)) {
                options.add(UiOptionItem.from(getHomeRowTitle(row, data), null, false, row));
            }
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());
        dialog.appendReorderCategory(getContext().getString(R.string.home_rows_order), options, items -> {
            List<Row> rows = new ArrayList<>();

            for (OptionItem item : items) {
                rows.add((Row) item.getData());
            }

            data.setRows(rows);
        });
        dialog.showDialog();
    }

    private String getHomeRowTitle(Row row, HomeRowsData data) {
        switch (row.type) {
            case Row.TYPE_YOUTUBE:
                return getContext().getString(data.isYouTubeOrdered() ? R.string.home_rows_other : R.string.home_rows_youtube);
            case Row.TYPE_SUBSCRIPTIONS:
                return getContext().getString(R.string.header_subscriptions);
            case Row.TYPE_CHANNEL:
                return row.title != null ? row.title : row.channelId;
            default:
                return getContext().getString(getHomeRowKindTitle(row.kind));
        }
    }

    private static int getHomeRowKindTitle(int kind) {
        switch (kind) {
            case MediaGroup.ROW_KIND_RECOMMENDED:
                return R.string.recommended;
            case MediaGroup.ROW_KIND_RECENTLY_UPLOADED:
                return R.string.recently_uploaded;
            case MediaGroup.ROW_KIND_CONTINUE_WATCHING:
                return R.string.continue_watching;
            case MediaGroup.ROW_KIND_WATCH_LATER:
                return R.string.watch_later;
            case MediaGroup.ROW_KIND_SHOWS:
                return R.string.shows;
            case MediaGroup.ROW_KIND_TOP_CHANNELS:
                return R.string.top_channels;
            case MediaGroup.ROW_KIND_SEARCH_TOPICS:
                return R.string.explore_topics;
            case MediaGroup.ROW_KIND_SHORTS:
                return R.string.header_shorts;
            default:
                return R.string.home_rows_other;
        }
    }

    /**
     * The Context menu card of the User interface settings, in General (was in the General panel): the items of the menu and
     * their order
     */
    public void showContextMenuItems() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.context_menu_items), option -> showContextMenuItemsList()));
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.context_menu_order), option -> showContextMenuOrder()));

        settingsPresenter.showDialog(getContext().getString(R.string.context_menu));
    }

    private void showContextMenuItemsList() {
        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());
        appendContextMenuItemsCategory(dialog);
        dialog.showDialog();
    }

    /**
     * The enabled items put in order with the remote (see OptionCategory.reorderList). Read again each time it's opened.
     */
    private void showContextMenuOrder() {
        List<OptionItem> options = new ArrayList<>();

        Map<Long, Integer> menuNames = getMenuNames();

        // The disabled ones come after them (see MainUIData.setMenuItemsOrder)
        for (Long menuItem : mMainUIData.getMenuItemsOrdered()) {
            Integer nameResId = menuNames.get(menuItem);

            if (nameResId != null && mMainUIData.isMenuItemEnabled(menuItem)) {
                options.add(UiOptionItem.from(getContext().getString(nameResId), null, false, menuItem));
            }
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());
        dialog.appendReorderCategory(getContext().getString(R.string.context_menu_order), options, items -> {
            List<Long> menuItems = new ArrayList<>();

            for (OptionItem item : items) {
                menuItems.add((Long) item.getData());
            }

            mMainUIData.setMenuItemsOrder(menuItems);
        });
        dialog.showDialog();
    }

    /**
     * The Android channels card of the User interface settings: the app's own channels (History, My videos...),
     * then the playlists of the Playlists section, shown as channels of the launcher's home screen. The ones added that aren't
     * there (deleted since) come after them.
     */
    public void showHomeScreenChannels() {
        MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);

        // Not getPlaylistsInfo: it has only the account's own playlists, not Liked videos and the saved ones
        RxHelper.execute(
                getContentService().getPlaylistsObserve(),
                this::showHomeScreenChannels,
                error -> showHomeScreenChannels(null) // the added ones still can be removed
        );
    }

    private void showHomeScreenChannels(MediaGroup myPlaylists) {
        HomeScreenPlaylistsData data = HomeScreenPlaylistsData.instance(getContext());
        Map<String, String> added = data.getPlaylists();
        boolean[] isChanged = {false};
        Map<String, String> playlists = new LinkedHashMap<>();
        List<MediaItem> mediaItems = myPlaylists != null ? myPlaylists.getMediaItems() : null;

        if (mediaItems != null) {
            for (MediaItem mediaItem : mediaItems) {
                Video video = Video.from(mediaItem);
                String playlistId = video != null ? video.findPlaylistId() : null;

                if (playlistId == null) {
                    continue;
                }

                String title = video.getTitle() != null ? video.getTitle() : playlistId;
                playlists.put(playlistId, title);

                // Renamed somewhere else
                if (added.containsKey(playlistId) && !title.equals(added.get(playlistId))) {
                    isChanged[0] |= data.rename(playlistId, title);
                }
            }
        }

        for (Entry<String, String> playlist : added.entrySet()) {
            if (!playlists.containsKey(playlist.getKey())) {
                playlists.put(playlist.getKey(), playlist.getValue());
            }
        }

        List<OptionItem> options = new ArrayList<>();

        // The app's own channels, named as in the launcher's list (sorted by name)
        appendHomeScreenChannel(options, data, R.string.header_history, ClipService.HISTORY_PROVIDER_ID, isChanged);
        appendHomeScreenChannel(options, data, R.string.my_videos, ClipService.MY_VIDEOS_PROVIDER_ID, isChanged);
        appendHomeScreenChannel(options, data, R.string.my_videos_shorts, ClipService.MY_SHORTS_PROVIDER_ID, isChanged);
        appendHomeScreenChannel(options, data, R.string.recommended, ClipService.RECOMMENDED_PROVIDER_ID, isChanged);
        appendHomeScreenChannel(options, data, R.string.header_subscriptions, ClipService.SUBSCRIPTIONS_PROVIDER_ID, isChanged);

        if (!playlists.isEmpty()) {
            OptionItem header = UiOptionItem.from(getContext().getString(R.string.header_playlists));
            header.setNote(true);
            options.add(header);
        }

        for (Entry<String, String> playlist : playlists.entrySet()) {
            options.add(UiOptionItem.from(playlist.getValue(), optionItem -> {
                if (optionItem.isSelected()) {
                    data.add(playlist.getKey(), playlist.getValue());
                } else {
                    data.remove(playlist.getKey());
                }

                isChanged[0] = true;
            }, added.containsKey(playlist.getKey())));
        }

        MessageHelpers.cancelToasts();

        String title = getContext().getString(R.string.home_screen_channels);
        String accountName = data.getAccountName();

        AppDialogPresenter dialog = AppDialogPresenter.instance(getContext());
        // Whose channels these are, named as in the launcher: it shows every account's
        dialog.appendCheckedCategory(accountName != null ? String.format("%s - %s", title, accountName) : title, options);
        dialog.showDialog(title, () -> {
            // Once for all the changes
            if (isChanged[0]) {
                Utils.updateChannels(getContext());
            }
        });
    }

    private void appendHomeScreenChannel(List<OptionItem> options, HomeScreenPlaylistsData data, int titleResId, String providerId,
                                         boolean[] isChanged) {
        options.add(UiOptionItem.from(getContext().getString(titleResId), optionItem -> {
            data.setChannelEnabled(providerId, optionItem.isSelected());
            isChanged[0] = true;
        }, data.isChannelEnabled(providerId)));
    }

    private interface CategoryAppender {
        void append(AppDialogPresenter settingsPresenter);
    }

    private void showCategory(int titleResId, CategoryAppender appender) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appender.append(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(titleResId), mOnFinish);
    }

    private void appendEnabledSections(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Integer, Integer> sections = mSidebarService.getDefaultSections();

        for (Entry<Integer, Integer> section : sections.entrySet()) {
            int sectionResId = section.getKey();
            int sectionId = section.getValue();

            if (sectionId == MediaGroup.TYPE_SETTINGS) {
                continue;
            }

            options.add(UiOptionItem.from(getContext().getString(sectionResId), optionItem -> {
                BrowsePresenter.instance(getContext()).enableSection(sectionId, optionItem.isSelected());
            }, mSidebarService.isSectionPinned(sectionId)));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.side_panel_sections), options);
    }

    private void appendContextMenuItemsCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Long, Integer> menuNames = getMenuNames();

        for (Long menuItem : mMainUIData.getMenuItemsOrdered()) {
            Integer nameResId = menuNames.get(menuItem);

            if (nameResId == null) {
                continue;
            }

            options.add(UiOptionItem.from(getContext().getString(nameResId), optionItem -> {
                if (optionItem.isSelected()) {
                    mMainUIData.setMenuItemEnabled(menuItem);
                } else {
                    mMainUIData.setMenuItemDisabled(menuItem);
                }
            }, mMainUIData.isMenuItemEnabled(menuItem)));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.context_menu_items), options);
    }

    private void appendBootToSection(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        Map<Integer, Integer> sections = mSidebarService.getDefaultSections();

        for (Entry<Integer, Integer> section : sections.entrySet()) {
            options.add(
                    UiOptionItem.from(
                            getContext().getString(section.getKey()),
                            optionItem -> mSidebarService.setBootSectionId(section.getValue()),
                            section.getValue().equals(mSidebarService.getBootSectionId())
                    )
            );
        }

        Collection<Video> pinnedItems = mSidebarService.getPinnedItems();

        for (Video item : pinnedItems) {
            if (item != null && item.getTitle() != null) {
                options.add(
                        UiOptionItem.from(
                                item.getTitle(),
                                optionItem -> mSidebarService.setBootSectionId(item.getId()),
                                item.hashCode() == mSidebarService.getBootSectionId()
                        )
                );
            }
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.boot_to_section), options);
    }

    private Map<Long, Integer> getMenuNames() {
        Map<Long, Integer> menuNames = new HashMap<>();
        menuNames.put(MainUIData.MENU_ITEM_EXIT_FROM_PIP, R.string.return_to_background_video);
        menuNames.put(MainUIData.MENU_ITEM_EXCLUDE_FROM_CONTENT_BLOCK, R.string.content_block_exclude_channel);
        menuNames.put(MainUIData.MENU_ITEM_MARK_AS_WATCHED, R.string.mark_as_watched);
        menuNames.put(MainUIData.MENU_ITEM_OPEN_CHANNEL, R.string.open_channel);
        menuNames.put(MainUIData.MENU_ITEM_UPDATE_CHECK, R.string.check_for_updates);
        menuNames.put(MainUIData.MENU_ITEM_CLEAR_HISTORY, R.string.clear_history);
        menuNames.put(MainUIData.MENU_ITEM_TOGGLE_HISTORY, R.string.disable_history);
        menuNames.put(MainUIData.MENU_ITEM_PLAYLIST_ORDER, R.string.playlist_order);
        menuNames.put(MainUIData.MENU_ITEM_PLAY_NEXT, R.string.play_next);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_QUEUE, R.string.add_remove_from_playback_queue);
        menuNames.put(MainUIData.MENU_ITEM_SHOW_QUEUE, R.string.action_playback_queue);
        menuNames.put(MainUIData.MENU_ITEM_STREAM_REMINDER, R.string.set_stream_reminder);
        menuNames.put(MainUIData.MENU_ITEM_SUBSCRIBE, R.string.subscribe_unsubscribe_from_channel);
        menuNames.put(MainUIData.MENU_ITEM_SAVE_REMOVE_PLAYLIST, R.string.save_remove_playlist);
        menuNames.put(MainUIData.MENU_ITEM_CREATE_PLAYLIST, R.string.create_playlist);
        menuNames.put(MainUIData.MENU_ITEM_CREATE_LOCAL_PLAYLIST, R.string.create_local_playlist);
        menuNames.put(MainUIData.MENU_ITEM_RENAME_PLAYLIST, R.string.rename_playlist);
        menuNames.put(MainUIData.MENU_ITEM_COPY_PLAYLIST_TO_YOUTUBE, R.string.copy_playlist_to_youtube);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_WATCH_LATER, R.string.add_video_to_watch_later);
        menuNames.put(MainUIData.MENU_ITEM_LIKE, R.string.like_remove_like);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_NEW_PLAYLIST, R.string.add_video_to_new_playlist);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_PLAYLIST, R.string.dialog_add_to_playlist);
        menuNames.put(MainUIData.MENU_ITEM_RECENT_PLAYLIST, R.string.add_remove_from_recent_playlist);
        menuNames.put(MainUIData.MENU_ITEM_PLAY_VIDEO, R.string.play_video);
        menuNames.put(MainUIData.MENU_ITEM_PLAY_VIDEO_INCOGNITO, R.string.play_video_incognito);
        menuNames.put(MainUIData.MENU_ITEM_PLAY_FROM_START, R.string.play_from_start);
        menuNames.put(MainUIData.MENU_ITEM_NOT_INTERESTED, R.string.not_interested);
        menuNames.put(MainUIData.MENU_ITEM_NOT_RECOMMEND_CHANNEL, R.string.not_recommend_channel);
        menuNames.put(MainUIData.MENU_ITEM_REMOVE_FROM_HISTORY, R.string.remove_from_history);
        menuNames.put(MainUIData.MENU_ITEM_REMOVE_FROM_SUBSCRIPTIONS, R.string.remove_from_subscriptions);
        menuNames.put(MainUIData.MENU_ITEM_PIN_TO_SIDEBAR, R.string.pin_unpin_from_sidebar);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_HOME_ROWS, R.string.add_remove_from_home_rows);
        menuNames.put(MainUIData.MENU_ITEM_ADD_TO_HOME_SCREEN, R.string.add_remove_from_home_screen);
        menuNames.put(MainUIData.MENU_ITEM_SHARE_LINK, R.string.share_link);
        menuNames.put(MainUIData.MENU_ITEM_SHARE_EMBED_LINK, R.string.share_embed_link);
        menuNames.put(MainUIData.MENU_ITEM_SHARE_QR_LINK, R.string.share_qr_link);
        menuNames.put(MainUIData.MENU_ITEM_SELECT_ACCOUNT, R.string.dialog_account_list);
        menuNames.put(MainUIData.MENU_ITEM_MOVE_SECTION_UP, R.string.move_section_up);
        menuNames.put(MainUIData.MENU_ITEM_MOVE_SECTION_DOWN, R.string.move_section_down);
        menuNames.put(MainUIData.MENU_ITEM_RENAME_SECTION, R.string.rename_section);
        menuNames.put(MainUIData.MENU_ITEM_OPEN_DESCRIPTION, R.string.action_video_info);
        menuNames.put(MainUIData.MENU_ITEM_OPEN_COMMENTS, R.string.open_comments);
        menuNames.put(MainUIData.MENU_ITEM_OPEN_PLAYLIST, R.string.open_playlist);
        menuNames.put(MainUIData.MENU_ITEM_BLOCK_CHANNEL, R.string.dialog_block_channel);
        menuNames.put(MainUIData.MENU_ITEM_REPORT_AI_CHANNEL, R.string.report_ai_channel);
        menuNames.put(MainUIData.MENU_ITEM_HIDE_KEYWORDS, R.string.keyword_filter_hide_words);

        for (ContextMenuProvider provider : new ContextMenuManager(getContext()).getProviders()) {
            menuNames.put(provider.getId(), provider.getTitleResId());
        }

        return menuNames;
    }

    private void appendTopButtonsCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.settings_search, MainUIData.TOP_BUTTON_SEARCH},
                {R.string.settings_language_country, MainUIData.TOP_BUTTON_CHANGE_LANGUAGE},
                {R.string.settings_accounts, MainUIData.TOP_BUTTON_BROWSE_ACCOUNTS}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                if (optionItem.isSelected()) {
                    mMainUIData.setTopButtonEnabled(pair[1]);
                } else {
                    mMainUIData.setTopButtonDisabled(pair[1]);
                }
            }, mMainUIData.isTopButtonEnabled(pair[1])));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.settings_buttons_options), options);
    }

    private void appendColorScheme(AppDialogPresenter settingsPresenter) {
        List<ColorScheme> colorSchemes = mMainUIData.getColorSchemes();

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.settings_color_scheme_options), fromColorSchemes(colorSchemes));
    }

    private List<OptionItem> fromColorSchemes(List<ColorScheme> colorSchemes) {
        List<OptionItem> styleOptions = new ArrayList<>();

        for (ColorScheme colorScheme : colorSchemes) {
            styleOptions.add(UiOptionItem.from(
                    getContext().getString(colorScheme.nameResId),
                    // The screens take the new colors by themselves once the settings are closed (see MotherActivity)
                    option -> mMainUIData.setColorScheme(colorScheme),
                    colorScheme.equals(mMainUIData.getColorScheme())));
        }

        return styleOptions;
    }

    private void appendCardPreviews(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.option_disabled, MainUIData.CARD_PREVIEW_DISABLED},
                {R.string.card_preview_full, MainUIData.CARD_PREVIEW_FULL},
                {R.string.card_preview_muted, MainUIData.CARD_PREVIEW_MUTED}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                mMainUIData.setCardPreviewType(pair[1]);
            }, mMainUIData.getCardPreviewType() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.card_preview), options);
    }

    private void appendCardStyle(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        OptionItem multilineTitle = UiOptionItem.from(getContext().getString(R.string.card_multiline_title),
                option -> mMainUIData.setCardMultilineTitleEnabled(option.isSelected()), mMainUIData.isCardMultilineTitleEnabled());

        OptionItem multilineSubtitle = UiOptionItem.from(getContext().getString(R.string.card_multiline_subtitle),
                option -> mMainUIData.setCardMultilineSubtitleEnabled(option.isSelected()), mMainUIData.isCardMultilineSubtitleEnabled());

        OptionItem autoScrolledTitle = UiOptionItem.from(getContext().getString(R.string.card_auto_scrolled_title),
                option -> mMainUIData.setCardTextAutoScrollEnabled(option.isSelected()), mMainUIData.isCardTextAutoScrollEnabled());

        OptionItem unlocalizedTitle = UiOptionItem.from(getContext().getString(R.string.card_unlocalized_titles),
                option -> mMainUIData.setUnlocalizedTitlesEnabled(option.isSelected()), mMainUIData.isUnlocalizedTitlesEnabled());

        OptionItem shortsDate = UiOptionItem.from(getContext().getString(R.string.card_shorts_date),
                option -> mMainUIData.setShortsDateEnabled(option.isSelected()), mMainUIData.isShortsDateEnabled());

        OptionItem roundedCardCorners = UiOptionItem.from(getContext().getString(R.string.rounded_card_corners),
                option -> {
                    if (option.isSelected()) {
                        mMainUIData.setUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS);
                    } else {
                        mMainUIData.setUiTweakDisabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS);
                    }
                    mRestartApp = true;
                },
                mMainUIData.isUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS));
        
        options.add(multilineTitle);
        options.add(multilineSubtitle);
        if (Build.VERSION.SDK_INT > 19) {
            options.add(autoScrolledTitle);
        }
        options.add(unlocalizedTitle);
        options.add(shortsDate);
        options.add(roundedCardCorners);

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.cards_style), options);
    }

    private void appendCardTitleLines(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int linesNum : new int[] {1, 2, 3, 4}) {
            options.add(UiOptionItem.from(String.format("%s", linesNum),
                    optionItem -> mMainUIData.setCartTitleLinesNum(linesNum),
                    linesNum == mMainUIData.getCardTitleLinesNum()));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.card_title_lines_num), options);
    }

    private void appendThumbSource(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.thumb_quality_default, ClickbaitRemover.THUMB_QUALITY_DEFAULT},
                {R.string.thumb_quality_start, ClickbaitRemover.THUMB_QUALITY_START},
                {R.string.thumb_quality_middle, ClickbaitRemover.THUMB_QUALITY_MIDDLE},
                {R.string.thumb_quality_end, ClickbaitRemover.THUMB_QUALITY_END}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mMainUIData.setThumbQuality(pair[1]),
                    mMainUIData.getThumbQuality() == pair[1]
                    ));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.card_content), options);
    }

    private void showChannelSortingMenu() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendChannelSortingCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.channels_section_sorting));
    }

    private void appendChannelSortingCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.sorting_last_viewed, MainUIData.CHANNEL_SORTING_LAST_VIEWED},
                {R.string.sorting_alphabetically, MainUIData.CHANNEL_SORTING_NAME},
                {R.string.sorting_by_new_content, MainUIData.CHANNEL_SORTING_NEW_CONTENT}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                mMainUIData.setChannelCategorySorting(pair[1]);
                BrowsePresenter.instance(getContext()).updateChannelSorting();
            }, mMainUIData.getChannelCategorySorting() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.channels_section_sorting), options);
    }

    private void appendPlaylistsCategoryStyle(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.playlists_style_grid, MainUIData.PLAYLISTS_STYLE_GRID},
                {R.string.playlists_style_rows, MainUIData.PLAYLISTS_STYLE_ROWS}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]), optionItem -> {
                mMainUIData.setPlaylistsStyle(pair[1]);
                BrowsePresenter.instance(getContext()).updatePlaylistsStyle();
            }, mMainUIData.getPlaylistsStyle() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.playlists_style), options);
    }

    private void appendScaleUI(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (float scale : new float[] {0.4f, 0.5f, 0.6f, 0.65f, 0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 0.95f, 1.0f, 1.05f, 1.1f, 1.15f, 1.2f, 1.25f, 1.3f, 1.35f, 1.4f}) {
            options.add(UiOptionItem.from(String.format("%sx", scale),
                    optionItem -> {
                        mMainUIData.setUIScale(scale);
                        mRestartApp = true;
                    },
                    Helpers.floatEquals(scale, mMainUIData.getUIScale())));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.scale_ui), options);
    }

    private void appendCardTextScrollSpeed(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (float factor : new float[] {1, 1.5f, 2, 2.5f, 3, 3.5f, 4, 4.5f, 5, 5.5f, 6, 6.5f, 7, 7.5f, 8}) {
            options.add(UiOptionItem.from(String.format("%sx", Helpers.formatFloat(factor)),
                    optionItem -> mMainUIData.setCardTextScrollSpeed(factor),
                    Helpers.floatEquals(factor, mMainUIData.getCardTextScrollSpeed())));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.card_text_scroll_factor), options);
    }

    private void appendVideoGridScale(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (float scale : new float[] {0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.25f, 1.3f, 1.35f, 1.4f, 1.5f}) {
            options.add(UiOptionItem.from(String.format("%sx", scale),
                    optionItem -> {
                        mMainUIData.setVideoGridScale(scale);
                        mRestartApp = true;
                    },
                    Helpers.floatEquals(scale, mMainUIData.getVideoGridScale())));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.video_grid_scale), options);
    }

    private void appendTimeFormatCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.time_format_24),
                option -> {
                    mGeneralData.set24HourLocaleEnabled(true);
                    mRestartApp = true;
                },
                mGeneralData.is24HourLocaleEnabled()));

        options.add(UiOptionItem.from(
                getContext().getString(R.string.time_format_12),
                option -> {
                    mGeneralData.set24HourLocaleEnabled(false);
                    mRestartApp = true;
                },
                !mGeneralData.is24HourLocaleEnabled()));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.time_format), options);
    }

    private void appendMiscCategory(AppDialogPresenter settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();
        
        options.add(UiOptionItem.from(getContext().getString(R.string.card_unlocalized_titles),
                option -> {
                    mMainUIData.setUnlocalizedTitlesEnabled(option.isSelected());
                    mDeArrowData.setReplaceTitlesEnabled(false);
                },
                mMainUIData.isUnlocalizedTitlesEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.fullscreen_mode),
                option -> {
                    mGeneralData.setFullscreenModeEnabled(option.isSelected());
                    mRestartApp = true;
                },
                mGeneralData.isFullscreenModeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.pinned_channel_rows),
                optionItem -> {
                    mMainUIData.setPinnedChannelRowsEnabled(optionItem.isSelected());
                    mRestartApp = true;
                },
                mMainUIData.isPinnedChannelRowsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.playlists_rows),
                optionItem -> {
                    mMainUIData.setPlaylistsStyle(optionItem.isSelected() ? MainUIData.PLAYLISTS_STYLE_ROWS : MainUIData.PLAYLISTS_STYLE_GRID);
                    BrowsePresenter.instance(getContext()).updatePlaylistsStyle();
                },
                mMainUIData.getPlaylistsStyle() == MainUIData.PLAYLISTS_STYLE_ROWS));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.settings_misc_options), options);
    }
}
