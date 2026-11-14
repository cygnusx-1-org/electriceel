package com.liskovsoft.smartyoutubetv2.tv.ui.modern.common;

import android.view.KeyEvent;
import android.view.View;

/**
 * The modern screens in a right-to-left language (Arabic, Persian, Hebrew) are mirrored, as the classic ones: the rail and
 * the lists at the right, the rows running to the left. The layouts mirror themselves (start and end), the code that moves
 * views or reads the keys asks here which side is the start.<br/>
 * Only for views that are laid out: the direction of a view is known once it's attached.
 */
public final class ModernRtl {
    private ModernRtl() {
    }

    public static boolean isRtl(View view) {
        return view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    /**
     * The key toward the start of the screen, the rail's side: Left, Right in a right-to-left language.
     */
    public static int startKey(View view) {
        return isRtl(view) ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
    }

    /**
     * The key toward the end of the screen.
     */
    public static int endKey(View view) {
        return isRtl(view) ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    /**
     * The focus search direction toward the start of the screen.
     */
    public static int startFocus(View view) {
        return isRtl(view) ? View.FOCUS_RIGHT : View.FOCUS_LEFT;
    }

    /**
     * A horizontal translation given toward the end (positive) or the start (negative), as an x offset.
     */
    public static float towardEnd(View view, float shift) {
        return isRtl(view) ? -shift : shift;
    }
}
