package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final Format VIDEO = Format.createContainerFormat("248", "video/webm", "video/x-vnd.on2.vp9", "vp9", 17_668_000,
            0, null);
    private static final Format AUDIO = Format.createContainerFormat("251", "audio/webm", "audio/opus", "opus", 128_000, 0, "en");
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
        Connections.addResponse(100, 900, null);
        Connections.addResponse(400, -1, null);
        Connections.addResponse(600, 900, null);

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
            Connections.addResponse(i * 1_000L, i * 1_000L + 500, null);
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
        Connections.add(1_000, 2_000, null);
        Connections.add(4_000, 6_000, null);
        Connections.add(9_000, 12_000, null);

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
            Connections.add(i * 10L, i * 10L + 5, null);
        }

        assertEquals(Connections.MAX_CONNECTIONS, Connections.take(0, 1_000).size());
        assertTrue(Connections.take(0, 5).isEmpty());
    }

    /**
     * MCY8QBR1: its rows said a start connected again and that a request waited 1.4 s for the server, not for which load: the audio
     * init's wait was told from the video init's only by when each ended. A call is made on its loader's thread, which tells it.
     */
    @Test
    public void aCallNamesTheLoadItIsMadeIn() throws Exception {
        load(new TestChunk(C.DATA_TYPE_MEDIA_INITIALIZATION, AUDIO, () -> {
            EventListener listener = Connections.LISTENER.create(mCall);
            mNow.set(1_000);
            listener.connectStart(mCall, new InetSocketAddress(IPV4, 443), Proxy.NO_PROXY);
            mNow.set(1_050);
            listener.connectEnd(mCall, new InetSocketAddress(IPV4, 443), Proxy.NO_PROXY, Protocol.HTTP_1_1);
            listener.requestHeadersStart(mCall);
            mNow.set(2_450);
            listener.responseBodyStart(mCall);
            listener.callEnd(mCall);
        }));

        List<VideoStart.Stage> stages = Connections.take(500, 3_000);

        assertEquals(2, stages.size());
        assertEquals(VideoStarts.STAGE_CONNECT, stages.get(0).name);
        assertEquals("audio init chunk, IPv4 50 ms, http/1.1", stages.get(0).detail);
        assertEquals(VideoStarts.STAGE_RESPONSE, stages.get(1).name);
        assertEquals("audio init chunk", stages.get(1).detail);
        assertEquals(1_050, stages.get(1).startMs);
        assertEquals(2_450, stages.get(1).endMs);
    }

    /**
     * The other engines' requests are made on their loader's thread too
     */
    @Test
    public void anOpeningNamesItsLoad() throws Exception {
        DataSource source = Connections.timeOpenings(new FakeDataSource(1_400, null));
        mNow.set(1_000);
        load(new TestChunk(C.DATA_TYPE_MEDIA, VIDEO, () -> source.open(new DataSpec(Uri.parse("https://example.com/videoplayback")))));

        List<VideoStart.Stage> stages = Connections.take(500, 3_000);

        assertEquals(1, stages.size());
        assertEquals(VideoStarts.STAGE_OPENING, stages.get(0).name);
        assertEquals("video chunk", stages.get(0).detail);
    }

    /**
     * Its load, then what it's waiting for
     */
    @Test
    public void oneWaitingSaysItsLoadThenStillGoing() {
        Connections.addResponse(1_000, -1, "audio init chunk");

        assertEquals("audio init chunk, " + VideoStarts.DETAIL_GOING, Connections.take(500, 3_000).get(0).detail);
    }

    @Test
    public void aLoadIsNamedByItsTrackAndKind() {
        assertEquals("video init chunk", Connections.describeLoad(new TestChunk(C.DATA_TYPE_MEDIA_INITIALIZATION, VIDEO, null)));
        assertEquals("audio chunk", Connections.describeLoad(new TestChunk(C.DATA_TYPE_MEDIA, AUDIO, null)));
        // Told by the container when the sample's type isn't known
        assertEquals("audio chunk", Connections.describeLoad(new TestChunk(C.DATA_TYPE_MEDIA,
                Format.createContainerFormat("140", "audio/mp4", null, null, Format.NO_VALUE, 0, null), null)));
        assertEquals("subtitle chunk", Connections.describeLoad(new TestChunk(C.DATA_TYPE_MEDIA,
                Format.createContainerFormat("en", null, "text/vtt", null, Format.NO_VALUE, 0, "en"), null)));
        // A muxed one has no track of its own
        assertEquals("chunk", Connections.describeLoad(new TestChunk(C.DATA_TYPE_MEDIA,
                Format.createContainerFormat("0", "application/x-mpegURL", null, null, Format.NO_VALUE, 0, null), null)));
        assertEquals("manifest", Connections.describeLoad(new ParsingLoadable<>(new FakeDataSource(0, null),
                Uri.parse("https://example.com/manifest.mpd"), C.DATA_TYPE_MANIFEST, (uri, inputStream) -> null)));
        // Not a loader's thread
        assertNull(Connections.describeLoad(null));
    }

    /**
     * Loaded by a real Loader, as the player's segments are: on the loader's own thread
     */
    private static void load(TestChunk chunk) throws Exception {
        Loader loader = new Loader("ConnectionsTest");

        try {
            loader.startLoading(chunk, new Loader.Callback<TestChunk>() {
                @Override
                public void onLoadCompleted(TestChunk loadable, long elapsedRealtimeMs, long loadDurationMs) {
                }

                @Override
                public void onLoadCanceled(TestChunk loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {
                }

                @Override
                public Loader.LoadErrorAction onLoadError(TestChunk loadable, long elapsedRealtimeMs, long loadDurationMs,
                                                          IOException error, int errorCount) {
                    return Loader.DONT_RETRY;
                }
            }, 0);

            chunk.await();
        } finally {
            loader.release();
        }
    }

    private interface LoadBody {
        void run() throws IOException;
    }

    /**
     * A segment whose load runs the body (none: never loaded), and tells when it's done
     */
    private static final class TestChunk extends Chunk {
        @Nullable
        private final LoadBody mBody;
        private final CountDownLatch mDone = new CountDownLatch(1);
        private final AtomicReference<Throwable> mError = new AtomicReference<>();

        TestChunk(int type, Format format, @Nullable LoadBody body) {
            super(new UnopenedDataSource(), new DataSpec(Uri.parse("https://example.com/videoplayback")), type, format,
                    C.SELECTION_REASON_UNKNOWN, null, 0, 5_000_000);
            mBody = body;
        }

        @Override
        public void cancelLoad() {
        }

        @Override
        public void load() {
            try {
                if (mBody != null) {
                    mBody.run();
                }
            } catch (Throwable e) {
                mError.set(e);
            } finally {
                mDone.countDown();
            }
        }

        void await() throws Exception {
            assertTrue("not loaded", mDone.await(5, TimeUnit.SECONDS));

            if (mError.get() != null) {
                throw new AssertionError(mError.get());
            }
        }
    }

    /**
     * A chunk's own source, never opened here: the body makes the requests
     */
    private static final class UnopenedDataSource implements DataSource {
        @Override
        public void addTransferListener(TransferListener transferListener) {
        }

        @Override
        public long open(DataSpec dataSpec) {
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
