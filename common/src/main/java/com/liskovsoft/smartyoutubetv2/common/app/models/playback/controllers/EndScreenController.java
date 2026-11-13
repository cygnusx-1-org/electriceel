package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.data.EndScreenItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.QrCodePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.EndScreenData;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.disposables.Disposable;

/**
 * Shows the end screen the creator put over the last seconds of a video (YouTube's end screen), if enabled (see EndScreenData).<br/>
 * The cards come with the formats when their client sends them. Otherwise they're asked of the web client a minute before the end
 * (see MediaItemService#getEndScreenObserve), so a video left before then costs no request.<br/>
 * Each card shows from its start to its end. One that lasts to the end of the video stays after the video ended (e.g. the playback
 * mode pauses there).<br/>
 * None in Child mode: like the suggestions, the cards lead to other videos and channels. None on live streams and shorts.
 */
public class EndScreenController extends BasePlayerController implements OnDataChange {
    private static final String TAG = EndScreenController.class.getSimpleName();
    private static final long POLL_INTERVAL_MS = 500;
    /**
     * The cards start up to 20 s before the end: asked this long before it, they're there in time
     */
    static final long FETCH_BEFORE_END_MS = 60_000;
    /**
     * A card set to last to the end of the video ends about when the media does: one that ends this close to it stays after the
     * video ended
     */
    static final long END_MARGIN_MS = 1_000;
    private EndScreenData mData;
    private String mVideoId;
    private boolean mIsSupported;
    // Of mVideoId, null while not known
    @Nullable
    private List<EndScreenItem> mItems;
    private boolean mIsFetchStarted;
    private List<EndScreenItem> mShown = Collections.emptyList();
    private Disposable mPollAction;
    private Disposable mFetchAction;

    @Override
    public void onInit() {
        mData = EndScreenData.instance(getContext());
        mData.setOnChange(this);
    }

    @Override
    public void onNewVideo(Video item) {
        // Till the new one is loaded: the player still tells the position of the video before
        stopPolling();
        // The cards of the video before go at once, e.g. after a click on one of them
        show(Collections.emptyList());
    }

    /**
     * Call before the player opens the video
     */
    public void onFormatInfo(Video video, MediaItemFormatInfo formatInfo) {
        if (!Helpers.equals(mVideoId, video.videoId)) {
            reset(video.videoId);
        }

        mIsSupported = !formatInfo.isLive() && !video.isLive && !video.isShorts && !video.belongsToShortsGroup();

        List<EndScreenItem> items = formatInfo.getEndScreen();

        // A reload from a client that doesn't send them keeps the ones asked for
        if (items != null) {
            mItems = filterCards(items);
        }
    }

    @Override
    public void onVideoLoaded(Video item) {
        startPolling();
    }

    @Override
    public void onEngineReleased() {
        stopPolling();
        show(Collections.emptyList());
    }

    @Override
    public void onDataChange() {
        startPolling();
    }

    @Override
    public void onEndScreenItemClicked(EndScreenItem item) {
        if (item == null) {
            return;
        }

        if (item.getType() == EndScreenItem.TYPE_LINK) {
            // A TV can't open a site: the QR code opens it on a phone
            QrCodePresenter.instance(getContext()).show(item.getUrl());
            return;
        }

        // As a suggestion: a video plays (in its playlist), a channel or a playlist page opens
        Video video = toVideo(item);

        if (video != null) {
            getMainController().onSuggestionItemClicked(video);
        }
    }

    private boolean isEnabled() {
        return mIsSupported && mData != null && mData.isShowEnabled() && !getGeneralData().isChildModeEnabled() && !isEmbedPlayer();
    }

    private void reset(String videoId) {
        RxHelper.disposeActions(mFetchAction);
        mVideoId = videoId;
        mItems = null;
        mIsFetchStarted = false;
        mIsSupported = false;
    }

    /**
     * Once the video is loaded (see onVideoLoaded): before, the player tells the position of the video before
     */
    private void startPolling() {
        stopPolling();

        // Not outside the player (e.g. the setting changed with the account): the next video starts it
        if (getPlayer() == null || !getPlayer().isEngineInitialized() || !isEnabled() || mVideoId == null
                || (mItems != null && mItems.isEmpty())) {
            show(Collections.emptyList());
            return;
        }

        mPollAction = RxHelper.interval(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
                .subscribe(tick -> update(), error -> Log.e(TAG, "Poll error: %s", error.getMessage()));
        update();
    }

    private void stopPolling() {
        RxHelper.disposeActions(mPollAction);
    }

    private void update() {
        PlaybackView player = getPlayer();
        Video video = getVideo();

        // The player may still play the video before while the next one loads
        if (player == null || video == null || !Helpers.equals(video.videoId, mVideoId)) {
            return;
        }

        long durationMs = player.getDurationMs();

        if (durationMs <= 0) {
            return;
        }

        long positionMs = player.getPositionMs();

        if (mItems == null) {
            if (positionMs >= durationMs - FETCH_BEFORE_END_MS) {
                fetch();
            }
            return;
        }

        show(findShown(mItems, positionMs, durationMs));
    }

    private void fetch() {
        if (mIsFetchStarted) {
            return;
        }

        mIsFetchStarted = true;
        String videoId = mVideoId;

        mFetchAction = getMediaItemService().getEndScreenObserve(videoId)
                .subscribe(items -> {
                    if (!Helpers.equals(videoId, mVideoId)) {
                        return;
                    }

                    mItems = filterCards(items);
                    Log.d(TAG, "%s: %s end screen cards", videoId, mItems.size());

                    if (mItems.isEmpty()) {
                        stopPolling();
                    } else {
                        update();
                    }
                }, error -> {
                    Log.e(TAG, "%s: can't load the end screen: %s", videoId, error.getMessage());

                    if (Helpers.equals(videoId, mVideoId)) {
                        mItems = Collections.emptyList();
                        stopPolling();
                    }
                });
    }

    private void show(List<EndScreenItem> items) {
        if (items.equals(mShown)) {
            return;
        }

        mShown = items;

        if (getPlayer() != null) {
            getPlayer().setEndScreen(items.isEmpty() ? null : items);
        }
    }

    /**
     * The cards on the screen at the position: started and not over. One that lasts to the end of the video isn't over after it.
     */
    static List<EndScreenItem> findShown(List<EndScreenItem> items, long positionMs, long durationMs) {
        List<EndScreenItem> result = new ArrayList<>();

        for (EndScreenItem item : items) {
            boolean isStarted = positionMs >= item.getStartMs();
            boolean isToTheEnd = item.getEndMs() >= durationMs - END_MARGIN_MS;
            boolean isOver = positionMs >= item.getEndMs() && !isToTheEnd;

            if (isStarted && !isOver) {
                result.add(item);
            }
        }

        return result;
    }

    /**
     * The cards that can be shown and opened: a known kind, with where they go, a place on the frame and a time
     */
    static List<EndScreenItem> filterCards(@Nullable List<EndScreenItem> items) {
        List<EndScreenItem> result = new ArrayList<>();

        if (items == null) {
            return result;
        }

        for (EndScreenItem item : items) {
            if (item != null && hasTarget(item) && item.getWidth() > 0 && item.getAspectRatio() > 0
                    && item.getStartMs() >= 0 && item.getEndMs() > item.getStartMs()) {
                result.add(item);
            }
        }

        return result;
    }

    private static boolean hasTarget(EndScreenItem item) {
        switch (item.getType()) {
            case EndScreenItem.TYPE_VIDEO:
                return item.getVideoId() != null;
            case EndScreenItem.TYPE_PLAYLIST:
                return item.getVideoId() != null || item.getPlaylistId() != null;
            case EndScreenItem.TYPE_CHANNEL:
                return item.getChannelId() != null;
            case EndScreenItem.TYPE_LINK:
                return item.getUrl() != null;
            default:
                return false;
        }
    }

    /**
     * The card as a suggestion would be: a video (in its playlist), a playlist page or a channel
     */
    @Nullable
    static Video toVideo(EndScreenItem item) {
        Video video = new Video();
        video.title = item.getTitle();
        video.cardImageUrl = item.getImageUrl();

        switch (item.getType()) {
            case EndScreenItem.TYPE_VIDEO:
            case EndScreenItem.TYPE_PLAYLIST:
                video.videoId = item.getVideoId();
                video.playlistId = item.getPlaylistId();
                break;
            case EndScreenItem.TYPE_CHANNEL:
                video.channelId = item.getChannelId();
                break;
            default:
                return null;
        }

        return video;
    }
}
