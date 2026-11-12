package com.google.android.exoplayer2.source.sabr.manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.android.exoplayer2.source.sabr.GoogleVideoHosts;

import org.junit.Before;
import org.junit.Test;

public class SabrCdnSelectorTest {
    private static final String PRIMARY_URL =
            "https://rr1---sn-primary.googlevideo.com/videoplayback?"
                    + "mn=sn-primary%2Csn-secondary&fvip=4&sig=signed";
    private static final String FALLBACK_URL =
            "https://r4---sn-secondary.googlevideo.com/videoplayback?"
                    + "mn=sn-primary%2Csn-secondary&fvip=4&sig=signed&fallback_count=1";

    @Before
    public void setUp() {
        GoogleVideoHosts.clearFailedNetworks();
    }

    @Test
    public void startsWithOriginalUrlAndAdvancesOnceToTheFvipHost() {
        SabrCdnSelector selector = new SabrCdnSelector(PRIMARY_URL);

        assertEquals(PRIMARY_URL, selector.getCurrentUrl());
        assertTrue(selector.maybeAdvance(PRIMARY_URL));
        assertEquals(FALLBACK_URL, selector.getCurrentUrl());
        assertFalse(selector.maybeAdvance(FALLBACK_URL));
        assertEquals(FALLBACK_URL, selector.getCurrentUrl());
    }

    @Test
    public void failureOfARequestWithItsOwnParametersIsMatchedByHost() {
        SabrCdnSelector selector = new SabrCdnSelector(PRIMARY_URL);

        assertTrue(selector.maybeAdvance(PRIMARY_URL + "&rn=7"));
        assertEquals(FALLBACK_URL, selector.getCurrentUrl());
    }

    @Test
    public void staleFailureRemainsRetryableAfterAnotherTrackAdvances() {
        SabrCdnSelector selector = new SabrCdnSelector(PRIMARY_URL);

        assertTrue(selector.maybeAdvance(PRIMARY_URL));
        assertTrue(selector.maybeAdvance(PRIMARY_URL));
        assertEquals(FALLBACK_URL, selector.getCurrentUrl());
    }

    @Test
    public void onlyTheSecondNetworkIsUsedWhateverTheirCount() {
        String primary = "https://rr1---sn-primary.googlevideo.com/videoplayback?"
                + "mn=sn-primary%2Csn-secondary%2Csn-tertiary&fvip=2&sig=signed";
        String fallback = "https://r2---sn-secondary.googlevideo.com/videoplayback?"
                + "mn=sn-primary%2Csn-secondary%2Csn-tertiary&fvip=2&sig=signed&fallback_count=1";
        SabrCdnSelector selector = new SabrCdnSelector(primary);

        assertTrue(selector.maybeAdvance(primary));
        assertEquals(fallback, selector.getCurrentUrl());
        assertFalse(selector.maybeAdvance(fallback));
        assertEquals(fallback, selector.getCurrentUrl());
    }

    @Test
    public void urlWithoutFvipHasNoFallback() {
        String url = "https://rr1---sn-primary.googlevideo.com/videoplayback?"
                + "mn=sn-primary%2Csn-secondary&sig=signed";
        SabrCdnSelector selector = new SabrCdnSelector(url);

        assertFalse(selector.maybeAdvance(url));
        assertEquals(url, selector.getCurrentUrl());
    }

    @Test
    public void urlWithASingleNetworkHasNoFallback() {
        String url = "https://rr1---sn-primary.googlevideo.com/videoplayback?mn=sn-primary&fvip=4";
        SabrCdnSelector selector = new SabrCdnSelector(url);

        assertFalse(selector.maybeAdvance(url));
        assertEquals(url, selector.getCurrentUrl());
    }

    @Test
    public void urlAlreadyOnTheFallbackHostHasNoFallback() {
        String url = "https://r4---sn-secondary.googlevideo.com/videoplayback?"
                + "mn=sn-primary%2Csn-secondary&fvip=4";
        SabrCdnSelector selector = new SabrCdnSelector(url);

        assertFalse(selector.maybeAdvance(url));
        assertEquals(url, selector.getCurrentUrl());
    }

    @Test
    public void unrelatedHostDoesNotChangeSelection() {
        SabrCdnSelector selector = new SabrCdnSelector(PRIMARY_URL);

        assertFalse(selector.maybeAdvance("https://redirect.googlevideo.com/videoplayback"));
        assertEquals(PRIMARY_URL, selector.getCurrentUrl());
    }

    @Test
    public void malformedCandidateIsIgnoredDuringFailureHandling() {
        String malformedUrl = "https://rr1---sn-primary.googlevideo.com/%";
        SabrCdnSelector selector = new SabrCdnSelector(malformedUrl);

        assertFalse(selector.maybeAdvance(PRIMARY_URL));
        assertEquals(malformedUrl, selector.getCurrentUrl());
    }

    @Test
    public void newSelectorSkipsRecentlyFailedNetwork() {
        SabrCdnSelector first = new SabrCdnSelector(PRIMARY_URL);
        assertTrue(first.maybeAdvance(PRIMARY_URL));

        // e.g. the engine was restarted: a new manifest is built from the same player response
        SabrCdnSelector second = new SabrCdnSelector(PRIMARY_URL);

        assertEquals(FALLBACK_URL, second.getCurrentUrl());
    }

    @Test
    public void newSelectorSkipsANetworkTheDashRequestsMarkedFailed() {
        GoogleVideoHosts.markNetworkFailed("https://rr5---sn-primary.googlevideo.com/videoplayback?itag=137");

        assertEquals(FALLBACK_URL, new SabrCdnSelector(PRIMARY_URL).getCurrentUrl());
    }

    @Test
    public void lastNetworkIsKeptEvenIfAllFailed() {
        SabrCdnSelector first = new SabrCdnSelector(PRIMARY_URL);
        assertTrue(first.maybeAdvance(PRIMARY_URL));
        GoogleVideoHosts.markNetworkFailed(FALLBACK_URL);

        SabrCdnSelector second = new SabrCdnSelector(PRIMARY_URL);

        assertEquals(FALLBACK_URL, second.getCurrentUrl());
    }

    @Test
    public void failedNetworkDoesNotAffectUnrelatedUrls() {
        SabrCdnSelector first = new SabrCdnSelector(PRIMARY_URL);
        assertTrue(first.maybeAdvance(PRIMARY_URL));

        String other = "https://rr3---sn-other.googlevideo.com/videoplayback?"
                + "mn=sn-other%2Csn-secondary&fvip=4&sig=signed";

        assertEquals(other, new SabrCdnSelector(other).getCurrentUrl());
    }
}
