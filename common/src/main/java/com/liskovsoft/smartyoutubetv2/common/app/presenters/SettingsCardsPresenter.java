package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SettingsCardsView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A settings section on its own screen: a list of cards instead of the settings panel (e.g. Content Filtering, Player).
 * A card opens the settings panel of its part, or the cards of a part on the same screen (e.g. Player - Options).
 */
public class SettingsCardsPresenter extends BasePresenter<SettingsCardsView> {
    @SuppressLint("StaticFieldLeak")
    private static SettingsCardsPresenter sInstance;
    // The sections open on the screen, the last one is shown. Back goes to the one before.
    private final List<Cards> mStack = new ArrayList<>();
    // The card that opened each section but the last, to focus it again on Back. A card is its index in the cards of its section:
    // the screen shows them sorted in the language of the app, which can change meanwhile (see LocaleData).
    private final List<Integer> mSelectedCards = new ArrayList<>();
    // The cards shown, in their order on the screen
    private final List<Integer> mShownCards = new ArrayList<>();
    // The card focused when the screen was saved, to focus it again when it's recreated (see onViewSaved)
    private int mSavedCard = -1;

    /**
     * The cards of a section. They're made when the screen opens, with its context, so their settings open over it.
     */
    public interface Cards {
        String getTitle(Context context);
        List<SettingsItem> getItems(Context context);
    }

    private SettingsCardsPresenter(Context context) {
        super(context);
    }

    public static SettingsCardsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new SettingsCardsPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    /**
     * From the screen itself: on top of the section shown. From elsewhere (e.g. the Settings section): a new screen.
     */
    public void show(Cards cards) {
        if (getView() != null) {
            mSelectedCards.add(getShownCard(getView().getSelectedPosition()));
            mStack.add(cards);
            updateView(-1);
            return;
        }

        mStack.clear();
        mSelectedCards.clear();
        mSavedCard = -1;
        mStack.add(cards);

        getViewManager().startView(SettingsCardsView.class);
    }

    /**
     * @return went back to the section before, the screen stays
     */
    public boolean goBack() {
        if (mStack.size() <= 1) {
            return false;
        }

        mStack.remove(mStack.size() - 1);
        updateView(mSelectedCards.remove(mSelectedCards.size() - 1));

        return true;
    }

    @Override
    public void onViewInitialized() {
        // Restored after the app was killed (e.g. an ANR): the sections were only in memory, the screen would stay black
        if (mStack.isEmpty()) {
            if (getView() != null) {
                getView().finish();
            }
            return;
        }

        int savedCard = mSavedCard;
        mSavedCard = -1;
        updateView(savedCard);
    }

    /**
     * The screen is saved, e.g. before it's recreated in another language (see MotherActivity)
     */
    public void onViewSaved(int selectedPosition) {
        mSavedCard = getShownCard(selectedPosition);
    }

    /**
     * @return the index of the card in the cards of its section, -1 when there's none at the position
     */
    private int getShownCard(int position) {
        return position >= 0 && position < mShownCards.size() ? mShownCards.get(position) : -1;
    }

    /**
     * @param selectedCard the index of the card to focus in the cards of the section, -1 for the first one shown
     */
    private void updateView(int selectedCard) {
        if (getView() == null || mStack.isEmpty()) {
            return;
        }

        Context context = getContext();
        Cards cards = mStack.get(mStack.size() - 1);
        List<SettingsItem> allItems = cards.getItems(context);
        List<Integer> shownCards = new ArrayList<>();

        for (int i = 0; i < allItems.size(); i++) {
            shownCards.add(i);
        }

        // Always alphabetical, in the language of the app
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.SECONDARY);
        Collections.sort(shownCards, (card1, card2) -> collator.compare(allItems.get(card1).title, allItems.get(card2).title));

        List<SettingsItem> items = new ArrayList<>();

        for (int card : shownCards) {
            items.add(allItems.get(card));
        }

        mShownCards.clear();
        mShownCards.addAll(shownCards);
        int selectedPosition = Math.max(0, shownCards.indexOf(selectedCard));

        // E.g. "Player - Options". The panels of its cards are titled "Options - <Card>".
        String cardsTitle = cards.getTitle(context);
        String title = mStack.size() > 1 ? String.format("%s - %s", mStack.get(mStack.size() - 2).getTitle(context), cardsTitle) : cardsTitle;

        getView().update(title, cardsTitle, items, selectedPosition);
    }
}
