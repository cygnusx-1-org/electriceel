package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.android.exoplayer2.source.sabr.GoogleVideoHosts;

import org.junit.Before;
import org.junit.Test;

public class GoogleVideoFallbackTest {
    private static final String GIVEN = "https://rr1---sn-given.googlevideo.com/videoplayback?itag=137&mn=sn-given%2Csn-second&fvip=3&sig=AB";
    private static final String FALLBACK = "https://r3---sn-second.googlevideo.com/videoplayback?itag=137&mn=sn-given%2Csn-second&fvip=3&sig=AB"
            + "&fallback_count=1";
    private static final String REDIRECTOR = "https://redirector.googlevideo.com/videoplayback?itag=137&mn=sn-given%2Csn-second&fvip=3&sig=AB"
            + "&cmo=pf%3D1";
    private static final boolean GET = true;
    private static final boolean POST = false;
    private static final boolean QUICK = false;
    private static final boolean TIMEOUT = true;
    private GoogleVideoFallback mFallback;

    @Before
    public void setUp() {
        GoogleVideoHosts.clearFailedNetworks();
        mFallback = new GoogleVideoFallback();
    }

    @Test
    public void givenHostIsRetriedOnceAfterAQuickFailure() {
        assertEquals(GIVEN, mFallback.resolve(GIVEN, GET));
        assertNull(mFallback.onHostFailure(GIVEN, QUICK, GET));
        assertEquals(GIVEN, mFallback.resolve(GIVEN, GET));
        assertFalse(GoogleVideoHosts.isNetworkFailed(GIVEN));
    }

    @Test
    public void everyHostIsTriedInTheLoadersFourAttempts() {
        String[] requested = new String[4];

        for (int attempt = 0; attempt < requested.length; attempt++) {
            requested[attempt] = mFallback.resolve(GIVEN, GET);
            mFallback.onHostFailure(requested[attempt], QUICK, GET);
        }

        assertEquals(GIVEN, requested[0]);
        assertEquals(GIVEN, requested[1]);
        assertEquals(FALLBACK, requested[2]);
        assertEquals(REDIRECTOR, requested[3]);
        assertEquals(REDIRECTOR, mFallback.resolve(GIVEN, GET)); // the last resort is kept
    }

    @Test
    public void timeoutLeavesTheGivenHostAtOnce() {
        mFallback.resolve(GIVEN, GET);

        assertEquals(FALLBACK, mFallback.onHostFailure(GIVEN, TIMEOUT, GET));
        assertEquals(FALLBACK, mFallback.resolve(GIVEN, GET));
    }

    @Test
    public void leftNetworksAreMarkedFailed() {
        mFallback.resolve(GIVEN, GET);
        mFallback.onHostFailure(GIVEN, TIMEOUT, GET);

        assertTrue(GoogleVideoHosts.isNetworkFailed(GIVEN));
        assertFalse(GoogleVideoHosts.isNetworkFailed(FALLBACK));

        mFallback.resolve(GIVEN, GET);
        assertEquals(REDIRECTOR, mFallback.onHostFailure(FALLBACK, QUICK, GET));
        assertTrue(GoogleVideoHosts.isNetworkFailed(FALLBACK));
    }

    @Test
    public void successResetsTheCount() {
        mFallback.resolve(GIVEN, GET);
        mFallback.onHostFailure(GIVEN, QUICK, GET);
        mFallback.resolve(GIVEN, GET);
        mFallback.onSuccess();
        mFallback.resolve(GIVEN, GET);

        assertNull(mFallback.onHostFailure(GIVEN, QUICK, GET));
        assertEquals(GIVEN, mFallback.resolve(GIVEN, GET));
    }

    @Test
    public void postNeverGoesToTheRedirector() {
        mFallback.resolve(GIVEN, POST);
        assertEquals(FALLBACK, mFallback.onHostFailure(GIVEN, TIMEOUT, POST));
        mFallback.resolve(GIVEN, POST);

        assertNull(mFallback.onHostFailure(FALLBACK, QUICK, POST));
        assertEquals(FALLBACK, mFallback.resolve(GIVEN, POST));
        assertEquals(GoogleVideoFallback.STEP_FALLBACK, mFallback.getStep());
    }

    @Test
    public void urlWithoutFvipGoesToTheRedirector() {
        String url = "https://rr1---sn-given.googlevideo.com/videoplayback?itag=137&sig=AB";
        mFallback.resolve(url, GET);
        mFallback.onHostFailure(url, QUICK, GET);
        mFallback.resolve(url, GET);

        assertEquals("https://redirector.googlevideo.com/videoplayback?itag=137&sig=AB&cmo=pf%3D1",
                mFallback.onHostFailure(url, QUICK, GET));
    }

    @Test
    public void postWithoutFvipStaysAndMarksNothing() {
        String url = "https://rr1---sn-given.googlevideo.com/videoplayback?itag=137&sig=AB";
        mFallback.resolve(url, POST);

        assertNull(mFallback.onHostFailure(url, TIMEOUT, POST));
        assertEquals(url, mFallback.resolve(url, POST));
        assertFalse(GoogleVideoHosts.isNetworkFailed(url));
    }

    @Test
    public void otherHostsAreUntouched() {
        String url = "https://example.com/video.mp4";
        mFallback.resolve(url, GET);

        assertNull(mFallback.onHostFailure(url, TIMEOUT, GET));
        assertEquals(url, mFallback.resolve(url, GET));
    }

    @Test
    public void stepHoldsForTheSameMediasNextRequests() {
        mFallback.resolve(GIVEN, GET);
        mFallback.onHostFailure(GIVEN, TIMEOUT, GET);

        assertEquals(FALLBACK.replace("&fallback_count=1", "&rn=2&fallback_count=1"), mFallback.resolve(GIVEN + "&rn=2", GET));
    }

    @Test
    public void anotherMediaOfALeftNetworkStartsOnItsFallbackHost() {
        mFallback.resolve(GIVEN, GET);
        mFallback.onHostFailure(GIVEN, TIMEOUT, GET);
        mFallback.resolve(GIVEN, GET);
        mFallback.onHostFailure(FALLBACK, QUICK, GET); // on the redirector now

        String audio = GIVEN.replace("itag=137", "itag=140").replace("sig=AB", "sig=CD");

        assertEquals(FALLBACK.replace("itag=137", "itag=140").replace("sig=AB", "sig=CD"), mFallback.resolve(audio, GET));
    }

    @Test
    public void anotherTracksLeftNetworkIsSkipped() {
        GoogleVideoHosts.markNetworkFailed(GIVEN);

        assertEquals(FALLBACK, mFallback.resolve(GIVEN, GET));
    }

    @Test
    public void anotherMediaOfAnotherNetworkStartsOnItsGivenHost() {
        mFallback.resolve(GIVEN, GET);
        mFallback.onHostFailure(GIVEN, TIMEOUT, GET);

        String other = "https://rr2---sn-other.googlevideo.com/videoplayback?itag=137&mn=sn-other%2Csn-second&fvip=3&sig=EF";

        assertEquals(other, mFallback.resolve(other, GET));
    }
}
