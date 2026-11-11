package com.liskovsoft.smartyoutubetv2.tv.ui.skin;

import android.app.Activity;

import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * The look the app always had: every view is shown by its classic activity
 */
public class ClassicSkin implements Skin {
    @Override
    public int getId() {
        return MainUIData.SKIN_CLASSIC;
    }

    @Override
    public int getNameResId() {
        return R.string.skin_classic;
    }

    @Override
    public Class<? extends Activity> resolve(Class<?> viewClass, Class<? extends Activity> classic) {
        return classic;
    }

    @Override
    public Class<? extends Activity> resolveRoot(Class<? extends Activity> classicRoot) {
        return classicRoot;
    }

    @Override
    public void onThemeApplied(Activity activity) {
        // NOP: the classic themes
    }

    @Override
    public boolean hasOwnSearchKeyboard() {
        return false;
    }

    @Override
    public boolean hasFixedCardSubtitle() {
        return false;
    }

    @Override
    public boolean showsListValuesAsSummary() {
        return false;
    }

    @Override
    public boolean keepsSectionOnChannelError() {
        return false;
    }

    @Override
    public boolean prefetchesFocusedVideo() {
        return false;
    }
}
