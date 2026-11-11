package com.liskovsoft.smartyoutubetv2.tv.ui.skin;

import android.app.Activity;

import com.liskovsoft.smartyoutubetv2.common.misc.SkinTraits;

/**
 * A look and navigation of the app (User interface > Skin): the activities that show the views, with their layouts and themes.
 * The presenters are the same in every skin.
 */
public interface Skin extends SkinTraits {
    /**
     * Persisted (MainUIData.SKIN_*): never change or reuse one
     */
    int getId();

    int getNameResId();

    /**
     * @param classic the activity of the classic look for the view
     * @return the activity that shows the view in this skin, the classic one when the skin has none of its own
     */
    Class<? extends Activity> resolve(Class<?> viewClass, Class<? extends Activity> classic);

    /**
     * @return the activity the others go back to in this skin
     */
    Class<? extends Activity> resolveRoot(Class<? extends Activity> classicRoot);

    /**
     * The theme of a classic activity is set (the color scheme's): this skin can adapt it, for a screen only the classic look
     * has, so it doesn't look like another app
     */
    void onThemeApplied(Activity activity);
}
