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
    // The card that opened each section but the last, to focus it again on Back
    private final List<Integer> mSelectedPositions = new ArrayList<>();

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
            mSelectedPositions.add(getView().getSelectedPosition());
            mStack.add(cards);
            updateView(0);
            return;
        }

        mStack.clear();
        mSelectedPositions.clear();
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
        updateView(mSelectedPositions.remove(mSelectedPositions.size() - 1));

        return true;
    }

    @Override
    public void onViewInitialized() {
        updateView(0);
    }

    private void updateView(int selectedPosition) {
        if (getView() == null || mStack.isEmpty()) {
            return;
        }

        Context context = getContext();
        Cards cards = mStack.get(mStack.size() - 1);
        List<SettingsItem> items = new ArrayList<>(cards.getItems(context));

        // Always alphabetical, in the language of the app
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.SECONDARY);
        Collections.sort(items, (item1, item2) -> collator.compare(item1.title, item2.title));

        // E.g. "Player - Options". The panels of its cards are titled "Options - <Card>".
        String cardsTitle = cards.getTitle(context);
        String title = mStack.size() > 1 ? String.format("%s - %s", mStack.get(mStack.size() - 2).getTitle(context), cardsTitle) : cardsTitle;

        getView().update(title, cardsTitle, items, selectedPosition);
    }
}
