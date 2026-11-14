package com.liskovsoft.smartyoutubetv2.common.misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The time codes of a video description, a line each (e.g. "0:00 Intro" of a tracklist or a list of highlights). YouTube makes
 * chapters of them only when they start at 0:00, there are three or more and each is ten seconds long or more: these are all of
 * them.
 */
public final class DescriptionTimestamps {
    /**
     * H:MM:SS or M:SS (MM:SS), not a part of a longer number or time code: not 16:9, 1:2:3:4, 3.5:00 or 12:30pm
     */
    private static final Pattern TIME_CODE = Pattern.compile("(?<![\\w:.])(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})(?![\\w:])");
    private static final Pattern LINE_BREAK = Pattern.compile("\\r?\\n");

    public static final class Timestamp {
        public final long positionMs;
        /**
         * The line it's on, as written
         */
        public final String text;

        Timestamp(long positionMs, String text) {
            this.positionMs = positionMs;
            this.text = text;
        }
    }

    private DescriptionTimestamps() {
    }

    /**
     * @param durationMs the video's length: the time codes past it aren't the video's (e.g. a time of day). Unknown (0 or less): all of
     *                   them.
     * @return the first time code of each line that has one, in the order of the description
     */
    public static List<Timestamp> parse(String description, long durationMs) {
        if (description == null || description.isEmpty()) {
            return Collections.emptyList();
        }

        List<Timestamp> result = new ArrayList<>();

        for (String line : LINE_BREAK.split(description)) {
            long positionMs = firstPositionMs(line);

            if (positionMs == -1 || (durationMs > 0 && positionMs > durationMs)) {
                continue;
            }

            result.add(new Timestamp(positionMs, line.trim()));
        }

        return result;
    }

    /**
     * @return the one playing at the position: the latest that starts at it or before, or null before them all
     */
    public static Timestamp current(List<Timestamp> timestamps, long positionMs) {
        Timestamp result = null;

        for (Timestamp timestamp : timestamps) {
            if (timestamp.positionMs <= positionMs && (result == null || timestamp.positionMs > result.positionMs)) {
                result = timestamp;
            }
        }

        return result;
    }

    private static long firstPositionMs(String line) {
        Matcher matcher = TIME_CODE.matcher(line);

        while (matcher.find()) {
            boolean hasHours = matcher.group(1) != null;
            int hours = hasHours ? Integer.parseInt(matcher.group(1)) : 0;
            int minutes = Integer.parseInt(matcher.group(2));
            int seconds = Integer.parseInt(matcher.group(3));

            if (seconds > 59 || (hasHours && minutes > 59)) {
                continue;
            }

            return TimeUnit.HOURS.toMillis(hours) + TimeUnit.MINUTES.toMillis(minutes) + TimeUnit.SECONDS.toMillis(seconds);
        }

        return -1;
    }
}
