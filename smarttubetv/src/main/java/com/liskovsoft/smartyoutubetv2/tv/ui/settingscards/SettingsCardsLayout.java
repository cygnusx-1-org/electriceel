package com.liskovsoft.smartyoutubetv2.tv.ui.settingscards;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.leanback.widget.VerticalGridView;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * The settings cards under their title, placed the same on every screen that has them (the Settings section, a settings section
 * on its own screen), so both land on the same pixels.<br/>
 * Whatever the number of rows, they stay where the grid scrolls three when the second has focus: the third row ends as far from
 * the bottom of the screen as the title starts from the top, and moving between three rows doesn't scroll them. The title moves
 * up to a row spacing above them. When it reaches the top of the screen, the rows stop under it instead.
 */
public class SettingsCardsLayout {
    private final VerticalGridView mGrid;
    private final TextView mTitleView;
    private final int mDefaultRowsTop;
    private final int mDefaultTitleTop;
    private final int[] mGridLocation = new int[2];
    private final int[] mTitleLocation = new int[2];
    // Before the draw, so the rows and the title are drawn once, where they land
    private final ViewTreeObserver.OnPreDrawListener mOnPreDraw = () -> !update();

    private SettingsCardsLayout(VerticalGridView grid, TextView titleView) {
        mGrid = grid;
        mTitleView = titleView;
        mDefaultRowsTop = grid.getPaddingTop();
        mDefaultTitleTop = titleView.getPaddingTop();
    }

    /**
     * @param gridView the view of the grid fragment
     * @return the view of the fragment: the grid under the title
     */
    public static View wrap(LayoutInflater inflater, @Nullable ViewGroup container, View gridView) {
        FrameLayout root = (FrameLayout) inflater.inflate(R.layout.settings_cards, container, false);
        root.addView(gridView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        return root;
    }

    /**
     * Places the cards and the title until {@link #detach()}
     *
     * @param root the view made by {@link #wrap}
     */
    public static SettingsCardsLayout attach(View root, VerticalGridView grid) {
        SettingsCardsLayout layout = new SettingsCardsLayout(grid, root.findViewById(R.id.settings_cards_title));
        grid.getViewTreeObserver().addOnPreDrawListener(layout.mOnPreDraw);

        return layout;
    }

    public void detach() {
        mGrid.getViewTreeObserver().removeOnPreDrawListener(mOnPreDraw);
    }

    public void setTitle(CharSequence title) {
        mTitleView.setText(title);
    }

    /**
     * @return a padding changed, the screen is laid out again before it's drawn
     */
    private boolean update() {
        if (mGrid.getChildCount() == 0) {
            return false;
        }

        int margin = mDefaultTitleTop;
        int spacing = mGrid.getVerticalSpacing();
        int threeRowsHeight = 3 * mGrid.getChildAt(0).getHeight() + 2 * spacing;
        int titleHeight = mTitleView.getHeight() - mTitleView.getPaddingTop() - mTitleView.getPaddingBottom();
        mGrid.getLocationInWindow(mGridLocation);
        mTitleView.getLocationInWindow(mTitleLocation);
        int titleOffset = mTitleLocation[1] - mGridLocation[1];

        int rowsTop = Math.min(mDefaultRowsTop, mGrid.getHeight() - margin - threeRowsHeight);
        int titleTop = Math.max(0, Math.min(margin, rowsTop - spacing - titleHeight - titleOffset));
        rowsTop = Math.max(rowsTop, titleOffset + titleTop + titleHeight + spacing);
        // Less than the margin when the rows stopped under the title. None when three don't fit: the third scrolls to the edge.
        int rowsBottom = Math.max(0, Math.min(margin, mGrid.getHeight() - rowsTop - threeRowsHeight));

        boolean isChanged = false;

        if (mGrid.getPaddingTop() != rowsTop || mGrid.getPaddingBottom() != rowsBottom) {
            mGrid.setPaddingRelative(mGrid.getPaddingStart(), rowsTop, mGrid.getPaddingEnd(), rowsBottom);
            isChanged = true;
        }

        if (mTitleView.getPaddingTop() != titleTop) {
            mTitleView.setPaddingRelative(mTitleView.getPaddingStart(), titleTop, mTitleView.getPaddingEnd(), mTitleView.getPaddingBottom());
            isChanged = true;
        }

        return isChanged;
    }
}
