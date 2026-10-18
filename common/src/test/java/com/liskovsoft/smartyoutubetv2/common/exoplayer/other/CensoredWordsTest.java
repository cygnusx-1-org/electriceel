package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.CensoredWords.Range;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CensoredWordsTest {
    // As the automatic captions write it: no-break spaces inside the brackets
    private static final String MARKER = "[ __ ]";

    /**
     * A line of rMyDvqnwIm4 ("you know, throwing [ __ ] up in the air."). The word is muted from a little before it starts
     * to a little after the next one starts.
     */
    @Test
    public void wordIsTimedOnItsOwn() throws JSONException {
        String json = captions(line(21039, 3201, seg("you", 0), seg(" know,", 80), seg(" throwing", 240), seg(" " + MARKER, 481),
                seg(" up", 721), seg(" in", 881)));

        assertRanges(CensoredWords.findRanges(json), 21520 - CensoredWords.LEAD_MS, 21760 + CensoredWords.TRAIL_MS);
    }

    @Test
    public void markerWithPlainSpacesIsFound() throws JSONException {
        String json = captions(line(1000, 3000, seg("so", 0), seg(" [ __ ]", 400), seg(" it", 700)));

        assertRanges(CensoredWords.findRanges(json), 1400 - CensoredWords.LEAD_MS, 1700 + CensoredWords.TRAIL_MS);
    }

    @Test
    public void otherBracketsAndUnderscoresAreNotCensoredWords() throws JSONException {
        String json = captions(line(1000, 3000, seg("[Music]", 0), seg(" __init__", 400), seg(" [_]", 700), seg(" [ ___ ]", 900)));

        assertTrue(CensoredWords.findRanges(json).isEmpty());
    }

    /**
     * The next word is on the next line, so the word ends with its line. Before a pause that's seconds later.
     */
    @Test
    public void lastWordOfALineLastsAtMostASecond() throws JSONException {
        String json = captions(line(1000, 5000, seg("what", 0), seg(" " + MARKER, 500)));

        assertRanges(CensoredWords.findRanges(json), 1500 - CensoredWords.LEAD_MS, 1500 + CensoredWords.MAX_WORD_MS + CensoredWords.TRAIL_MS);
    }

    @Test
    public void shortLastWordEndsWithItsLine() throws JSONException {
        String json = captions(line(1000, 700, seg("what", 0), seg(" " + MARKER, 500)));

        assertRanges(CensoredWords.findRanges(json), 1500 - CensoredWords.LEAD_MS, 1700 + CensoredWords.TRAIL_MS);
    }

    @Test
    public void wordAtTheStartOfTheVideoStartsAtZero() throws JSONException {
        String json = captions(line(50, 2000, seg(MARKER, 0), seg(" off", 300)));

        assertRanges(CensoredWords.findRanges(json), 0, 350 + CensoredWords.TRAIL_MS);
    }

    /**
     * rMyDvqnwIm4 again: "[ __ ] The [ __ ] is in heat." The two ranges overlap, so they're one.
     */
    @Test
    public void closeWordsMergeIntoOneRange() throws JSONException {
        String json = captions(line(46719, 3601, seg(MARKER, 0), seg(" The", 481), seg(" " + MARKER, 641), seg(" is", 801)));

        assertRanges(CensoredWords.findRanges(json), 46719 - CensoredWords.LEAD_MS, 47520 + CensoredWords.TRAIL_MS);
    }

    @Test
    public void rangesAreSortedWhateverTheOrderOfTheLines() throws JSONException {
        String json = captions(line(9000, 2000, seg(MARKER, 0), seg(" b", 300)), line(2000, 2000, seg(MARKER, 0), seg(" a", 300)));

        assertRanges(CensoredWords.findRanges(json),
                2000 - CensoredWords.LEAD_MS, 2300 + CensoredWords.TRAIL_MS,
                9000 - CensoredWords.LEAD_MS, 9300 + CensoredWords.TRAIL_MS);
    }

    /**
     * The automatic captions have events with no words (new lines) and events with no segments (the window)
     */
    @Test
    public void eventsWithoutWordsAreSkipped() throws JSONException {
        JSONObject window = new JSONObject().put("tStartMs", 0).put("dDurationMs", 99000).put("id", 1);
        String json = captions(window, line(2950, 2570, seg("\n", 0)), line(5000, 2000, seg(MARKER, 0), seg(" x", 200)));

        assertRanges(CensoredWords.findRanges(json), 5000 - CensoredWords.LEAD_MS, 5200 + CensoredWords.TRAIL_MS);
    }

    @Test
    public void nothingCensoredIsNoRanges() throws JSONException {
        assertTrue(CensoredWords.findRanges(captions(line(0, 1000, seg("hello", 0)))).isEmpty());
        assertTrue(CensoredWords.findRanges("{}").isEmpty());
    }

    /**
     * A refused request may still answer 200 with an empty body
     */
    @Test(expected = JSONException.class)
    public void emptyBodyIsAnError() throws JSONException {
        CensoredWords.findRanges("");
    }

    @Test
    public void rangeHoldsItsStartButNotItsEnd() {
        Range range = new Range(1000, 2000);

        assertFalse(range.contains(999));
        assertTrue(range.contains(1000));
        assertTrue(range.contains(1999));
        assertFalse(range.contains(2000));
    }

    private static void assertRanges(List<Range> ranges, long... bounds) {
        assertEquals(ranges.toString(), bounds.length / 2, ranges.size());

        for (int i = 0; i < ranges.size(); i++) {
            assertEquals("start of " + i, bounds[i * 2], ranges.get(i).startMs);
            assertEquals("end of " + i, bounds[i * 2 + 1], ranges.get(i).endMs);
        }
    }

    private static String captions(JSONObject... events) throws JSONException {
        JSONArray array = new JSONArray();

        for (JSONObject event : events) {
            array.put(event);
        }

        return new JSONObject().put("wireMagic", "pb3").put("events", array).toString();
    }

    private static JSONObject line(long startMs, long durationMs, JSONObject... segs) throws JSONException {
        JSONArray array = new JSONArray();

        for (JSONObject seg : segs) {
            array.put(seg);
        }

        return new JSONObject().put("tStartMs", startMs).put("dDurationMs", durationMs).put("wWinId", 1).put("segs", array);
    }

    /**
     * The first word of a line has no offset
     */
    private static JSONObject seg(String text, long offsetMs) throws JSONException {
        JSONObject seg = new JSONObject().put("utf8", text);

        return offsetMs > 0 ? seg.put("tOffsetMs", offsetMs) : seg;
    }
}
