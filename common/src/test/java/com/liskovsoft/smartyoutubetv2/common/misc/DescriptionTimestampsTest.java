package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.smartyoutubetv2.common.misc.DescriptionTimestamps.Timestamp;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DescriptionTimestampsTest {
    private static final long HOUR_MS = 60 * 60 * 1_000;
    private static final long UNKNOWN_DURATION = 0;

    @Test
    public void tracklist() {
        List<Timestamp> timestamps = DescriptionTimestamps.parse(
                "Tracklist:\n0:00 Intro\n4:05 Second song\n1:02:03 Last song\n\nThanks for watching", 2 * HOUR_MS);

        assertEquals(3, timestamps.size());
        assertTimestamp(0, "0:00 Intro", timestamps.get(0));
        assertTimestamp(245_000, "4:05 Second song", timestamps.get(1));
        assertTimestamp(3_723_000, "1:02:03 Last song", timestamps.get(2));
    }

    @Test
    public void textIsTheLineAsWritten() {
        List<Timestamp> timestamps = DescriptionTimestamps.parse("  00:00 - Intro\t\n[12:34] Ending \r\nOutro (59:59)", HOUR_MS);

        assertEquals("00:00 - Intro", timestamps.get(0).text);
        assertEquals("[12:34] Ending", timestamps.get(1).text);
        assertEquals("Outro (59:59)", timestamps.get(2).text);
        assertEquals(3_599_000, timestamps.get(2).positionMs);
    }

    @Test
    public void firstTimeCodeOfTheLine() {
        List<Timestamp> timestamps = DescriptionTimestamps.parse("0:30 - 1:30 The setup", HOUR_MS);

        assertEquals(1, timestamps.size());
        assertEquals(30_000, timestamps.get(0).positionMs);
    }

    @Test
    public void notTimeCodes() {
        String description = "Shot in 16:9\nJohn 3.16:20\nLive at 12:30pm\nVersion 1:2:3:4\nScore 1:75\nNothing here";

        assertTrue(DescriptionTimestamps.parse(description, UNKNOWN_DURATION).isEmpty());
    }

    /**
     * The next one on the line is used when the first isn't a time (seconds past 59)
     */
    @Test
    public void invalidTimeSkipped() {
        List<Timestamp> timestamps = DescriptionTimestamps.parse("1:75 then 2:15 Chorus\n1:60:00 Too many minutes", HOUR_MS);

        assertEquals(1, timestamps.size());
        assertEquals(135_000, timestamps.get(0).positionMs);
    }

    @Test
    public void pastTheEndDropped() {
        List<Timestamp> timestamps = DescriptionTimestamps.parse("0:00 Start\n10:00 End\n10:01 Too late\nRecorded 23:45", 600_000);

        assertEquals(2, timestamps.size());
        assertEquals(600_000, timestamps.get(1).positionMs);
    }

    @Test
    public void unknownDurationKeepsAll() {
        assertEquals(2, DescriptionTimestamps.parse("0:00 Start\n9:59:59 Late", UNKNOWN_DURATION).size());
        assertEquals(2, DescriptionTimestamps.parse("0:00 Start\n9:59:59 Late", -1).size());
    }

    @Test
    public void noDescription() {
        assertTrue(DescriptionTimestamps.parse(null, HOUR_MS).isEmpty());
        assertTrue(DescriptionTimestamps.parse("", HOUR_MS).isEmpty());
    }

    @Test
    public void current_latestStartedOne() {
        List<Timestamp> timestamps = DescriptionTimestamps.parse("1:00 A\n2:00 B\n3:00 C", HOUR_MS);

        assertNull(DescriptionTimestamps.current(timestamps, 59_999));
        assertSame(timestamps.get(0), DescriptionTimestamps.current(timestamps, 60_000));
        assertSame(timestamps.get(1), DescriptionTimestamps.current(timestamps, 179_999));
        assertSame(timestamps.get(2), DescriptionTimestamps.current(timestamps, HOUR_MS));
    }

    /**
     * Out of order (e.g. the same list twice, in two languages): the latest start, the first written of the same one
     */
    @Test
    public void current_outOfOrder() {
        List<Timestamp> timestamps = DescriptionTimestamps.parse("2:00 B\n1:00 A\n2:00 B again", HOUR_MS);

        assertSame(timestamps.get(1), DescriptionTimestamps.current(timestamps, 90_000));
        assertSame(timestamps.get(0), DescriptionTimestamps.current(timestamps, 150_000));
        assertNull(DescriptionTimestamps.current(new ArrayList<>(), 150_000));
    }

    private static void assertTimestamp(long positionMs, String text, Timestamp timestamp) {
        assertEquals(positionMs, timestamp.positionMs);
        assertEquals(text, timestamp.text);
    }
}
