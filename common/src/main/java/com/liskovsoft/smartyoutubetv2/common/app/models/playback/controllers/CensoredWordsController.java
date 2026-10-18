package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import androidx.annotation.Nullable;

import com.liskovsoft.googlecommon.common.helpers.DefaultHeaders;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.sharedutils.querystringparser.UrlQueryString;
import com.liskovsoft.sharedutils.querystringparser.UrlQueryStringFactory;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.CensoredWords;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.CensoredWords.Range;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.CensoredWordsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import io.reactivex.disposables.Disposable;
import okhttp3.Response;

/**
 * Mutes the words YouTube's automatic captions censor, if enabled (see CensoredWordsData).<br/>
 * The times come from the automatic captions of the video's own language, fetched apart from the subtitles shown,
 * so it works with subtitles off. Videos without automatic captions (and live streams) play as they are.
 */
public class CensoredWordsController extends BasePlayerController implements OnDataChange {
    private static final String TAG = CensoredWordsController.class.getSimpleName();
    private static final String TYPE_ASR = "asr"; // automatic speech recognition
    // Translated tracks are marked automatic too
    private static final String PARAM_TRANSLATED = "tlang=";
    // The automatic captions of a dubbed audio track
    private static final String PARAM_VARIANT_DUB = "variant=timing-optimized";
    private CensoredWordsData mData;
    private String mVideoId;
    private String mCaptionUrl;
    // Of mVideoId, null until fetched
    private List<Range> mRanges;
    private Disposable mRangesAction;
    // The times are when the original audio says the words
    private boolean mIsDubbedAudio;

    @Override
    public void onInit() {
        mData = CensoredWordsData.instance(getContext());
        mData.setOnChange(this);
    }

    /**
     * Call before the player opens the video
     */
    public void onFormatInfo(Video video, MediaItemFormatInfo formatInfo) {
        if (!Helpers.equals(mVideoId, video.videoId)) {
            RxHelper.disposeActions(mRangesAction);
            mVideoId = video.videoId;
            mRanges = null;
        }

        mCaptionUrl = formatInfo.isLive() ? null : findCaptionUrl(formatInfo.getSubtitles());
        loadRanges();
    }

    @Override
    public void onVideoLoaded(Video item) {
        // The player drops them when it opens a video
        applyRanges();
    }

    @Override
    public void onTrackChanged(FormatItem track) {
        if (track != null && track.getType() == FormatItem.TYPE_AUDIO) {
            // E.g. "de (dubbed-auto)". Kept from here: asking the player while it opens a video sets up its track lists too early.
            mIsDubbedAudio = Helpers.contains(track.getLanguage(), "dubbed");
            applyRanges();
        }
    }

    @Override
    public void onDataChange() {
        loadRanges();
        applyRanges();
    }

    private void loadRanges() {
        if (mData == null || !mData.isMuteEnabled() || mCaptionUrl == null || mRanges != null || RxHelper.isAnyActionRunning(mRangesAction)) {
            return;
        }

        String videoId = mVideoId;
        String captionUrl = mCaptionUrl;

        mRangesAction = RxHelper.fromCallable(() -> fetchRanges(captionUrl))
                .subscribe(ranges -> {
                    if (!Helpers.equals(videoId, mVideoId)) {
                        return;
                    }

                    Log.d(TAG, "%s: %s censored words", videoId, ranges.size());
                    mRanges = ranges;
                    applyRanges();
                }, error -> Log.e(TAG, "%s: can't load the automatic captions: %s", videoId, error.getMessage()));
    }

    private void applyRanges() {
        PlaybackView player = getPlayer();
        Video video = getVideo();

        if (mData == null || player == null) {
            return;
        }

        // Whatever the video: the player may still play the previous one while the next one loads
        if (!mData.isMuteEnabled() || mIsDubbedAudio) {
            player.setMuteRanges(null);
            return;
        }

        if (video != null && Helpers.equals(video.videoId, mVideoId)) {
            player.setMuteRanges(mRanges);
        }
    }

    private static List<Range> fetchRanges(String url) throws Exception {
        try (Response response = OkHttpManager.instance().doGetRequest(url, Collections.singletonMap("User-Agent", DefaultHeaders.APP_USER_AGENT))) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }

            return CensoredWords.findRanges(response.body().string());
        }
    }

    @Nullable
    private static String findCaptionUrl(@Nullable List<MediaSubtitle> subtitles) {
        if (subtitles == null) {
            return null;
        }

        for (MediaSubtitle subtitle : subtitles) {
            String url = subtitle.getBaseUrl();

            if (TYPE_ASR.equals(subtitle.getType()) && url != null && !url.contains(PARAM_TRANSLATED) && !url.contains(PARAM_VARIANT_DUB)) {
                UrlQueryString query = UrlQueryStringFactory.parse(url);
                query.set("fmt", CensoredWords.CAPTION_FORMAT);
                return query.toString();
            }
        }

        return null;
    }
}
