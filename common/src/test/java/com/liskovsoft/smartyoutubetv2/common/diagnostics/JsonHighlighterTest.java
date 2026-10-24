package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class JsonHighlighterTest {
    private static final int KEY = 1;
    private static final int STRING = 2;
    private static final int NUMBER = 3;
    private static final int LITERAL = 4;

    @Test
    public void eachPartHasItsColor() {
        String json = "{\n \"name\": \"a \\\"b\\\" c\",\n \"ms\": -1.5e3,\n \"list\": [true, false, null, 7]\n}";

        assertEquals(Arrays.asList(
                KEY + " \"name\"",
                STRING + " \"a \\\"b\\\" c\"",
                KEY + " \"ms\"",
                NUMBER + " -1.5e3",
                KEY + " \"list\"",
                LITERAL + " true",
                LITERAL + " false",
                LITERAL + " null",
                NUMBER + " 7"), spans(json));
    }

    @Test
    public void whatsInAStringIsTheString() {
        assertEquals(Arrays.asList(KEY + " \"a\"", STRING + " \"true, 12: null\""), spans("{\"a\": \"true, 12: null\"}"));
    }

    @Test
    public void cutShortItRunsToTheEnd() {
        assertEquals(Arrays.asList(KEY + " \"a\"", STRING + " \"bc"), spans("{\"a\": \"bc"));
        // Its colon is cut off: it can't be told from a string
        assertEquals(Arrays.asList(STRING + " \"a\""), spans("{\"a\""));
    }

    /**
     * "color text" of each span, in order
     */
    private static List<String> spans(String json) {
        SpannableStringBuilder text = JsonHighlighter.highlight(json, KEY, STRING, NUMBER, LITERAL);
        List<String> result = new ArrayList<>();

        for (ForegroundColorSpan span : text.getSpans(0, text.length(), ForegroundColorSpan.class)) {
            result.add(span.getForegroundColor() + " " + text.subSequence(text.getSpanStart(span), text.getSpanEnd(span)));
        }

        return result;
    }
}
