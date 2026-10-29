package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.VideoStart;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.VideoStarts;
import com.liskovsoft.smartyoutubetv2.common.prefs.DebuggingData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * Shows where the time of the start of the video playing went, when the user wants it (Settings → Debugging → Show video starts):
 * with the video's stats, while the bug button is on. A start that doesn't play is shown as it goes, refreshed, so a spinner that
 * spins on shows what it waits for.
 */
public class VideoStartController extends BasePlayerController implements VideoStarts.Listener {
    /**
     * A start going on is drawn again this often: its bars grow
     */
    private static final long GRAPH_REFRESH_MS = 1_000;
    /**
     * Of the video playing, once it played (or was given up): none while it starts
     */
    @Nullable
    private VideoStart mStart;
    private final Runnable mRefreshGraph = this::applyGraph;

    @Override
    public void onInit() {
        VideoStarts.init(getContext());
        VideoStarts.setListener(this);
    }

    @Override
    public void onStart() {
        mStart = null;
        applyGraph();
    }

    @Override
    public void onTimed(VideoStart start) {
        mStart = start;
        applyGraph();
    }

    /**
     * After the player's controller (see PlayerUIController#onDebugInfoClicked): the button is on or off already
     */
    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        if (buttonId == R.id.action_video_stats) {
            applyGraph();
        }
    }

    @Override
    public void onEngineInitialized() {
        applyGraph();
    }

    @Override
    public void onEngineReleased() {
        VideoStarts.onEngineReleased();
        hideGraph();
    }

    @Override
    public void onViewResumed() {
        applyGraph();
    }

    @Override
    public void onViewPaused() {
        PlaybackView player = getPlayer();

        // In PIP it plays on, but there's no room for the graph. Under a dialog of the player (e.g. its settings), it wasn't left.
        if (player == null || (!player.isInPIPMode() && !getAppDialogPresenter().isDialogShown())) {
            VideoStarts.abandon(VideoStart.OUTCOME_LEFT);
        }

        hideGraph();
    }

    @Override
    public void onFinish() {
        VideoStarts.abandon(VideoStart.OUTCOME_LEFT);
        hideGraph();
    }

    /**
     * Shown with the stats (the bug button), if the user wants it: the start as it goes, then as it ended
     */
    private void applyGraph() {
        Utils.removeCallbacks(mRefreshGraph);
        PlaybackView player = getPlayer();

        if (player == null) {
            return;
        }

        boolean isWanted = !player.isEmbed() && !player.isInPIPMode()
                && player.getButtonState(R.id.action_video_stats) == PlayerUI.BUTTON_ON
                && getContext() != null && DebuggingData.instance(getContext()).isStartGraphShown();
        VideoStart start = isWanted ? (mStart != null ? mStart : VideoStarts.snapshot()) : null;

        player.showStartGraph(start);

        if (start != null && mStart == null) {
            Utils.postDelayed(mRefreshGraph, GRAPH_REFRESH_MS); // it goes on
        }
    }

    private void hideGraph() {
        Utils.removeCallbacks(mRefreshGraph);

        if (getPlayer() != null) {
            getPlayer().showStartGraph(null);
        }
    }
}
