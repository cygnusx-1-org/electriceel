package com.liskovsoft.smartyoutubetv2.tv.ui.modern.playback;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.ControlBar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Control bar of the modern player.<br/>
 * Transport bar (previous, play/pause, next) is laid out exactly centered on the row.<br/>
 * Secondary bar spans the whole row: left group buttons from the start, social and other groups from the end. Each side
 * is one pill, its buttons side by side, so the bar is symmetric around the transport bar. The social group goes to the
 * side that keeps the two pills closest in width (when it fits there). The label of the focused button is drawn below it.
 */
public class ModernControlBar extends ControlBar {
    private final Paint mPillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final int mButtonsHeight;
    private final int mCenterGap;
    private final int mLabelTop;
    private final int mTransportGap;
    private final int mButtonSize;
    private final int mCompactButtonSize;
    private final int mTransportWidth;
    private final boolean mLabelsEnabled;
    /**
     * The social group is laid out after the left group, in the start pill (else before the other group, in the end pill).
     */
    private boolean mSocialAtStart;

    public ModernControlBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ModernControlBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Resources res = getResources();
        mButtonsHeight = res.getDimensionPixelSize(R.dimen.modern_player_controls_height);
        mCenterGap = res.getDimensionPixelSize(R.dimen.modern_player_center_gap);
        mLabelTop = res.getDimensionPixelSize(R.dimen.modern_player_label_top);
        mTransportGap = res.getDimensionPixelSize(R.dimen.modern_player_transport_min_gap);
        mButtonSize = res.getDimensionPixelSize(R.dimen.modern_player_button);
        mCompactButtonSize = res.getDimensionPixelSize(R.dimen.modern_player_button_compact);
        // Player setting "Show button tooltips": the label under the focused button
        mLabelsEnabled = PlayerData.instance(context).isTooltipsEnabled();
        mTransportWidth = 2 * mButtonSize + res.getDimensionPixelSize(R.dimen.modern_player_play_button) + 2 * mCenterGap;

        mPillPaint.setColor(ContextCompat.getColor(context, R.color.modern_white_10));
        mLabelPaint.setColor(ContextCompat.getColor(context, R.color.modern_text_primary));
        mLabelPaint.setTextSize(res.getDimension(R.dimen.modern_player_pill_text));
        mLabelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mLabelPaint.setTextAlign(Paint.Align.CENTER);

        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
    }

    /**
     * Up from the buttons goes to the progress bar (the generic search would reach the hidden top edge and close the overlay).
     */
    @Override
    public View focusSearch(View focused, int direction) {
        if (direction == View.FOCUS_UP) {
            View current = this;
            while (current.getParent() instanceof View && current.getId() != R.id.transport_row) {
                current = (View) current.getParent();
            }
            View progress = current.findViewById(R.id.playback_progress);
            if (progress != null && progress.isFocusable() && progress.getVisibility() == View.VISIBLE) {
                return progress;
            }
        }

        return super.focusSearch(focused, direction);
    }

    private boolean isTransportBar() {
        return getParent() instanceof View && ((View) getParent()).getId() == R.id.controls_dock;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!isTransportBar()) {
            fitGroups(MeasureSpec.getSize(widthMeasureSpec));
        }

        int width = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            measureChild(child, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(mButtonsHeight, MeasureSpec.AT_MOST));
            width += child.getMeasuredWidth() + (i > 0 ? mCenterGap : 0);
        }

        if (!isTransportBar()) {
            width = MeasureSpec.getSize(widthMeasureSpec);
        }

        setMeasuredDimension(width, mButtonsHeight);
    }

    /**
     * Many enabled buttons: the buttons of both pills get closer (all the same size).
     * The transport bar stays exactly centered whenever one of these arrangements fits, the pills never overlap it.
     */
    private void fitGroups(int width) {
        for (boolean compact : new boolean[] {false, true}) {
            applyCompact(compact);

            if (fitsCentered(width)) {
                return;
            }
        }

        // No room for a centered transport bar: it moves to the middle of the free space (placeTransport)
        mSocialAtStart = false;
    }

    /**
     * @return true if the pills fit on both sides of the centered transport bar (sets where the social group goes: the
     * side that keeps the two pills closest in width, the end one when even)
     */
    private boolean fitsCentered(int width) {
        int start = 0;
        int social = 0;
        int other = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) {
                continue;
            }
            measureChild(child, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(mButtonsHeight, MeasureSpec.AT_MOST));
            int group = groupOf(child);
            if (group == ModernActionStyle.GROUP_LEFT) {
                start += child.getMeasuredWidth();
            } else if (group == ModernActionStyle.GROUP_SOCIAL) {
                social += child.getMeasuredWidth();
            } else {
                other += child.getMeasuredWidth();
            }
        }

        // Room on each side of the centered transport bar
        int side = (width - mTransportWidth) / 2 - mTransportGap;

        boolean fitsAtEnd = start <= side && social + other <= side;
        boolean fitsAtStart = social > 0 && start + social <= side && other <= side;

        if (fitsAtEnd && fitsAtStart) {
            mSocialAtStart = Math.abs(start + social - other) < Math.abs(start - social - other);
            return true;
        }

        if (fitsAtEnd || fitsAtStart) {
            mSocialAtStart = fitsAtStart;
            return true;
        }

        return false;
    }

    private void applyCompact(boolean compact) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            View button = child.findViewById(R.id.button);

            if (button == null) {
                continue;
            }

            ViewGroup.LayoutParams params = button.getLayoutParams();
            int targetWidth = compact ? mCompactButtonSize : mButtonSize;

            if (params.width != targetWidth) {
                params.width = targetWidth;
                button.setLayoutParams(params);
                // The focus circle as wide as the button, centered in the pill's height (an oval otherwise)
                int inset = Math.max(0, (params.height - targetWidth) / 2);
                Drawable background = ContextCompat.getDrawable(getContext(), R.drawable.modern_player_button_bg_grouped);
                button.setBackground(inset > 0 ? new InsetDrawable(background, 0, inset, 0, inset) : background);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (isTransportBar()) {
            layoutTransport();
        } else {
            layoutGroups(r - l);
        }
    }

    private void layoutTransport() {
        List<View> ordered = new ArrayList<>();

        for (int i = 0; i < getChildCount(); i++) {
            ordered.add(getChildAt(i));
        }

        // previous | play | next
        Collections.sort(ordered, (a, b) -> transportOrder(a) - transportOrder(b));

        int x = 0;

        for (View child : ordered) {
            layoutChild(child, x);
            x += child.getMeasuredWidth() + mCenterGap;
        }
    }

    private static int transportOrder(View child) {
        Object order = child.getTag(R.id.modern_transport_order);
        return order instanceof Integer ? (Integer) order : Integer.MAX_VALUE;
    }

    private void layoutGroups(int width) {
        int left = 0;
        List<View> start = new ArrayList<>();
        List<View> social = new ArrayList<>();
        List<View> other = new ArrayList<>();

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int group = groupOf(child);

            if (group == ModernActionStyle.GROUP_LEFT) {
                start.add(child);
            } else if (group == ModernActionStyle.GROUP_SOCIAL) {
                social.add(child);
            } else {
                other.add(child);
            }
        }

        // avatar | description | subscription, like the reference
        Collections.sort(start, (a, b) -> ModernActionStyle.startOrder(actionIdOf(a)) - ModernActionStyle.startOrder(actionIdOf(b)));

        // The start pill: the left group, then the social group when it is on this side, side by side
        List<View> startPill = new ArrayList<>(start);
        List<View> endPill = new ArrayList<>(other);

        if (mSocialAtStart) {
            startPill.addAll(social);
        } else {
            endPill.addAll(0, social);
        }

        for (View child : startPill) {
            if (child.getVisibility() == View.GONE) {
                continue;
            }
            layoutChild(child, left);
            left += child.getMeasuredWidth();
        }

        // The end pill: the social group when it is on this side, then the other group, side by side up to the end
        int right = width;

        for (int i = endPill.size() - 1; i >= 0; i--) {
            View child = endPill.get(i);
            if (child.getVisibility() == View.GONE) {
                continue;
            }
            right -= child.getMeasuredWidth();
            layoutChild(child, right);
        }

        placeTransport(left, right);
    }

    /**
     * The transport bar (separate dock, centered on the row) is moved to the middle of the free space between the
     * start group and the end groups when centered on the row it would come closer than the min gap to one of them.
     */
    private void placeTransport(int freeStart, int freeEnd) {
        View parent = getParent() instanceof View ? (View) getParent() : null;
        View row = parent != null && parent.getParent() instanceof View ? (View) parent.getParent() : null;
        View dock = row != null ? row.findViewById(R.id.controls_dock) : null;

        if (dock == null || parent.getLeft() != 0) {
            return;
        }

        int dockWidth = dock.getMeasuredWidth();
        int centered = (row.getWidth() - dockWidth) / 2;
        int min = freeStart + mTransportGap;
        int max = freeEnd - mTransportGap - dockWidth;
        int x;

        if (centered >= min && centered <= max) {
            x = centered;
        } else {
            // Centered on the row it would touch a group: centered in the free space, same distance from both groups
            x = freeStart + (freeEnd - freeStart - dockWidth) / 2;
        }

        dock.setTranslationX(x - centered);
    }

    private static int actionIdOf(View child) {
        Object id = child.getTag(R.id.modern_action_id);
        return id instanceof Integer ? (Integer) id : 0;
    }

    private void layoutChild(View child, int x) {
        int top = (mButtonsHeight - child.getMeasuredHeight()) / 2;
        child.layout(x, top, x + child.getMeasuredWidth(), top + child.getMeasuredHeight());
    }

    private static int groupOf(View child) {
        Object group = child.getTag(R.id.modern_action_group);
        return group instanceof Integer ? (Integer) group : ModernActionStyle.GROUP_OTHER;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!isTransportBar()) {
            drawPill(canvas, true);
            drawPill(canvas, false);
        }

        super.dispatchDraw(canvas);

        drawFocusedLabel(canvas);
    }

    private boolean isOnStartSide(View child) {
        int group = groupOf(child);
        return group == ModernActionStyle.GROUP_LEFT || (group == ModernActionStyle.GROUP_SOCIAL && mSocialAtStart);
    }

    private void drawPill(Canvas canvas, boolean startSide) {
        int start = Integer.MAX_VALUE;
        int end = Integer.MIN_VALUE;
        int top = 0;
        int bottom = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (isOnStartSide(child) == startSide && child.getVisibility() == View.VISIBLE) {
                start = Math.min(start, child.getLeft());
                end = Math.max(end, child.getRight());
                top = child.getTop();
                bottom = child.getBottom();
            }
        }

        if (start < end) {
            float radius = (bottom - top) / 2f;
            mRect.set(start, top, end, bottom);
            canvas.drawRoundRect(mRect, radius, radius, mPillPaint);
        }
    }

    private void drawFocusedLabel(Canvas canvas) {
        if (!mLabelsEnabled) {
            return;
        }

        View focused = getFocusedChild();

        if (focused == null || !focused.hasFocus()) {
            return;
        }

        Object label = focused.getTag(R.id.modern_action_label);

        if (!(label instanceof CharSequence)) {
            return;
        }

        float x = (focused.getLeft() + focused.getRight()) / 2f;
        float y = focused.getBottom() + mLabelTop - mLabelPaint.ascent();
        canvas.drawText(label.toString(), x, y, mLabelPaint);
    }
}
