package com.liskovsoft.smartyoutubetv2.common.misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The links of a video description to other YouTube videos (e.g. "Previous video: https://youtu.be/..." or the other parts of a
 * series), with the line each is on.
 */
public final class DescriptionVideoLinks {
    /**
     * A video's address: youtube.com (or www., m., music.) /watch?v=, /shorts/, /live/, /embed/ or /v/, or youtu.be/, with or without
     * the scheme. Not a part of another address (e.g. notyoutube.com or example.com/youtu.be/...). The id is 11 characters, then the
     * rest of the address (e.g. its time).
     */
    private static final Pattern LINK = Pattern.compile("(?<![\\w.@/-])(?:https?://)?(?:(?:www|m|music)\\.)?" +
            "(?:youtube\\.com/(?:watch/?\\?(?:[^\\s#]*?&)?v=|(?:shorts|live|embed|v)/)|youtu\\.be/)([\\w-]{11})(?![\\w-])\\S*",
            Pattern.CASE_INSENSITIVE);
    /**
     * The time of an address: t= (90, 90s, 1m30s, 1h2m3s) or start= (of an embed)
     */
    private static final Pattern TIME = Pattern.compile("[?&#](?:t|start)=(?:(\\d{1,9})h)?(?:(\\d{1,9})m)?(?:(\\d{1,9})s?)?(?!\\w)");
    private static final Pattern LINE_BREAK = Pattern.compile("\\r?\\n");

    public static final class VideoLink {
        public final String videoId;
        /**
         * Where it starts, from the address, or -1 for none
         */
        public final long positionMs;
        /**
         * The line it's on, as written. A line of only the link gets the line it's under (e.g. "Previous video:") in front.
         */
        public final String text;

        VideoLink(String videoId, long positionMs, String text) {
            this.videoId = videoId;
            this.positionMs = positionMs;
            this.text = text;
        }
    }

    private DescriptionVideoLinks() {
    }

    /**
     * @param exceptVideoId the video of the description: its own links aren't another video's. Null: none left out.
     * @return each video link, in the order of the description: the same video at the same time once
     */
    public static List<VideoLink> parse(String description, String exceptVideoId) {
        if (description == null || description.isEmpty()) {
            return Collections.emptyList();
        }

        List<VideoLink> result = new ArrayList<>();
        Set<String> added = new HashSet<>();
        // The latest line without an address since a blank line: the label of the lines of only a link under it
        String label = null;

        for (String rawLine : LINE_BREAK.split(description)) {
            String line = rawLine.trim();

            if (line.isEmpty()) {
                label = null;
                continue;
            }

            Matcher matcher = LINK.matcher(line);
            List<String> videoIds = new ArrayList<>();
            List<Long> positions = new ArrayList<>();
            StringBuilder withoutLinks = new StringBuilder();
            int end = 0;

            while (matcher.find()) {
                videoIds.add(matcher.group(1));
                positions.add(positionMs(matcher.group()));
                withoutLinks.append(line, end, matcher.start());
                end = matcher.end();
            }

            if (videoIds.isEmpty()) {
                // Not another link's (e.g. "Subscribe: https://youtube.com/@channel")
                label = line.contains("://") ? null : line;
                continue;
            }

            withoutLinks.append(line, end, line.length());
            String text = label != null && !hasWords(withoutLinks) ? label + " " + line : line;

            for (int i = 0; i < videoIds.size(); i++) {
                String videoId = videoIds.get(i);
                long positionMs = positions.get(i);

                if (!videoId.equals(exceptVideoId) && added.add(videoId + "@" + positionMs)) {
                    result.add(new VideoLink(videoId, positionMs, text));
                }
            }
        }

        return result;
    }

    /**
     * @return -1 when the address has no time
     */
    private static long positionMs(String link) {
        Matcher matcher = TIME.matcher(link);

        while (matcher.find()) {
            if (matcher.group(1) == null && matcher.group(2) == null && matcher.group(3) == null) {
                continue;
            }

            return TimeUnit.HOURS.toMillis(toLong(matcher.group(1))) + TimeUnit.MINUTES.toMillis(toLong(matcher.group(2))) +
                    TimeUnit.SECONDS.toMillis(toLong(matcher.group(3)));
        }

        return -1;
    }

    private static long toLong(String number) {
        return number != null ? Long.parseLong(number) : 0;
    }

    /**
     * Not only signs (e.g. "->", "▶ ", " | ")
     */
    private static boolean hasWords(CharSequence text) {
        for (int i = 0; i < text.length(); ) {
            int codePoint = Character.codePointAt(text, i);

            if (Character.isLetterOrDigit(codePoint)) {
                return true;
            }

            i += Character.charCount(codePoint);
        }

        return false;
    }
}
