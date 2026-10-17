package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import android.os.Parcel;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.AudioTrack;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VoiceBoostFormatTest {
    @After
    public void tearDown() {
        TrackSelectorUtil.setVoiceBoostMark(null);
    }

    @Test
    public void isVb_readsTheFormatField() {
        assertTrue(TrackSelectorUtil.isVb(audio("251", 140_000, false, true)));
        assertFalse(TrackSelectorUtil.isVb(audio("251", 140_000, false, false)));
        assertFalse(TrackSelectorUtil.isVb(null));
    }

    @Test
    public void isVb_readsTheMpdIdSuffix() {
        assertTrue(TrackSelectorUtil.isVb(audio("251-vb", 140_000, false, false)));
    }

    @Test
    public void mpdIdOfDrcVoiceBoost_isBothDrcAndVoiceBoost() {
        Format format = audio("251-vb-drc", 140_000, false, false);

        assertTrue(TrackSelectorUtil.isDrc(format));
        assertTrue(TrackSelectorUtil.isVb(format));
    }

    @Test
    public void isVb_survivesFormatCopies() {
        Format format = audio("251", 140_000, false, true);

        assertTrue(format.copyWithBitrate(150_000).isVb);
        assertTrue(format.copyWithLabel("label").isVb);
        assertTrue(format.copyWithMetadata(null).isVb);
        assertTrue(format.copyWithMaxInputSize(1_000).isVb);
    }

    @Test
    public void isVb_survivesParcel() {
        Format format = audio("251", 140_000, false, true);
        Parcel parcel = Parcel.obtain();

        try {
            format.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            assertTrue(Format.CREATOR.createFromParcel(parcel).isVb);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void voiceBoostCopy_isNotEqualToTheOriginal() {
        Format original = audio("251", 140_000, false, false);
        Format voiceBoost = audio("251", 140_000, false, true);

        assertNotEquals(original, voiceBoost);
        assertNotEquals(original.hashCode(), voiceBoost.hashCode());
    }

    @Test
    public void trackName_marksOnlyVoiceBoost() {
        TrackSelectorUtil.setVoiceBoostMark("Voice boost");

        assertTrue(TrackSelectorUtil.buildTrackNameShort(audio("251", 140_000, false, true)).toString().endsWith(", Voice boost"));
        assertFalse(TrackSelectorUtil.buildTrackNameShort(audio("251", 140_000, false, false)).toString().contains("Voice boost"));
    }

    @Test
    public void trackName_skipsEmptyParts() {
        // No channels, sample rate or language: those parts are empty
        Format format = Format.createAudioSampleFormat("251", MimeTypes.AUDIO_OPUS, "opus", 140_000,
                Format.NO_VALUE, Format.NO_VALUE, Format.NO_VALUE, null, null, 0, null);

        assertEquals("0.14Mbps, opus", TrackSelectorUtil.buildTrackNameShort(format).toString());
    }

    @Test
    public void originalSpec_keepsTheOldFieldCount() {
        String spec = ExoFormatItem.from(track(audio("251", 140_000, false, false))).toString();

        // Older versions reject anything but 11 fields
        assertEquals(11, spec.split(",").length);
    }

    @Test
    public void voiceBoostSpec_roundTrips() {
        String spec = ExoFormatItem.from(track(audio("251", 140_000, false, true))).toString();

        assertEquals(12, spec.split(",").length);

        ExoFormatItem restored = ExoFormatItem.from(spec);

        assertNotNull(restored);
        assertTrue(restored.getTrack().format.isVb);
        assertEquals(140_000, restored.getTrack().format.bitrate);
    }

    @Test
    public void legacySpecs_areUpgraded() {
        String noBitrate = "1,1,251,opus,-1,-1,-1.0,en,false";
        String noDrc = noBitrate + ",140000";
        String noVb = noDrc + ",true";

        ExoFormatItem fromNoBitrate = ExoFormatItem.from(noBitrate);
        ExoFormatItem fromNoDrc = ExoFormatItem.from(noDrc);
        ExoFormatItem fromNoVb = ExoFormatItem.from(noVb);

        assertNotNull(fromNoBitrate);
        assertEquals(-1, fromNoBitrate.getTrack().format.bitrate);
        assertNotNull(fromNoDrc);
        assertFalse(fromNoDrc.getTrack().format.isDrc);
        assertNotNull(fromNoVb);
        assertTrue(fromNoVb.getTrack().format.isDrc);
        assertFalse(fromNoVb.getTrack().format.isVb);
    }

    @Test
    public void formatItems_differByVoiceBoost() {
        ExoFormatItem original = ExoFormatItem.from(track(audio("251", 140_000, false, false)));
        ExoFormatItem voiceBoost = ExoFormatItem.from(track(audio("251", 140_000, false, true)));

        assertNotEquals(original, voiceBoost);
    }

    private static AudioTrack track(Format format) {
        AudioTrack track = new AudioTrack(TrackSelectorManager.RENDERER_INDEX_AUDIO);
        track.format = format;
        return track;
    }

    private static Format audio(String id, int bitrate, boolean isDrc, boolean isVb) {
        return Format.createAudioSampleFormat(id, MimeTypes.AUDIO_OPUS, "opus", bitrate, Format.NO_VALUE, 2, 48_000,
                null, null, 0, "en", isDrc, isVb);
    }
}
