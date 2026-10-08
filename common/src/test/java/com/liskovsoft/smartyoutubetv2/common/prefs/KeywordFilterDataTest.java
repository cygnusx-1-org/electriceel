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

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.addKeyword("reaction");

        prefs.onAccountChanged(TestAccounts.SECOND);

        // Starts with no keywords, not the other account's
        assertTrue(data.getKeywords().isEmpty());

        data.addKeyword("prank");

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertEquals(Collections.singletonList("reaction"), data.getKeywords());
        assertFalse(data.isHidden("Best prank"));

        prefs.onAccountChanged(TestAccounts.SECOND);

        assertEquals(Collections.singletonList("prank"), data.getKeywords());
        assertTrue(data.isHidden("Best prank"));
    }

    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), DATA_KEY);
        KeywordFilterData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static KeywordFilterData getData() {
        return KeywordFilterData.instance(RuntimeEnvironment.getApplication());
    }
}
