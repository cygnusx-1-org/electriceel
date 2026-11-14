package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.source.sabr.parser.SabrStream;
import com.liskovsoft.googlecommon.common.helpers.DefaultHeaders;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.Connections;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Connects to the host a SABR stream is redirected to (see SabrStream#setRedirectListener) while the response that said so is still
 * read: the next request finds the connection open, not opening one of its own. On a Realtek box (Iraq, Wi-Fi) 78 of 93 starts opened
 * one for their first video chunk, a median 237 ms on the path to the first frame, and the one start that numbered its servers had
 * moved to a second server after the init chunks. With the OkHttp engine only: its client's pool is the app's (see
 * ExoMediaSourceFactory#buildOkHttpDataSourceFactory), Cronet and the default engine keep their own. The request is the one YouTube's
 * players warm a host with, an empty 204. Shown in a video start as a connect of the "prewarm" load (see Connections#LOAD_PREWARM).
 */
public final class SabrHostPrewarmer {
    private static final String TAG = SabrHostPrewarmer.class.getSimpleName();
    @VisibleForTesting
    static final String PATH = "/generate_204";
    private static final String MEDIA_DOMAIN = ".googlevideo.com";
    /**
     * The same host isn't connected to again this soon: the pool keeps an idle connection for minutes
     */
    @VisibleForTesting
    static final long SAME_HOST_MS = 30_000;
    @SuppressLint("StaticFieldLeak")
    @Nullable
    private static Context sContext;
    @Nullable
    private static String sLastHost;
    private static long sLastMs;
    @VisibleForTesting
    static Requester sRequester = SabrHostPrewarmer::request;
    @VisibleForTesting
    static EngineCheck sEngineCheck = SabrHostPrewarmer::isOkHttpEngine;

    interface Requester {
        void request(String url);
    }

    interface EngineCheck {
        boolean isOkHttp();
    }

    private SabrHostPrewarmer() {
    }

    public static void init(Context context) {
        if (context != null) {
            sContext = context.getApplicationContext();
        }

        SabrStream.setRedirectListener(SabrHostPrewarmer::onRedirect);
    }

    /**
     * On the loader's thread: the request is made on OkHttp's
     */
    @VisibleForTesting
    static void onRedirect(@Nullable String fromUrl, String toUrl) {
        String host = hostOf(toUrl);

        if (host == null || !host.endsWith(MEDIA_DOMAIN) || host.equalsIgnoreCase(hostOf(fromUrl)) || !sEngineCheck.isOkHttp()) {
            return;
        }

        long nowMs = SystemClock.elapsedRealtime();

        synchronized (SabrHostPrewarmer.class) {
            if (host.equalsIgnoreCase(sLastHost) && nowMs - sLastMs < SAME_HOST_MS) {
                return;
            }

            sLastHost = host;
            sLastMs = nowMs;
        }

        sRequester.request("https://" + host + PATH);
    }

    @Nullable
    @VisibleForTesting
    static String hostOf(@Nullable String url) {
        if (url == null) {
            return null;
        }

        try {
            return Uri.parse(url).getHost();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isOkHttpEngine() {
        if (sContext == null) {
            return false;
        }

        try {
            String engine = Utils.getEffectiveNetworkEngine(sContext);

            return Utils.NETWORK_ENGINE_OKHTTP.equals(engine) || Utils.NETWORK_ENGINE_OKHTTP_NO_IPV6.equals(engine);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void request(String url) {
        // The pool and the DNS of the player's client, and its listener: the connect is timed as the player's (see Connections)
        OkHttpClient client = OkHttpManager.instance().getClient().newBuilder()
                .eventListenerFactory(Connections.LISTENER)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", DefaultHeaders.APP_USER_AGENT)
                .tag(Connections.Prewarm.class, Connections.Prewarm.INSTANCE)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d(TAG, "Can't connect ahead to %s: %s", call.request().url().host(), e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                // Read to its end (it's empty): the connection goes back to the pool
                response.close();
            }
        });
    }

    @VisibleForTesting
    static void resetForTesting() {
        synchronized (SabrHostPrewarmer.class) {
            sLastHost = null;
            sLastMs = 0;
        }

        sRequester = SabrHostPrewarmer::request;
        sEngineCheck = SabrHostPrewarmer::isOkHttpEngine;
    }
}
