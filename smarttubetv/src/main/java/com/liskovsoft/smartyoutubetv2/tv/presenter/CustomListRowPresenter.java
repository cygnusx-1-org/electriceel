package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridView;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class CustomListRowPresenter extends ListRowPresenter {
    private CardHold mHold;
    // The lists of the rows that lay out a row past their ends (see onRowViewAttachedToWindow)
    private final Set<VerticalGridView> mPreloadedLists = Collections.newSetFromMap(new WeakHashMap<>());

    public CustomListRowPresenter() {
        super(ViewUtil.FOCUS_ZOOM_FACTOR, ViewUtil.FOCUS_DIMMER_ENABLED);
        setSelectEffectEnabled(ViewUtil.ROW_SELECT_EFFECT_ENABLED);
    }

    /**
     * A new row shows its title together with its cards (see CardHold), and the cards and the rows next to the shown ones
     * are bound before they scroll in. Set before the rows are created.
     */
    public void enableImageHold(CardHold hold) {
        mHold = hold;
    }

    @Override
    protected void initializeRowViewHolder(RowPresenter.ViewHolder holder) {
        super.initializeRowViewHolder(holder);

        if (mHold != null) {
            ViewUtil.preloadNextItem(((ViewHolder) holder).getGridView());
        }
    }

    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder holder, Object item) {
        // Bound again in place (e.g. its cards changed): its title is shown, so it isn't hidden
        boolean isNewRow = holder.getRowObject() != item;

        super.onBindRowViewHolder(holder, item);

        if (mHold != null && isNewRow) {
            for (View titleView : getTitleViews(holder)) {
                mHold.holdWith(titleView);
            }
        }
    }

    @Override
    protected void onUnbindRowViewHolder(RowPresenter.ViewHolder holder) {
        if (mHold != null) {
            for (View titleView : getTitleViews(holder)) {
                mHold.drop(titleView);
            }
        }

        super.onUnbindRowViewHolder(holder);
    }

    @Override
    protected void onRowViewAttachedToWindow(RowPresenter.ViewHolder holder) {
        super.onRowViewAttachedToWindow(holder);

        VerticalGridView rowsList = mHold != null ? findRowsList(holder.view) : null;

        if (rowsList != null && mPreloadedLists.add(rowsList)) {
            ViewUtil.preloadNextItem(rowsList);
        }
    }

    /**
     * The title and the description of the row, not the header that holds them: leanback sets the alpha of the header
     * (the select level, see RowHeaderPresenter) and its visibility (the entrance transition, the expanded state, see RowPresenter)
     */
    private static View[] getTitleViews(RowPresenter.ViewHolder holder) {
        View header = holder.getHeaderViewHolder() != null ? holder.getHeaderViewHolder().view : null;

        if (!(header instanceof ViewGroup)) {
            return new View[0];
        }

        ViewGroup group = (ViewGroup) header;
        View[] views = new View[group.getChildCount()];

        for (int i = 0; i < views.length; i++) {
            views[i] = group.getChildAt(i);
        }

        return views;
    }

    private static VerticalGridView findRowsList(View rowView) {
        ViewParent parent = rowView.getParent();

        while (parent != null && !(parent instanceof VerticalGridView)) {
            parent = parent.getParent();
        }

        return (VerticalGridView) parent;
    }
}
