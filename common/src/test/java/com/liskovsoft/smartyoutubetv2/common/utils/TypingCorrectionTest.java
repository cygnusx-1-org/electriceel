package com.liskovsoft.smartyoutubetv2.common.utils;

import android.graphics.Typeface;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * A search field with the keyboard's corrections off
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TypingCorrectionTest {
    private EditText mField;

    /**
     * As the search fields' layouts have them: plain text, in a font of their own (the Modern search's is bold)
     */
    @Before
    public void setUp() {
        mField = new EditText(RuntimeEnvironment.getApplication());
        mField.setInputType(InputType.TYPE_CLASS_TEXT);
        mField.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    }

    /**
     * What the keyboard is told when it opens: a password shown as typed, which no keyboard corrects
     */
    @Test
    public void theKeyboardIsToldAVisiblePassword() {
        TypingCorrection.turnOff(mField, false);

        EditorInfo editorInfo = new EditorInfo();
        mField.onCreateInputConnection(editorInfo);

        assertEquals(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, editorInfo.inputType);
    }

    /**
     * On a Fire TV: not a password, so its keyboard keeps voice typing
     */
    @Test
    public void onAFireTV_theKeyboardIsToldPlainTextWithoutSuggestions() {
        TypingCorrection.turnOff(mField, true);

        EditorInfo editorInfo = new EditorInfo();
        mField.onCreateInputConnection(editorInfo);

        assertEquals(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, editorInfo.inputType);
    }

    /**
     * setInputType would switch a visible password's field to a plain monospace
     */
    @Test
    public void theFieldKeepsItsFont() {
        Typeface font = mField.getTypeface();

        TypingCorrection.turnOff(mField);

        assertSame(font, mField.getTypeface());
    }
}
