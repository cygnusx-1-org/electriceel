package com.liskovsoft.smartyoutubetv2.tv.ui.settingscards;

import android.view.KeyEvent;
import android.view.View;

import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

/**
 * A long press of OK on a settings card, or the Menu key, as on the video cards (see LongClickPresenter), in every skin: e.g.
 * the addresses of the card as QR codes (see SettingsLinksPresenter).<br/>
 * A press the card has no use for is left alone: a long one opens the card on release, as it did before (the click follows
 * an unused long press).
 */
public final class SettingsCardLongPress {
    public interface Action {
        /**
         * @return the press was used: the card isn't opened
         */
        boolean run();
    }

    private SettingsCardLongPress() {
    }

    public static void attach(View card, Action action) {
        // Read at each press: the setting can change while the cards are shown
        card.setOnLongClickListener(v -> !GeneralData.instance(v.getContext()).isOkButtonLongPressDisabled() && action.run());
        card.setOnKeyListener((v, keyCode, event) ->
                KeyHelpers.isMenuKey(keyCode) && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && action.run());
    }

    public static void detach(View card) {
        card.setOnLongClickListener(null);
        card.setOnKeyListener(null);
    }
}
