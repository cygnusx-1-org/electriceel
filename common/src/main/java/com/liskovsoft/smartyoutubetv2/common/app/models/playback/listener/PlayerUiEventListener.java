package com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

public interface PlayerUiEventListener {
    void onSuggestionItemClicked(Video item);
    void onSuggestionItemLongClicked(Video item);
    void onScrollEnd(Video item);
    /**
     * Moving toward the end of a row. Its next page is loaded before it's needed, without the loading bar.
     */
    void onScrollNearEnd(Video item);
    boolean onPreviousClicked();
    boolean onNextClicked();
    void onPlayClicked();
    void onPauseClicked();
    boolean onKeyDown(int keyCode);
    void onButtonClicked(int buttonId, int buttonState);
    void onButtonLongClicked(int buttonId, int buttonState);
    void onControlsShown(boolean shown);
}
