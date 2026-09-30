package com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

public interface VideoGroupPresenter {
    void onVideoItemSelected(Video item);
    void onVideoItemClicked(Video item);
    void onVideoItemLongClicked(Video item);
    void onScrollEnd(Video item);
    /**
     * Moving toward the end of a row. Its next page is loaded before it's needed, without the loading bar where
     * overridden, like at the end otherwise.
     */
    default void onScrollNearEnd(Video item) {
        onScrollEnd(item);
    }
    boolean hasPendingActions();
}
