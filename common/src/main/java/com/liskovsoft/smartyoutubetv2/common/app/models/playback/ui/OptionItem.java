package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

public interface OptionItem {
    int getId();
    CharSequence getTitle();
    CharSequence getDescription();
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
     * Show this item of a checked list as a line of text: no checkbox, can't be focused or selected
     */
    void setNote(boolean isNote);
    boolean isNote();
    /**
     * Show this item of a checked list as a row that opens a menu: no checkbox, selecting it calls the callback and leaves it unchecked
     */
    void setMenu(boolean isMenu);
    boolean isMenu();
    /**
     * Show this item of a checked list as a switch instead of a checkbox
     */
    void setToggle(boolean isToggle);
    boolean isToggle();
    ChatReceiver getChatReceiver();
    CommentsReceiver getCommentsReceiver();
}
