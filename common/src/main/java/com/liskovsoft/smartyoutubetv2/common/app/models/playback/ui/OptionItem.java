package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsPanel;

public interface OptionItem {
    int getId();
    CharSequence getTitle();
    CharSequence getDescription();
    /**
     * A single button's, while it's shown (e.g. a check's results, as they come): its row shows the new one at once. On the main
     * thread. A description of several lines shows each of them (two at most otherwise).
     */
    void setDescription(CharSequence description);
    /**
     * Set by the row that shows the item: called with each new description (see setDescription)
     */
    void setOnDescriptionChange(Runnable onChange);
    boolean isSelected();
    void onSelect(boolean isSelected);
    Object getData();
    void setRequired(OptionItem... items);
    OptionItem[] getRequired();
    void setRadio(OptionItem... items);
    OptionItem[] getRadio();
    /**
     * Grey out this item (and ignore clicks) while the other item of the same checked list is selected
     */
    void setDisabledBy(OptionItem item);
    OptionItem getDisabledBy();
    /**
     * Grey out this item of a checked list (and ignore clicks) for as long as the list is shown
     */
    void setEnabled(boolean isEnabled);
    boolean isEnabled();
    /**
     * Show this item of a checked list as a line of text: no checkbox, can't be focused or selected.<br/>
     * A single button: its title and description, as a row's, but it can't be focused or pressed.
     */
    void setNote(boolean isNote);
    boolean isNote();
    /**
     * Show this item of a checked list as a line between the items above and below it: no title or checkbox, can't be focused or selected
     */
    void setSeparator(boolean isSeparator);
    boolean isSeparator();
    /**
     * Show this item of a checked list as the title of the items below it: no checkbox, can't be focused or selected
     */
    void setLabel(boolean isLabel);
    boolean isLabel();
    /**
     * Show this item of a checked list as a row that opens a menu: no checkbox, selecting it calls the callback and leaves it unchecked
     */
    void setMenu(boolean isMenu);
    boolean isMenu();
    /**
     * Of a row that opens a menu: the rows of that menu, as it shows them (see SettingsPanel). Found by the settings search.
     */
    void setPanel(SettingsPanel panel);
    /**
     * @return null when none is set
     */
    SettingsPanel getPanel();
    /**
     * Show this item of a checked list as a switch instead of a checkbox
     */
    void setToggle(boolean isToggle);
    boolean isToggle();
    /**
     * Check or uncheck the other items of the same checked list along with this one (e.g. "All"). It's checked while they all are.
     */
    void setSelectAll(OptionItem... items);
    OptionItem[] getSelectAll();
    /**
     * A single button's image, shown round at the start of its row (e.g. a channel's avatar). Null: none.
     */
    void setIconUrl(String iconUrl);
    String getIconUrl();
    ChatReceiver getChatReceiver();
    CommentsReceiver getCommentsReceiver();
}
