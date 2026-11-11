package com.liskovsoft.smartyoutubetv2.tv.ui.skin;

import android.app.Activity;

import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.BrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse.ModernBrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.playback.ModernPlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ModernSkinTest {
    private final Skin mSkin = new ModernSkin();

    @Test
    public void viewWithAModernActivityResolvesToIt() {
        assertEquals(ModernBrowseActivity.class, mSkin.resolve(BrowseView.class, BrowseActivity.class));
        assertEquals(ModernPlaybackActivity.class, mSkin.resolve(PlaybackView.class, PlaybackActivity.class));
    }

    @Test
    public void viewWithoutAModernActivityKeepsTheClassicOne() {
        assertEquals(SplashActivity.class, mSkin.resolve(SplashView.class, SplashActivity.class));
    }

    @Test
    public void rootIsTheModernBrowse() {
        assertEquals(ModernBrowseActivity.class, mSkin.resolveRoot(BrowseActivity.class));
    }

    @Test
    public void unknownRootIsKept() {
        assertEquals(OtherActivity.class, mSkin.resolveRoot(OtherActivity.class));
    }

    /**
     * The hybrid skin is the modern screens in another look
     */
    @Test
    public void hybridHasTheModernActivities() {
        Skin hybrid = new HybridSkin();

        assertEquals(MainUIData.SKIN_HYBRID, hybrid.getId());
        assertEquals(ModernBrowseActivity.class, hybrid.resolve(BrowseView.class, BrowseActivity.class));
        assertEquals(ModernPlaybackActivity.class, hybrid.resolve(PlaybackView.class, PlaybackActivity.class));
        assertEquals(SplashActivity.class, hybrid.resolve(SplashView.class, SplashActivity.class));
        assertEquals(ModernBrowseActivity.class, hybrid.resolveRoot(BrowseActivity.class));
    }

    @Test
    public void classicResolvesEverythingToTheClassicActivity() {
        Skin classic = new ClassicSkin();

        assertEquals(BrowseActivity.class, classic.resolve(BrowseView.class, BrowseActivity.class));
        assertEquals(BrowseActivity.class, classic.resolveRoot(BrowseActivity.class));
    }

    private static class OtherActivity extends Activity {
    }
}
