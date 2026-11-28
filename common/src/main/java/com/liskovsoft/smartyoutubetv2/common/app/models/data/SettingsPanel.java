package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * What a settings panel has: its rows, as they're shown. The panel shows them (e.g. PlayerSettingsPresenter.showVideo appends them
 * to the dialog, then shows it), and the settings search reads them (see SettingsCatalog), as a settings screen of Android is
 * declared once, then both shown and indexed.<br/>
 * Only appends: nothing is shown, held, started or saved as they're made.
 */
public interface SettingsPanel {
    void appendRows(SettingsRows rows);

    /**
     * Its rows, without showing them
     */
    static List<OptionCategory> collect(SettingsPanel panel) {
        List<OptionCategory> categories = new ArrayList<>();
        panel.appendRows(categories::add);
        return categories;
    }
}
