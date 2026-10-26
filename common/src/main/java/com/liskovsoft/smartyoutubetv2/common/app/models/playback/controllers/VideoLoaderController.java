package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.os.Build.VERSION;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SimpleMediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoActionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlayerFailure;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MusicAutoplayManager;
import com.liskovsoft.smartyoutubetv2.common.misc.VideoCategoryManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.youtubeapi.videoinfo.V2.ClientAttempts;

import java.util.Collections;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

public class VideoLoaderController extends BasePlayerController {
    private static final String TAG = VideoLoaderController.class.getSimpleName();
    private static final int MIN_SHUFFLE_SIZE = 30;
    private final Playlist mPlaylist;
    private Video mPendingVideo;
    private SuggestionsController mSuggestionsController;
    private ErrorFixerController mErrorFixerController;
    private CensoredWordsController mCensoredWordsController;
    private Disposable mFormatInfoAction;
    /**
     * Fetched while the engine starts (see prefetchFormatInfo), for the video in mPrefetchVideoId
     */
    private Observable<MediaItemFormatInfo> mPrefetchedFormatInfo;
    private String mPrefetchVideoId;
    private Disposable mMusicAutoplayAction;
    /**
     * Where the formats in use are from and how they're played (see PlayerFailure#source), of the video in mFormatVideoId
     */
    private String mFormatVideoId;
    private String mFormatClient;
    private String mFormatProtocol;
    private final Runnable mReloadVideo = () -> {
        getMainController().onNewVideo(getVideo());
    };
    private final Runnable mLoadNext = this::loadNext;
    private final Runnable mMetadataSync = () -> {
        if (getPlayer() != null) {
            waitMetadataSync(getVideo(), false);
        }
    };
    private final Runnable mRestartEngine = () -> {
        if (getPlayer() != null) {
            getPlayer().restartEngine(); // properly save position of the current track
        }
    };
    private final Runnable mOnApplyPlaybackMode = () -> {
        if (getPlayer() != null && getPlayer().getPositionMs() >= getPlayer().getDurationMs()) {
            applyPlaybackMode(getPlaybackMode());
        }
    };
    private final Runnable mShowProgressBar = () -> {
        if (getPlayer() != null) {
            getPlayer().showProgressBar(true);
        }
    };

    public VideoLoaderController() {
        mPlaylist = Playlist.instance();
    }

    @Override
    public void onInit() {
        mSuggestionsController = getController(SuggestionsController.class);
        mErrorFixerController = getController(ErrorFixerController.class);
        mCensoredWordsController = getController(CensoredWordsController.class);
    }

    @Override
    public void onNewVideo(Video item) {
        if (item == null) {
            return;
        }

        item.isShuffled = false;

        if (!item.fromQueue && !item.belongsToPlaybackQueue()) {
            mPlaylist.add(item);
        } else {
            item.fromQueue = false;
        }

        if (getPlayer() != null && getPlayer().isEngineInitialized()) { // player is initialized
            // Fix improperly resized video after exit from PIP (Device Formuler Z8 Pro)
            loadVideo(item); // force play immediately even the same video
        } else {
            mPendingVideo = item;
            prefetchFormatInfo(item);
        }
    }

    @Override
    public void onEngineInitialized() {
        if (getPlayer() == null) {
            return;
        }
        
        loadVideo(Helpers.firstNonNull(mPendingVideo, getVideo()));
        updateRepeatButton();
        mPendingVideo = null;
    }

    @Override
    public void onEngineReleased() {
        disposeActions();
    }

    @Override
    public void onVideoLoaded(Video video) {
        if (getPlayer() == null) {
            return;
        }
        
        updateRepeatButton();
        resolveMusicCategory(video);
        // Can't set title at this point
        //checkSleepTimer();
    }

    @Override
    public boolean onPreviousClicked() {
        loadPrevious();

        return true;
    }

    @Override
    public boolean onNextClicked() {
        if (getGeneralData().isChildModeEnabled()) {
            onPlayEnd();
        } else {
            loadNext();
        }

        return true;
    }

    public void loadPrevious() {
        if (getPlayer() == null) {
            return;
        }

        openVideoInt(mSuggestionsController.getPrevious());

        if (getPlayerTweaksData().isPlayerUiOnNextEnabled()) {
            getPlayer().showOverlay(true);
        }
    }

    public void loadNext() {
        if (getPlayer() == null || getVideo() == null) {
            return;
        }

        Video next = mSuggestionsController.getNext();

        if (next != null) {
            openVideoInt(next);
        } else {
            waitMetadataSync(getVideo(), true);
        }

        if (getPlayerTweaksData().isPlayerUiOnNextEnabled()) {
            getPlayer().showOverlay(true);
        }
    }

    @Override
    public void onPlayEnd() {
        if (getPlayer() == null) {
            return;
        }

        // Stop the playback if the user is browsing options or reading comments
        int playbackMode = getPlaybackMode();
        if (getAppDialogPresenter().isDialogShown() && !getAppDialogPresenter().isOverlay() && playbackMode != PlayerConstants.PLAYBACK_MODE_ONE) {
            getAppDialogPresenter().setOnFinish(mOnApplyPlaybackMode);
        } else {
            applyPlaybackMode(playbackMode);
        }
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        openVideoInt(item);

        if (getPlayer() != null)
            getPlayer().showControls(false);
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        Utils.removeCallbacks(mRestartEngine);

        return false;
    }

    /**
     * Force load and play!
     */
    private void loadVideo(Video item) {
        if (getPlayer() != null && item != null) {
            mPlaylist.setCurrent(item);
            getPlayer().setVideo(item);
            getPlayer().resetPlayerState();
            loadFormatInfo(item);
        }
    }

    /**
     * Force load suggestions.
     */
    private void loadSuggestions(Video item) {
        if (getPlayer() == null) {
            return;
        }

        if (item != null) {
            mPlaylist.setCurrent(item);
            getPlayer().setVideo(item);
            mSuggestionsController.loadSuggestions(item);
        }
    }

    private void waitMetadataSync(Video current, boolean showLoadingMsg) {
        if (current == null) {
            return;
        }

        if (current.nextMediaItem != null) {
            openVideoInt(Video.from(current.nextMediaItem));
        } else if (!current.isSynced) { // Maybe there's nothing left. E.g. when casting from phone
            // Wait in a loop while suggestions have been loaded...
            if (showLoadingMsg) {
                MessageHelpers.showMessage(getContext(), R.string.wait_data_loading);
            }
            // Short videos next fix (suggestions aren't loaded yet)
            boolean isEnded = getPlayer() != null && Math.abs(getPlayer().getDurationMs() - getPlayer().getPositionMs()) < 100;
            if (isEnded) {
                Utils.postDelayed(mMetadataSync, 1_000);
            }
        }
    }

    private void loadFormatInfo(Video video) {
        if (getPlayer() == null) {
            return;
        }

        // Fix no progress on next video (the engine may still buffering a bit)
        //getPlayer().showProgressBar(true);
        Utils.post(mShowProgressBar);
        disposeActions();

        mFormatInfoAction = takeFormatInfoObserve(video)
                .subscribe(this::processFormatInfo,
                           error -> {
                               if (getPlayer() != null) {
                                   getPlayer().showProgressBar(false);
                               }
                               mErrorFixerController.runFormatErrorAction(error);
                           });
    }

    /**
     * The formats are fetched while the engine starts, rather than after: the load of the video gets them (see takeFormatInfoObserve)
     */
    private void prefetchFormatInfo(Video video) {
        if (video.videoId == null) {
            return;
        }

        // Cached: its fetch isn't disposed with the load, which waits for it rather than fetching again
        mPrefetchedFormatInfo = getFormatInfoObserve(video.videoId).cache();
        mPrefetchVideoId = video.videoId;
        RxHelper.execute(mPrefetchedFormatInfo, error -> {}); // the load handles the error
    }

    /**
     * The formats fetched while the engine started, once. A reload of the video fetches them again.
     */
    private Observable<MediaItemFormatInfo> takeFormatInfoObserve(Video video) {
        Observable<MediaItemFormatInfo> prefetched = Helpers.equals(video.videoId, mPrefetchVideoId) ? mPrefetchedFormatInfo : null;

        mPrefetchedFormatInfo = null;
        mPrefetchVideoId = null;

        // Delivered after the load returns, like a fetch: a prefetch that's done (e.g. its formats were cached) would otherwise reach
        // the player inside onEngineInitialized, before the other controllers have had theirs
        return prefetched != null ? prefetched.observeOn(AndroidSchedulers.mainThread()) : getFormatInfoObserve(video.videoId);
    }

    private static Observable<MediaItemFormatInfo> getFormatInfoObserve(String videoId) {
        ServiceManager service = YouTubeServiceManager.instance();
        MediaItemService mediaItemManager = service.getMediaItemService();
        return mediaItemManager.getFormatInfoObserve(videoId);
    }

    private void processFormatInfo(MediaItemFormatInfo formatInfo) {
        PlaybackView player = getPlayer();

        if (player == null || getVideo() == null) {
            return;
        }

        String bgImageUrl = null;

        getVideo().sync(formatInfo);

        mFormatVideoId = getVideo().videoId;
        mFormatClient = getClientName(formatInfo);
        mFormatProtocol = null;

        mCensoredWordsController.onFormatInfo(getVideo(), formatInfo);

        // Fix stretched video for a couple milliseconds (before the onVideoSizeChanged gets called)
        applyAspectRatio(formatInfo);

        if (formatInfo.getPaidContentText() != null && getSponsorBlockData().isPaidContentNotificationEnabled()) {
            MessageHelpers.showMessage(getContext(), formatInfo.getPaidContentText());
        }

        if (formatInfo.isUnplayable()) {
            PlayerFailure failure = newFailure(PlayerFailure.KIND_UNPLAYABLE, formatInfo);

            if (isEmbedPlayer()) {
                failure.action("finish_embed").record();
                player.finish();
                return;
            }

            failure.action("load_next_video").record();

            player.setTitle(formatInfo.getPlayabilityReason());
            player.showProgressBar(false);
            mSuggestionsController.loadSuggestions(getVideo());
            bgImageUrl = getVideo().getBackgroundUrl();

            // 18+ video or the video is hidden/removed
            player.showOverlay(true);
            loadNextVideo(5_000);

            //if (formatInfo.isUnknownError()) { // the bot error or the video not available
            //    scheduleRebootAppTimer(5_000);
            //} else { // 18+ video or the video is hidden/removed
            //    scheduleNextVideoTimer(5_000);
            //}
        } else if (acceptAdaptiveFormats(formatInfo) && formatInfo.containsDashFormats()) {
            Log.d(TAG, "Loading regular video in dash format...");

            if (getPlayerTweaksData().isHighBitrateFormatsEnabled() && formatInfo.hasExtendedHlsFormats()) {
                mFormatProtocol = PlayerFailure.PROTOCOL_DASH + "+" + PlayerFailure.PROTOCOL_HLS;
                player.openMerged(formatInfo, formatInfo.getHlsManifestUrl());
            } else {
                mFormatProtocol = PlayerFailure.PROTOCOL_DASH;
                player.openDash(formatInfo);
            }
        } else if (acceptAdaptiveFormats(formatInfo) && formatInfo.containsSabrFormats() && !formatInfo.isLive()) { // TODO: SABR live not implemented yet
            Log.d(TAG, "Loading video in sabr format...");
            mFormatProtocol = PlayerFailure.PROTOCOL_SABR;
            player.openSabr(formatInfo);
        } else if (acceptDashLive(formatInfo)) {
            Log.d(TAG, "Loading live video (current or past live stream) in dash format...");
            mFormatProtocol = PlayerFailure.PROTOCOL_DASH_LIVE;
            player.openDashUrl(formatInfo.getDashManifestUrl());
        } else if (formatInfo.isLive() && formatInfo.containsHlsUrl()) {
            Log.d(TAG, "Loading live video (current or past live stream) in hls format...");
            mFormatProtocol = PlayerFailure.PROTOCOL_HLS;
            player.openHlsUrl(formatInfo.getHlsManifestUrl());
        } else if (formatInfo.containsUrlFormats()) {
            Log.d(TAG, "Loading url list video. This is always LQ...");
            mFormatProtocol = PlayerFailure.PROTOCOL_URL_LIST;
            player.openUrlList(formatInfo.createUrlList());
        } else {
            Log.d(TAG, "Empty format info received. Seems future live translation. No video data to pass to the player.");
            newFailure(PlayerFailure.KIND_EMPTY_FORMATS, formatInfo).action("reload_video").record();
            player.setTitle(formatInfo.getPlayabilityReason());
            player.showProgressBar(false);
            mSuggestionsController.loadSuggestions(getVideo());
            bgImageUrl = getVideo().getBackgroundUrl();
            player.showOverlay(true);
            reloadVideo(30 * 1_000);
        }

        player.showBackground(bgImageUrl); // remove bg (if video playing) or set another bg
    }

    /**
     * Of the video playing (or failing to): null when it has none yet
     */
    public String getFormatClient() {
        return isFormatOfVideo() ? mFormatClient : null;
    }

    /**
     * See PlayerFailure.PROTOCOL_ constants. Of the video playing (or failing to): null when it has none yet.
     */
    public String getFormatProtocol() {
        return isFormatOfVideo() ? mFormatProtocol : null;
    }

    private boolean isFormatOfVideo() {
        return getVideo() != null && mFormatVideoId != null && mFormatVideoId.equals(getVideo().videoId);
    }

    /**
     * The YouTube client's name, e.g. TV
     */
    private static String getClientName(MediaItemFormatInfo formatInfo) {
        MediaItemFormatInfo.ClientInfo client = formatInfo.getClientInfo();

        if (client == null) {
            return null;
        }

        return client instanceof Enum ? ((Enum<?>) client).name() : client.getClientName();
    }

    /**
     * Of a video that has no formats to play
     */
    private PlayerFailure newFailure(String kind, MediaItemFormatInfo formatInfo) {
        Video video = getVideo();

        return PlayerFailure.create(getContext(), kind)
                .video(video)
                .source(mFormatClient, null)
                .playability(formatInfo.getPlayabilityStatus(), formatInfo.getPlayabilityReason())
                .clientAttempts(video != null ? ClientAttempts.get(video.videoId) : null);
    }

    public void reloadVideo(int delayMs) {
        if (getPlayer() == null) {
            return;
        }

        if (getPlayer().isEngineInitialized()) {
            Log.d(TAG, "Reloading the video...");
            Utils.postDelayed(mReloadVideo, delayMs);
        }
    }

    private void loadNextVideo(int delayMs) {
        if (getPlayer() == null) {
            return;
        }

        if (getPlayer().isEngineInitialized()) {
            Log.d(TAG, "Starting the next video...");
            Utils.postDelayed(mLoadNext, delayMs);
        }
    }

    private void restartEngine(int delayMs) {
        if (getPlayer() != null) {
            Log.d(TAG, "Restarting the engine...");
            Utils.postDelayed(mRestartEngine, delayMs);
        }
    }

    private void openVideoInt(Video item) {
        if (item == null) {
            return;
        }

        disposeActions();

        if (item.hasVideo()) {
            // NOTE: Next clicked: instant playback even a mix
            // NOTE: Bypass PIP fullscreen on next caused by startView
            getMainController().onNewVideo(item);
            //getPlayer().showOverlay(true);
        } else {
            VideoActionPresenter.instance(getContext()).apply(item);
        }
    }

    private boolean isActionsRunning() {
        return RxHelper.isAnyActionRunning(mFormatInfoAction);
    }

    private void disposeActions() {
        MediaServiceManager.instance().disposeActions();
        RxHelper.disposeActions(mFormatInfoAction, mMusicAutoplayAction);
        Utils.removeCallbacks(mReloadVideo, mLoadNext, mRestartEngine, mMetadataSync);
    }

    public void restartEngine() {
        restartEngine(1_000);
    }

    public void reloadVideo() {
        reloadVideo(1_000);
    }

    private void applyPlaybackMode(int playbackMode) {
        if (getPlayer() == null) {
            return;
        }

        Video video = getVideo();
        // Fix simultaneous videos loading (e.g. when playback ends and user opens new video)
        if (video == null || isActionsRunning()) {
            return;
        }

        if (isEmbedPlayer()) {
            playbackMode = PlayerConstants.PLAYBACK_MODE_CLOSE;
        } else if (isNextVideoMode(video, playbackMode) && MusicAutoplayManager.isMusicVideo(getContext(), video)) {
            applyMusicPlaybackMode(video, playbackMode);
            return;
        }

        applyPlaybackModeInt(video, playbackMode);
    }

    private void applyPlaybackModeInt(Video video, int playbackMode) {
        switch (playbackMode) {
            case PlayerConstants.PLAYBACK_MODE_REVERSE_LIST:
                if (video.hasPlaylist() || video.belongsToChannelUploads() || video.belongsToChannel()) {
                    VideoGroup group = video.getGroup();
                    if (group != null && group.indexOf(video) != 0) { // stop after first
                        onPreviousClicked();
                    }
                    break;
                }
            case PlayerConstants.PLAYBACK_MODE_ALL:
            case PlayerConstants.PLAYBACK_MODE_SHUFFLE:
                loadNext();
                break;
            case PlayerConstants.PLAYBACK_MODE_ONE:
                if (VERSION.SDK_INT <= 19) {
                    // Fix frozen image on Android 4
                    restartEngine();
                } else {
                    getPlayer().setPositionMs(0);
                }
                break;
            case PlayerConstants.PLAYBACK_MODE_CLOSE:
                // Close player if suggestions not shown
                // Except when playing from queue
                if (mPlaylist.getNext() != null && !getPlayerTweaksData().isQueueRespectsPlaybackMode()) {
                    loadNext();
                } else {
                    AppDialogPresenter dialog = getAppDialogPresenter();
                    if (!getPlayer().isSuggestionsShown() && (!dialog.isDialogShown() || dialog.isOverlay())) {
                        dialog.closeDialog();
                        getPlayer().finishReally();
                    }
                }
                break;
            case PlayerConstants.PLAYBACK_MODE_PAUSE:
                // Stop player after each video.
                // Except when playing from queue
                if (mPlaylist.getNext() != null && !getPlayerTweaksData().isQueueRespectsPlaybackMode()) {
                    loadNext();
                } else {
                    stopPlayback();
                }
                break;
            case PlayerConstants.PLAYBACK_MODE_LIST:
                // if video has a playlist load next or restart playlist
                if (video.hasNextPlaylist() || mPlaylist.getNext() != null) {
                    loadNext();
                } else {
                    //restartPlaylistIfNeeded();
                    stopPlayback();
                }
                break;
            default:
                Log.e(TAG, "Undetected repeat mode " + playbackMode);
                break;
        }
    }

    /**
     * A music video goes on to the next one only when it's music too, the playback mode of the other videos decides otherwise
     * (e.g. a mix whose next video is a vlog stops with Pause after each video).<br/>
     * Waits for the category of the next video when it isn't known yet: it's usually looked up with the metadata (see resolveMusicCategory).
     * A video still unknown after that isn't music.
     */
    private void applyMusicPlaybackMode(Video video, int playbackMode) {
        Video next = mSuggestionsController.getNext();
        // Not ended when it's the Next button of the child mode (see onNextClicked)
        boolean isEnded = isEnded();

        RxHelper.disposeActions(mMusicAutoplayAction);
        mMusicAutoplayAction = RxHelper.execute(
                VideoCategoryManager.instance(getContext()).resolveVideos(Collections.singletonList(next != null ? next.videoId : null)),
                error -> onNextVideoResolved(video, isEnded, playbackMode),
                () -> onNextVideoResolved(video, isEnded, playbackMode));
    }

    private void onNextVideoResolved(Video video, boolean isEnded, int playbackMode) {
        // Another video was opened, or the user went on with this one meanwhile
        if (getPlayer() == null || video != getVideo() || (isEnded && !isEnded())) {
            return;
        }

        // The next video may have changed meanwhile (e.g. added to the queue)
        Video next = mSuggestionsController.getNext();

        if (next == null || !VideoCategoryManager.instance(getContext()).isMusic(next.videoId)) {
            Log.d(TAG, "Music autoplay: the next video isn't music, the playback mode of the other videos applies");
            playbackMode = getPlayerData().getPlaybackMode();
        }

        applyPlaybackModeInt(video, playbackMode);
    }

    /**
     * The mode goes on to the next video (the queue aside). The reverse order is the next video too outside a playlist or a channel.
     */
    private boolean isNextVideoMode(Video video, int playbackMode) {
        return playbackMode == PlayerConstants.PLAYBACK_MODE_ALL || playbackMode == PlayerConstants.PLAYBACK_MODE_SHUFFLE ||
                (playbackMode == PlayerConstants.PLAYBACK_MODE_LIST && video.hasNextPlaylist()) ||
                (playbackMode == PlayerConstants.PLAYBACK_MODE_REVERSE_LIST &&
                        !(video.hasPlaylist() || video.belongsToChannelUploads() || video.belongsToChannel()));
    }

    private boolean isEnded() {
        return getPlayer() != null && getPlayer().getPositionMs() >= getPlayer().getDurationMs();
    }

    /**
     * Music autoplay: the current video is looked up when it's loaded, the next one with the metadata. The player button shows the mode
     * of the music videos once the current one is known to be music, and the end of the video doesn't wait for the next one.
     */
    private void resolveMusicCategory(Video item) {
        Video current = getVideo();

        if (item == null || current == null || !MusicAutoplayManager.isEnabled(getContext())) {
            return;
        }

        // Not disposed with the video: the categories are kept for later
        RxHelper.execute(VideoCategoryManager.instance(getContext()).resolveVideos(Collections.singletonList(item.videoId)),
                () -> {
                    if (current != getVideo()) {
                        return;
                    }

                    updateRepeatButton();

                    // The metadata came before the video was known to be music, with another mode (see onMetadata)
                    if (Helpers.equals(item.videoId, current.videoId) && current.playlistInfo != null && !current.isShuffled &&
                            MusicAutoplayManager.getPlaybackMode(getContext(), current) == PlayerConstants.PLAYBACK_MODE_SHUFFLE) {
                        initRandomNext();
                    }
                });
    }

    /**
     * The playback mode of the video: a music video has its own with Music autoplay on (see MusicAutoplayManager)
     */
    private void updateRepeatButton() {
        Video video = getVideo();

        if (getPlayer() == null || video == null) {
            return;
        }

        getPlayer().setButtonState(R.id.action_repeat, video.finishOnEnded ? PlayerConstants.PLAYBACK_MODE_CLOSE :
                MusicAutoplayManager.getPlaybackMode(getContext(), video));
    }

    private void stopPlayback() {
        if (getPlayer() == null) {
            return;
        }

        getPlayer().setPositionMs(getPlayer().getDurationMs());
        getPlayer().setPlayWhenReady(false);
        getPlayer().showSuggestions(true);
    }

    private void restartPlaylistIfNeeded() {
        if (getPlayer() == null || getVideo() == null) {
            return;
        }
        
        VideoGroup group = getVideo().getGroup(); // Get the VideoGroup (playlist)

        if (group != null && !group.isEmpty() && getVideo().belongsToSamePlaylistGroup()) {
            openVideoInt(group.get(0));
        } else {
            Log.e(TAG, "VideoGroup is null or empty. Can't restart playlist.");
            stopPlayback();
        }
    }

    private boolean acceptAdaptiveFormats(MediaItemFormatInfo formatInfo) {
        if (getPlayerData().isLegacyCodecsForced() && formatInfo.containsUrlFormats()) {
            return false;
        }

        if (getPlayerTweaksData().isHlsStreamsForced() && formatInfo.isLive() && formatInfo.containsHlsUrl()) {
            return false;
        }

        // Not enough info for full length live streams
        if (formatInfo.isLive() && formatInfo.getStartTimeMs() == 0) {
            return false;
        }

        // Live dash url doesn't work with None buffer
        //if (formatInfo.isLive() && (getPlayerTweaksData().isDashUrlStreamsForced() || getPlayerData().getVideoBufferType() == PlayerData.BUFFER_NONE)) {
        if (formatInfo.isLive() && getPlayerTweaksData().isDashUrlStreamsForced() && formatInfo.containsDashUrl()) {
            return false;
        }

        if (formatInfo.isLive() && getPlayerTweaksData().isHlsStreamsForced() && formatInfo.containsHlsUrl()) {
            return false;
        }

        return true;
    }

    private boolean acceptDashLive(MediaItemFormatInfo formatInfo) {
        if (getPlayerTweaksData().isHlsStreamsForced() && formatInfo.isLive() && formatInfo.containsHlsUrl()) {
            return false;
        }

        return formatInfo.isLive() && formatInfo.containsDashUrl();
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        initRandomNext();
        resolveMusicCategory(mSuggestionsController.getNext());
    }

    private void initRandomNext() {
        MediaServiceManager.instance().disposeActions();

        PlaybackView player = getPlayer();
        PlayerData playerData = getPlayerData();
        Video current = getVideo();

        if (player == null || playerData == null || current == null || current.playlistInfo == null ||
                MusicAutoplayManager.getPlaybackMode(getContext(), current) != PlayerConstants.PLAYBACK_MODE_SHUFFLE) {
            return;
        }

        // NOTE: Shuffle only user created playlists (size != -1)
        if (current.playlistInfo.getSize() > MIN_SHUFFLE_SIZE) {
            Video video = new Video();
            video.playlistId = current.playlistId;
            video.playlistIndex = Utils.getRandomIndex(current.playlistInfo.getCurrentIndex(), current.playlistInfo.getSize());
            MediaServiceManager.instance().loadMetadata(video, randomMetadata -> {
                if (randomMetadata.getNextVideo() == null) {
                    return;
                }

                current.nextMediaItem = SimpleMediaItem.from(randomMetadata);
                current.isShuffled = true;
                player.setNextTitle(Video.from(current.nextMediaItem));
            });
        }
        //else {
        //    VideoGroup topRow = player.getSuggestionsByIndex(0); // the playlist row
        //
        //    if (topRow != null && topRow.isChapters()) {
        //        topRow = player.getSuggestionsByIndex(1);
        //    }
        //
        //    if (topRow != null) {
        //        int currentIdx = topRow.indexOf(current);
        //        int randomIndex = Utils.getRandomIndex(currentIdx, topRow.getSize());
        //
        //        if (randomIndex != -1) {
        //            Video nextVideo = topRow.get(randomIndex);
        //            current.nextMediaItem = SimpleMediaItem.from(nextVideo);
        //            current.isShuffled = true;
        //            player.setNextTitle(nextVideo);
        //        }
        //    }
        //}
    }

    private int getPlaybackMode() {
        Video video = getVideo();
        int playbackMode = MusicAutoplayManager.getPlaybackMode(getContext(), video);

        if (video != null && video.finishOnEnded) {
            playbackMode = PlayerConstants.PLAYBACK_MODE_CLOSE;
        } else if (video != null && video.belongsToShortsGroup() && getPlayerTweaksData().isLoopShortsEnabled()) {
            playbackMode = PlayerConstants.PLAYBACK_MODE_ONE;
        }
        return playbackMode;
    }

    /**
     * Fix stretched video for a couple milliseconds (before the onVideoSizeChanged gets called)
     */
    private void applyAspectRatio(MediaItemFormatInfo formatInfo) {
        if (getPlayer() == null) {
            return;
        }

        // Fix stretched video for a couple milliseconds (before the onVideoSizeChanged gets called)
        if (formatInfo.containsDashFormats()) {
            MediaFormat format = formatInfo.getAdaptiveFormats().get(0);
            int width = format.getWidth();
            int height = format.getHeight();
            boolean isShorts = width < height;
            if (width > 0 && height > 0 && (getPlayerData().getAspectRatio() == PlayerData.ASPECT_RATIO_DEFAULT || isShorts)) {
                getPlayer().setAspectRatio((float) width / height);
            } else {
                getPlayer().setAspectRatio(getPlayerData().getAspectRatio());
            }
        }
    }

    private void preloadNextVideoIfNeeded() {
        if (isEmbedPlayer() || getPlayer() == null || getVideo() == null || getVideo().isLive) {
            return;
        }

        if (getPlayer().getDurationMs() - getPlayer().getPositionMs() < 50_000) {
            MediaServiceManager.instance().loadFormatInfo(mSuggestionsController.getNext(), formatInfo -> {});
        }
    }
}
