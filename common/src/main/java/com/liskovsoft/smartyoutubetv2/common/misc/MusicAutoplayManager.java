package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.MusicAutoplayData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

/**
 * Music autoplay: with it on, music videos have their own playback mode (see MusicAutoplayData) and the other videos keep
 * the one of PlayerData. The player button shows and changes the mode of the video playing.<br/>
 * With it off (the default) or without the user's own Data API key, every video has the playback mode of PlayerData.<br/>
 * A video is music once its category is known (see VideoCategoryManager.isMusic). Until then it has the mode of the other videos.
 */
public class MusicAutoplayManager {
    public static boolean isEnabled(Context context) {
        return MusicAutoplayData.instance(context).isEnabled() && VideoCategoryManager.isAvailable();
    }

    /**
     * The video has the playback mode of the music videos
     */
    public static boolean isMusicVideo(Context context, Video video) {
        return video != null && isEnabled(context) && VideoCategoryManager.instance(context).isMusic(video.videoId);
    }

    /**
     * @return one of PlayerConstants.PLAYBACK_MODE_*, the one of the other videos when the video is null
     */
    public static int getPlaybackMode(Context context, Video video) {
        return isMusicVideo(context, video) ? MusicAutoplayData.instance(context).getMusicPlaybackMode() :
                PlayerData.instance(context).getPlaybackMode();
    }

    /**
     * Sets the mode of the music videos when the video is one, the one of the other videos otherwise
     */
    public static void setPlaybackMode(Context context, Video video, int mode) {
        if (isMusicVideo(context, video)) {
            MusicAutoplayData.instance(context).setMusicPlaybackMode(mode);
        } else {
            PlayerData.instance(context).setPlaybackMode(mode);
        }
    }
}
