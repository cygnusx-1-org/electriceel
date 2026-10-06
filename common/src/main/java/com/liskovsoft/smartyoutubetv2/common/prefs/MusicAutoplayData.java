package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataSaverBase;

/**
 * Music autoplay: music videos have their own playback mode, the other videos keep the one of PlayerData (see MusicAutoplayManager).<br/>
 * Only with the user's own Data API key (see VideoCategoryManager.isAvailable). The values are kept without one.<br/>
 * Each account has its own values while "Use separate settings per each account" is on.
 */
public class MusicAutoplayData extends DataSaverBase {
    // Music plays on, as the option says, until the player button of a music video picks another mode
    private static final int DEFAULT_MUSIC_PLAYBACK_MODE = PlayerConstants.PLAYBACK_MODE_ALL;
    // Storage layout. Don't change: the values are saved by index.
    private static final int ENABLED_INDEX = 0;
    private static final int MUSIC_PLAYBACK_MODE_INDEX = 1; // one of PlayerConstants.PLAYBACK_MODE_*
    private static MusicAutoplayData sInstance;

    private MusicAutoplayData(Context context) {
        // Saved right away: a save still pending when the account changes is dropped
        super(context, true, true);
    }

    public static MusicAutoplayData instance(Context context) {
        if (sInstance == null) {
            sInstance = new MusicAutoplayData(context);
        }

        return sInstance;
    }

    public boolean isEnabled() {
        return getBoolean(ENABLED_INDEX);
    }

    public void setEnabled(boolean enable) {
        setBoolean(ENABLED_INDEX, enable);
    }

    /**
     * One of PlayerConstants.PLAYBACK_MODE_*. Plays the videos continuously until one is picked.
     */
    public int getMusicPlaybackMode() {
        int mode = getInt(MUSIC_PLAYBACK_MODE_INDEX, DEFAULT_MUSIC_PLAYBACK_MODE);

        return mode >= PlayerConstants.PLAYBACK_MODE_PAUSE && mode <= PlayerConstants.PLAYBACK_MODE_REVERSE_LIST ? mode :
                DEFAULT_MUSIC_PLAYBACK_MODE;
    }

    public void setMusicPlaybackMode(int mode) {
        setInt(MUSIC_PLAYBACK_MODE_INDEX, mode);
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
