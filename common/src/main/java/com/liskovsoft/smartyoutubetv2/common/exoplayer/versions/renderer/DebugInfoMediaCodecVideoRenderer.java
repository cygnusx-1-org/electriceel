package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build.VERSION;
import android.os.Handler;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.ExoUtils;

import java.nio.ByteBuffer;

public class DebugInfoMediaCodecVideoRenderer extends MediaCodecVideoRenderer {
    private static final String TAG = DebugInfoMediaCodecVideoRenderer.class.getSimpleName();
    /**
     * Shows the next decoded frame while paused. Payload: the request number, see {@link #getLastFrameStepRequest()}.
     */
    public static final int MSG_FRAME_STEP = C.MSG_CUSTOM_BASE + 1;
    /**
     * Does nothing, but wakes the paused playback loop, which otherwise renders once a second.
     */
    public static final int MSG_FRAME_STEP_WAKE = C.MSG_CUSTOM_BASE + 2;
    private int mFrameIndex;
    private boolean mIsSetOutputSurfaceWorkaroundEnabled;
    // Playback thread only
    private int mPendingFrameSteps;
    // Read by the main thread
    private volatile boolean mHasPendingFrameSteps;
    private volatile int mLastFrameStepRequest;
    private volatile long mFrameStepPresentationTimeUs = C.TIME_UNSET;
    private volatile int mFrameStepRequest;
    private volatile int mFrameStepCount;
    private volatile boolean mIsFrameStepSupported;

    // Exo 2.9
    //public DebugInfoMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
    //                                     @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys,
    //                                     @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener,
    //                                     int maxDroppedFramesToNotify) {
    //    super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener,
    //            maxDroppedFramesToNotify);
    //}

    // Exo 2.10, 2.11
    public DebugInfoMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
                                            @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, boolean enableDecoderFallback, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys, enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    // Exo 2.12, 2.13
    //public DebugInfoMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
    //                                     boolean enableDecoderFallback, @Nullable Handler eventHandler,
    //                                     @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
    //    super(context, mediaCodecSelector, allowedJoiningTimeMs, enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify);
    //}

    @Override
    protected CodecMaxValues getCodecMaxValues(
            MediaCodecInfo codecInfo, Format format, Format[] streamFormats) {
        ExoUtils.updateVideoDecoderInfo(codecInfo);

        return super.getCodecMaxValues(codecInfo, format, streamFormats);
    }

    // Measure real fps.
    // Note, that you can't accurate measure frame rate because actual frame rate is the average frame rate for the whole video track!
    // 29.97fps test: https://www.youtube.com/watch?v=LXb3EKWsInQ (Costa Rica)
    // More info: https://github.com/google/ExoPlayer/issues/4088
    //@Override
    //protected void renderOutputBuffer(MediaCodec codec, int index, long presentationTimeUs) {
    //    super.renderOutputBuffer(codec, index, presentationTimeUs);
    //}
    //
    //@Override
    //protected void renderOutputBufferV21(MediaCodec codec, int index, long presentationTimeUs, long releaseTimeNs) {
    //    super.renderOutputBufferV21(codec, index, presentationTimeUs, releaseTimeNs);
    //
    //    mFrameIndex++;
    //
    //    Log.d(TAG, "Real fps: %s", 1_000_000f / (presentationTimeUs / mFrameIndex));
    //}

    @Override
    protected boolean codecNeedsSetOutputSurfaceWorkaround(String name) {
        // Null surface error on Android 9 (VERSION.SDK_INT >= 28) and above (appears on background audio playback)
        // Need to be enabled on older version of ExoPlayer (e.g. 2.10.6).
        // It's because there's no tweaks for modern devices.
        return mIsSetOutputSurfaceWorkaroundEnabled || super.codecNeedsSetOutputSurfaceWorkaround(name);
    }

    /**
     * Null surface error on Android 9 (VERSION.SDK_INT >= 28) and above (appears on background audio playback)<br/>
     * Need to be enabled on older version of ExoPlayer (e.g. 2.10.6).<br/>
     * It's because there's no tweaks for modern devices.
     */
    public void enableSetOutputSurfaceWorkaround(boolean enable) {
        mIsSetOutputSurfaceWorkaroundEnabled = enable;
    }

    @Override
    public void handleMessage(int messageType, @Nullable Object message) throws ExoPlaybackException {
        if (messageType == MSG_FRAME_STEP) {
            if (mIsFrameStepSupported) {
                setPendingFrameSteps(mPendingFrameSteps + 1);
            }
            mLastFrameStepRequest = (Integer) message;
        } else if (messageType != MSG_FRAME_STEP_WAKE) {
            super.handleMessage(messageType, message);
        }
    }

    @Override
    protected void onEnabled(boolean joining) throws ExoPlaybackException {
        super.onEnabled(joining);

        // Tunneled frames go straight from the decoder to the display, so there's no decoded frame to hold back and release
        mIsFrameStepSupported = getConfiguration().tunnelingAudioSessionId == C.AUDIO_SESSION_ID_UNSET;
    }

    @Override
    protected void onStarted() {
        super.onStarted();
        setPendingFrameSteps(0); // a step is only for the frame on screen right now
    }

    @Override
    protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
        super.onPositionReset(positionUs, joining);
        setPendingFrameSteps(0);
    }

    @Override
    protected void onDisabled() {
        super.onDisabled();
        setPendingFrameSteps(0);
    }

    @Override
    protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodec codec, ByteBuffer buffer, int bufferIndex,
                                          int bufferFlags, long bufferPresentationTimeUs, boolean isDecodeOnlyBuffer, boolean isLastBuffer,
                                          Format format) throws ExoPlaybackException {
        if (super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex, bufferFlags, bufferPresentationTimeUs,
                isDecodeOnlyBuffer, isLastBuffer, format)) {
            return true;
        }

        if (mPendingFrameSteps == 0 || isDecodeOnlyBuffer || getState() == STATE_STARTED) {
            return false;
        }

        // Paused, so this decoded frame would wait for playback to reach it. Show it now: a step that needs no seek.
        long presentationTimeUs = bufferPresentationTimeUs - getOutputStreamOffsetUs();
        renderOutputBufferV21(codec, bufferIndex, presentationTimeUs, System.nanoTime());
        mFrameStepPresentationTimeUs = presentationTimeUs;
        mFrameStepRequest = mLastFrameStepRequest;
        mFrameStepCount++;
        setPendingFrameSteps(mPendingFrameSteps - 1);

        return true;
    }

    public boolean hasPendingFrameSteps() {
        return mHasPendingFrameSteps;
    }

    public int getLastFrameStepRequest() {
        return mLastFrameStepRequest;
    }

    /**
     * Presentation time (period time) of the frame the last step showed.
     */
    public long getFrameStepPresentationTimeUs() {
        return mFrameStepPresentationTimeUs;
    }

    /**
     * The last request received before the frame the last step showed.
     */
    public int getFrameStepRequest() {
        return mFrameStepRequest;
    }

    /**
     * Number of frames shown by steps so far. Bumped after the other frame step values are updated.
     */
    public int getFrameStepCount() {
        return mFrameStepCount;
    }

    private void setPendingFrameSteps(int count) {
        mPendingFrameSteps = count;
        mHasPendingFrameSteps = count > 0;
    }
}
