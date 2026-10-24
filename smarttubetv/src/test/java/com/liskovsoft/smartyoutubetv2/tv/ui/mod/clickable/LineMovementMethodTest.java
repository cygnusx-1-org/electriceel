package com.liskovsoft.smartyoutubetv2.tv.ui.mod.clickable;

import android.app.Application;
import android.text.Layout;
import android.text.Spannable;
import android.view.KeyEvent;
import android.view.View.MeasureSpec;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The app's Conscrypt has no native library here, and no TLS is used
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LineMovementMethodTest {
    private static final int LINES = 40;
    private static final int PADDING = 14;
    private TextView mView;

    @Before
    public void setUp() {
        mView = new TextView(RuntimeEnvironment.getApplication());
        mView.setPadding(0, PADDING, 0, PADDING);
        StringBuilder text = new StringBuilder();

        for (int i = 1; i <= LINES; i++) {
            text.append("line ").append(i).append(i < LINES ? "\n" : "");
        }

        mView.setText(text);
        mView.setMovementMethod(LineMovementMethod.getInstance());

        // Five and a half lines high: lining up the bottom line would cut the top one
        int height = mView.getLineHeight() * 11 / 2 + 2 * PADDING;
        mView.measure(MeasureSpec.makeMeasureSpec(600, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        mView.layout(0, 0, 600, height);
    }

    @Test
    public void downMovesALineWithTheTopOneWhole() {
        Layout layout = mView.getLayout();

        for (int line = 1; line <= 10; line++) {
            assertTrue(press(KeyEvent.KEYCODE_DPAD_DOWN));
            assertEquals(layout.getLineTop(line), mView.getScrollY());
        }
    }

    @Test
    public void upMovesALineBack() {
        Layout layout = mView.getLayout();

        press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue(press(KeyEvent.KEYCODE_DPAD_UP));

        assertEquals(layout.getLineTop(2), mView.getScrollY());
    }

    @Test
    public void downStopsOnceTheLastLineIsWhole() {
        Layout layout = mView.getLayout();
        int innerHeight = mView.getHeight() - 2 * PADDING;
        int presses = 0;

        while (press(KeyEvent.KEYCODE_DPAD_DOWN)) {
            presses++;
            assertEquals(0, mView.getScrollY() - layout.getLineTop(layout.getLineForVertical(mView.getScrollY())));
        }

        assertTrue(layout.getLineBottom(LINES - 1) <= mView.getScrollY() + innerHeight);
        // Five lines are whole in view: the sixth line can go to the top once the first is gone
        assertEquals(LINES - 5, presses);
        assertFalse(press(KeyEvent.KEYCODE_DPAD_DOWN));
    }

    @Test
    public void pageDownBringsTheFirstLineNotWholeToTheTop() {
        assertTrue(press(KeyEvent.KEYCODE_PAGE_DOWN));

        assertEquals(mView.getLayout().getLineTop(5), mView.getScrollY());
    }

    private boolean press(int keyCode) {
        return mView.getMovementMethod().onKeyDown(mView, (Spannable) mView.getText(), keyCode, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
    }
}
