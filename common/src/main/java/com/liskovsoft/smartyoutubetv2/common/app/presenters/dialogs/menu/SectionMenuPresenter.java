package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.MenuAction;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuManager;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SectionMenuPresenter extends BaseMenuPresenter {
    private static final String TAG = SectionMenuPresenter.class.getSimpleName();
    private final AppDialogPresenter mDialogPresenter;
    private Video mVideo;
    private BrowseSection mSection;
    private boolean mIsReturnToBackgroundVideoEnabled;
    private boolean mIsMarkAllChannelsWatchedEnabled;
    private boolean mIsRefreshEnabled;
    private boolean mIsMoveSectionUpEnabled;
    private boolean mIsMoveSectionDownEnabled;
    private boolean mIsRenameSectionEnabled;
    private final Map<Long, MenuAction> mMenuMapping = new HashMap<>();

    private SectionMenuPresenter(Context context) {
        super(context);
        mDialogPresenter = AppDialogPresenter.instance(context);

        initMenuMapping();
    }

    public static SectionMenuPresenter instance(Context context) {
        return new SectionMenuPresenter(context);
    }

    @Override
    protected @Nullable Video getVideo() {
        return mVideo;
    }

    @Override
    protected int getMenu() {
        return MainUIData.MENU_SIDEBAR;
    }

    @Override
    protected BrowseSection getSection() {
        return mSection;
    }

    @Override
    protected AppDialogPresenter getDialogPresenter() {
        return mDialogPresenter;
    }

    @Override
    protected VideoMenuCallback getCallback() {
        return null;
    }

    public void showMenu(BrowseSection section) {
        if (section == null) {
            return;
        }

        disposeActions();

        mSection = section;
        mVideo = section.getData() instanceof Video ? (Video) section.getData() : null;

        MediaServiceManager.instance().authCheck(this::obtainPlaylistsAndShowDialogSigned, this::prepareAndShowDialogUnsigned);
    }

    private void obtainPlaylistsAndShowDialogSigned() {
        prepareAndShowDialogSigned();
    }

    private void prepareAndShowDialogSigned() {
        if (getContext() == null) {
            return;
        }

        // Not in the settings: first, then the ones in their order
        appendReturnToBackgroundVideoButton();
        appendRefreshButton();
        appendMarkAllChannelsWatchedButton();
        appendRemoveWatchedButton();

        for (Long menuItem : MainUIData.instance(getContext()).getMenuItemsOrdered(MainUIData.MENU_SIDEBAR)) {
            MenuAction menuAction = mMenuMapping.get(menuItem);
            if (menuAction != null) {
                menuAction.run();
            }
        }

        if (!mDialogPresenter.isEmpty()) {
            String title = mSection != null ? mSection.getTitle() : null;
            mDialogPresenter.showDialog(title, this::disposeActions);
        }
    }

    private void prepareAndShowDialogUnsigned() {
        if (getContext() == null) {
            return;
        }

        appendReturnToBackgroundVideoButton();
        appendRefreshButton();

        for (Long menuItem : MainUIData.instance(getContext()).getMenuItemsOrdered(MainUIData.MENU_SIDEBAR)) {
            MenuAction menuAction = mMenuMapping.get(menuItem);
            if (menuAction != null && !menuAction.isAuth()) {
                menuAction.run();
            }
        }

        if (!mDialogPresenter.isEmpty()) {
            String title = mSection != null ? mSection.getTitle() : null;
            mDialogPresenter.showDialog(title, this::disposeActions);
        }
    }

    private void appendRefreshButton() {
        if (!mIsRefreshEnabled) {
            return;
        }

        if (mSection == null || mSection.getId() == MediaGroup.TYPE_SETTINGS) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.refresh_section), optionItem -> {
                    if (BrowsePresenter.instance(getContext()).getView() != null) {
                        BrowsePresenter.instance(getContext()).getView().focusOnContent();
                        BrowsePresenter.instance(getContext()).refresh();
                    }
                    mDialogPresenter.closeDialog();
                }));
    }

    private void appendMoveSectionUpButton() {
        if (!mIsMoveSectionUpEnabled || !canMoveSection()) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.move_section_up), optionItem -> {
                    //mDialogPresenter.closeDialog();
                    BrowsePresenter.instance(getContext()).moveSectionUp(mSection);
                }));
    }

    private void appendMoveSectionDownButton() {
        if (!mIsMoveSectionDownEnabled || !canMoveSection()) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.move_section_down), optionItem -> {
                    //mDialogPresenter.closeDialog();
                    BrowsePresenter.instance(getContext()).moveSectionDown(mSection);
                }));
    }

    /**
     * Settings is always last (see SidebarService.keepSettingsLast)
     */
    private boolean canMoveSection() {
        return mSection != null && mSection.getId() != MediaGroup.TYPE_SETTINGS;
    }

    private void appendRenameSectionButton() {
        if (!mIsRenameSectionEnabled) {
            return;
        }

        if (mSection == null || mSection.isDefault() || getVideo() == null ||
                (!getVideo().hasPlaylist() && !getVideo().hasReloadPageKey() && !getVideo().hasChannel())) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.rename_section), optionItem -> {
                    mDialogPresenter.closeDialog();
                    SimpleEditDialog.show(
                            getContext(),
                            getContext().getString(R.string.rename_section),
                            mSection.getTitle(),
                            newValue -> {
                                mSection.setTitle(newValue);
                                BrowsePresenter.instance(getContext()).renameSection(mSection);
                                return true;
                            });
                }));
    }

    private void appendReturnToBackgroundVideoButton() {
        if (!mIsReturnToBackgroundVideoEnabled || !PlaybackPresenter.instance(getContext()).isRunningInBackground()) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.return_to_background_video),
                        // Assume that the Playback view already blocked and remembered.
                        optionItem -> getViewManager().startView(SplashView.class)
                )
        );
    }

    private void appendMarkAllChannelsWatchedButton() {
        if (!mIsMarkAllChannelsWatchedEnabled) {
            return;
        }

        if (mSection == null || mSection.getId() != MediaGroup.TYPE_CHANNEL_UPLOADS) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.mark_all_channels_watched), optionItem -> {
                    mDialogPresenter.closeDialog();

                    MediaServiceManager serviceManager = MediaServiceManager.instance();

                    MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);

                    serviceManager.loadSubscribedChannels(group -> {
                        Iterator<MediaItem> iterator = group.getMediaItems().iterator();

                        processNextChannel(serviceManager, iterator);
                    });
                }));
    }

    /**
     * Only in the Watch later section. YouTube picks the videos: every one with any of it watched.
     */
    private void appendRemoveWatchedButton() {
        if (mSection == null || mSection.getId() != MediaGroup.TYPE_WATCH_LATER) {
            return;
        }

        mDialogPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.remove_watched_videos),
                        optionItem -> AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.remove_watched_videos), () -> {
                            mDialogPresenter.closeDialog();
                            MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);

                            MediaServiceManager.instance().removeWatchedFromWatchLater(getContext(), () -> {
                                refreshWatchLaterSection();
                                MessageHelpers.showMessage(getContext(), R.string.msg_done);
                            }, error -> {
                                // YouTube might have removed them all the same (see removeWatchedFromWatchLater)
                                refreshWatchLaterSection();
                                MessageHelpers.showLongMessage(getContext(), error.getMessage());
                            });
                        })));
    }

    private void refreshWatchLaterSection() {
        BrowsePresenter presenter = BrowsePresenter.instance(getContext());

        if (presenter.isWatchLaterSection()) {
            presenter.refresh();
        }
    }

    private void processNextChannel(MediaServiceManager serviceManager, Iterator<MediaItem> iterator) {
        if (iterator.hasNext()) {
            MediaItem next = iterator.next();

            if (!next.hasNewContent()) {
                processNextChannel(serviceManager, iterator);
                return;
            }

            MessageHelpers.showMessage(getContext(), next.getTitle());
            serviceManager.loadChannelUploads(next, (groupTmp) -> processNextChannel(serviceManager, iterator));
        } else {
            MessageHelpers.showMessage(getContext(), R.string.msg_done);
        }
    }

    private void disposeActions() {
        //RxUtils.disposeActions(mPlaylistAction);
    }

    @Override
    protected void updateEnabledMenuItems() {
        super.updateEnabledMenuItems();

        mIsReturnToBackgroundVideoEnabled = true;
        mIsRefreshEnabled = true;
        mIsMarkAllChannelsWatchedEnabled = true;

        MainUIData mainUIData = MainUIData.instance(getContext());

        mIsMoveSectionUpEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_SIDEBAR, MainUIData.MENU_ITEM_MOVE_SECTION_UP);
        mIsMoveSectionDownEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_SIDEBAR, MainUIData.MENU_ITEM_MOVE_SECTION_DOWN);
        mIsRenameSectionEnabled = mainUIData.isMenuItemEnabled(MainUIData.MENU_SIDEBAR, MainUIData.MENU_ITEM_RENAME_SECTION);
    }

    /**
     * The items of the menu in the settings (see MainUIData.MENU_SIDEBAR). Those for the signed in only are like before they
     * followed an order.
     */
    private void initMenuMapping() {
        mMenuMapping.clear();

        // Only one of them at a time: the pinned channel or playlist, or the section
        mMenuMapping.put(MainUIData.MENU_ITEM_PIN_TO_SIDEBAR, new MenuAction(() -> { appendUnpinVideoFromSidebarButton(); appendUnpinSectionFromSidebarButton(); }, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_ADD_TO_HOME_SCREEN, new MenuAction(this::appendToggleHomeScreenPlaylistButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_ADD_TO_HOME_ROWS, new MenuAction(this::appendToggleSectionHomeRowButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_SELECT_ACCOUNT, new MenuAction(this::appendAccountSelectionButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_MOVE_SECTION_UP, new MenuAction(this::appendMoveSectionUpButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_MOVE_SECTION_DOWN, new MenuAction(this::appendMoveSectionDownButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_RENAME_SECTION, new MenuAction(this::appendRenameSectionButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_CREATE_PLAYLIST, new MenuAction(this::appendCreatePlaylistButton, true));
        mMenuMapping.put(MainUIData.MENU_ITEM_CREATE_LOCAL_PLAYLIST, new MenuAction(this::appendCreateLocalPlaylistButton, true));
        mMenuMapping.put(MainUIData.MENU_ITEM_TOGGLE_HISTORY, new MenuAction(this::appendToggleHistoryButton, true));
        mMenuMapping.put(MainUIData.MENU_ITEM_CLEAR_HISTORY, new MenuAction(this::appendClearHistoryButton, false));
        mMenuMapping.put(MainUIData.MENU_ITEM_UPDATE_CHECK, new MenuAction(this::appendUpdateCheckButton, true));

        for (ContextMenuProvider provider : new ContextMenuManager(getContext()).getProviders()) {
            if (provider.getMenuType() != ContextMenuProvider.MENU_TYPE_SECTION) {
                continue;
            }
            mMenuMapping.put(provider.getId(), new MenuAction(() -> appendContextMenuItem(provider), false));
        }
    }

    private void appendContextMenuItem(ContextMenuProvider provider) {
        MainUIData mainUIData = MainUIData.instance(getContext());
        if (mainUIData.isMenuItemEnabled(MainUIData.MENU_SIDEBAR, provider.getId()) && provider.isEnabled(getVideo())) {
            mDialogPresenter.appendSingleButton(
                    UiOptionItem.from(getContext().getString(provider.getTitleResId()), optionItem -> provider.onClicked(getVideo(), getCallback()))
            );
        }
    }
}
