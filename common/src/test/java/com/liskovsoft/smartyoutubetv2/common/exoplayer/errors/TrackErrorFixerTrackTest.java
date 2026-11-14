package com.liskovsoft.smartyoutubetv2.common.exoplayer.errors;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.MediaSourceEventListener.MediaLoadData;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlayerFailure;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * K067TC3S #7–#18: 429s of SABR requests and of subtitles were all "other", since only a DASH segment's URL names its mime type.
 * The track is the load's.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TrackErrorFixerTrackTest {
    private static final String SABR_URL = "https://rr3---sn-abc.googlevideo.com/videoplayback?sabr=1&rn=3";
    private static final String TIMEDTEXT_URL = "https://www.youtube.com/api/timedtext?v=x&lang=en&tlang=ar";

    @Test
    public void aLoadIsOfItsTracksType() {
        assertEquals(PlayerFailure.TRACK_VIDEO, TrackErrorFixer.trackOf(load(C.TRACK_TYPE_VIDEO, null), SABR_URL));
        assertEquals(PlayerFailure.TRACK_AUDIO, TrackErrorFixer.trackOf(load(C.TRACK_TYPE_AUDIO, null), SABR_URL));
        assertEquals(PlayerFailure.TRACK_TEXT, TrackErrorFixer.trackOf(load(C.TRACK_TYPE_TEXT, null), TIMEDTEXT_URL));
        // Muxed (the URL list's)
        assertEquals(PlayerFailure.TRACK_VIDEO, TrackErrorFixer.trackOf(load(C.TRACK_TYPE_DEFAULT, null), SABR_URL));
    }

    /**
     * A sideloaded subtitle's load has no track type (see SingleSampleMediaPeriod), its format has
     */
    @Test
    public void aLoadOfNoTrackTypeIsOfItsFormats() {
        Format vtt = Format.createTextSampleFormat(null, MimeTypes.TEXT_VTT, 0, "ar");

        assertEquals(PlayerFailure.TRACK_TEXT, TrackErrorFixer.trackOf(load(C.TRACK_TYPE_UNKNOWN, vtt), TIMEDTEXT_URL));
    }

    @Test
    public void aLoadNotKnownGoesByItsUrl() {
        assertEquals(PlayerFailure.TRACK_AUDIO, TrackErrorFixer.trackOf(null, "https://x.googlevideo.com/videoplayback/mime/audio%2Fwebm"));
        assertEquals(PlayerFailure.TRACK_OTHER, TrackErrorFixer.trackOf(load(C.TRACK_TYPE_UNKNOWN, null), SABR_URL));
    }

    /**
     * The domain only: a media server's name says where the user is
     */
    @Test
    public void aHostIsItsDomain() {
        assertEquals("googlevideo.com", PlayerFailure.describeHost("rr3---sn-4g5e6nsz.googlevideo.com"));
        assertEquals("youtube.com", PlayerFailure.describeHost("www.youtube.com"));
        assertEquals("other", PlayerFailure.describeHost("example.org"));
        assertEquals("other", PlayerFailure.describeHost("notgooglevideo.com"));
        assertNull(PlayerFailure.describeHost(null));
    }

    private static MediaLoadData load(int trackType, Format format) {
        return new MediaLoadData(C.DATA_TYPE_MEDIA, trackType, format, C.SELECTION_REASON_UNKNOWN, null, 0, 0);
    }
}
