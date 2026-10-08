package com.liskovsoft.smartyoutubetv2.common.utils;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CompactInfoTest {
    private static final long NOW_MS = getTimeMs(2026, Calendar.OCTOBER, 7, 12, 0); // Wed Oct 7 2026, noon

    @Test
    public void shortViewsKeepTheirDigits() {
        assertEquals("1.2M", compact("1.2M views"));
        assertEquals("231K", compact("231K views"));
        assertEquals("3b", compact("3B views"));
        assertEquals("1.2K", compact("1.2K views"));
    }

    /**
     * Cut like YouTube's: one decimal below ten, never rounded up
     */
    @Test
    public void fullViewsAreCut() {
        assertEquals("231K", compact("231,491 views"));
        assertEquals("2.7K", compact("2,767 views"));
        assertEquals("1.2M", compact("1,299,999 views"));
        assertEquals("1K", compact("1,000 views"));
        assertEquals("3b", compact("3,068,080,060 views"));
        assertEquals("446", compact("446 views"));
        assertEquals("1", compact("1 view"));
        assertEquals("0", compact("No views"));
    }

    @Test
    public void agesAreShortened() {
        assertEquals("30s", compact("30 seconds ago"));
        assertEquals("5min", compact("5 minutes ago"));
        assertEquals("9h", compact("9 hours ago"));
        assertEquals("2d", compact("2 days ago"));
        assertEquals("1w", compact("1 week ago"));
        assertEquals("11m", compact("11 months ago"));
        assertEquals("3y", compact("3 years ago"));
    }

    /**
     * The word that tells it was live or a premiere stays
     */
    @Test
    public void agesKeepWhatHappened() {
        assertEquals("Streamed 2d", compact("Streamed 2 days ago"));
        assertEquals("Streamed 15h", compact("Streamed live 15 hours ago"));
        assertEquals("Premiered 3w", compact("Premiered 3 weeks ago"));
    }

    @Test
    public void wholeLineIsShortened() {
        assertEquals("PBS • 205K • 7d", compact("PBS • 205K views • 7 days ago"));
        assertEquals("Nathan Grennan • 1m", compact("Nathan Grennan • 1 month ago • @nathangrennan4804"));
    }

    @Test
    public void handleIsDropped() {
        assertEquals("Some Channel", compact("Some Channel • @somechannel"));
        assertEquals("Some Channel • 3d", compact("Some Channel • 3 days ago • @somechannel"));
        assertEquals("2d", compact("@somechannel • 2 days ago"));
        assertEquals("", compact("@somechannel"));
        assertEquals("", compact("@one • @two"));
        // Not a handle alone
        assertEquals("Ask @someone • 2d", compact("Ask @someone • 2 days ago"));
    }

    @Test
    public void youTubeDatesBecomeAges() {
        assertEquals("2w", compact("Published on Sep 23, 2026"));
        assertEquals("12h", compact("Oct 7, 2026"));
        assertEquals("1y", compact("Uploaded on Oct 6, 2025"));
        assertEquals("Streamed 1d", compact("Streamed live on Oct 6, 2026"));
        assertEquals("Premiered 9m", compact("Premiered Dec 31, 2025"));
    }

    /**
     * The app's own dates (see DateHelper.toShortDate), e.g. of RSS feeds and of upcoming videos
     */
    @Test
    public void appDatesBecomeAges() {
        assertEquals("Some Channel • 1.2K • 2d", compact("Some Channel • 1.2K views • Mon Oct 5 2026"));
        assertEquals("Premieres in 3d", compact("Premieres Sat Oct 10 2026 9:00 PM"));
        assertEquals("Premieres in 9h", compact("Premieres Wed Oct 7 2026 9:00 PM"));
    }

    /**
     * A day alone doesn't tell the hour: today counts from midnight, never "0d" (it's noon)
     */
    @Test
    public void dayOfTodayCountsFromMidnight() {
        assertEquals("Some Channel • 581K • 12h", compact("Some Channel • 581K views • Oct 7, 2026"));
        assertEquals("12h", compact("Wed Oct 7 2026"));
        assertEquals("30min", CompactInfo.getAge(NOW_MS - 12 * 60 * 60 * 1_000, NOW_MS - 11 * 60 * 60 * 1_000 - 30 * 60 * 1_000, false));
    }

    @Test
    public void ageIsFoundInTheLine() {
        assertTrue(CompactInfo.hasAge("Some Channel • 581K views • 6 hours ago"));
        assertTrue(CompactInfo.hasAge("Some Channel • Published on Sep 23, 2026"));
        assertFalse(CompactInfo.hasAge("Some Channel • @somechannel"));
        assertFalse(CompactInfo.hasAge(null));
    }

    @Test
    public void datesToComeTellTheTimeLeft() {
        assertEquals("Scheduled in 11m", compact("Scheduled for Sep 24, 2027"));
        assertEquals("Premieres in 2w", compact("Premieres Oct 21, 2026"));
    }

    /**
     * A channel or a title isn't a count or a date, nor is anything in another language
     */
    @Test
    public void otherTextIsLeftAsItIs() {
        String live = "Some Channel • 1.2K watching";
        String other = "Канал • 1,2 млн просмотров • 3 дня назад";

        assertSame(live, CompactInfo.compact(live, NOW_MS));
        assertSame(other, CompactInfo.compact(other, NOW_MS));
        assertEquals("Top 10 of 2 years", compact("Top 10 of 2 years"));
        assertEquals("Daily News Oct 7, 2026 • 2d", compact("Daily News Oct 7, 2026 • 2 days ago"));
        assertEquals(null, CompactInfo.compact(null, NOW_MS));
    }

    @Test
    public void otherPartsKeepTheirStyle() {
        SpannableString info = new SpannableString("Some Channel • 1.2M views • LIVE");
        ForegroundColorSpan red = new ForegroundColorSpan(0xFFFF0000);
        info.setSpan(red, info.length() - 4, info.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        CharSequence result = CompactInfo.compact(info, NOW_MS);
        Spanned spanned = (Spanned) result;

        assertEquals("Some Channel • 1.2M • LIVE", result.toString());
        assertEquals(result.length() - 4, spanned.getSpanStart(red));
        assertEquals(result.length(), spanned.getSpanEnd(red));
    }

    private static String compact(String info) {
        CharSequence result = CompactInfo.compact(info, NOW_MS);
        return result != null ? result.toString() : null;
    }

    private static long getTimeMs(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute);
        return calendar.getTimeInMillis();
    }
}
