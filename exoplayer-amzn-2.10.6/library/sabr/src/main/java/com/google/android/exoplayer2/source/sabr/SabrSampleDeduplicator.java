package com.google.android.exoplayer2.source.sabr;

import android.util.SparseArray;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkExtractorWrapper.TrackOutputProvider;
import com.google.android.exoplayer2.source.sabr.parser.SabrStream;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps the samples of a segment that the server sends again from being queued twice.
 * <p/>
 * A response can end in the middle of a segment, e.g. when a read times out and SabrMatroskaAdapter or
 * SabrFragmentedMp4Adapter ends the chunk. The stream time only advances when a segment ends, so the next
 * request asks for the same segment and the server sends all of it again. Without this, its first
 * samples would be queued a second time and the video would fall seconds behind the audio.
 */
/* package */ final class SabrSampleDeduplicator {
    private static final String TAG = SabrSampleDeduplicator.class.getSimpleName();
    private final SabrStream sabrStream;
    private final SparseArray<Segment> segments = new SparseArray<>();

    private static final class Segment {
        private int itag = -1;
        private long sequenceNumber = -1;
        /** The chunk that queued each sample of the segment, by sample time */
        private final Map<Long, Chunk> queuedSamples = new HashMap<>();
        @Nullable private Chunk resentBy;
    }

    /* package */ SabrSampleDeduplicator(SabrStream sabrStream) {
        this.sabrStream = sabrStream;
    }

    /**
     * Wraps the outputs of a media chunk so that the samples another chunk already queued are dropped.
     */
    /* package */ TrackOutputProvider wrap(Chunk chunk, TrackOutputProvider output) {
        SparseArray<TrackOutput> trackOutputs = new SparseArray<>();

        return (id, type) -> {
            TrackOutput trackOutput = trackOutputs.get(id);

            if (trackOutput == null) {
                trackOutput = new DeduplicatingTrackOutput(output.track(id, type), id, chunk);
                trackOutputs.put(id, trackOutput);
            }

            return trackOutput;
        };
    }

    /**
     * Forgets the queued samples. Call it when the sample queue is reset or chunks are discarded from it,
     * so that a segment loaded again isn't mistaken for a repeat.
     */
    /* package */ void reset() {
        segments.clear();
    }

    /**
     * False if an earlier chunk already queued this sample of the segment.
     */
    /* package */ boolean shouldQueue(int trackId, Chunk chunk, int itag, long sequenceNumber, long timeUs) {
        Segment segment = segments.get(trackId);

        if (segment == null) {
            segment = new Segment();
            segments.put(trackId, segment);
        }

        if (segment.itag != itag || segment.sequenceNumber != sequenceNumber) {
            segment.itag = itag;
            segment.sequenceNumber = sequenceNumber;
            segment.queuedSamples.clear();
            segment.resentBy = null;
        }

        Chunk queuedBy = segment.queuedSamples.get(timeUs);

        if (queuedBy != null && queuedBy != chunk) {
            if (segment.resentBy != chunk) {
                segment.resentBy = chunk;
                Log.w(TAG, "Segment " + sequenceNumber + " of itag " + itag
                        + " was sent again, dropping the samples already queued");
            }

            return false;
        }

        segment.queuedSamples.put(timeUs, chunk);

        return true;
    }

    private final class DeduplicatingTrackOutput implements TrackOutput {
        private final TrackOutput output;
        private final int trackId;
        private final Chunk chunk;

        private DeduplicatingTrackOutput(TrackOutput output, int trackId, Chunk chunk) {
            this.output = output;
            this.trackId = trackId;
            this.chunk = chunk;
        }

        @Override
        public void format(Format format) {
            output.format(format);
        }

        @Override
        public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput)
                throws IOException, InterruptedException {
            return output.sampleData(input, length, allowEndOfInput);
        }

        @Override
        public void sampleData(ParsableByteArray data, int length) {
            output.sampleData(data, length);
        }

        @Override
        public void sampleMetadata(long timeUs, int flags, int size, int offset, @Nullable CryptoData cryptoData) {
            // The data of a dropped sample stays in the queue unreferenced, and the offset of the next sample
            // skips it. SampleQueue does the same while it waits for a keyframe.
            if (shouldQueue(trackId, chunk, sabrStream.getSegmentItag(), sabrStream.getSegmentSequenceNumber(), timeUs)) {
                output.sampleMetadata(timeUs, flags, size, offset, cryptoData);
            }
        }
    }
}
