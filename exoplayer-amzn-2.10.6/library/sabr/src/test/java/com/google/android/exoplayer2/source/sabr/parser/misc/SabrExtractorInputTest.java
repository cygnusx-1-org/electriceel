package com.google.android.exoplayer2.source.sabr.parser.misc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.sabr.parser.SabrStream;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.PoTokenError;
import com.google.android.exoplayer2.source.sabr.parser.models.VideoSelector;
import com.google.android.exoplayer2.source.sabr.parser.parts.PoTokenStatusSabrPart.PoTokenStatus;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPPartId;
import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatInitializationMetadata;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.MediaHeader;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamProtectionStatus;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamProtectionStatus.Status;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext.ClientInfo;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
@Config(sdk = 28)
public class SabrExtractorInputTest {
    private static final String PO_TOKEN = "dG9rZW4";

    /**
     * Attestation required with the poToken sent: the server rejected it, and won't send media past its grace period
     */
    @Test
    public void aRejectedPoTokenFailsTheLoad() throws Exception {
        assertEquals(PoTokenStatus.INVALID, readFailure(PO_TOKEN, Status.ATTESTATION_REQUIRED).status);
    }

    /**
     * Attestation required with none sent: the client needs one
     */
    @Test
    public void aMissingPoTokenFailsTheLoad() throws Exception {
        assertEquals(PoTokenStatus.MISSING, readFailure(null, Status.ATTESTATION_REQUIRED).status);
    }

    /**
     * Pending and OK go on: the media still comes
     */
    @Test
    public void otherStatusesDontFailTheLoad() throws Exception {
        assertEquals(C.RESULT_END_OF_INPUT, read(PO_TOKEN, Status.ATTESTATION_PENDING));
        assertEquals(C.RESULT_END_OF_INPUT, read(null, Status.ATTESTATION_PENDING));
        assertEquals(C.RESULT_END_OF_INPUT, read(PO_TOKEN, Status.OK));
        assertEquals(C.RESULT_END_OF_INPUT, read(null, Status.OK));
    }

    /**
     * The init chunk's request is answered with the init segment, then a media segment: only the init segment is read, the media
     * segment is left to the media chunk that asks for it
     */
    @Test
    public void theInitChunkReadsUpToItsInitSegment() throws Exception {
        SabrStream sabrStream = newStream();
        sabrStream.setInitRequest(true);
        FakeExtractorInput response = new FakeExtractorInput.Builder().setData(initResponse()).build();

        assertArrayEquals(INIT_DATA, readAll(sabrStream, response));
        // The media segment wasn't downloaded
        assertTrue(response.getPosition() < response.getLength());
    }

    /**
     * A media chunk's response is read whole, an init segment in it too
     */
    @Test
    public void aMediaChunkReadsTheWholeResponse() throws Exception {
        SabrStream sabrStream = newStream();
        sabrStream.setInitRequest(false);
        FakeExtractorInput response = new FakeExtractorInput.Builder().setData(initResponse()).build();
        byte[] expected = Arrays.copyOf(INIT_DATA, INIT_DATA.length + MEDIA_DATA.length);
        System.arraycopy(MEDIA_DATA, 0, expected, INIT_DATA.length, MEDIA_DATA.length);

        assertArrayEquals(expected, readAll(sabrStream, response));
        assertEquals(response.getLength(), response.getPosition());
    }

    private static final int ITAG = 315;
    private static final byte[] INIT_DATA = {1, 2, 3, 4};
    private static final byte[] MEDIA_DATA = {5, 6, 7, 8, 9, 10};

    private static SabrStream newStream() {
        SabrStream sabrStream = new SabrStream(
                "https://rr1---sn-primary.googlevideo.com/videoplayback", "", ClientInfo.getDefaultInstance(),
                -1, -1, -1, null, false, "test", 60_000);
        sabrStream.setFormatSelector(new VideoSelector("video", false, FormatId.newBuilder().setItag(ITAG).build()));
        return sabrStream;
    }

    /**
     * As the server sends it: the format's metadata, its init segment (header 0), then its first media segment (header 1)
     */
    private static byte[] initResponse() throws Exception {
        FormatId formatId = FormatId.newBuilder().setItag(ITAG).build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        part(out, UMPPartId.FORMAT_INITIALIZATION_METADATA, FormatInitializationMetadata.newBuilder()
                .setFormatId(formatId)
                .setMimeType("video/webm; codecs=\"vp09.00.51.08\"")
                .setEndSegmentNumber(10)
                .setDurationUnits(50_000)
                .setDurationTimescale(1_000)
                .build().toByteArray());
        segment(out, MediaHeader.newBuilder().setHeaderId(0).setFormatId(formatId).setIsInitSeg(true)
                .setContentLength(INIT_DATA.length).build(), 0, INIT_DATA);
        segment(out, MediaHeader.newBuilder().setHeaderId(1).setFormatId(formatId).setSequenceNumber(1).setStartMs(0)
                .setDurationMs(5_000).setContentLength(MEDIA_DATA.length).build(), 1, MEDIA_DATA);
        return out.toByteArray();
    }

    private static void segment(ByteArrayOutputStream out, MediaHeader header, int headerId, byte[] data) throws Exception {
        part(out, UMPPartId.MEDIA_HEADER, header.toByteArray());
        byte[] media = new byte[1 + data.length];
        media[0] = (byte) headerId;
        System.arraycopy(data, 0, media, 1, data.length);
        part(out, UMPPartId.MEDIA, media);
        part(out, UMPPartId.MEDIA_END, new byte[] {(byte) headerId});
    }

    /**
     * Ids and sizes below 128: a one-byte varint each
     */
    private static void part(ByteArrayOutputStream out, int partId, byte[] payload) throws Exception {
        out.write(partId);
        out.write(payload.length);
        out.write(payload);
    }

    private static byte[] readAll(SabrStream sabrStream, FakeExtractorInput response) throws Exception {
        SabrExtractorInput input = new SabrExtractorInput(sabrStream);
        input.init(response);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[16];
        int read;

        while ((read = input.read(buffer, 0, buffer.length)) != C.RESULT_END_OF_INPUT) {
            result.write(buffer, 0, read);
        }

        return result.toByteArray();
    }

    private static PoTokenError readFailure(String poToken, Status status) throws Exception {
        try {
            read(poToken, status);
        } catch (PoTokenError e) {
            return e;
        }

        fail("No PoTokenError for " + status);
        return null;
    }

    /**
     * A response that has only the StreamProtectionStatus
     */
    private static int read(String poToken, Status status) throws Exception {
        SabrStream sabrStream = new SabrStream(
                "https://rr1---sn-primary.googlevideo.com/videoplayback", "", ClientInfo.getDefaultInstance(),
                -1, -1, -1, poToken, false, "test", 60_000);
        SabrExtractorInput input = new SabrExtractorInput(sabrStream);
        byte[] payload = StreamProtectionStatus.newBuilder().setStatus(status).build().toByteArray();
        // Both are below 128: a one-byte varint each
        byte[] response = new byte[2 + payload.length];
        response[0] = (byte) UMPPartId.STREAM_PROTECTION_STATUS;
        response[1] = (byte) payload.length;
        System.arraycopy(payload, 0, response, 2, payload.length);

        input.init(new FakeExtractorInput.Builder().setData(response).build());

        return input.read(new byte[1], 0, 1);
    }
}
