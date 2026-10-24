package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

/**
 * Colors JSON for reading (see the Debugging panel's Preview data): its keys, strings, numbers, and true, false and null. The rest
 * keeps the text's color. JSON cut short is colored too: a string left open runs to the end.
 */
public final class JsonHighlighter {
    private static final String[] LITERALS = {"true", "false", "null"};

    private JsonHighlighter() {
    }

    public static SpannableStringBuilder highlight(CharSequence json, int keyColor, int stringColor, int numberColor, int literalColor) {
        SpannableStringBuilder result = new SpannableStringBuilder(json);
        int length = json.length();
        int i = 0;

        while (i < length) {
            char c = json.charAt(i);
            int end;

            if (c == '"') {
                end = getStringEnd(json, i);
                // A key is followed by its colon
                color(result, i, end, getNextNonSpace(json, end) == ':' ? keyColor : stringColor);
            } else if (c == '-' || isDigit(c)) {
                end = i + 1;

                while (end < length && isNumberPart(json.charAt(end))) {
                    end++;
                }

                color(result, i, end, numberColor);
            } else if ((end = getLiteralEnd(json, i)) > i) {
                color(result, i, end, literalColor);
            } else {
                end = i + 1;
            }

            i = end;
        }

        return result;
    }

    /**
     * Past its closing quote, or the end of the text when it has none
     */
    private static int getStringEnd(CharSequence json, int start) {
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == '\\') {
                i++; // escaped, e.g. \"
            } else if (c == '"') {
                return i + 1;
            }
        }

        return json.length();
    }

    /**
     * 0 at the end of the text
     */
    private static char getNextNonSpace(CharSequence json, int start) {
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                return c;
            }
        }

        return 0;
    }

    /**
     * Past true, false or null starting there, else the start
     */
    private static int getLiteralEnd(CharSequence json, int start) {
        for (String literal : LITERALS) {
            int end = start + literal.length();

            if (end <= json.length() && literal.contentEquals(json.subSequence(start, end))) {
                return end;
            }
        }

        return start;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isNumberPart(char c) {
        return isDigit(c) || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-';
    }

    private static void color(SpannableStringBuilder text, int start, int end, int color) {
        text.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
