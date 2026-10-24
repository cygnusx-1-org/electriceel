package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * The report the app sends against the contract: ElectricEelDataRestAPI/schema/submission.schema.json, from the server's repo
 * cloned next to this one (skipped when it isn't). Every field the report has must be declared there, with its type: a field
 * added here and not there fails.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DiagnosticsContractTest {
    private static final FormatItem VP9_4K = ExoFormatItem.fromVideoSpec("3840,2160,60,vp9", false);
    private static final FormatItem AVC_FHD = ExoFormatItem.fromVideoSpec("1920,1080,30,avc", false);
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();
    private PlayerFailureLog mLog;
    private int mSavedEngine;

    @Before
    public void setUp() {
        mLog = PlayerFailureLog.useForTesting(mFolder.getRoot());
        FormatAttempts.resetForTesting();
        // Cronet can't load under Robolectric
        mSavedEngine = PlayerTweaksData.instance(getContext()).getPlayerDataSource();
        PlayerTweaksData.instance(getContext()).setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP);
    }

    @After
    public void tearDown() {
        FormatAttempts.sClock = System::currentTimeMillis;
        FormatAttempts.resetForTesting();
        PlayerFailureLog.useForTesting(null);
        PlayerTweaksData.instance(getContext()).setPlayerDataSource(mSavedEngine);
    }

    /**
     * Every kind of failure and every part of the report, as the app builds them
     */
    @Test
    public void theAppsReportIsTheContract() throws Exception {
        JsonSchemaValidator validator = new JsonSchemaValidator(readSchema());
        recordEveryKind();

        JSONObject report = DiagnosticsReport.build(getContext(), account(), 2, mLog.readAll(), System.currentTimeMillis());

        assertEquals(Collections.emptyList(), validator.validate(report));

        // It's all there: a part missing would pass unchecked
        for (String part : Arrays.asList("schema", "submittedAt", "app", "system", "device", "playbackStats", "playerFailures")) {
            assertTrue(part, report.has(part));
        }

        Set<String> kinds = new HashSet<>();
        JSONArray failures = report.getJSONArray("playerFailures");

        for (int i = 0; i < failures.length(); i++) {
            kinds.add(failures.getJSONObject(i).getString("kind"));
        }

        assertEquals(new HashSet<>(Arrays.asList(PlayerFailure.KIND_ENGINE_ERROR, PlayerFailure.KIND_FORMAT_ERROR,
                PlayerFailure.KIND_UNPLAYABLE, PlayerFailure.KIND_EMPTY_FORMATS, PlayerFailure.KIND_STALL,
                PlayerFailure.KIND_TRACK_LOAD_ERROR, PlaybackHealth.KIND_NO_VIDEO, PlaybackHealth.KIND_VIDEO_FROZEN,
                PlaybackHealth.KIND_FRAME_DROPS, PlaybackHealth.KIND_STALLS, PlaybackHealth.KIND_AUDIO_SILENT, AppExits.KIND_CRASH,
                AppExits.KIND_APP_EXIT)), kinds);
        // The E-AC3 that failed and the AAC that played
        assertEquals(2, report.getJSONObject("playbackStats").getJSONArray("formats").length());
    }

    /**
     * Each kind in the schema is one the app records, and the other way round
     */
    @Test
    public void theSchemasKindsAreTheAppsKinds() throws Exception {
        JSONArray kinds = readSchema().getJSONObject("$defs").getJSONObject("FailureKind").getJSONArray("enum");
        Set<String> schemaKinds = new HashSet<>();

        for (int i = 0; i < kinds.length(); i++) {
            schemaKinds.add(kinds.getString(i));
        }

        assertEquals(new HashSet<>(Arrays.asList(PlayerFailure.KIND_ENGINE_ERROR, PlayerFailure.KIND_FORMAT_ERROR,
                PlayerFailure.KIND_UNPLAYABLE, PlayerFailure.KIND_EMPTY_FORMATS, PlayerFailure.KIND_STALL,
                PlayerFailure.KIND_TRACK_LOAD_ERROR, PlaybackHealth.KIND_NO_VIDEO, PlaybackHealth.KIND_VIDEO_FROZEN,
                PlaybackHealth.KIND_FRAME_DROPS, PlaybackHealth.KIND_STALLS, PlaybackHealth.KIND_AUDIO_SILENT, AppExits.KIND_CRASH,
                AppExits.KIND_APP_EXIT)), schemaKinds);
    }

    /**
     * The server's example (its tests post it) keeps to the contract too
     */
    @Test
    public void theServersExampleIsTheContract() throws Exception {
        File example = new File(getServerRepo(), "examples/submission.json");
        assumeTrue(example.isFile());

        JsonSchemaValidator validator = new JsonSchemaValidator(readSchema());

        assertEquals(Collections.emptyList(), validator.validate(new JSONObject(read(example))));
    }

    /**
     * The schema's own check catches what it should
     */
    @Test
    public void aFieldNotInTheContractFails() throws Exception {
        JsonSchemaValidator validator = new JsonSchemaValidator(readSchema());
        JSONObject report = DiagnosticsReport.build(getContext(), null, 0, Collections.emptyList(), System.currentTimeMillis());
        report.getJSONObject("app").put("somethingNew", 1);
        report.getJSONObject("system").put("sdkInt", "34");

        List<String> errors = validator.validate(report);

        assertTrue(errors.toString(), errors.contains("$.app.somethingNew: not in the schema"));
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.startsWith("$.system.sdkInt:")));
    }

    private void recordEveryKind() throws Exception {
        long[] now = {1_792_000_000_000L};
        FormatAttempts.sClock = () -> now[0];
        Video video = video("video");

        // A video playing: formats tried, offered, decoders, frames, a stall, segments loaded
        FormatAttempts.onNewVideo(getContext(), "video");
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_51_EC3);
        FormatAttempts.onOffered(Arrays.asList(VP9_4K, AVC_FHD), Collections.singletonList(FormatItem.AUDIO_HQ_MP4A));
        FormatAttempts.onDecoder(true, "OMX.vendor.vp9.decoder");
        FormatAttempts.onDecoder(false, "c2.android.eac3.decoder");
        FormatAttempts.onVideoRendered();
        FormatAttempts.onLoad(500_000, 100);
        FormatAttempts.onPlaying(true);
        now[0] += 15_000;
        FormatAttempts.onDroppedFrames(4);
        FormatAttempts.onBuffering();
        now[0] += 2_000;
        FormatAttempts.onPlaying(true);
        FormatAttempts.onStreamFailed(false);

        PlayerFailure.create(getContext(), PlayerFailure.KIND_ENGINE_ERROR)
                .video(video)
                .playback(17_000, 212_000, true)
                .formats(VP9_4K, FormatItem.AUDIO_51_EC3)
                .source("TV", PlayerFailure.PROTOCOL_SABR)
                .engineError(PlayerEventListener.ERROR_TYPE_RENDERER, PlayerEventListener.RENDERER_INDEX_AUDIO,
                        new IllegalStateException("AudioTrack init failed", new IOException("timeout")))
                .action("audio_hq_mp4a")
                .action("reload_video")
                .record();

        // The fallback works
        FormatAttempts.onTracks(VP9_4K, FormatItem.AUDIO_HQ_MP4A);
        FormatAttempts.onVideoRendered();
        FormatAttempts.onAudioPlayed();

        PlayerFailure.create(getContext(), PlayerFailure.KIND_TRACK_LOAD_ERROR)
                .video(video)
                .position(30_000, 212_000)
                .http(404, "audio", null)
                .action("select_other_codec")
                .record();
        PlayerFailure.create(getContext(), PlayerFailure.KIND_STALL).video(video).playback(40_000, 212_000, true)
                .action("reload_video").record();

        for (String kind : Arrays.asList(PlaybackHealth.KIND_NO_VIDEO, PlaybackHealth.KIND_VIDEO_FROZEN, PlaybackHealth.KIND_FRAME_DROPS,
                PlaybackHealth.KIND_STALLS, PlaybackHealth.KIND_AUDIO_SILENT)) {
            PlayerFailure.create(getContext(), kind).video(video).position(50_000, 212_000).formats(VP9_4K, FormatItem.AUDIO_HQ_MP4A)
                    .detail("renderedFrames", 1_200L).detail("droppedFrames", 40L).detail("silentMs", 15_000L).detail("stalls", 3)
                    .record();
        }

        now[0] += 20_000;
        FormatAttempts.onNewVideo(getContext(), "other"); // the first video is left: its formats are written

        // Another video's fetch: clients' answers
        Video other = video("other");
        List<ClientAttempts.Attempt> attempts = Arrays.asList(ClientAttempts.Attempt.http("TV", 403),
                ClientAttempts.Attempt.unplayable("WEB", "LOGIN_REQUIRED", "Sign in to confirm your age"),
                ClientAttempts.Attempt.exception("IOS", new IllegalStateException("timeout")),
                ClientAttempts.Attempt.nullResponse("INNERTUBE"), ClientAttempts.Attempt.noRegularFormats("MWEB"),
                ClientAttempts.Attempt.ok("ANDROID_VR"));
        PlayerFailure.create(getContext(), PlayerFailure.KIND_FORMAT_ERROR).video(other).playback(0, 0, false)
                .error(new IllegalStateException("fromNullable result is null")).clientAttempts(attempts).action("reload_video").record();
        PlayerFailure.create(getContext(), PlayerFailure.KIND_UNPLAYABLE).video(other).playability("LOGIN_REQUIRED", "Sign in")
                .clientAttempts(attempts).action("reload_video_30s").record();
        PlayerFailure.create(getContext(), PlayerFailure.KIND_EMPTY_FORMATS).video(other).playability("OK", null)
                .source("TV", null).action("reload_video").record();

        AppExits.recordCrash(getContext(), new Thread("main"), new IllegalStateException("Boom"));
        PlayerFailure.create(getContext(), AppExits.KIND_APP_EXIT).at(now[0] - 60_000).withoutState()
                .detail("reason", "anr").detail("description", "Input dispatching timed out").detail("process", getContext().getPackageName())
                .detail("foreground", true).detail("pssMb", 300L).detail("rssMb", 400L)
                .detail("mainThread", new JSONArray().put("\"main\" prio=5 tid=1 Blocked"))
                .record();

        FormatAttempts.end(getContext());
    }

    private static JSONObject readSchema() throws Exception {
        File schema = new File(getServerRepo(), "schema/submission.schema.json");
        assumeTrue("The server's repo isn't cloned next to this one: " + schema, schema.isFile());

        return new JSONObject(read(schema));
    }

    /**
     * ElectricEelDataRestAPI next to the repo this test runs in
     */
    private static File getServerRepo() {
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();

        while (dir != null && !new File(dir, "settings.gradle").isFile()) {
            dir = dir.getParentFile();
        }

        return new File(dir != null ? dir.getParentFile() : new File("."), "ElectricEelDataRestAPI");
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static Video video(String videoId) {
        Video video = new Video();
        video.videoId = videoId;
        return video;
    }

    private static Account account() {
        return (Account) Proxy.newProxyInstance(Account.class.getClassLoader(), new Class<?>[]{Account.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getProfileName":
                    return "Contract_Profile";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    Class<?> type = method.getReturnType();
                    return type == boolean.class ? false : type == int.class ? 0 : null;
            }
        });
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }
}
