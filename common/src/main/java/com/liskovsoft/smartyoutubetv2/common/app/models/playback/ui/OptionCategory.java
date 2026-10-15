package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OptionCategory {
    /**
     * The items of a list that's put in order (see {@link #reorderList})
     */
    public interface OnReorder {
        /**
         * @param items all of them, in their new order
         */
        void onReorder(List<OptionItem> items);
    }

    public static OptionCategory radioList(CharSequence title, List<OptionItem> items) {
        return new OptionCategory(title, items, TYPE_RADIO_LIST);
    }

    public static OptionCategory checkedList(CharSequence title, List<OptionItem> items) {
        return new OptionCategory(title, items, TYPE_CHECKBOX_LIST);
    }

    public static OptionCategory stringList(CharSequence title, List<OptionItem> items) {
        return new OptionCategory(title, items, TYPE_STRING_LIST);
    }

    /**
     * Its items are moved up and down: one is picked up and moved to its place. Told of the new order at each move.
     */
    public static OptionCategory reorderList(CharSequence title, List<OptionItem> items, OnReorder onReorder) {
        return new OptionCategory(title, items, TYPE_REORDER_LIST, -1, null, onReorder);
    }

    public static OptionCategory longText(CharSequence title, OptionItem item) {
        return new OptionCategory(title, Collections.singletonList(item), TYPE_LONG_TEXT);
    }

    public static OptionCategory chat(CharSequence title, OptionItem item) {
        return new OptionCategory(title, Collections.singletonList(item), TYPE_CHAT);
    }

    public static OptionCategory comments(CharSequence title, OptionItem item) {
        return new OptionCategory(title, Collections.singletonList(item), TYPE_COMMENTS);
    }

    /**
     * A QR code with its title above it. Nothing to press.
     *
     * @param text e.g. an address, made into the code on the device (see QrCodeEncoder): shown at once, offline too
     */
    public static OptionCategory qrCode(CharSequence title, String text) {
        return new OptionCategory(title, Collections.<OptionItem>emptyList(), TYPE_QR_CODE, -1, text);
    }

    public static OptionCategory singleSwitch(OptionItem item) {
        ArrayList<OptionItem> items = new ArrayList<>();
        items.add(item);
        return new OptionCategory(null, items, TYPE_SINGLE_SWITCH);
    }

    public static OptionCategory singleButton(OptionItem item) {
        ArrayList<OptionItem> items = new ArrayList<>();
        items.add(item);
        return new OptionCategory(null, items, TYPE_SINGLE_BUTTON);
    }

    public static OptionCategory from(int id, int type, CharSequence title, List<OptionItem> options) {
        return new OptionCategory(title, options, type, id);
    }

    public static OptionCategory from(int id, int type, CharSequence title, OptionItem option) {
        return new OptionCategory(title, Collections.singletonList(option), type, id);
    }

    private OptionCategory(CharSequence title, List<OptionItem> options, int type) {
        this(title, options, type, -1);
    }

    private OptionCategory(CharSequence title, List<OptionItem> options, int type, int id) {
        this(title, options, type, id, null);
    }

    private OptionCategory(CharSequence title, List<OptionItem> options, int type, int id, String qrCodeText) {
        this(title, options, type, id, qrCodeText, null);
    }

    private OptionCategory(CharSequence title, List<OptionItem> options, int type, int id, String qrCodeText, OnReorder onReorder) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.options = options;
        this.qrCodeText = qrCodeText;
        this.onReorder = onReorder;
    }

    public static final int TYPE_RADIO_LIST = 0;
    public static final int TYPE_CHECKBOX_LIST = 1;
    public static final int TYPE_SINGLE_SWITCH = 2;
    public static final int TYPE_SINGLE_BUTTON = 3;
    public static final int TYPE_STRING_LIST = 4;
    public static final int TYPE_LONG_TEXT = 5;
    public static final int TYPE_CHAT = 6;
    public static final int TYPE_COMMENTS = 7;
    public static final int TYPE_QR_CODE = 8;
    public static final int TYPE_REORDER_LIST = 9;
    public final int id;
    public final int type;
    public final CharSequence title;
    public final List<OptionItem> options;
    /**
     * Of {@link #TYPE_QR_CODE}, null for the others
     */
    public final String qrCodeText;
    /**
     * Of {@link #TYPE_REORDER_LIST}, null for the others
     */
    public final OnReorder onReorder;
}
