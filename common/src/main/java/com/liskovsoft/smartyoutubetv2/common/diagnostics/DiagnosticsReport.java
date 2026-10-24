package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.liskovsoft.googlecommon.common.locale.LocaleManager;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.smartyoutubetv2.common.BuildConfig;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.DebuggingData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.NetworkData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * What Settings → Debugging → Submit data sends (schema 1, see openapi.yaml of ElectricEelDataRestAPI): the app's settings, the
 * device and the recorded player failures. No account names, and of the proxy only whether it's on.
 */
public final class DiagnosticsReport {
    public static final int SCHEMA = 1;

    private DiagnosticsReport() {
    }

    /**
     * Not on the main thread (the device's decoders are listed)
     *
     * @param failures as read from the log (see PlayerFailureLog#readAll)
     */
    public static JSONObject build(Context context, List<JSONObject> failures) {
        MediaServiceManager service = MediaServiceManager.instance();
        List<Account> accounts = service.getAccounts();

        return build(context, service.getSelectedAccount(), accounts != null ? accounts.size() : 0, failures, System.currentTimeMillis());
    }

    @VisibleForTesting
    static JSONObject build(Context context, @Nullable Account selectedAccount, int signedInAccounts, List<JSONObject> failures,
                            long nowMs) {
        Scrubber.update(context);
        DebuggingData debuggingData = DebuggingData.instance(context);
        JSONObject result = new JSONObject();

        try {
            result.put("schema", SCHEMA);
            result.put("submittedAt", formatUtc(nowMs));

            JSONObject app = new JSONObject();
            app.put("package", context.getPackageName());
            String versionName = AppInfoHelpers.getAppVersionName(context);
            // Required by the server
            app.put("versionName", versionName != null && !versionName.isEmpty() ? versionName : "unknown");
            app.put("versionCode", AppInfoHelpers.getAppVersionCode(context));
            app.put("flavor", BuildConfig.FLAVOR);
            app.put("buildType", BuildConfig.BUILD_TYPE);
            app.put("settings", buildSettings(context));

            JSONObject accountsJson = new JSONObject();
            accountsJson.put("signedIn", signedInAccounts);
            accountsJson.put("active", debuggingData.getAccountNumber(selectedAccount));
            app.put("accounts", accountsJson);
            result.put("app", app);

            result.put("system", buildSystem());
            result.put("device", DeviceCapabilities.build(context));

            boolean includeVideoIds = debuggingData.isVideoIdsIncluded();
            JSONArray failuresJson = new JSONArray();

            for (JSONObject failure : failures) {
                if (!includeVideoIds) {
                    failure.remove("videoId");
                }

                failuresJson.put(failure);
            }

            result.put("playerFailures", failuresJson);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }

        try {
            // How the formats played, failures or not: the baseline the failures are compared with
            result.put("playbackStats", PlayerFailureLog.instance(context).readStats());
        } catch (JSONException | RuntimeException e) {
            // Sent without them
        }

        return result;
    }

    /**
     * The account in use's values, where they're per account
     */
    private static JSONObject buildSettings(Context context) throws JSONException {
        PlayerTweaksData tweaksData = PlayerTweaksData.instance(context);
        MediaServiceData serviceData = MediaServiceData.instance();
        JSONObject settings = new JSONObject();

        JSONObject engine = new JSONObject();
        engine.put("selected", Utils.getNetworkEngineId(tweaksData.getPlayerDataSource()));
        engine.put("effective", Utils.getEffectiveNetworkEngine(context));
        settings.put("networkEngine", engine);

        JSONObject dns = new JSONObject();
        dns.put("preferredType", getDnsTypeName(tweaksData.getPreferredDnsType()));
        settings.put("dns", dns);

        JSONObject network = new JSONObject();
        network.put("webProxyEnabled", GeneralData.instance(context).isProxyEnabled());
        network.put("conscryptEnabled", NetworkData.instance(context).isConscryptEnabled());
        network.put("ipv6TestEnabled", tweaksData.isIpv6TestEnabled());
        network.put("ipv6Disabled", OkHttpManager.isIpv6Disabled());
        settings.put("network", network);

        PlayerData playerData = PlayerData.instance(context);

        JSONObject video = new JSONObject();
        video.put("swDecoderForced", tweaksData.isSWDecoderForced());
        video.put("legacyCodecsForced", playerData.isLegacyCodecsForced());
        video.put("tunneledPlayback", tweaksData.isTunneledPlaybackEnabled());
        video.put("avcOverVp9", tweaksData.isAvcOverVp9Preferred());
        video.put("amlogicFix", tweaksData.isAmlogicFixEnabled());
        video.put("sonyFrameDropFix", tweaksData.isSonyFrameDropFixEnabled());
        video.put("highBitrateFormats", tweaksData.isHighBitrateFormatsEnabled());
        video.put("altPresets", tweaksData.isAltPresetsEnabled());
        video.put("bufferType", getBufferTypeName(playerData.getVideoBufferType()));
        video.put("networkErrorFixingDisabled", tweaksData.isNetworkErrorFixingDisabled());
        settings.put("video", video);

        JSONObject audio = new JSONObject();
        audio.put("delayEnabled", playerData.isAudioDelayEnabled());
        audio.put("delayMs", playerData.getAudioDelayMs());
        audio.put("timeStretching", tweaksData.isAudioTimeStretchingEnabled());
        audio.put("syncFix", tweaksData.isAudioSyncFixEnabled());
        audio.put("unsafeFormats", tweaksData.isUnsafeAudioFormatsEnabled());
        audio.put("volume", playerData.getPlayerVolume());
        audio.put("autoVolume", tweaksData.isPlayerAutoVolumeEnabled());
        audio.put("audioFocus", tweaksData.isAudioFocusEnabled());
        audio.put("language", nullable(emptyToNull(playerData.getAudioLanguage())));
        settings.put("audio", audio);

        JSONObject autoFrameRate = new JSONObject();
        autoFrameRate.put("enabled", playerData.isAfrEnabled());
        autoFrameRate.put("fpsCorrection", playerData.isAfrFpsCorrectionEnabled());
        autoFrameRate.put("resolutionSwitch", playerData.isAfrResSwitchEnabled());
        autoFrameRate.put("pauseMs", playerData.getAfrPauseMs());
        settings.put("autoFrameRate", autoFrameRate);

        settings.put("youtubeClientsOrder", new JSONArray(serviceData.getClientOrder()));
        settings.put("currentClient", YouTubeServiceManager.getCurrentClientName());
        // The ones YouTube is asked for: chosen in the app, else the system's
        LocaleManager localeManager = LocaleManager.instance();
        settings.put("language", nullable(emptyToNull(localeManager.getLanguage())));
        settings.put("country", nullable(emptyToNull(localeManager.getCountry())));

        return settings;
    }

    private static JSONObject buildSystem() throws JSONException {
        JSONObject system = new JSONObject();

        system.put("androidVersion", VERSION.RELEASE);
        system.put("sdkInt", VERSION.SDK_INT);
        system.put("make", Build.MANUFACTURER);
        system.put("brand", Build.BRAND);
        system.put("model", Build.MODEL);
        system.put("modelNumber", Build.DEVICE);
        system.put("product", Build.PRODUCT);
        system.put("hardware", Build.HARDWARE);
        system.put("sku", VERSION.SDK_INT >= 31 ? nullable(Build.SKU) : JSONObject.NULL);

        return system;
    }

    /**
     * The settings of the playback in use, for a failure: the ones a failure depends on most (see PlayerFailure#create)
     */
    static JSONObject buildPlaybackSettings(Context context) throws JSONException {
        PlayerTweaksData tweaksData = PlayerTweaksData.instance(context);
        PlayerData playerData = PlayerData.instance(context);
        JSONObject result = new JSONObject();

        result.put("dns", getDnsTypeName(tweaksData.getPreferredDnsType()));
        result.put("ipv6Disabled", OkHttpManager.isIpv6Disabled());
        result.put("tunneledPlayback", tweaksData.isTunneledPlaybackEnabled());
        result.put("swDecoderForced", tweaksData.isSWDecoderForced());
        result.put("legacyCodecsForced", playerData.isLegacyCodecsForced());
        result.put("avcOverVp9", tweaksData.isAvcOverVp9Preferred());
        result.put("highBitrateFormats", tweaksData.isHighBitrateFormatsEnabled());
        result.put("bufferType", getBufferTypeName(playerData.getVideoBufferType()));
        result.put("autoFrameRate", playerData.isAfrEnabled());
        result.put("audioDelayMs", playerData.isAudioDelayEnabled() ? playerData.getAudioDelayMs() : 0);
        result.put("audioTimeStretching", tweaksData.isAudioTimeStretchingEnabled());
        result.put("audioSyncFix", tweaksData.isAudioSyncFixEnabled());

        return result;
    }

    private static String getBufferTypeName(int bufferType) {
        switch (bufferType) {
            case PlayerConstants.BUFFER_LOW:
                return "low";
            case PlayerConstants.BUFFER_HIGH:
                return "high";
            case PlayerConstants.BUFFER_HIGHEST:
                return "highest";
            default:
                return "medium";
        }
    }

    private static String getDnsTypeName(int dnsType) {
        switch (dnsType) {
            case PlayerTweaksData.DNS_TYPE_IPV4:
                return "ipv4";
            case PlayerTweaksData.DNS_TYPE_GOOGLE:
                return "google";
            default:
                return "system";
        }
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static Object nullable(@Nullable Object value) {
        return value != null ? value : JSONObject.NULL;
    }

    private static String formatUtc(long ms) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));

        return format.format(new Date(ms));
    }
}
