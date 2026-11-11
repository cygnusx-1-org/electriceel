package com.liskovsoft.smartyoutubetv2.tv.ui.modern.common;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Color scheme of the modern interface (User interface > Color scheme), see values/modern_schemes.xml.
 * Layouts read the scheme through theme attributes, code through these helpers.
 */
public final class ModernColorScheme {
    private ModernColorScheme() {
    }

    /**
     * Call right after {@code setTheme()} with one of the modern themes.
     */
    public static void apply(Activity activity) {
        apply(activity, ModernStyle.isHybrid() ? R.style.ThemeOverlay_Modern_Hybrid : R.style.ThemeOverlay_Modern_Window);
    }

    /**
     * The screens over the others, the player and the side panels: the colors of the scheme, the window of their theme
     * in every skin (black behind the video, transparent around a panel so the screen that opened it stays visible).
     * Call right after {@code setTheme()}.
     */
    public static void applyKeepingWindow(Activity activity) {
        apply(activity, 0);
    }

    /**
     * @param windowOverlayResId the background of the skin behind the cards (window and shelf), 0 to keep the theme's
     */
    private static void apply(Activity activity, int windowOverlayResId) {
        int overlayResId = getOverlayResId(MainUIData.instance(activity).getColorScheme().id);

        if (overlayResId > 0) {
            activity.getTheme().applyStyle(overlayResId, true);
        }

        // The hybrid skin: the classic background behind the cards. The modern one: its own, set again (a screen taking a
        // picked skin keeps its theme, with the hybrid values, see MotherActivity.showNewThemeBackground)
        if (windowOverlayResId != 0) {
            activity.getTheme().applyStyle(windowOverlayResId, true);
        }

        // The window reads its style from the manifest theme before setTheme(): set the background of the scheme
        TypedArray window = activity.obtainStyledAttributes(new int[] {android.R.attr.windowBackground});
        activity.getWindow().setBackgroundDrawable(window.getDrawable(0));
        window.recycle();
    }

    /**
     * A screen only the classic look has (its activity and theme, see ModernSkin): the background of the skin (the classic one
     * for the hybrid skin), in the color scheme. Call right after the classic theme is set.
     */
    public static void applyToClassicScreen(Activity activity) {
        int overlayResId = getOverlayResId(MainUIData.instance(activity).getColorScheme().id);

        if (overlayResId > 0) {
            activity.getTheme().applyStyle(overlayResId, true);
        }

        // The hybrid skin: the classic background of the scheme, as its own screens
        activity.getTheme().applyStyle(ModernStyle.isHybrid() ? R.style.ThemeOverlay_Modern_Hybrid : R.style.ThemeOverlay_Modern_ClassicScreen, true);

        TypedArray window = activity.obtainStyledAttributes(new int[] {android.R.attr.windowBackground});
        activity.getWindow().setBackgroundDrawable(window.getDrawable(0));
        window.recycle();
    }

    /**
     * The overlay of a color scheme (MainUIData.ColorScheme.id), 0 for none (the default look)
     */
    public static int getOverlayResId(int colorSchemeId) {
        switch (colorSchemeId) {
            case 0:
                return R.style.ThemeOverlay_Modern_Scheme_Teal;
            case 1:
                return R.style.ThemeOverlay_Modern_Scheme_DarkGrey;
            case 2:
                return R.style.ThemeOverlay_Modern_Scheme_Red;
            case 3:
                return R.style.ThemeOverlay_Modern_Scheme_Oled_DarkGrey;
            case 4:
                return R.style.ThemeOverlay_Modern_Scheme_Oled_Teal;
            case 5:
                return R.style.ThemeOverlay_Modern_Scheme_Monochrome;
            case 6:
                return R.style.ThemeOverlay_Modern_Scheme_Blue;
            case 7:
                return R.style.ThemeOverlay_Modern_Scheme_Oled_Blue;
            case 8:
                return R.style.ThemeOverlay_Modern_Scheme_ElectricBlue;
            default:
                return 0;
        }
    }

    public static int background(Context context) {
        return color(context, R.attr.modernBackground, R.color.modern_bg);
    }

    /**
     * The text area of a card (hybrid skin): the info field of the classic cards in the scheme.
     */
    public static int cardBackground(Context context, boolean focused) {
        return focused ? color(context, R.attr.cardSelectedBackground, R.color.card_selected_background_white)
                : color(context, R.attr.cardDefaultBackground, R.color.card_default_background_dark);
    }

    /**
     * Behind the cards: the background of the skin, the one of the classic screens in the scheme for the hybrid skin.
     */
    public static int shelfBackground(Context context) {
        return ModernStyle.isHybrid() ? color(context, R.attr.modernShelfBackground, R.color.shelf_background_dark) : background(context);
    }

    /**
     * Expanded rail, like the headers of the classic interface.
     */
    public static int brand(Context context) {
        return color(context, R.attr.modernBrand, R.color.modern_bg);
    }

    /**
     * Watch progress, seek bar, live badge, voice search.
     */
    public static int accent(Context context) {
        return color(context, R.attr.modernAccent, R.color.modern_progress);
    }

    /**
     * Logo of the top bar, 0 when the scheme hides it (OLED).
     */
    public static int logo(Context context) {
        TypedValue value = new TypedValue();

        if (context.getTheme().resolveAttribute(R.attr.modernLogo, value, true)) {
            return value.resourceId;
        }

        return R.mipmap.app_logo_semi_red;
    }

    public static int drawable(Context context, int attr, int defaultResId) {
        TypedValue value = new TypedValue();

        if (context.getTheme().resolveAttribute(attr, value, true) && value.resourceId != 0) {
            return value.resourceId;
        }

        return defaultResId;
    }

    /**
     * Live badge of the tiles (also upcoming and new content): dark red as the classic cards (VideoCardPresenter), in every
     * color scheme.
     */
    public static Drawable liveBackground(Context context) {
        Drawable background = DrawableCompat.wrap(ContextCompat.getDrawable(context, R.drawable.modern_live_bg).mutate());
        DrawableCompat.setTint(background, ContextCompat.getColor(context, R.color.dark_red));
        return background;
    }

    /**
     * Voice search button: white when focused, accent while listening.
     */
    public static Drawable micBackground(Context context) {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused}, oval(ContextCompat.getColor(context, R.color.modern_rail_selected_pill)));
        background.addState(new int[] {android.R.attr.state_activated}, oval(accent(context)));
        background.addState(new int[] {}, oval(ContextCompat.getColor(context, R.color.modern_white_10)));
        return background;
    }

    private static Drawable oval(int color) {
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(color);
        return oval;
    }

    private static int color(Context context, int attr, int defaultResId) {
        TypedValue value = new TypedValue();

        if (!context.getTheme().resolveAttribute(attr, value, true)) {
            return ContextCompat.getColor(context, defaultResId);
        }

        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }

        return ContextCompat.getColor(context, value.resourceId != 0 ? value.resourceId : defaultResId);
    }
}
