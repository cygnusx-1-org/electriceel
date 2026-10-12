package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SearchDataTest {
    private static final String SEARCH_DATA = "search_data";

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
    public void keyboardShowsAutomaticallyByDefault() {
        assertTrue(getData().isKeyboardAutoShowEnabled());
    }

    @Test
    public void keyboardAutoShowOffSurvivesRestart() {
        getData().setKeyboardAutoShowEnabled(false);
        SearchData.resetInstanceForTesting();

        assertFalse(getData().isKeyboardAutoShowEnabled());
    }

    /**
     * Every value is saved when any one is set
     */
    @Test
    public void keyboardAutoShowStaysOnWhenAnotherValueIsSaved() {
        getData().setFocusOnResultsEnabled(false);
        SearchData.resetInstanceForTesting();

        assertTrue(getData().isKeyboardAutoShowEnabled());
        assertFalse(getData().isFocusOnResultsEnabled());
    }

    @Test
    public void eachAccountHasItsOwnValues() {
        AppPrefs prefs = getPrefs();
        SearchData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.setKeyboardAutoShowEnabled(false);
        data.setSpeechRecognizerType(SearchData.SPEECH_RECOGNIZER_GOTEV);

        prefs.onAccountChanged(TestAccounts.SECOND);

        // Starts from the defaults, not the other account's
        assertTrue(data.isKeyboardAutoShowEnabled());
        assertEquals(SearchData.SPEECH_RECOGNIZER_SYSTEM, data.getSpeechRecognizerType());

        data.setSearchHistoryDisabled(true);

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertFalse(data.isKeyboardAutoShowEnabled());
        assertEquals(SearchData.SPEECH_RECOGNIZER_GOTEV, data.getSpeechRecognizerType());
        assertFalse(data.isSearchHistoryDisabled());

        prefs.onAccountChanged(TestAccounts.SECOND);

        assertTrue(data.isSearchHistoryDisabled());
    }

    /**
     * AppPrefs outlives the test, so do the saved values
     */
    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), SEARCH_DATA);
        SearchData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static SearchData getData() {
        return SearchData.instance(RuntimeEnvironment.getApplication());
    }
}
