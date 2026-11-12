package com.google.android.exoplayer2.source.sabr;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * The other hosts a signed googlevideo URL can be asked on, as the YouTube app builds them (21.44), and the media networks that
 * failed lately. Both the SABR and the DASH requests use them. No Android dependency: plain JUnit tests.
 */
public final class GoogleVideoHosts {
    private static final String GOOGLEVIDEO_SUFFIX = ".googlevideo.com";
    private static final String A1_SUFFIX = ".a1" + GOOGLEVIDEO_SUFFIX;
    @VisibleForTesting
    static final String REDIRECTOR_HOST = "redirector" + GOOGLEVIDEO_SUFFIX;
    private static final String VIDEOPLAYBACK_PATH = "/videoplayback";
    /**
     * How long a media network that failed to answer stays skipped. Restarting the engine or reloading the video builds new
     * requests from the same player response, which would otherwise go to the broken network again (e.g. an ISP-hosted cache node
     * that hangs on cache misses).
     */
    private static final long FAILED_NETWORK_TTL_MS = 30 * 60 * 1_000;
    private static final Map<String, Long> sFailedNetworks = new HashMap<>();

    private GoogleVideoHosts() {
    }

    /**
     * The URL on the fallback host the YouTube app switches to: {@code r{fvip}---{mn[1]}.googlevideo.com}, with
     * {@code fallback_count=1}. Null when the URL doesn't name one (no fvip or a single network) or is already on it.
     */
    public static @Nullable String toFallbackUrl(@Nullable String url) {
        URI uri = parse(url);
        String host = uri != null ? uri.getHost() : null;

        if (host == null || !host.endsWith(GOOGLEVIDEO_SUFFIX)) {
            return null;
        }

        String fvip = getQueryParameter(uri.getRawQuery(), "fvip");
        String networks = getQueryParameter(uri.getRawQuery(), "mn");

        if (!isHostLabel(fvip) || networks == null) {
            return null;
        }

        String[] network = networks.split(",");

        if (network.length < 2 || !isHostLabel(network[1])) {
            return null;
        }

        String fallbackHost = "r" + fvip + "---" + network[1] + GOOGLEVIDEO_SUFFIX;

        if (fallbackHost.equalsIgnoreCase(host)) {
            return null;
        }

        String result = replaceHost(url, uri, fallbackHost);

        return result != null ? appendQueryParameter(result, "fallback_count", "1") : null;
    }

    /**
     * The URL on the redirector, which answers with a redirect to a host that works: the YouTube app's last resort. Only for a GET:
     * OkHttp follows a 302 to a POST with a GET, without its body. Null when the URL isn't a media one with its parameters in the
     * query (e.g. a manifest, or a live segment's path-style URL), or is on the redirector already.
     */
    public static @Nullable String toRedirectorUrl(@Nullable String url) {
        URI uri = parse(url);
        String host = uri != null ? uri.getHost() : null;

        if (extractNetwork(host) == null || !VIDEOPLAYBACK_PATH.equals(uri.getRawPath())) {
            return null;
        }

        String result = replaceHost(url, uri, REDIRECTOR_HOST);

        if (result == null) {
            return null;
        }

        result = appendQueryParameter(result, "cmo", "pf=1");

        return host.endsWith(A1_SUFFIX) ? appendQueryParameter(result, "cmo", "td=a1.googlevideo.com") : result;
    }

    /**
     * Whether two URLs ask for the same media on the same host: host, path and signatures as the YouTube app compares them. Other
     * parameters (e.g. the request number or the segment) don't count.
     */
    public static boolean isSameMedia(@Nullable String url, @Nullable String other) {
        URI uri = parse(url);
        URI otherUri = parse(other);

        if (uri == null || otherUri == null) {
            return false;
        }

        return equalsIgnoreCase(uri.getHost(), otherUri.getHost())
                && equals(uri.getRawPath(), otherUri.getRawPath())
                && equals(getQueryParameter(uri.getRawQuery(), "signature"), getQueryParameter(otherUri.getRawQuery(), "signature"))
                && equals(getQueryParameter(uri.getRawQuery(), "sig"), getQueryParameter(otherUri.getRawQuery(), "sig"))
                && equals(getQueryParameter(uri.getRawQuery(), "lsig"), getQueryParameter(otherUri.getRawQuery(), "lsig"));
    }

    public static @Nullable String getHost(@Nullable String url) {
        URI uri = parse(url);

        return uri != null ? uri.getHost() : null;
    }

    /**
     * Skips the media network of this URL's host for a while (see FAILED_NETWORK_TTL_MS)
     */
    public static void markNetworkFailed(@Nullable String url) {
        String network = extractNetwork(getHost(url));

        if (network == null) {
            return;
        }

        synchronized (sFailedNetworks) {
            sFailedNetworks.put(network, nowMs() + FAILED_NETWORK_TTL_MS);
        }
    }

    public static boolean isNetworkFailed(@Nullable String url) {
        String network = extractNetwork(getHost(url));

        if (network == null) {
            return false;
        }

        synchronized (sFailedNetworks) {
            Long expiresAt = sFailedNetworks.get(network);

            if (expiresAt == null) {
                return false;
            }

            if (expiresAt <= nowMs()) {
                sFailedNetworks.remove(network);
                return false;
            }

            return true;
        }
    }

    @VisibleForTesting
    public static void clearFailedNetworks() {
        synchronized (sFailedNetworks) {
            sFailedNetworks.clear();
        }
    }

    private static long nowMs() {
        return System.nanoTime() / 1_000_000; // monotonic, no Android dependency
    }

    /**
     * The media network of a host: sn-4gxb5u-qo3l of rr1---sn-4gxb5u-qo3l.googlevideo.com
     */
    private static @Nullable String extractNetwork(@Nullable String host) {
        if (host == null || !host.endsWith(GOOGLEVIDEO_SUFFIX)) {
            return null;
        }

        int networkStart = host.indexOf("---");
        int networkEnd = host.length() - GOOGLEVIDEO_SUFFIX.length();

        if (networkStart < 0 || networkStart + 3 >= networkEnd) {
            return null;
        }

        return host.substring(networkStart + 3, networkEnd).toLowerCase();
    }

    private static boolean isHostLabel(@Nullable String value) {
        return value != null && value.matches("[A-Za-z0-9-]+");
    }

    private static @Nullable URI parse(@Nullable String url) {
        if (url == null) {
            return null;
        }

        try {
            return URI.create(url);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable String getQueryParameter(@Nullable String rawQuery, String key) {
        if (rawQuery == null) {
            return null;
        }

        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            String rawKey = separator >= 0 ? pair.substring(0, separator) : pair;

            if (key.equals(decode(rawKey))) {
                return decode(separator >= 0 ? pair.substring(separator + 1) : "");
            }
        }

        return null;
    }

    private static String appendQueryParameter(String url, String key, String value) {
        int fragmentStart = url.indexOf('#');
        String base = fragmentStart >= 0 ? url.substring(0, fragmentStart) : url;
        String fragment = fragmentStart >= 0 ? url.substring(fragmentStart) : "";
        String separator = base.indexOf('?') < 0 ? "?" : base.endsWith("?") || base.endsWith("&") ? "" : "&";

        return base + separator + encode(key) + "=" + encode(value) + fragment;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return value;
        }
    }

    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static @Nullable String replaceHost(String url, URI uri, String host) {
        String oldAuthority = uri.getRawAuthority();

        if (oldAuthority == null || uri.getRawUserInfo() != null) {
            return null;
        }

        String newAuthority = uri.getPort() >= 0 ? host + ":" + uri.getPort() : host;
        int authorityStart = url.indexOf("//" + oldAuthority);

        if (authorityStart < 0) {
            return null;
        }

        authorityStart += 2;

        return url.substring(0, authorityStart) + newAuthority + url.substring(authorityStart + oldAuthority.length());
    }

    private static boolean equals(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean equalsIgnoreCase(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }
}
