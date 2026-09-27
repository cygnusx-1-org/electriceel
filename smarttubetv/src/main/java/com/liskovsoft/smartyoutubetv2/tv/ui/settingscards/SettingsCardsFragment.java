package com.liskovsoft.smartyoutubetv2.tv.ui.settingscards;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

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

        mCardsAdapter = new ArrayObjectAdapter(new SettingsCardPresenter());
        setAdapter(mCardsAdapter);

        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof SettingsItem) {
                    // The panel of the card is titled "<Section> - <Card>", e.g. "Player - Video". The card stays "Video".
                    // The lists inside it "<Parent> - <List>", e.g. "General - Playback mode".
                    AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());
                    dialogPresenter.setTitlePrefix(mTitle);
                    dialogPresenter.enableNestedTitles(true);
                    try {
                        ((SettingsItem) item).onClick.run();
                    } finally {
                        // Only the panel opened right away
                        dialogPresenter.setTitlePrefix(null);
                        dialogPresenter.enableNestedTitles(false);
                    }
                }
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        GridFragmentHelper.enableDownToShorterRow(getBrowseGrid(), getGridPresenter().getNumberOfColumns());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mPresenter.onViewInitialized();
    }

    @Override
    public void update(String title, String cardsTitle, List<SettingsItem> items, int selectedPosition) {
        mTitle = cardsTitle;

        // The title is above the fragment, in the activity layout
        Activity activity = getActivity();
        TextView titleView = activity != null ? activity.findViewById(R.id.settings_cards_title) : null;

        if (titleView != null) {
            titleView.setText(title);
        }

        mCardsAdapter.clear();

        for (SettingsItem item : items) {
            mCardsAdapter.add(item);
        }

        // getSelectedPosition() of the grid tells the card back
        setSelectedPosition(Math.max(0, Math.min(selectedPosition, items.size() - 1)));
    }

    /**
     * @return went back to the section before, the screen stays
     */
    public boolean goBack() {
        return mPresenter.goBack();
    }
}
