package com.google.android.exoplayer2.source.sabr.parser.exceptions;

import com.google.android.exoplayer2.source.sabr.parser.parts.PoTokenStatusSabrPart.PoTokenStatus;

/**
 * The server requires attestation (StreamProtectionStatus ATTESTATION_REQUIRED). It won't send media past its grace period
 * (about a minute) with this poToken: the one sent is invalid, or none was sent.
 */
public class PoTokenError extends SabrStreamError {
    /**
     * INVALID when a poToken was sent, MISSING when none was
     */
    public final PoTokenStatus status;

    public PoTokenError(PoTokenStatus status) {
        super(status == PoTokenStatus.INVALID ? "SABR server rejected the poToken" : "SABR server requires a poToken");
        this.status = status;
    }
}
