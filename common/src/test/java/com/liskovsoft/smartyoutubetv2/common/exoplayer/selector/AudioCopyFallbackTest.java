package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import android.util.Pair;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection.Definition;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * A DRC or voice boost copy chosen, a video without it: the original of the same itag, whatever its bitrate (#57)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AudioCopyFallbackTest {
    // The bitrate of an itag differs between the videos
    private static final Format OPUS_249 = audio("249", MimeTypes.AUDIO_OPUS, "opus", 50_000, false, false);
    private static final Format OPUS_250 = audio("250", MimeTypes.AUDIO_OPUS, "opus", 70_000, false, false);
    private static final Format OPUS_251 = audio("251", MimeTypes.AUDIO_OPUS, "opus", 160_000, false, false);
    // Above the bitrate of the settings format (160 kbps)
    private static final Format OPUS_251_HIGH = audio("251", MimeTypes.AUDIO_OPUS, "opus", 180_000, false, false);

    @Test
    public void drcOrigin_takesTheOriginalOfTheItag() {
        assertSame(OPUS_251, select(saved("251", 130_000, true, false), OPUS_249, OPUS_250, OPUS_251));
    }

    @Test
    public void drcOrigin_takesTheOriginalOfTheItagListedFirst() {
        assertSame(OPUS_251, select(saved("251", 130_000, true, false), OPUS_251, OPUS_250, OPUS_249));
    }

    @Test
    public void voiceBoostOrigin_takesTheOriginalOfTheItag() {
        assertSame(OPUS_251, select(saved("251", 130_000, false, true), OPUS_249, OPUS_250, OPUS_251));
    }

    @Test
    public void drcOrigin_keepsDrcWhenTheVideoHasIt() {
        Format drc = audio("251", MimeTypes.AUDIO_OPUS, "opus", 160_000, true, false);

        assertSame(drc, select(saved("251", 130_000, true, false), OPUS_251, drc, OPUS_250));
        assertSame(drc, select(saved("251", 130_000, true, false), drc, OPUS_251, OPUS_250));
    }

    @Test
    public void drcOrigin_skipsVoiceBoostOfTheItag() {
        Format voiceBoost = audio("251", MimeTypes.AUDIO_OPUS, "opus", 160_000, false, true);

        assertSame(OPUS_251, select(saved("251", 130_000, true, false), voiceBoost, OPUS_251, OPUS_250));
    }

    /**
     * The MPD builder marks the copies in the id, e.g. "251-drc"
     */
    @Test
    public void mpdDrcOrigin_takesTheOriginalOfTheItag() {
        assertSame(OPUS_251, select(saved("251-drc", 130_000, true, false), OPUS_249, OPUS_250, OPUS_251));
    }

    @Test
    public void mpdVoiceBoostOrigin_takesTheOriginalOfTheItag() {
        assertSame(OPUS_251, select(saved("251-vb", 130_000, false, true), OPUS_249, OPUS_250, OPUS_251));
    }

    @Test
    public void mpdDrcOrigin_keepsDrcWhenTheVideoHasIt() {
        Format drc = audio("251-drc", MimeTypes.AUDIO_OPUS, "opus", 160_000, false, false);

        assertSame(drc, select(saved("251-drc", 130_000, true, false), OPUS_251, drc, OPUS_250));
    }

    @Test
    public void settingsDrcFormat_takesTheOriginalOfTheItag() {
        assertSame(OPUS_251_HIGH, select(settingsFormat("251", true, false).getTrack(), OPUS_249, OPUS_250, OPUS_251_HIGH));
    }

    @Test
    public void settingsVoiceBoostFormat_takesTheOriginalOfTheItag() {
        assertSame(OPUS_251_HIGH, select(settingsFormat("251", false, true).getTrack(), OPUS_249, OPUS_250, OPUS_251_HIGH));
    }

    /**
     * A new player for every video
     */
    private static Format select(MediaTrack origin, Format... formats) {
        TrackSelectorManager manager = new TrackSelectorManager(RuntimeEnvironment.getApplication());
        manager.selectTrack(origin);
        TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));

        Pair<Definition, MediaTrack> selection = manager.onSelectAudioTrack(groups, DefaultTrackSelector.Parameters.DEFAULT);

        assertNotNull(selection);
        return selection.second.format;
    }

    private static FormatItem settingsFormat(String iTag, boolean isDrc, boolean isVb) {
        for (FormatItem format : AppDataSourceManager.instance().getAudioFormats()) {
            Format item = format.getTrack().format;
            if (iTag.equals(item.id) && TrackSelectorUtil.isDrc(item) == isDrc && TrackSelectorUtil.isVb(item) == isVb) {
                return format;
            }
        }

        throw new AssertionError("No such format in the settings: " + iTag);
    }

    /**
     * Fake format restored from the saved preferences
     */
    private static MediaTrack saved(String id, int bitrate, boolean isDrc, boolean isVb) {
        return ExoFormatItem.from(FormatItem.TYPE_AUDIO, TrackSelectorManager.RENDERER_INDEX_AUDIO, id, "opus",
                -1, -1, -1, "en", false, bitrate, isDrc, isVb).getTrack();
    }

    private static Format audio(String id, String mimeType, String codecs, int bitrate, boolean isDrc, boolean isVb) {
        return Format.createAudioSampleFormat(id, mimeType, codecs, bitrate, Format.NO_VALUE, 2, 48_000,
                null, null, 0, "en", isDrc, isVb);
    }
}
