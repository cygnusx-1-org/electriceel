package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.endscreen;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.FocusFinder;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.data.EndScreenItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.EndScreenPlacement;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The end screen cards over the video (see EndScreenController), where the creator placed them on the video's frame.<br/>
 * They take no focus until {@link #enter()} (D-pad up while they show): then the arrows move between them, OK opens one and back
 * (or down from the lowest one) leaves them, while the video plays on.<br/>
 * A channel is round, with its name under it. The others show their title over the bottom of the image, and a badge (the length of a
 * video, the size of a playlist, the site of a link) in the top corner.
 */
public class EndScreenView extends FrameLayout {
    private static final int TITLE_SCRIM_COLOR = 0xCC000000;
    private static final int BADGE_COLOR = 0xCC000000;
    private static final int PLACEHOLDER_COLOR = 0x80000000;
    private static final int LABEL_SHADOW_COLOR = Color.BLACK;
    private final Map<EndScreenItem, Card> mCards = new HashMap<>();
    private final List<EndScreenItem> mItems = new ArrayList<>();
    private final Rect mFrame = new Rect();
    private final int mCornerRadius;
    private final int mFocusStroke;
    private final int mPadding;
    private final int mLabelGap;
    // Laid out again after the frame, not inside its layout
    private final OnLayoutChangeListener mFrameChanged = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> post(this::requestLayout);
    @Nullable
    private View mFrameView;
    @Nullable
    private Listener mListener;
    @Nullable
    private View mFocusBefore;

    public interface Listener {
        void onCardClicked(EndScreenItem item);
    }

    /**
     * The views of a card: the card itself and, for a channel, its name under it
     */
    private static final class Card {
        final View view;
        @Nullable
        final View label;

        Card(View view, @Nullable View label) {
            this.view = view;
            this.label = label;
        }
    }

    public EndScreenView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mCornerRadius = getResources().getDimensionPixelSize(R.dimen.end_screen_corner_radius);
        mFocusStroke = getResources().getDimensionPixelSize(R.dimen.end_screen_focus_stroke);
        mPadding = getResources().getDimensionPixelSize(R.dimen.end_screen_padding);
        mLabelGap = getResources().getDimensionPixelSize(R.dimen.end_screen_label_gap);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setClipChildren(false);
    }

    /**
     * The view the video is drawn in: the cards are placed on it
     */
    public void setFrameView(@Nullable View frameView) {
        if (mFrameView != null) {
            mFrameView.removeOnLayoutChangeListener(mFrameChanged);
        }

        mFrameView = frameView;

        if (mFrameView != null) {
            mFrameView.addOnLayoutChangeListener(mFrameChanged);
        }

        requestLayout();
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /**
     * The cards to show now. The ones already shown stay as they are (their images aren't loaded again). Null or empty: none.
     */
    public void setItems(@Nullable List<EndScreenItem> items) {
        List<EndScreenItem> newItems = items != null ? items : new ArrayList<>();
        View focus = findFocus();
        EndScreenItem focusedItem = focus != null && focus.getTag() instanceof EndScreenItem ? (EndScreenItem) focus.getTag() : null;

        for (EndScreenItem item : new ArrayList<>(mCards.keySet())) {
            if (!newItems.contains(item)) {
                Card card = mCards.remove(item);

                if (card != null) {
                    removeCard(card);
                }
            }
        }

        mItems.clear();
        mItems.addAll(newItems);

        for (EndScreenItem item : mItems) {
            if (!mCards.containsKey(item)) {
                mCards.put(item, addCard(item));
            }
        }

        // The focused card went: the focus moves to another one, or leaves the cards
        if (isActive() && (focusedItem == null || !mCards.containsKey(focusedItem))) {
            View first = findFirstCard();

            if (first != null) {
                first.requestFocus();
            } else {
                exit();
            }
        }

        requestLayout();
    }

    public boolean hasCards() {
        return !mItems.isEmpty() && getVisibility() == VISIBLE;
    }

    /**
     * The cards have the focus (see {@link #enter()})
     */
    public boolean isActive() {
        return getDescendantFocusability() != FOCUS_BLOCK_DESCENDANTS;
    }

    /**
     * The focus moves to the first card: the one at the top left
     *
     * @return whether a card got it
     */
    public boolean enter() {
        View first = findFirstCard();

        if (first == null) {
            return false;
        }

        mFocusBefore = getRootView().findFocus();
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);

        if (!first.requestFocus()) {
            setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            mFocusBefore = null;
            return false;
        }

        return true;
    }

    /**
     * The focus goes back to where it was before {@link #enter()}
     */
    public void exit() {
        if (!isActive()) {
            return;
        }

        View before = mFocusBefore;
        mFocusBefore = null;
        boolean hadFocus = hasFocus();
        boolean isRestored = hadFocus && before != null && before.isAttachedToWindow() && before.requestFocus();

        // Drops the card's focus when it wasn't moved back
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);

        if (hadFocus && !isRestored) {
            // The cards can't take it now: the first view of the window outside them does
            getRootView().requestFocus();
        }
    }

    /**
     * A key pressed while the cards have the focus: the arrows move between them, OK opens one, back (or down from the lowest one)
     * leaves them
     *
     * @return true if handled, false for a key the player handles (e.g. a media key)
     */
    public boolean onActiveKeyDown(KeyEvent event) {
        int direction = toFocusDirection(event.getKeyCode());

        if (direction != 0) {
            View focus = findFocus();
            View next = focus != null ? FocusFinder.getInstance().findNextFocus(this, focus, direction) : findFirstCard();

            if (next != null) {
                next.requestFocus(direction);
            } else if (direction == View.FOCUS_DOWN) {
                // Up went in: down goes out
                exit();
            }

            return true;
        }

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (event.getRepeatCount() == 0) {
                    View focus = findFocus();
                    EndScreenItem item = focus != null && focus.getTag() instanceof EndScreenItem ? (EndScreenItem) focus.getTag() : null;
                    exit();

                    if (item != null && mListener != null) {
                        mListener.onCardClicked(item);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                exit();
                return true;
            default:
                return false;
        }
    }

    private static int toFocusDirection(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return View.FOCUS_RIGHT;
            default:
                return 0;
        }
    }

    @Nullable
    private View findFirstCard() {
        View result = null;
        int[] best = null;

        for (EndScreenItem item : mItems) {
            Card card = mCards.get(item);

            if (card == null) {
                continue;
            }

            int[] place = placeOnFrame(item);

            if (best == null || place[1] < best[1] || (place[1] == best[1] && place[0] < best[0])) {
                best = place;
                result = card.view;
            }
        }

        return result;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
        updateFrame();

        for (EndScreenItem item : mItems) {
            Card card = mCards.get(item);

            if (card == null) {
                continue;
            }

            int[] place = placeOnFrame(item);
            int width = place[2] - place[0];
            card.view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(place[3] - place[1], MeasureSpec.EXACTLY));

            if (card.label != null) {
                // Wider than the circle: a name a bit longer than it fits on one line
                card.label.measure(MeasureSpec.makeMeasureSpec(width + width / 2, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (EndScreenItem item : mItems) {
            Card card = mCards.get(item);

            if (card == null) {
                continue;
            }

            int[] place = placeOnFrame(item);
            card.view.layout(place[0], place[1], place[2], place[3]);

            if (card.label != null) {
                int labelLeft = (place[0] + place[2] - card.label.getMeasuredWidth()) / 2;
                int labelTop = place[3] + mLabelGap;
                card.label.layout(labelLeft, labelTop, labelLeft + card.label.getMeasuredWidth(), labelTop + card.label.getMeasuredHeight());
            }
        }
    }

    /**
     * The video's frame as it's shown (scaled, moved next to a dialog), in this view's coordinates. The whole view before the video
     * has a size.
     */
    private void updateFrame() {
        if (mFrameView != null && mFrameView.getWidth() > 0 && mFrameView.getHeight() > 0 && mFrameView.getParent() == getParent()) {
            mFrameView.getHitRect(mFrame);
            mFrame.offset(-getLeft(), -getTop());
        } else {
            mFrame.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        }
    }

    private int[] placeOnFrame(EndScreenItem item) {
        return EndScreenPlacement.place(item, mFrame.left, mFrame.top, mFrame.width(), mFrame.height());
    }

    private Card addCard(EndScreenItem item) {
        Context context = getContext();
        boolean isRound = item.getType() == EndScreenItem.TYPE_CHANNEL;

        FrameLayout view = new FrameLayout(context);
        view.setTag(item);
        view.setFocusable(true);
        view.setContentDescription(item.getTitle());
        view.setBackground(new ColorDrawable(PLACEHOLDER_COLOR));
        view.setForeground(createFocusRing(isRound));
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                if (isRound) {
                    outline.setOval(0, 0, v.getWidth(), v.getHeight());
                } else {
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), mCornerRadius);
                }
            }
        });
        view.setClipToOutline(true);
        view.setOnClickListener(v -> {
            exit();

            if (mListener != null) {
                mListener.onCardClicked(item);
            }
        });

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        Glide.with(context).load(item.getImageUrl()).into(image);

        TextView label = null;

        if (isRound) {
            label = createText(R.dimen.end_screen_title_text_size);
            label.setText(item.getTitle());
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setShadowLayer(3, 1, 1, LABEL_SHADOW_COLOR);
        } else {
            TextView title = createText(R.dimen.end_screen_title_text_size);
            title.setText(item.getTitle());
            title.setMaxLines(2);
            title.setPadding(mPadding, mPadding * 2, mPadding, mPadding);
            title.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[] {TITLE_SCRIM_COLOR, Color.TRANSPARENT}));
            view.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

            String badgeText = item.getType() == EndScreenItem.TYPE_LINK ? item.getMetadata() : item.getBadge();

            if (!TextUtils.isEmpty(badgeText)) {
                int badgePadding = getResources().getDimensionPixelSize(R.dimen.end_screen_badge_padding);
                TextView badge = createText(R.dimen.end_screen_badge_text_size);
                badge.setText(badgeText);
                badge.setPadding(badgePadding * 2, badgePadding, badgePadding * 2, badgePadding);
                GradientDrawable background = new GradientDrawable();
                background.setColor(BADGE_COLOR);
                background.setCornerRadius(mCornerRadius);
                badge.setBackground(background);
                LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
                params.setMargins(mPadding, mPadding, mPadding, mPadding);
                view.addView(badge, params);
            }
        }

        addView(view);

        if (label != null) {
            addView(label);
        }

        return new Card(view, label);
    }

    private void removeCard(Card card) {
        if (card.view.hasFocus()) {
            // Not to the next view in the window: setItems moves it to another card or out of the cards
            card.view.clearFocus();
        }

        for (int i = 0; i < ((ViewGroup) card.view).getChildCount(); i++) {
            View child = ((ViewGroup) card.view).getChildAt(i);

            if (child instanceof ImageView) {
                Glide.with(getContext()).clear(child);
            }
        }

        removeView(card.view);

        if (card.label != null) {
            removeView(card.label);
        }
    }

    private TextView createText(int textSizeResId) {
        TextView text = new TextView(getContext());
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(textSizeResId));
        text.setMaxLines(1);
        text.setEllipsize(TextUtils.TruncateAt.END);
        return text;
    }

    private Drawable createFocusRing(boolean isRound) {
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(isRound ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
        ring.setStroke(mFocusStroke, Color.WHITE);
        ring.setColor(Color.TRANSPARENT);

        if (!isRound) {
            ring.setCornerRadius(mCornerRadius);
        }

        StateListDrawable result = new StateListDrawable();
        result.addState(new int[] {android.R.attr.state_focused}, ring);
        result.addState(new int[] {}, new ColorDrawable(Color.TRANSPARENT));
        return result;
    }
}
