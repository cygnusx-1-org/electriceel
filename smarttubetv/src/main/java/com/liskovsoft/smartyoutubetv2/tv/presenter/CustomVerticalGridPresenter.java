package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.leanback.widget.VerticalGridPresenter;
import androidx.leanback.widget.VerticalGridView;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class CustomVerticalGridPresenter extends VerticalGridPresenter {
    private final int mLayoutResId;
    private final int mRootResId;

    public CustomVerticalGridPresenter() {
        this(-1, -1);
    }

    public CustomVerticalGridPresenter(int layoutResId, int rootResId) {
        super(ViewUtil.FOCUS_ZOOM_FACTOR, ViewUtil.FOCUS_DIMMER_ENABLED);

        mLayoutResId = layoutResId;
        mRootResId = rootResId;
    }

    /**
     * Subclass may override this to inflate a different layout.
     */
    @Override
    protected ViewHolder createGridViewHolder(ViewGroup parent) {
        if (mLayoutResId > 0 && mRootResId > 0) {
            View root = LayoutInflater.from(parent.getContext()).inflate(
                    mLayoutResId, parent, false);
            return new ViewHolder(root.findViewById(mRootResId));
        }

        ViewHolder viewHolder = super.createGridViewHolder(parent);

        // Not working!!!
        // Tweaking grid to fix broken UI layout on some devices like Shield
        //if (viewHolder.getGridView() != null) {
        //    viewHolder.getGridView().setGravity(Gravity.TOP | Gravity.START);
        //    viewHolder.getGridView().setItemViewCacheSize(0);
        //    //viewHolder.getGridView().setItemAlignmentOffsetPercent(0);
        //    //viewHolder.getGridView().setItemAlignmentOffsetWithPadding(true);
        //}

        return viewHolder;
    }

    @Override
    protected void initializeGridViewHolder(ViewHolder vh) {
        super.initializeGridViewHolder(vh);

        preloadNextRow(vh.getGridView());
    }

    /**
     * Lays out a row past each edge of the grid, so a row's cards are bound and their images loaded before it scrolls in.
     * Leanback lays out only the visible rows, with the item prefetch off (see GridLayoutManager).
     */
    @SuppressLint("RestrictedApi")
    private static void preloadNextRow(VerticalGridView gridView) {
        gridView.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int rowHeight = 0;

            for (int i = 0; i < gridView.getChildCount(); i++) {
                rowHeight = Math.max(rowHeight, gridView.getChildAt(i).getHeight());
            }

            int extraSpace = rowHeight + gridView.getVerticalSpacing();

            if (rowHeight > 0 && extraSpace > gridView.getExtraLayoutSpace()) {
                // Another layout, so not in this one
                gridView.post(() -> gridView.setExtraLayoutSpace(extraSpace));
            }
        });
    }
}
