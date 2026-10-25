package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;

import com.google.android.exoplayer2.Format;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The Formats of the Audio card of the Player settings
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AudioFormatsCategoryTest {
    // Default, ac-3 and ec-3, then 5 itags with their DRC and voice boost copies
    private static final int FORMATS_COUNT = 1 + 2 + 5 * 3;

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
    public void untouched_isDefault() {
        OptionCategory category = createCategory();

        assertEquals(getContext().getString(R.string.audio_formats), category.title);
        assertEquals(FORMATS_COUNT, category.options.size());
        assertEquals(getContext().getString(R.string.audio_format_default), category.options.get(0).getTitle().toString());
        assertTrue(category.options.get(0).isSelected());
        assertEquals(1, countSelected(category));
    }

    @Test
    public void formatPicked_isSavedAndChecked() {
        findOption(createCategory(), "0.16Mbps, opus, DRC").onSelect(true);

        Format saved = getPlayerData().getSavedAudioFormat().getTrack().format;
        assertEquals("251", saved.id);
        assertTrue(TrackSelectorUtil.isDrc(saved));
        assertFalse(TrackSelectorUtil.isVb(saved));

        OptionCategory category = createCategory();
        assertTrue(findOption(category, "0.16Mbps, opus, DRC").isSelected());
        assertEquals(1, countSelected(category));
    }

    @Test
    public void defaultPicked_isTheUntouchedFormat() {
        findOption(createCategory(), "0.13Mbps, mp4a").onSelect(true);
        createCategory().options.get(0).onSelect(true);

        assertEquals(getPlayerData().getDefaultAudioFormat(), getPlayerData().getSavedAudioFormat());
        assertTrue(createCategory().options.get(0).isSelected());
    }

    @Test
    public void voiceBoost_isLabelled() {
        String voiceBoost = getContext().getString(R.string.audio_voice_boost);

        assertNotNull(findOption(createCategory(), "0.16Mbps, opus, " + voiceBoost));
        assertNotNull(findOption(createCategory(), "0.38Mbps, ac-3, 5.1"));
    }

    /**
     * Saved by an older version, picked in the player: other bitrate and language
     */
    @Test
    public void formatSavedByThePlayer_isChecked() {
        getPlayerData().setFormat(audio("251", "opus", 141_262, true));

        OptionCategory category = createCategory();
        assertTrue(findOption(category, "0.16Mbps, opus, DRC").isSelected());
        assertEquals(1, countSelected(category));
    }

    @Test
    public void formatNotInTheList_isShownChecked() {
        getPlayerData().setFormat(audio("600", "opus", 35_000, false));

        OptionCategory category = createCategory();
        assertEquals(FORMATS_COUNT + 1, category.options.size());
        assertTrue(category.options.get(1).isSelected());
        assertEquals(1, countSelected(category));
    }

    @Test
    public void soundOff_keepsTheFormatChecked() {
        findOption(createCategory(), "0.07Mbps, opus").onSelect(true);
        getPlayerData().setSoundOff(true);

        assertTrue(findOption(createCategory(), "0.07Mbps, opus").isSelected());
    }

    private static OptionCategory createCategory() {
        return AppDialogUtil.createAudioFormatsCategory(getContext());
    }

    private static OptionItem findOption(OptionCategory category, String title) {
        for (OptionItem option : category.options) {
            if (title.equals(option.getTitle().toString())) {
                return option;
            }
        }

        throw new AssertionError("No such option: " + title);
    }

    private static int countSelected(OptionCategory category) {
        int count = 0;

        for (OptionItem option : category.options) {
            if (option.isSelected()) {
                count++;
            }
        }

        return count;
    }

    private static FormatItem audio(String id, String codecs, int bitrate, boolean isDrc) {
        return ExoFormatItem.from(FormatItem.TYPE_AUDIO, TrackSelectorManager.RENDERER_INDEX_AUDIO, id, codecs,
                -1, -1, -1, "en", false, bitrate, isDrc, false);
    }

    private static void reset() {
        PlayerData data = getPlayerData();
        data.setSoundOff(false);
        data.setFormat(data.getDefaultAudioFormat());
    }

    private static PlayerData getPlayerData() {
        return PlayerData.instance(getContext());
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }
}
