package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.source.sabr.ValidatedNetwork;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidContentTypeException;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

/**
 * Asks a googlevideo host that doesn't answer on the hosts the YouTube app falls back to (see GoogleVideoFallback). For the DASH
 * requests: the SABR ones move with their manifest (see SabrCdnSelector), and the redirector can't take their POSTs.
 */
final class GoogleVideoFallbackDataSource implements DataSource {
    private static final String TAG = GoogleVideoFallbackDataSource.class.getSimpleName();
    private final DataSource mUpstream;
    private final GoogleVideoFallback mFallback = new GoogleVideoFallback();
    @Nullable
    private String mRequestedUrl;
    private boolean mIsGet;

    private GoogleVideoFallbackDataSource(DataSource upstream) {
        mUpstream = upstream;
    }

    static DataSource.Factory wrap(DataSource.Factory factory) {
        return () -> new GoogleVideoFallbackDataSource(factory.createDataSource());
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        mUpstream.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        String url = dataSpec.uri.toString();
        mIsGet = dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET;
        mRequestedUrl = mFallback.resolve(url, mIsGet);
        DataSpec requested = mRequestedUrl.equals(url) ? dataSpec : dataSpec.withUri(Uri.parse(mRequestedUrl));

        try {
            long result = mUpstream.open(requested);
            mFallback.onSuccess();
            return result;
        } catch (IOException e) {
            onError(e, true);
            throw e;
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        try {
            return mUpstream.read(buffer, offset, readLength);
        } catch (IOException e) {
            onError(e, false);
            throw e;
        }
    }

    @Nullable
    @Override
    public Uri getUri() {
        return mUpstream.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return mUpstream.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        mUpstream.close();
    }

    private void onError(IOException error, boolean isOpen) {
        if (mRequestedUrl == null || Thread.currentThread().isInterrupted() || !isHostFailure(error, isOpen)) {
            return;
        }

        if (!ValidatedNetwork.isAvailable()) {
            // Offline: the failure says nothing about the host, don't leave it or mark its network failed
            Log.w(TAG, "No validated network, keeping the media host");
            return;
        }

        String nextUrl = mFallback.onHostFailure(mRequestedUrl, hasCause(error, SocketTimeoutException.class), mIsGet);

        if (nextUrl != null) {
            Log.w(TAG, "Media host %s didn't answer, the next request goes to %s", Uri.parse(mRequestedUrl).getHost(),
                    Uri.parse(nextUrl).getHost());
        }
    }

    /**
     * No answer from the host: no connection (refused, no route, unknown host, timed out), or none in the read timeout. Not an
     * answer it gave (an HTTP error), nor a load cancelled (the loader interrupts its thread).
     */
    @VisibleForTesting
    static boolean isHostFailure(IOException error, boolean isOpen) {
        if (!(error instanceof HttpDataSourceException)
                || error instanceof InvalidResponseCodeException
                || error instanceof InvalidContentTypeException) {
            return false;
        }

        if (hasCause(error, SocketTimeoutException.class)) {
            return true;
        }

        return isOpen && !hasCause(error, InterruptedIOException.class);
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }

        return false;
    }
}
