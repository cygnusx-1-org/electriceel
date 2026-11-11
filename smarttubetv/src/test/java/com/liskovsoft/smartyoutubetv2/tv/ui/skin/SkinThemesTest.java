package com.liskovsoft.smartyoutubetv2.tv.ui.skin;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData.ColorScheme;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Every color scheme works in every skin, and the classic dialogs look as they always did
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
// The app's Conscrypt has no native library here, and no TLS is used
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SkinThemesTest {
    /**
     * A new color scheme can't ship without its look in the modern skin
     */
    @Test
    public void everyColorSchemeHasAModernOverlay() {
        for (ColorScheme scheme : MainUIData.instance(getContext()).getColorSchemes()) {
            assertNotEquals("Scheme " + scheme.id, 0, ModernColorScheme.getOverlayResId(scheme.id));
        }
    }

    @Test
    public void modernOverlaysDiffer() {
        List<Integer> overlays = new ArrayList<>();

        for (ColorScheme scheme : MainUIData.instance(getContext()).getColorSchemes()) {
            int overlay = ModernColorScheme.getOverlayResId(scheme.id);
            assertFalse("Scheme " + scheme.id, overlays.contains(overlay));
            overlays.add(overlay);
        }
    }

    /**
     * Behind the cards the hybrid skin has the background of the classic screens in every scheme (e.g. the blue of
     * Electric Blue), drawn by the window and the background manager.
     */
    @Test
    public void hybridCardScreensHaveTheClassicBackground() {
        for (ColorScheme scheme : MainUIData.instance(getContext()).getColorSchemes()) {
            int classicThemeResId = scheme.browseThemeResId > 0 ? scheme.browseThemeResId : R.style.App_Theme_Leanback_Browse;
            int expected = resolve(newTheme(classicThemeResId), R.attr.shelfBackground).data;

            Resources.Theme hybrid = newTheme(R.style.Theme_Modern);
            hybrid.applyStyle(ModernColorScheme.getOverlayResId(scheme.id), true);
            hybrid.applyStyle(R.style.ThemeOverlay_Modern_Hybrid, true);

            assertEquals("Scheme " + scheme.id, expected, resolve(hybrid, R.attr.shelfBackground).data);
            assertEquals("Scheme " + scheme.id, expected, resolve(hybrid, android.R.attr.windowBackground).data);
        }
    }

    /**
     * The modern skin keeps its own background behind the cards (the hybrid skin has the classic one)
     */
    @Test
    public void modernCardScreensKeepTheModernBackground() {
        for (ColorScheme scheme : MainUIData.instance(getContext()).getColorSchemes()) {
            Resources.Theme modern = newTheme(R.style.Theme_Modern);
            modern.applyStyle(ModernColorScheme.getOverlayResId(scheme.id), true);
            int expected = resolve(modern, R.attr.modernBackground).data;

            assertEquals("Scheme " + scheme.id, expected, resolve(modern, R.attr.shelfBackground).data);
            assertEquals("Scheme " + scheme.id, expected, resolve(modern, android.R.attr.windowBackground).data);
        }
    }

    /**
     * The text area of a hybrid card is the info field of the classic cards: its colors in every scheme, in the browse
     * screens and in the player (suggestions)
     */
    @Test
    public void cardTextAreasHaveTheClassicColors() {
        for (ColorScheme scheme : MainUIData.instance(getContext()).getColorSchemes()) {
            int classicThemeResId = scheme.browseThemeResId > 0 ? scheme.browseThemeResId : R.style.App_Theme_Leanback_Browse;
            Resources.Theme classic = newTheme(classicThemeResId);

            for (int modernThemeResId : new int[] {R.style.Theme_Modern, R.style.Theme_Modern_Player}) {
                Resources.Theme modern = newTheme(modernThemeResId);
                modern.applyStyle(ModernColorScheme.getOverlayResId(scheme.id), true);

                for (int attr : new int[] {R.attr.cardDefaultBackground, R.attr.cardSelectedBackground}) {
                    assertEquals("Scheme " + scheme.id, resolve(classic, attr).data, resolve(modern, attr).data);
                }
            }
        }
    }

    /**
     * The dialog layouts read these attributes: in the classic themes they're the values the layouts had before
     */
    @Test
    public void classicDialogsKeepTheirLook() {
        for (int themeResId : getClassicDialogThemes()) {
            Resources.Theme theme = newTheme(themeResId);
            String name = getContext().getResources().getResourceEntryName(themeResId);

            assertEquals(name, R.color.lb_preference_decor_list_background, resolveResId(theme, R.attr.modernPrefPanelBackground));
            assertEquals(name, R.color.lb_preference_item_primary_text_color, resolveResId(theme, R.attr.modernPrefItemTextColor));
            assertEquals(name, R.color.comments_bg, resolveResId(theme, R.attr.modernPrefChatBubble));
            assertEquals(name, R.color.comments_bg_pressed, resolveResId(theme, R.attr.modernPrefChatBubbleFocused));
            assertSameValue(name, theme, R.attr.defaultBrandColor, R.attr.modernPrefTitleBackground);
            assertSameValue(name, theme, android.R.attr.listChoiceIndicatorMultiple, R.attr.modernPrefCheckBox);
            assertSameValue(name, theme, android.R.attr.listChoiceIndicatorSingle, R.attr.modernPrefRadioButton);
            // The list rows take the theme's own radio button and checkbox, as the library's rows do
            assertSameValue(name, theme, android.R.attr.radioButtonStyle, R.attr.modernPrefRadioButtonStyle);
            assertSameValue(name, theme, android.R.attr.checkboxStyle, R.attr.modernPrefCheckBoxStyle);

            TypedValue duplicateState = resolve(theme, R.attr.modernPrefDuplicateState);
            assertEquals(name, TypedValue.TYPE_INT_BOOLEAN, duplicateState.type);
            assertEquals(name, 0, duplicateState.data);
        }
    }

    /**
     * The icons of the rows of Electric Eel (menu arrow, reorder handle) stay their white drawables in the classic themes, and take
     * the text color in the modern one
     */
    @Test
    public void rowIconsAreTintedInTheModernSkinOnly() {
        for (int themeResId : getClassicDialogThemes()) {
            String name = getContext().getResources().getResourceEntryName(themeResId);
            assertNull(name, getMenuArrow(themeResId).getImageTintList());
            assertNull(name, getReorderHandle(themeResId).getImageTintList());
        }

        assertNotNull(getMenuArrow(R.style.Theme_Modern_Preferences).getImageTintList());
        assertNotNull(getReorderHandle(R.style.Theme_Modern_Preferences).getImageTintList());
    }

    private static ImageView getMenuArrow(int themeResId) {
        ViewGroup row = (ViewGroup) LayoutInflater.from(new ContextThemeWrapper(getContext(), themeResId))
                .inflate(R.layout.dialog_list_preference_item_menu, null, false);
        return (ImageView) row.getChildAt(row.getChildCount() - 1);
    }

    private static ImageView getReorderHandle(int themeResId) {
        View row = LayoutInflater.from(new ContextThemeWrapper(getContext(), themeResId))
                .inflate(R.layout.dialog_list_preference_item_reorder, null, false);
        return row.findViewById(R.id.reorder_handle);
    }

    private static List<Integer> getClassicDialogThemes() {
        List<Integer> result = new ArrayList<>();
        result.add(R.style.App_Theme_Leanback_Preferences);
        result.add(R.style.App_Theme_Leanback_Preferences_Opaque);

        for (ColorScheme scheme : MainUIData.instance(getContext()).getColorSchemes()) {
            if (scheme.settingsThemeResId > 0 && !result.contains(scheme.settingsThemeResId)) {
                result.add(scheme.settingsThemeResId);
            }
        }

        return result;
    }

    private static void assertSameValue(String name, Resources.Theme theme, int expectedAttr, int attr) {
        TypedValue expected = resolve(theme, expectedAttr);
        TypedValue actual = resolve(theme, attr);

        assertEquals(name, expected.type, actual.type);
        assertEquals(name, expected.data, actual.data);
        assertEquals(name, expected.resourceId, actual.resourceId);
    }

    private static int resolveResId(Resources.Theme theme, int attr) {
        return resolve(theme, attr).resourceId;
    }

    private static TypedValue resolve(Resources.Theme theme, int attr) {
        TypedValue value = new TypedValue();
        assertTrue(theme.resolveAttribute(attr, value, true));
        return value;
    }

    private static Resources.Theme newTheme(int themeResId) {
        Resources.Theme theme = getContext().getResources().newTheme();
        theme.applyStyle(themeResId, true);
        return theme;
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }
}
