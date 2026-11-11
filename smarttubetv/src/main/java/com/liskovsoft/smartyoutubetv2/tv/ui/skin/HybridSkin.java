package com.liskovsoft.smartyoutubetv2.tv.ui.skin;

import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * The screens of the modern skin with the look of the classic one where it was asked for (see ModernStyle): the size, text and
 * colors of the classic cards, the classic background, the classic sidebar (no Search entry, Settings in the list, no
 * separator), rows that scroll only at the edge, titles that scroll when they're cut.<br/>
 * Same activities as the modern skin: switching between the two recreates them (see MotherActivity).
 */
public class HybridSkin extends ModernSkin {
    @Override
    public int getId() {
        return MainUIData.SKIN_HYBRID;
    }

    @Override
    public int getNameResId() {
        return R.string.skin_hybrid;
    }
}
