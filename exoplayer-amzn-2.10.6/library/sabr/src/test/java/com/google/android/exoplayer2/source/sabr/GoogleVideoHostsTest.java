package com.google.android.exoplayer2.source.sabr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class GoogleVideoHostsTest {
    /**
     * As in the player response fixtures: the host's prefix (rr1, the mvi) differs from the fvip
     */
    private static final String URL = "https://rr1---sn-4gxb5u-qo3l.googlevideo.com/videoplayback?expire=1762756396&itag=18"
            + "&mh=iw&mm=31%2C29&mn=sn-4gxb5u-qo3l%2Csn-3c27sn7y&ms=au%2Crdu&mv=m&mvi=1&pl=25&fvip=8&c=WEB&sig=AOq0QJ8";

    @Before
    public void setUp() {
        GoogleVideoHosts.clearFailedNetworks();
    }

    @Test
    public void fallbackUrlIsOnTheFvipHostOfTheSecondNetwork() {
        assertEquals(URL.replace("rr1---sn-4gxb5u-qo3l", "r8---sn-3c27sn7y") + "&fallback_count=1",
                GoogleVideoHosts.toFallbackUrl(URL));
    }

    @Test
    public void fallbackUrlKeepsThePortAndTheFragment() {
        String url = "https://rr1---sn-a.googlevideo.com:8443/videoplayback?mn=sn-a,sn-b&fvip=3#frag";

        assertEquals("https://r3---sn-b.googlevideo.com:8443/videoplayback?mn=sn-a,sn-b&fvip=3&fallback_count=1#frag",
                GoogleVideoHosts.toFallbackUrl(url));
    }

    @Test
    public void noFallbackUrlWithoutFvipOrASecondNetwork() {
        assertNull(GoogleVideoHosts.toFallbackUrl("https://rr1---sn-a.googlevideo.com/videoplayback?mn=sn-a%2Csn-b"));
        assertNull(GoogleVideoHosts.toFallbackUrl("https://rr1---sn-a.googlevideo.com/videoplayback?mn=sn-a&fvip=3"));
        assertNull(GoogleVideoHosts.toFallbackUrl("https://rr1---sn-a.googlevideo.com/videoplayback?mn=sn-a%2C&fvip=3"));
        assertNull(GoogleVideoHosts.toFallbackUrl("https://rr1---sn-a.googlevideo.com/videoplayback?fvip=3"));
        assertNull(GoogleVideoHosts.toFallbackUrl("https://rr1---sn-a.googlevideo.com/videoplayback?mn=sn-a%2Csn-b&fvip="));
    }

    @Test
    public void noFallbackUrlForAnotherDomainOrABadHostLabel() {
        assertNull(GoogleVideoHosts.toFallbackUrl("https://example.com/videoplayback?mn=sn-a%2Csn-b&fvip=3"));
        assertNull(GoogleVideoHosts.toFallbackUrl("https://rr1---sn-a.googlevideo.com/videoplayback?mn=sn-a%2Csn-b.evil.com%2F&fvip=3"));
        assertNull(GoogleVideoHosts.toFallbackUrl("https://rr1---sn-a.googlevideo.com/videoplayback?mn=sn-a%2Csn-b&fvip=3.x"));
        assertNull(GoogleVideoHosts.toFallbackUrl(null));
        assertNull(GoogleVideoHosts.toFallbackUrl("https://rr1---sn-a.googlevideo.com/%"));
    }

    @Test
    public void noFallbackUrlWhenAlreadyOnIt() {
        assertNull(GoogleVideoHosts.toFallbackUrl("https://R3---sn-b.googlevideo.com/videoplayback?mn=sn-a%2Csn-b&fvip=3"));
    }

    @Test
    public void redirectorUrlAsksForAWorkingHost() {
        assertEquals(URL.replace("rr1---sn-4gxb5u-qo3l", "redirector") + "&cmo=pf%3D1", GoogleVideoHosts.toRedirectorUrl(URL));
    }

    @Test
    public void redirectorUrlOfAnA1HostNamesItsDomain() {
        assertEquals("https://redirector.googlevideo.com/videoplayback?id=1&cmo=pf%3D1&cmo=td%3Da1.googlevideo.com",
                GoogleVideoHosts.toRedirectorUrl("https://r1---sn-a.c.a1.googlevideo.com/videoplayback?id=1"));
    }

    @Test
    public void noRedirectorUrlForAnotherDomainOrTheRedirectorItself() {
        assertNull(GoogleVideoHosts.toRedirectorUrl("https://rr1---sn-a.example.com/videoplayback?id=1"));
        assertNull(GoogleVideoHosts.toRedirectorUrl("https://redirector.googlevideo.com/videoplayback?id=1&cmo=pf%3D1"));
        assertNull(GoogleVideoHosts.toRedirectorUrl(null));
    }

    @Test
    public void noRedirectorUrlForAManifestOrAPathStyleUrl() {
        assertNull(GoogleVideoHosts.toRedirectorUrl("https://manifest.googlevideo.com/api/manifest/hls_playlist/expire/1/id/x"));
        assertNull(GoogleVideoHosts.toRedirectorUrl("https://rr1---sn-a.googlevideo.com/videoplayback/expire/1/itag/134/sq/5"));
    }

    @Test
    public void sameMediaIgnoresTheRequestsOwnParameters() {
        assertTrue(GoogleVideoHosts.isSameMedia(URL + "&range=0-1000", URL + "&rn=4&sq=7"));
    }

    @Test
    public void sameMediaComparesHostPathAndSignatures() {
        assertFalse(GoogleVideoHosts.isSameMedia(URL, URL.replace("rr1---", "rr2---")));
        assertFalse(GoogleVideoHosts.isSameMedia(URL, URL.replace("/videoplayback", "/videoplayback2")));
        assertFalse(GoogleVideoHosts.isSameMedia(URL, URL.replace("sig=AOq0QJ8", "sig=other")));
        assertFalse(GoogleVideoHosts.isSameMedia(URL, URL + "&lsig=x"));
        assertFalse(GoogleVideoHosts.isSameMedia(URL + "&signature=a", URL + "&signature=b"));
        assertFalse(GoogleVideoHosts.isSameMedia(URL, null));
        assertTrue(GoogleVideoHosts.isSameMedia(URL, URL.replace("rr1---sn-4gxb5u-qo3l", "RR1---sn-4gxb5u-qo3l")));
    }

    @Test
    public void failedNetworkCoversEveryHostOfIt() {
        GoogleVideoHosts.markNetworkFailed(URL);

        assertTrue(GoogleVideoHosts.isNetworkFailed("https://rr7---sn-4gxb5u-qo3l.googlevideo.com/videoplayback?itag=1"));
        assertFalse(GoogleVideoHosts.isNetworkFailed(GoogleVideoHosts.toFallbackUrl(URL)));
        assertFalse(GoogleVideoHosts.isNetworkFailed("https://redirector.googlevideo.com/videoplayback"));
    }

    @Test
    public void hostsWithoutANetworkAreNeverMarked() {
        GoogleVideoHosts.markNetworkFailed("https://redirector.googlevideo.com/videoplayback");
        GoogleVideoHosts.markNetworkFailed("https://example.com/videoplayback");

        assertFalse(GoogleVideoHosts.isNetworkFailed("https://redirector.googlevideo.com/videoplayback"));
        assertFalse(GoogleVideoHosts.isNetworkFailed("https://example.com/videoplayback"));
    }
}
