package com.liskovsoft.smartyoutubetv2.common.storage;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * electriceel-storage's API (/api/v1). Its own client: no proxy and the system's DNS, as the server is the user's own (often
 * on the LAN, by an address or a .local name the app's DNS over HTTPS can't resolve), and none of the YouTube client's
 * headers or cookies.
 */
final class StorageClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    static final String API_PATH = "/api/v1";
    /**
     * A pull waits up to this long for a change
     */
    static final int WAIT_SEC = 25;
    private static final OkHttpClient sClient = new OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .dns(Dns.SYSTEM)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(WAIT_SEC + 15, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    /**
     * The token isn't (or no longer) valid: revoked, its group deleted, or another server at the address
     */
    static final class DeviceUnknownException extends IOException {
        DeviceUnknownException(String message) {
            super(message);
        }
    }

    /**
     * Refused by the server, with its HTTP status
     */
    static final class HttpException extends IOException {
        final int code;

        HttpException(int code, String message) {
            super("HTTP " + code + (message != null ? ": " + message : ""));
            this.code = code;
        }
    }

    static final class ServerInfo {
        String name;
        String instanceId;
        String version;
        @Nullable
        String publicUrl;
    }

    static final class Pairing {
        String id;
        String secret;
        String code;
        long expiresAt;
    }

    static final class PairingStatus {
        /**
         * pending, approved, denied, expired
         */
        String status;
        String deviceId;
        String token;
        String groupId;
        String groupName;
    }

    static final class DeviceResponse {
        String deviceName;
        String groupId;
        String groupName;
        boolean needsManifest;
    }

    static final class Change {
        String scope;
        String key;
        @Nullable
        String json; // null: deleted
        long revision;
    }

    static final class PullResult {
        final List<Change> changes = new ArrayList<>();
        final Map<String, Long> cursors = new LinkedHashMap<>();
        boolean more;
        /**
         * New each time the group's database is restored from a backup; null from a server that doesn't tell
         */
        @Nullable
        String databaseId;
    }

    static final class PushChange {
        final String scope;
        final String key;
        final String json;
        final long baseRevision;
        final long changedAt;
        /**
         * Never had from the server: the group's value wins if it has one (see the server's Change.IfAbsent)
         */
        final boolean ifAbsent;

        PushChange(String scope, String key, String json, long baseRevision, long changedAt, boolean ifAbsent) {
            this.scope = scope;
            this.key = key;
            this.json = json;
            this.baseRevision = baseRevision;
            this.changedAt = changedAt;
            this.ifAbsent = ifAbsent;
        }
    }

    static final class PushResult {
        String scope;
        String key;
        /**
         * applied, unchanged, superseded, rejected
         */
        String status;
        long revision;
        @Nullable
        String json; // the stored value, when superseded
        @Nullable
        String error;
    }

    private final String mBaseUrl;
    @Nullable
    private final String mToken;
    /**
     * The call waiting for changes, to end it early (see cancel)
     */
    @Nullable
    private volatile Call mWaitingCall;

    /**
     * @param serverUrl e.g. http://192.168.1.10:8181
     */
    StorageClient(String serverUrl, @Nullable String token) {
        mBaseUrl = serverUrl + API_PATH;
        mToken = token;
    }

    /**
     * The address as typed: a scheme and the default port are added when missing. Null when it isn't an address.
     */
    @Nullable
    static String normalizeUrl(@Nullable String input) {
        if (input == null) {
            return null;
        }

        String url = input.trim();

        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        if (url.endsWith(API_PATH)) {
            url = url.substring(0, url.length() - API_PATH.length());
        }

        boolean hasScheme = url.startsWith("http://") || url.startsWith("https://");
        HttpUrl parsed = HttpUrl.parse(hasScheme ? url : "http://" + url);

        if (parsed == null) {
            return null;
        }

        if (!hasScheme && !url.matches(".*:\\d+$") && !url.matches("^\\[.*\\]:\\d+$")) {
            // The server's default port: it's plain http by default
            parsed = parsed.newBuilder().port(8181).build();
        }

        String result = parsed.toString();
        return result.endsWith("/") ? result.substring(0, result.length() - 1) : result;
    }

    private JSONObject call(String method, String path, @Nullable String authorization, @Nullable String body) throws IOException {
        Request.Builder builder = new Request.Builder().url(mBaseUrl + path);
        RequestBody requestBody = body != null ? RequestBody.create(body, JSON) : null;

        if (requestBody == null && ("POST".equals(method) || "PUT".equals(method))) {
            requestBody = RequestBody.create("{}", JSON);
        }

        builder.method(method, requestBody);

        if (authorization != null) {
            builder.header("Authorization", authorization);
        }

        Call call = sClient.newCall(builder.build());
        boolean isWaiting = path.startsWith("/sync/pull") || path.startsWith("/pairings/");

        if (isWaiting) {
            mWaitingCall = call;
        }

        try (Response response = call.execute()) {
            ResponseBody responseBody = response.body();
            String text = responseBody != null ? responseBody.string() : "";
            JSONObject json = null;

            if (!text.isEmpty()) {
                try {
                    json = new JSONObject(text);
                } catch (JSONException e) {
                    if (response.isSuccessful()) {
                        throw new IOException("Not electriceel-storage: " + e.getMessage());
                    }
                }
            }

            if (response.code() == 401 && authorization != null && authorization.startsWith("Bearer ")) {
                throw new DeviceUnknownException(json != null ? json.optString("message") : "unauthorized");
            }

            if (!response.isSuccessful()) {
                throw new HttpException(response.code(), json != null ? json.optString("message", null) : null);
            }

            return json != null ? json : new JSONObject();
        } finally {
            if (isWaiting) {
                mWaitingCall = null;
            }
        }
    }

    /**
     * Ends the request waiting for changes (or for the authorization): it fails with an IOException at once
     */
    void cancel() {
        Call call = mWaitingCall;

        if (call != null) {
            call.cancel();
        }
    }

    private String bearer() {
        return "Bearer " + mToken;
    }

    ServerInfo info() throws IOException {
        JSONObject json = call("GET", "/info", null, null);

        if (!"electriceel-storage".equals(json.optString("service"))) {
            throw new IOException("Not an electriceel-storage server");
        }

        ServerInfo info = new ServerInfo();
        info.name = json.optString("name");
        info.instanceId = json.optString("instanceId");
        info.version = json.optString("version");
        info.publicUrl = json.optString("publicUrl", null);
        return info;
    }

    Pairing createPairing(JSONObject device) throws IOException {
        JSONObject json = call("POST", "/pairings", null, device.toString());
        Pairing p = new Pairing();
        p.id = json.optString("pairingId");
        p.secret = json.optString("pairingSecret");
        p.code = json.optString("code");
        p.expiresAt = json.optLong("expiresAt");
        return p;
    }

    PairingStatus pollPairing(String id, String secret, int waitSec) throws IOException {
        PairingStatus st = new PairingStatus();

        try {
            JSONObject json = call("GET", "/pairings/" + id + "?wait=" + waitSec, "Pairing " + secret, null);
            st.status = json.optString("status");
            st.deviceId = json.optString("deviceId", null);
            st.token = json.optString("token", null);
            JSONObject group = json.optJSONObject("group");

            if (group != null) {
                st.groupId = group.optString("id");
                st.groupName = group.optString("name");
            }
        } catch (HttpException e) {
            if (e.code != 404) {
                throw e;
            }

            st.status = "expired";
        }

        return st;
    }

    DeviceResponse putDevice(JSONObject report) throws IOException {
        return deviceResponse(call("PUT", "/device", bearer(), report.toString()));
    }

    private static DeviceResponse deviceResponse(JSONObject json) {
        DeviceResponse r = new DeviceResponse();
        r.deviceName = json.optString("deviceName");
        r.needsManifest = json.optBoolean("needsManifest");
        JSONObject group = json.optJSONObject("group");

        if (group != null) {
            r.groupId = group.optString("id");
            r.groupName = group.optString("name");
        }

        return r;
    }

    void putManifest(String manifest) throws IOException {
        call("PUT", "/manifest", bearer(), manifest);
    }

    void deleteDevice() throws IOException {
        call("DELETE", "/device", bearer(), null);
    }

    PullResult pull(Map<String, Long> cursors, int waitSec) throws IOException {
        try {
            JSONObject body = new JSONObject();
            body.put("cursors", new JSONObject(cursors));
            body.put("wait", waitSec);
            JSONObject json = call("POST", "/sync/pull", bearer(), body.toString());
            PullResult result = new PullResult();
            JSONArray changes = json.optJSONArray("changes");

            if (changes != null) {
                for (int i = 0; i < changes.length(); i++) {
                    JSONObject c = changes.getJSONObject(i);
                    Change change = new Change();
                    change.scope = c.getString("scope");
                    change.key = c.getString("key");
                    change.revision = c.getLong("revision");
                    change.json = c.optBoolean("deleted") || !c.has("value") ? null : jsonText(c.get("value"));
                    result.changes.add(change);
                }
            }

            JSONObject newCursors = json.optJSONObject("cursors");

            if (newCursors != null) {
                for (Iterator<String> it = newCursors.keys(); it.hasNext(); ) {
                    String scope = it.next();
                    result.cursors.put(scope, newCursors.getLong(scope));
                }
            }

            result.more = json.optBoolean("more");
            String databaseId = json.isNull("databaseId") ? null : json.optString("databaseId");
            result.databaseId = databaseId != null && !databaseId.isEmpty() ? databaseId : null;
            return result;
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    List<PushResult> push(List<PushChange> changes) throws IOException {
        try {
            JSONArray list = new JSONArray();

            for (PushChange c : changes) {
                JSONObject item = new JSONObject();
                item.put("scope", c.scope);
                item.put("key", c.key);
                item.put("value", new org.json.JSONTokener(c.json).nextValue());
                item.put("baseRevision", c.baseRevision);
                item.put("changedAt", c.changedAt);

                if (c.ifAbsent) {
                    item.put("ifAbsent", true);
                }

                list.put(item);
            }

            JSONObject body = new JSONObject();
            body.put("sentAt", System.currentTimeMillis());
            body.put("changes", list);
            JSONObject json = call("POST", "/sync/push", bearer(), body.toString());
            List<PushResult> results = new ArrayList<>();
            JSONArray array = json.optJSONArray("results");

            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject r = array.getJSONObject(i);
                    PushResult result = new PushResult();
                    result.scope = r.optString("scope");
                    result.key = r.optString("key");
                    result.status = r.optString("status");
                    result.revision = r.optLong("revision");
                    result.json = r.has("value") ? jsonText(r.get("value")) : null;
                    result.error = r.optString("error", null);
                    results.add(result);
                }
            }

            return results;
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    /**
     * A value parsed by org.json, as JSON text again
     */
    private static String jsonText(Object value) throws JSONException {
        if (value == JSONObject.NULL) {
            return "null";
        }

        if (value instanceof String) {
            return JSONObject.quote((String) value);
        }

        if (value instanceof Number) {
            return JSONObject.numberToString((Number) value);
        }

        return value.toString(); // a boolean, an object, an array
    }
}
