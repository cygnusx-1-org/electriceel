package com.google.android.exoplayer2.source.sabr.parser.exceptions;

/**
 * Where a UMP part's header should be, the response has bytes no part starts with (an id far past the known ones): the response is
 * broken or was read out of step, not one the stream can go on with. An IllegalStateException, as it was thrown before.
 */
public class UnknownPartError extends IllegalStateException {
    public UnknownPartError(String msg) {
        super(msg);
    }
}
