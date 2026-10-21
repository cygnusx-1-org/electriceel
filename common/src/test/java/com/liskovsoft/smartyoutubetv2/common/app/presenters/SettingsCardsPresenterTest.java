package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.app.Activity;
import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.views.SettingsCardsView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SettingsCardsPresenterTest {
    private SettingsCardsPresenter mPresenter;
    private FakeView mView;

    @Before
    public void setUp() {
        mView = Robolectric.buildActivity(FakeView.class).setup().get();
        mPresenter = SettingsCardsPresenter.instance(mView);
        mPresenter.setView(mView);
    }

    /**
     * Recreated in another language: the cards are sorted in it, the focused one stays focused
     */
    @Test
    public void focusedCardKeptInAnotherLanguage() {
        TestCards cards = new TestCards("Country", "Language", "Home");
        mPresenter.show(cards); // Country, Home, Language
        mView.select("Language");

        mPresenter.onViewSaved(mView.getSelectedPosition());
        cards.setTitles("Land", "Sprache", "Start");
        mPresenter.onViewInitialized(); // Land, Sprache, Start

        assertEquals("Sprache", mView.getSelectedTitle());
    }

    /**
     * Back after the language changed in a section opened from a card: the card that opened it is focused again
     */
    @Test
    public void openingCardFocusedOnBackInAnotherLanguage() {
        TestCards cards = new TestCards("Country", "Language", "Home");
        mPresenter.show(cards);
        mView.select("Language");
        mPresenter.show(new TestCards("Options"));

        cards.setTitles("Land", "Sprache", "Start");
        mPresenter.goBack();

        assertEquals("Sprache", mView.getSelectedTitle());
    }

    /**
     * Recreated in the same language: as before
     */
    @Test
    public void focusedCardKeptInTheSameLanguage() {
        mPresenter.show(new TestCards("Country", "Language", "Home"));
        mView.select("Home");

        mPresenter.onViewSaved(mView.getSelectedPosition());
        mPresenter.onViewInitialized();

        assertEquals("Home", mView.getSelectedTitle());
    }

    private static class TestCards implements SettingsCardsPresenter.Cards {
        private String[] mTitles;

        private TestCards(String... titles) {
            mTitles = titles;
        }

        private void setTitles(String... titles) {
            mTitles = titles;
        }

        @Override
        public String getTitle(Context context) {
            return "Test";
        }

        @Override
        public List<SettingsItem> getItems(Context context) {
            List<SettingsItem> items = new ArrayList<>();

            for (String title : mTitles) {
                items.add(new SettingsItem(title, () -> {}));
            }

            return items;
        }
    }

    public static class FakeView extends Activity implements SettingsCardsView {
        private List<SettingsItem> mItems = new ArrayList<>();
        private int mSelectedPosition;

        @Override
        public void update(String title, String cardsTitle, List<SettingsItem> items, int selectedPosition) {
            mItems = items;
            mSelectedPosition = selectedPosition;
        }

        @Override
        public int getSelectedPosition() {
            return mSelectedPosition;
        }

        private void select(String title) {
            for (int i = 0; i < mItems.size(); i++) {
                if (mItems.get(i).title.equals(title)) {
                    mSelectedPosition = i;
                    return;
                }
            }

            throw new IllegalArgumentException(title);
        }

        private String getSelectedTitle() {
            return mItems.get(mSelectedPosition).title;
        }
    }
}
