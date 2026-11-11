package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;

import java.util.Collections;
import java.util.List;

/**
 * The skins as the shared code sees them: an id and its traits. The app sets the provider at start (SkinRegistry).<br/>
 * The skin picked (MainUIData) is applied when it can be: the activities are registered for it then (ViewManager). A skin
 * picked while a video is open waits till the player is closed, so the app shows one skin at a time.
 */
public final class Skins {
    private static Provider sProvider;

    public interface Provider {
        /**
         * In display order
         */
        List<Integer> getSkinIds();

        int getSkinNameResId(int skinId);

        /**
         * The skin the activities are registered for
         */
        int getAppliedSkinId();

        SkinTraits getTraits(int skinId);

        /**
         * Registers the activities of the skin picked (by this account), unless it can't be now (e.g. a video is open)
         */
        void applyPickedSkin(Context context);

        /**
         * @return true when the picked skin shows every view with the activities of the applied one (e.g. Hybrid and
         * Modern, or the same skin): it can be applied at once, the activities are only recreated
         */
        boolean isPickedSkinInPlace(Context context);

        /**
         * The activity belongs to a skin that's no longer applied: starts the one of the applied skin that shows the same
         * view, and finishes this one.
         *
         * @return false when the applied skin shows the view with this same activity (e.g. a screen only the classic look has)
         */
        boolean replace(Activity activity);
    }

    private Skins() {
    }

    public static void setProvider(Provider provider) {
        sProvider = provider;
    }

    /**
     * The skins to pick from, in display order
     */
    public static List<Integer> getSkinIds() {
        return sProvider != null ? sProvider.getSkinIds() : Collections.singletonList(MainUIData.SKIN_CLASSIC);
    }

    public static int getSkinNameResId(int skinId) {
        return sProvider != null ? sProvider.getSkinNameResId(skinId) : 0;
    }

    public static int getAppliedSkinId() {
        return sProvider != null ? sProvider.getAppliedSkinId() : MainUIData.SKIN_CLASSIC;
    }

    /**
     * The traits of the skin the app shows
     */
    public static SkinTraits getTraits() {
        SkinTraits traits = sProvider != null ? sProvider.getTraits(getAppliedSkinId()) : null;

        return traits != null ? traits : SkinTraits.CLASSIC;
    }

    public static void applyPickedSkin(Context context) {
        if (sProvider != null) {
            sProvider.applyPickedSkin(context);
        }
    }

    public static boolean isPickedSkinInPlace(Context context) {
        return sProvider == null || sProvider.isPickedSkinInPlace(context);
    }

    public static boolean replace(Activity activity) {
        return sProvider != null && sProvider.replace(activity);
    }
}
