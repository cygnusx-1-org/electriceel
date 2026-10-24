package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DebuggingDataTest {
    @Before
    public void setUp() {
        getPrefs().onAccountChanged(null); // the anonymous account
        clearSaved();
    }

    @After
    public void tearDown() {
        Utils.sHandler.removeCallbacksAndMessages(null);
        getPrefs().onAccountChanged(null);
        clearSaved();
    }

    @Test
    public void videoIdsAreIncludedByDefault() {
        assertTrue(getData().isVideoIdsIncluded());
    }

    @Test
    public void autoSubmitIsOffByDefault() {
        assertFalse(getData().isAutoSubmitEnabled());

        // Not after the other values are set either (see DataSaverBase#checkCapacity)
        getData().getAccountNumber(TestAccounts.FIRST);
        getData().setLastSubmission("K7M2QX9P", 123, 456);
        ShadowLooper.shadowMainLooper().idle();
        DebuggingData.resetInstanceForTesting();

        assertFalse(getData().isAutoSubmitEnabled());
        assertTrue(getData().isVideoIdsIncluded());
    }

    @Test
    public void theLastSubmissionIsKept() {
        assertNull(getData().getLastSubmissionId());
        assertEquals(0, getData().getLastSubmittedFailureTs());

        getData().setAutoSubmitEnabled(true);
        getData().setLastSubmission("K7M2QX9P", 1_792_000_000_000L, 1_792_000_100_000L);
        ShadowLooper.shadowMainLooper().idle();
        DebuggingData.resetInstanceForTesting();

        assertTrue(getData().isAutoSubmitEnabled());
        assertEquals("K7M2QX9P", getData().getLastSubmissionId());
        assertEquals(1_792_000_000_000L, getData().getLastSubmittedFailureTs());
        assertEquals(1_792_000_100_000L, getData().getLastSubmissionTime());
    }

    @Test
    public void theSameForEveryAccount() {
        AppPrefs prefs = getPrefs();

        prefs.onAccountChanged(TestAccounts.FIRST);
        getData().setVideoIdsIncluded(false);
        ShadowLooper.shadowMainLooper().idle();

        prefs.onAccountChanged(TestAccounts.SECOND);
        assertFalse(getData().isVideoIdsIncluded());

        prefs.onAccountChanged(null);
        assertFalse(getData().isVideoIdsIncluded());
    }

    @Test
    public void keptOverARestart() {
        getData().setVideoIdsIncluded(false);
        getData().getAccountNumber(TestAccounts.FIRST);
        getData().getAccountNumber(TestAccounts.SECOND);
        ShadowLooper.shadowMainLooper().idle();

        DebuggingData.resetInstanceForTesting();

        assertFalse(getData().isVideoIdsIncluded());
        assertEquals(1, getData().getAccountNumber(TestAccounts.FIRST));
        assertEquals(2, getData().getAccountNumber(TestAccounts.SECOND));
    }

    @Test
    public void accountsAreNumberedInTheOrderFirstSeen() {
        DebuggingData data = getData();

        assertEquals(0, data.getAccountNumber(null));
        assertEquals(1, data.getAccountNumber(TestAccounts.SECOND));
        assertEquals(2, data.getAccountNumber(TestAccounts.FIRST));
        assertEquals(1, data.getAccountNumber(TestAccounts.SECOND));
        assertEquals(3, data.getAccountNumber(TestAccounts.named("Third")));
        assertEquals(2, data.getAccountNumber(TestAccounts.FIRST));
    }

    @Test
    public void theAccountNamesArentSaved() {
        getData().getAccountNumber(TestAccounts.withProfile("Some Name", "someone@example.com", "Some_Profile_Name", null));
        ShadowLooper.shadowMainLooper().idle();

        String saved = getPrefs().getData(DebuggingData.class.getSimpleName());

        assertFalse(saved, saved.contains("Some"));
        assertFalse(saved, saved.contains("someone"));
    }

    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), DebuggingData.class.getSimpleName());
        DebuggingData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static DebuggingData getData() {
        return DebuggingData.instance(RuntimeEnvironment.getApplication());
    }
}
