package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.PlayerMessage;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.CensoredWords.Range;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutes the sound during the given ranges of the video (see {@link CensoredWords}).<br/>
 * The player delivers a message at each start and end when it plays that position, so the ranges are kept at any speed.
 * The messages stay after delivery and come again when a seek back passes them once more.
 * A seek may land inside a range, so the sound is set from the position after each.
 */
public class RangeMuter implements PlayerMessage.Target {
    private static final int MSG_MUTE = 1;
    private static final int MSG_UNMUTE = 2;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<PlayerMessage> mMessages = new ArrayList<>();
    private final Listener mListener;
    private SimpleExoPlayer mPlayer;
    private List<Range> mRanges;
    private boolean mIsMuted;

    public interface Listener {
        void onMuteChanged(boolean muted);
    }

    public RangeMuter(Listener listener) {
        mListener = listener;
    }

    public void setPlayer(@Nullable SimpleExoPlayer player) {
        reset();
        mPlayer = player;
    }

    /**
     * Call once the player has the video the ranges are for. Its messages are dropped when another one opens.
     */
    public void setRanges(@Nullable List<Range> ranges) {
        cancelMessages();
        mRanges = ranges;

        if (mPlayer != null && ranges != null) {
            for (Range range : ranges) {
                mMessages.add(send(MSG_MUTE, range.startMs));
                mMessages.add(send(MSG_UNMUTE, range.endMs));
            }
        }

        onSeek();
    }

    /**
     * Call on every position discontinuity
     */
    public void onSeek() {
        setMuted(mPlayer != null && isInRange(mPlayer.getCurrentPosition()));
    }

    /**
     * Forgets the ranges, e.g. when another video opens
     */
    public void reset() {
        setRanges(null);
    }

    @Override
    public void handleMessage(int messageType, @Nullable Object payload) {
        // Delivered on the main thread. One canceled before then isn't delivered.
        setMuted(messageType == MSG_MUTE);
    }

    private PlayerMessage send(int type, long positionMs) {
        return mPlayer.createMessage(this)
                .setType(type)
                .setPosition(positionMs)
                .setHandler(mHandler)
                .setDeleteAfterDelivery(false)
                .send();
    }

    private void cancelMessages() {
        for (PlayerMessage message : mMessages) {
            message.cancel();
        }

        mMessages.clear();
    }

    private boolean isInRange(long positionMs) {
        if (mRanges == null) {
            return false;
        }

        for (Range range : mRanges) {
            if (range.contains(positionMs)) {
                return true;
            }
        }

        return false;
    }

    private void setMuted(boolean muted) {
        if (mIsMuted == muted) {
            return;
        }

        mIsMuted = muted;
        mListener.onMuteChanged(muted);
    }
}
