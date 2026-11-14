package com.liskovsoft.smartyoutubetv2.tv.ui.settingscards;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SettingsCardsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.SettingsLinksPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SettingsCardsView;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.SettingsCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.GridFragmentHelper;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.fragments.GridFragment;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.List;

/**
 * A settings section on its own screen: the Settings cards under the section title, in rows like the Settings section
 */
public class SettingsCardsFragment extends GridFragment implements SettingsCardsView {
    private SettingsCardsPresenter mPresenter;
    private ArrayObjectAdapter mCardsAdapter;
    private String mTitle;
    private SettingsCardsLayout mCardsLayout;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPresenter = SettingsCardsPresenter.instance(getContext());
        mPresenter.setView(this);

        // The same grid as the Settings section (see SettingsGridFragment)
        VerticalGridPresenter gridPresenter = new VerticalGridPresenter(ViewUtil.FOCUS_ZOOM_FACTOR, ViewUtil.FOCUS_DIMMER_ENABLED);
        gridPresenter.enableChildRoundedCorners(MainUIData.instance(getContext()).isUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS));
        gridPresenter.setNumberOfColumns(GridFragmentHelper.getMaxColsNum(getContext(), R.dimen.settings_card_width));
        setGridPresenter(gridPresenter);

        SettingsCardPresenter cardPresenter = new SettingsCardPresenter();
        // The addresses of the card as QR codes (see SettingsLinksPresenter), in a panel titled as the one the click opens
        cardPresenter.setOnCardLongPressedListener(item -> !item.getLinks().isEmpty()
                && openWithTitles(() -> SettingsLinksPresenter.instance(getContext()).show(item)));
        mCardsAdapter = new ArrayObjectAdapter(cardPresenter);
        setAdapter(mCardsAdapter);

        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof SettingsItem) {
                    openWithTitles(((SettingsItem) item).onClick);
                }
            }
        });

        setOnItemViewSelectedListener((itemViewHolder, item, rowViewHolder, row) -> {
            if (mCardsLayout != null) {
                mCardsLayout.setDescription(item);
            }
        });
    }

    /**
     * The panel of the card is titled "<Section> - <Card>", e.g. "Player - Video". The card stays "Video".
     * The lists inside it "<Parent> - <List>", e.g. "General - Playback mode".
     *
     * @return true, for the long press: it was used
     */
    private boolean openWithTitles(Runnable open) {
        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());
        dialogPresenter.setTitlePrefix(mTitle);
        dialogPresenter.enableNestedTitles(true);
        try {
            open.run();
        } finally {
            // Only the panel opened right away
            dialogPresenter.setTitlePrefix(null);
            dialogPresenter.enableNestedTitles(false);
        }

        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return SettingsCardsLayout.wrap(inflater, container, super.onCreateView(inflater, container, savedInstanceState));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        GridFragmentHelper.enableDownToShorterRow(getBrowseGrid(), getGridPresenter().getNumberOfColumns());
        // The presenter focuses the card again when the screen is recreated (see SettingsCardsPresenter.onViewSaved). The grid would
        // restore the position it had, where another card can be: the cards are sorted in the language of the app, which can change.
        getBrowseGrid().setSaveEnabled(false);
        mCardsLayout = SettingsCardsLayout.attach(view, getBrowseGrid());
    }

    @Override
    public void onDestroyView() {
        if (mCardsLayout != null) {
            mCardsLayout.detach();
            mCardsLayout = null;
        }

        super.onDestroyView();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mPresenter.onViewInitialized();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        mPresenter.onViewSaved(getSelectedPosition());
    }

    @Override
    public void update(String title, String cardsTitle, List<SettingsItem> items, int selectedPosition) {
        mTitle = cardsTitle;

        if (mCardsLayout != null) {
            mCardsLayout.setTitle(title);
        }

        mCardsAdapter.clear();

        for (SettingsItem item : items) {
            mCardsAdapter.add(item);
        }

        // getSelectedPosition() of the grid tells the card back
        int position = Math.max(0, Math.min(selectedPosition, items.size() - 1));
        setSelectedPosition(position);

        // The grid tells the selection after its layout, if the card at the position changed at all
        if (mCardsLayout != null) {
            mCardsLayout.setDescription(items.isEmpty() ? null : items.get(position));
        }
    }

    @Override
    public void finish() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    /**
     * The activity of another skin takes the place of this one (see SettingsCardsActivity.onReplaced)
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
}
