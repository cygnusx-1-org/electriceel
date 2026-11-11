package com.liskovsoft.smartyoutubetv2.tv.ui.modern.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SettingsCardsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SettingsCardsView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernIcons;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernText;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * A settings section on its own screen, like the Settings page of the modern skin: the cards as a list on the start side, the
 * focused one on the end side. A card opens its panel, as in the classic skin (see SettingsCardsFragment).
 */
public class ModernSettingsCardsFragment extends Fragment implements SettingsCardsView {
    private SettingsCardsPresenter mPresenter;
    private final List<SettingsItem> mItems = new ArrayList<>();
    private TextView mTitle;
    private LinearLayout mList;
    private View mPreview;
    private ImageView mPreviewIcon;
    private TextView mPreviewTitle;
    private String mCardsTitle;
    private int mSelectedPosition;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPresenter = SettingsCardsPresenter.instance(getContext());
        mPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FrameLayout root = new FrameLayout(requireContext());
        inflater.inflate(R.layout.modern_settings_page, root, true);
        mTitle = root.findViewById(R.id.modern_settings_title);
        mList = root.findViewById(R.id.modern_settings_list);
        mPreview = root.findViewById(R.id.modern_settings_preview);
        mPreviewIcon = root.findViewById(R.id.modern_settings_preview_icon);
        mPreviewTitle = root.findViewById(R.id.modern_settings_preview_title);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPresenter.onViewInitialized();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        mPresenter.onViewSaved(getSelectedPosition());
    }

    @Override
    public void update(String title, String cardsTitle, List<SettingsItem> items, int selectedPosition) {
        if (mList == null) {
            return;
        }

        mCardsTitle = cardsTitle;
        mTitle.setText(title);
        mItems.clear();
        mItems.addAll(items);
        mList.removeAllViews();

        for (SettingsItem item : mItems) {
            mList.addView(createRow(item));
        }

        mSelectedPosition = Math.max(0, Math.min(selectedPosition, mItems.size() - 1));

        if (mList.getChildCount() > 0) {
            mList.getChildAt(mSelectedPosition).requestFocus();
            showPreview(mItems.get(mSelectedPosition));
        }
    }

    @Override
    public int getSelectedPosition() {
        return mSelectedPosition;
    }

    @Override
    public void finish() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    /**
     * The activity of another skin takes the place of this one (see ModernSettingsCardsActivity.onReplaced)
     */
    public void saveSelectedPosition() {
        mPresenter.onViewSaved(getSelectedPosition());
    }

    /**
     * @return went back to the section before, the screen stays
     */
    public boolean goBack() {
        return mPresenter.goBack();
    }

    private View createRow(SettingsItem item) {
        View row = LayoutInflater.from(getContext()).inflate(R.layout.modern_settings_row, mList, false);
        TextView text = row.findViewById(R.id.modern_settings_row_text);
        text.setText(item.title);
        row.setOnClickListener(v -> open(item));
        row.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                mSelectedPosition = mList.indexOfChild(v);
                showPreview(item);
            }
        });
        return row;
    }

    /**
     * As the classic cards: the panel of the card is titled "<Section> - <Card>", the lists inside it "<Parent> - <List>"
     */
    private void open(SettingsItem item) {
        if (item.onClick == null) {
            return;
        }

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());
        dialogPresenter.setTitlePrefix(mCardsTitle);
        dialogPresenter.enableNestedTitles(true);
        try {
            item.onClick.run();
        } finally {
            // Only the panel opened right away
            dialogPresenter.setTitlePrefix(null);
            dialogPresenter.enableNestedTitles(false);
        }
    }

    private void showPreview(SettingsItem item) {
        mPreviewTitle.setText(ModernText.sentenceCase(item.title));
        int icon = ModernIcons.forSettings(item.imageResId);

        if (icon == 0) {
            icon = item.imageResId;
        }

        if (icon > 0) {
            mPreviewIcon.setImageResource(icon);
            mPreviewIcon.setVisibility(View.VISIBLE);
        } else {
            mPreviewIcon.setVisibility(View.GONE);
        }

        mPreview.setAlpha(0.6f);
        mPreview.animate().alpha(1f).setDuration(ModernTheme.ANIM_FAST_MS);
    }
}
