package com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.HorizontalScrollView;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Row that scrolls with the focus: the focused item stays the screen margin away from the edges (a plain
 * HorizontalScrollView brings it just onto the screen, its focus ring cut at the edge). The content has that margin as its
 * own padding, so the first and last items can keep it too.
 */
public class ModernScrollRow extends HorizontalScrollView {
    private final int mEdge;

    public ModernScrollRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        mEdge = getResources().getDimensionPixelSize(R.dimen.modern_content_left);
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        Rect withEdges = new Rect(rect);
        withEdges.inset(-mEdge, 0);
        return super.computeScrollDeltaToGetChildRectOnScreen(withEdges);
    }
}
