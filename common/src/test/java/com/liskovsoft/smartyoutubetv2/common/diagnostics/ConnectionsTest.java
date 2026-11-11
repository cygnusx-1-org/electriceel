package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ConnectionsTest {
    private static final String HOST = "rr1---sn-example.googlevideo.com";
    private static final InetAddress IPV4 = address(new byte[] {(byte) 142, (byte) 250, 1, 1});
    private static final InetAddress IPV6 = address(new byte[] {0x26, 0x07, (byte) 0xf8, (byte) 0xb0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
    private final AtomicLong mNow = new AtomicLong();
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();
    /**
     * Not run: the listener is told what OkHttp would tell it
     */
    private final Call mCall = new OkHttpClient().newCall(new Request.Builder().url("https://example.com/videoplayback").build());

    @Before
    public void setUp() {
        Connections.resetForTesting();
        Connections.sClock = mNow::get;
    }

    @After
    public void tearDown() {
        Connections.resetForTesting();
        Connections.sClock = android.os.SystemClock::elapsedRealtime;
    }

    /**
     * A segment of 1 KB that took 3.5 s on a connection ready at once: its request waited for the server, not for the bytes
     */
    @Test
    public void aResponseIsTheWaitForItsFirstByte() {
        EventListener listener = Connections.LISTENER.create(mCall);
        mNow.set(1_000);
        listener.requestHeadersStart(mCall);
        mNow.set(4_500);
        listener.responseBodyStart(mCall);
        mNow.set(4_510);
        listener.responseBodyStart(mCall); // told once by OkHttp; a second one changes nothing
        listener.callEnd(mCall);

        List<VideoStart.Stage> stages = Connections.take(500, 5_000);

        assertEquals(1, stages.size());
        assertEquals(VideoStarts.STAGE_RESPONSE, stages.get(0).name);
        assertNull(stages.get(0).detail);
        assertEquals(1, stages.get(0).depth);
        assertEquals(1_000, stages.get(0).startMs);
        assertEquals(4_500, stages.get(0).endMs);
    }

    /**
     * The start is looked at or given up while a request waits for its answer: what it waits for
     */
    @Test
    public void oneWaitingIsStillGoing() {
        EventListener listener = Connections.LISTENER.create(mCall);
        mNow.set(1_000);
        listener.requestHeadersStart(mCall);

        List<VideoStart.Stage> stages = Connections.take(500, 3_000);

        assertEquals(1, stages.size());
        assertEquals(VideoStarts.DETAIL_GOING, stages.get(0).detail);
        assertEquals(1_000, stages.get(0).startMs);
        assertEquals(3_000, stages.get(0).endMs);
    }

    /**
     * Followed up (e.g. a redirect), the response before ends: its body isn't read
     */
    @Test
    public void aFollowUpEndsTheResponseBefore() {
        EventListener listener = Connections.LISTENER.create(mCall);
        mNow.set(1_000);
        listener.requestHeadersStart(mCall);
        mNow.set(1_200);
        listener.requestHeadersStart(mCall);
        mNow.set(1_500);
        listener.responseBodyStart(mCall);

        List<VideoStart.Stage> stages = Connections.take(500, 3_000);

        assertEquals(2, stages.size());
        assertEquals(1_000, stages.get(0).startMs);
        assertEquals(1_200, stages.get(0).endMs);
        assertEquals(1_200, stages.get(1).startMs);
        assertEquals(1_500, stages.get(1).endMs);
    }

    @Test
    public void aFailedCallEndsItsResponse() {
        EventListener listener = Connections.LISTENER.create(mCall);
        mNow.set(1_000);
        listener.requestHeadersStart(mCall);
        mNow.set(2_000);
        listener.callFailed(mCall, new IOException("timeout"));

        List<VideoStart.Stage> stages = Connections.take(500, 3_000);

        assertEquals(1, stages.size());
        assertNull(stages.get(0).detail);
        assertEquals(2_000, stages.get(0).endMs);
    }

    /**
     * Sent before the start, a request is of the source before (e.g. the video before's), even one still waiting
     */
    @Test
    public void responsesOfTheSourceBeforeAreLeftOut() {
        Connections.addResponse(100, 900);
        Connections.addResponse(400, -1);
        Connections.addResponse(600, 900);

        List<VideoStart.Stage> stages = Connections.take(500, 2_000);

        assertEquals(1, stages.size());
        assertEquals(600, stages.get(0).startMs);
        assertEquals(900, stages.get(0).endMs);
    }

    /**
     * The default engine's init chunks took 7.9 s: with no OkHttp client, a request is timed whole, its open, from being made to its
     * headers
     */
    @Test
    public void anOpeningIsTimedFromMadeToHeaders() throws IOException {
        DataSource source = Connections.timeOpenings(new FakeDataSource(7_900, null));
        mNow.set(1_000);
        source.open(new DataSpec(Uri.parse("https://example.com/videoplayback")));
        mNow.set(9_500);
        source.close(); // its end isn't the opening's

        List<VideoStart.Stage> stages = Connections.take(500, 10_000);

        assertEquals(1, stages.size());
        assertEquals(VideoStarts.STAGE_OPENING, stages.get(0).name);
        assertNull(stages.get(0).detail);
        assertEquals(1_000, stages.get(0).startMs);
        assertEquals(8_900, stages.get(0).endMs);
    }

    /**
     * One that fails to open (e.g. a connect timeout) ends then, not still going
     */
    @Test
    public void aFailedOpeningEndsWhenItFails() {
        DataSource source = Connections.timeOpenings(new FakeDataSource(5_000, new IOException("connect timed out")));
        mNow.set(1_000);

        try {
            source.open(new DataSpec(Uri.parse("https://example.com/videoplayback")));
        } catch (IOException e) {
            // As the player gets it
        }

        List<VideoStart.Stage> stages = Connections.take(500, 7_000);

        assertEquals(1, stages.size());
        assertNull(stages.get(0).detail);
        assertEquals(1_000, stages.get(0).startMs);
        assertEquals(6_000, stages.get(0).endMs);
    }

    /**
     * A file or an asset isn't a request
     */
    @Test
    public void onlyNetworkOpeningsAreTimed() throws IOException {
        DataSource source = Connections.timeOpenings(new FakeDataSource(1_000, null));
        mNow.set(1_000);
        source.open(new DataSpec(Uri.parse("file:///data/video.mp4")));

        assertTrue(Connections.take(500, 3_000).isEmpty());
    }

    @Test
    public void onlyTheLastResponsesAreKept() {
        for (int i = 0; i < Connections.MAX_RESPONSES + 1; i++) {
            Connections.addResponse(i * 1_000L, i * 1_000L + 500);
        }

        assertEquals(Connections.MAX_RESPONSES, Connections.take(0, 100_000).size());
        assertTrue(Connections.take(0, 999).isEmpty());
    }

    /**
     * Its lookup, then the address that connected (TLS included)
     */
    @Test
    public void aConnectSaysItsLookupAndAddress() throws IOException {
        EventListener listener = Connections.LISTENER.create(mCall);
        mNow.set(1_000);
        listener.dnsStart(mCall, HOST);
        mNow.set(1_002);
        listener.dnsEnd(mCall, HOST, Collections.singletonList(IPV4));
        listener.connectStart(mCall, new InetSocketAddress(IPV4, 443), Proxy.NO_PROXY);
        mNow.set(1_070);
        listener.connectEnd(mCall, new InetSocketAddress(IPV4, 443), Proxy.NO_PROXY, Protocol.HTTP_1_1);

        List<VideoStart.Stage> stages = Connections.take(500, 3_000);

        assertEquals(1, stages.size());
        assertEquals(VideoStarts.STAGE_CONNECT, stages.get(0).name);
        assertEquals("DNS 2 ms, IPv4 68 ms, http/1.1", stages.get(0).detail);
        assertEquals(1_000, stages.get(0).startMs);
        assertEquals(1_070, stages.get(0).endMs);
    }

    /**
     * VP4G86E9: each first segment of a SABR start connected again, 100-350 ms, and nothing said to which server or why. The
     * server's number is the video's (as its attempts' hosts), and the protocol tells HTTP/1.1, which loses a connection whose
     * response was closed before its end, from HTTP/2.
     */
    @Test
    public void aConnectSaysItsServerAndProtocol() throws IOException {
        PlayerFailureLog.useForTesting(mFolder.getRoot());
        FormatAttempts.resetForTesting();
        FormatAttempts.onNewVideo(RuntimeEnvironment.getApplication(), "oL_NZPsIf0c");
        FormatAttempts.onRequest("rr2---sn-other.googlevideo.com");
        FormatAttempts.onRequest(HOST);

        try {
            Call call = new OkHttpClient().newCall(new Request.Builder().url("https://" + HOST + "/videoplayback?sabr=1").build());
            EventListener listener = Connections.LISTENER.create(call);
            mNow.set(1_000);
            listener.connectStart(call, new InetSocketAddress(IPV4, 443), Proxy.NO_PROXY);
            mNow.set(1_100);
            listener.connectEnd(call, new InetSocketAddress(IPV4, 443), Proxy.NO_PROXY, Protocol.HTTP_2);

            assertEquals("server 2, IPv4 100 ms, h2", Connections.take(500, 3_000).get(0).detail);
        } finally {
            FormatAttempts.resetForTesting();
            PlayerFailureLog.useForTesting(null);
        }
    }

    /**
     * A server the video's segments weren't asked of (e.g. the connection is the first request's, made before it's told): no number
     */
    @Test
    public void aConnectToAServerNotAskedHasNoNumber() throws IOException {
        EventListener listener = Connections.LISTENER.create(mCall);
        mNow.set(1_000);
        listener.connectStart(mCall, new InetSocketAddress(IPV4, 443), Proxy.NO_PROXY);
        mNow.set(1_050);
        listener.connectEnd(mCall, new InetSocketAddress(IPV4, 443), Proxy.NO_PROXY, Protocol.HTTP_2);

        assertEquals("IPv4 50 ms, h2", Connections.take(500, 3_000).get(0).detail);
    }

    /**
     * 5070 ms on a Shield: an address that doesn't answer costs the connect timeout, then the next one is tried. Told apart from a
     * slow lookup.
     */
    @Test
    public void aDeadAddressIsTimedApartFromTheLookup() throws IOException {
        EventListener listener = Connections.LISTENER.create(mCall);
        mNow.set(1_000);
        listener.dnsStart(mCall, HOST);
        mNow.set(1_003);
        listener.dnsEnd(mCall, HOST, Arrays.asList(IPV4, IPV6));
        listener.connectStart(mCall, new InetSocketAddress(IPV4, 443), Proxy.NO_PROXY);
        mNow.set(6_003);
        listener.connectFailed(mCall, new InetSocketAddress(IPV4, 443), Proxy.NO_PROXY, null,
                new SocketTimeoutException("connect timed out"));
        listener.connectStart(mCall, new InetSocketAddress(IPV6, 443), Proxy.NO_PROXY);
        mNow.set(6_073);
        listener.connectEnd(mCall, new InetSocketAddress(IPV6, 443), Proxy.NO_PROXY, Protocol.HTTP_1_1);

        List<VideoStart.Stage> stages = Connections.take(500, 7_000);

        assertEquals(1, stages.size());
        assertEquals("DNS 3 ms, IPv4 timed out after 5000 ms, IPv6 70 ms, http/1.1", stages.get(0).detail);
        assertEquals(1_000, stages.get(0).startMs);
        assertEquals(6_073, stages.get(0).endMs);
    }

    @Test
    public void aSlowLookupIsTheDns() throws IOException {
        EventListener listener = Connections.LISTENER.create(mCall);
        mNow.set(1_000);
        listener.dnsStart(mCall, HOST);
        mNow.set(6_003);
        listener.dnsEnd(mCall, HOST, Collections.singletonList(IPV4));
        listener.connectStart(mCall, new InetSocketAddress(IPV4, 443), Proxy.NO_PROXY);
        mNow.set(6_070);
        listener.connectEnd(mCall, new InetSocketAddress(IPV4, 443), Proxy.NO_PROXY, Protocol.HTTP_1_1);

        assertEquals("DNS 5003 ms, IPv4 67 ms, http/1.1", Connections.take(500, 7_000).get(0).detail);
    }

    /**
     * Looked at while it connects: what it's at
     */
    @Test
    public void oneGoingSaysWhatItsAt() throws IOException {
        EventListener listener = Connections.LISTENER.create(mCall);
        mNow.set(1_000);
        listener.dnsStart(mCall, HOST);

        assertEquals("DNS, " + VideoStarts.DETAIL_GOING, Connections.take(500, 3_000).get(0).detail);

        listener.dnsEnd(mCall, HOST, Collections.singletonList(IPV4));
        listener.connectStart(mCall, new InetSocketAddress(IPV4, 443), Proxy.NO_PROXY);

        assertEquals("DNS 0 ms, IPv4, " + VideoStarts.DETAIL_GOING, Connections.take(500, 3_000).get(0).detail);
    }

    /**
     * Every address failed: the call fails, and the connect ends with them
     */
    @Test
    public void aConnectThatFailsEndsWithItsAttempts() throws IOException {
        EventListener listener = Connections.LISTENER.create(mCall);
        mNow.set(1_000);
        listener.dnsStart(mCall, HOST);
        listener.dnsEnd(mCall, HOST, Collections.singletonList(IPV4));
        listener.connectStart(mCall, new InetSocketAddress(IPV4, 443), Proxy.NO_PROXY);
        mNow.set(1_020);
        listener.connectFailed(mCall, new InetSocketAddress(IPV4, 443), Proxy.NO_PROXY, null,
                new ConnectException("Connection refused"));
        listener.callFailed(mCall, new ConnectException("Connection refused"));

        List<VideoStart.Stage> stages = Connections.take(500, 3_000);

        assertEquals("DNS 0 ms, IPv4 refused after 20 ms", stages.get(0).detail);
        assertEquals(1_020, stages.get(0).endMs);
    }

    @Test
    public void onlyTheOnesOfTheStartCutToIt() {
        Connections.add(1_000, 2_000);
        Connections.add(4_000, 6_000);
        Connections.add(9_000, 12_000);

        List<VideoStart.Stage> stages = Connections.take(5_000, 10_000);

        assertEquals(2, stages.size());
        assertEquals(VideoStarts.STAGE_CONNECT, stages.get(0).name);
        assertEquals(1, stages.get(0).depth);
        assertEquals(5_000, stages.get(0).startMs);
        assertEquals(6_000, stages.get(0).endMs);
        assertEquals(9_000, stages.get(1).startMs);
        assertEquals(10_000, stages.get(1).endMs);
    }

    @Test
    public void onlyTheLastOnesAreKept() {
        for (int i = 0; i < Connections.MAX_CONNECTIONS + 1; i++) {
            Connections.add(i * 10L, i * 10L + 5);
        }

        assertEquals(Connections.MAX_CONNECTIONS, Connections.take(0, 1_000).size());
        assertTrue(Connections.take(0, 5).isEmpty());
    }

    private static InetAddress address(byte[] bytes) {
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Its open takes that long on the test's clock, then throws the error if any
     */
    private final class FakeDataSource implements DataSource {
        private final long mOpenMs;
        @Nullable
        private final IOException mError;

        FakeDataSource(long openMs, @Nullable IOException error) {
            mOpenMs = openMs;
            mError = error;
        }

        @Override
        public void addTransferListener(TransferListener transferListener) {
        }

        @Override
        public long open(DataSpec dataSpec) throws IOException {
            mNow.addAndGet(mOpenMs);

            if (mError != null) {
                throw mError;
            }

            return C.LENGTH_UNSET;
        }

        @Override
        public int read(byte[] buffer, int offset, int readLength) {
            return C.RESULT_END_OF_INPUT;
        }

        @Override
        public Uri getUri() {
            return null;
        }

        @Override
        public void close() {
        }
    }
}
