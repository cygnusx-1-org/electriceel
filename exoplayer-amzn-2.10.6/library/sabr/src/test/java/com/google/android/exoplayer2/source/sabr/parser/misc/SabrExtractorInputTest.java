package com.google.android.exoplayer2.source.sabr.parser.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.sabr.parser.SabrStream;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.PoTokenError;
import com.google.android.exoplayer2.source.sabr.parser.parts.PoTokenStatusSabrPart.PoTokenStatus;
import com.google.android.exoplayer2.source.sabr.parser.ump.UMPPartId;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamProtectionStatus;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamProtectionStatus.Status;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext.ClientInfo;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;

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
