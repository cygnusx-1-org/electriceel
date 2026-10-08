package com.liskovsoft.smartyoutubetv2.common.prefs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SearchDataTest {
    private static final String SEARCH_DATA = "search_data";

    @Before
    public void setUp() {
        clearSaved();
    }

    @After
    public void tearDown() {
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

    /**
     * AppPrefs outlives the test, so do the saved values
     */
    private static void clearSaved() {
        getPrefs().setData(SEARCH_DATA, "");
        SearchData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static SearchData getData() {
        return SearchData.instance(RuntimeEnvironment.getApplication());
    }
}
