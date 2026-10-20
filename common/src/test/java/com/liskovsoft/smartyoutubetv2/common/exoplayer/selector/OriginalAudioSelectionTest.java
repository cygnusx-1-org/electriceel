package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import android.util.Pair;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection.Definition;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * Audio language "Original" (saved as "") has to find the original among the dubs, even when no track is labelled original.
 * Every format here has the same bitrate, so without the language filter the first group wins.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OriginalAudioSelectionTest {
    private TrackSelectorManager mManager;

    @Before
    public void setUp() {
        mManager = new TrackSelectorManager(RuntimeEnvironment.getApplication());
        PlayerData.instance(RuntimeEnvironment.getApplication()).setAudioLanguage("");
    }

    @Test
    public void labelledOriginal_beatsAutoDubs() {
        Format original = audio("en (original)");

        assertSame(original, select(audio("pt (dubbed-auto)"), audio("de (dubbed-auto)"), original));
    }

    @Test
    public void unlabelledOriginal_beatsAutoDubs() {
        // E.g. a url xtags with a lang but no acont
        Format original = audio("en");

        assertSame(original, select(audio("pt (dubbed-auto)"), audio("de (dubbed-auto)"), original));
    }

    @Test
    public void unlabelledOriginal_beatsDubs() {
        Format original = audio("en");

        assertSame(original, select(audio("fr (dubbed)"), audio("de (dubbed-auto)"), original));
    }

    @Test
    public void originalLabelledDubbed_beatsAutoDubs() {
        // An audio track id without the original id (.4) is labelled dubbed
        Format original = audio("en (dubbed)");

        assertSame(original, select(audio("pt (dubbed-auto)"), audio("de (dubbed-auto)"), original));
    }

    @Test
    public void onlyAutoDubs_selectsOneOfThem() {
        Format first = audio("pt (dubbed-auto)");

        assertSame(first, select(first, audio("de (dubbed-auto)"), audio("fr (dubbed-auto)")));
    }

    private Format select(Format... formats) {
        mManager.selectTrack(savedOriginal());

        TrackGroup[] groups = new TrackGroup[formats.length];

        for (int i = 0; i < formats.length; i++) {
            groups[i] = new TrackGroup(formats[i]); // one group per language
        }

        Pair<Definition, MediaTrack> selection = mManager.onSelectAudioTrack(new TrackGroupArray(groups), DefaultTrackSelector.Parameters.DEFAULT);

        assertNotNull(selection);
        return selection.second.format;
    }

    /**
     * Fake format restored from the saved preferences
     */
    private static MediaTrack savedOriginal() {
        MediaTrack track = ExoFormatItem.from(FormatItem.TYPE_AUDIO, TrackSelectorManager.RENDERER_INDEX_AUDIO, "251", "opus",
                -1, -1, -1, "en", false, 140_000, false, false).getTrack();
        track.isSaved = true;
        return track;
    }

    private static Format audio(String language) {
        return Format.createAudioSampleFormat("251", MimeTypes.AUDIO_OPUS, "opus", 140_000, Format.NO_VALUE, 2, 48_000,
                null, null, 0, language, false, false);
    }
}
