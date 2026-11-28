package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;

import java.util.List;

/**
 * Where the rows of a settings panel are put: the dialog that shows them (AppDialogPresenter), or a list that only keeps them
 * (see SettingsPanel.collect), e.g. for the settings search.
 */
public interface SettingsRows {
    void appendCategory(OptionCategory category);

    /**
     * The row that has the focus as the panel opens: of the dialog only
     */
    default void setFocusedItem(OptionItem item) {
    }

    default void appendRadioCategory(CharSequence categoryTitle, List<OptionItem> items) {
        appendCategory(OptionCategory.radioList(categoryTitle, items));
    }

    default void appendCheckedCategory(CharSequence categoryTitle, List<OptionItem> items) {
        appendCategory(OptionCategory.checkedList(categoryTitle, items));
    }

    /**
     * Its items are moved up and down, see {@link OptionCategory#reorderList}
     */
    default void appendReorderCategory(CharSequence categoryTitle, List<OptionItem> items, OptionCategory.OnReorder onReorder) {
        appendCategory(OptionCategory.reorderList(categoryTitle, items, onReorder));
    }

    default void appendStringsCategory(CharSequence categoryTitle, List<OptionItem> items) {
        appendCategory(OptionCategory.stringList(categoryTitle, items));
    }

    default void appendLongTextCategory(CharSequence categoryTitle, OptionItem item) {
        appendCategory(OptionCategory.longText(categoryTitle, item));
    }

    /**
     * @param isLinkified its addresses and time codes are links
     */
    default void appendLongTextCategory(CharSequence categoryTitle, OptionItem item, boolean isLinkified) {
        appendCategory(OptionCategory.longText(categoryTitle, item, isLinkified));
    }

    default void appendChatCategory(CharSequence categoryTitle, OptionItem item) {
        appendCategory(OptionCategory.chat(categoryTitle, item));
    }

    /**
     * @param subtitle on its own line under the title (e.g. the number of comments), null for none
     */
    default void appendCommentsCategory(CharSequence categoryTitle, CharSequence subtitle, OptionItem item) {
        appendCategory(OptionCategory.comments(categoryTitle, subtitle, item));
    }

    default void appendSingleSwitch(OptionItem optionItem) {
        appendCategory(OptionCategory.singleSwitch(optionItem));
    }

    default void appendSingleButton(OptionItem optionItem) {
        appendCategory(OptionCategory.singleButton(optionItem));
    }

    /**
     * A QR code of the text (e.g. an address) with its title above it
     */
    default void appendQrCode(CharSequence title, String text) {
        appendCategory(OptionCategory.qrCode(title, text));
    }
}
