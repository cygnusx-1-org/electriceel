package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;

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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PlayerFailureLogTest {
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();
    private final AtomicLong mNow = new AtomicLong(1_000_000);
    private int mSavedEngine;

    @Before
    public void setUp() {
        // Cronet can't load under Robolectric
        mSavedEngine = getTweaks().getPlayerDataSource();
        getTweaks().setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP);
    }

    @After
    public void tearDown() {
        getTweaks().setPlayerDataSource(mSavedEngine);
    }

    @Test
    public void readsInTheOrderRecorded() throws Exception {
        PlayerFailureLog log = newLog("1.0", 10);

        log.record(failure(PlayerFailure.KIND_ENGINE_ERROR, "first").action("reload_video"));
        tick(1_000);
        log.record(failure(PlayerFailure.KIND_FORMAT_ERROR, "second"));
        tick(1_000);
        log.record(failure(PlayerFailure.KIND_STALL, "third"));

        List<JSONObject> events = log.readAll();

        assertEquals(3, events.size());
        assertEquals("first", events.get(0).optString("videoId"));
        assertEquals("engine_error", events.get(0).optString("kind"));
        assertEquals("reload_video", events.get(0).optJSONArray("actions").optString(0));
        assertEquals("second", events.get(1).optString("videoId"));
        assertEquals("third", events.get(2).optString("videoId"));
        assertEquals(7, events.get(0).optInt("account"));
        // 1,000,000 ms after the epoch, in UTC
        assertEquals("1970-01-01T00:16:40.000Z", events.get(0).optString("time"));
    }

    @Test
    public void keptOverARestartAndAnUpdate() {
        PlayerFailureLog before = newLog("1.0", 10);
        before.record(failure(PlayerFailure.KIND_UNPLAYABLE, "before"));
        before.readAll(); // written

        // The app restarts as a newer version
        PlayerFailureLog after = newLog("2.0", 20);
        tick(1_000);
        after.record(failure(PlayerFailure.KIND_UNPLAYABLE, "after"));

        List<JSONObject> events = after.readAll();

        assertEquals(2, events.size());
        // Each keeps the version it was recorded with
        assertEquals("1.0", events.get(0).optString("appVersion"));
        assertEquals(10, events.get(0).optInt("appVersionCode"));
        assertEquals("2.0", events.get(1).optString("appVersion"));
        assertEquals(20, events.get(1).optInt("appVersionCode"));
    }

    @Test
    public void rotatesAtTheCapAndDropsTheOldest() {
        PlayerFailureLog log = newLog("1.0", 10);
        String padding = repeat('x', 10_000);
        // Enough for three files' worth: the first one is dropped
        int count = (int) (3 * PlayerFailureLog.MAX_FILE_BYTES / padding.length());

        for (int i = 0; i < count; i++) {
            tick(1_000);
            log.record(failure(PlayerFailure.KIND_ENGINE_ERROR, "video" + i, padding + i));
        }

        List<JSONObject> events = log.readAll();
        File dir = getDir();

        assertTrue(events.size() < count);
        assertFalse("the oldest is kept", "video0".equals(events.get(0).optString("videoId")));
        assertEquals("video" + (count - 1), events.get(events.size() - 1).optString("videoId"));
        // Oldest first, none skipped after the first one kept
        int first = Integer.parseInt(events.get(0).optString("videoId").substring("video".length()));
        for (int i = 0; i < events.size(); i++) {
            assertEquals("video" + (first + i), events.get(i).optString("videoId"));
        }
        assertTrue(new File(dir, PlayerFailureLog.PREVIOUS).length() <= PlayerFailureLog.MAX_FILE_BYTES + 2 * padding.length());
        assertTrue(new File(dir, PlayerFailureLog.CURRENT).length() <= PlayerFailureLog.MAX_FILE_BYTES + 2 * padding.length());
    }

    @Test
    public void aLineCutShortByAKillIsSkipped() throws IOException {
        PlayerFailureLog before = newLog("1.0", 10);
        before.record(failure(PlayerFailure.KIND_STALL, "whole"));
        before.readAll();

        try (FileOutputStream out = new FileOutputStream(new File(getDir(), PlayerFailureLog.CURRENT), true)) {
            out.write("{\"ts\":123,\"kind\":\"sta".getBytes(StandardCharsets.UTF_8));
        }

        PlayerFailureLog after = newLog("1.0", 10);
        assertEquals(1, after.readAll().size());

        // The next one isn't taken with it
        tick(1_000);
        after.record(failure(PlayerFailure.KIND_STALL, "next"));
        List<JSONObject> events = after.readAll();

        assertEquals(2, events.size());
        assertEquals("next", events.get(1).optString("videoId"));
    }

    @Test
    public void theSameFailureWithinAMinuteIsCounted() {
        PlayerFailureLog log = newLog("1.0", 10);
        long start = mNow.get();

        log.record(failure(PlayerFailure.KIND_FORMAT_ERROR, "video"));

        for (int i = 0; i < 3; i++) {
            tick(10_000);
            log.record(failure(PlayerFailure.KIND_FORMAT_ERROR, "video"));
        }

        // Another video isn't the same failure
        tick(5_000);
        log.record(failure(PlayerFailure.KIND_FORMAT_ERROR, "other"));

        // Past the minute: the count is written, and this one starts another minute
        mNow.set(start + PlayerFailureLog.REPEAT_WINDOW_MS + 10_000);
        log.record(failure(PlayerFailure.KIND_FORMAT_ERROR, "video"));

        List<JSONObject> events = log.readAll();

        assertEquals(4, events.size());
        assertEquals(start, events.get(0).optLong("ts"));
        assertEquals(0, events.get(0).optInt("repeats"));
        // The last of the three, with the two before it
        assertEquals("video", events.get(1).optString("videoId"));
        assertEquals(start + 30_000, events.get(1).optLong("ts"));
        assertEquals(2, events.get(1).optInt("repeats"));
        assertEquals("other", events.get(2).optString("videoId"));
        assertEquals(start + PlayerFailureLog.REPEAT_WINDOW_MS + 10_000, events.get(3).optLong("ts"));
        assertEquals(0, events.get(3).optInt("repeats"));
    }

    @Test
    public void readingWritesTheCountSoFarAndTheMinuteGoesOn() {
        PlayerFailureLog log = newLog("1.0", 10);

        log.record(failure(PlayerFailure.KIND_FORMAT_ERROR, "video"));
        tick(1_000);
        log.record(failure(PlayerFailure.KIND_FORMAT_ERROR, "video"));
        tick(1_000);
        log.record(failure(PlayerFailure.KIND_FORMAT_ERROR, "video"));

        List<JSONObject> events = log.readAll();

        assertEquals(2, events.size());
        assertEquals(1, events.get(1).optInt("repeats"));

        // Still within the minute of the line just written: counted, not written
        tick(1_000);
        log.record(failure(PlayerFailure.KIND_FORMAT_ERROR, "video"));
        tick(1_000);
        log.record(failure(PlayerFailure.KIND_FORMAT_ERROR, "video"));

        events = log.readAll();

        assertEquals(3, events.size());
        assertEquals(1, events.get(2).optInt("repeats"));
    }

    @Test
    public void theFormatsTriedLaterGoToTheFailuresOfThatSession() throws Exception {
        PlayerFailureLog log = newLog("1.0", 10);

        log.record(failure(PlayerFailure.KIND_ENGINE_ERROR, "video"));
        // The app was killed: this session's formats were never written
        tick(1_000);
        long sessionStart = mNow.get();
        tick(1_000);
        log.record(failure(PlayerFailure.KIND_STALL, "video"));
        tick(1_000);
        log.record(failure(PlayerFailure.KIND_STALL, "other"));
        tick(1_000);
        log.recordFormats("video", sessionStart, new JSONObject()
                .put("attempted", new JSONArray().put(new JSONObject().put("video", "a")).put(new JSONObject().put("video", "b")))
                .put("final", new JSONObject().put("video", "b")), false);

        List<JSONObject> events = log.readAll();

        // Its line isn't a failure
        assertEquals(3, events.size());
        assertTrue(events.get(0).isNull("formats"));
        assertEquals("b", events.get(1).getJSONObject("formats").getJSONObject("final").getString("video"));
        assertEquals(2, events.get(1).getJSONObject("formats").getJSONArray("attempted").length());
        assertTrue(events.get(2).isNull("formats"));
    }

    @Test
    public void aCrashIsWrittenBeforeItReturnsWithTheRepeats() throws Exception {
        PlayerFailureLog log = newLog("1.0", 10);
        log.record(failure(PlayerFailure.KIND_FORMAT_ERROR, "video"));
        tick(1_000);
        log.record(failure(PlayerFailure.KIND_FORMAT_ERROR, "video")); // a repeat, in memory
        log.readAll(); // the first one written
        tick(1_000);
        log.record(failure(PlayerFailure.KIND_FORMAT_ERROR, "video")); // another repeat
        tick(1_000);

        log.recordNow(failure(AppExits.KIND_CRASH, "video"));

        // On the disk already: the app dies next
        String written = new String(java.nio.file.Files.readAllBytes(new File(getDir(), PlayerFailureLog.CURRENT).toPath()),
                StandardCharsets.UTF_8);
        assertTrue(written, written.contains("\"kind\":\"crash\""));
        assertTrue(written, written.contains("\"repeats\":0"));
    }

    @Test
    public void aFlushWritesTheRepeatsInMemory() throws Exception {
        PlayerFailureLog log = newLog("1.0", 10);
        log.record(failure(PlayerFailure.KIND_STALL, "video"));
        tick(1_000);
        log.record(failure(PlayerFailure.KIND_STALL, "video"));
        tick(1_000);
        log.record(failure(PlayerFailure.KIND_STALL, "video"));

        log.flush();
        log.readAll(); // waits for the flush

        List<JSONObject> events = log.readAll();
        assertEquals(2, events.size());
        assertEquals(1, events.get(1).getInt("repeats"));
    }

    @Test
    public void theLastFormatsWrittenForASessionWin() throws Exception {
        PlayerFailureLog log = newLog("1.0", 10);
        long sessionStart = mNow.get();
        log.record(failure(PlayerFailure.KIND_ENGINE_ERROR, "video"));
        tick(1_000);
        // Written when the app went to the background, then again when the video was left
        log.recordFormats("video", sessionStart, new JSONObject().put("final", new JSONObject().put("video", "a")), false);
        tick(1_000);
        log.recordFormats("video", sessionStart, new JSONObject().put("final", new JSONObject().put("video", "b")), false);

        List<JSONObject> events = log.readAll();

        assertEquals(1, events.size());
        assertEquals("b", events.get(0).getJSONObject("formats").getJSONObject("final").getString("video"));
    }

    @Test
    public void eachFailureHasTheSettingsAndTheStateItHappenedIn() throws Exception {
        PlayerFailureLog log = newLog("1.0", 10);
        log.record(failure(PlayerFailure.KIND_ENGINE_ERROR, "video"));

        JSONObject event = log.readAll().get(0);

        assertTrue(event.getJSONObject("settings").has("tunneledPlayback"));
        assertTrue(event.getJSONObject("settings").has("bufferType"));
        assertTrue(event.getJSONObject("state").has("heapMaxMb"));
    }

    @Test
    public void howTheFormatsPlayedIsCounted() throws Exception {
        PlayerFailureLog log = newLog("1.0", 10);

        log.countPlayback("vp9 1080p30", "opus", FormatAttempts.STATUS_WORKED, FormatAttempts.STATUS_WORKED, 60_000);
        log.countPlayback("vp9 1080p30", "opus", FormatAttempts.STATUS_WORKED, FormatAttempts.STATUS_FAILED, 1_000);
        log.countPlayback("avc 720p30", "mp4a", FormatAttempts.STATUS_WORKED, FormatAttempts.STATUS_WORKED, 30_000);

        log.readStats(); // written

        // Kept over a restart
        JSONObject stats = newLog("1.0", 10).readStats();
        org.json.JSONArray formats = stats.getJSONArray("formats");

        assertEquals(2, formats.length());
        JSONObject vp9 = formats.getJSONObject(0);
        assertEquals("vp9 1080p30", vp9.getString("video"));
        assertEquals(2, vp9.getInt("plays"));
        assertEquals(2, vp9.getInt("videoWorked"));
        assertEquals(1, vp9.getInt("audioWorked"));
        assertEquals(1, vp9.getInt("audioFailed"));
        assertEquals(61_000, vp9.getLong("playedMs"));
    }

    @Test
    public void aUrlsQueryIsLeftOut() {
        PlayerFailureLog log = newLog("1.0", 10);
        IllegalStateException error = new IllegalStateException(
                "Unable to connect to https://rr1---sn-abc.googlevideo.com/videoplayback?expire=1&ip=203.0.113.9&sig=x",
                new RuntimeException("Interrupted OkHttp request to https://www.youtube.com/youtubei/v1/player?key=abc"));

        log.record(PlayerFailure.create(RuntimeEnvironment.getApplication(), PlayerFailure.KIND_ENGINE_ERROR)
                .video(video("video")).error(error));

        String json = log.readAll().get(0).toString();

        assertFalse(json, json.contains("203.0.113.9"));
        assertFalse(json, json.contains("key=abc"));
        assertTrue(json, json.contains("rr1---sn-abc.googlevideo.com\\/videoplayback?…"));
    }

    private PlayerFailureLog newLog(String appVersion, int appVersionCode) {
        return new PlayerFailureLog(getDir(), appVersion, appVersionCode, () -> 7, mNow::get);
    }

    private File getDir() {
        return new File(mFolder.getRoot(), PlayerFailureLog.DIR);
    }

    private void tick(long ms) {
        mNow.addAndGet(ms);
    }

    private static PlayerFailure failure(String kind, String videoId) {
        return failure(kind, videoId, "Response code: 403");
    }

    private static PlayerFailure failure(String kind, String videoId, String message) {
        return PlayerFailure.create(RuntimeEnvironment.getApplication(), kind)
                .video(video(videoId))
                .error(new IllegalStateException(message));
    }

    private static Video video(String videoId) {
        Video video = new Video();
        video.videoId = videoId;
        return video;
    }

    private static PlayerTweaksData getTweaks() {
        return PlayerTweaksData.instance(RuntimeEnvironment.getApplication());
    }

    private static String repeat(char c, int count) {
        StringBuilder result = new StringBuilder(count);

        for (int i = 0; i < count; i++) {
            result.append(c);
        }

        return result.toString();
    }
}
