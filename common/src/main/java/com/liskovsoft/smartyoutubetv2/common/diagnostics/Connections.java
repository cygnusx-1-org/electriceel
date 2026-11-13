package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.MimeTypes;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Proxy;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Protocol;

/**
 * The connections the player's OkHttp client opened, from the name's lookup to the connection being ready (with the lookup's time and
 * each address tried in their detail), and its requests that waited long for their answer, from being sent to its first byte. A
 * segment that took long with no response in its time was slow to come, one with a long response waited for the server. The other
 * engines' requests are timed whole, from being made to their answer's headers, connect included (see timeOpenings). Each names the
 * load it's of, e.g. the video's init chunk (see describeLoad). Any thread: OkHttp's and the loaders'.
 */
public final class Connections {
    /**
     * The last ones: a start only needs the ones since it was prepared
     */
    static final int MAX_CONNECTIONS = 8;
    /**
     * A start has a request for each first segment, and more while it buffers
     */
    static final int MAX_RESPONSES = 16;
    /**
     * Shorter, a response says nothing more than none: the server answered at once. Each request of a start's buffering shown, its
     * segments' rows went past what the graph shows (see StartGraphView#MAX_ROWS).
     */
    static final long MIN_RESPONSE_MS = 200;
    /**
     * Of an address given up on: another connected first, or the call was cancelled
     */
    static final String FAILURE_CANCELLED = "cancelled";
    /**
     * Of a load (see describeLoad): the format's header, after its track, e.g. "video init chunk"
     */
    static final String LOAD_INIT_CHUNK = "init chunk";
    /**
     * Of a load (see describeLoad): a segment of the media, after its track, e.g. "audio chunk"
     */
    static final String LOAD_CHUNK = "chunk";
    /**
     * Of a load (see describeLoad): a DASH manifest or an HLS playlist
     */
    static final String LOAD_MANIFEST = "manifest";
    /**
     * For the client of the player only: the API's connections aren't the video's. Each call is made on the thread of the load it's
     * of (e.g. OkHttpDataSource#open), and its listener with it: that's when the load is known.
     */
    public static final EventListener.Factory LISTENER = call -> new Listener(describeLoad(Loader.getCurrentLoadable()));
    private static final ArrayDeque<Connection> sConnections = new ArrayDeque<>();
    private static final ArrayDeque<TimedRequest> sResponses = new ArrayDeque<>();
    private static final ArrayDeque<TimedRequest> sOpenings = new ArrayDeque<>();
    @VisibleForTesting
    static VideoStarts.Clock sClock = SystemClock::elapsedRealtime;

    private Connections() {
    }

    /**
     * A new connection: its name's lookup, then each address tried till one connects. OkHttp tries the next when one fails (e.g. after
     * the connect timeout), and 250 ms into one that hasn't connected yet. Its fields change on OkHttp's threads (an attempt has its
     * own), and are read by take: under the class's lock.
     */
    @VisibleForTesting
    static final class Connection {
        final long startMs;
        /**
         * Of the call that opened it (see describeLoad), null for one not known
         */
        @Nullable
        final String load;
        long endMs = -1;
        boolean isDnsStarted;
        /**
         * The name's lookup ended, -1 for none or one going on
         */
        long dnsEndMs = -1;
        /**
         * The addresses tried, in the order they were: e.g. "IPv4 timed out after 5000 ms", "IPv6 70 ms". They can overlap: OkHttp 5
         * starts the next 250 ms into one that hasn't connected yet (fast fallback).
         */
        final List<Attempt> attempts = new ArrayList<>(2);
        /**
         * The server's name, only to number it as the video's (see FormatAttempts#getHostNumber): it isn't recorded
         */
        @Nullable
        String host;
        /**
         * What it agreed on with the server, once connected: e.g. HTTP/1.1, where a response closed before its end loses the connection
         */
        @Nullable
        Protocol protocol;

        Connection(long startMs, @Nullable String load) {
            this.startMs = startMs;
            this.load = load;
        }

        /**
         * E.g. "video init chunk, server 1, DNS 2 ms, IPv4 70 ms, h2", "audio chunk, DNS 5003 ms, IPv4 68 ms, http/1.1", "DNS 1 ms,
         * IPv4 timed out after 5000 ms, IPv6 70 ms" (its load not known). With "still going" for one that hasn't ended, after what
         * it's at: "DNS, still going", "DNS 2 ms, IPv4, still going". Null for nothing known.
         *
         * @param hostNumber of its server among the video's (see FormatAttempts#getHostNumber), -1 for one not known
         */
        @Nullable
        String describe(boolean isGoing, int hostNumber) {
            List<String> parts = new ArrayList<>(attempts.size() + 5);

            if (load != null) {
                parts.add(load);
            }

            if (hostNumber > 0) {
                parts.add("server " + hostNumber);
            }

            // Its load and its server say nothing of the connection itself
            int named = parts.size();

            if (dnsEndMs >= 0) {
                parts.add("DNS " + (dnsEndMs - startMs) + " ms");
            } else if (isDnsStarted) {
                // Ended in its lookup: the name didn't resolve (OkHttp tells no end of a lookup that fails)
                parts.add(isGoing ? "DNS" : "DNS failed after " + (endMs - startMs) + " ms");
            }

            for (Attempt attempt : attempts) {
                parts.add(attempt.describe());
            }

            if (protocol != null) {
                parts.add(protocol.toString());
            }

            if (isGoing) {
                parts.add(VideoStarts.DETAIL_GOING);
            }

            return parts.size() > named ? TextUtils.join(", ", parts) : load;
        }
    }

    /**
     * A request that waited for its answer: a response (from being sent to its first byte) or an opening (from being made to its
     * answer's headers). Its end changes under the class's lock.
     */
    @VisibleForTesting
    static final class TimedRequest {
        final long startMs;
        /**
         * -1 while it waits
         */
        long endMs;
        /**
         * Of the load it's of (see describeLoad), null for one not known
         */
        @Nullable
        final String load;

        TimedRequest(long startMs, long endMs, @Nullable String load) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.load = load;
        }
    }

    /**
     * An address tried. Its end changes under the class's lock.
     */
    static final class Attempt {
        final InetSocketAddress address;
        final String family;
        final long startMs;
        long endMs = -1;
        /**
         * Why it didn't connect, e.g. "timed out", null for one that did or is going
         */
        @Nullable
        String failure;

        Attempt(InetSocketAddress address, String family, long startMs) {
            this.address = address;
            this.family = family;
            this.startMs = startMs;
        }

        /**
         * E.g. "IPv4 70 ms", "IPv6 timed out after 5000 ms", just "IPv4" for one going
         */
        String describe() {
            if (endMs < 0) {
                return family;
            }

            long durationMs = endMs - startMs;

            return failure != null ? family + " " + failure + " after " + durationMs + " ms" : family + " " + durationMs + " ms";
        }
    }

    /**
     * Of a call. Its dead ends count: they're the time it took to connect.
     */
    private static final class Listener extends EventListener {
        /**
         * Of the call (see describeLoad), null for one not known
         */
        @Nullable
        private final String mLoad;
        private volatile Connection mConnection;
        private volatile TimedRequest mResponse;

        Listener(@Nullable String load) {
            mLoad = load;
        }

        private void onStart(Call call) {
            if (mConnection == null) {
                mConnection = add(sClock.now(), -1, mLoad);
                setHost(mConnection, call.request().url().host());
            }
        }

        private void onEnd() {
            Connection connection = mConnection;

            if (connection != null) {
                end(connection);
                mConnection = null;
            }
        }

        private void onResponseEnd() {
            TimedRequest response = mResponse;

            if (response != null) {
                end(response);
                mResponse = null;
            }
        }

        @Override
        public void dnsStart(@NonNull Call call, @NonNull String domainName) {
            onStart(call);
            startDns(mConnection);
        }

        @Override
        public void dnsEnd(@NonNull Call call, @NonNull String domainName, @NonNull List<InetAddress> inetAddressList) {
            Connection connection = mConnection;

            if (connection != null) {
                endDns(connection);
            }
        }

        @Override
        public void connectStart(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull Proxy proxy) {
            onStart(call);
            startAttempt(mConnection, inetSocketAddress);
        }

        /**
         * OkHttp tries the next address, if there's one
         */
        @Override
        public void connectFailed(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull Proxy proxy,
                                  @Nullable Protocol protocol, @NonNull IOException ioe) {
            Connection connection = mConnection;

            if (connection != null) {
                endAttempt(connection, inetSocketAddress, describeFailure(ioe));
            }
        }

        @Override
        public void connectEnd(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull Proxy proxy, Protocol protocol) {
            Connection connection = mConnection;

            if (connection != null) {
                setProtocol(connection, protocol);
                endAttempt(connection, inetSocketAddress, null);
            }

            onEnd();
        }

        /**
         * A follow-up of the call (e.g. a redirect) ends the response before: its body isn't read
         */
        @Override
        public void requestHeadersStart(@NonNull Call call) {
            onResponseEnd();
            mResponse = addResponse(sClock.now(), -1, mLoad);
        }

        /**
         * Told after the first read of the body returned (OkHttp 4): its first byte came
         */
        @Override
        public void responseBodyStart(@NonNull Call call) {
            onResponseEnd();
        }

        @Override
        public void requestFailed(@NonNull Call call, @NonNull IOException ioe) {
            onResponseEnd();
        }

        @Override
        public void responseFailed(@NonNull Call call, @NonNull IOException ioe) {
            onResponseEnd();
        }

        @Override
        public void callEnd(@NonNull Call call) {
            onEnd();
            onResponseEnd();
        }

        @Override
        public void callFailed(@NonNull Call call, @NonNull IOException ioe) {
            onEnd();
            onResponseEnd();
        }
    }

    @VisibleForTesting
    static synchronized Connection add(long startMs, long endMs, @Nullable String load) {
        Connection connection = new Connection(startMs, load);
        connection.endMs = endMs;
        sConnections.addLast(connection);

        if (sConnections.size() > MAX_CONNECTIONS) {
            sConnections.removeFirst();
        }

        return connection;
    }

    @VisibleForTesting
    static synchronized TimedRequest addResponse(long sentMs, long firstByteMs, @Nullable String load) {
        TimedRequest response = new TimedRequest(sentMs, firstByteMs, load);
        sResponses.addLast(response);

        if (sResponses.size() > MAX_RESPONSES) {
            sResponses.removeFirst();
        }

        return response;
    }

    /**
     * For the player's engines with no OkHttp client (the default one, Cronet): their connections and responses aren't told apart, a
     * request is timed whole, its open, from being made to its answer's headers. One that fails to open (e.g. a connect timeout) or is
     * cancelled ends then.
     */
    public static DataSource timeOpenings(DataSource dataSource) {
        return new OpeningTimer(dataSource);
    }

    private static final class OpeningTimer implements DataSource {
        private final DataSource mDataSource;

        OpeningTimer(DataSource dataSource) {
            mDataSource = dataSource;
        }

        @Override
        public void addTransferListener(TransferListener transferListener) {
            mDataSource.addTransferListener(transferListener);
        }

        @Override
        public long open(DataSpec dataSpec) throws IOException {
            // Made on the thread of the load it's of
            TimedRequest opening = isNetwork(dataSpec) ? addOpening(sClock.now(), -1, describeLoad(Loader.getCurrentLoadable())) : null;

            try {
                return mDataSource.open(dataSpec);
            } finally {
                if (opening != null) {
                    end(opening);
                }
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int readLength) throws IOException {
            return mDataSource.read(buffer, offset, readLength);
        }

        @Nullable
        @Override
        public Uri getUri() {
            return mDataSource.getUri();
        }

        @Override
        public Map<String, List<String>> getResponseHeaders() {
            return mDataSource.getResponseHeaders();
        }

        @Override
        public void close() throws IOException {
            mDataSource.close();
        }

        /**
         * A file or an asset isn't a request
         */
        private static boolean isNetwork(DataSpec dataSpec) {
            String scheme = dataSpec.uri.getScheme();

            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        }
    }

    @VisibleForTesting
    static synchronized TimedRequest addOpening(long madeMs, long headersMs, @Nullable String load) {
        TimedRequest opening = new TimedRequest(madeMs, headersMs, load);
        sOpenings.addLast(opening);

        if (sOpenings.size() > MAX_RESPONSES) {
            sOpenings.removeFirst();
        }

        return opening;
    }

    private static synchronized void end(TimedRequest request) {
        request.endMs = sClock.now();
    }

    private static synchronized void end(Connection connection) {
        connection.endMs = sClock.now();
    }

    private static synchronized void setHost(Connection connection, String host) {
        connection.host = host;
    }

    private static synchronized void setProtocol(Connection connection, @Nullable Protocol protocol) {
        connection.protocol = protocol;
    }

    private static synchronized void startDns(Connection connection) {
        connection.isDnsStarted = true;
    }

    private static synchronized void endDns(Connection connection) {
        connection.dnsEndMs = sClock.now();
    }

    private static synchronized void startAttempt(Connection connection, InetSocketAddress address) {
        connection.attempts.add(new Attempt(address, familyOf(address), sClock.now()));
    }

    /**
     * Ends the last attempt going to the address. One that connected ends the others going: OkHttp cancels them, and may tell their
     * end only after this one's.
     *
     * @param failure why it didn't connect, e.g. "timed out", null for one that did
     */
    private static synchronized void endAttempt(Connection connection, InetSocketAddress address, @Nullable String failure) {
        long nowMs = sClock.now();

        for (int i = connection.attempts.size() - 1; i >= 0; i--) {
            Attempt attempt = connection.attempts.get(i);

            if (attempt.endMs < 0 && attempt.address.equals(address)) {
                attempt.endMs = nowMs;
                attempt.failure = failure;
                break;
            }
        }

        if (failure != null) {
            return;
        }

        for (Attempt attempt : connection.attempts) {
            if (attempt.endMs < 0) {
                attempt.endMs = nowMs;
                attempt.failure = FAILURE_CANCELLED;
            }
        }
    }

    /**
     * IPv4 or IPv6, "address" for one not resolved (e.g. behind a proxy)
     */
    private static String familyOf(InetSocketAddress socketAddress) {
        InetAddress address = socketAddress.getAddress();

        return address instanceof Inet6Address ? "IPv6" : address instanceof Inet4Address ? "IPv4" : "address";
    }

    /**
     * E.g. "timed out": the connect timeout (5 s, see OkHttpCommons) on an address that doesn't answer
     */
    private static String describeFailure(IOException ioe) {
        if (ioe instanceof SocketTimeoutException) {
            return "timed out";
        }

        if (ioe instanceof NoRouteToHostException) {
            return "unreachable";
        }

        String message = ioe.getMessage();

        if (ioe instanceof ConnectException) {
            return message != null && message.contains("ENETUNREACH") ? "unreachable" : "refused";
        }

        // OkHttp closes the socket of an attempt it cancels ("Socket closed"), or stops one not started yet ("canceled"). Not a
        // handshake's "Connection closed by peer": the server ended that one.
        if ("canceled".equalsIgnoreCase(message)
                || ioe instanceof SocketException && ("Socket closed".equals(message) || "Socket is closed".equals(message))) {
            return FAILURE_CANCELLED;
        }

        return "failed";
    }

    /**
     * The ones between the times, cut to them, as stages of a start's buffering (in the clock's time). One still connecting is
     * what a stuck start waits for, so it's there too.
     */
    static synchronized List<VideoStart.Stage> take(long fromMs, long toMs) {
        List<VideoStart.Stage> result = new ArrayList<>();

        for (Connection connection : sConnections) {
            long startMs = Math.max(connection.startMs, fromMs);
            boolean isGoing = connection.endMs < 0;
            long endMs = isGoing ? toMs : Math.min(connection.endMs, toMs);

            if (endMs - startMs >= 1) {
                result.add(new VideoStart.Stage(VideoStarts.STAGE_CONNECT,
                        connection.describe(isGoing, FormatAttempts.getHostNumber(connection.host)), 1, startMs, endMs));
            }
        }

        addRequests(result, sResponses, VideoStarts.STAGE_RESPONSE, fromMs, toMs);
        addRequests(result, sOpenings, VideoStarts.STAGE_OPENING, fromMs, toMs);

        return result;
    }

    /**
     * The ones that waited long (see MIN_RESPONSE_MS)
     */
    private static void addRequests(List<VideoStart.Stage> result, ArrayDeque<TimedRequest> requests, String name, long fromMs,
                                    long toMs) {
        for (TimedRequest request : requests) {
            // Made before, it's of the source before (e.g. the video before's)
            if (request.startMs < fromMs) {
                continue;
            }

            boolean isGoing = request.endMs < 0;
            long endMs = isGoing ? toMs : Math.min(request.endMs, toMs);

            if (endMs - request.startMs >= MIN_RESPONSE_MS) {
                result.add(new VideoStart.Stage(name, describeRequest(request.load, isGoing), 1, request.startMs, endMs));
            }
        }
    }

    /**
     * E.g. "audio init chunk", "audio init chunk, still going", "still going" (its load not known). Null for nothing known.
     */
    @Nullable
    private static String describeRequest(@Nullable String load, boolean isGoing) {
        if (!isGoing) {
            return load;
        }

        return load != null ? load + ", " + VideoStarts.DETAIL_GOING : VideoStarts.DETAIL_GOING;
    }

    /**
     * Which of the player's loads a request is of, e.g. "video init chunk", "audio chunk", "subtitle chunk", "manifest": told by its
     * loader's thread, which it's made on (see Loader#getCurrentLoadable). Null for a request made elsewhere (e.g. on a prefetch's
     * thread) or of another load (e.g. a progressive stream's, whose one request is all of it).
     */
    @VisibleForTesting
    @Nullable
    static String describeLoad(@Nullable Loader.Loadable loadable) {
        if (loadable instanceof Chunk) {
            Chunk chunk = (Chunk) loadable;
            String kind = chunk.type == C.DATA_TYPE_MEDIA_INITIALIZATION ? LOAD_INIT_CHUNK : LOAD_CHUNK;
            String track = trackOf(chunk.trackFormat);

            return track != null ? track + " " + kind : kind;
        }

        return loadable instanceof ParsingLoadable ? LOAD_MANIFEST : null;
    }

    /**
     * "video", "audio", "subtitle", null for one not known (e.g. a muxed stream's)
     */
    @Nullable
    private static String trackOf(@Nullable Format format) {
        if (format == null) {
            return null;
        }

        int trackType = MimeTypes.getTrackType(format.sampleMimeType);

        if (trackType == C.TRACK_TYPE_UNKNOWN) {
            trackType = MimeTypes.getTrackType(format.containerMimeType);
        }

        switch (trackType) {
            case C.TRACK_TYPE_VIDEO:
                return "video";
            case C.TRACK_TYPE_AUDIO:
                return "audio";
            case C.TRACK_TYPE_TEXT:
                return "subtitle";
            default:
                return null;
        }
    }

    @VisibleForTesting
    static synchronized void resetForTesting() {
        sConnections.clear();
        sResponses.clear();
        sOpenings.clear();
    }
}
