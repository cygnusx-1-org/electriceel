package com.google.android.exoplayer2.source.dash.manifest;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2.C;
import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * A video's period ends where its longest track does. Rounded down, its whole seconds (lengthSeconds) cut the last second off
 * (#43); rounded up, they're past the media (9lKpEOEbr0U: 1972 of 1971.792 s), where a video stopped at its length never got
 * ready over DASH, and the stall check reloaded it from 0:00
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 28)
public class DashManifestParser2Test {
    @Test
    public void aVideoRoundedUpEndsWithItsMedia() {
        DashManifest manifest = new DashManifestParser2().parse(formatInfo("1972", false,
                format(1_971_719), format(1_971_792), format(1_971_741)));

        assertEquals(1_971_792, manifest.durationMs);
    }

    @Test
    public void aVideoRoundedDownIsntCutShort() {
        assertEquals(1_971_400, new DashManifestParser2().parse(formatInfo("1971", false, format(1_971_400))).durationMs);
    }

    @Test
    public void withoutFormatLengthsItsWholeSeconds() {
        assertEquals(1_972_000, new DashManifestParser2().parse(formatInfo("1972", false, format(0))).durationMs);
    }

    @Test
    public void aLiveStreamIsntGivenItsFormatsLength() {
        assertEquals(C.TIME_UNSET, new DashManifestParser2().parse(formatInfo("0", true, format(1_971_792))).durationMs);
    }

    private static MediaItemFormatInfo formatInfo(String lengthSeconds, boolean isLive, MediaFormat... formats) {
        MediaItemFormatInfo formatInfo = mock(MediaItemFormatInfo.class);
        when(formatInfo.getLengthSeconds()).thenReturn(lengthSeconds);
        when(formatInfo.isLive()).thenReturn(isLive);
        when(formatInfo.getVideoId()).thenReturn("9lKpEOEbr0U");
        when(formatInfo.getAdaptiveFormats()).thenReturn(Arrays.asList(formats));
        when(formatInfo.getMediaDurationMs()).thenAnswer(invocation ->
                MediaItemFormatInfo.getMediaDurationMs(formatInfo.getAdaptiveFormats(), formatInfo.getLengthSeconds()));

        return formatInfo;
    }

    /**
     * Its approxDurationMs only: not a format the manifest takes
     */
    private static MediaFormat format(int approxDurationMs) {
        MediaFormat format = mock(MediaFormat.class);
        when(format.getApproxDurationMs()).thenReturn(approxDurationMs);

        return format;
    }
}
