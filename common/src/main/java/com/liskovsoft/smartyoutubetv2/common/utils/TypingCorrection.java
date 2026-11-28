package com.liskovsoft.smartyoutubetv2.common.utils;

import android.text.InputType;
import android.widget.EditText;
import androidx.annotation.VisibleForTesting;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;

/**
 * The keyboard's corrections and suggestions while a search is typed: off by default (Settings, Search), in every search field
 * alike — the search screen, the channel search and the channels filter
 */
public final class TypingCorrection {
    /**
     * A password shown as typed: no keyboard corrects or suggests words in one, even those that ignore textNoSuggestions
     */
    @VisibleForTesting
    static final int OFF_INPUT_TYPE = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
    /**
     * On a Fire TV: plain text without suggestions. Its keyboard has no voice typing in a password field, so the remote's mic
     * couldn't type the search.
     */
    @VisibleForTesting
    static final int OFF_INPUT_TYPE_FIRE_TV = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;

    private TypingCorrection() {
    }

    /**
     * Turns the keyboard's corrections off in a search field, unless the search setting keeps them
     */
    public static void applySearchSetting(EditText field) {
        if (SearchData.instance(field.getContext()).isTypingCorrectionDisabled()) {
            turnOff(field);
        }
    }

    /**
     * Before the field has the keyboard: the keyboard reads the type when it opens. Only what the keyboard is told changes:
     * setInputType would also switch the field's font to a plain monospace, a visible password's.
     */
    public static void turnOff(EditText field) {
        turnOff(field, Helpers.isAmazonFireTVDevice());
    }

    @VisibleForTesting
    static void turnOff(EditText field, boolean isFireTV) {
        field.setRawInputType(isFireTV ? OFF_INPUT_TYPE_FIRE_TV : OFF_INPUT_TYPE);
    }
}
