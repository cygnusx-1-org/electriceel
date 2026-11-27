package com.liskovsoft.smartyoutubetv2.common.settingssearch;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;

import java.util.Collections;
import java.util.List;

/**
 * A setting the settings search finds: a card, or a row of a settings panel (a switch, a list of choices, a checkbox of a list, a
 * button). In the language of the app, as it's shown.
 */
public final class SettingEntry {
    /**
     * A card that opens more cards (e.g. Player)
     */
    public static final int KIND_SECTION = 0;
    /**
     * A card that opens a settings panel (e.g. Player - Video)
     */
    public static final int KIND_CARD = 1;
    /**
     * A list of choices, one of them picked (e.g. Video buffer)
     */
    public static final int KIND_CHOICE = 2;
    /**
     * A switch, or a checkbox of a list: on or off
     */
    public static final int KIND_TOGGLE = 3;
    /**
     * A row that does something or opens something else (e.g. Video speed, Check for updates)
     */
    public static final int KIND_BUTTON = 4;

    public final String id;
    public final int kind;
    public final String title;
    /**
     * Where it is: the titles of the cards that lead to it, then of the list it's in (e.g. Player, General, Player buttons)
     */
    public final List<String> path;
    @Nullable
    public final String description;
    /**
     * Of a list of choices, empty for the others
     */
    public final List<String> choices;
    /**
     * Of a list of choices: the title of the one picked, null when none is
     */
    @Nullable
    public final String picked;
    /**
     * Of a switch or a checkbox
     */
    public final boolean isOn;

    /**
     * The index of each card that leads to it, from the cards of the Settings section down (see SettingsCatalog.open). As many as
     * there are cards at the start of {@link #path}.
     */
    final int[] cards;
    /**
     * Of a row of a panel, the title of its list (null for a switch or a button of its own), and its own title (null for a list of
     * choices: its row). Matched against the panel shown, to give its row the focus.
     */
    @Nullable
    final String categoryTitle;
    @Nullable
    final String itemTitle;
    final boolean isRow;
    /**
     * Of a row of a menu opened by a row of the panel (e.g. Hide shorts everywhere, in the menu of Shorts in Hide content): the
     * list of that row and its title. Null for a row of the panel itself.
     */
    @Nullable
    final String menuCategoryTitle;
    @Nullable
    final String menuItemTitle;

    SettingEntry(String id, int kind, String title, List<String> path, @Nullable String description, List<String> choices,
                 @Nullable String picked, boolean isOn, int[] cards, boolean isRow, @Nullable String categoryTitle,
                 @Nullable String itemTitle, @Nullable String menuCategoryTitle, @Nullable String menuItemTitle) {
        this.id = id;
        this.kind = kind;
        this.title = title;
        this.path = Collections.unmodifiableList(path);
        this.description = description;
        this.choices = Collections.unmodifiableList(choices);
        this.picked = picked;
        this.isOn = isOn;
        this.cards = cards;
        this.isRow = isRow;
        this.categoryTitle = categoryTitle;
        this.itemTitle = itemTitle;
        this.menuCategoryTitle = menuCategoryTitle;
        this.menuItemTitle = menuItemTitle;
    }

    /**
     * The row of the panel that opens the menu it's in
     */
    boolean matchesMenu(OptionCategory category, OptionItem item) {
        return menuItemTitle != null && item.isMenu() && sameText(category.title, menuCategoryTitle)
                && sameText(item.getTitle(), menuItemTitle);
    }

    /**
     * Its row in the panel shown: the list it's in and its title. Any row of a list of choices.
     */
    boolean matches(OptionCategory category, OptionItem item) {
        return isRow && sameText(category.title, categoryTitle) && (itemTitle == null || sameText(item.getTitle(), itemTitle));
    }

    private static boolean sameText(@Nullable CharSequence text1, @Nullable CharSequence text2) {
        return text1 == null || text2 == null ? text1 == text2 : TextUtils.equals(text1.toString(), text2.toString());
    }

    @Override
    public String toString() {
        return id + " " + TextUtils.join(" › ", path) + " › " + title;
    }
}
