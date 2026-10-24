package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.util.Pair;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FilterExclusionDataTest {
    private static final String DATA_KEY = "filter_exclusion_data";
    private static final int STREAMED = FilterExclusionData.FILTER_STREAMED;
    private static final int WATCH_LATER = FilterExclusionData.FILTER_WATCH_LATER;
    private static final int COLLABORATIONS = FilterExclusionData.FILTER_COLLABORATIONS;

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
    public void nothingIsExcludedByDefault() {
        FilterExclusionData data = getData();

        assertFalse(data.isExcluded(STREAMED, "UCclimbing", "World Climbing"));
        assertTrue(data.getChannels(STREAMED).isEmpty());
    }

    @Test
    public void channelIsExcludedOnlyFromThePickedFilters() {
        FilterExclusionData data = getData();

        data.setExcluded(STREAMED, "UCclimbing", "World Climbing", true);

        assertTrue(data.isExcluded(STREAMED, "UCclimbing", "World Climbing"));
        assertFalse(data.isExcluded(WATCH_LATER, "UCclimbing", "World Climbing"));
        assertFalse(data.isExcluded(COLLABORATIONS, "UCclimbing", "World Climbing"));
        assertFalse(data.isExcluded(STREAMED, "UCother", "Other"));
    }

    /**
     * Most TV cards have only the channel name
     */
    @Test
    public void channelIsFoundByItsNameWhenTheCardHasNoId() {
        FilterExclusionData data = getData();

        data.setExcluded(STREAMED, "UCclimbing", "World Climbing", true);

        assertTrue(data.isExcluded(STREAMED, null, "World Climbing"));
        assertFalse(data.isExcluded(STREAMED, null, "world climbing"));
    }

    /**
     * Another channel of the same name, or the channel after it was renamed
     */
    @Test
    public void idWinsOverTheName() {
        FilterExclusionData data = getData();

        data.setExcluded(STREAMED, "UCclimbing", "World Climbing", true);

        assertFalse(data.isExcluded(STREAMED, "UCimpostor", "World Climbing"));
        assertTrue(data.isExcluded(STREAMED, "UCclimbing", "IFSC"));
    }

    /**
     * Excluded from a card without the id, it's found by the id once a card with it is excluded too
     */
    @Test
    public void idIsAddedLater() {
        FilterExclusionData data = getData();

        data.setExcluded(STREAMED, null, "World Climbing", true);
        data.setExcluded(WATCH_LATER, "UCclimbing", "World Climbing", true);

        assertTrue(data.isExcluded(STREAMED, "UCclimbing", "IFSC"));
        assertEquals(Collections.singletonList("UCclimbing"), getIds(data.getChannels(STREAMED)));
    }

    @Test
    public void channelIsIncludedAgain() {
        FilterExclusionData data = getData();

        data.setExcluded(STREAMED, "UCclimbing", "World Climbing", true);
        data.setExcluded(WATCH_LATER, "UCclimbing", "World Climbing", true);
        data.setExcluded(STREAMED, "UCclimbing", "World Climbing", false);

        assertFalse(data.isExcluded(STREAMED, "UCclimbing", "World Climbing"));
        assertTrue(data.isExcluded(WATCH_LATER, "UCclimbing", "World Climbing"));
        assertTrue(data.getChannels(STREAMED).isEmpty());

        data.setExcluded(WATCH_LATER, null, "World Climbing", false);

        assertTrue(data.getChannels(WATCH_LATER).isEmpty());
    }

    @Test
    public void channelsOfAFilterAreByName() {
        FilterExclusionData data = getData();

        data.setExcluded(STREAMED, "UCifsc", "World Climbing", true);
        data.setExcluded(STREAMED, "UCbouldering", "bouldering league", true);
        data.setExcluded(WATCH_LATER, "UCcooking", "Cooking", true);
        data.setExcluded(STREAMED, "UCalpine", null, true);

        assertEquals(Arrays.asList("bouldering league", "UCalpine", "World Climbing"), getTitles(data.getChannels(STREAMED)));
        assertEquals(Collections.singletonList("Cooking"), getTitles(data.getChannels(WATCH_LATER)));
    }

    @Test
    public void exclusionsSurviveRestart() {
        FilterExclusionData data = getData();

        data.setExcluded(STREAMED, "UCclimbing", "World Climbing", true);
        data.setExcluded(COLLABORATIONS, null, "Sidemen", true);
        FilterExclusionData.resetInstanceForTesting();

        FilterExclusionData restored = getData();

        assertTrue(restored.isExcluded(STREAMED, null, "World Climbing"));
        assertTrue(restored.isExcluded(COLLABORATIONS, "UCsidemen", "Sidemen"));
        assertFalse(restored.isExcluded(STREAMED, null, "Sidemen"));
    }

    @Test
    public void includingTheLastChannelIsSaved() {
        FilterExclusionData data = getData();

        data.setExcluded(STREAMED, "UCclimbing", "World Climbing", true);
        data.setExcluded(STREAMED, "UCclimbing", "World Climbing", false);
        FilterExclusionData.resetInstanceForTesting();

        assertTrue(getData().getChannels(STREAMED).isEmpty());
    }

    @Test
    public void eachAccountHasItsOwnChannels() {
        AppPrefs prefs = getPrefs();
        FilterExclusionData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.setExcluded(STREAMED, "UCclimbing", "World Climbing", true);

        prefs.onAccountChanged(TestAccounts.SECOND);

        // Starts with none, not the other account's
        assertFalse(data.isExcluded(STREAMED, "UCclimbing", "World Climbing"));

        data.setExcluded(STREAMED, "UCcooking", "Cooking", true);

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertTrue(data.isExcluded(STREAMED, "UCclimbing", "World Climbing"));
        assertFalse(data.isExcluded(STREAMED, "UCcooking", "Cooking"));

        prefs.onAccountChanged(TestAccounts.SECOND);

        assertEquals(Collections.singletonList("Cooking"), getTitles(data.getChannels(STREAMED)));
    }

    private static List<String> getTitles(List<Pair<String, String>> channels) {
        List<String> titles = new ArrayList<>();

        for (Pair<String, String> channel : channels) {
            titles.add(channel.second != null ? channel.second : channel.first);
        }

        return titles;
    }

    private static List<String> getIds(List<Pair<String, String>> channels) {
        List<String> ids = new ArrayList<>();

        for (Pair<String, String> channel : channels) {
            ids.add(channel.first);
        }

        return ids;
    }

    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), DATA_KEY);
        FilterExclusionData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static FilterExclusionData getData() {
        return FilterExclusionData.instance(RuntimeEnvironment.getApplication());
    }
}
