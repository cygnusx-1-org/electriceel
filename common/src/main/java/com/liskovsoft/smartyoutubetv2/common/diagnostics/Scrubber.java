package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Takes out of the text of an error what tells who or where the user is: the query of a URL (a googlevideo one has the user's IP
 * address), IP addresses, email addresses and the host of the proxy set in the app
 */
public final class Scrubber {
    private static final Pattern URL_QUERY = Pattern.compile("(https?://[^\\s?#]+)[?#][^\\s]*");
    private static final Pattern IPV4 = Pattern.compile("(?<![\\d.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\d.])");
    /**
     * A run of hex digits and colons: an IPv6 address when it has "::" or 7 colons (not a time like 12:30:45)
     */
    private static final Pattern IPV6_CANDIDATE = Pattern.compile("(?<![0-9A-Fa-f:])[0-9A-Fa-f:]{2,39}(?:%\\w+)?(?![0-9A-Fa-f:])");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final String IP = "<ip>";
    private static final String PROXY = "<proxy>";
    @Nullable
    private static volatile String sProxyHost;

    private Scrubber() {
    }

    /**
     * The host of the proxy set in the app (see AppPrefs#getWebProxyUri), to leave out wherever it's written
     */
    public static void update(Context context) {
        String uri = AppPrefs.instance(context).getWebProxyUri();
        String host = uri != null && !uri.isEmpty() ? Uri.parse(uri.contains("://") ? uri : "http://" + uri).getHost() : null;
        sProxyHost = host != null && !host.isEmpty() ? host.toLowerCase(Locale.ROOT) : null;
    }

    @VisibleForTesting
    static void setProxyHost(@Nullable String host) {
        sProxyHost = host != null ? host.toLowerCase(Locale.ROOT) : null;
    }

    @Nullable
    public static String scrub(@Nullable String text) {
        if (text == null) {
            return null;
        }

        String result = URL_QUERY.matcher(text).replaceAll("$1?…");

        String proxyHost = sProxyHost;

        if (proxyHost != null) {
            result = Pattern.compile(Pattern.quote(proxyHost), Pattern.CASE_INSENSITIVE).matcher(result).replaceAll(PROXY);
        }

        result = EMAIL.matcher(result).replaceAll("<email>");
        result = IPV4.matcher(result).replaceAll(IP);

        return scrubIpv6(result);
    }

    private static String scrubIpv6(String text) {
        Matcher matcher = IPV6_CANDIDATE.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String candidate = matcher.group();
            String address = candidate.split("%")[0];
            int colons = address.length() - address.replace(":", "").length();
            int groups = 0;

            for (String group : address.split(":")) {
                if (!group.isEmpty()) {
                    groups++;
                }
            }

            // "2001:db8::1", "::1", "fe80::1%eth0" or all 8 groups; not the "d::" of std::string
            boolean isIpv6 = address.contains("::") ? groups >= 2 || (address.startsWith("::") && groups == 1) : colons == 7;
            matcher.appendReplacement(result, Matcher.quoteReplacement(isIpv6 ? IP : candidate));
        }

        matcher.appendTail(result);

        return result.toString();
    }
}
