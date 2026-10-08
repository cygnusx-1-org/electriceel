package com.liskovsoft.smartyoutubetv2.common.utils;

import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shortens the info line of a short as it's shown (its card, the player): the views and the dates, and the channel handle goes.
 * Other videos keep YouTube's.<br/>
 * "Channel • 1.2M views • 9 hours ago" becomes "Channel • 1.2M • 9h". A date becomes the age the same way
 * ("Published on Oct 7, 2026" becomes "2w"), a date to come the time left ("Premieres in 3d").<br/>
 * Counts end in K (thousand), M (million) or b (billion). Ages end in s, min, h, d, w, m (months) or y.<br/>
 * Only the English texts are known, like in OldVideoFilter, which reads the line as it's kept. Anything else is left as it is.
 */
public class CompactInfo {
    private static final String DELIM = " " + Video.TERTIARY_TEXT_DELIM + " ";
    // The marks that wrap mixed RTL and LTR text (see ServiceHelper.createInfo)
    private static final Pattern BIDI_MARKS = Pattern.compile("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]");
    private static final Pattern LEADING_MARKS = Pattern.compile("^" + BIDI_MARKS.pattern() + "+");
    private static final Pattern TRAILING_MARKS = Pattern.compile(BIDI_MARKS.pattern() + "+$");
    // What YouTube puts before a date, e.g. "Streamed live on". Other words before it make it a name (e.g. a channel).
    private static final String PREFIXES = "published(?: on)?|uploaded(?: on)?|premiered|premieres|streamed(?: live)?(?: on)?|started streaming(?: on)?|scheduled for";
    // "1.2M views", "231,491 views", "1 view", "No views". YouTube might put a no-break space between the words.
    private static final Pattern VIEWS = Pattern.compile("(?i)(no|\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d+)?)[\\s\\u00A0]*([kmb])?[\\s\\u00A0]+views?");
    // "9 hours ago", "Streamed 2 days ago"
    private static final Pattern AGO = Pattern.compile("(?i)(?:(" + PREFIXES + ")[\\s\\u00A0]+)?(\\d{1,4})[\\s\\u00A0]+(second|minute|hour|day|week|month|year)s?[\\s\\u00A0]+ago");
    private static final Pattern DATE_PREFIX = Pattern.compile("(?i)" + PREFIXES);
    // Every date has its year: a part without one isn't parsed (most aren't dates, e.g. the channel)
    private static final Pattern YEAR = Pattern.compile("\\d{4}");
    // The dates of YouTube ("Published on Oct 7, 2026", "Scheduled for Sep 24, 2020, 9:00 PM")
    private static final String[] YOUTUBE_DATES = {"MMM d, y, h:mm a", "MMM d, y"};
    // The dates of the app (see DateHelper.toShortDate) in the language of the device, with or without the time
    private static final String[] APP_DATES = {"EEE MMM d y h:mm a", "EEE d MMM y H:mm", "EEE MMM d y", "EEE d MMM y"};
    private static final long SECOND_MS = 1_000;
    private static final long MINUTE_MS = 60 * SECOND_MS;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final long DAY_MS = 24 * HOUR_MS;
    // The same as OldVideoFilter's
    private static final int DAYS_IN_WEEK = 7;
    private static final int DAYS_IN_MONTH = 30;
    private static final int DAYS_IN_YEAR = 365;

    public static CharSequence compact(CharSequence info) {
        return compact(info, System.currentTimeMillis());
    }

    /**
     * A part of the line is an age or a date (e.g. "9 hours ago", "Published on Oct 7, 2026")
     */
    public static boolean hasAge(CharSequence info) {
        if (TextUtils.isEmpty(info)) {
            return false;
        }

        String text = info.toString();

        for (int[] part : getParts(text)) {
            String value = BIDI_MARKS.matcher(text.substring(part[0], part[1])).replaceAll("").trim();

            if (AGO.matcher(value).matches() || compactDate(value, System.currentTimeMillis()) != null) {
                return true;
            }
        }

        return false;
    }

    /**
     * Each part of the line between the bullets is shortened by itself. The other parts keep their styles (e.g. a red LIVE).
     */
    static CharSequence compact(CharSequence info, long nowMs) {
        if (TextUtils.isEmpty(info)) {
            return info;
        }

        String text = info.toString();
        SpannableStringBuilder result = null;
        List<int[]> parts = getParts(text);

        // From the end: a replaced part moves the ones after it
        for (int i = parts.size() - 1; i >= 0; i--) {
            int start = parts.get(i)[0];
            int end = parts.get(i)[1];
            String original = text.substring(start, end);
            String value = BIDI_MARKS.matcher(original).replaceAll("").trim();
            String part = isHandle(value) ? "" : compactPart(value, nowMs);

            if (part == null) {
                continue;
            }

            if (result == null) {
                result = new SpannableStringBuilder(info);
            }

            // The marks around it stay: a mark that starts the RTL text has its end in another part
            String leadingMarks = getMarks(original, true);
            String trailingMarks = getMarks(original, false);

            if (part.isEmpty()) {
                // With the bullet before it, or after it when it's the first
                result.replace(i > 0 ? start - DELIM.length() : start, i == 0 ? Math.min(end + DELIM.length(), result.length()) : end,
                        leadingMarks + trailingMarks);
            } else {
                result.replace(start, end, leadingMarks + part + trailingMarks);
            }
        }

        // Marks alone (e.g. the line was a handle) show nothing: no bullet goes after a marker (see VideoCardPresenter)
        if (result != null && BIDI_MARKS.matcher(result).replaceAll("").trim().isEmpty()) {
            return "";
        }

        return result != null ? result : info;
    }

    /**
     * @return the marks that start (or end) the text
     */
    private static String getMarks(String text, boolean isStart) {
        Matcher matcher = (isStart ? LEADING_MARKS : TRAILING_MARKS).matcher(text);

        return matcher.find() ? matcher.group() : "";
    }

    /**
     * The channel handle (e.g. "@betterstack")
     */
    private static boolean isHandle(String part) {
        return part.startsWith("@") && !part.contains(" ");
    }

    /**
     * @return the start and the end of each part between the bullets
     */
    private static List<int[]> getParts(String text) {
        List<int[]> result = new ArrayList<>();
        int start = 0;

        while (true) {
            int end = text.indexOf(DELIM, start);

            if (end == -1) {
                result.add(new int[] {start, text.length()});
                return result;
            }

            result.add(new int[] {start, end});
            start = end + DELIM.length();
        }
    }

    /**
     * @return the short part or null when it's neither the views nor a date (or can't be read)
     */
    static String compactPart(String part, long nowMs) {
        Matcher views = VIEWS.matcher(part);

        if (views.matches()) {
            return compactViews(views.group(1), views.group(2));
        }

        Matcher ago = AGO.matcher(part);

        if (ago.matches()) {
            return withPrefix(ago.group(1), ago.group(2) + getUnit(ago.group(3)));
        }

        return compactDate(part, nowMs);
    }

    /**
     * YouTube's short count keeps its digits ("1.2M" stays), a full one is cut like YouTube's ("231,491" is "231K", "1,234,567" is "1.2M")
     */
    private static String compactViews(String count, String suffix) {
        if ("no".equalsIgnoreCase(count)) {
            return "0";
        }

        if (suffix != null) {
            return count + getCountSuffix(suffix);
        }

        long value;

        try {
            value = Long.parseLong(count.replace(",", ""));
        } catch (NumberFormatException e) { // e.g. a fraction without a suffix, or too long
            return null;
        }

        if (value < 1_000) {
            return String.valueOf(value);
        }

        String[] suffixes = {"K", "M", "b"};
        int index = 0;
        double shortValue = value / 1_000.0;

        while (shortValue >= 1_000 && index < suffixes.length - 1) {
            shortValue /= 1_000;
            index++;
        }

        // One decimal below ten (e.g. "1.2M"), cut not rounded
        String number = shortValue < 10 ? String.valueOf(Math.floor(shortValue * 10) / 10) : String.valueOf((long) shortValue);

        return number.replaceAll("\\.0$", "") + suffixes[index];
    }

    private static String getCountSuffix(String suffix) {
        switch (suffix.toLowerCase(Locale.US)) {
            case "k":
                return "K";
            case "m":
                return "M";
            default:
                return "b";
        }
    }

    private static String getUnit(String unit) {
        switch (unit.toLowerCase(Locale.US)) {
            case "second":
                return "s";
            case "minute":
                return "min";
            case "hour":
                return "h";
            case "day":
                return "d";
            case "week":
                return "w";
            case "month":
                return "m";
            default:
                return "y";
        }
    }

    /**
     * A date of YouTube after a known prefix or none (e.g. "Published on"), or a date of the app after any (e.g. "Premieres")
     */
    private static String compactDate(String part, long nowMs) {
        if (!YEAR.matcher(part).find()) {
            return null;
        }

        int wordStart = 0;

        while (wordStart != -1) {
            String date = part.substring(wordStart);
            String prefix = part.substring(0, wordStart).trim();
            Long timeMs = prefix.isEmpty() || DATE_PREFIX.matcher(prefix).matches() ? parseDate(date, YOUTUBE_DATES, Locale.US) : null;

            if (timeMs == null) {
                // The day of the week makes it the app's
                timeMs = parseDate(date, APP_DATES, Locale.getDefault());
            }

            if (timeMs != null) {
                return withPrefix(prefix, getAge(timeMs, nowMs, hasTime(date)));
            }

            int space = part.indexOf(' ', wordStart);
            wordStart = space != -1 ? space + 1 : -1;
        }

        return null;
    }

    /**
     * @return the time (ms) when the whole text is a date in one of the patterns
     */
    private static Long parseDate(String text, String[] patterns, Locale locale) {
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date date = format.parse(text, position);

            if (date != null && position.getIndex() == text.length()) {
                return date.getTime();
            }
        }

        return null;
    }

    private static boolean hasTime(String date) {
        return date.contains(":");
    }

    /**
     * @param hasTime a date alone counts in whole days. Today's counts from midnight (e.g. "9h" at 9 AM): its hour isn't known.
     * @return e.g. "9h" or "in 3d" for a date to come
     */
    static String getAge(long timeMs, long nowMs, boolean hasTime) {
        boolean isFuture = timeMs > nowMs;
        long ageMs = Math.abs(nowMs - timeMs);
        long days = hasTime ? -1 : getDayDistance(timeMs, nowMs);
        String age;

        if (days > 0) {
            age = getDaysAge(days);
        } else if (ageMs >= DAY_MS) {
            age = getDaysAge(ageMs / DAY_MS);
        } else if (ageMs >= HOUR_MS) {
            age = ageMs / HOUR_MS + "h";
        } else if (ageMs >= MINUTE_MS) {
            age = ageMs / MINUTE_MS + "min";
        } else {
            age = ageMs / SECOND_MS + "s";
        }

        return isFuture ? "in " + age : age;
    }

    private static String getDaysAge(long days) {
        if (days >= DAYS_IN_YEAR) {
            return days / DAYS_IN_YEAR + "y";
        }

        if (days >= DAYS_IN_MONTH) {
            return days / DAYS_IN_MONTH + "m";
        }

        if (days >= DAYS_IN_WEEK) {
            return days / DAYS_IN_WEEK + "w";
        }

        return days + "d";
    }

    /**
     * Whole days between the two dates in the time zone of the device
     */
    private static long getDayDistance(long timeMs, long nowMs) {
        return Math.abs(getDayStartMs(nowMs) - getDayStartMs(timeMs)) / DAY_MS;
    }

    private static long getDayStartMs(long timeMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMs);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        // The offset of the day: the clock change days are 23 or 25 hours long
        return calendar.getTimeInMillis() + calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET);
    }

    /**
     * "Published on" and "Uploaded on" go, the words that tell what happened stay without "on" or "for" ("Streamed 2d", "Scheduled in 3d")
     */
    private static String withPrefix(String prefix, String age) {
        if (prefix == null) {
            return age;
        }

        String result = prefix.replaceAll("(?i)^(published|uploaded)( on)?$", "")
                .replaceAll("(?i)^streamed live", "Streamed")
                .replaceAll("(?i)\\s+(on|for)$", "")
                .trim();

        return result.isEmpty() ? age : result + " " + age;
    }
}
