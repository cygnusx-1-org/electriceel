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
     * Show this item of a checked list as a row that opens a menu: no checkbox, selecting it calls the callback and leaves it unchecked
     */
    void setMenu(boolean isMenu);
    boolean isMenu();
    ChatReceiver getChatReceiver();
    CommentsReceiver getCommentsReceiver();
}
