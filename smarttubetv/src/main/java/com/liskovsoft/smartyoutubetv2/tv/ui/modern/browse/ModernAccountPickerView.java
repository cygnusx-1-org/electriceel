package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.FocusFinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets.ModernRingFrame;

import java.util.List;

/**
 * Profile picker: one round tile per account, an "add account" tile and the guest button.
 */
public class ModernAccountPickerView extends FrameLayout {
    private HorizontalScrollView mScroll;
    private LinearLayout mAccounts;
    private View mGuest;
    private float mRingExtent;
    private Listener mListener;

    public interface Listener {
        void onAccountSelected(@Nullable Account account);
        void onAccountSettings();
        void onAddAccount();
    }

    public ModernAccountPickerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
    }

    public static ModernAccountPickerView inflate(ViewGroup parent) {
        return (ModernAccountPickerView) LayoutInflater.from(parent.getContext()).inflate(R.layout.modern_account_picker, parent, false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mScroll = findViewById(R.id.modern_picker_scroll);
        mAccounts = findViewById(R.id.modern_picker_accounts);
        mScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> invalidate());
        // How far the focus ring reaches out of a tile
        mRingExtent = getResources().getDimension(R.dimen.modern_focus_ring_inset) + getResources().getDimension(R.dimen.modern_focus_ring_width);
        mGuest = findViewById(R.id.modern_picker_guest);
        mGuest.setOnClickListener(v -> mListener.onAccountSelected(null));
    }

    /**
     * The picker covers the content: the keys move the focus among its own buttons only, never to the cards under it
     */
    @Override
    public View focusSearch(View focused, int direction) {
        View next = FocusFinder.getInstance().findNextFocus(this, focused, direction);
        return next != null ? next : focused;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setAccounts(List<Account> accounts) {
        mAccounts.removeAllViews();
        int gap = getResources().getDimensionPixelSize(R.dimen.modern_picker_gap);
        View focus = null;

        if (accounts != null) {
            for (Account account : accounts) {
                View tile = addTile(gap);
                bindAccount(tile, account);
                if (account.isSelected() || focus == null) {
                    focus = tile.findViewById(R.id.modern_account_avatar_frame);
                }
            }
        }

        View add = addTile(gap);
        bindAdd(add);

        if (focus == null) {
            focus = add.findViewById(R.id.modern_account_avatar_frame);
        }

        // Shown over the content, which has the focus: take it once laid out, so the keys never reach the cards under the picker
        View target = focus;
        target.requestFocus();
        post(() -> {
            if (!target.hasFocus()) {
                target.requestFocus();
            }
        });
    }

    private View addTile(int gap) {
        View tile = LayoutInflater.from(getContext()).inflate(R.layout.modern_account_tile, mAccounts, false);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) tile.getLayoutParams();
        if (mAccounts.getChildCount() > 0) {
            params.leftMargin = gap;
        }
        mAccounts.addView(tile, params);
        return tile;
    }

    private void bindAccount(View tile, Account account) {
        ModernRingFrame frame = tile.findViewById(R.id.modern_account_avatar_frame);
        ImageView avatar = tile.findViewById(R.id.modern_account_avatar);
        TextView name = tile.findViewById(R.id.modern_account_name);
        TextView handle = tile.findViewById(R.id.modern_account_handle);
        View settings = tile.findViewById(R.id.modern_account_settings);

        name.setText(account.getName() != null ? account.getName() : account.getEmail());
        handle.setText(account.getName() != null ? account.getEmail() : null);
        handle.setVisibility(handle.getText().length() > 0 ? View.VISIBLE : View.GONE);
        Glide.with(getContext()).load(account.getAvatarImageUrl()).transform(new CircleCrop()).into(avatar);

        frame.setOnClickListener(v -> mListener.onAccountSelected(account));
        settings.setOnClickListener(v -> mListener.onAccountSettings());

        OnFocusChangeListener focusListener = (v, hasFocus) -> updateTile(tile);
        frame.setExtraFocusListener(focusListener);
        settings.setOnFocusChangeListener(focusListener);
        updateTile(tile);
    }

    private void bindAdd(View tile) {
        ModernRingFrame frame = tile.findViewById(R.id.modern_account_avatar_frame);
        tile.findViewById(R.id.modern_account_add).setVisibility(View.VISIBLE);
        ((TextView) tile.findViewById(R.id.modern_account_name)).setText(R.string.modern_add_account);
        tile.findViewById(R.id.modern_account_handle).setVisibility(View.GONE);
        tile.findViewById(R.id.modern_account_settings).setVisibility(View.GONE);
        frame.setOnClickListener(v -> mListener.onAddAccount());
        frame.setExtraFocusListener((v, hasFocus) -> updateTile(tile));
        updateTile(tile);
    }

    /**
     * Focused account: primary name color and settings button below.
     */
    private void updateTile(View tile) {
        View frame = tile.findViewById(R.id.modern_account_avatar_frame);
        View settings = tile.findViewById(R.id.modern_account_settings);
        boolean active = frame.hasFocus() || settings.hasFocus();

        ((TextView) tile.findViewById(R.id.modern_account_name)).setTextColor(ContextCompat.getColor(getContext(),
                active ? R.color.modern_text_primary : R.color.modern_text_secondary));

        if (settings.getVisibility() != View.GONE) {
            settings.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        }

        mAccounts.invalidate();
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        // Focus ring of the round tiles, outside of their circular clip, where the scrolled row shows them
        canvas.save();
        canvas.clipRect(mScroll.getLeft(), mScroll.getTop() - mRingExtent, mScroll.getRight(), mScroll.getBottom() + mRingExtent);

        for (int i = 0; i < mAccounts.getChildCount(); i++) {
            ViewGroup tile = (ViewGroup) mAccounts.getChildAt(i);
            ModernRingFrame frame = tile.findViewById(R.id.modern_account_avatar_frame);
            canvas.save();
            canvas.translate(mScroll.getLeft() - mScroll.getScrollX() + mAccounts.getLeft() + tile.getLeft(),
                    mScroll.getTop() + mAccounts.getTop() + tile.getTop());
            frame.drawRing(canvas);
            canvas.restore();
        }

        canvas.restore();
    }
}
