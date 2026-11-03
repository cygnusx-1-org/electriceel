package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import com.google.android.exoplayer2.source.sabr.parser.parts.PoTokenStatusSabrPart.PoTokenStatus;

import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ProtectionStatusesTest {
    private final AtomicLong mNow = new AtomicLong(10_000);

    @Before
    public void setUp() {
        ProtectionStatuses.resetForTesting(mNow::get);
    }

    @After
    public void tearDown() {
        ProtectionStatuses.resetForTesting(null);
    }

    /**
     * The first status of the start, then each change: the audio and the video streams each tell theirs
     */
    @Test
    public void aStartHasTheFirstStatusAndEachChange() {
        tell(10_500, "video", PoTokenStatus.PENDING);
        tell(10_510, "video", PoTokenStatus.PENDING);
        tell(10_700, "video", PoTokenStatus.OK);
        tell(10_800, "video", PoTokenStatus.OK);

        List<VideoStart.Stage> stages = ProtectionStatuses.take("video", 10_000, 13_000);

        assertEquals(2, stages.size());
        assertEquals(VideoStarts.STAGE_PROTECTION, stages.get(0).name);
        assertEquals("PENDING", stages.get(0).detail);
        assertEquals(1, stages.get(0).depth);
        assertEquals(10_500, stages.get(0).startMs);
        assertEquals(10_500, stages.get(0).endMs);
        assertEquals(VideoStart.Category.MARK, stages.get(0).getCategory());
        assertEquals("OK", stages.get(1).detail);
        assertEquals(10_700, stages.get(1).startMs);
    }

    /**
     * Told before the start, it's of the stream before. Another video's isn't the start's.
     */
    @Test
    public void onlyTheStartsVideoInItsTimeIsTaken() {
        tell(9_000, "video", PoTokenStatus.OK);
        tell(11_000, "other", PoTokenStatus.INVALID);
        tell(14_000, "video", PoTokenStatus.PENDING);

        assertTrue(ProtectionStatuses.take("video", 10_000, 13_000).isEmpty());
        assertTrue(ProtectionStatuses.take(null, 0, 20_000).isEmpty());
    }

    /**
     * A failure has the video's changes, the last ones, with how long ago each was told
     */
    @Test
    public void aFailureHasTheLastChanges() throws Exception {
        tell(1_000, "video", PoTokenStatus.OK);
        tell(2_000, "video", PoTokenStatus.OK);
        tell(3_000, "video", PoTokenStatus.PENDING);
        tell(4_000, "video", PoTokenStatus.OK);
        tell(5_000, "video", PoTokenStatus.PENDING);
        tell(6_000, "video", PoTokenStatus.INVALID);
        mNow.set(70_000);

        JSONArray statuses = ProtectionStatuses.snapshot("video");

        assertEquals(ProtectionStatuses.MAX_FAILURE_STATUSES, statuses.length());
        assertEquals("PENDING", statuses.getJSONObject(0).getString("status"));
        assertEquals(67_000, statuses.getJSONObject(0).getLong("msAgo"));
        assertEquals("INVALID", statuses.getJSONObject(3).getString("status"));
        assertEquals(64_000, statuses.getJSONObject(3).getLong("msAgo"));
        // Not streamed by SABR
        assertNull(ProtectionStatuses.snapshot("other"));
        assertNull(ProtectionStatuses.snapshot(null));
    }

    @Test
    public void keepsTheLastOnesOfTheLastVideos() {
        for (int i = 0; i <= ProtectionStatuses.MAX_STATUSES; i++) {
            tell(20_000 + i, "video", i % 2 == 0 ? PoTokenStatus.OK : PoTokenStatus.PENDING);
        }

        List<VideoStart.Stage> stages = ProtectionStatuses.take("video", 0, 30_000);
        assertEquals(ProtectionStatuses.MAX_STATUSES, stages.size());
        assertEquals(20_001, stages.get(0).startMs);

        for (int i = 0; i < ProtectionStatuses.MAX_VIDEOS; i++) {
            tell(30_000, "other" + i, PoTokenStatus.OK);
        }

        assertNull(ProtectionStatuses.snapshot("video"));
        assertEquals(1, ProtectionStatuses.snapshot("other0").length());
    }

    private void tell(long ms, String videoId, PoTokenStatus status) {
        mNow.set(ms);
        ProtectionStatuses.onStatus(videoId, status);
    }
}
