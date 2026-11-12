package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;

import com.google.android.exoplayer2.source.sabr.parser.exceptions.PoTokenError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrProtocolError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.SabrReloadPlayerError;
import com.google.android.exoplayer2.source.sabr.parser.exceptions.UnknownPartError;
import com.google.android.exoplayer2.source.sabr.parser.parts.PoTokenStatusSabrPart.PoTokenStatus;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.google.android.exoplayer2.upstream.Loader.UnexpectedLoaderException;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.PlayerFailureLog;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.VideoStarts;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.app.PoTokenGate;
import com.liskovsoft.youtubeapi.videoinfo.V2.ClientAttempts;

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
import java.lang.reflect.Proxy;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ErrorFixerControllerTest {
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
    private boolean mSavedIpv6Test;

    @Before
    public void setUp() throws Exception {
        mLog = PlayerFailureLog.useForTesting(mFolder.getRoot());
        // Cronet can't load under Robolectric
        mSavedEngine = getTweaks().getPlayerDataSource();
        getTweaks().setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP);
        mSavedDns = getTweaks().getPreferredDnsType();
        mSavedIpv6Test = getGlobalPrefs().isIpv6TestEnabled();
        setTvPotRejected(false);
        setIpv6Working(null);
        getPlayerData().setTempVideoFormat(null);
        // No segment loaded or loading
        VideoStarts.onPrepare();
    }

    @After
    public void tearDown() throws Exception {
        PlayerFailureLog.useForTesting(null);
        getTweaks().setPlayerDataSource(mSavedEngine);
        getTweaks().setPreferredDnsType(mSavedDns);
        getGlobalPrefs().setIpv6TestEnabled(mSavedIpv6Test);
        Utils.sHandler.removeCallbacksAndMessages(null);
        setTvPotRejected(false);
        setIpv6Working(null);
        VideoStarts.onPrepare();
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
        assertEquals(Arrays.asList("switch_next_client", "reload_video"), getActions(events.get(1)));
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
     * IPv6 doesn't connect: the player uses OkHttp whatever is chosen. The DNS is switched, the engine chosen isn't.
     */
    @Test
    public void theDnsFixKeepsTheEngineChosenWhenOkHttpIsInUse() throws Exception {
        getTweaks().setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET);
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_IPV4);
        getGlobalPrefs().setIpv6TestEnabled(true);
        setIpv6Working(false);
        TestController controller = new TestController(video("p5miU3x4Sr8"));

        controller.onLongBuffering();

        JSONObject event = mLog.readAll().get(0);
        assertEquals("okhttp_no_ipv6", event.getJSONObject("player").getString("engineEffective"));
        assertEquals(Arrays.asList("dns_google", "restart_engine"), getActions(event));
        assertEquals(PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET, getTweaks().getPlayerDataSource());
        assertEquals(PlayerTweaksData.DNS_TYPE_GOOGLE, getTweaks().getPreferredDnsType());
    }

    /**
     * The DNS isn't Google's at the start of a video: it's switched, and the engine restarted (the DNS is read at each lookup)
     */
    @Test
    public void aStartThatHangsSwitchesTheDns() throws Exception {
        getTweaks().setPreferredDnsType(PlayerTweaksData.DNS_TYPE_SYSTEM);
        TestController controller = new TestController(video("p5miU3x4Sr8"));

        controller.onLongBuffering();

        assertEquals(Arrays.asList("dns_google", "restart_engine"), getActions(mLog.readAll().get(0)));
        assertEquals(PlayerTweaksData.DNS_TYPE_GOOGLE, getTweaks().getPreferredDnsType());
    }

    /**
     * A resumed video that hangs: a hang there is more likely the poToken's (no attestation grace period on resume) than the DNS's.
     * The client is switched, the DNS left as it is.
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

        assertEquals(Arrays.asList("switch_next_client", "reload_video"), getActions(mLog.readAll().get(0)));
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
        ClientAttempts.clear("removed");
        ClientAttempts.record("removed", ClientAttempts.Attempt.unplayable("TV", "ERROR", "This video is unavailable"));
        ClientAttempts.record("removed", ClientAttempts.Attempt.unplayable("WEB", "ERROR", "This video is unavailable"));
        ClientAttempts.record("removed", ClientAttempts.Attempt.nullResponse("INNERTUBE"));
        TestController controller = new TestController(video("removed"));

        controller.runFormatErrorAction(new IllegalStateException("fromNullable result is null"));

        JSONObject event = mLog.readAll().get(0);
        assertEquals("unplayable", event.getString("kind"));
        assertEquals("ERROR", event.getJSONObject("playability").getString("status"));
        assertEquals("This video is unavailable", event.getJSONObject("playability").getString("reason"));
        assertEquals("reload_video_30s", event.getJSONArray("actions").getString(0));
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
     * The next size down, 1440p, as a limit of the same codec: not the track of the stream played
     */
    private static void assertLowered(FormatItem format) {
        assertEquals(2560, format.getWidth());
        assertEquals(1440, format.getHeight());
        assertEquals(null, format.getTrack().format.id);
        assertEquals("vp9", com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil.codecNameShort(format.getTrack().format.codecs));
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
            setAltContext(RuntimeEnvironment.getApplication());

            // Without a player: reloads and restarts do nothing
            Field loader = ErrorFixerController.class.getDeclaredField("mVideoLoaderController");
            loader.setAccessible(true);
            loader.set(this, new VideoLoaderController() {
                @Override
                public String getFormatClient() {
                    return formatClient;
                }
            });
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
