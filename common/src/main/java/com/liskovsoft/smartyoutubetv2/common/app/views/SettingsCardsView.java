package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;

import java.util.List;

/**
 * A settings section on its own screen, one list of cards (see SettingsCardsPresenter)
 */
public interface SettingsCardsView {
    /**
     * @param title of the screen, e.g. "Player - Options"
     * @param cardsTitle of the section, the start of its panel titles (e.g. "Options - Seek options")
     * @param selectedPosition the card to focus
     */
    void update(String title, String cardsTitle, List<SettingsItem> items, int selectedPosition);
    int getSelectedPosition();
}
