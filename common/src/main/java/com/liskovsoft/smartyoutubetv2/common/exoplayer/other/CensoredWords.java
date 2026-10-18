package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds when the words YouTube's automatic captions censor are said, from the captions in json3 format.<br/>
 * The automatic captions time each word on its own and show a censored one as "[ __ ]" (with no-break spaces).
 * The formats the player shows (TTML, WebVTT) only time whole lines, which last seconds.
 */
public final class CensoredWords {
    public static final String CAPTION_FORMAT = "json3";
    private static final Pattern MARKER = Pattern.compile("\\[[\\s\\u00A0]*__[\\s\\u00A0]*]");
    // The times are where the speech recognition put the words, which is a little off at times
    static final long LEAD_MS = 150;
    static final long TRAIL_MS = 100;
    // A word ends where the next one starts. Before a pause that's seconds later.
    static final long MAX_WORD_MS = 1_000;

    public static class Range {
        public final long startMs;
        public final long endMs;

        public Range(long startMs, long endMs) {
            this.startMs = startMs;
            this.endMs = endMs;
        }

        public boolean contains(long positionMs) {
            return positionMs >= startMs && positionMs < endMs;
        }

        @NonNull
        @Override
        public String toString() {
            return startMs + "-" + endMs;
        }
    }

    private CensoredWords() {
    }

    /**
     * @return the ranges to mute, sorted and not overlapping. Empty when nothing is censored.
     */
    @NonNull
    public static List<Range> findRanges(@NonNull String json3) throws JSONException {
        List<Range> words = new ArrayList<>();
        JSONArray events = new JSONObject(json3).optJSONArray("events");

        if (events == null) {
            return words;
        }

        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            JSONArray segs = event != null ? event.optJSONArray("segs") : null;

            if (segs == null) {
                continue;
            }

            long lineStartMs = event.optLong("tStartMs");
            long lineEndMs = lineStartMs + event.optLong("dDurationMs");

            for (int j = 0; j < segs.length(); j++) {
                JSONObject seg = segs.optJSONObject(j);

                if (seg == null || !MARKER.matcher(seg.optString("utf8")).find()) {
                    continue;
                }

                long startMs = lineStartMs + seg.optLong("tOffsetMs");
                JSONObject next = segs.optJSONObject(j + 1);
                long endMs = next != null ? lineStartMs + next.optLong("tOffsetMs") : lineEndMs;

                words.add(new Range(Math.max(0, startMs - LEAD_MS), Math.min(Math.max(endMs, startMs), startMs + MAX_WORD_MS) + TRAIL_MS));
            }
        }

        return merge(words);
    }

    private static List<Range> merge(List<Range> ranges) {
        Collections.sort(ranges, (a, b) -> Long.compare(a.startMs, b.startMs));
        List<Range> result = new ArrayList<>();

        for (Range range : ranges) {
            Range last = result.isEmpty() ? null : result.get(result.size() - 1);

            if (last != null && range.startMs <= last.endMs) {
                result.set(result.size() - 1, new Range(last.startMs, Math.max(last.endMs, range.endMs)));
            } else {
                result.add(range);
            }
        }

        return result;
    }
}
