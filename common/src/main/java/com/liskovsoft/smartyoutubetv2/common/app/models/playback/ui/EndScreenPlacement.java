package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.mediaserviceinterfaces.data.EndScreenItem;

/**
 * Where a card of the end screen goes on the screen: its place on the video's frame (fractions of it, see EndScreenItem) over the
 * frame as the player shows it (e.g. smaller than the screen next to a dialog, or letterboxed)
 */
public final class EndScreenPlacement {
    private EndScreenPlacement() {
    }

    /**
     * @return left, top, right and bottom of the card, in the coordinates of the frame's
     */
    public static int[] place(EndScreenItem item, int frameLeft, int frameTop, int frameWidth, int frameHeight) {
        int left = frameLeft + Math.round(item.getLeft() * frameWidth);
        int top = frameTop + Math.round(item.getTop() * frameHeight);
        int width = Math.round(item.getWidth() * frameWidth);
        int height = item.getAspectRatio() > 0 ? Math.round(width / item.getAspectRatio()) : 0;

        return new int[] {left, top, left + width, top + height};
    }
}
