package com.google.android.exoplayer2.source.sabr.parser.adapter;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.extractor.mp4.Track;
import com.google.android.exoplayer2.source.sabr.parser.SabrStream;
import com.google.android.exoplayer2.source.sabr.parser.misc.SabrExtractorInput;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;
import java.util.List;

public class SabrFragmentedMp4Adapter extends FragmentedMp4Extractor {
    private static final String TAG = SabrFragmentedMp4Adapter.class.getSimpleName();
    private final SabrStream sabrStream;
    private final SabrExtractorInput extractorInput;

    public SabrFragmentedMp4Adapter(SabrStream sabrStream) {
        this.sabrStream = sabrStream;
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    public SabrFragmentedMp4Adapter(int flags, SabrStream sabrStream) {
        super(flags);
        this.sabrStream = sabrStream;
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    public SabrFragmentedMp4Adapter(
            int flags,
            @Nullable TimestampAdjuster timestampAdjuster,
            SabrStream sabrStream) {
        super(flags, timestampAdjuster);
        this.sabrStream = sabrStream;
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    public SabrFragmentedMp4Adapter(
            int flags,
            @Nullable TimestampAdjuster timestampAdjuster,
            @Nullable Track sideloadedTrack,
            @Nullable DrmInitData sideloadedDrmInitData,
            SabrStream sabrStream) {
        super(flags, timestampAdjuster, sideloadedTrack, sideloadedDrmInitData);
        this.sabrStream = sabrStream;
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    public SabrFragmentedMp4Adapter(
            int flags,
            @Nullable TimestampAdjuster timestampAdjuster,
            @Nullable Track sideloadedTrack,
            @Nullable DrmInitData sideloadedDrmInitData,
            List<Format> closedCaptionFormats,
            SabrStream sabrStream) {
        super(flags, timestampAdjuster, sideloadedTrack, sideloadedDrmInitData, closedCaptionFormats);
        this.sabrStream = sabrStream;
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    public SabrFragmentedMp4Adapter(
            int flags,
            @Nullable TimestampAdjuster timestampAdjuster,
            @Nullable Track sideloadedTrack,
            @Nullable DrmInitData sideloadedDrmInitData,
            List<Format> closedCaptionFormats,
            @Nullable TrackOutput additionalEmsgTrackOutput,
            SabrStream sabrStream) {
        super(flags, timestampAdjuster, sideloadedTrack, sideloadedDrmInitData, closedCaptionFormats, additionalEmsgTrackOutput);
        this.sabrStream = sabrStream;
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition)
            throws IOException, InterruptedException {
        int result = RESULT_END_OF_INPUT;

        try {
            extractorInput.init(input);
            result = super.read(extractorInput, seekPosition);
        } catch (IOException | IllegalStateException e) {
            IOException connectionError = SabrStream.getConnectionError(e);

            if (connectionError == null) {
                throw e;
            }

            // FIX: a retry would resume the response at a byte offset, which a SABR request can't do ("Unknown part
            // encountered"), and fail the playback. The chunk ends with the media it has instead: the next request
            // gets the segment again, and SabrSampleDeduplicator drops what is already queued.
            Log.e(TAG, "Mp4Adapter: the response broke off: %s", e.getMessage());
            sabrStream.setResponseError(connectionError);
        } finally {
            if (result != RESULT_CONTINUE) {
                Log.e(TAG, "Mp4Adapter: disposing, result=%s", result);
                extractorInput.dispose();
            }
        }

        return result;
    }
}
