package com.liskovsoft.smartyoutubetv2.common.utils;

import androidx.annotation.NonNull;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.HttpException;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.data.HttpUrlFetcher;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.load.model.stream.HttpGlideUrlLoader;
import com.bumptech.glide.util.ContentLengthInputStream;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Glide's images go through the app's OkHttp client while IPv6 doesn't connect (see OkHttpManager.isIpv6Disabled):
 * only its lookups leave IPv6 out. Glide's own fetcher otherwise.
 */
public final class Ipv6AwareUrlLoader implements ModelLoader<GlideUrl, InputStream> {
    private static volatile OkHttpClient sClient;

    @Override
    public LoadData<InputStream> buildLoadData(@NonNull GlideUrl model, int width, int height, @NonNull Options options) {
        int timeoutMs = options.get(HttpGlideUrlLoader.TIMEOUT);
        DataFetcher<InputStream> fetcher = OkHttpManager.isIpv6Disabled() ? new OkHttpFetcher(getClient(timeoutMs), model) : new HttpUrlFetcher(model, timeoutMs);

        // The url is the key, as with Glide's loader: the images cached already stay
        return new LoadData<>(model, fetcher);
    }

    @Override
    public boolean handles(@NonNull GlideUrl model) {
        return true;
    }

    /**
     * The app's client with Glide's timeouts. Not its interceptors (e.g. the profiler of the debug builds would hold every image).
     */
    private static OkHttpClient getClient(int timeoutMs) {
        if (sClient == null) {
            synchronized (Ipv6AwareUrlLoader.class) {
                if (sClient == null) {
                    OkHttpClient.Builder builder = OkHttpManager.instance().getClient().newBuilder()
                            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS);
                    builder.interceptors().clear();
                    builder.networkInterceptors().clear();
                    sClient = builder.build();
                }
            }
        }

        return sClient;
    }

    public static final class Factory implements ModelLoaderFactory<GlideUrl, InputStream> {
        @NonNull
        @Override
        public ModelLoader<GlideUrl, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
            return new Ipv6AwareUrlLoader();
        }

        @Override
        public void teardown() {
        }
    }

    /**
     * Like Glide's HttpUrlFetcher: an HTTP error fails with its status code (e.g. a missing thumbnail's 404, see ShortsCardPresenter)
     */
    private static final class OkHttpFetcher implements DataFetcher<InputStream> {
        private final OkHttpClient mClient;
        private final GlideUrl mUrl;
        private volatile Call mCall;
        private ResponseBody mBody;
        private InputStream mStream;

        OkHttpFetcher(OkHttpClient client, GlideUrl url) {
            mClient = client;
            mUrl = url;
        }

        @Override
        public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
            Request.Builder request = new Request.Builder().url(mUrl.toStringUrl());

            for (Map.Entry<String, String> header : mUrl.getHeaders().entrySet()) {
                request.addHeader(header.getKey(), header.getValue());
            }

            mCall = mClient.newCall(request.build());

            try {
                Response response = mCall.execute();
                mBody = response.body();

                if (!response.isSuccessful() || mBody == null) {
                    callback.onLoadFailed(new HttpException(response.message(), response.code()));
                    return;
                }

                mStream = ContentLengthInputStream.obtain(mBody.byteStream(), mBody.contentLength());
                callback.onDataReady(mStream);
            } catch (IOException e) {
                callback.onLoadFailed(e);
            }
        }

        @Override
        public void cleanup() {
            try {
                if (mStream != null) {
                    mStream.close();
                }
            } catch (IOException e) {
                // Nothing to do
            }

            if (mBody != null) {
                mBody.close();
            }
        }

        @Override
        public void cancel() {
            Call call = mCall;

            if (call != null) {
                call.cancel();
            }
        }

        @NonNull
        @Override
        public Class<InputStream> getDataClass() {
            return InputStream.class;
        }

        @NonNull
        @Override
        public DataSource getDataSource() {
            return DataSource.REMOTE;
        }
    }
}
