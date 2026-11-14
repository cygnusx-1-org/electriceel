package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem.Link;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

import java.util.List;

/**
 * The addresses of a settings card as QR codes, on a long press of the card (e.g. the site of SponsorBlock), to open them on
 * a phone. Made on the device: there at once, offline too (see QrCodeEncoder).<br/>
 * Not behind the settings password: the addresses are public.
 */
public class SettingsLinksPresenter extends BasePresenter<Void> {
    private SettingsLinksPresenter(Context context) {
        super(context);
    }

    public static SettingsLinksPresenter instance(Context context) {
        return new SettingsLinksPresenter(context);
    }

    /**
     * One address: its QR code right away. More: a row each, that opens its QR code (they don't fit in the panel together).
     *
     * @return false when the card has no address: nothing shown
     */
    public boolean show(SettingsItem item) {
        List<Link> links = item.getLinks();

        if (links.isEmpty()) {
            return false;
        }

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        if (links.size() == 1) {
            Link link = links.get(0);
            settingsPresenter.appendQrCode(link.title, link.url);
            // A single item would be opened right away instead of showing the panel with the picture
            settingsPresenter.enableExpandable(false);
        } else {
            for (Link link : links) {
                settingsPresenter.appendSingleButton(UiOptionItem.from(link.title, option -> showQrCode(link)));
            }
        }

        settingsPresenter.showDialog(item.title);

        return true;
    }

    /**
     * The code has the address, so it isn't written out (see AboutSimpleSettingsPresenter)
     */
    private void showQrCode(Link link) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        settingsPresenter.appendQrCode(link.title, link.url);
        settingsPresenter.enableExpandable(false);
        settingsPresenter.showDialog(link.title);
    }
}
