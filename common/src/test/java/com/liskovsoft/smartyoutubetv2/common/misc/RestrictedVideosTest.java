package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.videoinfo.V2.VideoInfoService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * In Child mode the formats of a video to play are fetched without the account, so a video only a signed in adult may watch (18+)
 * isn't played (see MediaServiceManager#getFormatInfoObserve). The fetch itself isn't run: the observable is only made.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RestrictedVideosTest {
    private GeneralData mGeneralData;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        mGeneralData = GeneralData.instance(context);
    }

    @After
    public void tearDown() {
        mGeneralData.setChildModeEnabled(false);
        VideoInfoService.instance().setRestrictedVideosAllowed(true);
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void childModeFetchesWithoutTheAccount() {
        mGeneralData.setChildModeEnabled(true);

        MediaServiceManager.getFormatInfoObserve("video");

        assertFalse(VideoInfoService.instance().isRestrictedVideosAllowed());
    }

    /**
     * Child mode turned off: the next video is fetched with the account again
     */
    @Test
    public void withoutChildModeFetchesWithTheAccount() {
        mGeneralData.setChildModeEnabled(true);
        MediaServiceManager.getFormatInfoObserve("video");

        mGeneralData.setChildModeEnabled(false);
        MediaServiceManager.getFormatInfoObserve("video");

        assertTrue(VideoInfoService.instance().isRestrictedVideosAllowed());
    }
}
