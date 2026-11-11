package com.liskovsoft.smartyoutubetv2.tv.ui.modern.common;

import com.liskovsoft.smartyoutubetv2.common.misc.Skins;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;

/**
 * The look of the modern screens: the modern skin's own, or the hybrid one (the modern screens with the classic cards,
 * background and sidebar, see HybridSkin). The screens read it when they're created: a switch between the two skins
 * recreates them.
 */
public final class ModernStyle {
    private ModernStyle() {
    }

    public static boolean isHybrid() {
        return Skins.getAppliedSkinId() == MainUIData.SKIN_HYBRID;
    }
}
