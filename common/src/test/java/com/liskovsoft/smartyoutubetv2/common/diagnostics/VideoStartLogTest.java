package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoStartLogTest {
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();

    @After
    public void tearDown() {
        VideoStartLog.useForTesting(null);
    }

    @Test
    public void keepsTheStartsOverRestarts() throws Exception {
        VideoStartLog log = new VideoStartLog(mFolder.getRoot());
        log.record(start("first", 2500));
        log.record(start("second", 3100));

        assertEquals(2, log.readAll().size());

        // The app restarted
        List<JSONObject> starts = new VideoStartLog(mFolder.getRoot()).readAll();

        assertEquals(2, starts.size());
        assertEquals("first", starts.get(0).getString("videoId"));
        assertEquals(2500, starts.get(0).getLong("totalMs"));
        assertEquals("second", starts.get(1).getString("videoId"));
        assertEquals(start("second", 3100).toJson().toString(), starts.get(1).toString());
    }

    @Test
    public void keepsTheLastOnes() throws Exception {
        VideoStartLog log = new VideoStartLog(mFolder.getRoot());

        for (int i = 0; i < VideoStartLog.MAX_LINES + 1; i++) {
            log.record(start("video" + i, 2000 + i));
        }

        List<JSONObject> starts = log.readAll();

        assertEquals(VideoStartLog.MAX_STARTS, starts.size());
        assertEquals("video" + (VideoStartLog.MAX_LINES + 1 - VideoStartLog.MAX_STARTS), starts.get(0).getString("videoId"));
        assertEquals("video" + VideoStartLog.MAX_LINES, starts.get(VideoStartLog.MAX_STARTS - 1).getString("videoId"));
        // The file was cut down, not just the reading
        assertEquals(VideoStartLog.MAX_STARTS, Files.readAllLines(new File(mFolder.getRoot(), VideoStartLog.FILE).toPath()).size());

        // And it goes on from there
        log.record(start("after", 2000));
        assertEquals("after", log.readAll().get(VideoStartLog.MAX_STARTS - 1).getString("videoId"));
    }

    /**
     * A kill while writing leaves half a line
     */
    @Test
    public void aCutLineIsSkipped() throws Exception {
        File file = new File(mFolder.getRoot(), VideoStartLog.FILE);

        try (OutputStream out = new FileOutputStream(file)) {
            out.write((start("whole", 2500).toJson() + "\n{\"ts\":12").getBytes(StandardCharsets.UTF_8));
        }

        VideoStartLog log = new VideoStartLog(mFolder.getRoot());

        assertEquals(1, log.readAll().size());
        assertEquals("whole", log.readAll().get(0).getString("videoId"));

        // The next one isn't joined to it
        log.record(start("next", 2500));

        assertEquals(2, log.readAll().size());
        assertEquals("next", log.readAll().get(1).getString("videoId"));
    }

    @Test
    public void noneRecordedIsNone() {
        assertTrue(new VideoStartLog(mFolder.getRoot()).readAll().isEmpty());
    }

    private static VideoStart start(String videoId, long totalMs) {
        return new VideoStart(1_792_000_000_000L, videoId, VideoStart.TRIGGER_OPEN, false, 60_000, "TV", "sabr", false, totalMs,
                Collections.singletonList(new VideoStart.Stage("buffering", null, 0, 0, totalMs)));
    }
}
