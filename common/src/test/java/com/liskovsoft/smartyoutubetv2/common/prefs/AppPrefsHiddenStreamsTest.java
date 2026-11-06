package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Hide streams from Subscriptions of Hide content is part of Streamed or Live now (see AppPrefs.migrateHiddenStreams)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AppPrefsHiddenStreamsTest {
    private static final String STREAMED_KEY = StreamedData.class.getSimpleName();
    private static final int HIDDEN = MediaServiceData.CONTENT_MIXES | MediaServiceData.CONTENT_STREAMS_SUBSCRIPTIONS;

    @Before
    public void setUp() {
        getPrefs().onAccountChanged(null); // the anonymous account
        clearSaved();
        // As at the start: they read the account's settings again when it changes
        HideContentData.instance(RuntimeEnvironment.getApplication());
        getStreamed();
    }

    @After
    public void tearDown() {
        Utils.sHandler.removeCallbacksAndMessages(null);
        getPrefs().onAccountChanged(null);
        clearSaved();
    }

    @Test
    public void checkedOptionHidesStreamsInSubscriptionsOnly() {
        saveHiddenContent(TestAccounts.FIRST.getName(), HIDDEN);

        getPrefs().onAccountChanged(TestAccounts.FIRST);

        StreamedData streamed = getStreamed();
        assertEquals(SectionFilterData.MODE_HIDE, streamed.getMode());
        assertTrue(streamed.isHidingEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
        assertFalse(streamed.isEnabled(MediaGroup.TYPE_HOME));
        assertFalse(streamed.isEnabled(MediaGroup.TYPE_HISTORY));
        // The option is gone, the rest of the hidden content stays
        assertFalse(getMedia().isContentHiddenInSettings(MediaServiceData.CONTENT_STREAMS_SUBSCRIPTIONS));
        assertTrue(getMedia().isContentHiddenInSettings(MediaServiceData.CONTENT_MIXES));
        assertEquals(MediaServiceData.CONTENT_MIXES, getSavedHiddenContent(TestAccounts.FIRST.getName()));
    }

    @Test
    public void pickedModeStaysAsItWasSet() {
        saveHiddenContent(TestAccounts.FIRST.getName(), HIDDEN);
        getPrefs().setData(TestAccounts.FIRST.getName() + "_" + STREAMED_KEY, Helpers.mergeData(SectionFilterData.MODE_MARK));

        getPrefs().onAccountChanged(TestAccounts.FIRST);

        StreamedData streamed = getStreamed();
        assertEquals(SectionFilterData.MODE_MARK, streamed.getMode());
        assertTrue(streamed.isMarkingEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
        assertTrue(streamed.isMarkingEnabled(MediaGroup.TYPE_HOME));
        assertFalse(getMedia().isContentHiddenInSettings(MediaServiceData.CONTENT_STREAMS_SUBSCRIPTIONS));
    }

    @Test
    public void eachAccountWhenItsSelected() {
        saveHiddenContent(TestAccounts.FIRST.getName(), HIDDEN);

        getPrefs().onAccountChanged(TestAccounts.SECOND);

        assertEquals(SectionFilterData.MODE_MARK, getStreamed().getMode());
        assertEquals(HIDDEN, getSavedHiddenContent(TestAccounts.FIRST.getName()));

        getPrefs().onAccountChanged(TestAccounts.FIRST);

        assertEquals(SectionFilterData.MODE_HIDE, getStreamed().getMode());

        getPrefs().onAccountChanged(TestAccounts.SECOND);

        assertEquals(SectionFilterData.MODE_MARK, getStreamed().getMode());
    }

    /**
     * E.g. from a device with a version before the option moved
     */
    @Test
    public void syncedOptionMovesToo() {
        getPrefs().onAccountChanged(TestAccounts.FIRST);
        assertEquals(SectionFilterData.MODE_MARK, getStreamed().getMode());

        saveHiddenContent(TestAccounts.FIRST.getName(), HIDDEN);
        getPrefs().onProfileDataSynced(Collections.singleton(HideContentData.HIDE_CONTENT_DATA));

        assertTrue(getStreamed().isHidingEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
        assertFalse(getStreamed().isEnabled(MediaGroup.TYPE_HOME));
        assertFalse(getMedia().isContentHiddenInSettings(MediaServiceData.CONTENT_STREAMS_SUBSCRIPTIONS));
        assertTrue(getMedia().isContentHiddenInSettings(MediaServiceData.CONTENT_MIXES));
    }

    @Test
    public void copiedOptionMovesToo() {
        saveHiddenContent(TestAccounts.FIRST.getName(), HIDDEN);
        getPrefs().onAccountChanged(TestAccounts.SECOND);

        getPrefs().copyProfileData(TestAccounts.FIRST);

        assertTrue(getStreamed().isHidingEnabled(MediaGroup.TYPE_SUBSCRIPTIONS));
        assertFalse(getMedia().isContentHiddenInSettings(MediaServiceData.CONTENT_STREAMS_SUBSCRIPTIONS));
        assertEquals(MediaServiceData.CONTENT_MIXES, getSavedHiddenContent(TestAccounts.SECOND.getName()));
    }

    @Test
    public void nothingChangesWithoutTheOption() {
        saveHiddenContent(TestAccounts.FIRST.getName(), MediaServiceData.CONTENT_MIXES);

        getPrefs().onAccountChanged(TestAccounts.FIRST);
        ShadowLooper.shadowMainLooper().idle();

        assertEquals(SectionFilterData.MODE_MARK, getStreamed().getMode());
        assertTrue(getStreamed().isMarkingEnabled(MediaGroup.TYPE_HOME));
        assertTrue(getMedia().isContentHiddenInSettings(MediaServiceData.CONTENT_MIXES));
        assertEquals("", getPrefs().getData(TestAccounts.FIRST.getName() + "_" + STREAMED_KEY));
    }

    private static void saveHiddenContent(String profileName, int hiddenContent) {
        getPrefs().setData(profileName + "_" + HideContentData.HIDE_CONTENT_DATA, Helpers.mergeData(hiddenContent, false, false));
    }

    private static int getSavedHiddenContent(String profileName) {
        return Helpers.parseInt(Helpers.splitData(getPrefs().getData(profileName + "_" + HideContentData.HIDE_CONTENT_DATA)), 0, -1);
    }

    /**
     * AppPrefs outlives the test, so do the saved values. So does MediaServiceData, which doesn't read its saved values in the tests.
     */
    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), HideContentData.HIDE_CONTENT_DATA);
        TestAccounts.clearSaved(getPrefs(), STREAMED_KEY);
        HideContentData.resetInstanceForTesting();
        StreamedData.resetInstanceForTesting();
        getMedia().setHiddenContentInSettings(MediaServiceData.CONTENT_HIDDEN_DEFAULT);
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static StreamedData getStreamed() {
        return StreamedData.instance(RuntimeEnvironment.getApplication());
    }

    private static MediaServiceData getMedia() {
        return MediaServiceData.instance();
    }
}
