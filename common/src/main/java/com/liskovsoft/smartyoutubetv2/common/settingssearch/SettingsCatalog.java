package com.liskovsoft.smartyoutubetv2.common.settingssearch;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsPanel;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SettingsCardsPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The search index of the settings: the cards of the Settings section, the cards of their sections, and the rows of the settings
 * panels they open. Read from what each of them declares, the way Android indexes its settings screens: a section its cards
 * (SettingsItem.getCards), a panel its rows (SettingsItem.getPanel, see SettingsPanel), a row that opens a menu the rows of the menu
 * (OptionItem.getPanel), one level down. Nothing is opened, shown or run to make it: the panels show the same rows.<br/>
 * A row that opens a menu without declaring it (e.g. a list loaded in the background) is found, not the rows of its menu.
 */
public final class SettingsCatalog {
    private static final String TAG = SettingsCatalog.class.getSimpleName();
    /**
     * Sections opened in sections, e.g. Settings, System, General: more than that is a card that opens its own section again
     */
    private static final int MAX_DEPTH = 6;
    @Nullable
    private static SettingsCatalog sCached;
    @Nullable
    private static String sCachedLocale;
    private final List<SettingEntry> mEntries;

    private SettingsCatalog(List<SettingEntry> entries) {
        mEntries = Collections.unmodifiableList(entries);
    }

    /**
     * Each card and row once, in the order of the cards (as made, not as sorted on the screen) and of the rows of their panels
     */
    public List<SettingEntry> getEntries() {
        return mEntries;
    }

    /**
     * Built on the first search, then kept, as the search index of most apps: built again after {@link #clearCache()} (the
     * settings may have changed meanwhile, and some panels have other rows then, e.g. Storage sync once it's on) and when the
     * language of the app changes (the names are in it). A setting that isn't there anymore is reported as it's opened (see
     * open). On the main thread.
     */
    public static SettingsCatalog get(Context context) {
        String locale = context.getResources().getConfiguration().locale.toString();

        if (sCached == null || !locale.equals(sCachedLocale)) {
            long start = System.currentTimeMillis();
            sCached = build(context);
            sCachedLocale = locale;
            Log.d(TAG, "Settings listed: " + sCached.mEntries.size() + " in " + (System.currentTimeMillis() - start) + " ms");
        }

        return sCached;
    }

    /**
     * Built again on the next search
     */
    public static void clearCache() {
        sCached = null;
        sCachedLocale = null;
    }

    /**
     * On the main thread: the rows of the panels are made there
     */
    public static SettingsCatalog build(Context context) {
        Builder builder = new Builder(context);
        List<SettingsItem> cards = AppDataSourceManager.instance().getSettingItems(context);

        for (int i = 0; i < cards.size(); i++) {
            builder.addCard(cards.get(i), new int[] {i}, new ArrayList<>());
        }

        return new SettingsCatalog(builder.mEntries);
    }

    /**
     * Opens it as its card would, the settings password asked before as for a card (see the caller).<br/>
     * A section: on its own screen, the sections before it under it.<br/>
     * A card or a row of its panel: the panel, titled as from its section (e.g. "Player - Video", "Updates - Release notes"), the
     * row focused. A row of a menu: the menu over it.<br/>
     * The cards are found again by their place: they're made in the same order each time.
     *
     * @return false when it isn't there anymore (e.g. the settings changed since they were listed), or the menu it's in is greyed
     * out: nothing opened
     */
    public static boolean open(Context context, SettingEntry entry) {
        try {
            return openCard(context, entry);
        } catch (RuntimeException e) {
            Log.e(TAG, "Can't open " + entry + ": " + e);
            return false;
        }
    }

    private static boolean openCard(Context context, SettingEntry entry) {
        List<SettingsItem> cards = AppDataSourceManager.instance().getSettingItems(context);
        List<SettingsCardsPresenter.Cards> sections = new ArrayList<>();
        List<Integer> openers = new ArrayList<>();
        SettingsItem card = null;

        for (int level = 0; level < entry.cards.length; level++) {
            int index = entry.cards[level];

            if (index >= cards.size() || !TextUtils.equals(cards.get(index).title, getCardTitle(entry, level))) {
                return false;
            }

            card = cards.get(index);

            if (level == entry.cards.length - 1) {
                break;
            }

            SettingsCardsPresenter.Cards section = card.getCards();

            if (section == null) {
                return false;
            }

            if (!sections.isEmpty()) {
                openers.add(index);
            }

            sections.add(section);
            cards = section.getItems(context);
        }

        if (card == null || card.onClick == null) {
            return false;
        }

        if (entry.kind == SettingEntry.KIND_SECTION) {
            SettingsCardsPresenter.Cards section = card.getCards();

            if (section == null) {
                return false;
            }

            if (!sections.isEmpty()) {
                openers.add(entry.cards[entry.cards.length - 1]);
            }

            sections.add(section);
            SettingsCardsPresenter.instance(context).showPath(sections, openers, -1);
            return true;
        }

        if (entry.isRow && !hasRow(card, entry)) {
            return false;
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        if (entry.menuItemTitle != null) {
            // The panel with the row of the menu focused, then the menu over it, once the panel is shown (Back goes to it)
            dialog.setFocusedItemMatcher(entry::matchesMenu);
            dialog.setOnStart(() -> Utils.post(() -> {
                OptionItem menu = dialog.getFocusedItem();

                if (menu == null || !menu.isMenu() || isDisabled(menu)) {
                    return;
                }

                if (entry.isRow) {
                    dialog.setFocusedItemMatcher(entry::matches);
                }

                menu.onSelect(isPressedSelected(dialog.getCategories(), menu));
            }));
        } else if (entry.isRow) {
            dialog.setFocusedItemMatcher(entry::matches);
        }

        // Titled as the card opens it: "<Section> - <Card>" in a section (see SettingsCardsFragment), its lists "<Parent> - <List>"
        // (see SettingsGridFragment)
        dialog.setTitlePrefix(sections.isEmpty() ? null : sections.get(sections.size() - 1).getTitle(context));
        dialog.enableNestedTitles(true);

        try {
            card.onClick.run();
        } finally {
            // Only the panel opened right away
            dialog.setTitlePrefix(null);
            dialog.enableNestedTitles(false);
        }

        return true;
    }

    /**
     * Its row is in the panel of the card, and in the menu of the row it's in, the menu not greyed out: as they are now
     */
    private static boolean hasRow(SettingsItem card, SettingEntry entry) {
        if (card.getPanel() == null) {
            return false;
        }

        List<OptionCategory> panel = SettingsPanel.collect(card.getPanel());

        if (entry.menuItemTitle == null) {
            return AppDialogPresenter.findItem(panel, entry::matches) != null;
        }

        OptionItem menu = AppDialogPresenter.findItem(panel, entry::matchesMenu);

        if (menu == null || isDisabled(menu) || menu.getPanel() == null) {
            return false;
        }

        return !entry.isRow || AppDialogPresenter.findItem(SettingsPanel.collect(menu.getPanel()), entry::matches) != null;
    }

    /**
     * Greyed out in the panel: it can't be clicked (e.g. Categories, without a key of the Data API)
     */
    static boolean isDisabled(OptionItem item) {
        return !item.isEnabled() || item.getDisabledBy() != null && item.getDisabledBy().isSelected();
    }

    /**
     * Of the card at the level: the titles of the cards are at the start of the path, a card's own is its title
     */
    private static String getCardTitle(SettingEntry entry, int level) {
        boolean isCard = entry.kind == SettingEntry.KIND_SECTION || entry.kind == SettingEntry.KIND_CARD;
        return isCard && level == entry.cards.length - 1 ? entry.title : entry.path.get(level);
    }

    /**
     * As the row is pressed on the screen (see AppPreferenceManager): a button selected, a menu of a list not
     */
    private static boolean isPressedSelected(@Nullable List<OptionCategory> categories, OptionItem item) {
        if (categories != null) {
            for (OptionCategory category : categories) {
                if (category.options != null && category.options.contains(item)) {
                    return category.type == OptionCategory.TYPE_SINGLE_BUTTON;
                }
            }
        }

        return false;
    }

    /**
     * @return trimmed, without the icons of the text (e.g. of an account), null when nothing is left
     */
    @Nullable
    static String toText(@Nullable CharSequence text) {
        if (text == null) {
            return null;
        }

        String result = text.toString().replace("￼", "").trim();

        return result.isEmpty() ? null : result;
    }

    private static final class Builder {
        private final Context mContext;
        private final List<SettingEntry> mEntries = new ArrayList<>();
        /**
         * While a menu of a panel is looked through: the row that opens it
         */
        private String mMenuCategoryTitle;
        private String mMenuItemTitle;
        /**
         * The rows of the menus looked through: a menu of the same rows (e.g. Sections, of each filter) isn't listed again
         */
        private final List<java.util.Set<String>> mMenus = new ArrayList<>();

        private Builder(Context context) {
            mContext = context;
        }

        private void addCard(SettingsItem card, int[] cards, List<String> path) {
            String title = toText(card.title);

            if (title == null) {
                return;
            }

            SettingsCardsPresenter.Cards section = card.getCards();
            add(section != null ? SettingEntry.KIND_SECTION : SettingEntry.KIND_CARD, title, path, Collections.emptyList(), cards,
                    false, null, null);
            List<String> inside = plus(path, title);

            if (section != null && cards.length < MAX_DEPTH) {
                List<SettingsItem> items;

                try {
                    items = section.getItems(mContext);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Can't get the cards of " + title + ": " + e);
                    return;
                }

                for (int i = 0; i < items.size(); i++) {
                    addCard(items.get(i), plus(cards, i), inside);
                }
            } else if (card.getPanel() != null) {
                List<OptionCategory> panel = collect(card.getPanel(), title);

                if (panel != null) {
                    addPanel(panel, cards, inside);
                }
            }
        }

        /**
         * @return null when it failed
         */
        @Nullable
        private static List<OptionCategory> collect(SettingsPanel panel, CharSequence title) {
            try {
                return SettingsPanel.collect(panel);
            } catch (RuntimeException e) {
                Log.e(TAG, "Can't get the rows of " + title + ": " + e);
                return null;
            }
        }

        private void addPanel(List<OptionCategory> categories, int[] cards, List<String> path) {
            for (OptionCategory category : categories) {
                if (category.options == null) {
                    continue;
                }

                String categoryTitle = toText(category.title);

                switch (category.type) {
                    case OptionCategory.TYPE_RADIO_LIST:
                        addChoices(category, categoryTitle, cards, path);
                        break;
                    case OptionCategory.TYPE_CHECKBOX_LIST:
                        addChecks(category, categoryTitle, cards, path);
                        break;
                    case OptionCategory.TYPE_SINGLE_SWITCH:
                    case OptionCategory.TYPE_SINGLE_BUTTON:
                        for (OptionItem item : category.options) {
                            String title = toText(item.getTitle());

                            if (title == null || item.isNote()) {
                                continue;
                            }

                            boolean isSwitch = category.type == OptionCategory.TYPE_SINGLE_SWITCH;
                            // A button that opens a list (e.g. Order of the sidebar, Set-up sections), unless it's greyed out
                            List<OptionCategory> opened = !isSwitch ? collectMenu(item) : null;
                            // To put in order: its items, as its choices
                            List<String> items = opened != null ? getOrderItems(opened) : Collections.emptyList();
                            add(isSwitch ? SettingEntry.KIND_TOGGLE : SettingEntry.KIND_BUTTON, title, path, items, cards, true,
                                    categoryTitle, title);

                            // Otherwise its rows, under it
                            if (opened != null && items.isEmpty()) {
                                addMenuRows(opened, categoryTitle, title, cards, plusOther(path, title));
                            }
                        }
                        break;
                    case OptionCategory.TYPE_STRING_LIST:
                    case OptionCategory.TYPE_REORDER_LIST:
                    case OptionCategory.TYPE_LONG_TEXT:
                        // A row that opens a list or a text: the items of a list, as its choices
                        if (categoryTitle != null) {
                            List<String> items = new ArrayList<>();

                            if (category.type != OptionCategory.TYPE_LONG_TEXT) {
                                for (OptionItem item : category.options) {
                                    String title = toText(item.getTitle());

                                    if (title != null) {
                                        items.add(title);
                                    }
                                }
                            }

                            add(SettingEntry.KIND_BUTTON, categoryTitle, path, items, cards, true, categoryTitle, null);
                        }
                        break;
                    default:
                        // QR codes, chat, comments: nothing to set
                        break;
                }
            }
        }

        private void addChoices(OptionCategory category, @Nullable String categoryTitle, int[] cards, List<String> path) {
            List<String> choices = new ArrayList<>();

            for (OptionItem item : category.options) {
                String title = toText(item.getTitle());

                if (title == null || item.isNote() || item.isLabel() || item.isSeparator()) {
                    continue;
                }

                choices.add(title);
            }

            // Untitled: the only list of the panel, named as its card
            String title = categoryTitle != null ? categoryTitle : path.get(path.size() - 1);
            add(SettingEntry.KIND_CHOICE, title, path, choices, cards, true, categoryTitle, null);
        }

        /**
         * Each checkbox under the title of its list, and under its label in the list when it has one
         */
        private void addChecks(OptionCategory category, @Nullable String categoryTitle, int[] cards, List<String> path) {
            // Not named twice when the list is named as its panel (e.g. Hide content, Hide content)
            List<String> inside = categoryTitle != null ? plusOther(path, categoryTitle) : path;
            String label = null;

            for (OptionItem item : category.options) {
                String title = toText(item.getTitle());

                if (title == null || item.isSeparator() || item.isNote()) {
                    continue;
                }

                if (item.isLabel()) {
                    label = title;
                    continue;
                }

                boolean isMenu = item.isMenu();
                List<String> itemPath = label != null ? plus(inside, label) : inside;
                add(isMenu ? SettingEntry.KIND_BUTTON : SettingEntry.KIND_TOGGLE, title, itemPath, Collections.emptyList(), cards,
                        true, categoryTitle, title);

                // Not what a greyed out one opens (e.g. Categories, without a key of the Data API): it can't be opened
                List<OptionCategory> menu = isMenu ? collectMenu(item) : null;

                if (menu != null) {
                    addMenuRows(menu, categoryTitle, title, cards, plusOther(itemPath, title));
                }
            }
        }

        /**
         * The rows of the menu the row opens: null when it opens none, declares none, is greyed out (e.g. Categories, without a
         * key of the Data API: it can't be opened), or is in a menu itself (one level down only)
         */
        @Nullable
        private List<OptionCategory> collectMenu(OptionItem item) {
            if (!item.isMenu() || item.getPanel() == null || isDisabled(item) || mMenuItemTitle != null) {
                return null;
            }

            return collect(item.getPanel(), item.getTitle());
        }

        /**
         * The items of its lists to put in order
         */
        private static List<String> getOrderItems(List<OptionCategory> panel) {
            List<String> result = new ArrayList<>();

            for (OptionCategory category : panel) {
                if (category.type != OptionCategory.TYPE_REORDER_LIST || category.options == null) {
                    continue;
                }

                for (OptionItem listItem : category.options) {
                    String title = toText(listItem.getTitle());

                    if (title != null) {
                        result.add(title);
                    }
                }
            }

            return result;
        }

        private void addMenuRows(List<OptionCategory> panel, @Nullable String categoryTitle, String title, int[] cards, List<String> path) {
            if (isListed(getRowTitles(panel))) {
                return;
            }

            mMenuCategoryTitle = categoryTitle;
            mMenuItemTitle = title;

            try {
                addPanel(panel, cards, path);
            } finally {
                mMenuCategoryTitle = null;
                mMenuItemTitle = null;
            }
        }

        private void add(int kind, String title, List<String> path, List<String> choices, int[] cards, boolean isRow,
                         @Nullable String categoryTitle, @Nullable String itemTitle) {
            mEntries.add(new SettingEntry(kind, title, path, choices, cards, isRow, categoryTitle, itemTitle, mMenuCategoryTitle,
                    mMenuItemTitle));
        }

        /**
         * Mostly the same rows as a menu looked through already (e.g. the Sections of each filter, a row more or less): only
         * the row that opens it is listed. Otherwise it's kept, as listed.
         */
        private boolean isListed(java.util.Set<String> titles) {
            for (java.util.Set<String> menu : mMenus) {
                int same = 0;

                for (String title : titles) {
                    if (menu.contains(title)) {
                        same++;
                    }
                }

                if (same >= 0.8f * Math.min(menu.size(), titles.size()) && same >= 3) {
                    return true;
                }
            }

            mMenus.add(titles);
            return false;
        }

        private static java.util.Set<String> getRowTitles(List<OptionCategory> categories) {
            java.util.Set<String> titles = new java.util.HashSet<>();

            for (OptionCategory category : categories) {
                if (category.options == null) {
                    continue;
                }

                for (OptionItem item : category.options) {
                    if (item.getTitle() != null) {
                        titles.add(item.getTitle().toString());
                    }
                }
            }

            return titles;
        }

        /**
         * Plus the item, unless it's the last one already
         */
        private static List<String> plusOther(List<String> list, String item) {
            return !list.isEmpty() && list.get(list.size() - 1).equals(item) ? list : plus(list, item);
        }

        private static List<String> plus(List<String> list, String item) {
            List<String> result = new ArrayList<>(list);
            result.add(item);
            return result;
        }

        private static int[] plus(int[] array, int item) {
            int[] result = Arrays.copyOf(array, array.length + 1);
            result[array.length] = item;
            return result;
        }
    }
}
