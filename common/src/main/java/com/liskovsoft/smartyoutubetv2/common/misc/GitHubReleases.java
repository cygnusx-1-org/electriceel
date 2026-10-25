package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.os.SystemClock;

import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import io.reactivex.Observable;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The latest releases of the app on GitHub (Settings → Updates → Release notes), their notes rendered by GitHub itself:
 * the same HTML as the releases page, not markdown to render here.
 */
public final class GitHubReleases {
    /**
     * Of the API: the notes as HTML too (body_html)
     */
    private static final String ACCEPT = "application/vnd.github.html+json";
    private static final String API_VERSION = "2022-11-28";
    /**
     * Back and forth between the list and the notes doesn't ask again: the API allows 60 requests an hour without an account
     */
    private static final long CACHE_MS = 10 * 60 * 1_000;
    private static volatile Cached sCached;

    public static final class Release {
        /**
         * The tag, e.g. 32.68.3
         */
        public final String version;
        /**
         * Of the releases page, e.g. https://github.com/cygnusx-1-org/electriceel/releases/tag/32.68.3
         */
        public final String pageUrl;
        /**
         * The notes as GitHub renders them. Its addresses can be relative to github.com (e.g. the images of the repo).
         */
        public final String bodyHtml;
        /**
         * 0: not published yet
         */
        public final long publishedMs;

        public Release(String version, String pageUrl, String bodyHtml, long publishedMs) {
            this.version = version;
            this.pageUrl = pageUrl;
            this.bodyHtml = bodyHtml;
            this.publishedMs = publishedMs;
        }
    }

    private static final class Cached {
        final List<Release> releases;
        final long timeMs;

        Cached(List<Release> releases, long timeMs) {
            this.releases = releases;
            this.timeMs = timeMs;
        }
    }

    private GitHubReleases() {
    }

    /**
     * The newest first. Asked in the background, once in a while (see CACHE_MS).
     */
    public static Observable<List<Release>> latestObserve(Context context) {
        String url = context.getString(R.string.release_notes_url);

        return RxHelper.fromCallable(() -> {
            Cached cached = sCached;
            // Not the wall clock: set back, it would keep an old list until the app restarts
            long nowMs = SystemClock.elapsedRealtime();

            if (cached != null && nowMs - cached.timeMs < CACHE_MS) {
                return cached.releases;
            }

            List<Release> releases = load(url);
            sCached = new Cached(releases, nowMs);

            return releases;
        });
    }

    private static List<Release> load(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", ACCEPT)
                .header("X-GitHub-Api-Version", API_VERSION)
                .build();

        try (Response response = OkHttpManager.instance().getClient().newCall(request).execute()) {
            ResponseBody body = response.body();

            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }

            return parse(body != null ? body.string() : "");
        }
    }

    /**
     * The drafts are left out: only an account that can edit the repo sees them
     */
    static List<Release> parse(String json) throws IOException {
        try {
            JSONArray items = new JSONArray(json);
            List<Release> releases = new ArrayList<>();

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);

                if (item == null || item.optBoolean("draft")) {
                    continue;
                }

                String version = getString(item, "tag_name");

                if (version.isEmpty()) {
                    continue;
                }

                releases.add(new Release(version, getString(item, "html_url"), getString(item, "body_html"),
                        parseDate(getString(item, "published_at"))));
            }

            return Collections.unmodifiableList(releases);
        } catch (JSONException e) {
            throw new IOException("Invalid response", e);
        }
    }

    /**
     * Empty when missing or null (e.g. the notes of a release without any): optString's is "null"
     */
    private static String getString(JSONObject item, String name) {
        return item.isNull(name) ? "" : item.optString(name, "");
    }

    /**
     * ISO 8601 in UTC, e.g. 2026-10-25T01:14:33Z
     */
    static long parseDate(String date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));

        try {
            return format.parse(date).getTime();
        } catch (ParseException e) {
            return 0;
        }
    }
}
