package com.google.android.exoplayer2.source.sabr.parser.exceptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.android.exoplayer2.source.sabr.protos.videostreaming.SabrError;

import org.junit.Test;

public class SabrProtocolErrorTest {
    /**
     * What the server said, on one line: e.g. the player's message and the diagnostics
     */
    @Test
    public void keepsWhatTheServerSaid() {
        SabrProtocolError error = new SabrProtocolError(SabrError.newBuilder()
                .setType("sabr.malformed_config")
                .setAction(2)
                .setError(com.google.android.exoplayer2.source.sabr.protos.videostreaming.Error.newBuilder().setStatusCode(400))
                .build());

        assertEquals("sabr.malformed_config", error.type);
        assertEquals(2, error.action);
        assertEquals(400, error.statusCode);
        assertEquals("SABR Protocol Error: sabr.malformed_config, action 2, status 400", error.getMessage());
    }

    /**
     * Left out by the server, it isn't made up: an action of 0 isn't one the server sent
     */
    @Test
    public void leavesOutWhatTheServerDidnt() {
        SabrProtocolError error = new SabrProtocolError(SabrError.getDefaultInstance());

        assertNull(error.type);
        assertEquals(SabrProtocolError.UNSET, error.action);
        assertEquals(SabrProtocolError.UNSET, error.statusCode);
        assertEquals("SABR Protocol Error: unknown", error.getMessage());
    }

    /**
     * An error without its status: the action is still told
     */
    @Test
    public void tellsTheActionWithoutAStatus() {
        SabrProtocolError error = new SabrProtocolError(SabrError.newBuilder()
                .setType("sabr.no_available_formats")
                .setAction(2)
                .setError(com.google.android.exoplayer2.source.sabr.protos.videostreaming.Error.getDefaultInstance())
                .build());

        assertEquals(SabrProtocolError.UNSET, error.statusCode);
        assertEquals("SABR Protocol Error: sabr.no_available_formats, action 2", error.getMessage());
    }
}
