package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Protocol;

/**
 * The connections the player's OkHttp client opened, from the name's lookup to the connection being ready. A segment that took long
 * with none in its time took long at the server. Any thread: OkHttp's.
 */
public final class Connections {
    /**
     * The last ones: a start only needs the ones since it was prepared
     */
    static final int MAX_CONNECTIONS = 8;
    /**
     * For the client of the player only: the API's connections aren't the video's
     */
    public static final EventListener.Factory LISTENER = call -> new Listener();
    private static final ArrayDeque<long[]> sConnections = new ArrayDeque<>(); // {start, end}, end -1 going on
    @VisibleForTesting
    static VideoStarts.Clock sClock = SystemClock::elapsedRealtime;

    private Connections() {
    }

    /**
     * Of a call. Its dead ends count: they're the time it took to connect.
     */
    private static final class Listener extends EventListener {
        private volatile long[] mConnection;

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

        @Override
        public void callEnd(@NonNull Call call) {
            onEnd();
        }

        @Override
        public void callFailed(@NonNull Call call, @NonNull IOException ioe) {
            onEnd();
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

    private static synchronized void end(long[] connection) {
        connection[1] = sClock.now();
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

        return result;
    }

    @VisibleForTesting
    static synchronized void resetForTesting() {
        sConnections.clear();
    }
}
