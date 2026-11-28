package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.ext.cronet.CronetDataSource;
import com.google.android.exoplayer2.source.sabr.SabrRefusedRequest;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.BrokenPartError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.PoTokenError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrProtocolError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrReloadPlayerError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.UnknownPartError;
import com.google.android.exoplayer2.source.sabr.parser.parts.PoTokenStatusSabrPart.PoTokenStatus;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.Loader.UnexpectedLoaderException;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.Connections;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.FormatAttempts;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.LiveHeadWaits;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlaybackSpeed;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.LoadErrors;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlaybackHealth;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlayerFailure;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlayerCommands;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlayerFailureLog;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.ProtectionStatuses;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.VideoDecoderState;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.VideoStarts;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.FrameDropCodecs;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SoundCodecs;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.TunnelingFallback;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.NetworkEngineFallback;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.app.PoTokenGate;
import com.liskovsoft.youtubeapi.videoinfo.V2.ClientAttempts;

import org.chromium.net.NetworkException;
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
import org.robolectric.shadows.ShadowToast;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SSLHandshakeException;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ErrorFixerControllerTest {
    private static final String HOST = "rr1---sn-test.googlevideo.com";
    private static final List<FormatItem> QUALITIES = Arrays.asList(
            ExoFormatItem.fromVideoSpec("3840,2160,30,vp9", false),
            ExoFormatItem.fromVideoSpec("3840,2160,30,avc", false),
            ExoFormatItem.fromVideoSpec("2560,1440,30,vp9", false),
            ExoFormatItem.fromVideoSpec("1280,720,30,vp9", false));
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();
    private PlayerFailureLog mLog;
    private int mSavedEngine;
    private int mSavedDns;
    private boolean mSavedDnsFix;
    private boolean mSavedIpv6Test;

    @Before
    public void setUp() throws Exception {
        mLog = PlayerFailureLog.useForTesting(mFolder.getRoot());
        // Cronet can't load under Robolectric
        mSavedEngine = getTweaks().getPlayerDataSource();
        getTweaks().setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP);
        mSavedDns = getTweaks().getPreferredDnsType();
        mSavedDnsFix = getTweaks().isDnsFixEnabled();
        getTweaks().setDnsFixEnabled(true);
        mSavedIpv6Test = getGlobalPrefs().isIpv6TestEnabled();
        setTvPotRejected(false);
        setIpv6Working(null);
        getPlayerData().setTempVideoFormat(null);
        getPlayerData().setTempAudioFormat(null);
        // No segment loaded or loading
        VideoStarts.onPrepare();
        LoadErrors.resetForTesting();
        Connections.resetForTesting();
        Connections.onPrepare();
        LiveHeadWaits.resetForTesting();
        ProtectionStatuses.resetForTesting(null);
        NetworkEngineFallback.resetForTesting();
        ShadowToast.reset();
    }

    @After
    public void tearDown() throws Exception {
        PlayerFailureLog.useForTesting(null);
        getTweaks().setPlayerDataSource(mSavedEngine);
        getTweaks().setPreferredDnsType(mSavedDns);
        getTweaks().setDnsFixEnabled(mSavedDnsFix);
        getGlobalPrefs().setIpv6TestEnabled(mSavedIpv6Test);
        Utils.sHandler.removeCallbacksAndMessages(null);
        setTvPotRejected(false);
        setIpv6Working(null);
        VideoStarts.onPrepare();
        LoadErrors.resetForTesting();
        Connections.resetForTesting();
        ProtectionStatuses.resetForTesting(null);
        NetworkEngineFallback.resetForTesting();
        // A test's player played at its own speed (see ErrorFixerController#onPlay)
        PlaybackSpeed.onChanged(1f);
    }

    /**
     * A 403 of a stream that never played: the next client right away, then the video again
     */
    @Test
    public void aSourceErrorBeforePlayingRecordsTheClientSwitch() throws Exception {
        TestController controller = new TestController(video("dQw4w9WgXcQ"));

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new IOException("Response code: 403"));

        List<JSONObject> events = mLog.readAll();

        assertEquals(1, events.size());

        JSONObject event = events.get(0);
        assertEquals("engine_error", event.getString("kind"));
        assertEquals("dQw4w9WgXcQ", event.getString("videoId"));
        assertEquals(false, event.getBoolean("startedPlaying"));
        assertEquals("SOURCE", event.getJSONObject("error").getString("type"));
        assertEquals(-1, event.getJSONObject("error").getInt("rendererIndex"));
        assertEquals("IOException", event.getJSONObject("error").getString("class"));
        assertEquals("Response code: 403", event.getJSONObject("error").getString("message"));
        assertEquals("okhttp", event.getJSONObject("player").getString("engineSelected"));

        JSONArray actions = event.getJSONArray("actions");
        assertEquals(2, actions.length());
        assertEquals("switch_next_client_now", actions.getString(0));
        assertEquals("reload_video", actions.getString(1));
    }

    /**
     * The stream's formats came from WEB because TV refused the video: its error says why it's WEB's (EENAQFZF: signed out, autoplay
     * went on WEB after ten videos on TV, and nothing in its 217 errors said what TV had answered)
     */
    @Test
    public void aSourceErrorHasTheClientsTriedBeforeTheOneThatGaveTheFormats() throws Exception {
        ClientAttempts.clear("skippedTv");
        ClientAttempts.record("skippedTv", ClientAttempts.Attempt.unplayable("TV", "LOGIN_REQUIRED", "Sign in to confirm you're not a bot"));
        ClientAttempts.record("skippedTv", ClientAttempts.Attempt.ok("WEB"));
        TestController controller = new TestController(video("skippedTv"));

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new IOException("Response code: 403"));

        JSONArray attempts = mLog.readAll().get(0).getJSONArray("clientAttempts");

        assertEquals(2, attempts.length());
        assertEquals("TV", attempts.getJSONObject(0).getString("client"));
        assertEquals("unplayable", attempts.getJSONObject(0).getString("result"));
        assertEquals("LOGIN_REQUIRED", attempts.getJSONObject(0).getString("status"));
        assertEquals("WEB", attempts.getJSONObject(1).getString("client"));
        assertEquals("ok", attempts.getJSONObject(1).getString("result"));
        ClientAttempts.clear("skippedTv");
    }

    /**
     * The first client tried gave the formats: the player's client says as much, no attempts
     */
    @Test
    public void aSourceErrorOfTheFirstClientTriedHasNoAttempts() throws Exception {
        ClientAttempts.clear("firstTv");
        ClientAttempts.record("firstTv", ClientAttempts.Attempt.ok("TV"));
        TestController controller = new TestController(video("firstTv"));

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new IOException("Response code: 403"));

        assertFalse(mLog.readAll().get(0).has("clientAttempts"));
        ClientAttempts.clear("firstTv");
    }

    /**
     * SABR's read timed out while it played: the same client again (it streams from the same servers), three times a video
     */
    @Test
    public void aReadTimeoutWhilePlayingReloadsWithTheSameClient() throws Exception {
        TestController controller = new TestController(video("eCRhdEua6gE"));
        controller.onPlay();

        for (int i = 0; i < 4; i++) {
            controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                    new UnexpectedLoaderException(new IllegalStateException(new HttpDataSourceException(
                            new SocketTimeoutException("timeout"), dataSpec(), HttpDataSourceException.TYPE_READ))));
        }

        // The same failure again: the first and the last of the repeats
        List<JSONObject> events = mLog.readAll();

        assertEquals(2, events.size());
        assertEquals(Arrays.asList("network_reload 1/3", "reload_video"), getActions(events.get(0)));
        assertEquals(Arrays.asList("switch_next_client:WEB", "reload_video"), getActions(events.get(1)));
    }

    /**
     * SABR's response had bytes no part starts with while it played: broken on the way, not the client's, so the same client
     * again, as for a broken connection
     */
    @Test
    public void aBrokenSabrResponseWhilePlayingReloadsWithTheSameClient() throws Exception {
        TestController controller = new TestController(video("-GLlqOV-qm0"));
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new UnknownPartError("Unknown part encountered: id=1459, size=1131850, position=5")));

        assertEquals(Arrays.asList("network_reload 1/3", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * A part whose payload isn't the protobuf its id says, or whose size can't be skipped: as broken as a part no id starts, not the
     * client's (DMQEWB9B #1 and FVNAAYZD #13, #15 switched the client)
     */
    @Test
    public void aBrokenSabrPartReloadsWithTheSameClient() throws Exception {
        TestController controller = new TestController(video("-GLlqOV-qm0"));
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new BrokenPartError("com.google.protobuf.InvalidProtocolBufferException$InvalidWireTypeException: "
                        + "Protocol message tag had invalid wire type.")));

        assertEquals(Arrays.asList("network_reload 1/3", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * Cronet and the default engine read a SABR response out of step, and OkHttp never did: the video's reload reads with OkHttp, and
     * the engine saved in the settings is the user's. The same video loaded again keeps it, the next goes back to the chosen engine.
     */
    @Test
    public void aBrokenSabrResponseOfAnotherEngineReadsWithOkHttpForThatVideoOnly() throws Exception {
        getTweaks().setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT);
        TestController controller = new TestController(video("-GLlqOV-qm0"));
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new UnknownPartError("Unknown part encountered: id=1459, size=1131850, position=5")));

        assertEquals(Arrays.asList("network_reload 1/3", "engine_okhttp_for_video", "reload_video"), getActions(mLog.readAll().get(0)));
        assertEquals(Utils.NETWORK_ENGINE_OKHTTP, Utils.getEffectiveNetworkEngine(RuntimeEnvironment.getApplication()));
        assertEquals(PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT, getTweaks().getPlayerDataSource());

        controller.onNewVideo(video("-GLlqOV-qm0"));

        assertEquals(Utils.NETWORK_ENGINE_OKHTTP, Utils.getEffectiveNetworkEngine(RuntimeEnvironment.getApplication()));

        controller.onNewVideo(video("dQw4w9WgXcQ"));

        assertEquals(Utils.NETWORK_ENGINE_DEFAULT, Utils.getEffectiveNetworkEngine(RuntimeEnvironment.getApplication()));
    }

    /**
     * OkHttp is in use already: nothing to switch to, and a response broken again says it isn't the engine's
     */
    @Test
    public void aBrokenSabrResponseOfOkHttpIsReloadedAsBefore() throws Exception {
        TestController controller = new TestController(video("-GLlqOV-qm0"));
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new UnknownPartError("Unknown part encountered: id=1459, size=1131850, position=5")));

        assertEquals(Arrays.asList("network_reload 1/3", "reload_video"), getActions(mLog.readAll().get(0)));
        assertEquals(false, NetworkEngineFallback.isOkHttp());
    }

    /**
     * A broken connection (a read timeout) isn't a response read out of step: the engine stays
     */
    @Test
    public void aBrokenConnectionKeepsTheEngine() throws Exception {
        getTweaks().setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT);
        TestController controller = new TestController(video("-GLlqOV-qm0"));
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new IllegalStateException(new HttpDataSourceException(
                        new SocketTimeoutException("timeout"), dataSpec(), HttpDataSourceException.TYPE_READ))));

        assertEquals(Arrays.asList("network_reload 1/3", "reload_video"), getActions(mLog.readAll().get(0)));
        assertEquals(false, NetworkEngineFallback.isOkHttp());
    }

    /**
     * SABR's server asked for the player response again, e.g. its session expired in a night's pause (K3F6GAY6 #15): reloaded without
     * a word, as it plays on. Asked again, it's told.
     */
    @Test
    public void aSabrReloadIsOnlyToldWhenItsAskedAgain() throws Exception {
        TestController controller = new TestController(video("bNAlycU0Xzg"));
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new SabrReloadPlayerError("token")));

        assertEquals(0, ShadowToast.shownToastCount());
        assertEquals(Arrays.asList("sabr_reload 1/3", "reload_video"), getActions(mLog.readAll().get(0)));

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new SabrReloadPlayerError("token")));

        assertEquals(1, ShadowToast.shownToastCount());
        assertEquals("SABR server requested a player response reload. Attempt: 2", ShadowToast.getTextOfLatestToast());
    }

    /**
     * SABR's server refused the request (e.g. sabr.malformed_config: its ustreamer config no longer applies): the player response is
     * fetched again with the same client, without a word. Refused again soon after, even though it played in between: the next
     * client, and it's told.
     */
    @Test
    public void aSabrErrorFetchesThePlayerResponseAgainThenSwitchesTheClient() throws Exception {
        TestController controller = new TestController(video("tJsJJnhQjro"));
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new SabrProtocolError("sabr.malformed_config", 2, SabrProtocolError.UNSET)));

        assertEquals(0, ShadowToast.shownToastCount());

        controller.onPlay();
        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new SabrProtocolError("sabr.malformed_config", 2, SabrProtocolError.UNSET)));

        assertEquals(1, ShadowToast.shownToastCount());
        assertEquals("SABR Protocol Error: sabr.malformed_config, action 2", ShadowToast.getTextOfLatestToast());

        List<JSONObject> events = mLog.readAll();

        assertEquals(2, events.size());
        assertEquals(Arrays.asList("refetch_player", "reload_video"), getActions(events.get(0)));
        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(events.get(1)));
    }

    /**
     * Refused again long after the player response was fetched again: that one played, so it's fetched again rather than the
     * client switched
     */
    @Test
    public void aSabrErrorLongAfterTheLastFetchesThePlayerResponseAgain() throws Exception {
        TestController controller = new TestController(video("tJsJJnhQjro"));
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new SabrProtocolError("sabr.malformed_config", 2, SabrProtocolError.UNSET)));
        org.robolectric.shadows.ShadowSystemClock.advanceBy(4, java.util.concurrent.TimeUnit.MINUTES);
        controller.onPlay();
        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new SabrProtocolError("sabr.malformed_config", 2, SabrProtocolError.UNSET)));

        assertEquals(Arrays.asList("refetch_player", "reload_video"), getActions(mLog.readAll().get(1)));
    }

    /**
     * Paused while it buffered (QYZPW0FN #2: by the remote's media key, 1 s into a restart's buffering): ExoPlayer tells no pause in
     * that state, so the timer ran on. Not a stall, and nothing plays it again.
     */
    @Test
    public void aLongBufferingWhilePausedIsntAStall() throws Exception {
        TestController controller = new TestController(video("p5miU3x4Sr8"), null, playerStub(false));

        controller.onLongBuffering();

        assertEquals(0, mLog.readAll().size());
        assertEquals(0, controller.mReloads.size());
    }

    /**
     * The new source's lookups still going 6 s on, no load error yet (QYZPW0FN #3: 20 s, and the client was switched on a network that
     * was down): the network's, waited for
     */
    @Test
    public void aStartStuckInItsLookupWaitsForTheNetwork() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_GOOGLE);
        TestController controller = new TestController(video("p5miU3x4Sr8"));
        Call call = newCall();
        Connections.LISTENER.create(call).dnsStart(call, HOST);
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofSeconds(6));

        controller.onLongBuffering();

        JSONObject event = mLog.readAll().get(0);
        assertEquals(Arrays.asList("network_wait", "reload_video"), getActions(event));
        assertEquals("DNS 6000 ms", event.getJSONObject("details").getString("stuckConnection"));
    }

    /**
     * A stall switched the client as one that hangs, then the video's formats didn't come for the network (QYZPW0FN #3, #4): the
     * switch was the outage's, the first client again
     */
    @Test
    public void aStallsClientSwitchIsUndoneWhenTheNetworkIsDown() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_GOOGLE);
        TestController controller = new TestController(video("g_jLBnnQuwE"));
        VideoStarts.onLoadStarted(true, true, false, SystemClock.elapsedRealtime());
        VideoStarts.onLoadStarted(true, false, true, SystemClock.elapsedRealtime());
        controller.onLongBuffering();

        controller.runFormatErrorAction(new UnknownHostException("Unable to resolve host \"www.youtube.com\": No address associated with hostname"));

        List<JSONObject> events = mLog.readAll();
        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(events.get(0)));
        assertEquals(Arrays.asList("network_wait", "reset_client", "reload_video"), getActions(events.get(1)));
    }

    /**
     * The network down without a switch before: nothing to undo
     */
    @Test
    public void aNetworkFailureWithNoSwitchBeforeResetsNothing() throws Exception {
        TestController controller = new TestController(video("g_jLBnnQuwE"));

        controller.runFormatErrorAction(new UnknownHostException("Unable to resolve host \"www.youtube.com\": No address associated with hostname"));

        assertEquals(Arrays.asList("network_wait", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * Refused with the player response the stream had just fetched again (89HAAW4J: the server asked for it, the new one was refused):
     * fetched once more, it's refused the same. The next client at once, for this video only, and what was refused is recorded.
     */
    @Test
    public void aSabrErrorWithAFreshSessionMovesTheVideoToTheNextClientAtOnce() throws Exception {
        TestController controller = new TestController(video("t4P-Axqv6GA"), "TV");
        controller.mNextClient = "TV_SAMSUNG";
        SabrProtocolError error = new SabrProtocolError("sabr.no_video_selected", 2, SabrProtocolError.UNSET);
        error.setRequest("init, video 248, audio 140, selected none, session 1", 400);

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(error));

        JSONObject event = mLog.readAll().get(0);
        assertEquals(Arrays.asList("client_for_video:TV_SAMSUNG", "reload_video"), getActions(event));
        assertEquals("init, video 248, audio 140, selected none, session 1", event.getJSONObject("details").getString("sabrRequest"));
        assertEquals(400, event.getJSONObject("details").getLong("sabrSessionAgeMs"));
    }

    /**
     * 0CW972QK: TV played the video between refusals, each one 38-95 ms into a session the stream had just swapped in. Played on the
     * client since the refusal before, it's the stream's: a whole new one on the same client. Refused again before it plays, the client's.
     */
    @Test
    public void aSabrErrorWithAFreshSessionAfterItPlayedStaysOnTheClientOnce() throws Exception {
        TestController controller = new TestController(video("Hf-CqpBeDs0"), "TV");
        controller.mNextClient = "TV_SAMSUNG";
        controller.onPlay();
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofSeconds(15));

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(freshSessionRefusal(62)));
        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(freshSessionRefusal(95)));

        List<JSONObject> events = mLog.readAll();
        assertEquals(Arrays.asList("refetch_player", "reload_video"), getActions(events.get(0)));
        assertTrue(events.get(0).getJSONObject("details").getBoolean("sabrPlayedOnClient"));
        assertEquals(Arrays.asList("client_for_video:TV_SAMSUNG", "reload_video"), getActions(events.get(1)));
    }

    /**
     * Played again in between, each refusal is the stream's again
     */
    @Test
    public void aSabrErrorAfterEachPlayStaysOnTheClient() throws Exception {
        TestController controller = new TestController(video("Hf-CqpBeDs0"), "TV");
        controller.mNextClient = "TV_SAMSUNG";

        for (int i = 0; i < 3; i++) {
            controller.onPlay();
            org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofSeconds(20));
            controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                    new UnexpectedLoaderException(freshSessionRefusal(70)));
        }

        for (JSONObject event : mLog.readAll()) {
            assertEquals(Arrays.asList("refetch_player", "reload_video"), getActions(event));
        }
    }

    /**
     * Refused a moment after it began to play: its first answer only, it's the client's
     */
    @Test
    public void aSabrErrorRightAfterItBeganToPlayMovesTheVideo() throws Exception {
        TestController controller = new TestController(video("Hf-CqpBeDs0"), "TV");
        controller.mNextClient = "TV_SAMSUNG";
        controller.onPlay();
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofSeconds(2));

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(freshSessionRefusal(70)));

        assertEquals(Arrays.asList("client_for_video:TV_SAMSUNG", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    private static SabrProtocolError freshSessionRefusal(long sessionAgeMs) {
        SabrProtocolError error = new SabrProtocolError("sabr.no_video_selected", 2, SabrProtocolError.UNSET);
        error.setRequest("init, video 248, audio 251, selected 251, session 1", sessionAgeMs);
        return error;
    }

    /**
     * The session the stream started with, refused: its player response may be stale, fetched again first as before
     */
    @Test
    public void aSabrErrorWithTheFirstSessionFetchesThePlayerResponseAgain() throws Exception {
        TestController controller = new TestController(video("t4P-Axqv6GA"), "TV");
        controller.mNextClient = "TV_SAMSUNG";
        SabrProtocolError error = new SabrProtocolError("sabr.malformed_config", 2, SabrProtocolError.UNSET);
        error.setRequest("media, video 248, audio 140, selected 248 140, session 0", SabrProtocolError.UNSET);

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(error));

        assertEquals(Arrays.asList("refetch_player", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * Refused again soon after: the video moves to the next client, the order isn't switched for the others
     */
    @Test
    public void aRepeatedSabrErrorMovesTheVideoToTheNextClient() throws Exception {
        TestController controller = new TestController(video("t4P-Axqv6GA"), "TV");
        controller.mNextClient = "TV_SAMSUNG";

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new SabrProtocolError("sabr.no_video_selected", 2, SabrProtocolError.UNSET)));
        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new SabrProtocolError("sabr.no_video_selected", 2, SabrProtocolError.UNSET)));

        assertEquals(Arrays.asList("client_for_video:TV_SAMSUNG", "reload_video"), getActions(mLog.readAll().get(1)));
    }

    /**
     * A broken SABR response says how long after a seek it came (15 of 16 came 52-786 ms after one)
     */
    @Test
    public void aBrokenSabrResponseSaysHowLongAfterASeekItCame() throws Exception {
        TestController controller = new TestController(video("-GLlqOV-qm0"));
        controller.onPlay();
        PlayerCommands.onNewVideo("-GLlqOV-qm0");
        PlayerCommands.onSeek(PlayerCommands.SOURCE_APP, 14_352);
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofMillis(171));

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new UnknownPartError("Unknown part encountered: id=401141735, size=21, position=6")));

        assertEquals(171, mLog.readAll().get(0).getJSONObject("details").getLong("msSinceSeek"));
    }

    /**
     * The start hangs 20 s on the video's first segment while the audio's came: the request is stuck, not the client or the DNS.
     * Reloaded once; stuck again, the client is switched (the DNS is Google's already).
     */
    @Test
    public void aStartStuckOnOneTracksFirstSegmentIsReloadedOnce() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_GOOGLE);
        TestController controller = new TestController(video("p5miU3x4Sr8"));
        loadAudioOnly();

        controller.onLongBuffering();
        loadAudioOnly();
        controller.onLongBuffering();

        List<JSONObject> events = mLog.readAll();

        assertEquals(2, events.size());
        assertEquals(Arrays.asList("stuck_load_reload", "reload_video"), getActions(events.get(0)));
        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(events.get(1)));
    }

    /**
     * Nothing came at all: no track to tell the request is stuck, the fixes as before
     */
    @Test
    public void aStartWithNothingLoadedSwitchesTheClient() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_GOOGLE);
        TestController controller = new TestController(video("g_jLBnnQuwE"));
        VideoStarts.onLoadStarted(true, true, false, SystemClock.elapsedRealtime());
        VideoStarts.onLoadStarted(true, false, true, SystemClock.elapsedRealtime());

        controller.onLongBuffering();

        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * IPv6 doesn't connect: the player uses OkHttp whatever is chosen, and its lookups tell. The DNS is switched, the engine chosen
     * isn't.
     */
    @Test
    public void theDnsFixKeepsTheEngineChosenWhenOkHttpIsInUse() throws Exception {
        getTweaks().setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET);
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_IPV4);
        getGlobalPrefs().setIpv6TestEnabled(true);
        setIpv6Working(false);
        TestController controller = new TestController(video("p5miU3x4Sr8"));
        lookupFails();

        controller.onLongBuffering();

        JSONObject event = mLog.readAll().get(0);
        assertEquals("okhttp_no_ipv6", event.getJSONObject("player").getString("engineEffective"));
        assertEquals(Arrays.asList("dns_google", "restart_engine"), getActions(event));
        assertEquals(PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET, getTweaks().getPlayerDataSource());
        assertEquals(PlayerTweaksData.DNS_TYPE_GOOGLE, getTweaks().getPreferredDnsType());
    }

    /**
     * The player's lookups failed (the formats came, so the network is up), and the DNS isn't Google's: it's switched, and the
     * engine restarted (the DNS is read at each lookup). What it went by is in the failure.
     */
    @Test
    public void aStartThatHangsSwitchesTheDns() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_SYSTEM);
        TestController controller = new TestController(video("p5miU3x4Sr8"));
        lookupFails();

        controller.onLongBuffering();

        JSONObject event = mLog.readAll().get(0);
        assertEquals(Arrays.asList("dns_google", "restart_engine"), getActions(event));
        assertEquals("failing", event.getJSONObject("details").getString("lookup"));
        assertEquals(PlayerTweaksData.DNS_TYPE_GOOGLE, getTweaks().getPreferredDnsType());
    }

    /**
     * A DNS the user chose is switched too while the DNS fix is on (the default): it's put back if Google's doesn't help
     */
    @Test
    public void aChosenDnsIsSwitchedWhileTheDnsFixIsOn() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_CLOUDFLARE);
        TestController controller = new TestController(video("p5miU3x4Sr8"));
        lookupFails();

        controller.onLongBuffering();

        assertEquals(Arrays.asList("dns_google", "restart_engine"), getActions(mLog.readAll().get(0)));
        assertEquals(PlayerTweaksData.DNS_TYPE_GOOGLE, getTweaks().getPreferredDnsType());
    }

    /**
     * The DNS fix turned off in the DNS settings: the DNS chosen stays though the lookups fail (#93). The lookups that fail are the
     * network's, waited for: another client would look the same names up (QYZPW0FN #3).
     */
    @Test
    public void aDnsFixTurnedOffKeepsTheDnsChosen() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_CLOUDFLARE);
        getTweaks().setDnsFixEnabled(false);
        TestController controller = new TestController(video("p5miU3x4Sr8"));
        lookupFails();

        controller.onLongBuffering();

        JSONObject event = mLog.readAll().get(0);
        assertEquals(Arrays.asList("network_wait", "reload_video"), getActions(event));
        assertEquals("failing", event.getJSONObject("details").getString("lookup"));
        assertEquals(PlayerTweaksData.DNS_TYPE_CLOUDFLARE, getTweaks().getPreferredDnsType());
    }

    /**
     * Every address the lookup gave timed out: the DNS may give dead ones, it's switched
     */
    @Test
    public void aStartWhoseAddressesDontAnswerSwitchesTheDns() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_SYSTEM);
        TestController controller = new TestController(video("p5miU3x4Sr8"));
        addressesFail();

        controller.onLongBuffering();

        assertEquals(Arrays.asList("dns_google", "restart_engine"), getActions(mLog.readAll().get(0)));
    }

    /**
     * A connection of the player connected: the lookup gave an address that answers, the hang isn't the DNS's. It isn't touched.
     */
    @Test
    public void aStartWhoseConnectionsWorkKeepsTheDns() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_SYSTEM);
        TestController controller = new TestController(video("p5miU3x4Sr8"));
        addressesFail();
        connectionWorks();

        controller.onLongBuffering();

        JSONObject event = mLog.readAll().get(0);
        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(event));
        assertEquals("working", event.getJSONObject("details").getString("lookup"));
        assertEquals(PlayerTweaksData.DNS_TYPE_SYSTEM, getTweaks().getPreferredDnsType());
    }

    /**
     * No connection of the player told anything (e.g. Cronet's, S4PGCB6X #7): the DNS can't be told to fail, it isn't touched
     */
    @Test
    public void aStartWithNothingToTellKeepsTheDns() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_SYSTEM);
        TestController controller = new TestController(video("p5miU3x4Sr8"));

        controller.onLongBuffering();

        JSONObject event = mLog.readAll().get(0);
        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(event));
        assertEquals("unknown", event.getJSONObject("details").getString("lookup"));
        assertEquals(PlayerTweaksData.DNS_TYPE_SYSTEM, getTweaks().getPreferredDnsType());
    }

    /**
     * The lookups of the source before (e.g. the video before's) aren't this one's
     */
    @Test
    public void theLookupsOfTheSourceBeforeArentThisOnes() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_SYSTEM);
        TestController controller = new TestController(video("p5miU3x4Sr8"));
        lookupFails();
        restarted();

        controller.onLongBuffering();

        assertEquals(PlayerTweaksData.DNS_TYPE_SYSTEM, getTweaks().getPreferredDnsType());
    }

    /**
     * A resumed video that hangs, with nothing to tell the DNS failed: the client is switched, the DNS left as it is
     */
    @Test
    public void aResumedVideoThatHangsSwitchesTheClientNotTheDns() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_SYSTEM);
        TestController controller = new TestController(video("p5miU3x4Sr8"), null, playerStub(null, null, 754_000));

        controller.onLongBuffering();

        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(mLog.readAll().get(0)));
        assertEquals(PlayerTweaksData.DNS_TYPE_SYSTEM, getTweaks().getPreferredDnsType());
    }

    /**
     * A 403 while it played is the client's: switched as before
     */
    @Test
    public void aResponseCodeWhilePlayingSwitchesTheClient() throws Exception {
        TestController controller = new TestController(video("dQw4w9WgXcQ"));
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new InvalidResponseCodeException(403, "Forbidden", Collections.emptyMap(), dataSpec()));

        assertEquals(Arrays.asList("switch_next_client:WEB", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * SABR rejected the TV poToken (e.g. a WebView whose BotGuard makes bad ones): the TV client again, without one. Rejected once
     * more, it has none to go without: the next client.
     */
    @Test
    public void aRejectedTvPoTokenReloadsWithoutIt() throws Exception {
        TestController controller = new TestController(video("dQw4w9WgXcQ"), "TV");
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new PoTokenError(PoTokenStatus.INVALID)));
        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new PoTokenError(PoTokenStatus.INVALID)));

        List<JSONObject> events = mLog.readAll();

        assertEquals(2, events.size());
        assertEquals(Arrays.asList("reject_potoken", "reload_video"), getActions(events.get(0)));
        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(events.get(1)));
    }

    /**
     * SABR requires a poToken the client didn't send: there's none to go without, the next client
     */
    @Test
    public void aMissingPoTokenSwitchesTheClient() throws Exception {
        TestController controller = new TestController(video("dQw4w9WgXcQ"), "TV");
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new PoTokenError(PoTokenStatus.MISSING)));

        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * SABR stopped sending media with the TV poToken's attestation pending (51AQC7FS: at 0:59): the next client, not the TV one
     * without its poToken. A fresh one was pending too, and without one the server required one (5V8J002W #11).
     */
    @Test
    public void aPendingPoTokenSwitchesTheClient() throws Exception {
        TestController controller = new TestController(video("0IJSLGVTLWs"), "TV");
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new PoTokenError(PoTokenStatus.PENDING)));

        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(mLog.readAll().get(0)));
        assertEquals(false, isTvPotRejected());
    }

    /**
     * A stall with the poToken's attestation pending, which the SABR stream didn't tell as a PoTokenError: reloaded with the same
     * client, it froze there (51AQC7FS: at 0:59). The next client.
     */
    @Test
    public void aStallWithTheAttestationPendingSwitchesTheClient() throws Exception {
        TestController controller = new TestController(video("0IJSLGVTLWs"), "TV", playerStub(atMs(59_910, 567_217)));
        controller.mProtocol = PlayerFailure.PROTOCOL_SABR;
        controller.onPlay();
        ProtectionStatuses.onStatus("0IJSLGVTLWs", PoTokenStatus.PENDING);

        controller.onLongBuffering();

        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * Attested since (OK after pending), the stall is another's: reloaded, as before
     */
    @Test
    public void aStallAfterTheAttestationCameIsReloaded() throws Exception {
        TestController controller = new TestController(video("0IJSLGVTLWs"), "TV", playerStub(atMs(59_910, 567_217)));
        controller.mProtocol = PlayerFailure.PROTOCOL_SABR;
        controller.onPlay();
        ProtectionStatuses.onStatus("0IJSLGVTLWs", PoTokenStatus.PENDING);
        ProtectionStatuses.onStatus("0IJSLGVTLWs", PoTokenStatus.OK);

        controller.onLongBuffering();

        assertEquals(Arrays.asList("reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * Pending was a SABR stream's before, and the video's DASH now (the client switched to VISIONOS): its stall is reloaded, as
     * before
     */
    @Test
    public void anAttestationPendingOfAStreamBeforeIsntThisStreams() throws Exception {
        TestController controller = new TestController(video("0IJSLGVTLWs"), "VISIONOS", playerStub(atMs(59_910, 567_217)));
        controller.mProtocol = PlayerFailure.PROTOCOL_DASH;
        controller.onPlay();
        ProtectionStatuses.onStatus("0IJSLGVTLWs", PoTokenStatus.PENDING);

        controller.onLongBuffering();

        assertEquals(Arrays.asList("reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * A web client can't stream without its poToken: the next client
     */
    @Test
    public void aRejectedWebPoTokenSwitchesTheClient() throws Exception {
        TestController controller = new TestController(video("dQw4w9WgXcQ"), "WEB");
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new PoTokenError(PoTokenStatus.INVALID)));

        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * Every client said it can't be played: unplayable with its why, tried again in 30 s (not each second)
     */
    @Test
    public void unplayableOnEveryClientIsUnplayable() throws Exception {
        ClientAttempts.clear("ended");
        ClientAttempts.record("ended", ClientAttempts.Attempt.unplayable("TV", "UNPLAYABLE", "This live stream recording is not available."));
        ClientAttempts.record("ended", ClientAttempts.Attempt.unplayable("WEB", "ERROR", "This video is unavailable"));
        ClientAttempts.record("ended", ClientAttempts.Attempt.nullResponse("INNERTUBE"));
        TestController controller = new TestController(video("ended"));

        controller.runFormatErrorAction(new IllegalStateException("fromNullable result is null"));

        JSONObject event = mLog.readAll().get(0);
        assertEquals("unplayable", event.getString("kind"));
        assertEquals("UNPLAYABLE", event.getJSONObject("playability").getString("status"));
        assertEquals("This live stream recording is not available.", event.getJSONObject("playability").getString("reason"));
        assertEquals("reload_video_30s", event.getJSONArray("actions").getString(0));
    }

    /**
     * Every client said it's gone (BG2Y27G3: removed by the uploader): it won't be in 30 s either. Opened by the user, it stays with
     * its why.
     */
    @Test
    public void aVideoGoneOnEveryClientIsntTriedAgain() throws Exception {
        ClientAttempts.clear("removed");
        ClientAttempts.record("removed", ClientAttempts.Attempt.unplayable("TV", "ERROR", "This video has been removed by the uploader"));
        ClientAttempts.record("removed", ClientAttempts.Attempt.unplayable("WEB", "ERROR", "Video unavailable"));
        ClientAttempts.record("removed", ClientAttempts.Attempt.nullResponse("INNERTUBE"));
        TestController controller = new TestController(video("removed"));

        controller.runFormatErrorAction(new IllegalStateException("fromNullable result is null"));

        JSONObject event = mLog.readAll().get(0);
        assertEquals("unplayable", event.getString("kind"));
        assertEquals(Arrays.asList("gone_stay"), getActions(event));
    }

    /**
     * Refused for the viewer's age (6ABPRYGH: every client, 5 opens, each tried again every 30 s): it won't play in 30 s either
     */
    @Test
    public void anAgeGatedVideoIsntTriedAgain() throws Exception {
        ClientAttempts.clear("adults");
        ClientAttempts.record("adults", ClientAttempts.Attempt.unplayable("TV", "UNPLAYABLE",
                "Sign in to your primary account to confirm your age."));
        ClientAttempts.record("adults", ClientAttempts.Attempt.unplayable("WEB", "LOGIN_REQUIRED", "Sign in to confirm your age", false,
                null, true));
        ClientAttempts.record("adults", ClientAttempts.Attempt.unplayable("WEB_EMBED", "ERROR", "This video is unavailable"));
        TestController controller = new TestController(video("adults"));

        controller.runFormatErrorAction(new IllegalStateException("fromNullable result is null"));

        assertEquals(Arrays.asList("age_gated_stay"), getActions(mLog.readAll().get(0)));
    }

    /**
     * A client asked for a sign-in not for the age (a bot check, LOGIN_REQUIRED too): that may pass, it's tried again
     */
    @Test
    public void anAgeGateWithABotCheckIsTriedAgain() throws Exception {
        ClientAttempts.clear("botcheck");
        ClientAttempts.record("botcheck", ClientAttempts.Attempt.unplayable("TV", "LOGIN_REQUIRED", "Sign in to confirm you're not a bot"));
        ClientAttempts.record("botcheck", ClientAttempts.Attempt.unplayable("WEB", "LOGIN_REQUIRED", "Sign in to confirm your age", false,
                null, true));
        TestController controller = new TestController(video("botcheck"));

        controller.runFormatErrorAction(new IllegalStateException("fromNullable result is null"));

        assertEquals(Arrays.asList("reload_video_30s"), getActions(mLog.readAll().get(0)));
    }

    /**
     * What every client said is the playability's: the error of a fetch that gave no formats is no exception of the player's, and isn't
     * recorded as one (a chain of members-only videos was 19 failures with it, 8E1WXX00)
     */
    @Test
    public void anUnplayableVideosEmptyFetchIsNotAnError() throws Exception {
        ClientAttempts.clear("members");
        ClientAttempts.record("members", ClientAttempts.Attempt.unplayable("TV", "UNPLAYABLE", "This video is available to this channel's members"));
        TestController controller = new TestController(video("members"));

        controller.runFormatErrorAction(new IllegalStateException("fromNullable result is null"));

        JSONObject event = mLog.readAll().get(0);
        assertEquals("unplayable", event.getString("kind"));
        assertEquals(false, event.has("error"));
        assertEquals("UNPLAYABLE", event.getJSONObject("playability").getString("status"));
    }

    /**
     * The player has no video at hand (DMQEWB9B #1: no video ID, position or formats): the failure is of the one the formats tried are
     * of
     */
    @Test
    public void aFailureWithoutAVideoHasTheOneTheFormatsAreOf() throws Exception {
        FormatAttempts.onNewVideo(RuntimeEnvironment.getApplication(), "bjJy8qsTBgw");
        TestController controller = new TestController(null);

        try {
            controller.runFormatErrorAction(new IllegalStateException("boom"));

            assertEquals("bjJy8qsTBgw", mLog.readAll().get(0).getString("videoId"));
        } finally {
            FormatAttempts.end(RuntimeEnvironment.getApplication());
        }
    }

    /**
     * One that failed otherwise has its error
     */
    @Test
    public void aFormatErrorKeepsItsError() throws Exception {
        ClientAttempts.clear("offline2");
        ClientAttempts.record("offline2", ClientAttempts.Attempt.exception("WEB", new IllegalStateException("timeout")));
        TestController controller = new TestController(video("offline2"));

        controller.runFormatErrorAction(new IllegalStateException("fromNullable result is null"));

        JSONObject event = mLog.readAll().get(0);
        assertEquals("format_error", event.getString("kind"));
        assertEquals("IllegalStateException", event.getJSONObject("error").getString("class"));
    }

    /**
     * Its why is the player's title, seen only with the controls: they come up with it
     */
    @Test
    public void unplayableOnEveryClientShowsTheControls() throws Exception {
        ClientAttempts.clear("ended");
        ClientAttempts.record("ended", ClientAttempts.Attempt.unplayable("TV", "UNPLAYABLE", "This live stream recording is not available."));
        ClientAttempts.record("ended", ClientAttempts.Attempt.unplayable("WEB", "UNPLAYABLE", "This live stream recording is not available."));
        List<String> calls = new ArrayList<>();
        PlaybackView stub = playerStub();
        PlaybackView player = (PlaybackView) Proxy.newProxyInstance(PlaybackView.class.getClassLoader(), new Class<?>[]{PlaybackView.class},
                (proxy, method, args) -> {
                    if (Arrays.asList("setTitle", "showOverlay", "showControls").contains(method.getName())) {
                        calls.add(method.getName() + "(" + args[0] + ")");
                    }

                    return method.invoke(stub, args);
                });
        TestController controller = new TestController(video("ended"), null, player);

        controller.runFormatErrorAction(new IllegalStateException("fromNullable result is null"));

        assertEquals(Arrays.asList("setTitle(This live stream recording is not available.)", "showOverlay(true)"), calls);
    }

    /**
     * One client failed otherwise (e.g. no network): still a format error
     */
    @Test
    public void unplayableOnSomeClientsIsAFormatError() throws Exception {
        ClientAttempts.clear("offline");
        ClientAttempts.record("offline", ClientAttempts.Attempt.unplayable("TV", "ERROR", "This video is unavailable"));
        ClientAttempts.record("offline", ClientAttempts.Attempt.exception("WEB", new IllegalStateException("timeout")));
        TestController controller = new TestController(video("offline"));

        controller.runFormatErrorAction(new IllegalStateException("fromNullable result is null"));

        assertEquals("format_error", mLog.readAll().get(0).getString("kind"));
    }

    /**
     * Prepared again (e.g. reloaded): the audio's first segments came, the video's init is still loading
     */
    /**
     * Reloading once may clear it (a hung connection). Again soon after, the connection can't keep up: the next size down, for this video
     */
    @Test
    public void aRepeatedStallLowersTheQualityAndRestartsTheEngine() throws Exception {
        TestController controller = new TestController(video("g_jLBnnQuwE"), QUALITIES, QUALITIES.get(0));
        controller.onPlay();

        controller.onLongBuffering();
        controller.onPlay();
        assertEquals(null, getPlayerData().getTempVideoFormat());

        controller.onLongBuffering();

        List<JSONObject> events = mLog.readAll();
        assertEquals(Arrays.asList("reload_video"), getActions(events.get(0)));
        assertEquals(Arrays.asList("lower_quality", "restart_engine"), getActions(events.get(1)));
        assertLowered(getPlayerData().getTempVideoFormat());
    }

    /**
     * Kept when the video's reloaded (the reload clears temp formats), gone with another video
     */
    @Test
    public void theLoweredQualityIsForThisVideoOnly() throws Exception {
        Video video = video("g_jLBnnQuwE");
        TestController controller = new TestController(video, QUALITIES, QUALITIES.get(0));
        controller.onNewVideo(video);
        controller.onPlay();
        controller.onLongBuffering();
        controller.onPlay();
        controller.onLongBuffering();

        getPlayerData().setTempVideoFormat(null);
        controller.onNewVideo(video);
        assertLowered(getPlayerData().getTempVideoFormat());

        getPlayerData().setTempVideoFormat(null);
        controller.onNewVideo(video("other"));
        assertEquals(null, getPlayerData().getTempVideoFormat());
    }

    /**
     * A seek while paused, e.g. a video stopped at its end: no stall check. Paused where it never gets ready, the "stall" reloaded
     * the video from 0:00 (9lKpEOEbr0U over DASH)
     */
    @Test
    public void aSeekWhilePausedIsNoStall() throws Exception {
        TestController controller = new TestController(video("9lKpEOEbr0U"), null, playerStub(false));
        controller.onPlay();
        controller.onPause();

        controller.onSeekEnd();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));

        assertEquals(0, mLog.readAll().size());
    }

    /**
     * A seek while playing that doesn't get ready in 20 s is a stall, as before
     */
    @Test
    public void aSeekWhilePlayingThatHangsIsAStall() throws Exception {
        TestController controller = new TestController(video("9lKpEOEbr0U"), null, playerStub(true));
        controller.onPlay();

        controller.onSeekEnd();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(21));

        List<JSONObject> events = mLog.readAll();

        assertEquals(1, events.size());
        assertEquals("stall", events.get(0).getString("kind"));
        assertEquals(Arrays.asList("reload_video"), getActions(events.get(0)));
    }

    /**
     * A video that played, stalled at its end (over DASH, past its whole-second length): it ended, and isn't reloaded from 0:00
     */
    @Test
    public void aStallAtTheEndOfAVideoEndsIt() throws Exception {
        TestController controller = new TestController(video("9lKpEOEbr0U"), null, playerStub(atMs(1_972_000, 1_972_000)));
        controller.onPlay();

        controller.onLongBuffering();

        assertEquals(Arrays.asList("play_end"), getActions(mLog.readAll().get(0)));
        assertEquals(1, controller.mPlayEnds);
    }

    /**
     * One that never played is stuck, even near its end
     */
    @Test
    public void aStallOfAVideoThatNeverPlayedIsntItsEnd() throws Exception {
        TestController controller = new TestController(video("9lKpEOEbr0U"), null, playerStub(atMs(1_972_000, 1_972_000)));

        controller.onLongBuffering();

        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(mLog.readAll().get(0)));
        assertEquals(0, controller.mPlayEnds);
    }

    /**
     * Further from its end, a stall is reloaded, as before
     */
    @Test
    public void aStallBeforeTheEndOfAVideoIsReloaded() throws Exception {
        TestController controller = new TestController(video("9lKpEOEbr0U"), null, playerStub(atMs(1_960_000, 1_972_000)));
        controller.onPlay();

        controller.onLongBuffering();

        assertEquals(Arrays.asList("reload_video"), getActions(mLog.readAll().get(0)));
        assertEquals(0, controller.mPlayEnds);
    }

    /**
     * A video that played failed half a second before its end (tunneled sound drained at the end of the stream, 9TJXo63g6JA at 3:21.3 of
     * 3:21.8): it ended, without the error's message or a restart where nothing's left to play
     */
    @Test
    public void anEngineErrorAtTheEndOfAVideoEndsIt() throws Exception {
        TestController controller = new TestController(video("9TJXo63g6JA"), null, playerStub(atMs(201_300, 201_800)));
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_UNEXPECTED, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new IllegalStateException());

        assertEquals(Arrays.asList("play_end"), getActions(mLog.readAll().get(0)));
        assertEquals(1, controller.mPlayEnds);
        assertEquals(0, ShadowToast.shownToastCount());
    }

    /**
     * Further from its end, the same error restarts the engine, as before
     */
    @Test
    public void anEngineErrorBeforeTheEndOfAVideoRestartsTheEngine() throws Exception {
        TestController controller = new TestController(video("9TJXo63g6JA"), null, playerStub(atMs(190_000, 201_800)));
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_UNEXPECTED, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new IllegalStateException());

        assertEquals(Arrays.asList("restart_engine"), getActions(mLog.readAll().get(0)));
        assertEquals(0, controller.mPlayEnds);
    }

    /**
     * A live stream stalls in the last minutes of its timeline (FVNAAYZD #29: 25 s before its end): it may have ended, so its metadata
     * is read again while the reload goes on. Not live any more, it ended: what the reload would load isn't, and playing ends.
     */
    @Test
    public void aLiveStreamThatStalledNearItsEndIsEndedByItsMetadata() throws Exception {
        Video live = video("pfsI290BV4U");
        live.isLive = true;
        TestController controller = new TestController(live, null, playerStub(atMs(3_601_200, 3_626_700)));
        controller.onPlay();

        controller.onLongBuffering();

        assertEquals(Arrays.asList("live_end_check", "reload_video"), getActions(mLog.readAll().get(0)));
        assertEquals(0, controller.mPlayEnds);

        controller.mOnMetadata.onMetadata(metadata(false));

        assertEquals(1, controller.mPlayEnds);
        assertEquals(1, controller.mCancels);
        assertEquals(true, live.isLiveEnd);
    }

    /**
     * Live still, by what the server says: the stall is a connection's, and the stream is reloaded as before
     */
    @Test
    public void aLiveStreamTheServerCallsLiveIsNotEnded() throws Exception {
        Video live = video("pfsI290BV4U");
        live.isLive = true;
        TestController controller = new TestController(live, null, playerStub(atMs(3_601_200, 3_626_700)));
        controller.onPlay();

        controller.onLongBuffering();
        controller.mOnMetadata.onMetadata(metadata(true));

        assertEquals(0, controller.mPlayEnds);
        assertEquals(0, controller.mCancels);
        assertEquals(false, live.isLiveEnd);
    }

    /**
     * At its head, far from the end of its timeline, a live stream's stall is no end: nothing is asked
     */
    @Test
    public void aLiveStreamThatStalledFarFromItsEndIsNotChecked() throws Exception {
        Video live = video("pfsI290BV4U");
        live.isLive = true;
        TestController controller = new TestController(live, null, playerStub(atMs(1_000_000, 3_626_700)));
        controller.onPlay();

        controller.onLongBuffering();

        assertEquals(Arrays.asList("reload_video"), getActions(mLog.readAll().get(0)));
        assertEquals(null, controller.mOnMetadata);
    }

    /**
     * GWS5HG2H #1-#3: a live stream's head stood still, its server answering each request that the next segment wasn't there yet, and a
     * reload every 21 s got the same from a black screen. Waited in place, the user told once, checked again a stall later; reloaded as
     * before once the server's waits stop.
     */
    @Test
    public void aLiveStallAtAHeadThatStandsStillWaitsInPlace() throws Exception {
        Video live = video("pfsI290BV4U");
        live.isLive = true;
        TestController controller = new TestController(live, null, playerStub(atMs(1_000_000, 3_626_700)));
        controller.onPlay();
        LiveHeadWaits.onWait(C.TRACK_TYPE_VIDEO, 5_000);

        controller.onLongBuffering();

        assertEquals(Arrays.asList("live_head_wait"), getActions(mLog.readAll().get(0)));
        assertEquals(1, ShadowToast.shownToastCount());

        // A stall later, the server still waiting
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(15));
        LiveHeadWaits.onWait(C.TRACK_TYPE_AUDIO, 5_000);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));

        List<JSONObject> events = mLog.readAll();
        assertEquals(Arrays.asList("live_head_wait"), getActions(events.get(events.size() - 1)));
        assertEquals(0, controller.mReloads.size());
        assertEquals(1, ShadowToast.shownToastCount());

        // No wait told since: reloaded
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(21));

        events = mLog.readAll();
        assertEquals(Arrays.asList("reload_video"), getActions(events.get(events.size() - 1)));
        assertEquals(1, controller.mReloads.size());
    }

    /**
     * A load that failed on its connection lately: the network's, not the head's, reloaded as before
     */
    @Test
    public void aLiveStallWithAConnectionErrorIsntTheHeads() throws Exception {
        Video live = video("pfsI290BV4U");
        live.isLive = true;
        TestController controller = new TestController(live, null, playerStub(atMs(1_000_000, 3_626_700)));
        controller.onPlay();
        LiveHeadWaits.onWait(C.TRACK_TYPE_VIDEO, 5_000);
        long now = SystemClock.elapsedRealtime();
        LoadErrors.onLoadError(now - 8_000, now, C.DATA_TYPE_MEDIA, C.TRACK_TYPE_VIDEO, new SocketTimeoutException("timeout"));

        controller.onLongBuffering();

        assertEquals(Arrays.asList("reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * Asked again within a minute it isn't: each stall of a stream that goes on doesn't ask the server
     */
    @Test
    public void aLiveStreamIsCheckedOncePerMinute() throws Exception {
        Video live = video("pfsI290BV4U");
        live.isLive = true;
        TestController controller = new TestController(live, null, playerStub(atMs(3_601_200, 3_626_700)));
        controller.onPlay();

        controller.onLongBuffering();
        controller.onPlay();
        controller.onLongBuffering();

        List<JSONObject> events = mLog.readAll();

        assertEquals(Arrays.asList("live_end_check", "reload_video"), getActions(events.get(0)));
        assertEquals(1, controller.mMetadataLoads);
    }

    /**
     * 97MSK8B6 #2, #4: TV_SAMSUNG's and VISIONOS's DASH answered 503 while the video played, and the client switched for every video
     * after it. The next client for this video only.
     */
    @Test
    public void aServerErrorWhilePlayingMovesTheVideoToTheNextClient() throws Exception {
        TestController controller = new TestController(video("Hf-CqpBeDs0"), "TV_SAMSUNG");
        controller.mNextClient = "VISIONOS";
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new InvalidResponseCodeException(503, "Service Unavailable", Collections.emptyMap(), dataSpec()));

        assertEquals(Arrays.asList("client_for_video:VISIONOS", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * Before it played too: the server's, not the client's poToken or n parameter
     */
    @Test
    public void aServerErrorBeforePlayingMovesTheVideoToTheNextClient() throws Exception {
        TestController controller = new TestController(video("fC86OVoSVJc"), "TV_DOWNGRADED");
        controller.mNextClient = "WEB";

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new InvalidResponseCodeException(503, "Service Unavailable", Collections.emptyMap(),
                        dataSpec())));

        assertEquals(Arrays.asList("client_for_video:WEB", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * No other client to pick (e.g. the order has one): switched as before
     */
    @Test
    public void aServerErrorWithNoClientToPickSwitchesTheClient() throws Exception {
        TestController controller = new TestController(video("Hf-CqpBeDs0"), "TV");
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new InvalidResponseCodeException(503, "Service Unavailable", Collections.emptyMap(), dataSpec()));

        assertEquals(Arrays.asList("switch_next_client:WEB", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    @Test
    public void aServerErrorIsAnHttp5xx() {
        assertTrue(ErrorFixerController.isServerError(new InvalidResponseCodeException(500, null, Collections.emptyMap(), dataSpec())));
        assertTrue(ErrorFixerController.isServerError(new IOException(new InvalidResponseCodeException(503, null, Collections.emptyMap(),
                dataSpec()))));
        assertFalse(ErrorFixerController.isServerError(new InvalidResponseCodeException(403, null, Collections.emptyMap(), dataSpec())));
        assertFalse(ErrorFixerController.isServerError(new InvalidResponseCodeException(429, null, Collections.emptyMap(), dataSpec())));
        assertFalse(ErrorFixerController.isServerError(new IOException("Response code: 503")));
        assertFalse(ErrorFixerController.isServerError(null));
    }

    /**
     * 7QAT714C #1: 4K at 9.9 Mbps of an 18 Mbps format, the first stall's reload didn't play in 5 s. Its loads came slower than it plays
     * and none failed: the next size down at the first stall.
     */
    @Test
    public void theFirstStallOnAConnectionTooSlowLowersTheQuality() throws Exception {
        try {
            playingAt(18_006_000, 9_885);
            TestController controller = new TestController(video("0N4ujc0HJus"), QUALITIES, QUALITIES.get(0));
            controller.onPlay();

            controller.onLongBuffering();

            assertEquals(Arrays.asList("lower_quality", "restart_engine"), getActions(mLog.readAll().get(0)));
            assertLowered(getPlayerData().getTempVideoFormat());
        } finally {
            FormatAttempts.end(RuntimeEnvironment.getApplication());
        }
    }

    /**
     * Its loads came faster than it plays: the connection stopped answering, reloaded as before
     */
    @Test
    public void theFirstStallOnAConnectionFastEnoughReloads() throws Exception {
        try {
            playingAt(18_006_000, 30_000);
            TestController controller = new TestController(video("0N4ujc0HJus"), QUALITIES, QUALITIES.get(0));
            controller.onPlay();

            controller.onLongBuffering();

            assertEquals(Arrays.asList("reload_video"), getActions(mLog.readAll().get(0)));
            assertEquals(null, getPlayerData().getTempVideoFormat());
        } finally {
            FormatAttempts.end(RuntimeEnvironment.getApplication());
        }
    }

    /**
     * Too slow, and a load failed on its connection: the connection's, not the format's
     */
    @Test
    public void theFirstStallTooSlowWithAConnectionErrorDoesntLowerTheQuality() throws Exception {
        try {
            playingAt(18_006_000, 9_885);
            TestController controller = new TestController(video("0N4ujc0HJus"), QUALITIES, QUALITIES.get(0));
            controller.onPlay();
            long nowMs = SystemClock.elapsedRealtime();
            LoadErrors.onLoadError(nowMs - 1_000, nowMs, C.DATA_TYPE_MEDIA, C.TRACK_TYPE_VIDEO,
                    new HttpDataSourceException(new SocketTimeoutException("timeout"), dataSpec(), HttpDataSourceException.TYPE_READ));

            controller.onLongBuffering();

            assertEquals(null, getPlayerData().getTempVideoFormat());
        } finally {
            FormatAttempts.end(RuntimeEnvironment.getApplication());
        }
    }

    /**
     * 7QAT714C #2: a private video, every client asked for a sign-in, VISIONOS too, which had played another video 10 min before. The
     * video's, not a bot check of the session: it stays, not tried every 30 s.
     */
    @Test
    public void aSignInAskedForByTheClientThatJustPlayedIsLasting() throws Exception {
        ClientAttempts.clear("bzi_LJTPFi0");
        ClientAttempts.record("bzi_LJTPFi0", ClientAttempts.Attempt.unplayable("TV", "LOGIN_REQUIRED", "This video is private"));
        ClientAttempts.record("bzi_LJTPFi0", ClientAttempts.Attempt.unplayable("VISIONOS", "LOGIN_REQUIRED", "Please sign in"));
        ClientAttempts.record("bzi_LJTPFi0", ClientAttempts.Attempt.unplayable("WEB", "LOGIN_REQUIRED", "Private video"));
        Video video = video("s26AHc0b_l4");
        TestController controller = new TestController(video, "VISIONOS");
        controller.onPlay();
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofMinutes(10));
        video.videoId = "bzi_LJTPFi0";

        controller.runFormatErrorAction(new IllegalStateException("fromNullable result is null"));

        assertEquals(Arrays.asList("sign_in_stay"), getActions(mLog.readAll().get(0)));
    }

    /**
     * No video played lately (or long ago): a bot check may refuse every client, tried again as before
     */
    @Test
    public void aSignInAskedForWithNoClientPlayingLatelyIsTriedAgain() throws Exception {
        ClientAttempts.clear("bzi_LJTPFi0");
        ClientAttempts.record("bzi_LJTPFi0", ClientAttempts.Attempt.unplayable("TV", "LOGIN_REQUIRED", "This video is private"));
        ClientAttempts.record("bzi_LJTPFi0", ClientAttempts.Attempt.unplayable("VISIONOS", "LOGIN_REQUIRED", "Please sign in"));
        Video video = video("s26AHc0b_l4");
        TestController controller = new TestController(video, "VISIONOS");
        controller.onPlay();
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofMinutes(16));
        video.videoId = "bzi_LJTPFi0";

        controller.runFormatErrorAction(new IllegalStateException("fromNullable result is null"));

        assertEquals(Arrays.asList("reload_video_30s"), getActions(mLog.readAll().get(0)));
    }

    @Test
    public void aSignInIsTheVideosOnlyWhenTheClientThatPlayedAskedForIt() {
        List<ClientAttempts.Attempt> attempts = Arrays.asList(
                ClientAttempts.Attempt.unplayable("TV", "LOGIN_REQUIRED", "Sign in to confirm you're not a bot"),
                ClientAttempts.Attempt.unplayable("TV_SAMSUNG", "LOGIN_REQUIRED", "Sign in to confirm you're not a bot"));

        assertEquals("sign_in", ErrorFixerController.getLastingCause(attempts, "TV"));
        assertEquals(null, ErrorFixerController.getLastingCause(attempts, "VISIONOS"));
        assertEquals(null, ErrorFixerController.getLastingCause(attempts, null));
        // Another answer than a sign-in: not one
        assertEquals(null, ErrorFixerController.getLastingCause(Arrays.asList(attempts.get(0),
                ClientAttempts.Attempt.unplayable("WEB", "UNPLAYABLE", "Video unavailable")), "TV"));
    }

    /**
     * Y36K953Z after 27XPFNZC, a run apart: the drops of a run before count, the second video's drops try another codec at once
     */
    @Test
    public void framesDroppedOnACodecInARunBeforeCount() throws Exception {
        List<FormatItem> formats = Arrays.asList(
                ExoFormatItem.fromVideoSpec("1920,1080,50,av01", false),
                ExoFormatItem.fromVideoSpec("1920,1080,50,vp9", false));
        droppedFramesBefore("av01", "1YKvmLD3QW4");
        TestController controller = new TestController(video("EOIkoWmVRBs"), formats, formats.get(0));
        controller.onPlay();

        dropFrames(controller);

        assertEquals(Arrays.asList("other_codec:vp9", "restart_engine"), getActions(mLog.readAll().get(0)));
    }

    /**
     * 27XPFNZC, Y36K953Z: AV1 and AVC 1080p50 both dropped frames (at 1.5x, as found later, 0HCC641V), VP9 none. With AVC first
     * (preferred over VP9), AV1's other codec is VP9: AVC has dropped them too.
     */
    @Test
    public void anotherCodecThatDroppedFramesIsPassedOver() throws Exception {
        List<FormatItem> formats = Arrays.asList(
                ExoFormatItem.fromVideoSpec("1920,1080,50,av01", false),
                ExoFormatItem.fromVideoSpec("1920,1080,50,avc", false),
                ExoFormatItem.fromVideoSpec("1920,1080,50,vp9", false));
        droppedFramesBefore("av01", "1YKvmLD3QW4");
        droppedFramesBefore("avc", "EOIkoWmVRBs");
        TestController controller = new TestController(video("g1T4NT2f_dA"), formats, formats.get(0));
        controller.onPlay();

        dropFrames(controller);

        assertEquals(Arrays.asList("other_codec:vp9", "restart_engine"), getActions(mLog.readAll().get(0)));
    }

    /**
     * Every other codec dropped frames too: the first of them, as before
     */
    @Test
    public void everyOtherCodecThatDroppedFramesLeavesTheFirst() throws Exception {
        List<FormatItem> formats = Arrays.asList(
                ExoFormatItem.fromVideoSpec("1920,1080,50,av01", false),
                ExoFormatItem.fromVideoSpec("1920,1080,50,avc", false));
        droppedFramesBefore("av01", "1YKvmLD3QW4");
        droppedFramesBefore("avc", "EOIkoWmVRBs");
        TestController controller = new TestController(video("g1T4NT2f_dA"), formats, formats.get(0));
        controller.onPlay();

        dropFrames(controller);

        assertEquals(Arrays.asList("other_codec:avc", "restart_engine"), getActions(mLog.readAll().get(0)));
    }

    /**
     * 97MSK8B6 #8: WEB's SABR refused the video 2.4 s before its end, and four clients in 15 s didn't play it. It ended.
     */
    @Test
    public void anEngineErrorSecondsBeforeTheEndOfAVideoEndsIt() throws Exception {
        TestController controller = new TestController(video("fC86OVoSVJc"), "WEB", playerStub(atMs(299_400, 301_800)));
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(freshSessionRefusal(75)));

        assertEquals(Arrays.asList("play_end"), getActions(mLog.readAll().get(0)));
        assertEquals(1, controller.mPlayEnds);
    }

    /**
     * A stall that far from the end isn't: the end is past what's buffered, it's reloaded
     */
    @Test
    public void aStallSecondsBeforeTheEndOfAVideoIsReloaded() throws Exception {
        TestController controller = new TestController(video("fC86OVoSVJc"), null, playerStub(atMs(299_400, 301_800)));
        controller.onPlay();

        controller.onLongBuffering();

        assertEquals(Arrays.asList("reload_video"), getActions(mLog.readAll().get(0)));
        assertEquals(0, controller.mPlayEnds);
    }

    /**
     * The video dropped frames on the codec at 1080p50, in this run or one before (see FrameDropCodecs)
     */
    private static void droppedFramesBefore(String codec, String videoId) {
        droppedFramesBefore(codec, videoId, 1);
    }

    /**
     * @param speed the playback's: the rate the drops are of is the video's times it
     */
    private static void droppedFramesBefore(String codec, String videoId, float speed) {
        FrameDropCodecs.add(RuntimeEnvironment.getApplication(), FrameDropCodecs.kindOf(TrackSelectorUtil.codecNameShort(codec), 1080, 50,
                speed), videoId);
    }

    /**
     * 0HCC641V: a Sansui's AV1 and AVC 1080p50 dropped frames at 1.5x, none at 1x (27XPFNZC). Drops at 1.5x don't count at 1x.
     */
    @Test
    public void framesDroppedAtASpeedDontCountAtAnother() throws Exception {
        List<FormatItem> formats = Arrays.asList(
                ExoFormatItem.fromVideoSpec("1920,1080,50,av01", false),
                ExoFormatItem.fromVideoSpec("1920,1080,50,vp9", false));
        droppedFramesBefore("av01", "1YKvmLD3QW4", 1.5f);
        TestController controller = new TestController(video("EOIkoWmVRBs"), formats, formats.get(0));
        controller.onPlay();

        dropFrames(controller);

        assertEquals(Collections.emptyList(), getActions(mLog.readAll().get(0)));
    }

    /**
     * 0HCC641V #2: the second video whose AVC 1080p50 dropped frames at 1.5x went to VP9, which kept up at that rate
     */
    @Test
    public void framesDroppedAtTheSameSpeedCount() throws Exception {
        List<FormatItem> formats = Arrays.asList(
                ExoFormatItem.fromVideoSpec("1920,1080,50,avc", false),
                ExoFormatItem.fromVideoSpec("1920,1080,50,vp9", false));
        droppedFramesBefore("avc", "-LPIayq78Zw", 1.5f);
        TestController controller = new TestController(video("nnwnqpQ_Gho"), null,
                speedPlayerStub(formats, formats.get(0), 1.5f, new ArrayList<>()));
        controller.onPlay();

        dropFrames(controller);

        assertEquals(Arrays.asList("other_codec:vp9", "restart_engine"), getActions(mLog.readAll().get(0)));
    }

    /**
     * 0HCC641V: each 1080p50 video at 1.5x played AV1 for 13-20 s of drops. Known to drop at that rate on two videos, another codec
     * that hasn't plays from the start, for this video, and the attempt says why.
     */
    @Test
    public void aFasterSpeedPlaysACodecThatKeepsUpFromTheStart() throws Exception {
        List<FormatItem> formats = Arrays.asList(
                ExoFormatItem.fromVideoSpec("1920,1080,50,av01", false),
                ExoFormatItem.fromVideoSpec("1920,1080,50,vp9", false));
        droppedFramesBefore("av01", "-LPIayq78Zw", 1.5f);
        droppedFramesBefore("av01", "nnwnqpQ_Gho", 1.5f);
        List<FormatItem> selected = new ArrayList<>();
        TestController controller = new TestController(video("EOIkoWmVRBs"), null,
                speedPlayerStub(formats, formats.get(0), 1.5f, selected));
        FormatAttempts.onNewVideo(RuntimeEnvironment.getApplication(), "EOIkoWmVRBs");

        controller.onTrackChanged(formats.get(0));

        assertEquals(Collections.singletonList(formats.get(1)), selected);
        assertEquals("vp9", TrackSelectorUtil.codecNameShort(getPlayerData().getTempVideoFormat().getTrack().format.codecs));
        // Once a video: the user's pick after it stays
        controller.onTrackChanged(formats.get(0));
        controller.onSpeedChanged(1.5f);
        assertEquals(1, selected.size());
        FormatAttempts.end(RuntimeEnvironment.getApplication());
    }

    /**
     * At 1x the same codecs dropped none (27XPFNZC): the one picked plays
     */
    @Test
    public void theNormalSpeedKeepsTheCodecPicked() throws Exception {
        List<FormatItem> formats = Arrays.asList(
                ExoFormatItem.fromVideoSpec("1920,1080,50,av01", false),
                ExoFormatItem.fromVideoSpec("1920,1080,50,vp9", false));
        droppedFramesBefore("av01", "-LPIayq78Zw", 1.5f);
        droppedFramesBefore("av01", "nnwnqpQ_Gho", 1.5f);
        List<FormatItem> selected = new ArrayList<>();
        TestController controller = new TestController(video("EOIkoWmVRBs"), null, speedPlayerStub(formats, formats.get(0), 1f, selected));

        controller.onTrackChanged(formats.get(0));
        controller.onSpeedChanged(1f);

        assertEquals(Collections.emptyList(), selected);
    }

    /**
     * Dropping on one video may be the video's (see FRAME_DROP_VIDEOS); another codec that dropped at the rate too keeps up no better
     */
    @Test
    public void aFasterSpeedKeepsACodecNotKnownToDropOrWithNoBetterOne() throws Exception {
        List<FormatItem> formats = Arrays.asList(
                ExoFormatItem.fromVideoSpec("1920,1080,50,av01", false),
                ExoFormatItem.fromVideoSpec("1920,1080,50,avc", false));
        droppedFramesBefore("av01", "-LPIayq78Zw", 1.5f);
        List<FormatItem> selected = new ArrayList<>();
        TestController controller = new TestController(video("EOIkoWmVRBs"), null,
                speedPlayerStub(formats, formats.get(0), 1.5f, selected));

        controller.onTrackChanged(formats.get(0));
        assertEquals(Collections.emptyList(), selected);

        droppedFramesBefore("av01", "nnwnqpQ_Gho", 1.5f);
        droppedFramesBefore("avc", "MP7apWQEHNY", 1.5f);
        controller.onSpeedChanged(1.5f);
        assertEquals(Collections.emptyList(), selected);
    }

    /**
     * At the speed, with the formats and the one playing: the formats it's told to play go to selected
     */
    private static PlaybackView speedPlayerStub(List<FormatItem> videoFormats, FormatItem videoFormat, float speed,
            List<FormatItem> selected) {
        PlaybackView stub = playerStub(videoFormats, videoFormat);

        return (PlaybackView) Proxy.newProxyInstance(PlaybackView.class.getClassLoader(), new Class<?>[]{PlaybackView.class},
                (proxy, method, args) -> {
                    if ("getSpeed".equals(method.getName())) {
                        return speed;
                    } else if ("setFormat".equals(method.getName())) {
                        selected.add((FormatItem) args[0]);
                        return null;
                    }

                    return method.invoke(stub, args);
                });
    }

    /**
     * The attempt of the video playing: its format's bitrate, and its loads lately at the throughput
     */
    private static void playingAt(int bitrate, long throughputKbps) {
        FormatAttempts.onNewVideo(RuntimeEnvironment.getApplication(), "0N4ujc0HJus");
        MediaTrack track = MediaTrack.forRendererIndex(TrackSelectorManager.RENDERER_INDEX_VIDEO);
        track.format = Format.createVideoSampleFormat("313", "video/x-vnd.on2.vp9", "vp9", bitrate, Format.NO_VALUE, 3840, 2160, 25,
                null, null);
        FormatAttempts.onTracks(ExoFormatItem.from(track), FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onLoad(throughputKbps * 1_000 / 8, 1_000);
    }

    /**
     * What the server says of a video, only whether it's live
     */
    private static MediaItemMetadata metadata(boolean isLive) {
        return (MediaItemMetadata) Proxy.newProxyInstance(MediaItemMetadata.class.getClassLoader(), new Class<?>[]{MediaItemMetadata.class},
                (proxy, method, args) -> "isLive".equals(method.getName()) ? isLive : method.getReturnType() == boolean.class ? false : null);
    }

    private static Map<String, Object> atMs(long positionMs, long durationMs) {
        Map<String, Object> answers = new HashMap<>();
        answers.put("getPositionMs", positionMs);
        answers.put("getDurationMs", durationMs);

        return answers;
    }

    @Test
    public void stallsFarApartOnlyReload() throws Exception {
        TestController controller = new TestController(video("g_jLBnnQuwE"), QUALITIES, QUALITIES.get(0));
        controller.onPlay();

        controller.onLongBuffering();
        org.robolectric.shadows.ShadowSystemClock.advanceBy(4, java.util.concurrent.TimeUnit.MINUTES);
        controller.onPlay();
        controller.onLongBuffering();

        assertEquals(Arrays.asList("reload_video"), getActions(mLog.readAll().get(1)));
    }

    /**
     * Not below 720p: reloaded as before
     */
    @Test
    public void aRepeatedStallAt720pOnlyReloads() throws Exception {
        TestController controller = new TestController(video("g_jLBnnQuwE"), QUALITIES, QUALITIES.get(3));
        controller.onPlay();

        controller.onLongBuffering();
        controller.onPlay();
        controller.onLongBuffering();

        assertEquals(Arrays.asList("reload_video"), getActions(mLog.readAll().get(1)));
    }

    /**
     * 27XPFNZC #1, #2: AV1 1080p50 dropped frames on one video, then on the next, with decoders that declare 1080p60 (VP9 1080p50 had
     * dropped none on that device). The first video's may be its own: nothing. The second plays another codec of its size.
     */
    @Test
    public void framesDroppedOnACodecVideoAfterVideoTryAnotherCodec() throws Exception {
        List<FormatItem> formats = Arrays.asList(
                ExoFormatItem.fromVideoSpec("1920,1080,30,av01", false),
                ExoFormatItem.fromVideoSpec("1920,1080,30,vp9", false),
                ExoFormatItem.fromVideoSpec("1280,720,30,av01", false));
        Video video = video("1YKvmLD3QW4");
        TestController controller = new TestController(video, formats, formats.get(0));
        controller.onPlay();

        dropFrames(controller);
        video.videoId = "g1T4NT2f_dA";
        dropFrames(controller);

        List<JSONObject> events = mLog.readAll();
        assertEquals(Collections.emptyList(), getActions(events.get(0)));
        assertEquals(Arrays.asList("other_codec:vp9", "restart_engine"), getActions(events.get(1)));
        assertEquals(1, controller.mRestarts.size());
    }

    /**
     * C2TJMVW6 #1, #2: a live stream's VP9 1080p60 on a 720p TV dropped 102 of 182 frames, its first video, with no fix. Lowered to the
     * display's size, for this video only.
     */
    @Test
    public void framesDroppedOnAVideoBiggerThanTheDisplayLowerItToTheDisplay() throws Exception {
        List<FormatItem> formats = Arrays.asList(
                ExoFormatItem.fromVideoSpec("1920,1080,60,vp9", false),
                ExoFormatItem.fromVideoSpec("1280,720,60,vp9", false),
                ExoFormatItem.fromVideoSpec("854,480,30,vp9", false));
        Video live = video("6J-bqpLI7CQ");
        live.isLive = true;
        TestController controller = new TestController(live, formats, formats.get(0));
        controller.mDisplaySize = 1280;
        controller.onPlay();

        dropFrames(controller);

        assertEquals(Arrays.asList("lower_to_display", "restart_engine"), getActions(mLog.readAll().get(0)));
        assertEquals(720, getPlayerData().getTempVideoFormat().getHeight());
        assertEquals(1, controller.mRestarts.size());
    }

    /**
     * 4K on a 1080p TV: straight to the display's size, not a size at a time
     */
    @Test
    public void framesDroppedOnAVideoBiggerThanTheDisplaySkipTheSizesBetween() throws Exception {
        List<FormatItem> formats = Arrays.asList(
                ExoFormatItem.fromVideoSpec("3840,2160,60,vp9", false),
                ExoFormatItem.fromVideoSpec("2560,1440,60,vp9", false),
                ExoFormatItem.fromVideoSpec("1920,1080,60,vp9", false));
        TestController controller = new TestController(video("6J-bqpLI7CQ"), formats, formats.get(0));
        controller.mDisplaySize = 1920;
        controller.onPlay();

        dropFrames(controller);

        assertEquals(Arrays.asList("lower_to_display", "restart_engine"), getActions(mLog.readAll().get(0)));
        assertEquals(1080, getPlayerData().getTempVideoFormat().getHeight());
    }

    /**
     * No bigger than the display: the drops of a first video are left alone, as before
     */
    @Test
    public void framesDroppedOnAVideoTheDisplayShowsAreLeftAlone() throws Exception {
        List<FormatItem> formats = Arrays.asList(
                ExoFormatItem.fromVideoSpec("1920,1080,60,vp9", false),
                ExoFormatItem.fromVideoSpec("1280,720,60,vp9", false));
        TestController controller = new TestController(video("6J-bqpLI7CQ"), formats, formats.get(0));
        controller.mDisplaySize = 1920;
        controller.onPlay();

        dropFrames(controller);

        assertEquals(Collections.emptyList(), getActions(mLog.readAll().get(0)));
        assertEquals(null, getPlayerData().getTempVideoFormat());
    }

    /**
     * The same video's drops again (e.g. after a reload) aren't another video's
     */
    @Test
    public void framesDroppedAgainOnTheSameVideoDontTryAnotherCodec() throws Exception {
        List<FormatItem> formats = Arrays.asList(
                ExoFormatItem.fromVideoSpec("1920,1080,30,av01", false),
                ExoFormatItem.fromVideoSpec("1920,1080,30,vp9", false));
        TestController controller = new TestController(video("1YKvmLD3QW4"), formats, formats.get(0));
        controller.onPlay();

        dropFrames(controller);
        dropFrames(controller);

        for (JSONObject event : mLog.readAll()) {
            assertEquals(Collections.emptyList(), getActions(event));
        }
    }

    private static void dropFrames(TestController controller) {
        PlayerFailure failure = PlayerFailure.create(RuntimeEnvironment.getApplication(), PlaybackHealth.KIND_FRAME_DROPS);
        controller.onHealthFailure(PlaybackHealth.KIND_FRAME_DROPS, failure);
        failure.record();
    }

    /**
     * The sound was silent for all it played (RF6HBNNH #5: a 4 kbps Opus track, 16 s of nothing, no fix): AAC for this video, and the
     * engine restarts
     */
    @Test
    public void aSoundSilentFromTheStartPlaysAacForTheVideo() throws Exception {
        TestController controller = new TestController(video("7uzblA-p8Xo"), null,
                playerStub(Collections.<String, Object>singletonMap("getAudioFormat", ExoFormatItem.fromAudioSpecs("opus,en"))));
        controller.onPlay();
        PlayerFailure failure = PlayerFailure.create(RuntimeEnvironment.getApplication(), PlaybackHealth.KIND_AUDIO_SILENT)
                .detail("silentFromStart", true);

        controller.onHealthFailure(PlaybackHealth.KIND_AUDIO_SILENT, failure);
        failure.record();

        assertEquals(Arrays.asList("audio_fallback:mp4a", "restart_engine"), getActions(mLog.readAll().get(0)));
        assertEquals(1, controller.mRestarts.size());
        assertEquals("mp4a", getPlayerData().getTempAudioFormat().getTrack().format.codecs);
    }

    /**
     * Silent for a stretch of a sound that came (a quiet part of the video): not the stream's, left as it is
     */
    @Test
    public void aSoundThatWentSilentLaterIsLeftAlone() throws Exception {
        TestController controller = new TestController(video("7uzblA-p8Xo"), null,
                playerStub(Collections.<String, Object>singletonMap("getAudioFormat", ExoFormatItem.fromAudioSpecs("opus,en"))));
        controller.onPlay();
        PlayerFailure failure = PlayerFailure.create(RuntimeEnvironment.getApplication(), PlaybackHealth.KIND_AUDIO_SILENT)
                .detail("silentFromStart", false);

        controller.onHealthFailure(PlaybackHealth.KIND_AUDIO_SILENT, failure);
        failure.record();

        assertEquals(Collections.<String>emptyList(), getActions(mLog.readAll().get(0)));
        assertEquals(0, controller.mRestarts.size());
    }

    /**
     * The next size down, 1440p, as a limit of the same codec: not the track of the stream played
     */
    private static void assertLowered(FormatItem format) {
        assertEquals(2560, format.getWidth());
        assertEquals(1440, format.getHeight());
        assertEquals(null, format.getTrack().format.id);
        assertEquals("vp9", com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil.codecNameShort(format.getTrack().format.codecs));
    }

    /**
     * The device refuses every AudioTrack, and the sound is AAC already: nothing left to fall back to (FARAJV5R #17–#35: 40 engine
     * restarts a minute for 10 minutes). Refused again with no track of the app's open, it's the device's: each restart waits twice as
     * long, from 2 s up to 30 s, and the user is told once to restart the device.
     */
    @Test
    public void aRefusedAudioTrackLoopWaitsLongerEachTime() throws Exception {
        TestController controller = new TestController(video("QQQQmXXTm1g"));

        for (int i = 0; i < 8; i++) {
            controller.onEngineError(PlayerEventListener.ERROR_TYPE_RENDERER, PlayerEventListener.RENDERER_INDEX_AUDIO,
                    new AudioSink.InitializationException(0, 44_100, 12, 56_720));
        }

        assertEquals(Collections.singletonList(1_000), controller.mReloads);
        assertEquals(Arrays.asList(2_000, 4_000, 8_000, 16_000, 30_000, 30_000, 30_000), controller.mRestarts);
        List<JSONObject> events = mLog.readAll();
        assertEquals(Arrays.asList("audio_reload", "reload_video"), getActions(events.get(0)));
        assertEquals(Arrays.asList("audio_fallback:none", "device_audio_refused", "restart_engine_30s"),
                getActions(events.get(events.size() - 1)));
        // To restart the device, once
        assertEquals(1, ShadowToast.shownToastCount());
    }

    /**
     * Each player's own: another's refusals don't add to its waits
     */
    @Test
    public void theAudioTrackWaitsAreOfTheVideo() throws Exception {
        TestController first = new TestController(video("QQQQmXXTm1g"));
        TestController controller = new TestController(video("iadNU7fJx6w"));

        for (int i = 0; i < 3; i++) {
            first.onEngineError(PlayerEventListener.ERROR_TYPE_RENDERER, PlayerEventListener.RENDERER_INDEX_AUDIO,
                    new AudioSink.InitializationException(0, 44_100, 12, 56_720));
            controller.onEngineError(PlayerEventListener.ERROR_TYPE_RENDERER, PlayerEventListener.RENDERER_INDEX_AUDIO,
                    new AudioSink.InitializationException(0, 44_100, 12, 56_720));
        }

        assertEquals(Arrays.asList(2_000, 4_000), first.mRestarts);
        assertEquals(Arrays.asList(2_000, 4_000), controller.mRestarts);
    }

    /**
     * A start that hangs while a load fails on its connection (FARAJV5R #46: the network was down; S4PGCB6X #10: a server's reads timed
     * out): another client streams from the same servers. The same client again, later each time.
     */
    @Test
    public void aStartThatHangsOnAFailingConnectionWaitsWithTheSameClient() throws Exception {
        TestController controller = new TestController(video("HWYxNVugkCA"), null, playerStub(null, null, 2_348_311));
        long now = SystemClock.elapsedRealtime();
        LoadErrors.onLoadError(now - 5_000, now, C.DATA_TYPE_MEDIA, C.TRACK_TYPE_VIDEO, new SocketTimeoutException("timeout"));

        controller.onLongBuffering();
        controller.onLongBuffering();
        controller.onLongBuffering();

        assertEquals(Arrays.asList(1_000, 2_000, 4_000), controller.mReloads);
        List<JSONObject> events = mLog.readAll();
        assertEquals(Arrays.asList("network_wait", "reload_video"), getActions(events.get(0)));
        assertEquals(Arrays.asList("network_wait", "reload_video_4s"), getActions(events.get(events.size() - 1)));
    }

    /**
     * Played, the waits start over
     */
    @Test
    public void aStartThatPlayedWaitsTheFirstWaitAgain() throws Exception {
        TestController controller = new TestController(video("HWYxNVugkCA"), null, playerStub(null, null, 2_348_311));
        long now = SystemClock.elapsedRealtime();
        LoadErrors.onLoadError(now - 5_000, now, C.DATA_TYPE_MEDIA, C.TRACK_TYPE_VIDEO, new SocketTimeoutException("timeout"));

        controller.onLongBuffering();
        controller.onLongBuffering();
        controller.onPlay();
        controller.onLongBuffering();

        assertEquals(Arrays.asList(1_000, 2_000, 1_000), controller.mReloads);
    }

    /**
     * A load that failed long before isn't this hang's: the client is switched, as before
     */
    @Test
    public void aConnectionErrorLongBeforeTheHangSwitchesTheClient() throws Exception {
        TestController controller = new TestController(video("HWYxNVugkCA"), null, playerStub(null, null, 2_348_311));
        long now = SystemClock.elapsedRealtime();
        LoadErrors.onLoadError(now - 65_000, now - 60_000, C.DATA_TYPE_MEDIA, C.TRACK_TYPE_VIDEO, new SocketTimeoutException("timeout"));

        controller.onLongBuffering();

        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * The lookups fail the same way with Google's DNS: it didn't help (S4PGCB6X #7–#19, and 500 ms more a lookup since). It's put
     * back, and the same client is tried again later: lookups that fail are the connection's, another client's servers are the same.
     */
    @Test
    public void theDnsFixIsUndoneWhenTheLookupsFailWithItToo() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_SYSTEM);
        TestController controller = new TestController(video("qrLjwKPRw3s"));
        lookupFails();

        controller.onLongBuffering();
        assertEquals(PlayerTweaksData.DNS_TYPE_GOOGLE, getTweaks().getPreferredDnsType());
        restarted();
        lookupFails();
        controller.onLongBuffering();

        List<JSONObject> events = mLog.readAll();
        assertEquals(Arrays.asList("dns_google", "restart_engine"), getActions(events.get(0)));
        assertEquals(Arrays.asList("dns_revert", "network_wait", "reload_video"), getActions(events.get(1)));
        assertEquals(PlayerTweaksData.DNS_TYPE_SYSTEM, getTweaks().getPreferredDnsType());
    }

    /**
     * With Google's DNS the player connects, and the start hangs anyway: the DNS fix may be what made the lookups work, it stays
     */
    @Test
    public void theDnsFixStaysWhenTheLookupsWorkWithIt() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_SYSTEM);
        TestController controller = new TestController(video("qrLjwKPRw3s"));
        lookupFails();

        controller.onLongBuffering();
        restarted();
        connectionWorks();
        controller.onLongBuffering();

        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(mLog.readAll().get(1)));
        assertEquals(PlayerTweaksData.DNS_TYPE_GOOGLE, getTweaks().getPreferredDnsType());
    }

    /**
     * Nothing told after the switch (no connection of the player): no reason to put it back
     */
    @Test
    public void theDnsFixStaysWithNothingToTell() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_SYSTEM);
        TestController controller = new TestController(video("qrLjwKPRw3s"));
        lookupFails();

        controller.onLongBuffering();
        restarted();
        controller.onLongBuffering();

        assertEquals(PlayerTweaksData.DNS_TYPE_GOOGLE, getTweaks().getPreferredDnsType());
    }

    /**
     * Undone, it isn't tried again
     */
    @Test
    public void anUndoneDnsFixIsntTriedAgain() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_SYSTEM);
        TestController controller = new TestController(video("qrLjwKPRw3s"));
        lookupFails();

        controller.onLongBuffering();
        restarted();
        lookupFails();
        controller.onLongBuffering();
        restarted();
        lookupFails();
        controller.onLongBuffering();

        List<JSONObject> events = mLog.readAll();
        // The second wait for the network, with the DNS as it was
        assertEquals(Arrays.asList("network_wait", "reload_video_2s"), getActions(events.get(events.size() - 1)));
        assertEquals(PlayerTweaksData.DNS_TYPE_SYSTEM, getTweaks().getPreferredDnsType());
    }

    /**
     * A video played after it: the DNS fix stays, and the next start whose lookups fail waits for the network
     */
    @Test
    public void aDnsFixThatPlayedStays() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_SYSTEM);
        TestController controller = new TestController(video("qrLjwKPRw3s"));
        lookupFails();

        controller.onLongBuffering();
        controller.onPlay();
        controller.onNewVideo(video("w2r-ETPghLs"));
        restarted();
        lookupFails();
        controller.onLongBuffering();

        assertEquals(Arrays.asList("network_wait", "reload_video"), getActions(mLog.readAll().get(1)));
        assertEquals(PlayerTweaksData.DNS_TYPE_GOOGLE, getTweaks().getPreferredDnsType());
    }

    /**
     * QYZPW0FN #3: the network was down and a reload's lookups were still going with no load error yet, so the stall into it had
     * nothing to go by: the client was switched, and the videos opened half an hour later still came from it. A lookup that goes on
     * is one that fails: the same client again, later.
     */
    @Test
    public void aStartWhoseLookupsHangWaitsWithTheSameClient() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_GOOGLE);
        TestController controller = new TestController(video("HWYxNVugkCA"), null, playerStub(null, null, 297_302));
        lookupHangs();

        controller.onLongBuffering();

        JSONObject event = mLog.readAll().get(0);
        assertEquals(Arrays.asList("network_wait", "reload_video"), getActions(event));
        assertEquals("failing", event.getJSONObject("details").getString("lookup"));
        assertEquals(PlayerTweaksData.DNS_TYPE_GOOGLE, getTweaks().getPreferredDnsType());
    }

    /**
     * With another DNS than Google's, lookups that hang get the DNS fix first, as ones that failed do
     */
    @Test
    public void aStartWhoseLookupsHangSwitchesTheDnsFirst() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_SYSTEM);
        TestController controller = new TestController(video("p5miU3x4Sr8"));
        lookupHangs();

        controller.onLongBuffering();

        JSONObject event = mLog.readAll().get(0);
        assertEquals(Arrays.asList("dns_google", "restart_engine"), getActions(event));
        assertEquals("failing", event.getJSONObject("details").getString("lookup"));
    }

    /**
     * The fetch didn't connect (FARAJV5R #47–#99: 55 a minute for 15 minutes): tried again later each time, up to 30 s, and told once
     */
    @Test
    public void aFetchThatFailsOnTheNetworkIsTriedAgainLaterEachTime() throws Exception {
        TestController controller = new TestController(video("HWYxNVugkCA"));

        for (int i = 0; i < 7; i++) {
            controller.runFormatErrorAction(new IllegalStateException(i % 2 == 0
                    ? new SocketTimeoutException("failed to connect to www.youtube.com after 5000ms")
                    : new SSLHandshakeException("Trust anchor for certification path not found.")));
        }

        assertEquals(Arrays.asList(1_000, 2_000, 4_000, 8_000, 16_000, 30_000, 30_000), controller.mReloads);
        assertEquals(1, ShadowToast.shownToastCount());
        List<JSONObject> events = mLog.readAll();
        assertEquals(Arrays.asList("network_wait", "reload_video"), getActions(events.get(0)));
    }

    /**
     * The poToken's BotGuard request couldn't connect (see PoTokenWebView#keepInitError): the network's, told by its cause. The client
     * isn't switched (FARAJV5R #60, #61).
     */
    @Test
    public void aPoTokenRequestThatCouldntConnectIsTheNetworks() throws Exception {
        TestController controller = new TestController(video("HWYxNVugkCA"));

        controller.runFormatErrorAction(new IllegalStateException("Interrupted OkHttp request to https://www.youtube.com/api/jnn/v1/Create",
                new ConnectException("ECONNREFUSED")));

        assertEquals(Arrays.asList("network_wait", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * A name that didn't resolve is the network's: the DNS isn't switched to IPv4 and the app isn't restarted for it. Every DNS type
     * ends in the system's lookup, which had just failed.
     */
    @Test
    public void aNameThatDidntResolveIsTheNetworks() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_GOOGLE);
        TestController controller = new TestController(video("HWYxNVugkCA"));

        controller.runFormatErrorAction(new UnknownHostException("Unable to resolve host \"www.youtube.com\": No address associated with hostname"));

        assertEquals(Arrays.asList("network_wait", "reload_video"), getActions(mLog.readAll().get(0)));
        assertEquals(PlayerTweaksData.DNS_TYPE_GOOGLE, getTweaks().getPreferredDnsType());
    }

    /**
     * A broken WebView after a fetch that couldn't connect is still a broken WebView: what failed before tells nothing of it
     */
    @Test
    public void aBrokenWebViewAfterTheNetworkFailedIsTheWebViews() throws Exception {
        TestController controller = new TestController(video("HWYxNVugkCA"));

        controller.runFormatErrorAction(new IllegalStateException(new SocketTimeoutException("failed to connect")));
        controller.runFormatErrorAction(new BadWebViewException("Uncaught SyntaxError: Unexpected token"));

        List<JSONObject> events = mLog.readAll();
        assertEquals(Arrays.asList("switch_next_client:WEB", "reload_video"), getActions(events.get(events.size() - 1)));
    }

    /**
     * Every client failed to connect, and the fetch's error says only that no formats came: the network's (a dead proxy: each
     * attempt refused, which used to read null_response)
     */
    @Test
    public void everyClientThatCouldntConnectIsTheNetworks() throws Exception {
        ClientAttempts.clear("offline");
        ClientAttempts.record("offline", ClientAttempts.Attempt.exception("TV", new ConnectException("ECONNREFUSED")));
        ClientAttempts.record("offline", ClientAttempts.Attempt.exception("WEB", new IllegalStateException(
                new ConnectException("ECONNREFUSED"))));
        TestController controller = new TestController(video("offline"));

        controller.runFormatErrorAction(new IllegalStateException("fromNullable result is null"));

        assertEquals(Arrays.asList("network_wait", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * One client got an answer: not the network's, whatever the others did
     */
    @Test
    public void oneClientThatGotAnAnswerIsntTheNetworks() throws Exception {
        ClientAttempts.clear("partly");
        ClientAttempts.record("partly", ClientAttempts.Attempt.exception("TV", new ConnectException("ECONNREFUSED")));
        ClientAttempts.record("partly", ClientAttempts.Attempt.http("WEB", 500));
        TestController controller = new TestController(video("partly"));

        controller.runFormatErrorAction(new IllegalStateException("fromNullable result is null"));

        assertEquals(Collections.singletonList("reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * With the network up, a poToken error is the client's, as before
     */
    @Test
    public void aPoTokenErrorSwitchesTheClient() throws Exception {
        TestController controller = new TestController(video("HWYxNVugkCA"));

        controller.runFormatErrorAction(new BadWebViewException("Error invoking downloadAndRunBotguard"));

        assertEquals(Arrays.asList("switch_next_client:WEB", "reload_video"), getActions(mLog.readAll().get(0)));
    }

    /**
     * Its formats came: a fetch that fails after that is tried again at once
     */
    @Test
    public void theFetchWaitsStartOverWhenTheFormatsCame() throws Exception {
        Video video = video("HWYxNVugkCA");
        TestController controller = new TestController(video);

        controller.runFormatErrorAction(new IllegalStateException(new SocketTimeoutException("failed to connect")));
        controller.runFormatErrorAction(new IllegalStateException(new SocketTimeoutException("failed to connect")));
        controller.onSourceChanged(video);
        controller.runFormatErrorAction(new IllegalStateException(new SocketTimeoutException("failed to connect")));

        assertEquals(Arrays.asList(1_000, 2_000, 1_000), controller.mReloads);
    }

    /**
     * 97Z1YWD3, signed out: the server held back each client's media at 0:59.9 with the attestation pending, and the app went from client
     * to client, a reload each time, for minutes. Once a client comes round again, every client had its try: no reload, and the user is
     * told to sign in.
     */
    @Test
    public void theAttestationWallStopsOnceEveryClientHadItsTry() throws Exception {
        TestController controller = new TestController(video("8aJEcMj1AOg"), "WEB", playerStub(atMs(59_917, 600_000)));
        controller.mProtocol = PlayerFailure.PROTOCOL_SABR;

        for (String client : Arrays.asList("WEB", "WEB_SAFARI", "MWEB", "WEB")) {
            controller.mClient = client;
            controller.onPlay();
            controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                    new UnexpectedLoaderException(new PoTokenError(PoTokenStatus.PENDING)));
        }

        // The same failure again: the first and the last of the repeats
        List<JSONObject> events = mLog.readAll();
        JSONObject first = events.get(0);
        JSONObject last = events.get(events.size() - 1);
        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(first));
        assertEquals("PENDING", first.getJSONObject("details").getString("poTokenStatus"));
        assertEquals(Arrays.asList("attestation_stop_sign_in"), getActions(last));
        assertEquals(Arrays.asList("WEB", "WEB_SAFARI", "MWEB"), toList(last.getJSONObject("details").getJSONArray("wallClients")));
        assertEquals(3, controller.mReloads.size());
        assertEquals(RuntimeEnvironment.getApplication().getString(com.liskovsoft.smartyoutubetv2.common.R.string.msg_attestation_sign_in),
                ShadowToast.getTextOfLatestToast());
    }

    /**
     * Signed in, and no client asked for a sign-in: the wall is told, not a sign-in. A client that asked for one (a bot check) is told
     * to sign in, signed in or not.
     */
    @Test
    public void theAttestationWallSaysToSignInOnlyWhenItWasAskedFor() throws Exception {
        ClientAttempts.clear("Gp_c7blMwZE");
        TestController controller = new TestController(video("Gp_c7blMwZE"), "WEB", playerStub(atMs(59_944, 600_000)));
        controller.mProtocol = PlayerFailure.PROTOCOL_SABR;
        controller.mIsSignedIn = true;

        for (String client : Arrays.asList("WEB", "WEB")) {
            controller.mClient = client;
            controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                    new UnexpectedLoaderException(new PoTokenError(PoTokenStatus.PENDING)));
        }

        List<JSONObject> events = mLog.readAll();
        assertEquals(Arrays.asList("attestation_stop"), getActions(events.get(events.size() - 1)));

        ClientAttempts.record("Gp_c7blMwZE", ClientAttempts.Attempt.unplayable("TV", "LOGIN_REQUIRED",
                "Sign in to confirm you're not a bot"));

        for (String client : Arrays.asList("WEB", "WEB")) {
            controller.mClient = client;
            controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                    new UnexpectedLoaderException(new PoTokenError(PoTokenStatus.PENDING)));
        }

        events = mLog.readAll();
        assertEquals(Arrays.asList("attestation_stop_sign_in"), getActions(events.get(events.size() - 1)));
        ClientAttempts.clear("Gp_c7blMwZE");
    }

    /**
     * A client that played well past where the last one stopped got past the wall: the next stop is a lap of its own
     */
    @Test
    public void aClientThatPlayedPastTheWallStartsAnotherLap() throws Exception {
        Map<String, Object> answers = atMs(59_917, 600_000);
        TestController controller = new TestController(video("8aJEcMj1AOg"), "WEB", playerStub(answers));

        for (String client : Arrays.asList("WEB", "WEB_SAFARI", "MWEB")) {
            controller.mClient = client;
            controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                    new UnexpectedLoaderException(new PoTokenError(PoTokenStatus.PENDING)));
        }

        answers.put("getPositionMs", 300_000L);
        controller.mClient = "WEB";
        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new UnexpectedLoaderException(new PoTokenError(PoTokenStatus.PENDING)));

        List<JSONObject> events = mLog.readAll();
        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(events.get(events.size() - 1)));
        assertEquals(4, controller.mReloads.size());
    }

    /**
     * 97Z1YWD3 #13: reloaded at 0:59.9, the picture and the clock stood still with the attestation pending, and nothing was done for
     * 58 s. The next client, as for a stall of it.
     */
    @Test
    public void aStillClockWithTheAttestationPendingSwitchesTheClient() throws Exception {
        TestController controller = new TestController(video("8aJEcMj1AOg"), "WEB", playerStub(atMs(59_921, 600_000)));
        controller.mProtocol = PlayerFailure.PROTOCOL_SABR;
        controller.onPlay();
        ProtectionStatuses.onStatus("8aJEcMj1AOg", PoTokenStatus.PENDING);
        // Its grace period over: pending since the start, at 0:59.9
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofMillis(59_921));
        PlayerFailure failure = PlayerFailure.create(RuntimeEnvironment.getApplication(), PlaybackHealth.KIND_VIDEO_FROZEN)
                .detail("clockStill", true);

        controller.onHealthFailure(PlaybackHealth.KIND_VIDEO_FROZEN, failure);
        failure.record();

        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(mLog.readAll().get(0)));
        assertEquals(1, controller.mReloads.size());
    }

    /**
     * 65FDWR9C #1: a SABR read timed out ("Unable to connect") with the attestation pending since the long pause: the next client, as a
     * stall of it gets, not the engine restarted with the same client (it played 0 ms)
     */
    @Test
    public void aSabrTimeoutWithTheAttestationPendingSwitchesTheClient() throws Exception {
        TestController controller = new TestController(video("oKgDcmHScsM"), "WEB");
        controller.mProtocol = PlayerFailure.PROTOCOL_SABR;
        controller.onPlay();
        ProtectionStatuses.onStatus("oKgDcmHScsM", PoTokenStatus.PENDING);
        // Its grace period over: pending 58.6 s before the error
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofMillis(58_600));

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN, unableToConnect());

        assertEquals(Arrays.asList("switch_next_client_now", "reload_video"), getActions(mLog.readAll().get(0)));
        assertEquals(0, controller.mRestarts.size());
    }

    /**
     * R71TW3F4 #1-#13: WEB's server timed out every 40 s, after the video played each time, and the engine was restarted 13 times with
     * nothing changed and a toast each. Reloaded without a word, three times for the video, then the next client for it.
     */
    @Test
    public void aConnectionThatBreaksAfterItPlayedIsReloadedThenMovesTheVideo() throws Exception {
        getGlobalPrefs().setIpv6TestEnabled(true);
        setIpv6Working(false);
        TestController controller = new TestController(video("zb6EkKExF_8"), "WEB");
        controller.mNextClient = "TV";

        for (int i = 0; i < 4; i++) {
            controller.onPlay();
            controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN, unableToConnect());
        }

        // The same failure again: the first and the last of the repeats
        List<JSONObject> events = mLog.readAll();
        assertEquals(Arrays.asList("network_reload 1/3", "reload_video"), getActions(events.get(0)));
        assertEquals(Arrays.asList("client_for_video:TV", "reload_video"), getActions(events.get(events.size() - 1)));
        assertEquals(0, ShadowToast.shownToastCount());
        assertEquals(0, controller.mRestarts.size());
        assertEquals(4, controller.mReloads.size());
    }

    /**
     * With IPv6 working, the first one switches the engine as before (a restart), the ones after it are only reloaded
     */
    @Test
    public void aConnectionThatBreaksAfterItPlayedSwitchesTheEngineOnce() throws Exception {
        getTweaks().setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT);
        TestController controller = new TestController(video("zb6EkKExF_8"), "WEB");

        controller.onPlay();
        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN, unableToConnect());
        controller.onPlay();
        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN, unableToConnect());

        List<JSONObject> events = mLog.readAll();
        assertEquals(Arrays.asList("network_reload 1/3", "switch_engine:" + Utils.NETWORK_ENGINE_OKHTTP, "restart_engine"),
                getActions(events.get(0)));
        assertEquals(Arrays.asList("network_reload 2/3", "reload_video"), getActions(events.get(1)));
        assertEquals(1, controller.mRestarts.size());
    }

    /**
     * 3TKGJY3Z #3: Cronet's request on a Wi-Fi that dropped (ERR_INTERNET_DISCONNECTED) set the default engine for good. The network's:
     * waited out with the engine kept, longer each time till it plays.
     */
    @Test
    public void aCronetRequestWithNoNetworkWaitsWithTheEngineKept() throws Exception {
        TestController controller = new TestController(video("cszzGTz_Fls"), "TV");
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN, cronetDisconnected());
        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN, cronetDisconnected());

        List<JSONObject> events = mLog.readAll();
        assertEquals(Arrays.asList("network_wait", "reload_video"), getActions(events.get(0)));
        assertEquals(Arrays.asList("network_wait", "reload_video_2s"), getActions(events.get(events.size() - 1)));
        assertEquals(PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP, getTweaks().getPlayerDataSource());
        assertEquals(Arrays.asList(1_000, 2_000), controller.mReloads);
        assertEquals(0, controller.mRestarts.size());
        assertEquals(0, ShadowToast.shownToastCount());
    }

    /**
     * 3TKGJY3Z #4: with no network, the default engine's name that didn't resolve switched the engine to OkHttp, saved. Waited out.
     */
    @Test
    public void aRequestThatCouldntConnectWithNoNetworkKeepsTheEngine() throws Exception {
        getTweaks().setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT);
        TestController controller = new TestController(video("cszzGTz_Fls"), "TV");
        controller.mHasNetwork = false;
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                new HttpDataSourceException("Unable to connect to https://www.youtube.com/api/timedtext",
                        new UnknownHostException("Unable to resolve host \"www.youtube.com\""), dataSpec(),
                        HttpDataSourceException.TYPE_OPEN));

        assertEquals(Arrays.asList("network_wait", "reload_video"), getActions(mLog.readAll().get(0)));
        assertEquals(PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT, getTweaks().getPlayerDataSource());
        assertEquals(0, controller.mRestarts.size());
    }

    /**
     * Cronet's request that timed out with the network up: its engine's, fixed as before
     */
    @Test
    public void aCronetTimeoutWithTheNetworkUpIsFixedAsBefore() throws Exception {
        TestController controller = new TestController(video("cszzGTz_Fls"), "TV");
        controller.onPlay();

        controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                cronetError(NetworkException.ERROR_TIMED_OUT, "ERR_TIMED_OUT"));

        assertEquals(Arrays.asList("engine_default", "restart_engine"), getActions(mLog.readAll().get(0)));
        assertEquals(PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT, getTweaks().getPlayerDataSource());
    }

    /**
     * A source's error tells what its loads did: the ones that failed since it last played, each try's time and error, and the number
     * of the server its request went to (R71TW3F4 #1-#13 told none of it)
     */
    @Test
    public void aSourceErrorTellsTheLoadsThatFailedSinceItPlayed() throws Exception {
        // No engine to switch to: OkHttp's next is Cronet, which can't load under Robolectric
        getGlobalPrefs().setIpv6TestEnabled(true);
        setIpv6Working(false);

        try {
            FormatAttempts.onNewVideo(RuntimeEnvironment.getApplication(), "zb6EkKExF_8");
            FormatAttempts.onRequest("rr1---sn-other.googlevideo.com");
            FormatAttempts.onRequest(HOST);
            TestController controller = new TestController(video("zb6EkKExF_8"), "WEB");
            controller.onPlay();
            long now = SystemClock.elapsedRealtime();
            LoadErrors.onLoadError(now - 4_000, now, C.DATA_TYPE_MEDIA, C.TRACK_TYPE_VIDEO, new SocketTimeoutException("timeout"));

            controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN, unableToConnect());

            JSONObject details = mLog.readAll().get(0).getJSONObject("details");
            JSONObject loadError = details.getJSONArray("loadErrors").getJSONObject(0);
            assertEquals("video chunk", loadError.getString("load"));
            assertEquals("timed out", loadError.getString("error"));
            assertEquals(4_000, loadError.getLong("ms"));
            assertEquals(2, details.getInt("hostNumber"));
            assertTrue(details.has("bufferedMs"));
        } finally {
            FormatAttempts.end(RuntimeEnvironment.getApplication());
        }
    }

    /**
     * WVE7ZKDD #2: SABR refused a rewind with a 403, protection OK, and switch_next_client made TV's poToken again (2.2 s of a 5.3 s
     * reload), reading as a switch. Its player response is fetched again first, with the same poToken; refused again soon, the poToken
     * is made again, and the action says so. The refused request is told, as a SabrError's is.
     */
    @Test
    public void aSabrRefusalAfterItPlayedFetchesThePlayerResponseAgainFirst() throws Exception {
        try {
            TestController controller = new TestController(video("dQw4w9WgXcQ"), "TV");
            controller.mProtocol = PlayerFailure.PROTOCOL_SABR;
            controller.mSwitchedTo = null;
            InvalidResponseCodeException refusal = new InvalidResponseCodeException(403, "Forbidden", Collections.emptyMap(), dataSpec());
            Method onLoadError = SabrRefusedRequest.class.getDeclaredMethod("onLoadError", Throwable.class, String.class, long.class);
            onLoadError.setAccessible(true);
            onLoadError.invoke(null, refusal, "media, video 313, audio 251, selected 313, session 0 (first)", 87_000L);

            controller.onPlay();
            controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN, refusal);
            controller.onPlay();
            controller.onEngineError(PlayerEventListener.ERROR_TYPE_SOURCE, PlayerEventListener.RENDERER_INDEX_UNKNOWN,
                    new InvalidResponseCodeException(403, "Forbidden", Collections.emptyMap(), dataSpec()));

            List<JSONObject> events = mLog.readAll();
            assertEquals(Arrays.asList("refetch_player", "reload_video"), getActions(events.get(0)));
            assertEquals(Arrays.asList("reset_potoken:TV", "reload_video"), getActions(events.get(events.size() - 1)));
            assertEquals(1, controller.mSwitches);
            JSONObject details = events.get(0).getJSONObject("details");
            assertEquals("media, video 313, audio 251, selected 313, session 0 (first)", details.getString("sabrRequest"));
            assertEquals(87_000, details.getLong("sabrSessionAgeMs"));
        } finally {
            SabrRefusedRequest.resetForTesting();
        }
    }

    /**
     * Y1D66CNW #1: a start's picture stood still 3.5 s through a tunneled decoder after a restore seek, the device's fourth such freeze,
     * and the fallback heard of none of them. It's over: counted, without a restart (the next player is made without it).
     */
    @Test
    public void aTunneledStartFreezeTurnsTunnelingOffWithoutARestart() throws Exception {
        assertTunnelingOffWithoutARestart(PlaybackHealth.KIND_START_FROZEN, PlayerFailure.create(RuntimeEnvironment.getApplication(),
                PlaybackHealth.KIND_START_FROZEN).detail("clockStill", false));
    }

    /**
     * Paused by the user while the picture stood still (see PlaybackHealth): counted, and not restarted, which would play it
     */
    @Test
    public void aTunneledFreezeEndedByAPauseTurnsTunnelingOffWithoutARestart() throws Exception {
        assertTunnelingOffWithoutARestart(PlaybackHealth.KIND_VIDEO_FROZEN, PlayerFailure.create(RuntimeEnvironment.getApplication(),
                PlaybackHealth.KIND_VIDEO_FROZEN).detail("endedByPause", true));
    }

    private void assertTunnelingOffWithoutARestart(String kind, PlayerFailure failure) throws Exception {
        TunnelingFallback.reset(RuntimeEnvironment.getApplication());
        VideoDecoderState.onSample(317, -1, true, null);

        try {
            TestController controller = new TestController(video("31GPth14P88"));
            controller.onPlay();

            controller.onHealthFailure(kind, failure);
            failure.record();

            assertEquals(Arrays.asList("tunneling_off"), getActions(mLog.readAll().get(0)));
            assertEquals(0, controller.mRestarts.size());
            assertTrue(TunnelingFallback.isOff(RuntimeEnvironment.getApplication()));
        } finally {
            VideoDecoderState.reset();
            TunnelingFallback.reset(RuntimeEnvironment.getApplication());
        }
    }

    /**
     * 93HXRTB5 #1: 3 stalls of a 4K VP9 lowered the quality, and nothing told whether the connection was too slow for it. Told as a long
     * stall's are.
     */
    @Test
    public void healthStallsTellHowFastTheLoadsCame() throws Exception {
        try {
            playingAt(110_468_000, 8_321);
            TestController controller = new TestController(video("0N4ujc0HJus"), QUALITIES, QUALITIES.get(0));
            controller.onPlay();
            PlayerFailure failure = PlayerFailure.create(RuntimeEnvironment.getApplication(), PlaybackHealth.KIND_STALLS);

            controller.onHealthFailure(PlaybackHealth.KIND_STALLS, failure);
            failure.record();

            JSONObject event = mLog.readAll().get(0);
            assertEquals(8_321, event.getJSONObject("details").getLong("recentThroughputKbps"));
            assertEquals(Arrays.asList("lower_quality", "restart_engine"), getActions(event));
        } finally {
            FormatAttempts.end(RuntimeEnvironment.getApplication());
        }
    }

    /**
     * H6ZWKZTQ #1: a high-bitrate 4K60, 45.3 Mbps on average, stalled 4 times in 2 minutes at 55.5 Mbps, with no fix. What it played
     * then needed more: the throughput is to keep up with the segments loaded lately, 80 Mbps here.
     */
    @Test
    public void healthStallsOnSegmentsAboveTheThroughputLowerTheQuality() throws Exception {
        try {
            playingAt(45_322_000, 55_545);
            // 10 s of the video in 100 MB, loaded at 55.5 Mbps
            FormatAttempts.onLoad(50_000_000, 7_200, true, false, 90_000, 95_000);
            FormatAttempts.onLoad(50_000_000, 7_200, true, false, 95_000, 100_000);

            JSONObject event = failHealthStalls();

            assertEquals(80_000, event.getJSONObject("details").getLong("recentVideoBitrateKbps"));
            assertEquals(Arrays.asList("lower_quality", "restart_engine"), getActions(event));
        } finally {
            FormatAttempts.end(RuntimeEnvironment.getApplication());
        }
    }

    /**
     * Too little of the video loaded lately to tell its bitrate there: the format's own goes, as before
     */
    @Test
    public void healthStallsOnTooLittleOfTheVideoGoByTheFormatsBitrate() throws Exception {
        try {
            playingAt(45_322_000, 55_545);
            FormatAttempts.onLoad(30_000_000, 4_320, true, false, 90_000, 93_000);

            JSONObject event = failHealthStalls();

            assertTrue(event.getJSONObject("details").isNull("recentVideoBitrateKbps"));
            assertEquals(Collections.emptyList(), getActions(event));
        } finally {
            FormatAttempts.end(RuntimeEnvironment.getApplication());
        }
    }

    /**
     * A health "stalls" failure of a 4K VP9 playing, recorded
     */
    private JSONObject failHealthStalls() throws Exception {
        TestController controller = new TestController(video("0N4ujc0HJus"), QUALITIES, QUALITIES.get(0));
        controller.onPlay();
        PlayerFailure failure = PlayerFailure.create(RuntimeEnvironment.getApplication(), PlaybackHealth.KIND_STALLS);

        controller.onHealthFailure(PlaybackHealth.KIND_STALLS, failure);
        failure.record();

        return mLog.readAll().get(0);
    }

    /**
     * Z6R2NPPX #6: AV1 1080p was lowered to VP9 720p, the list's first, though AV1 720p was offered: the codec playing
     */
    @Test
    public void theQualityLoweredKeepsTheCodecPlaying() throws Exception {
        List<FormatItem> formats = Arrays.asList(
                ExoFormatItem.fromVideoSpec("1920,1080,25,av01", false),
                ExoFormatItem.fromVideoSpec("1280,720,25,vp9", false),
                ExoFormatItem.fromVideoSpec("1280,720,25,av01", false));

        assertEquals(codecOf(formats.get(2)), codecOf(lowerOnRepeatedStall(formats)));
    }

    /**
     * Not offered at the next size down: the one of the fewest bits a second there, not the list's first
     */
    @Test
    public void theQualityLoweredToAnotherCodecIsTheSmallest() throws Exception {
        List<FormatItem> formats = Arrays.asList(
                videoFormat("av01.0.08M.08", "video/av01", 2_352_000, 1920, 1080),
                videoFormat("vp09.00.31.08", "video/x-vnd.on2.vp9", 1_213_000, 1280, 720),
                videoFormat("avc1.4d401f", "video/avc", 900_000, 1280, 720));

        assertEquals(codecOf(formats.get(2)), codecOf(lowerOnRepeatedStall(formats)));
    }

    /**
     * The format of the lowered quality, the limit a repeated stall sets (see ErrorFixerController#lowerVideoQuality)
     */
    private FormatItem lowerOnRepeatedStall(List<FormatItem> formats) throws Exception {
        TestController controller = new TestController(video("uiSWOWttNvo"), formats, formats.get(0));
        controller.onPlay();
        controller.onLongBuffering();
        controller.onPlay();
        controller.onLongBuffering();

        FormatItem lowered = getPlayerData().getTempVideoFormat();
        assertEquals(720, lowered.getHeight());

        return lowered;
    }

    private static String codecOf(FormatItem format) {
        return TrackSelectorUtil.codecNameShort(format.getTrack().format.codecs);
    }

    private static FormatItem videoFormat(String codecs, String mimeType, int bitrate, int width, int height) {
        MediaTrack track = MediaTrack.forRendererIndex(TrackSelectorManager.RENDERER_INDEX_VIDEO);
        track.format = Format.createVideoSampleFormat(null, mimeType, codecs, bitrate, Format.NO_VALUE, width, height, 25, null, null);

        return ExoFormatItem.from(track);
    }

    /**
     * CQHM6BGP #1: a 2-minute silent intro was reported silent from its start. On Opus, heard on the device before, it would have been
     * swapped to AAC and the engine restarted for nothing: left as it is.
     */
    @Test
    public void aSilentStartOfACodecHeardBeforeIsLeftAlone() throws Exception {
        MediaTrack track = MediaTrack.forRendererIndex(TrackSelectorManager.RENDERER_INDEX_AUDIO);
        track.format = Format.createAudioSampleFormat("251", "audio/opus", "opus", 146_000, Format.NO_VALUE, 2, 48_000, null, null, 0,
                "en");
        TestController controller = new TestController(video("j1LaXgDlqZs"), null,
                playerStub(Collections.<String, Object>singletonMap("getAudioFormat", ExoFormatItem.from(track))));
        SoundCodecs.onSound(RuntimeEnvironment.getApplication(), "audio/opus");
        controller.onPlay();
        PlayerFailure failure = PlayerFailure.create(RuntimeEnvironment.getApplication(), PlaybackHealth.KIND_AUDIO_SILENT)
                .detail("silentFromStart", true);

        controller.onHealthFailure(PlaybackHealth.KIND_AUDIO_SILENT, failure);
        failure.record();

        JSONObject event = mLog.readAll().get(0);
        assertEquals(Collections.<String>emptyList(), getActions(event));
        assertTrue(event.getJSONObject("details").has("codecHeardAgoMs"));
        assertEquals(0, controller.mRestarts.size());
    }

    /**
     * Another codec heard: this one's silence may be the device's, AAC is tried as before
     */
    @Test
    public void aSilentStartOfACodecNotHeardPlaysAac() throws Exception {
        MediaTrack track = MediaTrack.forRendererIndex(TrackSelectorManager.RENDERER_INDEX_AUDIO);
        track.format = Format.createAudioSampleFormat("251", "audio/opus", "opus", 146_000, Format.NO_VALUE, 2, 48_000, null, null, 0,
                "en");
        TestController controller = new TestController(video("j1LaXgDlqZs"), null,
                playerStub(Collections.<String, Object>singletonMap("getAudioFormat", ExoFormatItem.from(track))));
        SoundCodecs.onSound(RuntimeEnvironment.getApplication(), "audio/mp4a-latm");
        controller.onPlay();
        PlayerFailure failure = PlayerFailure.create(RuntimeEnvironment.getApplication(), PlaybackHealth.KIND_AUDIO_SILENT)
                .detail("silentFromStart", true);

        controller.onHealthFailure(PlaybackHealth.KIND_AUDIO_SILENT, failure);
        failure.record();

        assertEquals(Arrays.asList("audio_fallback:mp4a", "restart_engine"), getActions(mLog.readAll().get(0)));
    }

    /**
     * "Unable to connect to …" from a read that timed out, as the player fails with it
     */
    private static HttpDataSourceException cronetDisconnected() {
        return cronetError(NetworkException.ERROR_INTERNET_DISCONNECTED, "ERR_INTERNET_DISCONNECTED");
    }

    /**
     * As CronetDataSource throws Cronet's error (3TKGJY3Z #3): its message the cause's, "...: Exception in CronetUrlRequest: ..."
     */
    private static HttpDataSourceException cronetError(int errorCode, String name) {
        NetworkException cause = new NetworkException(String.format(Locale.US,
                "Exception in CronetUrlRequest: net::%s, ErrorCode=%d, InternalErrorCode=-106, Retryable=false", name, errorCode), null) {
            @Override
            public int getErrorCode() {
                return errorCode;
            }

            @Override
            public int getCronetInternalErrorCode() {
                return -106;
            }

            @Override
            public boolean immediatelyRetryable() {
                return false;
            }
        };

        return new CronetDataSource.OpenException(cause, dataSpec(), 0);
    }

    private static HttpDataSourceException unableToConnect() {
        return new HttpDataSourceException("Unable to connect to https://" + HOST + "/videoplayback",
                new SocketTimeoutException("timeout"), dataSpec(), HttpDataSourceException.TYPE_OPEN);
    }

    private static List<String> toList(JSONArray array) throws Exception {
        List<String> result = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            result.add(array.getString(i));
        }

        return result;
    }

    /**
     * Named as the WebView's (see PoTokenWebView): the fix tells it by its name
     */
    private static class BadWebViewException extends RuntimeException {
        BadWebViewException(String message) {
            super(message);
        }
    }

    /**
     * A connection of the player whose name didn't resolve
     */
    private static void lookupFails() {
        Call call = newCall();
        EventListener listener = Connections.LISTENER.create(call);
        listener.dnsStart(call, HOST);
        listener.callFailed(call, new UnknownHostException(HOST));
    }

    /**
     * A connection of the player whose lookup has gone on past the time one answers in (see Connections#LOOKUP_STUCK_MS)
     */
    private static void lookupHangs() {
        Call call = newCall();
        EventListener listener = Connections.LISTENER.create(call);
        listener.dnsStart(call, HOST);
        // Robolectric's clock moves on with the main looper
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
    }

    /**
     * A connection of the player where every address the lookup gave timed out
     */
    private static void addressesFail() throws Exception {
        Call call = newCall();
        EventListener listener = Connections.LISTENER.create(call);
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByAddress(new byte[]{(byte) 142, (byte) 250, 1, 1}), 443);
        listener.dnsStart(call, HOST);
        listener.dnsEnd(call, HOST, Collections.singletonList(address.getAddress()));
        listener.connectStart(call, address, java.net.Proxy.NO_PROXY);
        listener.connectFailed(call, address, java.net.Proxy.NO_PROXY, null, new SocketTimeoutException("connect timed out"));
        listener.callFailed(call, new SocketTimeoutException("connect timed out"));
    }

    /**
     * A connection of the player that connected
     */
    private static void connectionWorks() throws Exception {
        Call call = newCall();
        EventListener listener = Connections.LISTENER.create(call);
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByAddress(new byte[]{(byte) 142, (byte) 250, 1, 2}), 443);
        listener.dnsStart(call, HOST);
        listener.dnsEnd(call, HOST, Collections.singletonList(address.getAddress()));
        listener.connectStart(call, address, java.net.Proxy.NO_PROXY);
        listener.connectEnd(call, address, java.net.Proxy.NO_PROXY, Protocol.HTTP_1_1);
        listener.callEnd(call);
    }

    /**
     * The engine restarted (e.g. by the DNS fix): the player is prepared with a new source, its connections are the new ones
     */
    private static void restarted() {
        // Robolectric's clock moves on with the main looper
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        Connections.onPrepare();
    }

    private static Call newCall() {
        return new OkHttpClient().newCall(new Request.Builder().url("https://" + HOST + "/videoplayback").build());
    }

    private static PlayerData getPlayerData() {
        return PlayerData.instance(RuntimeEnvironment.getApplication());
    }

    private static void loadAudioOnly() {
        VideoStarts.onPrepare();
        long now = SystemClock.elapsedRealtime();
        VideoStarts.onLoadStarted(true, true, false, now);
        VideoStarts.onLoadStarted(true, false, true, now);
        VideoStarts.onLoad(true, false, true, now, now + 300, 2_000);
        VideoStarts.onLoadStarted(false, false, true, now + 300);
        VideoStarts.onLoad(false, false, true, now + 300, now + 500, 145_000);
    }

    private static DataSpec dataSpec() {
        return new DataSpec(Uri.parse("https://rr1---sn-test.googlevideo.com/videoplayback"));
    }

    private static List<String> getActions(JSONObject event) throws Exception {
        JSONArray actions = event.getJSONArray("actions");
        List<String> result = new ArrayList<>();

        for (int i = 0; i < actions.length(); i++) {
            result.add(actions.getString(i));
        }

        return result;
    }

    private static Video video(String videoId) {
        Video video = new Video();
        video.videoId = videoId;
        return video;
    }

    /**
     * Kept by PoTokenGate till the app restarts
     */
    private static void setTvPotRejected(boolean rejected) throws Exception {
        Field field = PoTokenGate.class.getDeclaredField("mTvPotRejected");
        field.setAccessible(true);
        field.setBoolean(null, rejected);
    }

    private static boolean isTvPotRejected() throws Exception {
        Field field = PoTokenGate.class.getDeclaredField("mTvPotRejected");
        field.setAccessible(true);
        return field.getBoolean(null);
    }

    /**
     * As the IPv6 test found it (see Ipv6CheckDns): null not tested
     */
    private static void setIpv6Working(Boolean isWorking) throws Exception {
        Field field = Class.forName("com.liskovsoft.sharedutils.okhttp.Ipv6CheckDns").getDeclaredField("sIsIpv6Working");
        field.setAccessible(true);
        field.set(null, isWorking);
    }

    private static PlayerTweaksData getTweaks() {
        return PlayerTweaksData.instance(RuntimeEnvironment.getApplication());
    }

    private static GlobalPreferences getGlobalPrefs() {
        return GlobalPreferences.instance(RuntimeEnvironment.getApplication());
    }

    /**
     * Returns the defaults (false, 0, null) for every call, and the subtitles off
     */
    private static PlaybackView playerStub() {
        return playerStub(null, null);
    }

    private static PlaybackView playerStub(List<FormatItem> videoFormats, FormatItem videoFormat) {
        return playerStub(videoFormats, videoFormat, 0);
    }

    /**
     * @param playWhenReady it plays, or is to once it's ready
     */
    private static PlaybackView playerStub(boolean playWhenReady) {
        return playerStub(Collections.singletonMap("getPlayWhenReady", playWhenReady));
    }

    /**
     * {@link #playerStub()} with these answers, by method name
     */
    private static PlaybackView playerStub(Map<String, Object> answers) {
        PlaybackView stub = playerStub();

        return (PlaybackView) Proxy.newProxyInstance(PlaybackView.class.getClassLoader(), new Class<?>[]{PlaybackView.class},
                (proxy, method, args) -> answers.containsKey(method.getName()) ? answers.get(method.getName()) : method.invoke(stub, args));
    }

    /**
     * @param positionMs where the playback is, e.g. a resumed video's saved position
     */
    private static PlaybackView playerStub(List<FormatItem> videoFormats, FormatItem videoFormat, long positionMs) {
        return (PlaybackView) Proxy.newProxyInstance(PlaybackView.class.getClassLoader(), new Class<?>[]{PlaybackView.class},
                (proxy, method, args) -> {
                    Class<?> type = method.getReturnType();

                    if ("getPositionMs".equals(method.getName())) {
                        return positionMs;
                    } else if ("getPlayWhenReady".equals(method.getName())) {
                        // It plays, as a stalled player does (see onLongBuffering): a paused one is asked for by the test
                        return true;
                    } else if ("getVideoFormats".equals(method.getName())) {
                        return videoFormats;
                    } else if ("getVideoFormat".equals(method.getName())) {
                        return videoFormat;
                    } else if ("getSubtitleFormat".equals(method.getName())) {
                        // Off: long loading subtitles have a fix of their own
                        return FormatItem.SUBTITLE_NONE;
                    } else if (type == boolean.class) {
                        return false;
                    } else if (type == int.class) {
                        return 0;
                    } else if (type == long.class) {
                        return 0L;
                    } else if (type == float.class) {
                        return 0f;
                    } else if (type == double.class) {
                        return 0d;
                    }

                    return null;
                });
    }

    private static class TestController extends ErrorFixerController {
        private final PlaybackView mPlayer;
        private final Video mVideo;
        private int mPlayEnds;
        /**
         * The waits of the reloads and the engine restarts asked for, in order
         */
        private final List<Integer> mReloads = new ArrayList<>();
        private final List<Integer> mRestarts = new ArrayList<>();
        /**
         * The reloads and restarts asked for that were taken back (see VideoLoaderController#cancelPendingFixes)
         */
        private int mCancels;
        /**
         * The last metadata load asked for, answered by the test (see #loadMetadata), null for none
         */
        private MediaServiceManager.OnMetadata mOnMetadata;
        private int mMetadataLoads;
        /**
         * Of the video's formats (see PlayerFailure.PROTOCOL_ constants), null when it has none
         */
        private String mProtocol;
        /**
         * The client the loader picks for the video when asked to (see VideoLoaderController#pickNextClientForVideo), null for none
         */
        private String mNextClient;
        /**
         * Of the video's formats, null when it has none
         */
        private String mClient;
        /**
         * The client a switch not forced goes to, null when it stays, its poToken made again (see
         * ErrorFixerController#switchNextClientOrPoToken)
         */
        private String mSwitchedTo = "WEB";
        private int mSwitches;
        private boolean mIsSignedIn;
        /**
         * The device has a network (see ErrorFixerController#hasActiveNetwork)
         */
        private boolean mHasNetwork = true;
        /**
         * The longest side of the display's biggest mode (see ErrorFixerController#getDisplaySize), -1 unknown
         */
        private int mDisplaySize = -1;

        private TestController(Video video) throws Exception {
            this(video, (String) null);
        }

        private TestController(Video video, List<FormatItem> videoFormats, FormatItem videoFormat) throws Exception {
            this(video, null, playerStub(videoFormats, videoFormat));
        }

        /**
         * @param formatClient of the video's formats, null when it has none
         */
        private TestController(Video video, String formatClient) throws Exception {
            this(video, formatClient, playerStub());
        }

        private TestController(Video video, String formatClient, PlaybackView player) throws Exception {
            mVideo = video;
            mPlayer = player;
            mClient = formatClient;
            setAltContext(RuntimeEnvironment.getApplication());

            // Without a player: reloads and restarts do nothing
            Field loader = ErrorFixerController.class.getDeclaredField("mVideoLoaderController");
            loader.setAccessible(true);
            loader.set(this, new VideoLoaderController() {
                @Override
                public String getFormatClient() {
                    return mClient;
                }

                @Override
                public String getFormatProtocol() {
                    return mProtocol;
                }

                @Override
                public void reloadVideo(int delayMs) {
                    mReloads.add(delayMs);
                }

                @Override
                void restartEngine(int delayMs) {
                    mRestarts.add(delayMs);
                }

                @Override
                void cancelPendingFixes() {
                    mCancels++;
                }

                @Override
                public String pickNextClientForVideo() {
                    return mNextClient;
                }
            });
        }

        @Override
        String switchNextClientOrPoToken() {
            mSwitches++;
            return mSwitchedTo;
        }

        @Override
        boolean isSignedIn() {
            return mIsSignedIn;
        }

        @Override
        boolean hasActiveNetwork() {
            return mHasNetwork;
        }

        @Override
        int getDisplaySize() {
            return mDisplaySize;
        }

        /**
         * Not the server's: the test answers it
         */
        @Override
        void loadMetadata(Video video, MediaServiceManager.OnMetadata onMetadata) {
            mOnMetadata = onMetadata;
            mMetadataLoads++;
        }

        @Override
        public PlaybackView getPlayer() {
            return mPlayer;
        }

        @Override
        public Video getVideo() {
            return mVideo;
        }

        /**
         * Counts the ends it's told of: the rest does nothing
         */
        @Override
        protected PlayerEventListener getMainController() {
            return (PlayerEventListener) Proxy.newProxyInstance(PlayerEventListener.class.getClassLoader(),
                    new Class<?>[]{PlayerEventListener.class}, (proxy, method, args) -> {
                        if ("onPlayEnd".equals(method.getName())) {
                            mPlayEnds++;
                        }

                        return method.getReturnType() == boolean.class ? false : null;
                    });
        }
    }
}
