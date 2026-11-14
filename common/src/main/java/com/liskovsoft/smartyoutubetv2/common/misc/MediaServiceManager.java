package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.NotificationsService;
import com.liskovsoft.mediaserviceinterfaces.SignInService;
import android.os.SystemClock;

import com.liskovsoft.mediaserviceinterfaces.SignInService.OnAccountChange;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.NotificationState;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.AccountsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.LoadingManager;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class MediaServiceManager implements OnAccountChange {
    private static final String TAG = MediaServiceManager.class.getSimpleName();
    private static final long WARM_UP_AGAIN_MS = 10 * 60 * 1_000;
    private static MediaServiceManager sInstance;
    private final MediaItemService mItemService;
    private final ContentService mContentService;
    private final SignInService mSignInService;
    private final NotificationsService mNotificationsService;
    private Disposable mMetadataAction;
    private Disposable mUploadsAction;
    private Disposable mRowsAction;
    private Disposable mSubscribedChannelsAction;
    private Disposable mFormatInfoAction;
    private Disposable mPlaylistGroupAction;
    private Disposable mPlaylistInfosAction;
    private Disposable mHistoryAction;
    private static final int MIN_GRID_GROUP_SIZE = 13;
    private static final int MIN_ROW_GROUP_SIZE = 5;
    private static final int MIN_SCALED_GRID_GROUP_SIZE = 35;
    private static final int MIN_SCALED_ROW_GROUP_SIZE = 10;
    private final GroupFiller mGroupFiller = new GroupFiller();
    private final List<AccountChangeListener> mAccountListeners = new CopyOnWriteArrayList<>();
    private boolean mIsPlaybackWarmedUp;
    /**
     * By the first section's load, before its videos were shown (see #warmUpPlaybackOnLoad)
     */
    private boolean mIsPlaybackWarmUpStarted;
    private long mWarmUpTimeMs;

    public interface OnMetadata {
        void onMetadata(MediaItemMetadata metadata);
    }

    public interface OnMediaGroup {
        void onMediaGroup(MediaGroup group);
    }

    public interface OnMediaGroupList {
        void onMediaGroupList(List<MediaGroup> groupList);
    }

    public interface OnFormatInfo {
        void onFormatInfo(MediaItemFormatInfo formatInfo);
    }

    public interface OnAccountList {
        void onAccountList(List<Account> accountList);
    }

    public interface OnPlaylistInfos {
        void onPlaylistInfos(List<PlaylistInfo> playlistInfos);
    }

    public interface AccountChangeListener {
        void onAccountChanged(Account account);
    }

    public interface OnError {
        void onError(Throwable error);
    }

    public interface OnComplete {
        void onComplete();
    }

    private MediaServiceManager() {
        ServiceManager service = YouTubeServiceManager.instance();
        mItemService = service.getMediaItemService();
        mContentService = service.getContentService();
        mSignInService = service.getSignInService();
        mNotificationsService = service.getNotificationsService();

        mSignInService.addOnAccountChange(this);
    }

    public static MediaServiceManager instance() {
        if (sInstance == null) {
            sInstance = new MediaServiceManager();
        }

        return sInstance;
    }

    public void loadMetadata(MediaItem mediaItem, OnMetadata onMetadata) {
        loadMetadata(mediaItem, onMetadata, null, null);
    }

    /**
     * NOTE: Load suggestions from MediaItem isn't robust. Because playlistId may be initialized from RemoteControlManager.
     */
    public void loadMetadata(MediaItem mediaItem, OnMetadata onMetadata, OnError onError, OnComplete onComplete) {
        loadMetadata(Video.from(mediaItem), onMetadata, onError, onComplete);
    }

    public void loadMetadata(Video video, OnMetadata onMetadata) {
        loadMetadata(video, onMetadata, null, null);
    }

    public void loadMetadata(Video video, OnMetadata onMetadata, OnError onError, OnComplete onComplete) {
        if (video == null) {
            return;
        }

        RxHelper.disposeActions(mMetadataAction);

        Observable<MediaItemMetadata> observable;

        // NOTE: Load suggestions from mediaItem isn't robust. Because playlistId may be initialized from RemoteControlManager.
        // Video might be loaded from Channels section (has playlistParams)
        if (video.mediaItem != null) {
            // Use additional data like playlist id
            observable = mItemService.getMetadataObserve(video.mediaItem);
        } else {
            // Simply load
            observable = mItemService.getMetadataObserve(video.videoId, video.getPlaylistId(), video.playlistIndex, video.playlistParams);
        }

        mMetadataAction = observable
                .subscribe(
                        onMetadata::onMetadata,
                        error -> {
                            Log.e(TAG, "loadMetadata error: %s", error.getMessage());
                            if (onError != null) {
                                onError.onError(error);
                            }
                        },
                        () -> {
                            if (onComplete != null) {
                                onComplete.onComplete();
                            }
                        }
                );
    }

    public void loadChannelUploads(Video item, OnMediaGroup onMediaGroup) {
        if (item == null) {
            return;
        }

        loadChannelUploads(item.mediaItem, onMediaGroup);
    }

    public void loadChannelUploads(MediaItem item, OnMediaGroup onMediaGroup) {
        if (item == null) {
            return;
        }

        RxHelper.disposeActions(mUploadsAction);

        Observable<MediaGroup> observable = mContentService.getGroupObserve(item);

        mUploadsAction = observable
                .subscribe(
                        onMediaGroup::onMediaGroup,
                        error -> {
                            onMediaGroup.onMediaGroup(null);
                            Log.e(TAG, "loadChannelUploads error: %s", error.getMessage());
                        }
                );
    }

    public void loadSubscribedChannels(OnMediaGroup onMediaGroup) {
        RxHelper.disposeActions(mSubscribedChannelsAction);

        Observable<MediaGroup> observable = mContentService.getSubscribedChannelsByNewContentObserve();

        mSubscribedChannelsAction = observable
                .subscribe(
                        onMediaGroup::onMediaGroup,
                        error -> Log.e(TAG, "loadSubscribedChannels error: %s", error.getMessage())
                );
    }

    private void loadChannelRows(Video item, OnMediaGroupList onMediaGroupList) {
        loadChannelRows(item, onMediaGroupList, null, null);
    }

    private void loadChannelRows(Video item, OnMediaGroupList onMediaGroupList, OnError onError, OnComplete onComplete) {
        if (item == null) {
            return;
        }

        RxHelper.disposeActions(mRowsAction);

        Observable<List<MediaGroup>> observable = item.mediaItem != null ?
                mContentService.getChannelObserve(item.mediaItem) : mContentService.getChannelObserve(item.channelId);

        // The rows are built without sidebar section (see ChannelPresenter, ChannelUploadsPresenter)
        mRowsAction = HiddenVideoResolver.resolveGroups(GlobalPreferences.context(), observable, null)
                .subscribe(
                        onMediaGroupList::onMediaGroupList,
                        error -> {
                            Log.e(TAG, "loadChannelRows error: %s", error.getMessage());
                            if (onError != null) {
                                onError.onError(error);
                            }
                        },
                        () -> {
                            if (onComplete != null) {
                                onComplete.onComplete();
                            }
                        }
                );
    }

    public void loadChannelPlaylist(Video item, OnMediaGroup callback) {
        loadChannelRows(
                item,
                mediaGroupList -> callback.onMediaGroup(mediaGroupList.get(0))
        );
    }

    /**
     * Once per run and account, when the first section begins to load: what the formats of the run's first video wait for is got ready
     * meanwhile (see ServiceManager#warmUpPlayback), not once its videos are shown. On a slow device (a Realtek box) a new player's
     * load ran longer than the section's, and a video opened meanwhile was given up 15 s in. The videos shown then check the languages
     * that subtitles translate to (see #warmUpPlaybackIfNeeded).
     */
    public void warmUpPlaybackOnLoad() {
        if (mIsPlaybackWarmUpStarted || mIsPlaybackWarmedUp) {
            return;
        }

        mIsPlaybackWarmUpStarted = true;
        mWarmUpTimeMs = SystemClock.elapsedRealtime();
        YouTubeServiceManager.instance().warmUpPlayback(null);
    }

    /**
     * Once per run and account, when the first videos are shown: the first one opened needn't wait for what the formats of the run's first
     * video wait for (see ServiceManager#warmUpPlayback). With the one started by the section's load still running, only the video's
     * subtitle languages are checked.
     */
    public void warmUpPlaybackIfNeeded(VideoGroup group) {
        if (mIsPlaybackWarmedUp || group == null || group.isEmpty()) {
            return;
        }

        // For the languages that subtitles translate to: a live stream may have none
        Video video = Helpers.findFirst(group.getVideos(), item -> item.videoId != null && !item.isLive && !item.isUpcoming);

        if (video == null) {
            return;
        }

        mIsPlaybackWarmedUp = true;
        mWarmUpTimeMs = SystemClock.elapsedRealtime();
        YouTubeServiceManager.instance().warmUpPlayback(video.videoId);
    }

    /**
     * Back after a long time (the poToken lives some hours, and is only made again by a fetch; the player's data is refreshed by a
     * fetch too): the next video needn't wait for them. Not sooner than WARM_UP_AGAIN_MS after the last time: it may fail again, and the
     * poToken's runs on the main thread mostly.
     */
    public void warmUpPlaybackIfExpired() {
        long nowMs = SystemClock.elapsedRealtime();
        ServiceManager service = YouTubeServiceManager.instance();

        if (!mIsPlaybackWarmedUp && !mIsPlaybackWarmUpStarted || nowMs - mWarmUpTimeMs < WARM_UP_AGAIN_MS
                || !service.isPoTokenToMake() && !service.isPlayerDataToRefresh()) {
            return;
        }

        mWarmUpTimeMs = nowMs;
        YouTubeServiceManager.instance().warmUpPlayback(null);
    }

    /**
     * The formats of a video to play. In Child mode they're fetched without the account, so a video only a signed in adult may watch
     * (18+) isn't played (see ServiceManager#allowRestrictedVideos).
     */
    public static Observable<MediaItemFormatInfo> getFormatInfoObserve(String videoId) {
        ServiceManager service = YouTubeServiceManager.instance();
        Context context = GlobalPreferences.context();

        service.allowRestrictedVideos(context == null || !GeneralData.instance(context).isChildModeEnabled());

        return service.getMediaItemService().getFormatInfoObserve(videoId);
    }

    public void loadFormatInfo(Video item, OnFormatInfo onFormatInfo) {
        if (item == null) {
            return;
        }

        RxHelper.disposeActions(mFormatInfoAction);

        Observable<MediaItemFormatInfo> observable = getFormatInfoObserve(item.videoId);

        mFormatInfoAction = observable
                .subscribe(
                        onFormatInfo::onFormatInfo,
                        error -> Log.e(TAG, "loadFormatInfo error: %s", error.getMessage())
                );
    }

    public void loadPlaylists(Video item, OnMediaGroup onPlaylistGroup) {
        if (item == null) {
            return;
        }

        RxHelper.disposeActions(mPlaylistGroupAction);

        Observable<MediaGroup> observable = mContentService.getPlaylistsObserve();

        mPlaylistGroupAction = observable
                .subscribe(
                        onPlaylistGroup::onMediaGroup,
                        error -> Log.e(TAG, "loadPlaylists error: %s", error.getMessage())
                );
    }

    public void getPlaylistInfos(OnPlaylistInfos onPlaylistInfos) {
        RxHelper.disposeActions(mPlaylistInfosAction);

        Observable<List<PlaylistInfo>> observable = mItemService.getPlaylistsInfoObserve(null);

        mPlaylistInfosAction = observable
                .subscribe(
                        onPlaylistInfos::onPlaylistInfos,
                        error -> Log.e(TAG, "getPlaylistInfos error: %s", error.getMessage())
                );
    }

    public void loadAccounts(OnAccountList onAccountList) {
        onAccountList.onAccountList(mSignInService.getAccounts());
    }

    public void authCheck(Runnable onSuccess, Runnable onError) {
        if (onSuccess == null && onError == null) {
            return;
        }

        if (mSignInService.isSigned()) {
            if (onSuccess != null) {
                onSuccess.run();
            }
        } else {
            if (onError != null) {
                onError.run();
            }
        }
    }

    public void disposeActions() {
        RxHelper.disposeActions(mMetadataAction, mUploadsAction, mRowsAction, mSubscribedChannelsAction);
    }

    /**
     * Most tiny ui has 8 cards in a row or 24 in grid.
     */
    public boolean shouldContinueGridGroup(Context context, VideoGroup group) {
        return shouldContinueTheGroup(context, group, true);
    }

    public boolean shouldContinueRowGroup(Context context, VideoGroup group) {
        return shouldContinueTheGroup(context, group,false);
    }

    /**
     * Most tiny ui has 8 cards in a row or 24 in grid.
     */
    public boolean shouldContinueTheGroup(Context context, VideoGroup group, boolean isGrid) {
        return mGroupFiller.shouldContinue(group, getMinGroupSize(context, isGrid));
    }

    /**
     * Emits the rows once the short ones have their next pages, so they are shown complete
     */
    public Observable<List<VideoGroup>> fillRowGroups(Context context, List<VideoGroup> groups, GroupFiller.Continuation continuation) {
        return mGroupFiller.fill(groups, getMinGroupSize(context, false), continuation);
    }

    private static int getMinGroupSize(Context context, boolean isGrid) {
        MainUIData mainUIData = MainUIData.instance(context);

        boolean isScaledUIEnabled = mainUIData.getUIScale() < 0.8f || mainUIData.getVideoGridScale() < 0.8f;

        if (isScaledUIEnabled) {
            return isGrid ? MIN_SCALED_GRID_GROUP_SIZE : MIN_SCALED_ROW_GROUP_SIZE;
        }

        return isGrid ? MIN_GRID_GROUP_SIZE : MIN_ROW_GROUP_SIZE;
    }

    /**
     * The only place the account's history is resumed or paused: when the user picks it, never on launch or an account switch
     */
    public void applyHistoryState(Context context, int historyState) {
        GeneralData.instance(context).setHistoryState(historyState);

        if (historyState == GeneralData.HISTORY_ENABLED || historyState == GeneralData.HISTORY_PAUSED) {
            RxHelper.runAsyncUser(() -> mContentService.enableHistory(historyState == GeneralData.HISTORY_ENABLED));
        }
    }

    public void clearHistory(Context context, Runnable onFinish) {
        RxHelper.runAsyncUser(mContentService::clearHistory, onFinish);
        VideoStateService.instance(context).clear(); // even for the logged users this needed too
    }

    public void clearSearchHistory() {
        RxHelper.runAsyncUser(mContentService::clearSearchHistory);
    }

    public void removeSearchTag(String tag) {
        RxHelper.runAsyncUser(() -> mContentService.removeSearchTag(tag));
    }

    public void updateHistory(Video video, long positionMs) {
        if (video == null || RxHelper.isAnyActionRunning(mHistoryAction)) {
            return;
        }

        RxHelper.disposeActions(mHistoryAction);

        Observable<Void> historyObservable;

        if (video.mediaItem != null) {
            historyObservable = mItemService.updateHistoryPositionObserve(video.mediaItem, positionMs / 1_000f);
        } else { // video launched form ATV channels
            historyObservable = mItemService.updateHistoryPositionObserve(video.videoId, positionMs / 1_000f);
        }

        mHistoryAction = RxHelper.execute(historyObservable, error -> setHistoryBroken(true), () -> setHistoryBroken(false));
    }

    public void hideNotification(Video item) {
        if (item != null && item.belongsToNotifications()) {
            RxHelper.execute(mNotificationsService.hideNotificationObserve(item.mediaItem));
        }
    }

    public void setNotificationState(NotificationState state, OnError onError) {
        RxHelper.execute(mNotificationsService.setNotificationStateObserve(state), onError::onError);
    }

    public void addToWatchLaterPlaylist(Video video) {
        addRemoveFromWatchLaterPlaylist(video, true);
    }

    /**
     * YouTube removes the watched videos from Watch later, the ones it picks: every one with any of it watched
     */
    public void removeWatchedFromWatchLater(Context context, Runnable onSuccess, OnError onError) {
        RxHelper.execute(mItemService.removeWatchedFromWatchLaterObserve(), error -> {
            // YouTube might have removed them all the same, e.g. from a long list after the request timed out
            WatchLaterManager.instance(context).reload();
            onError.onError(error);
        }, () -> {
            WatchLaterManager.instance(context).reload();
            onSuccess.run();
        });
    }

    public void addRemoveFromWatchLaterPlaylist(Video video, boolean isAdd) {
        if (video == null || !mSignInService.isSigned()) {
            return;
        }

        Disposable playlistsInfoAction = mItemService.getPlaylistsInfoObserve(video.videoId)
                .subscribe(
                        videoPlaylistInfos -> {
                            // The playlists are by title, so Watch later isn't the first one
                            PlaylistInfo watchLater = Helpers.findFirst(videoPlaylistInfos,
                                    info -> WatchLaterManager.WATCH_LATER_PLAYLIST_ID.equals(info.getPlaylistId()));

                            if (watchLater == null) {
                                Log.e(TAG, "Edit Watch later error: no Watch later");
                                return;
                            }

                            // BUG: YT not marked Watch later as selected
                            //if (watchLater.isSelected() == isAdd) {
                            //    return;
                            //}

                            Observable<Void> editObserve = isAdd ? mItemService.addToPlaylistObserve(watchLater.getPlaylistId(), video.videoId)
                                   : mItemService.removeFromPlaylistObserve(watchLater.getPlaylistId(), video.videoId);

                            RxHelper.execute(editObserve,
                                    error -> Log.e(TAG, "Edit Watch later error: %s", error.getMessage()),
                                    () -> WatchLaterManager.onPlaylistEdited(GlobalPreferences.context(), watchLater.getPlaylistId(), video, isAdd));
                        },
                        error -> {
                            // Fallback to something on error
                            Log.e(TAG, "Get playlists error: %s", error.getMessage());
                        }
                );
    }

    public void addAccountListener(AccountChangeListener listener) {
        if (!mAccountListeners.contains(listener)) {
            if (listener instanceof AccountsData ||
                listener instanceof AppPrefs) {
                mAccountListeners.add(0, listener); // data classes should be called before regular listeners
            } else {
                mAccountListeners.add(listener);
            }
        }
    }

    public void removeAccountListener(AccountChangeListener listener) {
        mAccountListeners.remove(listener);
    }

    public Account getSelectedAccount() {
        return mSignInService.getSelectedAccount();
    }

    public List<Account> getAccounts() {
        return mSignInService.getAccounts();
    }

    public String printAccountDebugInfo() {
        return mSignInService.printDebugInfo();
    }

    @Override
    public void onAccountChanged(Account account) {
        // Its poToken was reset with the account: made again when the account's videos are shown, not by the first one opened
        mIsPlaybackWarmedUp = false;
        mIsPlaybackWarmUpStarted = false;

        for (AccountChangeListener listener : mAccountListeners) {
            listener.onAccountChanged(account);
        }
    }

    /**
     * Selecting right presenter for the channel.<br/>
     * Channels could be of two types: regular (usr channel) and playlist channel (contains single row, try search: 'Mon mix')
     */
    public static void chooseChannelPresenter(Context context, Video item) {
        if (item.hasVideo() || item.hasReloadPageKey()) { // a channel item from Channels section
            ChannelPresenter.instance(context).openChannel(item);
            return;
        }

        // Child mode keeps to the subscribed channels
        ChildModeManager.instance(context).checkChannel(context, item.channelId, () -> loadChannelPresenter(context, item));
    }

    private static void loadChannelPresenter(Context context, Video item) {
        LoadingManager.showLoading(context, true);

        AtomicInteger atomicIndex = new AtomicInteger(-1);

        MediaServiceManager.instance().loadChannelRows(item, groups -> {
            LoadingManager.showLoading(context, false);

            if (groups == null || groups.isEmpty()) {
                return;
            }

            MediaGroup firstGroup = groups.get(0);
            int type = firstGroup.getType();

            if (type == MediaGroup.TYPE_CHANNEL_UPLOADS) {
                if (atomicIndex.incrementAndGet() == 0) {
                    ChannelUploadsPresenter.instance(context).clear();
                    ChannelUploadsPresenter.instance(context).setChannel(item);
                }
                // NOTE: Crashes RecycleView IndexOutOfBoundsException when doing add immediately after clear
                Utils.postDelayed(() -> ChannelUploadsPresenter.instance(context).update(firstGroup), 100);
            } else if (type == MediaGroup.TYPE_CHANNEL) {
                if (atomicIndex.incrementAndGet() == 0) {
                    ChannelPresenter.instance(context).clear();
                    ChannelPresenter.instance(context).setChannel(item);
                }
                // NOTE: Crashes RecycleView IndexOutOfBoundsException when doing add immediately after clear
                Utils.postDelayed(() -> ChannelPresenter.instance(context).updateRows(groups), 100);
            } else {
                MessageHelpers.showMessage(context, "Unknown type of channel");
            }
        }, error -> LoadingManager.showLoading(context, false), () -> LoadingManager.showLoading(context, false));
    }

    private void setHistoryBroken(boolean isBroken) {
        VideoStateService stateService = VideoStateService.instance(null);

        if (stateService != null) {
            stateService.setHistoryBroken(isBroken);
        }
    }
}