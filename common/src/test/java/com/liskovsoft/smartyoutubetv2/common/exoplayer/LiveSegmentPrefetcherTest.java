package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LiveSegmentPrefetcherTest {
    private static final String AUDIO = "https://rr1---sn-example.googlevideo.com/videoplayback?itag=140&live=1&hang=1";
    private static final String VIDEO = "https://rr1---sn-example.googlevideo.com/videoplayback?itag=299&live=1&hang=1";
    private final AtomicLong mNow = new AtomicLong();
    private final FakeUpstream mUpstream = new FakeUpstream();

    @Before
    public void setUp() {
        LiveSegmentPrefetcher.sClock = mNow::get;
    }

    @After
    public void tearDown() {
        LiveSegmentPrefetcher.sClock = android.os.SystemClock::elapsedRealtime;
    }

    @Test
    public void nextSegmentsAreAskedForAheadAndRead() throws IOException {
        LiveSegmentPrefetcher prefetcher = new LiveSegmentPrefetcher(mUpstream, Runnable::run);
        DataSource source = prefetcher.createDataSource();

        assertEquals("segment 10 of itag=140", readSegment(source, AUDIO, 10));
        assertEquals(Arrays.asList("140/10", "140/11", "140/12", "140/13"), mUpstream.requests());

        source.open(new DataSpec(Uri.parse(segmentUrl(AUDIO, 11))));
        // Its response, as the player's listeners see it (see LoadEventInfo)
        assertEquals("100", source.getResponseHeaders().get("X-Head-Seqnum").get(0));
        assertEquals(segmentUrl(AUDIO, 11), String.valueOf(source.getUri()));
        source.close();

        // 11 was read from the one asked for ahead, and the next one is asked for
        assertEquals(Arrays.asList("140/10", "140/11", "140/12", "140/13", "140/14"), mUpstream.requests());
        assertEquals("segment 12 of itag=140", readSegment(source, AUDIO, 12));
    }

    @Test
    public void segmentsPastTheHeadAreNotAskedForAhead() throws IOException {
        mUpstream.head = 11;
        LiveSegmentPrefetcher prefetcher = new LiveSegmentPrefetcher(mUpstream, Runnable::run);

        readSegment(prefetcher.createDataSource(), AUDIO, 10);

        // Past the head the server holds the request until the segment exists (hang=1)
        assertEquals(Arrays.asList("140/10", "140/11"), mUpstream.requests());
    }

    @Test
    public void nothingIsAskedForAheadWithoutAKnownHead() throws IOException {
        mUpstream.head = -1;
        LiveSegmentPrefetcher prefetcher = new LiveSegmentPrefetcher(mUpstream, Runnable::run);

        readSegment(prefetcher.createDataSource(), AUDIO, 10);

        assertEquals(Collections.singletonList("140/10"), mUpstream.requests());
    }

    @Test
    public void aSegmentThatFailedAheadIsAskedForAgain() throws IOException {
        mUpstream.failOnce.add(11L);
        LiveSegmentPrefetcher prefetcher = new LiveSegmentPrefetcher(mUpstream, Runnable::run);
        DataSource source = prefetcher.createDataSource();

        readSegment(source, AUDIO, 10);

        assertEquals("segment 11 of itag=140", readSegment(source, AUDIO, 11));
        assertEquals(2, mUpstream.count("140/11"));
    }

    @Test
    public void aSegmentOfAnotherTrackIsNotRead() throws IOException {
        LiveSegmentPrefetcher prefetcher = new LiveSegmentPrefetcher(mUpstream, Runnable::run);

        readSegment(prefetcher.createDataSource(), AUDIO, 10);
        assertEquals("segment 11 of itag=299", readSegment(prefetcher.createDataSource(), VIDEO, 11));

        assertEquals(1, mUpstream.count("299/11"));
    }

    @Test
    public void notASegmentNumberIsPassedThrough() throws IOException {
        LiveSegmentPrefetcher prefetcher = new LiveSegmentPrefetcher(mUpstream, Runnable::run);
        DataSource source = prefetcher.createDataSource();

        // The init segment: a byte range, no segment number
        source.open(new DataSpec(Uri.parse(AUDIO), 0, 1000, null));
        source.close();
        // A byte range of a segment (a load resumed part way)
        source.open(new DataSpec(Uri.parse(segmentUrl(AUDIO, 10)), 500, C.LENGTH_UNSET, null));
        source.close();

        assertEquals(Arrays.asList("140/-", "140/10"), mUpstream.requests());
        assertEquals(0, prefetcher.getPendingCount());
    }

    @Test
    public void passedSegmentsAreDropped() throws IOException {
        LiveSegmentPrefetcher prefetcher = new LiveSegmentPrefetcher(mUpstream, Runnable::run);
        DataSource source = prefetcher.createDataSource();

        readSegment(source, AUDIO, 10); // 11, 12, 13 ahead
        readSegment(source, AUDIO, 30); // seeked: 31, 32, 33 ahead

        assertEquals(3, prefetcher.getPendingCount());
        assertEquals(3 * "segment 31 of itag=140".length(), prefetcher.getKeptBytes());
    }

    @Test
    public void segmentsNotReadInTimeAreDropped() throws IOException {
        LiveSegmentPrefetcher prefetcher = new LiveSegmentPrefetcher(mUpstream, Runnable::run);

        readSegment(prefetcher.createDataSource(), VIDEO, 10); // the quality the player switched from
        mNow.addAndGet(21_000);
        readSegment(prefetcher.createDataSource(), AUDIO, 10);

        assertEquals("only the audio ones", 3, prefetcher.getPendingCount());
        assertEquals(3 * "segment 11 of itag=140".length(), prefetcher.getKeptBytes());
    }

    @Test
    public void thePlayerWaitsForASegmentBeingAskedForAhead() throws Exception {
        CountDownLatch fetchStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        mUpstream.gate = sq -> {
            if (sq == 11) {
                fetchStarted.countDown();
                await(release);
            }
        };
        Executor threads = runnable -> new Thread(runnable).start();
        LiveSegmentPrefetcher prefetcher = new LiveSegmentPrefetcher(mUpstream, threads);
        DataSource source = prefetcher.createDataSource();

        readSegment(source, AUDIO, 10);
        assertTrue(fetchStarted.await(5, TimeUnit.SECONDS));

        AtomicReference<String> read = new AtomicReference<>();
        Thread player = new Thread(() -> {
            try {
                read.set(readSegment(source, AUDIO, 11));
            } catch (IOException e) {
                read.set(e.toString());
            }
        });
        player.start();
        Thread.sleep(200);
        assertTrue("waiting", player.isAlive());

        release.countDown();
        player.join(5_000);

        assertEquals("segment 11 of itag=140", read.get());
        assertEquals(1, mUpstream.count("140/11"));
    }

    @Test
    public void transferListenersHearTheSegmentsAskedForAhead() throws IOException {
        LiveSegmentPrefetcher prefetcher = new LiveSegmentPrefetcher(mUpstream, Runnable::run);
        DataSource source = prefetcher.createDataSource();
        TransferListener listener = new TransferListener() {
            @Override
            public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {
            }

            @Override
            public void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork) {
            }

            @Override
            public void onBytesTransferred(DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred) {
            }

            @Override
            public void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {
            }
        };
        source.addTransferListener(listener);

        readSegment(source, AUDIO, 10);

        assertEquals("the player's source and the 3 ahead", 4, mUpstream.listened.size());
        for (List<TransferListener> listeners : mUpstream.listened) {
            assertEquals(Collections.singletonList(listener), listeners);
        }
    }

    @Test
    public void onlyShortSegmentsNeedIt() {
        assertTrue("ultra low latency", LiveSegmentPrefetcher.isNeeded(1_000_000));
        assertTrue("low latency", LiveSegmentPrefetcher.isNeeded(2_000_000));
        assertFalse("normal", LiveSegmentPrefetcher.isNeeded(5_000_000));
        assertFalse("unknown", LiveSegmentPrefetcher.isNeeded(0));
        assertFalse("unknown", LiveSegmentPrefetcher.isNeeded(-1));
    }

    private static String readSegment(DataSource source, String trackUrl, long sq) throws IOException {
        try {
            source.open(new DataSpec(Uri.parse(segmentUrl(trackUrl, sq))));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[7]; // smaller than a segment: read in parts
            int read;
            while ((read = source.read(buffer, 0, buffer.length)) != C.RESULT_END_OF_INPUT) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        } finally {
            source.close();
        }
    }

    private static String segmentUrl(String trackUrl, long sq) {
        return trackUrl + "&sq=" + sq;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                fail("not released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private interface Gate {
        void onOpen(long sq);
    }

    /**
     * A googlevideo live track: a segment's bytes name it, and each response says the head
     */
    private static final class FakeUpstream implements DataSource.Factory {
        private final List<String> mRequests = Collections.synchronizedList(new ArrayList<>());
        final List<Long> failOnce = Collections.synchronizedList(new ArrayList<>());
        final List<List<TransferListener>> listened = Collections.synchronizedList(new ArrayList<>());
        volatile long head = 100;
        volatile Gate gate;

        List<String> requests() {
            synchronized (mRequests) {
                return new ArrayList<>(mRequests);
            }
        }

        int count(String request) {
            return Collections.frequency(requests(), request);
        }

        @Override
        public DataSource createDataSource() {
            return new FakeSource(this);
        }
    }

    private static final class FakeSource implements DataSource {
        private final FakeUpstream mUpstream;
        private final List<TransferListener> mListeners = new ArrayList<>();
        private byte[] mData;
        private int mPosition;
        private Uri mUri;

        FakeSource(FakeUpstream upstream) {
            mUpstream = upstream;
        }

        @Override
        public void addTransferListener(TransferListener transferListener) {
            mListeners.add(transferListener);
        }

        @Override
        public long open(DataSpec dataSpec) throws IOException {
            String sq = dataSpec.uri.getQueryParameter("sq");
            String itag = dataSpec.uri.getQueryParameter("itag");
            mUpstream.mRequests.add(itag + "/" + (sq != null ? sq : "-"));
            if (sq != null) {
                mUpstream.listened.add(new ArrayList<>(mListeners));
            }
            Gate gate = mUpstream.gate;
            if (gate != null && sq != null) {
                gate.onOpen(Long.parseLong(sq));
            }
            if (sq != null && mUpstream.failOnce.remove(Long.valueOf(sq))) {
                throw new IOException("failed once: " + sq);
            }
            mUri = dataSpec.uri;
            mData = ("segment " + (sq != null ? sq : "init") + " of itag=" + itag).getBytes("UTF-8");
            mPosition = (int) dataSpec.position;
            return mData.length - mPosition;
        }

        @Override
        public int read(byte[] buffer, int offset, int readLength) {
            if (mPosition >= mData.length) {
                return C.RESULT_END_OF_INPUT;
            }
            int count = Math.min(readLength, mData.length - mPosition);
            System.arraycopy(mData, mPosition, buffer, offset, count);
            mPosition += count;
            return count;
        }

        @Nullable
        @Override
        public Uri getUri() {
            return mUri;
        }

        @Override
        public Map<String, List<String>> getResponseHeaders() {
            Map<String, List<String>> headers = new HashMap<>();
            if (mUpstream.head >= 0) {
                headers.put("X-Head-Seqnum", Collections.singletonList(String.valueOf(mUpstream.head)));
            }
            return headers;
        }

        @Override
        public void close() {
            mData = null;
        }
    }
}
