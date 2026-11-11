package com.liskovsoft.smartyoutubetv2.tv.ui.modern.playback;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Root of the modern controls row: the title and metadata card (top start) spans the row up to the quality and date info
 * (top end), so the title and the metadata line each fit on one line.
 */
public class ModernTransportRowLayout extends FrameLayout {
    private final int mInfoGap;

    public ModernTransportRowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mInfoGap = getResources().getDimensionPixelSize(R.dimen.modern_player_title_info_gap);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        View card = findViewById(R.id.controls_card);

        if (card == null || card.getVisibility() == View.GONE || !(card.getLayoutParams() instanceof MarginLayoutParams)) {
            return;
        }

        MarginLayoutParams cardParams = (MarginLayoutParams) card.getLayoutParams();
        int cardMargin = cardParams.getMarginStart();
        // Without the info: as far from the end as from the start
        int end = cardMargin;
        View info = findViewById(R.id.additional_info);

        if (info != null && info.getVisibility() != View.GONE && info instanceof ViewGroup
                && info.getLayoutParams() instanceof MarginLayoutParams) {
            // The info box spans the row, its lines are aligned to its end: the widest line is how far it reaches
            ViewGroup infoGroup = (ViewGroup) info;
            int infoWidth = 0;

            for (int i = 0; i < infoGroup.getChildCount(); i++) {
                View child = infoGroup.getChildAt(i);
                if (child.getVisibility() != View.GONE) {
                    infoWidth = Math.max(infoWidth, child.getMeasuredWidth());
                }
            }

            if (infoWidth > 0) {
                end = ((MarginLayoutParams) info.getLayoutParams()).getMarginEnd() + infoWidth + mInfoGap;
            }
        }

        int width = Math.max(0, getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - cardMargin - end);
        int heightSpec = getChildMeasureSpec(heightMeasureSpec,
                getPaddingTop() + getPaddingBottom() + cardParams.topMargin + cardParams.bottomMargin, cardParams.height);
        card.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightSpec);
    }
}
