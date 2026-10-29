package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.LoadHolds;

/**
 * The player's load control, whose decisions not to load are told to the timing of video starts (see LoadHolds)
 */
final class TimedLoadControl implements LoadControl {
    private final LoadControl mLoadControl;

    TimedLoadControl(LoadControl loadControl) {
        mLoadControl = loadControl;
    }

    @Override
    public void onPrepared() {
        mLoadControl.onPrepared();
    }

    @Override
    public void onTracksSelected(Renderer[] renderers, TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        mLoadControl.onTracksSelected(renderers, trackGroups, trackSelections);
    }

    @Override
    public void onStopped() {
        mLoadControl.onStopped();
    }

    @Override
    public void onReleased() {
        mLoadControl.onReleased();
    }

    @Override
    public Allocator getAllocator() {
        return mLoadControl.getAllocator();
    }

    @Override
    public long getBackBufferDurationUs() {
        return mLoadControl.getBackBufferDurationUs();
    }

    @Override
    public boolean retainBackBufferFromKeyframe() {
        return mLoadControl.retainBackBufferFromKeyframe();
    }

    @Override
    public boolean shouldContinueLoading(long bufferedDurationUs, float playbackSpeed) {
        boolean isLoading = mLoadControl.shouldContinueLoading(bufferedDurationUs, playbackSpeed);
        LoadHolds.onDecision(isLoading, bufferedDurationUs, mLoadControl.getAllocator().getTotalBytesAllocated());

        return isLoading;
    }

    @Override
    public boolean shouldStartPlayback(long bufferedDurationUs, float playbackSpeed, boolean rebuffering) {
        return mLoadControl.shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering);
    }
}
