package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import android.net.Uri;
import android.util.Pair;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection.Definition;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.errors.TrackErrorFixer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * SABR copies of an itag share the id: original, DRC and voice boost differ only by their flags.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VoiceBoostSelectionTest {
    private static final Format OPUS = opus(false, false);
    private static final Format OPUS_DRC = opus(true, false);
    private static final Format OPUS_VB = opus(false, true);

    private TrackSelectorManager mManager;

    @Before
    public void setUp() {
        mManager = new TrackSelectorManager(RuntimeEnvironment.getApplication());
    }

    @Test
    public void originalOrigin_skipsVoiceBoostListedFirst() {
        assertSame(OPUS, select(saved("251", "opus", 140_000, false, false), OPUS_VB, OPUS));
    }

    @Test
    public void originalOrigin_skipsVoiceBoostListedLast() {
        assertSame(OPUS, select(saved("251", "opus", 140_000, false, false), OPUS, OPUS_VB));
    }

    @Test
    public void voiceBoostOrigin_keepsVoiceBoost() {
        // Same id and bitrate as the original: only the flag tells them apart
        assertSame(OPUS_VB, select(saved("251", "opus", 140_000, false, true), OPUS, OPUS_VB));
    }

    @Test
    public void originalOrigin_takesAnotherCodecOverVoiceBoost() {
        // The only aac in bounds is voice boost, the opus original is above the origin's bitrate
        Format aacVoiceBoost = audio("140", MimeTypes.AUDIO_AAC, "mp4a.40.2", 130_000, false, true);
        Format opus = audio("251", MimeTypes.AUDIO_OPUS, "opus", 140_000, false, false);

        assertSame(opus, select(saved("140", "mp4a.40.2", 130_000, false, false), aacVoiceBoost, opus));
    }

    @Test
    public void originalOrigin_skipsDrcCopyWithTheSameBitrate() {
        assertSame(OPUS, select(saved("251", "opus", 140_000, false, false), OPUS_DRC, OPUS));
    }

    @Test
    public void fallback_prefersTheOriginalOverVoiceBoost() {
        // Everything is above the origin's bitrate, so the "muted audio" fallback is used
        Format voiceBoost = audio("251", MimeTypes.AUDIO_OPUS, "opus", 160_000, false, true);
        Format original = audio("251", MimeTypes.AUDIO_OPUS, "opus", 150_000, false, false);

        assertSame(original, select(saved("249", "opus", 50_000, false, false), voiceBoost, original));
    }

    @Test
    public void fallback_usesVoiceBoostWhenNothingElseIsPlayable() {
        Format voiceBoost = audio("251", MimeTypes.AUDIO_OPUS, "opus", 160_000, false, true);

        assertSame(voiceBoost, select(saved("249", "opus", 50_000, false, false), voiceBoost));
    }

    @Test
    public void mergedSource_keepsBothCopies() {
        mManager.setMergedSource(true);
        Format voiceBoost = audio("140", MimeTypes.AUDIO_AAC, "mp4a.40.2", 130_000, false, true);
        Format original = audio("140", MimeTypes.AUDIO_AAC, "mp4a.40.2", 130_000, false, false);

        assertSame(original, select(saved("140", "mp4a.40.2", 130_000, false, false), voiceBoost, original));

        Set<MediaTrack> tracks = mManager.getAudioTracks();
        assertEquals(3, tracks.size()); // auto + both copies
    }

    @Test
    public void codecErrorFallback_skipsVoiceBoost() {
        mManager.setTrackSelector(new DefaultTrackSelector());
        Format opus = audio("251", MimeTypes.AUDIO_OPUS, "opus", 200_000, false, false);
        Format aacVoiceBoost = audio("140", MimeTypes.AUDIO_AAC, "mp4a.40.2", 131_000, false, true);
        Format aac = audio("140", MimeTypes.AUDIO_AAC, "mp4a.40.2", 130_000, false, false);

        assertSame(opus, select(saved("251", "opus", 200_000, false, false), opus, aacVoiceBoost, aac));

        TrackErrorFixer fixer = new TrackErrorFixer(mManager);
        boolean isFixed = fixer.fixError(new InvalidResponseCodeException(503, Collections.emptyMap(),
                new DataSpec(Uri.parse("https://example.com/videoplayback/mime/audio"))));

        assertTrue(isFixed);
        MediaTrack selected = mManager.getSelectedTrack(TrackSelectorManager.RENDERER_INDEX_AUDIO);
        assertSame(aac, selected.format);
        assertFalse(TrackSelectorUtil.isVb(selected.format));
    }

    private Format select(MediaTrack origin, Format... formats) {
        mManager.selectTrack(origin);
        TrackGroupArray groups = new TrackGroupArray(new TrackGroup(formats));

        Pair<Definition, MediaTrack> selection = mManager.onSelectAudioTrack(groups, DefaultTrackSelector.Parameters.DEFAULT);

        assertNotNull(selection);
        return selection.second.format;
    }

    /**
     * Fake format restored from the saved preferences
     */
    private static MediaTrack saved(String id, String codecs, int bitrate, boolean isDrc, boolean isVb) {
        return ExoFormatItem.from(FormatItem.TYPE_AUDIO, TrackSelectorManager.RENDERER_INDEX_AUDIO, id, codecs,
                -1, -1, -1, "en", false, bitrate, isDrc, isVb).getTrack();
    }

    private static Format opus(boolean isDrc, boolean isVb) {
        return audio("251", MimeTypes.AUDIO_OPUS, "opus", 140_000, isDrc, isVb);
    }

    private static Format audio(String id, String mimeType, String codecs, int bitrate, boolean isDrc, boolean isVb) {
        return Format.createAudioSampleFormat(id, mimeType, codecs, bitrate, Format.NO_VALUE, 2, 48_000,
                null, null, 0, "en", isDrc, isVb);
    }
}
