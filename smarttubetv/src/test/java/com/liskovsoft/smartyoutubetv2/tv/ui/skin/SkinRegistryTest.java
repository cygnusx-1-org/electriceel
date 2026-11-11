package com.liskovsoft.smartyoutubetv2.tv.ui.skin;

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.Skins;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
// The app's Conscrypt has no native library here, and no TLS is used
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SkinRegistryTest {
    private static final int SECOND_SKIN = 7;
    private static final int THIRD_SKIN = 8;
    private SkinRegistry mRegistry;

    @Before
    public void setUp() {
        mRegistry = new SkinRegistry(Arrays.asList(new ClassicSkin(), new SecondSkin(), new ThirdSkin()));
        mRegistry.setClassicRoot(BrowseActivity.class);
        mRegistry.addView(SplashView.class, SplashActivity.class, true);
        mRegistry.addView(BrowseView.class, BrowseActivity.class, true);
        mRegistry.addView(PlaybackView.class, PlaybackActivity.class, false);
    }

    @After
    public void tearDown() {
        pick(MainUIData.SKIN_CLASSIC);
        Skins.setProvider(null);
    }

    @Test
    public void unknownSkinIsClassic() {
        assertEquals(MainUIData.SKIN_CLASSIC, mRegistry.getSkin(99).getId());
    }

    @Test
    public void classicSkinIsAppliedByDefault() {
        mRegistry.init(getContext());

        assertEquals(MainUIData.SKIN_CLASSIC, Skins.getAppliedSkinId());
        assertSame(BrowseActivity.class, getViewManager().getRootActivity());
        assertSame(PlaybackActivity.class, getViewManager().getActivity(PlaybackView.class));
    }

    @Test
    public void skinPickedIsAppliedAtStart() {
        pick(SECOND_SKIN);
        mRegistry.init(getContext());

        assertEquals(SECOND_SKIN, Skins.getAppliedSkinId());
        assertSame(SecondBrowseActivity.class, getViewManager().getRootActivity());
        assertSame(SecondBrowseActivity.class, getViewManager().getActivity(BrowseView.class));
        assertSame(SecondPlaybackActivity.class, getViewManager().getActivity(PlaybackView.class));
        // A view the skin has no activity for keeps the classic one
        assertSame(SplashActivity.class, getViewManager().getActivity(SplashView.class));
    }

    /**
     * The activities of the old skin are no longer registered: a parent mapped for one would match the new one that extends it
     */
    @Test
    public void skinPickedLaterReplacesTheRegisteredActivities() {
        mRegistry.init(getContext());
        pick(SECOND_SKIN);
        Skins.applyPickedSkin(getContext());

        assertEquals(SECOND_SKIN, Skins.getAppliedSkinId());
        assertSame(SecondPlaybackActivity.class, getViewManager().getActivity(PlaybackView.class));
        assertNull(getViewManager().getView(PlaybackActivity.class));
        assertNull(getViewManager().getView(BrowseActivity.class));
    }

    @Test
    public void activityOfTheOldSkinIsReplacedWithTheNewOne() {
        mRegistry.init(getContext());
        pick(SECOND_SKIN);
        Skins.applyPickedSkin(getContext());

        assertSame(SecondBrowseActivity.class, mRegistry.getReplacement(BrowseActivity.class));
        assertSame(SecondPlaybackActivity.class, mRegistry.getReplacement(PlaybackActivity.class));
    }

    @Test
    public void activityOfTheAppliedSkinIsKept() {
        mRegistry.init(getContext());
        pick(SECOND_SKIN);
        Skins.applyPickedSkin(getContext());

        assertNull(mRegistry.getReplacement(SecondBrowseActivity.class));
        // The same in both skins
        assertNull(mRegistry.getReplacement(SplashActivity.class));
    }

    @Test
    public void backToClassicReplacesTheSecondSkinsActivities() {
        pick(SECOND_SKIN);
        mRegistry.init(getContext());
        pick(MainUIData.SKIN_CLASSIC);
        Skins.applyPickedSkin(getContext());

        assertEquals(MainUIData.SKIN_CLASSIC, Skins.getAppliedSkinId());
        assertSame(BrowseActivity.class, mRegistry.getReplacement(SecondBrowseActivity.class));
        assertSame(BrowseActivity.class, getViewManager().getRootActivity());
    }

    /**
     * Two skins with the same activities (the modern and the hybrid one): they stay, recreated in the new look (see
     * MotherActivity), never replaced
     */
    @Test
    public void skinWithTheSameActivitiesKeepsThem() {
        pick(SECOND_SKIN);
        mRegistry.init(getContext());
        pick(THIRD_SKIN);
        Skins.applyPickedSkin(getContext());

        assertEquals(THIRD_SKIN, Skins.getAppliedSkinId());
        assertNull(mRegistry.getReplacement(SecondBrowseActivity.class));
        assertNull(mRegistry.getReplacement(SecondPlaybackActivity.class));
        assertSame(SecondBrowseActivity.class, getViewManager().getRootActivity());
        assertSame(SecondPlaybackActivity.class, getViewManager().getActivity(PlaybackView.class));
    }

    @Test
    public void skinsAreListedInOrder() {
        mRegistry.init(getContext());

        assertEquals(Arrays.asList(MainUIData.SKIN_CLASSIC, SECOND_SKIN, THIRD_SKIN), Skins.getSkinIds());
        assertEquals(R.string.skin_classic, Skins.getSkinNameResId(MainUIData.SKIN_CLASSIC));
    }

    private static void pick(int skinId) {
        MainUIData.instance(getContext()).setSkinId(skinId);
        ShadowLooper.shadowMainLooper().idle();
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }

    private static ViewManager getViewManager() {
        return ViewManager.instance(getContext());
    }

    public static class SecondBrowseActivity extends BrowseActivity {
    }

    public static class SecondPlaybackActivity extends PlaybackActivity {
    }

    private static class SecondSkin extends ClassicSkin {
        @Override
        public int getId() {
            return SECOND_SKIN;
        }

        @Override
        public Class<? extends Activity> resolve(Class<?> viewClass, Class<? extends Activity> classic) {
            if (viewClass == BrowseView.class) {
                return SecondBrowseActivity.class;
            }

            if (viewClass == PlaybackView.class) {
                return SecondPlaybackActivity.class;
            }

            return classic;
        }

        @Override
        public Class<? extends Activity> resolveRoot(Class<? extends Activity> classicRoot) {
            return SecondBrowseActivity.class;
        }
    }

    private static class ThirdSkin extends SecondSkin {
        @Override
        public int getId() {
            return THIRD_SKIN;
        }
    }
}
