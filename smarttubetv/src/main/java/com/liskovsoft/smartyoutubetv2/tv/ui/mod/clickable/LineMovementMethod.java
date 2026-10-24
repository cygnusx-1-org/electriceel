package com.liskovsoft.smartyoutubetv2.tv.ui.mod.clickable;

import android.text.Layout;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

/**
 * Scrolls the text down a line at a time with its top line whole. The stock one lines up the bottom line with the bottom, which
 * cuts the top line in half when the view isn't a whole number of lines high. Scrolling up lines up the top already. The links
 * work as they do in LinkMovementMethod.
 */
public class LineMovementMethod extends LinkMovementMethod {
    private static LineMovementMethod sInstance;

    public static LineMovementMethod getInstance() {
        if (sInstance == null) {
            sInstance = new LineMovementMethod();
        }

        return sInstance;
    }

    @Override
    protected boolean down(TextView widget, Spannable buffer) {
        int scrollY = widget.getScrollY();
        boolean isHandled = super.down(widget, buffer);

        // Nothing further down, or it went to a link
        if (!isHandled || widget.getScrollY() == scrollY) {
            return isHandled;
        }

        // Scrolled the stock way: a line from where it was instead
        widget.scrollTo(widget.getScrollX(), scrollY);
        scrollLinesDown(widget, 1);

        return true;
    }

    /**
     * The first line that isn't whole in view goes to the top
     */
    @Override
    protected boolean pageDown(TextView widget, Spannable buffer) {
        Layout layout = widget.getLayout();

        if (layout == null) {
            return false;
        }

        int scrollY = widget.getScrollY();
        int bottom = scrollY + getInnerHeight(widget);
        int wholeLines = 0;

        for (int line = layout.getLineForVertical(scrollY); line < layout.getLineCount() && layout.getLineBottom(line) <= bottom; line++) {
            if (layout.getLineTop(line) >= scrollY) {
                wholeLines++;
            }
        }

        return scrollLinesDown(widget, Math.max(wholeLines, 1));
    }

    /**
     * The top line goes down by the lines, lined up with the top. Not past the last line whole in view.
     */
    private static boolean scrollLinesDown(TextView widget, int lines) {
        Layout layout = widget.getLayout();

        if (layout == null || layout.getLineCount() == 0) {
            return false;
        }

        int scrollY = widget.getScrollY();
        int lastLine = layout.getLineCount() - 1;

        if (layout.getLineBottom(lastLine) <= scrollY + getInnerHeight(widget)) {
            return false;
        }

        // A top line cut short (e.g. scrolled to a link) counts as the first one moved past
        int topLine = layout.getLineForVertical(scrollY);
        widget.scrollTo(widget.getScrollX(), layout.getLineTop(Math.min(topLine + lines, lastLine)));

        return true;
    }

    private static int getInnerHeight(TextView widget) {
        return widget.getHeight() - widget.getTotalPaddingTop() - widget.getTotalPaddingBottom();
    }
}
