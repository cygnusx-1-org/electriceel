package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer.DebugInfoMediaCodecVideoRenderer;

/**
 * Steps a paused video forward one frame at a time.<br/>
 * The renderer shows the next frame the decoder already holds, so a step costs one frame of decoding instead of a seek
 * (which would decode everything from the previous keyframe).<br/>
 * The player's clock stays where playback paused. A while after stepping stops, or when playback resumes, it moves to the shown
 * frame with everything but the video (see {@link SimpleExoPlayer#setPositionKeepingVideo(long)}), so the video decoder doesn't
 * start over from the keyframe. Where that's not possible it's an exact seek, which lands on the frame already on screen.
 */
public class FrameStepper {
    private static final long WAKE_INTERVAL_MS = 10;
    // How long a step may go without a new frame before it's given up on (e.g. at the end of the video). Longer while buffering:
    // a sync that falls back to a seek decodes from the keyframe first, which takes seconds for 4K.
    private static final long STEP_TIMEOUT_MS = 1_000;
    private static final long STEP_TIMEOUT_BUFFERING_MS = 10_000;
    // A sync that falls back to a seek delays a step made meanwhile, so it waits for a real pause, not the gap between clicks
    private static final long SYNC_DELAY_MS = 3_000;
    // The player buffers ahead of its own position, which stepping leaves behind. Below the smallest buffer (5s), or
    // stepping on (holding the key) would run the buffer dry and stall.
    private static final long MAX_UNSYNCED_MS = 3_000;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Timeline.Period mPeriod = new Timeline.Period();
    private final Runnable mWaitForStep = this::waitForStep;
    private final Runnable mSync = this::sync;
    private final Listener mListener;
    private SimpleExoPlayer mPlayer;
    private DebugInfoMediaCodecVideoRenderer mRenderer;
    private int mLastRequest;
    private int mLastClearedRequest;
    private long mLastProgressTimeMs;
    private int mFrameStepCount;
    private long mFramePresentationTimeUs = C.TIME_UNSET;

    public interface Listener {
        /**
         * @param positionMs position of the stepped frame, or -1 once the player's own position is right again
         */
        void onFramePositionChanged(long positionMs);
    }

    public FrameStepper(Listener listener) {
        mListener = listener;
    }

    public void setPlayer(@Nullable SimpleExoPlayer player, @Nullable DebugInfoMediaCodecVideoRenderer renderer) {
        reset();
        mPlayer = player;
        mRenderer = renderer;
        mFrameStepCount = renderer != null ? renderer.getFrameStepCount() : 0;
    }

    /**
     * Pauses (if playing) and shows the next frame.
     */
    public void stepForward() {
        if (mPlayer == null || mRenderer == null ||
                (mPlayer.getPlaybackState() != Player.STATE_READY && mPlayer.getPlaybackState() != Player.STATE_BUFFERING)) {
            return;
        }

        mHandler.removeCallbacks(mSync);

        if (mPlayer.getPlayWhenReady()) {
            mPlayer.setPlayWhenReady(false);
        }

        mLastRequest++;
        mLastProgressTimeMs = SystemClock.uptimeMillis();
        mPlayer.createMessage(mRenderer).setType(DebugInfoMediaCodecVideoRenderer.MSG_FRAME_STEP).setPayload(mLastRequest).send();

        mHandler.removeCallbacks(mWaitForStep);
        mHandler.postDelayed(mWaitForStep, WAKE_INTERVAL_MS);
    }

    /**
     * Position of the frame on screen while stepping has moved it past the player's own position, otherwise -1.
     */
    public long getPositionMs() {
        long positionUs = getWindowPositionUs();

        return positionUs != C.TIME_UNSET ? positionUs / 1_000 : -1;
    }

    /**
     * Call when playback is requested. Moves the clock to the stepped frame,
     * or the audio would replay the stepped part while the video holds still.
     */
    public void onPlay() {
        mHandler.removeCallbacks(mWaitForStep);
        collectSteppedFrame();
        // Right away, so the playback thread gets it straight after the play request: until then, the audio plays from the old clock
        sync();
    }

    /**
     * Call on every seek (position discontinuity). A seek makes the stepped frame irrelevant. Syncs report none.
     */
    public void onSeek() {
        mHandler.removeCallbacks(mWaitForStep);
        clearSteppedFrame();
    }

    /**
     * Forgets any stepping, e.g. when another video opens.
     */
    public void reset() {
        mHandler.removeCallbacks(mWaitForStep);
        clearSteppedFrame();
    }

    private void waitForStep() {
        if (mPlayer == null || mRenderer == null) {
            return;
        }

        // Before collecting: once done, the frame collected is the last one
        boolean isStepDone = mRenderer.getLastFrameStepRequest() == mLastRequest && !mRenderer.hasPendingFrameSteps();

        if (collectSteppedFrame()) {
            mLastProgressTimeMs = SystemClock.uptimeMillis();
            mListener.onFramePositionChanged(getPositionMs());
        }

        if (!isStepDone) {
            long timeoutMs = mPlayer.getPlaybackState() == Player.STATE_BUFFERING ? STEP_TIMEOUT_BUFFERING_MS : STEP_TIMEOUT_MS;

            if (SystemClock.uptimeMillis() - mLastProgressTimeMs < timeoutMs) {
                // The next frame is still being decoded. Paused, the player would only look again in a second.
                // No sync meanwhile: one that falls back to a seek would drop the steps still to show.
                mPlayer.createMessage(mRenderer).setType(DebugInfoMediaCodecVideoRenderer.MSG_FRAME_STEP_WAKE).send();
                mHandler.postDelayed(mWaitForStep, WAKE_INTERVAL_MS);
                return;
            }
        }

        if (mFramePresentationTimeUs != C.TIME_UNSET) {
            if (getPositionMs() - mPlayer.getCurrentPosition() >= MAX_UNSYNCED_MS) {
                sync();
            } else {
                mHandler.removeCallbacks(mSync);
                mHandler.postDelayed(mSync, SYNC_DELAY_MS);
            }
        }
    }

    /**
     * @return true if the renderer has shown a new frame since the last call
     */
    private boolean collectSteppedFrame() {
        if (mRenderer == null || mRenderer.getFrameStepCount() == mFrameStepCount) {
            return false;
        }

        mFrameStepCount = mRenderer.getFrameStepCount();

        // Requested before a seek or another reset, so it shows a frame from before it
        if (mRenderer.getFrameStepRequest() <= mLastClearedRequest) {
            return false;
        }

        mFramePresentationTimeUs = mRenderer.getFrameStepPresentationTimeUs();

        return true;
    }

    private void sync() {
        mHandler.removeCallbacks(mSync);

        if (mFramePresentationTimeUs == C.TIME_UNSET || mPlayer == null) {
            return;
        }

        SeekParameters seekParameters = mPlayer.getSeekParameters();

        // Exact in case it falls back to a seek, even if seeks normally snap to keyframes (TextureView tweak)
        mPlayer.setSeekParameters(SeekParameters.EXACT);
        mPlayer.setPositionKeepingVideo(mFramePresentationTimeUs);
        mPlayer.setSeekParameters(seekParameters);

        // After the call, so the player already reports the new position
        clearSteppedFrame();
    }

    private void clearSteppedFrame() {
        mHandler.removeCallbacks(mSync);
        mLastClearedRequest = mLastRequest;

        if (mFramePresentationTimeUs == C.TIME_UNSET) {
            return;
        }

        mFramePresentationTimeUs = C.TIME_UNSET;
        mListener.onFramePositionChanged(-1);
    }

    private long getWindowPositionUs() {
        if (mFramePresentationTimeUs == C.TIME_UNSET || mPlayer == null) {
            return C.TIME_UNSET;
        }

        // The renderer works in period time, the player's position is window time
        Timeline timeline = mPlayer.getCurrentTimeline();
        long periodPositionInWindowUs = timeline.isEmpty() ? 0 :
                timeline.getPeriod(mPlayer.getCurrentPeriodIndex(), mPeriod).getPositionInWindowUs();

        return Math.max(0, mFramePresentationTimeUs + periodPositionInWindowUs);
    }
}
