package com.google.android.exoplayer2.source.sabr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.sabr.parser.SabrStream;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext.ClientInfo;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@Config(sdk = 28)
public class SabrSampleDeduplicatorTest {
    private static final int TRACK_ID = 1;
    private static final int ITAG = 248;

    private final SabrSampleDeduplicator deduplicator = new SabrSampleDeduplicator(createStream());
    private final Chunk firstChunk = mock(Chunk.class);
    private final Chunk secondChunk = mock(Chunk.class);
    private final Chunk thirdChunk = mock(Chunk.class);

    @Test
    public void queuesEverySampleOfOneResponse() {
        assertTrue(queue(firstChunk, 7, 0));
        assertTrue(queue(firstChunk, 7, 100_000));
        assertTrue(queue(firstChunk, 7, 33_000));
    }

    @Test
    public void dropsTheSamplesOfAResentSegmentThatAreAlreadyQueued() {
        // Decode order with B-frames: the queued samples are not a prefix of the times
        queue(firstChunk, 7, 0);
        queue(firstChunk, 7, 100_000);
        queue(firstChunk, 7, 33_000);

        assertFalse(queue(secondChunk, 7, 0));
        assertFalse(queue(secondChunk, 7, 100_000));
        assertFalse(queue(secondChunk, 7, 33_000));
        assertTrue(queue(secondChunk, 7, 66_000));
        assertTrue(queue(secondChunk, 7, 200_000));
    }

    @Test
    public void dropsTheSamplesQueuedByEveryEarlierResponse() {
        queue(firstChunk, 7, 0);
        queue(secondChunk, 7, 0);
        queue(secondChunk, 7, 33_000);

        assertFalse(queue(thirdChunk, 7, 0));
        assertFalse(queue(thirdChunk, 7, 33_000));
        assertTrue(queue(thirdChunk, 7, 66_000));
    }

    @Test
    public void queuesTheNextSegment() {
        queue(firstChunk, 7, 0);

        assertTrue(queue(secondChunk, 8, 5_000_000));
    }

    @Test
    public void queuesTheSameSegmentOfAnotherFormat() {
        deduplicator.shouldQueue(TRACK_ID, firstChunk, ITAG, 7, 0);

        assertTrue(deduplicator.shouldQueue(TRACK_ID, secondChunk, 247, 7, 0));
    }

    @Test
    public void tracksAreIndependent() {
        deduplicator.shouldQueue(TRACK_ID, firstChunk, ITAG, 7, 0);

        assertTrue(deduplicator.shouldQueue(TRACK_ID + 1, secondChunk, ITAG, 7, 0));
    }

    @Test
    public void queuesASegmentAgainAfterAReset() {
        queue(firstChunk, 7, 0);
        deduplicator.reset();

        assertTrue(queue(secondChunk, 7, 0));
    }

    @Test
    public void droppedSampleDataIsSkippedInTheQueue() {
        // The stream has not read any segment, so every sample belongs to the same one
        SampleQueue sampleQueue = new SampleQueue(new DefaultAllocator(true, 16));
        TrackOutput first = deduplicator.wrap(firstChunk, (id, type) -> sampleQueue).track(TRACK_ID, C.TRACK_TYPE_VIDEO);
        first.format(Format.createSampleFormat(null, MimeTypes.VIDEO_VP9, 0));
        writeSample(first, 0, 1, C.BUFFER_FLAG_KEY_FRAME);
        writeSample(first, 33_000, 2, 0);
        // A response that ended in the middle of the segment leaves part of a sample
        first.sampleData(new ParsableByteArray(new byte[] {9, 9, 9}), 3);

        TrackOutput second = deduplicator.wrap(secondChunk, (id, type) -> sampleQueue).track(TRACK_ID, C.TRACK_TYPE_VIDEO);
        writeSample(second, 0, 1, C.BUFFER_FLAG_KEY_FRAME);
        writeSample(second, 33_000, 2, 0);
        writeSample(second, 66_000, 3, 0);

        assertEquals(Arrays.asList("0:1", "33000:2", "66000:3"), readSamples(sampleQueue));
    }

    private boolean queue(Chunk chunk, long sequenceNumber, long timeUs) {
        return deduplicator.shouldQueue(TRACK_ID, chunk, ITAG, sequenceNumber, timeUs);
    }

    private static void writeSample(TrackOutput output, long timeUs, int value, int flags) {
        byte[] data = new byte[] {(byte) value, (byte) value};
        output.sampleData(new ParsableByteArray(data), data.length);
        output.sampleMetadata(timeUs, flags, data.length, 0, null);
    }

    private static List<String> readSamples(SampleQueue sampleQueue) {
        List<String> samples = new ArrayList<>();
        FormatHolder formatHolder = new FormatHolder();
        DecoderInputBuffer buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);

        while (true) {
            buffer.clear();
            int result = sampleQueue.read(formatHolder, buffer, false, false, 0);

            if (result == C.RESULT_NOTHING_READ) {
                return samples;
            }

            if (result == C.RESULT_BUFFER_READ) {
                buffer.flip();
                byte[] data = new byte[buffer.data.remaining()];
                buffer.data.get(data);
                assertEquals(2, data.length);
                assertEquals(data[0], data[1]);
                samples.add(buffer.timeUs + ":" + data[0]);
            }
        }
    }

    private static SabrStream createStream() {
        return new SabrStream(
                "https://rr1---sn-primary.googlevideo.com/videoplayback", "", ClientInfo.getDefaultInstance(),
                -1, -1, -1, null, false, "test", 60_000);
    }
}
