package com.liskovsoft.smartyoutubetv2.common.exoplayer.controller;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

public interface PlayerView {
    void setQualityInfo(String info);
    void setVideo(Video video);
    /**
     * Position of the frame shown by frame stepping, ahead of the player's own position, or -1 when there's none
     */
    void setFramePositionMs(long positionMs);
}
