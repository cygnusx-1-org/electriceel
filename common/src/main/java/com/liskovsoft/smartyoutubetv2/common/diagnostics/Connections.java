package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Protocol;

/**
 * The connections the player's OkHttp client opened, from the name's lookup to the connection being ready, and its requests that
 * waited long for their answer, from being sent to its first byte. A segment that took long with no response in its time was slow
 * to come, one with a long response waited for the server. The other engines' requests are timed whole, from being made to their
 * answer's headers, connect included (see timeOpenings). Any thread: OkHttp's and the loaders'.
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
    private static final ArrayDeque<long[]> sConnections = new ArrayDeque<>(); // {start, end}, end -1 going on
    private static final ArrayDeque<long[]> sResponses = new ArrayDeque<>(); // {sent, first byte}, first byte -1 waiting for it
    private static final ArrayDeque<long[]> sOpenings = new ArrayDeque<>(); // {made, headers}, headers -1 waiting for them
    @VisibleForTesting
    static VideoStarts.Clock sClock = SystemClock::elapsedRealtime;

    private Connections() {
    }

    /**
     * Of a call. Its dead ends count: they're the time it took to connect.
     */
    private static final class Listener extends EventListener {
        private volatile long[] mConnection;
        private volatile long[] mResponse;

        private void onStart() {
            if (mConnection == null) {
                mConnection = add(sClock.now(), -1);
            }
        }

        private void onEnd() {
            long[] connection = mConnection;

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
        }

        @Override
        public void connectStart(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull Proxy proxy) {
            onStart();
        }

        @Override
        public void connectEnd(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull Proxy proxy, Protocol protocol) {
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
    static synchronized long[] add(long startMs, long endMs) {
        long[] connection = {startMs, endMs};
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

    /**
     * The ones between the times, cut to them, as stages of a start's buffering (in the clock's time). One still connecting is
     * what a stuck start waits for, so it's there too.
     */
    static synchronized List<VideoStart.Stage> take(long fromMs, long toMs) {
        List<VideoStart.Stage> result = new ArrayList<>();

        for (long[] connection : sConnections) {
            long startMs = Math.max(connection[0], fromMs);
            boolean isGoing = connection[1] < 0;
            long endMs = isGoing ? toMs : Math.min(connection[1], toMs);

            if (endMs - startMs >= 1) {
                result.add(new VideoStart.Stage(VideoStarts.STAGE_CONNECT, isGoing ? VideoStarts.DETAIL_GOING : null, 1, startMs, endMs));
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
