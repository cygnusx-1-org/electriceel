package com.liskovsoft.smartyoutubetv2.tv.ui.skin;

import android.app.Activity;

import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SettingsCardsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.AppDialogActivityOpaque;
import com.liskovsoft.smartyoutubetv2.tv.ui.keywords.KeywordWordsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.settings.ModernSettingsCardsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.qrcode.QrCodeActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.releasenotes.ReleaseNotesActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse.ModernBrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.channel.ModernChannelActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.channel.ModernChannelUploadsActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.dialogs.ModernDialogActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.playback.ModernPlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.search.ModernSearchActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.signin.ModernSignInActivity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The look and navigation of current TV streaming apps (tv.ui.modern, from SmartTube's PR #6334): a navigation rail, a top
 * bar, shelves, centered player controls. A view without an activity of its own keeps the classic one.
 */
public class ModernSkin implements Skin {
    static final Map<Class<?>, Class<? extends Activity>> ACTIVITIES = new HashMap<>();
    static final Map<Class<? extends Activity>, Class<? extends Activity>> ROOTS = new HashMap<>();
    // Shown with their classic activity, in the colors of this skin
    static final Set<Class<? extends Activity>> CLASSIC_SCREENS = new HashSet<>();

    static {
        ACTIVITIES.put(BrowseView.class, ModernBrowseActivity.class);
        ACTIVITIES.put(PlaybackView.class, ModernPlaybackActivity.class);
        ACTIVITIES.put(SignInView.class, ModernSignInActivity.class);
        ACTIVITIES.put(SearchView.class, ModernSearchActivity.class);
        ACTIVITIES.put(ChannelView.class, ModernChannelActivity.class);
        ACTIVITIES.put(ChannelUploadsView.class, ModernChannelUploadsActivity.class);
        ACTIVITIES.put(AppDialogView.class, ModernDialogActivity.class);
        ACTIVITIES.put(SettingsCardsView.class, ModernSettingsCardsActivity.class);
        ROOTS.put(BrowseActivity.class, ModernBrowseActivity.class);
        CLASSIC_SCREENS.add(QrCodeActivity.class);
        CLASSIC_SCREENS.add(ReleaseNotesActivity.class);
        CLASSIC_SCREENS.add(KeywordWordsActivity.class);
    }

    @Override
    public int getId() {
        return MainUIData.SKIN_MODERN;
    }

    @Override
    public int getNameResId() {
        return R.string.skin_modern;
    }

    @Override
    public Class<? extends Activity> resolve(Class<?> viewClass, Class<? extends Activity> classic) {
        // The Android 8 devices that crash on a translucent dialog keep the opaque one (see MainApplication.getDialogClass)
        if (classic == AppDialogActivityOpaque.class) {
            return classic;
        }

        Class<? extends Activity> modern = ACTIVITIES.get(viewClass);
        return modern != null ? modern : classic;
    }

    @Override
    public Class<? extends Activity> resolveRoot(Class<? extends Activity> classicRoot) {
        Class<? extends Activity> modern = ROOTS.get(classicRoot);
        return modern != null ? modern : classicRoot;
    }

    @Override
    public void onThemeApplied(Activity activity) {
        // The screens only the classic look has: the background of the modern skin, in the color scheme
        if (CLASSIC_SCREENS.contains(activity.getClass())) {
            ModernColorScheme.applyToClassicScreen(activity);
        }
    }

    @Override
    public boolean hasOwnSearchKeyboard() {
        // The search field is typed with the system keyboard, as the classic one (Search > Keyboard fix applies)
        return false;
    }

    @Override
    public boolean hasFixedCardSubtitle() {
        return true;
    }

    @Override
    public boolean showsListValuesAsSummary() {
        return true;
    }

    @Override
    public boolean keepsSectionOnChannelError() {
        return true;
    }

    @Override
    public boolean prefetchesFocusedVideo() {
        // Off until a measured start time justifies the requests it makes (see SKINS-PLAN.md, Phase 5)
        return false;
    }
}
