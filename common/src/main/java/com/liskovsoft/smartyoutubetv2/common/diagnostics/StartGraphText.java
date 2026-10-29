package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import java.util.Arrays;
import java.util.Locale;

/**
 * A start (see VideoStart) as a text waterfall, for the log: a row per stage, on a time axis from the user's action
 * <pre>
 * Video start 3420 ms dQw4w9WgXcQ: intent, first of run, TV sabr
 *                     0s        1s        2s        3s
 * player screen       |##                                   |   52
 * formats             | ##################                  | 1840
 *   lock wait         |  ###                                |  210 warm-up
 * </pre>
 */
public final class StartGraphText {
    static final int BAR_WIDTH = 50;
    private static final int MAX_LABEL = 24;
    /**
     * Between the seconds marked on the axis, at least
     */
    private static final int MIN_TICK_COLUMNS = 8;
    private static final long[] TICK_STEPS_MS = {500, 1_000, 2_000, 5_000, 10_000, 20_000, 30_000, 60_000};

    private StartGraphText() {
    }

    public static String render(VideoStart start) {
        long spanMs = getSpanMs(start);
        int labelWidth = getLabelWidth(start);
        StringBuilder result = new StringBuilder();

        result.append(String.format(Locale.US, start.isPlayed() ? "Video start %d ms %s: %s" : "Video start not playing after %d ms %s: %s",
                start.totalMs, start.videoId, start.describe()));
        result.append('\n').append(axis(labelWidth, spanMs));

        for (VideoStart.Stage stage : start.stages) {
            result.append('\n').append(row(stage, labelWidth, spanMs));
        }

        return result.toString();
    }

    /**
     * The time the axis spans: the start's, or longer when a stage ends after it played (it can't, but in case)
     */
    static long getSpanMs(VideoStart start) {
        long spanMs = Math.max(1, start.totalMs);

        for (VideoStart.Stage stage : start.stages) {
            spanMs = Math.max(spanMs, stage.endMs);
        }

        return spanMs;
    }

    /**
     * Between the ticks of the axis: the shortest step that leaves room for its labels
     */
    public static long getTickStepMs(long spanMs, int columns, int minTickColumns) {
        for (long step : TICK_STEPS_MS) {
            if (step * columns / Math.max(1, spanMs) >= minTickColumns) {
                return step;
            }
        }

        return TICK_STEPS_MS[TICK_STEPS_MS.length - 1];
    }

    /**
     * E.g. 1s, 1.5s
     */
    public static String formatTick(long ms) {
        return ms % 1_000 == 0 ? (ms / 1_000) + "s" : String.format(Locale.US, "%.1fs", ms / 1_000f);
    }

    private static String axis(int labelWidth, long spanMs) {
        char[] line = new char[labelWidth + 1 + BAR_WIDTH + 1];
        Arrays.fill(line, ' ');
        long step = getTickStepMs(spanMs, BAR_WIDTH, MIN_TICK_COLUMNS);

        for (long ms = 0; ms <= spanMs; ms += step) {
            String tick = formatTick(ms);
            int column = labelWidth + 1 + toColumn(ms, spanMs);

            if (column + tick.length() > line.length) {
                break;
            }

            tick.getChars(0, tick.length(), line, column);
        }

        return new String(line).replaceAll("\\s+$", "");
    }

    private static String row(VideoStart.Stage stage, int labelWidth, long spanMs) {
        StringBuilder label = new StringBuilder();

        for (int i = 0; i < stage.depth; i++) {
            label.append("  ");
        }

        label.append(stage.name);
        String labelText = label.length() > labelWidth ? label.substring(0, labelWidth) : label.toString();
        char[] bar = new char[BAR_WIDTH];
        Arrays.fill(bar, ' ');
        // A stage that starts at the end is drawn in the last column
        int from = Math.min(BAR_WIDTH - 1, toColumn(stage.startMs, spanMs));
        boolean isMark = stage.getCategory() == VideoStart.Category.MARK;

        if (isMark) {
            bar[from] = '^';
        } else {
            int to = Math.max(from + 1, (int) Math.min(BAR_WIDTH, (stage.endMs * BAR_WIDTH + spanMs - 1) / spanMs));

            for (int i = from; i < to; i++) {
                bar[i] = '#';
            }
        }

        long value = isMark ? stage.startMs : stage.getDurationMs();
        String text = String.format(Locale.US, "%-" + labelWidth + "s|%s|%5d", labelText, new String(bar), value);

        return stage.detail != null ? text + " " + stage.detail : text;
    }

    private static int toColumn(long ms, long spanMs) {
        return (int) Math.max(0, Math.min(BAR_WIDTH, ms * BAR_WIDTH / spanMs));
    }

    private static int getLabelWidth(VideoStart start) {
        int width = 0;

        for (VideoStart.Stage stage : start.stages) {
            width = Math.max(width, stage.depth * 2 + stage.name.length());
        }

        return Math.min(MAX_LABEL, width + 1);
    }
}
