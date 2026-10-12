package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The ones in use are in MediaServiceData, each account has its own copy
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HideContentDataTest {
    private static final String SIDEBAR_DATA = "sidebar_data"; // see AppPrefs
    private static final String PREFS_DIR = "app_prefs"; // see AppPrefs
    private static final Account ADDED = TestAccounts.named("Added later"); // nothing saved under it

    @Before
    public void setUp() {
        getPrefs().onAccountChanged(null); // the anonymous account
        clearSaved();
        getData(); // the account's, as at the start
    }

    @After
    public void tearDown() {
        Utils.sHandler.removeCallbacksAndMessages(null);
        getPrefs().onAccountChanged(null);
        clearSaved();
        // As in the other tests: MediaServiceData doesn't read its saved values in them
        getMediaServiceData().setHiddenContentInSettings(0);
    }

    @Test
    public void eachAccountHasItsOwnHiddenContent() {
        AppPrefs prefs = getPrefs();
        MediaServiceData media = getMediaServiceData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        getData().setContentHidden(MediaServiceData.CONTENT_MIXES, true);

        prefs.onAccountChanged(TestAccounts.SECOND);

        // Starts from the defaults, not the other account's
        assertEquals(MediaServiceData.CONTENT_HIDDEN_DEFAULT, media.getHiddenContentInSettings());

        getData().setContentHidden(MediaServiceData.CONTENT_SHORTS_HOME, true);

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertTrue(media.isContentHiddenInSettings(MediaServiceData.CONTENT_MIXES));
        assertFalse(media.isContentHiddenInSettings(MediaServiceData.CONTENT_SHORTS_HOME));

        prefs.onAccountChanged(TestAccounts.SECOND);

        assertFalse(media.isContentHiddenInSettings(MediaServiceData.CONTENT_MIXES));
        assertTrue(media.isContentHiddenInSettings(MediaServiceData.CONTENT_SHORTS_HOME));
    }

    @Test
    public void eachAccountHasItsOwnShortsQuickToggle() {
        AppPrefs prefs = getPrefs();
        MediaServiceData media = getMediaServiceData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        getData().setShortsQuickToggleEnabled(true);
        getData().setShortsQuickToggleHiding(true);

        prefs.onAccountChanged(TestAccounts.SECOND);

        assertFalse(media.isShortsQuickToggleEnabled());
        assertFalse(media.isShortsQuickToggleHiding());

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertTrue(media.isShortsQuickToggleEnabled());
        assertTrue(media.isShortsQuickToggleHiding());
    }

    @Test
    public void copiedSettingsAreTheOnesInUse() {
        AppPrefs prefs = getPrefs();
        MediaServiceData media = getMediaServiceData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        getData().setContentHidden(MediaServiceData.CONTENT_MIXES, true);
        prefs.onAccountChanged(TestAccounts.SECOND);

        prefs.copyProfileData(TestAccounts.FIRST);

        assertTrue(media.isContentHiddenInSettings(MediaServiceData.CONTENT_MIXES));
    }

    /**
     * A version before this one: the ones in use were for all the accounts, each account that has been used keeps them
     */
    @Test
    public void hiddenContentInUseStaysWithEachAccount() {
        AppPrefs prefs = getPrefs();
        MediaServiceData media = getMediaServiceData();
        prefs.onAccountChanged(TestAccounts.FIRST);
        prefs.setData(TestAccounts.FIRST.getName() + "_" + SIDEBAR_DATA, "first's"); // used before
        prefs.onAccountChanged(TestAccounts.SECOND);
        media.setContentHidden(MediaServiceData.CONTENT_MIXES, true);
        HideContentData.resetInstanceForTesting();
        // Never saved
        new File(FileHelpers.getFilesDir(prefs.getContext()), PREFS_DIR + "/" + HideContentData.HIDE_CONTENT_DATA).delete();

        getData(); // the app starts

        assertTrue(media.isContentHiddenInSettings(MediaServiceData.CONTENT_MIXES));

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertTrue(media.isContentHiddenInSettings(MediaServiceData.CONTENT_MIXES));

        prefs.onAccountChanged(ADDED);

        assertEquals(MediaServiceData.CONTENT_HIDDEN_DEFAULT, media.getHiddenContentInSettings());
    }

    private static void clearSaved() {
        AppPrefs prefs = getPrefs();
        TestAccounts.clearSaved(prefs, HideContentData.HIDE_CONTENT_DATA);
        TestAccounts.clearSaved(prefs, SIDEBAR_DATA);
        HideContentData.resetInstanceForTesting();
        MediaServiceData media = getMediaServiceData();
        media.setHiddenContentInSettings(MediaServiceData.CONTENT_HIDDEN_DEFAULT);
        media.setShortsQuickToggleEnabled(false);
        media.setShortsQuickToggleHiding(false);
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static HideContentData getData() {
        return HideContentData.instance(RuntimeEnvironment.getApplication());
    }

    private static MediaServiceData getMediaServiceData() {
        return MediaServiceData.instance();
    }
}
