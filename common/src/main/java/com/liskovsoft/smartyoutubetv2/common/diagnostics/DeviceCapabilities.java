package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.hardware.display.DisplayManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.media.MediaRouter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build.VERSION;
import android.view.Display;

import androidx.annotation.Nullable;
import androidx.webkit.WebViewCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioCapabilities;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * What the device can play and output, for the report (see DiagnosticsReport): its decoders, its audio output, its display, its
 * memory, its WebView, its network and its time zone. No names of the devices connected to it. A part that can't be read is
 * {"error": "…"}, the rest is still sent.
 */
public final class DeviceCapabilities {
    private static final String[] VIDEO_MIME_TYPES = {
            "video/x-vnd.on2.vp9", "video/av01", "video/avc", "video/hevc", "video/dolby-vision"
    };
    private static final String[] AUDIO_MIME_TYPES = {
            "audio/opus", "audio/mp4a-latm", "audio/ac3", "audio/eac3", "audio/eac3-joc", "audio/vnd.dts", "audio/vnd.dts.hd"
    };
    /**
     * The sizes and rates YouTube serves, checked against each video decoder
     */
    private static final int[][] VIDEO_CHECKS = {
            {3840, 2160, 60}, {3840, 2160, 30}, {2560, 1440, 60}, {1920, 1080, 60}, {1920, 1080, 30}, {1280, 720, 60}
    };

    private DeviceCapabilities() {
    }

    /**
     * Each part on its own, not on the main thread (the decoders are listed)
     */
    public static JSONObject build(Context context) {
        JSONObject result = new JSONObject();

        put(result, "decoders", DeviceCapabilities::buildDecoders, context);
        put(result, "audioOutput", DeviceCapabilities::buildAudioOutput, context);
        put(result, "display", DeviceCapabilities::buildDisplay, context);
        put(result, "memory", DeviceCapabilities::buildMemory, context);
        put(result, "network", ctx -> {
            JSONObject network = new JSONObject();
            network.put("transport", nullable(getNetworkTransport(ctx)));
            return network;
        }, context);
        put(result, "webView", ctx -> {
            JSONObject webView = new JSONObject();
            webView.put("version", nullable(getWebViewVersion(ctx)));
            return webView;
        }, context);
        // Its offset only: the zone's name (e.g. America/Los_Angeles) tells where the user lives
        put(result, "timeZone", ctx -> {
            JSONObject json = new JSONObject();
            json.put("utcOffsetMinutes", TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000);
            return json;
        }, context);

        return result;
    }

    /**
     * Where the player is at, for a failure: the memory left, the network and the display mode
     */
    public static JSONObject buildState(Context context, long afrSwitchTimeMs) {
        JSONObject result = new JSONObject();

        try {
            Runtime runtime = Runtime.getRuntime();
            result.put("heapUsedMb", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
            result.put("heapMaxMb", runtime.maxMemory() / (1024 * 1024));

            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

            if (activityManager != null) {
                ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(memoryInfo);
                result.put("availMemMb", memoryInfo.availMem / (1024 * 1024));
                result.put("lowMemory", memoryInfo.lowMemory);
            }

            result.put("network", nullable(getNetworkTransport(context)));

            Display display = getDisplay(context);
            result.put("displayMode", display != null ? describeMode(display.getMode()) : JSONObject.NULL);
            // Auto frame rate switched the display mode this long ago (a switch can blank the picture or drop the sound)
            result.put("displayModeSwitchMsAgo", afrSwitchTimeMs > 0 ? System.currentTimeMillis() - afrSwitchTimeMs : JSONObject.NULL);
        } catch (JSONException | RuntimeException e) {
            putError(result, e);
        }

        return result;
    }

    /**
     * By MIME type: each decoder, whether it's hardware, and what it can play
     */
    private static JSONObject buildDecoders(Context context) throws JSONException {
        JSONObject result = new JSONObject();
        MediaCodecInfo[] codecs = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();

        for (String mimeType : VIDEO_MIME_TYPES) {
            result.put(mimeType, buildDecoders(codecs, mimeType, true));
        }

        for (String mimeType : AUDIO_MIME_TYPES) {
            result.put(mimeType, buildDecoders(codecs, mimeType, false));
        }

        return result;
    }

    private static JSONArray buildDecoders(MediaCodecInfo[] codecs, String mimeType, boolean isVideo) throws JSONException {
        JSONArray result = new JSONArray();

        for (MediaCodecInfo codec : codecs) {
            if (codec.isEncoder() || !supportsType(codec, mimeType)) {
                continue;
            }

            JSONObject decoder = new JSONObject();
            decoder.put("name", codec.getName());
            decoder.put("hardware", isHardware(codec));

            try {
                CodecCapabilities capabilities = codec.getCapabilitiesForType(mimeType);
                decoder.put("secure", capabilities.isFeatureSupported(CodecCapabilities.FEATURE_SecurePlayback));
                decoder.put("tunneling", capabilities.isFeatureSupported(CodecCapabilities.FEATURE_TunneledPlayback));

                if (isVideo && capabilities.getVideoCapabilities() != null) {
                    VideoCapabilities video = capabilities.getVideoCapabilities();
                    decoder.put("maxSize", video.getSupportedWidths().getUpper() + "x" + video.getSupportedHeights().getUpper());
                    JSONObject supports = new JSONObject();

                    for (int[] check : VIDEO_CHECKS) {
                        supports.put(Math.min(check[0], check[1]) + "p" + check[2],
                                video.areSizeAndRateSupported(check[0], check[1], check[2]));
                    }

                    decoder.put("supports", supports);
                } else if (!isVideo && capabilities.getAudioCapabilities() != null) {
                    decoder.put("maxChannels", capabilities.getAudioCapabilities().getMaxInputChannelCount());
                }
            } catch (IllegalArgumentException e) {
                // Listed for the type, but its capabilities can't be read
                decoder.put("error", e.getClass().getSimpleName());
            }

            result.put(decoder);
        }

        return result;
    }

    private static boolean supportsType(MediaCodecInfo codec, String mimeType) {
        for (String type : codec.getSupportedTypes()) {
            if (type.equalsIgnoreCase(mimeType)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Android 10 says, before it the name does (the software ones of Android and of the vendors)
     */
    private static boolean isHardware(MediaCodecInfo codec) {
        if (VERSION.SDK_INT >= 29) {
            return codec.isHardwareAccelerated();
        }

        String name = codec.getName().toLowerCase(Locale.ROOT);

        return !(name.startsWith("omx.google.") || name.startsWith("c2.android.") || name.startsWith("omx.ffmpeg.")
                || name.contains(".sw.") || name.endsWith(".sw"));
    }

    /**
     * The encodings the TV or receiver takes as they are (passthrough), the channels, and the outputs by type
     */
    private static JSONObject buildAudioOutput(Context context) throws JSONException {
        JSONObject result = new JSONObject();
        AudioCapabilities capabilities = AudioCapabilities.getCapabilities(context);

        JSONObject passthrough = new JSONObject();
        passthrough.put("ac3", capabilities.supportsEncoding(C.ENCODING_AC3));
        passthrough.put("eac3", capabilities.supportsEncoding(C.ENCODING_E_AC3));
        passthrough.put("eac3Joc", capabilities.supportsEncoding(C.ENCODING_E_AC3_JOC));
        passthrough.put("ac4", capabilities.supportsEncoding(C.ENCODING_AC4));
        passthrough.put("dts", capabilities.supportsEncoding(C.ENCODING_DTS));
        passthrough.put("dtsHd", capabilities.supportsEncoding(C.ENCODING_DTS_HD));
        passthrough.put("trueHd", capabilities.supportsEncoding(C.ENCODING_DOLBY_TRUEHD));
        result.put("passthrough", passthrough);
        result.put("maxChannels", capabilities.getMaxChannelCount());

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        if (audioManager == null) {
            return result;
        }

        JSONArray outputs = new JSONArray();

        for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            outputs.put(describeDevice(device));
        }

        result.put("outputs", outputs);

        // Where the sound of a video goes now, by kind (not its name)
        MediaRouter mediaRouter = (MediaRouter) context.getSystemService(Context.MEDIA_ROUTER_SERVICE);
        MediaRouter.RouteInfo route = mediaRouter != null ? mediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO) : null;
        result.put("currentRoute", route != null ? getRouteTypeName(route.getDeviceType()) : JSONObject.NULL);

        return result;
    }

    private static String getRouteTypeName(int deviceType) {
        switch (deviceType) {
            case MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH:
                return "bluetooth";
            case MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER:
                return "speaker";
            case MediaRouter.RouteInfo.DEVICE_TYPE_TV:
                return "tv";
            default:
                return "unknown";
        }
    }

    /**
     * Its type, channels and encodings: not its name (e.g. the model of a soundbar or headphones)
     */
    private static JSONObject describeDevice(AudioDeviceInfo device) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("type", getDeviceTypeName(device.getType()));

        JSONArray channels = new JSONArray();
        for (int count : device.getChannelCounts()) {
            channels.put(count);
        }
        result.put("channelCounts", channels);

        JSONArray encodings = new JSONArray();
        for (int encoding : device.getEncodings()) {
            encodings.put(getEncodingName(encoding));
        }
        result.put("encodings", encodings);

        return result;
    }

    private static String getDeviceTypeName(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_HDMI:
                return "hdmi";
            case AudioDeviceInfo.TYPE_HDMI_ARC:
                return "hdmi_arc";
            case 29: // AudioDeviceInfo.TYPE_HDMI_EARC, API 31
                return "hdmi_earc";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                return "bluetooth_a2dp";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return "bluetooth_sco";
            case 26: // AudioDeviceInfo.TYPE_BLE_HEADSET, API 31
            case 27: // AudioDeviceInfo.TYPE_BLE_SPEAKER, API 31
                return "bluetooth_le";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return "builtin_speaker";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return "usb";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                return "line_digital";
            case AudioDeviceInfo.TYPE_LINE_ANALOG:
                return "line_analog";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return "wired_headphones";
            case AudioDeviceInfo.TYPE_AUX_LINE:
                return "aux_line";
            case AudioDeviceInfo.TYPE_TELEPHONY:
                return "telephony";
            default:
                return "type_" + type;
        }
    }

    private static String getEncodingName(int encoding) {
        switch (encoding) {
            case C.ENCODING_PCM_16BIT:
                return "pcm_16bit";
            case C.ENCODING_PCM_FLOAT:
                return "pcm_float";
            case C.ENCODING_AC3:
                return "ac3";
            case C.ENCODING_E_AC3:
                return "eac3";
            case C.ENCODING_E_AC3_JOC:
                return "eac3_joc";
            case C.ENCODING_AC4:
                return "ac4";
            case C.ENCODING_DTS:
                return "dts";
            case C.ENCODING_DTS_HD:
                return "dts_hd";
            case C.ENCODING_DOLBY_TRUEHD:
                return "truehd";
            default:
                return "encoding_" + encoding;
        }
    }

    /**
     * The mode in use and the ones it can switch to (see AutoFrameRateController), the HDR it shows, its density
     */
    private static JSONObject buildDisplay(Context context) throws JSONException {
        JSONObject result = new JSONObject();
        Display display = getDisplay(context);

        if (display == null) {
            return result;
        }

        result.put("mode", describeMode(display.getMode()));

        Set<String> modes = new LinkedHashSet<>();
        for (Display.Mode mode : display.getSupportedModes()) {
            modes.add(describeMode(mode));
        }
        result.put("supportedModes", new JSONArray(modes));

        JSONArray hdr = new JSONArray();
        Display.HdrCapabilities hdrCapabilities = display.getHdrCapabilities();

        if (hdrCapabilities != null) {
            for (int type : hdrCapabilities.getSupportedHdrTypes()) {
                hdr.put(getHdrTypeName(type));
            }
        }

        result.put("hdr", hdr);
        result.put("densityDpi", context.getResources().getDisplayMetrics().densityDpi);

        return result;
    }

    private static String getHdrTypeName(int type) {
        switch (type) {
            case Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION:
                return "dolby_vision";
            case Display.HdrCapabilities.HDR_TYPE_HDR10:
                return "hdr10";
            case Display.HdrCapabilities.HDR_TYPE_HLG:
                return "hlg";
            case 4: // Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS, API 29
                return "hdr10_plus";
            default:
                return "type_" + type;
        }
    }

    /**
     * E.g. 3840x2160@59.94
     */
    private static String describeMode(Display.Mode mode) {
        return mode.getPhysicalWidth() + "x" + mode.getPhysicalHeight() + "@" + String.format(Locale.US, "%.2f", mode.getRefreshRate());
    }

    @Nullable
    private static Display getDisplay(Context context) {
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);

        return displayManager != null ? displayManager.getDisplay(Display.DEFAULT_DISPLAY) : null;
    }

    private static JSONObject buildMemory(Context context) throws JSONException {
        JSONObject result = new JSONObject();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        if (activityManager != null) {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            result.put("lowRamDevice", activityManager.isLowRamDevice());
            result.put("memoryClassMb", activityManager.getMemoryClass());
            result.put("largeMemoryClassMb", activityManager.getLargeMemoryClass());
            result.put("totalMemMb", memoryInfo.totalMem / (1024 * 1024));
            result.put("availMemMb", memoryInfo.availMem / (1024 * 1024));
            result.put("lowMemory", memoryInfo.lowMemory);
        }

        result.put("heapMaxMb", Runtime.getRuntime().maxMemory() / (1024 * 1024));

        return result;
    }

    /**
     * Ethernet, wifi, cellular or none: the one the app's requests go over
     */
    @Nullable
    private static String getNetworkTransport(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return null;
        }

        Network network = connectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = network != null ? connectivityManager.getNetworkCapabilities(network) : null;

        if (capabilities == null) {
            return "none";
        }

        String transport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "ethernet" :
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "wifi" :
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "cellular" : "other";

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? transport + "+vpn" : transport;
    }

    /**
     * Its package's version: the PoToken and Botguard checks run in it
     */
    @Nullable
    private static String getWebViewVersion(Context context) {
        PackageInfo webView = WebViewCompat.getCurrentWebViewPackage(context);

        return webView != null ? webView.packageName + " " + webView.versionName : null;
    }

    private interface Part {
        Object build(Context context) throws JSONException;
    }

    private static void put(JSONObject result, String name, Part part, Context context) {
        try {
            result.put(name, part.build(context));
        } catch (JSONException | RuntimeException e) {
            JSONObject error = new JSONObject();
            putError(error, e);

            try {
                result.put(name, error);
            } catch (JSONException ignored) {
                // A name and an object
            }
        }
    }

    private static void putError(JSONObject json, Exception e) {
        try {
            json.put("error", e.getClass().getSimpleName() + ": " + PlayerFailure.scrub(e.getMessage()));
        } catch (JSONException ignored) {
            // A name and a string
        }
    }

    private static Object nullable(@Nullable Object value) {
        return value != null ? value : JSONObject.NULL;
    }
}
