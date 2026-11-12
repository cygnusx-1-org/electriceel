package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.sabr.GoogleVideoHosts;
import com.google.android.exoplayer2.source.sabr.ValidatedNetwork;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GoogleVideoFallbackDataSourceTest {
    private static final String GIVEN = "https://rr1---sn-given.googlevideo.com/videoplayback?itag=137&mn=sn-given%2Csn-second&fvip=3&sig=AB";
    private static final String GIVEN_HOST = "rr1---sn-given.googlevideo.com";
    private static final String FALLBACK_HOST = "r3---sn-second.googlevideo.com";
    private static final String REDIRECTOR_HOST = "redirector.googlevideo.com";
    private FakeUpstream mUpstream;
    private DataSource mDataSource;

    @Before
    public void setUp() {
        GoogleVideoHosts.clearFailedNetworks();
        mUpstream = new FakeUpstream();
        mDataSource = GoogleVideoFallbackDataSource.wrap(() -> mUpstream).createDataSource();
    }

    @After
    public void tearDown() {
        ValidatedNetwork.setSupplier(null);
        Thread.interrupted(); // clears it
    }

    @Test
    public void unreachableHostsAreLeftInTheLoadersAttempts() {
        mUpstream.fail(GIVEN_HOST, connectFailure(new UnknownHostException()));
        mUpstream.fail(FALLBACK_HOST, connectFailure(new UnknownHostException()));

        for (int attempt = 0; attempt < 3; attempt++) {
            openFailing(GIVEN);
        }

        open(GIVEN);

        assertEquals(hosts(GIVEN_HOST, GIVEN_HOST, FALLBACK_HOST, REDIRECTOR_HOST), mUpstream.hosts);
    }

    @Test
    public void readTimeoutLeavesTheHost() throws IOException {
        mUpstream.failRead(GIVEN_HOST, new HttpDataSourceException(new SocketTimeoutException(), null, HttpDataSourceException.TYPE_READ));
        open(GIVEN);

        try {
            mDataSource.read(new byte[1], 0, 1);
            fail();
        } catch (HttpDataSourceException expected) {
            // the error still reaches the loader
        }

        mDataSource.close();
        open(GIVEN);

        assertEquals(hosts(GIVEN_HOST, FALLBACK_HOST), mUpstream.hosts);
    }

    @Test
    public void otherReadFailuresAndHttpErrorsKeepTheHost() throws IOException {
        mUpstream.failRead(GIVEN_HOST, new HttpDataSourceException(new IOException("reset"), null, HttpDataSourceException.TYPE_READ));
        open(GIVEN);

        try {
            mDataSource.read(new byte[1], 0, 1);
            fail();
        } catch (HttpDataSourceException expected) {
        }

        mUpstream.failRead(GIVEN_HOST, null);
        mUpstream.fail(GIVEN_HOST, new InvalidResponseCodeException(403, null, Collections.emptyMap(), null));
        openFailing(GIVEN);
        openFailing(GIVEN);
        openFailing(GIVEN);

        assertEquals(hosts(GIVEN_HOST, GIVEN_HOST, GIVEN_HOST, GIVEN_HOST), mUpstream.hosts);
        assertFalse(GoogleVideoHosts.isNetworkFailed(GIVEN));
    }

    @Test
    public void offlineFailuresKeepTheHostAndMarkNothing() {
        ValidatedNetwork.setSupplier(() -> false);
        mUpstream.fail(GIVEN_HOST, connectFailure(new SocketTimeoutException()));

        openFailing(GIVEN);
        openFailing(GIVEN);
        openFailing(GIVEN);

        assertEquals(hosts(GIVEN_HOST, GIVEN_HOST, GIVEN_HOST), mUpstream.hosts);
        assertFalse(GoogleVideoHosts.isNetworkFailed(GIVEN));
    }

    @Test
    public void cancelledLoadsDontCount() {
        mUpstream.fail(GIVEN_HOST, connectFailure(new InterruptedIOException()));
        openFailing(GIVEN);
        openFailing(GIVEN);

        Thread.currentThread().interrupt(); // the loader cancels a load by interrupting its thread
        mUpstream.fail(GIVEN_HOST, connectFailure(new SocketTimeoutException()));
        openFailing(GIVEN);
        Thread.interrupted();

        mUpstream.fail(GIVEN_HOST, null);
        open(GIVEN);

        assertEquals(hosts(GIVEN_HOST, GIVEN_HOST, GIVEN_HOST, GIVEN_HOST), mUpstream.hosts);
    }

    @Test
    public void postIsNeverSentToTheRedirector() {
        mUpstream.fail(GIVEN_HOST, connectFailure(new UnknownHostException()));
        mUpstream.fail(FALLBACK_HOST, connectFailure(new UnknownHostException()));
        DataSpec post = new DataSpec(Uri.parse(GIVEN), new byte[] {1}, 0, 0, -1, null, 0);

        for (int attempt = 0; attempt < 4; attempt++) {
            openFailing(post);
        }

        assertEquals(hosts(GIVEN_HOST, GIVEN_HOST, FALLBACK_HOST, FALLBACK_HOST), mUpstream.hosts);
        assertEquals(DataSpec.HTTP_METHOD_POST, mUpstream.lastSpec.httpMethod);
    }

    @Test
    public void requestKeepsItsRangeAndHeaders() {
        mUpstream.fail(GIVEN_HOST, connectFailure(new SocketTimeoutException()));
        openFailing(GIVEN);
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Test", "1");
        DataSpec range = new DataSpec(Uri.parse(GIVEN), DataSpec.HTTP_METHOD_GET, null, 100, 100, 50, "key", 0, headers);

        try {
            mDataSource.open(range);
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        assertEquals(FALLBACK_HOST, mUpstream.lastSpec.uri.getHost());
        assertEquals(100, mUpstream.lastSpec.position);
        assertEquals(50, mUpstream.lastSpec.length);
        assertEquals("1", mUpstream.lastSpec.httpRequestHeaders.get("X-Test"));
    }

    private static HttpDataSourceException connectFailure(IOException cause) {
        return new HttpDataSourceException("Unable to connect", cause, null, HttpDataSourceException.TYPE_OPEN);
    }

    private static List<String> hosts(String... hosts) {
        List<String> result = new ArrayList<>();
        Collections.addAll(result, hosts);
        return result;
    }

    private void open(String url) {
        try {
            mDataSource.open(new DataSpec(Uri.parse(url)));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private void openFailing(String url) {
        openFailing(new DataSpec(Uri.parse(url)));
    }

    private void openFailing(DataSpec dataSpec) {
        try {
            mDataSource.open(dataSpec);
            fail("Expected a failure");
        } catch (IOException expected) {
            // the error still reaches the loader
        }
    }

    private static final class FakeUpstream implements DataSource {
        final List<String> hosts = new ArrayList<>();
        final Map<String, IOException> openFailures = new HashMap<>();
        final Map<String, IOException> readFailures = new HashMap<>();
        DataSpec lastSpec;

        void fail(String host, @Nullable IOException error) {
            openFailures.put(host, error);
        }

        void failRead(String host, @Nullable IOException error) {
            readFailures.put(host, error);
        }

        @Override
        public void addTransferListener(TransferListener transferListener) {
        }

        @Override
        public long open(DataSpec dataSpec) throws IOException {
            lastSpec = dataSpec;
            hosts.add(dataSpec.uri.getHost());
            IOException error = openFailures.get(dataSpec.uri.getHost());

            if (error != null) {
                throw error;
            }

            return 1;
        }

        @Override
        public int read(byte[] buffer, int offset, int readLength) throws IOException {
            IOException error = readFailures.get(lastSpec.uri.getHost());

            if (error != null) {
                throw error;
            }

            return 1;
        }

        @Nullable
        @Override
        public Uri getUri() {
            return lastSpec != null ? lastSpec.uri : null;
        }

        @Override
        public void close() {
        }
    }
}
