package com.google.android.exoplayer2.source.sabr.parser.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.source.sabr.parser.SabrStream;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext.ClientInfo;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@Config(sdk = 28)
public class SabrMatroskaAdapterTest {
    private static final DataSpec DATA_SPEC = new DataSpec(Uri.parse("https://rr1---sn-primary.googlevideo.com/videoplayback"));

    private final SabrStream sabrStream = new SabrStream(
            "https://rr1---sn-primary.googlevideo.com/videoplayback", "", ClientInfo.getDefaultInstance(),
            -1, -1, -1, null, false, "test", 60_000);
    private final SabrMatroskaAdapter adapter = new SabrMatroskaAdapter(sabrStream);

    @Before
    public void setUp() {
        adapter.init(mock(ExtractorOutput.class));
    }

    @Test
    public void reportsABrokenConnection() throws Exception {
        HttpDataSourceException error = new HttpDataSourceException(
                new SocketTimeoutException("timeout"), DATA_SPEC, HttpDataSourceException.TYPE_READ);

        assertEquals(Extractor.RESULT_END_OF_INPUT, adapter.read(failingInput(error), new PositionHolder()));
        assertSame(error, sabrStream.pollResponseError());
    }

    @Test
    public void doesNotReportACanceledRead() throws Exception {
        HttpDataSourceException error = new HttpDataSourceException(
                new InterruptedIOException("interrupted"), DATA_SPEC, HttpDataSourceException.TYPE_READ);

        assertEquals(Extractor.RESULT_END_OF_INPUT, adapter.read(failingInput(error), new PositionHolder()));
        assertNull(sabrStream.pollResponseError());
    }

    private static ExtractorInput failingInput(IOException error) throws Exception {
        ExtractorInput input = mock(ExtractorInput.class);
        when(input.readFully(any(), anyInt(), anyInt(), anyBoolean())).thenThrow(error);
        return input;
    }
}
