package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class KeywordFilterDataTest {
    private static final String DATA_KEY = "keyword_filter_data";
    private static final String ANONYMOUS_PROFILE_KEY = "anonymous_" + DATA_KEY;

    @Before
    public void setUp() {
        getPrefs().enableMultiProfiles(false);
        clearSaved();
    }

    @After
    public void tearDown() {
        Utils.sHandler.removeCallbacksAndMessages(null);
        getPrefs().enableMultiProfiles(false);
        clearSaved();
    }

    @Test
    public void emptyByDefault() {
        KeywordFilterData data = getData();

        assertTrue(data.isEmpty());
        assertFalse(data.isHidden("Anything at all"));
    }

    @Test
    public void keywordsAreSorted() {
        KeywordFilterData data = getData();

        data.addKeyword("unboxing");
        data.addKeyword("Asmr");
        data.addKeyword("tier list");

        assertEquals(Arrays.asList("asmr", "tier list", "unboxing"), data.getKeywords());
    }

    @Test
    public void keywordsSurviveRestart() {
        KeywordFilterData data = getData();

        data.addKeyword("reaction");
        data.addKeyword("Tier List");
        data.addKeyword("null"); // a word, not a missing value
        KeywordFilterData.resetInstanceForTesting();

        KeywordFilterData restored = getData();

        assertEquals(Arrays.asList("null", "reaction", "tier list"), restored.getKeywords());
        assertTrue(restored.isHidden("My tier list"));
        assertTrue(restored.isHidden("REACTION"));
    }

    @Test
    public void removingTheLastKeywordIsSaved() {
        KeywordFilterData data = getData();

        data.addKeyword("reaction");
        data.removeKeyword("reaction");
        KeywordFilterData.resetInstanceForTesting();

        assertTrue(getData().isEmpty());
    }

    @Test
    public void eachAccountHasItsOwnKeywords() {
        AppPrefs prefs = getPrefs();
        KeywordFilterData data = getData();

        data.addKeyword("reaction");

        prefs.enableMultiProfiles(true);
        prefs.onAccountChanged(null); // the anonymous account

        // Starts from the shared keywords
        assertEquals(Collections.singletonList("reaction"), data.getKeywords());

        data.addKeyword("prank");

        prefs.enableMultiProfiles(false);

        assertEquals(Collections.singletonList("reaction"), data.getKeywords());
        assertFalse(data.isHidden("Best prank"));

        prefs.enableMultiProfiles(true);

        assertEquals(Arrays.asList("prank", "reaction"), data.getKeywords());
        assertTrue(data.isHidden("Best prank"));
    }

    /**
     * AppPrefs outlives the test, so do the saved values
     */
    private static void clearSaved() {
        getPrefs().setData(DATA_KEY, "");
        getPrefs().setData(ANONYMOUS_PROFILE_KEY, "");
        KeywordFilterData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static KeywordFilterData getData() {
        return KeywordFilterData.instance(RuntimeEnvironment.getApplication());
    }
}
