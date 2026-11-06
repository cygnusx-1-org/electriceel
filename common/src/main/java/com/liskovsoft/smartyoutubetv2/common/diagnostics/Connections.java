package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Proxy;
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
 * engines' requests are timed whole, from being made to their answer's headers, connect included (see timeOpenings). Any thread:
 * OkHttp's and the loaders'.
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
     * For the client of the player only: the API's connections aren't the video's
     */
    public static final EventListener.Factory LISTENER = call -> new Listener();
    private static final ArrayDeque<Connection> sConnections = new ArrayDeque<>();
    private static final ArrayDeque<long[]> sResponses = new ArrayDeque<>(); // {sent, first byte}, first byte -1 waiting for it
    private static final ArrayDeque<long[]> sOpenings = new ArrayDeque<>(); // {made, headers}, headers -1 waiting for them
    @VisibleForTesting
    static VideoStarts.Clock sClock = SystemClock::elapsedRealtime;

    private Connections() {
    }

    /**
     * A new connection: its name's lookup, then each address tried till one connects (OkHttp tries the next when one fails, e.g. after
     * the connect timeout). Its fields change on the call's thread, and are read by take: under the class's lock.
     */
    @VisibleForTesting
    static final class Connection {
        final long startMs;
        long endMs = -1;
        boolean isDnsStarted;
        /**
         * The name's lookup ended, -1 for none or one going on
         */
        long dnsEndMs = -1;
        /**
         * The addresses tried that didn't connect, then the one that did: e.g. "IPv4 timed out after 5000 ms", "IPv6 70 ms"
         */
        final List<String> attempts = new ArrayList<>(2);
        /**
         * Of the address being tried, null for none
         */
        @Nullable
        String attemptFamily;
        long attemptStartMs = -1;

        Connection(long startMs) {
            this.startMs = startMs;
        }

        /**
         * E.g. "DNS 2 ms, IPv4 70 ms", "DNS 5003 ms, IPv4 68 ms", "DNS 1 ms, IPv4 timed out after 5000 ms, IPv6 70 ms". With
         * "still going" for one that hasn't ended, after what it's at: "DNS, still going", "DNS 2 ms, IPv4, still going". Null for
         * nothing known.
         */
        @Nullable
        String describe(boolean isGoing) {
            List<String> parts = new ArrayList<>(attempts.size() + 2);

            if (dnsEndMs >= 0) {
                parts.add("DNS " + (dnsEndMs - startMs) + " ms");
            } else if (isDnsStarted) {
                // Ended in its lookup: the name didn't resolve (OkHttp tells no end of a lookup that fails)
                parts.add(isGoing ? "DNS" : "DNS failed after " + (endMs - startMs) + " ms");
            }

            parts.addAll(attempts);

            if (attemptFamily != null) {
                parts.add(attemptFamily);
            }

            if (isGoing) {
                parts.add(VideoStarts.DETAIL_GOING);
            }

            return parts.isEmpty() ? null : TextUtils.join(", ", parts);
        }
    }

    /**
     * Of a call. Its dead ends count: they're the time it took to connect.
     */
    private static final class Listener extends EventListener {
        private volatile Connection mConnection;
        private volatile long[] mResponse;

        private void onStart() {
            if (mConnection == null) {
                mConnection = add(sClock.now(), -1);
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
            long[] response = mResponse;

            if (response != null) {
                end(response);
                mResponse = null;
            }
        }

        @Override
        public void dnsStart(@NonNull Call call, @NonNull String domainName) {
            onStart();
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
            onStart();
            startAttempt(mConnection, familyOf(inetSocketAddress));
        }

        /**
         * OkHttp tries the next address, if there's one
         */
        @Override
        public void connectFailed(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull Proxy proxy,
                                  @Nullable Protocol protocol, @NonNull IOException ioe) {
            Connection connection = mConnection;

            if (connection != null) {
                endAttempt(connection, describeFailure(ioe));
            }
        }

        @Override
        public void connectEnd(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull Proxy proxy, Protocol protocol) {
            Connection connection = mConnection;

            if (connection != null) {
                endAttempt(connection, null);
            }

            onEnd();
        }

        /**
         * A follow-up of the call (e.g. a redirect) ends the response before: its body isn't read
         */
        @Override
        public void requestHeadersStart(@NonNull Call call) {
            onResponseEnd();
            mResponse = addResponse(sClock.now(), -1);
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
    static synchronized Connection add(long startMs, long endMs) {
        Connection connection = new Connection(startMs);
        connection.endMs = endMs;
        sConnections.addLast(connection);

        if (sConnections.size() > MAX_CONNECTIONS) {
            sConnections.removeFirst();
        }

        return connection;
    }

    @VisibleForTesting
    static synchronized long[] addResponse(long sentMs, long firstByteMs) {
        long[] response = {sentMs, firstByteMs};
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
            long[] opening = isNetwork(dataSpec) ? addOpening(sClock.now(), -1) : null;

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
    static synchronized long[] addOpening(long madeMs, long headersMs) {
        long[] opening = {madeMs, headersMs};
        sOpenings.addLast(opening);

        if (sOpenings.size() > MAX_RESPONSES) {
            sOpenings.removeFirst();
        }

        return opening;
    }

    private static synchronized void end(long[] entry) {
        entry[1] = sClock.now();
    }

    private static synchronized void end(Connection connection) {
        connection.endMs = sClock.now();
    }

    private static synchronized void startDns(Connection connection) {
        connection.isDnsStarted = true;
    }

    private static synchronized void endDns(Connection connection) {
        connection.dnsEndMs = sClock.now();
    }

    private static synchronized void startAttempt(Connection connection, String family) {
        connection.attemptFamily = family;
        connection.attemptStartMs = sClock.now();
    }

    /**
     * @param failure why it didn't connect, e.g. "timed out", null for one that did
     */
    private static synchronized void endAttempt(Connection connection, @Nullable String failure) {
        if (connection.attemptFamily == null) {
            return;
        }

        long durationMs = sClock.now() - connection.attemptStartMs;
        connection.attempts.add(failure != null ? connection.attemptFamily + " " + failure + " after " + durationMs + " ms"
                : connection.attemptFamily + " " + durationMs + " ms");
        connection.attemptFamily = null;
        connection.attemptStartMs = -1;
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

        if (ioe instanceof ConnectException) {
            String message = ioe.getMessage();

            return message != null && message.contains("ENETUNREACH") ? "unreachable" : "refused";
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
                result.add(new VideoStart.Stage(VideoStarts.STAGE_CONNECT, connection.describe(isGoing), 1, startMs, endMs));
            }
        }

        addRequests(result, sResponses, VideoStarts.STAGE_RESPONSE, fromMs, toMs);
        addRequests(result, sOpenings, VideoStarts.STAGE_OPENING, fromMs, toMs);

        return result;
    }

    /**
     * The ones that waited long (see MIN_RESPONSE_MS)
     */
    private static void addRequests(List<VideoStart.Stage> result, ArrayDeque<long[]> requests, String name, long fromMs, long toMs) {
        for (long[] request : requests) {
            // Made before, it's of the source before (e.g. the video before's)
            if (request[0] < fromMs) {
                continue;
            }

            boolean isGoing = request[1] < 0;
            long endMs = isGoing ? toMs : Math.min(request[1], toMs);

            if (endMs - request[0] >= MIN_RESPONSE_MS) {
                result.add(new VideoStart.Stage(name, isGoing ? VideoStarts.DETAIL_GOING : null, 1, request[0], endMs));
            }
        }
    }

    @VisibleForTesting
    static synchronized void resetForTesting() {
        sConnections.clear();
        sResponses.clear();
        sOpenings.clear();
    }
}
