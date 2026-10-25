package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Sound off is saved on its own: the audio format chosen in the settings stays
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PlayerDataSoundOffTest {
    private static final FormatItem OPUS_DRC = ExoFormatItem.from(FormatItem.TYPE_AUDIO, TrackSelectorManager.RENDERER_INDEX_AUDIO,
            "251", "opus", -1, -1, -1, null, false, 160_000, true, false);

    @Before
    public void setUp() {
        reset();
    }

    @After
    public void tearDown() {
        reset();
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void soundOff_keepsTheSavedFormat() {
        PlayerData data = getData();
        data.setFormat(OPUS_DRC);

        data.setSoundOff(true);

        assertTrue(data.getFormat(FormatItem.TYPE_AUDIO).isDefault());
        assertEquals(OPUS_DRC, data.getSavedAudioFormat());

        data.setSoundOff(false);

        assertEquals(OPUS_DRC, data.getFormat(FormatItem.TYPE_AUDIO));
    }

    @Test
    public void soundOff_isSaved() {
        PlayerData data = getData();
        data.setFormat(OPUS_DRC);
        data.setSoundOff(true);

        ShadowLooper.shadowMainLooper().idle(); // saved
        data.onProfileChanged(); // restored

        assertTrue(data.isSoundOff());
        assertEquals(OPUS_DRC, data.getSavedAudioFormat());
    }

    /**
     * An older version saved sound off as the audio format
     */
    @Test
    public void legacySoundOff_isMigrated() {
        PlayerData data = getData();
        String[] legacy = new String[11];
        legacy[10] = FormatItem.NO_AUDIO.toString();
        AppPrefs.instance(RuntimeEnvironment.getApplication()).setProfileData(PlayerData.VIDEO_PLAYER_DATA, Helpers.mergeData((Object[]) legacy));

        data.onProfileChanged();

        assertTrue(data.isSoundOff());
        assertTrue(data.getFormat(FormatItem.TYPE_AUDIO).isDefault());
        assertEquals(data.getDefaultAudioFormat(), data.getSavedAudioFormat());
    }

    @Test
    public void legacyFormat_isKept() {
        PlayerData data = getData();
        String[] legacy = new String[11];
        legacy[10] = OPUS_DRC.toString();
        AppPrefs.instance(RuntimeEnvironment.getApplication()).setProfileData(PlayerData.VIDEO_PLAYER_DATA, Helpers.mergeData((Object[]) legacy));

        data.onProfileChanged();

        assertFalse(data.isSoundOff());
        assertEquals(OPUS_DRC, data.getFormat(FormatItem.TYPE_AUDIO));
    }

    private static void reset() {
        PlayerData data = getData();
        data.setSoundOff(false);
        data.setFormat(data.getDefaultAudioFormat());
        data.setTempAudioFormat(null);
    }

    private static PlayerData getData() {
        return PlayerData.instance(RuntimeEnvironment.getApplication());
    }
}
