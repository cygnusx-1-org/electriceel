package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.prefs.DebuggingData;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import io.reactivex.Observable;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Sends the {@link DiagnosticsReport} to the diagnostics server, gzipped, with the app's own network settings (DNS, IPv6, proxy)
 */
public final class DataSubmitter {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /**
     * Refused by the server: why, e.g. "HTTP 413"
     */
    public static final class SubmitException extends IOException {
        public final int code;

        SubmitException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    private static final String TAG = DataSubmitter.class.getSimpleName();

    private DataSubmitter() {
    }

    /**
     * Built and sent in the background. Emits the submission's ID.
     */
    public static Observable<String> submitObserve(Context context) {
        Context appContext = context.getApplicationContext();

        return RxHelper.fromCallable(() -> submit(appContext, false));
    }

    /**
     * What would be sent now, readable (see the Debugging panel's Preview data)
     */
    public static Observable<String> previewObserve(Context context) {
        Context appContext = context.getApplicationContext();

        return RxHelper.fromCallable(() -> {
            JSONObject report = DiagnosticsReport.build(appContext, PlayerFailureLog.instance(appContext).readAll());
            return report.toString(1);
        });
    }

    /**
     * When the app starts, with Settings → Debugging → Submit data on startup on (see AutoSubmitWorker). Turned off: nothing's
     * scheduled anymore.
     */
    public static void autoSubmit(Context context) {
        try {
            AutoSubmitWorker.schedule(context.getApplicationContext());
        } catch (RuntimeException e) {
            Log.e(TAG, "Can't schedule the submission: " + e.getMessage());
        }
    }

    /**
     * One at a time: the startup and the daily submissions (see AutoSubmitWorker) can run together, and the second is to see
     * what the first sent
     *
     * @param onlyIfNew nothing is sent, and null returned, when no failure was recorded since the last submission
     */
    @Nullable
    static synchronized String submit(Context context, boolean onlyIfNew) throws IOException {
        DebuggingData debuggingData = DebuggingData.instance(context);
        List<JSONObject> failures = PlayerFailureLog.instance(context).readAll();
        long newestFailureTs = debuggingData.getLastSubmittedFailureTs();

        for (JSONObject failure : failures) {
            newestFailureTs = Math.max(newestFailureTs, failure.optLong("ts"));
        }

        if (onlyIfNew && newestFailureTs <= debuggingData.getLastSubmittedFailureTs()) {
            return null;
        }

        String id = submit(context.getString(R.string.submit_data_url), DiagnosticsReport.build(context, failures));
        debuggingData.setLastSubmission(id, newestFailureTs, System.currentTimeMillis());

        return id;
    }

    static String submit(String url, JSONObject report) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Content-Encoding", "gzip")
                .post(RequestBody.create(gzip(report.toString()), JSON))
                .build();

        try (Response response = OkHttpManager.instance().getClient().newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body != null ? body.string() : "";

            if (!response.isSuccessful()) {
                throw new SubmitException(response.code(), "HTTP " + response.code() + errorOf(text));
            }

            String id = new JSONObject(text).optString("id", "");

            if (id.isEmpty()) {
                throw new IOException("No ID in the response");
            }

            return id;
        } catch (JSONException e) {
            throw new IOException("Invalid response", e);
        }
    }

    private static String errorOf(String responseText) {
        try {
            String error = new JSONObject(responseText).optString("error", "");
            return error.isEmpty() ? "" : ": " + error;
        } catch (JSONException e) {
            return "";
        }
    }

    static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }

        return bytes.toByteArray();
    }
}
