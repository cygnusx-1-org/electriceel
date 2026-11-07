package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks for the next segments of a live stream with short segments (ultra low latency: 1 s) while the player loads one.<br/>
 * A segment the googlevideo edge hasn't cached is answered in ~1.4 s however old it is, and the player asks for one segment of a track
 * at a time, so 1 s segments came at 0.41x of real time and the stream stalled every few seconds (upstream SmartTube #6009). Requests in
 * parallel are answered in the same ~1.4 s each.<br/>
 * Only the segments the server said exist (X-Head-Seqnum) are asked for ahead: a later one is held until it exists (hang=1).
 */
public final class LiveSegmentPrefetcher implements DataSource.Factory {
    /**
     * Asked for past the one the player loads: with it, 1 + 3 requests of ~1.4 s each are going on for 1 s segments
     */
    static final int PREFETCH_COUNT = 3;
    /**
     * A segment that's bigger isn't kept (4K at 25 Mbps is ~3 MB a second)
     */
    private static final int MAX_SEGMENT_BYTES = 8 * 1024 * 1024;
    /**
     * Of the segments fetched ahead and not read yet: past it, no more are asked for
     */
    private static final long MAX_KEPT_BYTES = 16 * 1024 * 1024;
    /**
     * Segments asked for ahead, of all tracks: the audio, the video and the ones of a quality that was just switched from
     */
    private static final int MAX_SEGMENTS = PREFETCH_COUNT * 4;
    /**
     * A segment fetched ahead and not read in this time won't be (the quality changed or the player seeked)
     */
    private static final long STALE_MS = 20_000;
    /**
     * The player waits for a segment asked for ahead at most this long, then asks for it itself
     */
    private static final long WAIT_MS = 20_000;
    private static final int READ_BUFFER_BYTES = 32 * 1024;
    private static final String HEAD_SEQ_NUM = "X-Head-Seqnum";
    private static final Pattern SEGMENT_NUM = Pattern.compile("([?&])sq=(\\d+)");
    private static final Executor sExecutor = createExecutor();
    @VisibleForTesting
    static Clock sClock = SystemClock::elapsedRealtime;
    private final DataSource.Factory mUpstreamFactory;
    private final Executor mExecutor;
    /**
     * The segments asked for ahead by their key (see Segment#key)
     */
    private final Map<String, Segment> mSegments = new HashMap<>();
    private long mHeadSeqNum = -1;
    private long mKeptBytes;

    interface Clock {
        long now();
    }

    public LiveSegmentPrefetcher(DataSource.Factory upstreamFactory) {
        this(upstreamFactory, sExecutor);
    }

    @VisibleForTesting
    LiveSegmentPrefetcher(DataSource.Factory upstreamFactory, Executor executor) {
        mUpstreamFactory = upstreamFactory;
        mExecutor = executor;
    }

    /**
     * Of a live stream's segment duration (see MediaItemFormatInfo#getSegmentDurationUs): 1 s for ultra low latency, 2 s for low latency
     * and 5 s for the others, whose sequential requests keep up
     */
    public static boolean isNeeded(int segmentDurationUs) {
        return segmentDurationUs > 0 && segmentDurationUs < 3_000_000;
    }

    @Override
    public DataSource createDataSource() {
        return new PrefetchingDataSource(mUpstreamFactory.createDataSource());
    }

    /**
     * The segments asked for ahead and not read yet
     */
    @VisibleForTesting
    synchronized int getPendingCount() {
        return mSegments.size();
    }

    @VisibleForTesting
    synchronized long getKeptBytes() {
        return mKeptBytes;
    }

    /**
     * The segment the player asks for, if asked for ahead. The older ones of its track are dropped: it went past them. So are the ones of
     * the qualities of its kind (video or audio) it left.
     */
    @Nullable
    private synchronized Segment take(SegmentId id) {
        Segment taken = mSegments.remove(id.key());

        if (taken != null) {
            taken.isTaken = true;
            unkeep(taken);
        }

        long nowMs = sClock.now();
        Iterator<Segment> iterator = mSegments.values().iterator();
        while (iterator.hasNext()) {
            Segment segment = iterator.next();
            boolean isSameTrack = segment.id.base.equals(id.base);
            boolean isPassed = isSameTrack && (segment.id.num < id.num || segment.id.num > id.num + PREFETCH_COUNT);
            boolean isLeftQuality = !isSameTrack && id.kind != null && id.kind.equals(segment.id.kind);
            if (isPassed || isLeftQuality || nowMs - segment.createdMs > STALE_MS) {
                unkeep(segment);
                iterator.remove();
            }
        }

        return taken;
    }

    /**
     * The segments after the one the player asks for, up to the head the server said exists
     */
    private void prefetchAfter(SegmentId id, List<TransferListener> listeners) {
        List<Segment> started = new ArrayList<>();

        synchronized (this) {
            for (long num = id.num + 1; num <= id.num + PREFETCH_COUNT && num <= mHeadSeqNum; num++) {
                SegmentId next = id.withNum(num);
                if (mSegments.containsKey(next.key())) {
                    continue;
                }
                if (mSegments.size() >= MAX_SEGMENTS || mKeptBytes >= MAX_KEPT_BYTES) {
                    break;
                }
                Segment segment = new Segment(next, sClock.now());
                mSegments.put(next.key(), segment);
                started.add(segment);
            }
        }

        for (Segment segment : started) {
            mExecutor.execute(() -> fetch(segment, listeners));
        }
    }

    private void fetch(Segment segment, List<TransferListener> listeners) {
        DataSource source = mUpstreamFactory.createDataSource();
        for (TransferListener listener : listeners) {
            source.addTransferListener(listener);
        }

        try {
            source.open(new DataSpec(segment.id.uri, segment.id.flags));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[READ_BUFFER_BYTES];
            int read;
            while ((read = source.read(buffer, 0, buffer.length)) != C.RESULT_END_OF_INPUT) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_SEGMENT_BYTES) {
                    throw new IOException("Segment too big to keep: " + out.size());
                }
            }
            segment.data = out.toByteArray();
            segment.headers = source.getResponseHeaders();
            Uri uri = source.getUri();
            segment.uri = uri != null ? uri : segment.id.uri;
        } catch (IOException | RuntimeException e) {
            segment.data = null; // the player asks for it itself
        } finally {
            closeQuietly(source);
        }

        onFetched(segment);
    }

    private synchronized void onFetched(Segment segment) {
        onHeaders(segment.headers);

        if (segment.data != null) {
            if (mSegments.get(segment.id.key()) == segment) {
                segment.isKept = true;
                mKeptBytes += segment.data.length;
            } else if (!segment.isTaken) {
                segment.data = null; // dropped while it was fetched
            }
        }

        segment.done.countDown();
    }

    private synchronized void onHeaders(@Nullable Map<String, List<String>> headers) {
        String value = getHeader(headers, HEAD_SEQ_NUM);

        if (value == null) {
            return;
        }

        try {
            mHeadSeqNum = Math.max(mHeadSeqNum, Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            // Not a number: the head stays the one known
        }
    }

    private void unkeep(Segment segment) {
        if (segment.isKept && segment.data != null) {
            mKeptBytes -= segment.data.length;
        }
        segment.isKept = false;
    }

    @Nullable
    private static String getHeader(@Nullable Map<String, List<String>> headers, String name) {
        if (headers == null) {
            return null;
        }

        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey()) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }

        return null;
    }

    private static void closeQuietly(DataSource source) {
        try {
            source.close();
        } catch (IOException e) {
            // Its bytes are read
        }
    }

    private static Executor createExecutor() {
        // Two tracks with all their segments asked for ahead, and a spare
        int threads = PREFETCH_COUNT * 2 + 1;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "LiveSegmentPrefetcher");
            thread.setDaemon(true);
            return thread;
        });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * A segment of a track: its URL without the segment number (the track, see YouTubeMPDBuilder: format URL + "&sq=$Number$") and
     * its number
     */
    @VisibleForTesting
    static final class SegmentId {
        final String base;
        final long num;
        final Uri uri;
        final int flags;
        /**
         * Video or audio (mime=video/mp4): the track's other qualities and codecs have the same
         */
        @Nullable
        final String kind;

        private SegmentId(String base, long num, Uri uri, int flags, @Nullable String kind) {
            this.base = base;
            this.num = num;
            this.uri = uri;
            this.flags = flags;
            this.kind = kind;
        }

        /**
         * Null when it's not a whole segment asked for by its number (the init segment is a byte range)
         */
        @Nullable
        static SegmentId of(DataSpec dataSpec) {
            if (dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET || dataSpec.httpBody != null || dataSpec.position != 0
                    || dataSpec.length != C.LENGTH_UNSET || !dataSpec.httpRequestHeaders.isEmpty()) {
                return null;
            }

            String url = dataSpec.uri.toString();
            Matcher matcher = SEGMENT_NUM.matcher(url);
            if (!matcher.find()) {
                return null;
            }

            long num;
            try {
                num = Long.parseLong(matcher.group(2));
            } catch (NumberFormatException e) {
                return null;
            }

            String base = url.substring(0, matcher.start()) + url.substring(matcher.end());

            if (matcher.find()) { // two segment numbers: not the URL of a template
                return null;
            }

            String mimeType = dataSpec.uri.getQueryParameter("mime");
            String kind = mimeType != null && mimeType.indexOf('/') > 0 ? mimeType.substring(0, mimeType.indexOf('/')) : null;
            return new SegmentId(base, num, dataSpec.uri, dataSpec.flags, kind);
        }

        SegmentId withNum(long num) {
            String url = SEGMENT_NUM.matcher(uri.toString()).replaceFirst("$1sq=" + num);
            return new SegmentId(base, num, Uri.parse(url), flags, kind);
        }

        String key() {
            return num + "|" + base;
        }
    }

    private static final class Segment {
        final SegmentId id;
        final long createdMs;
        final CountDownLatch done = new CountDownLatch(1);
        // Written by the fetch before done counts down, read after it
        volatile byte[] data;
        volatile Map<String, List<String>> headers;
        volatile Uri uri;
        // Guarded by the prefetcher
        boolean isKept;
        boolean isTaken;

        Segment(SegmentId id, long createdMs) {
            this.id = id;
            this.createdMs = createdMs;
        }
    }

    /**
     * The player's (one per track): reads a segment asked for ahead, or asks for it itself
     */
    private final class PrefetchingDataSource implements DataSource {
        private final DataSource mUpstream;
        private final List<TransferListener> mListeners = new ArrayList<>();
        @Nullable
        private DataSource mOpened;
        @Nullable
        private Segment mServed;
        private int mReadPosition;

        PrefetchingDataSource(DataSource upstream) {
            mUpstream = upstream;
        }

        @Override
        public void addTransferListener(TransferListener transferListener) {
            mUpstream.addTransferListener(transferListener);
            mListeners.add(transferListener);
        }

        @Override
        public long open(DataSpec dataSpec) throws IOException {
            SegmentId id = SegmentId.of(dataSpec);

            if (id == null) {
                mOpened = mUpstream;
                return mUpstream.open(dataSpec);
            }

            Segment segment = take(id);

            if (segment != null && await(segment) && segment.data != null) {
                mServed = segment;
                mReadPosition = 0;
                prefetchAfter(id, new ArrayList<>(mListeners));
                return segment.data.length;
            }

            mOpened = mUpstream;
            long length = mUpstream.open(dataSpec);
            onHeaders(mUpstream.getResponseHeaders());
            prefetchAfter(id, new ArrayList<>(mListeners));
            return length;
        }

        private boolean await(Segment segment) throws InterruptedIOException {
            try {
                return segment.done.await(WAIT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException(); // the player cancelled the load
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int readLength) throws IOException {
            Segment served = mServed;

            if (served == null) {
                if (mOpened == null) {
                    throw new IOException("Not opened");
                }
                return mOpened.read(buffer, offset, readLength);
            }

            byte[] data = served.data;
            if (readLength == 0) {
                return 0;
            }
            if (mReadPosition >= data.length) {
                return C.RESULT_END_OF_INPUT;
            }

            int count = Math.min(readLength, data.length - mReadPosition);
            System.arraycopy(data, mReadPosition, buffer, offset, count);
            mReadPosition += count;
            return count;
        }

        @Nullable
        @Override
        public Uri getUri() {
            Segment served = mServed;
            if (served != null) {
                return served.uri;
            }
            return mOpened != null ? mOpened.getUri() : null;
        }

        @Override
        public Map<String, List<String>> getResponseHeaders() {
            Segment served = mServed;
            if (served != null) {
                return served.headers != null ? served.headers : Collections.emptyMap();
            }
            return mOpened != null ? mOpened.getResponseHeaders() : Collections.emptyMap();
        }

        @Override
        public void close() throws IOException {
            mServed = null;
            DataSource opened = mOpened;
            mOpened = null;
            if (opened != null) {
                opened.close();
            }
        }
    }
}
