package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.content.Context;

import com.liskovsoft.googlecommon.common.locale.LocaleManager;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.locale.LocaleUpdater;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.DebuggingData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.NetworkData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DiagnosticsReportTest {
    private static final long NOW = 1_792_000_000_000L;
    private int mSavedEngine;
    private int mSavedDns;
    private boolean mSavedIpv6Test;
    private boolean mSavedProxy;
    private boolean mSavedConscrypt;
    private boolean mSavedVideoIds;
    private String mSavedProxyUri;
    private String mSavedLanguage;
    private String mSavedCountry;

    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        VideoStartLog.useForTesting(mFolder.getRoot());
        PlayerTweaksData tweaks = getTweaks();
        mSavedEngine = tweaks.getPlayerDataSource();
        mSavedDns = tweaks.getPreferredDnsType();
        mSavedIpv6Test = tweaks.isIpv6TestEnabled();
        mSavedProxy = GeneralData.instance(getContext()).isProxyEnabled();
        mSavedConscrypt = NetworkData.instance(getContext()).isConscryptEnabled();
        mSavedVideoIds = getDebuggingData().isVideoIdsIncluded();
        mSavedProxyUri = AppPrefs.instance(getContext()).getWebProxyUri();
        mSavedLanguage = getGlobalPrefs().getPreferredLanguage();
        mSavedCountry = getGlobalPrefs().getPreferredCountry();

        // Cronet can't load under Robolectric
        tweaks.setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP);
    }

    @After
    public void tearDown() {
        VideoStartLog.useForTesting(null);
        PlayerTweaksData tweaks = getTweaks();
        tweaks.setPlayerDataSource(mSavedEngine);
        tweaks.setPreferredDnsType(mSavedDns);
        tweaks.setIpv6TestEnabled(mSavedIpv6Test);
        GeneralData.instance(getContext()).setProxyEnabled(mSavedProxy);
        NetworkData.instance(getContext()).setConscryptEnabled(mSavedConscrypt);
        getDebuggingData().setVideoIdsIncluded(mSavedVideoIds);
        AppPrefs.instance(getContext()).setWebProxyUri(mSavedProxyUri);
        setLocale(mSavedLanguage, mSavedCountry);
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void readsTheSettingsAndTheDevice() throws Exception {
        PlayerTweaksData tweaks = getTweaks();
        tweaks.setPreferredDnsType(PlayerTweaksData.DNS_TYPE_IPV4);
        tweaks.setIpv6TestEnabled(false);
        GeneralData.instance(getContext()).setProxyEnabled(true);
        NetworkData.instance(getContext()).setConscryptEnabled(true);

        JSONObject report = build(null, 0, Collections.emptyList());

        assertEquals(1, report.getInt("schema"));
        assertEquals("2026-10-14T17:46:40Z", report.getString("submittedAt"));

        JSONObject app = report.getJSONObject("app");
        assertEquals(getContext().getPackageName(), app.getString("package"));
        assertTrue(app.has("versionName"));
        assertTrue(app.has("versionCode"));
        assertTrue(app.has("flavor"));
        assertTrue(app.has("buildType"));

        JSONObject settings = app.getJSONObject("settings");
        assertEquals("okhttp", settings.getJSONObject("networkEngine").getString("selected"));
        assertEquals(Utils.getEffectiveNetworkEngine(getContext()), settings.getJSONObject("networkEngine").getString("effective"));
        assertEquals("ipv4", settings.getJSONObject("dns").getString("preferredType"));

        JSONObject network = settings.getJSONObject("network");
        assertTrue(network.getBoolean("webProxyEnabled"));
        assertTrue(network.getBoolean("conscryptEnabled"));
        assertFalse(network.getBoolean("ipv6TestEnabled"));
        assertTrue(network.has("ipv6Disabled"));

        assertEquals(MediaServiceData.instance().getClientOrder(), toList(settings.getJSONArray("youtubeClientsOrder")));
        assertFalse(settings.isNull("currentClient"));

        JSONObject accounts = app.getJSONObject("accounts");
        assertEquals(0, accounts.getInt("signedIn"));
        assertEquals(0, accounts.getInt("active"));

        JSONObject system = report.getJSONObject("system");
        assertEquals(28, system.getInt("sdkInt"));
        for (String field : Arrays.asList("androidVersion", "make", "brand", "model", "modelNumber", "product", "hardware", "sku")) {
            assertTrue(field, system.has(field));
        }

        assertEquals(0, report.getJSONArray("playerFailures").length());

        // What the device can play and output, each part on its own
        JSONObject device = report.getJSONObject("device");
        for (String part : Arrays.asList("decoders", "audioOutput", "display", "memory", "network", "webView", "timeZone")) {
            assertTrue(part, device.has(part));
        }
        assertTrue(device.getJSONObject("decoders").has("video/x-vnd.on2.vp9"));
        assertTrue(device.getJSONObject("audioOutput").getJSONObject("passthrough").has("eac3"));
        assertTrue(device.getJSONObject("memory").has("heapMaxMb"));
        assertTrue(device.getJSONObject("timeZone").has("utcOffsetMinutes"));
        assertFalse(device.getJSONObject("timeZone").has("id"));

        for (String group : Arrays.asList("video", "audio", "autoFrameRate")) {
            assertTrue(group, settings.has(group));
        }
        assertTrue(settings.getJSONObject("video").has("tunneledPlayback"));
        assertTrue(settings.getJSONObject("audio").has("delayMs"));
        assertTrue(settings.getJSONObject("autoFrameRate").has("enabled"));
    }

    @Test
    public void theLanguageAndCountryAreTheOnesInUse() throws Exception {
        setLocale("ru", "US");

        JSONObject settings = build(null, 0, Collections.emptyList()).getJSONObject("app").getJSONObject("settings");

        assertEquals("ru-US", settings.getString("language"));
        assertEquals("US", settings.getString("country"));

        // Not chosen in the app: the system's (Robolectric's en-US)
        setLocale("", "");

        settings = build(null, 0, Collections.emptyList()).getJSONObject("app").getJSONObject("settings");

        assertEquals("en-US", settings.getString("language"));
        assertEquals("US", settings.getString("country"));
    }

    @Test
    public void theCurrentClientIsNamed() throws Exception {
        JSONObject settings = build(null, 0, Collections.emptyList()).getJSONObject("app").getJSONObject("settings");

        assertEquals(YouTubeServiceManager.getCurrentClientName(), settings.getString("currentClient"));
        assertFalse(settings.getString("currentClient").isEmpty());
    }

    @Test
    public void neitherTheProxyNorTheAccountsAreNamed() throws Exception {
        AppPrefs.instance(getContext()).setWebProxyUri("http://proxyuser:pa55word@proxy.example.org:3128");
        GeneralData.instance(getContext()).setProxyEnabled(true);
        Account account = account("Visible Name", "someone@example.com", "Secret_Profile_7");

        String json = build(account, 2, failures("video")).toString();

        for (String secret : Arrays.asList("proxyuser", "pa55word", "proxy.example.org", "Visible Name", "someone", "Secret_Profile_7")) {
            assertFalse(secret + " in " + json, json.contains(secret));
        }
    }

    @Test
    public void videoIdsAreLeftOutWhenNotIncluded() throws Exception {
        VideoStartLog.useForTesting(mFolder.getRoot()).record(new VideoStart(NOW, "slow", VideoStart.TRIGGER_OPEN, true, 5_000,
                "TV", "sabr", false, 2_500, Collections.singletonList(new VideoStart.Stage("buffering", null, 0, 0, 2_500))));
        getDebuggingData().setVideoIdsIncluded(true);
        JSONObject included = build(null, 0, failures("first", "second"));
        JSONArray failures = included.getJSONArray("playerFailures");

        assertEquals(2, failures.length());
        assertEquals("first", failures.getJSONObject(0).getString("videoId"));
        assertEquals("second", failures.getJSONObject(1).getString("videoId"));
        assertEquals("slow", included.getJSONArray("videoStarts").getJSONObject(0).getString("videoId"));

        getDebuggingData().setVideoIdsIncluded(false);
        JSONObject report = build(null, 0, failures("first", "second"));
        failures = report.getJSONArray("playerFailures");

        assertEquals(2, failures.length());
        assertFalse(report.toString(), report.toString().contains("videoId"));
        // The rest of each failure stays, and of each start
        assertEquals("format_error", failures.getJSONObject(0).getString("kind"));
        assertEquals(2_500, report.getJSONArray("videoStarts").getJSONObject(0).getLong("totalMs"));
    }

    @Test
    public void theAccountNumbersStayTheSame() throws Exception {
        Account first = account("A", null, "Profile_A");
        Account second = account("B", null, "Profile_B");

        int firstNumber = getActive(build(first, 2, Collections.emptyList()));
        int secondNumber = getActive(build(second, 2, Collections.emptyList()));

        assertTrue(firstNumber > 0);
        assertTrue(secondNumber > 0);
        assertNotEquals(firstNumber, secondNumber);
        assertEquals(firstNumber, getActive(build(first, 2, Collections.emptyList())));
        assertEquals(secondNumber, getActive(build(second, 2, Collections.emptyList())));
        assertEquals(0, getActive(build(null, 0, Collections.emptyList())));
        assertEquals(2, build(first, 2, Collections.emptyList()).getJSONObject("app").getJSONObject("accounts").getInt("signedIn"));
    }

    /**
     * As LocaleData sets them: the cached locale is read again
     */
    private static void setLocale(String language, String country) {
        getGlobalPrefs().setPreferredLanguage(language);
        getGlobalPrefs().setPreferredCountry(country);
        LocaleUpdater.clearCache();
        LocaleManager.unhold();
    }

    private static JSONObject build(Account selected, int signedIn, List<JSONObject> failures) {
        return DiagnosticsReport.build(getContext(), selected, signedIn, failures, NOW);
    }

    private static int getActive(JSONObject report) throws Exception {
        return report.getJSONObject("app").getJSONObject("accounts").getInt("active");
    }

    /**
     * As read from the log
     */
    private static List<JSONObject> failures(String... videoIds) throws Exception {
        List<JSONObject> result = new ArrayList<>();

        for (String videoId : videoIds) {
            result.add(new JSONObject().put("ts", NOW).put("account", 1).put("kind", "format_error").put("videoId", videoId));
        }

        return result;
    }

    private static List<String> toList(JSONArray array) throws Exception {
        List<String> result = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            result.add(array.getString(i));
        }

        return result;
    }

    private static Account account(String name, String email, String profileName) {
        return (Account) Proxy.newProxyInstance(Account.class.getClassLoader(), new Class<?>[]{Account.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getName":
                    return name;
                case "getEmail":
                    return email;
                case "getProfileName":
                    return profileName;
                case "getId":
                    return profileName.hashCode();
                case "isSelected":
                case "hasChannel":
                    return true;
                case "isEmpty":
                    return false;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return null;
            }
        });
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }

    private static PlayerTweaksData getTweaks() {
        return PlayerTweaksData.instance(getContext());
    }

    private static DebuggingData getDebuggingData() {
        return DebuggingData.instance(getContext());
    }

    private static GlobalPreferences getGlobalPrefs() {
        return GlobalPreferences.instance(getContext());
    }
}
