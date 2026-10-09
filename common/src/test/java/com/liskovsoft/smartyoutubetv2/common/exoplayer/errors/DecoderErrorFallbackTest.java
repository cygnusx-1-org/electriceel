package com.liskovsoft.smartyoutubetv2.common.exoplayer.errors;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DecoderErrorFallbackTest {
    @Test
    public void vp9_4k_fallsBackToAvc1080() {
        assertAvc(1920, 1080, DecoderErrorFallback.getVideoFormat(video(MimeTypes.VIDEO_VP9, "vp9", 3840, 2160)));
    }

    @Test
    public void av1_1080_fallsBackToAvcOfTheSameSize() {
        assertAvc(1920, 1080, DecoderErrorFallback.getVideoFormat(video(MimeTypes.VIDEO_AV1, "av01.0.09M.08", 1920, 1080)));
    }

    @Test
    public void vp9_720_isNotRaisedTo1080() {
        assertAvc(1280, 720, DecoderErrorFallback.getVideoFormat(video(MimeTypes.VIDEO_VP9, "vp9", 1280, 720)));
    }

    @Test
    public void ultraWide_isSizedByItsWidth() {
        // 2560x1066 is 1440p by the app's labels
        assertAvc(1920, 1080, DecoderErrorFallback.getVideoFormat(video(MimeTypes.VIDEO_VP9, "vp9", 2560, 1066)));
    }

    @Test
    public void avc_goesASizeDown() {
        assertAvc(1280, 720, DecoderErrorFallback.getVideoFormat(video(MimeTypes.VIDEO_H264, "avc1.64002A", 1920, 1080)));
        assertAvc(854, 480, DecoderErrorFallback.getVideoFormat(video(MimeTypes.VIDEO_H264, "avc1.4D401F", 1280, 720)));
    }

    @Test
    public void avc_360_hasNothingSmaller() {
        assertNull(DecoderErrorFallback.getVideoFormat(video(MimeTypes.VIDEO_H264, "avc1.4D401E", 640, 360)));
    }

    @Test
    public void vp9_240_hasNoAvcThatSmall() {
        assertNull(DecoderErrorFallback.getVideoFormat(video(MimeTypes.VIDEO_VP9, "vp9", 426, 240)));
    }

    @Test
    public void unknownSize_nonAvc_fallsBackToAvc1080() {
        assertAvc(1920, 1080, DecoderErrorFallback.getVideoFormat(video(MimeTypes.VIDEO_VP9, "vp9", Format.NO_VALUE, Format.NO_VALUE)));
    }

    @Test
    public void unknownSize_avc_goesASizeDownFrom1080() {
        assertAvc(1280, 720, DecoderErrorFallback.getVideoFormat(video(MimeTypes.VIDEO_H264, "avc1.64002A", Format.NO_VALUE, Format.NO_VALUE)));
    }

    @Test
    public void unknownVideoFormat_hasNoFallback() {
        assertNull(DecoderErrorFallback.getVideoFormat(null));
    }

    @Test
    public void opus_fallsBackToAacInTheSameLanguage() {
        FormatItem format = DecoderErrorFallback.getAudioFormat(audio(MimeTypes.AUDIO_OPUS, "opus"), "en");

        assertEquals(FormatItem.TYPE_AUDIO, format.getType());
        assertEquals("mp4a", format.getTrack().format.codecs);
        assertEquals("en", format.getLanguage());
    }

    @Test
    public void opus_withTheDefaultLanguage_fallsBackToAacInTheDefaultLanguage() {
        FormatItem format = DecoderErrorFallback.getAudioFormat(audio(MimeTypes.AUDIO_OPUS, "opus"), null);

        assertEquals("mp4a", format.getTrack().format.codecs);
        assertNull(format.getLanguage());
    }

    @Test
    public void aac_hasNoFallback() {
        assertNull(DecoderErrorFallback.getAudioFormat(audio(MimeTypes.AUDIO_AAC, "mp4a.40.2"), "en"));
    }

    @Test
    public void unknownAudioFormat_hasNoFallback() {
        assertNull(DecoderErrorFallback.getAudioFormat(null, "en"));
    }

    private static void assertAvc(int width, int height, FormatItem format) {
        assertEquals(FormatItem.TYPE_VIDEO, format.getType());
        assertEquals("avc", format.getTrack().format.codecs);
        assertEquals(width, format.getWidth());
        assertEquals(height, format.getHeight());
        assertEquals(60, format.getFrameRate(), 0);
        // No id: matched as a limit, like a preset (see VideoTrack.inBounds)
        assertNull(format.getTrack().format.id);
    }

    private static Format video(String mimeType, String codecs, int width, int height) {
        return Format.createVideoSampleFormat("id", mimeType, codecs, Format.NO_VALUE, Format.NO_VALUE, width, height, 60, null, null);
    }

    private static Format audio(String mimeType, String codecs) {
        return Format.createAudioSampleFormat("id", mimeType, codecs, Format.NO_VALUE, Format.NO_VALUE, 2, 48_000, null, null, 0, "en");
    }
}
