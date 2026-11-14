package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * K067TC3S, aBcubRmx73g: the init chunks on server 1, the first chunks on server 2, each opening a connection (237 ms median there).
 * A redirect to another media host is connected to at once, through the OkHttp engine only, once per host for a while.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SabrHostPrewarmerTest {
    private static final String FROM = "https://rr1---sn-aaa.googlevideo.com/videoplayback?sabr=1&rn=0";
    private static final String TO = "https://rr4---sn-bbb.googlevideo.com/videoplayback?sabr=1&rn=1";
    private final List<String> mRequested = new ArrayList<>();
    private boolean mIsOkHttp = true;

    @Before
    public void setUp() {
        SabrHostPrewarmer.resetForTesting();
        SabrHostPrewarmer.sRequester = mRequested::add;
        SabrHostPrewarmer.sEngineCheck = () -> mIsOkHttp;
    }

    @After
    public void tearDown() {
        SabrHostPrewarmer.resetForTesting();
    }

    @Test
    public void aRedirectToAnotherMediaHostIsConnectedToAhead() {
        SabrHostPrewarmer.onRedirect(FROM, TO);

        assertEquals(1, mRequested.size());
        assertEquals("https://rr4---sn-bbb.googlevideo.com" + SabrHostPrewarmer.PATH, mRequested.get(0));
    }

    @Test
    public void aRedirectToTheSameHostIsnt() {
        SabrHostPrewarmer.onRedirect(FROM, FROM.replace("rn=0", "rn=1"));

        assertTrue(mRequested.isEmpty());
    }

    @Test
    public void theSameHostIsntConnectedToAgainSoon() {
        SabrHostPrewarmer.onRedirect(FROM, TO);
        SabrHostPrewarmer.onRedirect(FROM, TO);

        assertEquals(1, mRequested.size());
    }

    /**
     * Cronet and the default engine keep pools of their own
     */
    @Test
    public void notWithAnotherEngine() {
        mIsOkHttp = false;
        SabrHostPrewarmer.onRedirect(FROM, TO);

        assertTrue(mRequested.isEmpty());
    }

    @Test
    public void notToAHostThatIsntMedia() {
        SabrHostPrewarmer.onRedirect(FROM, "https://example.org/videoplayback");
        SabrHostPrewarmer.onRedirect(null, "not a url");

        assertTrue(mRequested.isEmpty());
    }

    /**
     * The first stream's redirect: there was no URL before
     */
    @Test
    public void aRedirectWithNoUrlBeforeIsConnectedToAhead() {
        SabrHostPrewarmer.onRedirect(null, TO);

        assertEquals(1, mRequested.size());
    }
}
