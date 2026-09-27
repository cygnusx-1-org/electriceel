package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.AiSListFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.KeywordFilterData;
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
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class KeywordFilterTest {
    private KeywordFilterData mData;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        mData = KeywordFilterData.instance(context);
        clearKeywords();
    }

    @After
    public void tearDown() {
        clearKeywords();
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void wordsAreLowerCaseWithoutPunctuation() {
        assertEquals(Arrays.asList("live", "the", "big", "match", "2024", "shorts"),
                KeywordFilter.getWords("LIVE: The Big Match (2024) #shorts"));
        assertEquals(Collections.emptyList(), KeywordFilter.getWords(null));
        assertEquals(Collections.emptyList(), KeywordFilter.getWords(" !? - "));
    }

    @Test
    public void apostropheStaysInsideTheWord() {
        assertEquals(Arrays.asList("don't", "stop"), KeywordFilter.getWords("Don’t Stop"));
        assertEquals(Arrays.asList("don't", "stop"), KeywordFilter.getWords("don't stop"));
        assertEquals(Arrays.asList("rock", "n", "roll"), KeywordFilter.getWords("rock 'n' roll"));
    }

    @Test
    public void wordsOfOtherAlphabets() {
        assertEquals(Arrays.asList("café", "straße", "видео"), KeywordFilter.getWords("Café Straße — Видео"));
    }

    @Test
    public void keywordIsNormalized() {
        assertEquals("tier list", KeywordFilter.normalize("  Tier-List! "));
        assertEquals("reaction", KeywordFilter.normalize("#Reaction"));
        assertNull(KeywordFilter.normalize("!!!"));
        assertNull(KeywordFilter.normalize(null));
    }

    @Test
    public void titleWordsAreUniqueInTheTitleOrder() {
        assertEquals(Arrays.asList("the", "cat", "and", "dog"), KeywordFilter.getTitleWords("The cat and THE dog"));
    }

    @Test
    public void feedWordsAreUniqueWithoutCaseAndSorted() {
        assertEquals(Arrays.asList("a", "b", "live", "z"),
                KeywordFilter.getUniqueWords(Arrays.asList("Z live", "LIVE b", "a Live")));
        assertEquals(Collections.emptyList(), KeywordFilter.getUniqueWords(new ArrayList<String>()));
    }

    @Test
    public void wholeWordsAreHidden() {
        mData.addKeyword("live");

        assertTrue(mData.isHidden("LIVE: the match"));
        assertTrue(mData.isHidden("Watch it live!"));
        assertFalse(mData.isHidden("Delivery day"));
        assertFalse(mData.isHidden("Lives of the stars"));
        assertFalse(mData.isHidden(null));
    }

    @Test
    public void phraseNeedsItsWordsTogether() {
        mData.addKeyword("tier list");

        assertTrue(mData.isHidden("My Tier-List of games"));
        assertTrue(mData.isHidden("TIER LIST"));
        assertFalse(mData.isHidden("A list of every tier"));
        assertFalse(mData.isHidden("Tier one list"));
    }

    /**
     * Typed or said, a few words are one keyword
     */
    @Test
    public void typedOrSaidWordsAreOneKeyword() {
        assertTrue(mData.addKeyword("Funny cats"));

        assertEquals(Collections.singletonList("funny cats"), mData.getKeywords());
        assertTrue(mData.isHidden("Top 10 FUNNY CATS of 2024"));
        assertFalse(mData.isHidden("Funny dogs and cats"));
        assertFalse(mData.isHidden("Cats being funny"));
    }

    @Test
    public void wordsAreGroupedByLetter() {
        Map<String, List<String>> groups = KeywordFilter.groupByLetter(
                Arrays.asList("2024", "apple", "avocado", "banana", "église", "zebra", "видео"));

        assertEquals(Arrays.asList("#", "A", "B", "E", "Z", "В"), new ArrayList<>(groups.keySet()));
        assertEquals(Arrays.asList("apple", "avocado"), groups.get("A"));
        assertEquals(Collections.singletonList("église"), groups.get("E"));
        assertEquals(Collections.singletonList("2024"), groups.get("#"));
    }

    @Test
    public void letterKeepsASingleCharacter() {
        assertEquals("ß", KeywordFilter.getLetter("ßtest"));
        assertEquals("#", KeywordFilter.getLetter("1st"));
        assertEquals("C", KeywordFilter.getLetter("ça"));
    }

    @Test
    public void keywordsAreKeptOnce() {
        assertTrue(mData.addKeyword("Reaction"));
        assertFalse(mData.addKeyword("reaction"));
        assertFalse(mData.addKeyword("#REACTION"));
        assertFalse(mData.addKeyword("?!"));

        assertEquals(Collections.singletonList("reaction"), mData.getKeywords());
        assertTrue(mData.containsKeyword("REACTION"));
    }

    @Test
    public void removedKeywordShowsTheVideosAgain() {
        mData.addKeyword("prank");
        assertTrue(mData.isHidden("Best prank ever"));

        mData.removeKeyword("PRANK");

        assertFalse(mData.isHidden("Best prank ever"));
        assertTrue(mData.isEmpty());
    }

    @Test
    public void videosWithKeywordAreHiddenFromFeeds() {
        mData.addKeyword("reaction");

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createVideo("hidden", "My Reaction to the trailer"));
        home.add(createVideo("shown", "The trailer"));

        assertEquals(1, home.getSize());
        assertEquals("shown", home.get(0).videoId);

        VideoGroup search = VideoGroup.from(new ArrayList<>());
        search.setType(MediaGroup.TYPE_SEARCH);
        search.add(createVideo("hidden", "Reaction"));

        assertEquals(0, search.getSize());
    }

    @Test
    public void deArrowTitleIsCheckedToo() {
        mData.addKeyword("reaction");

        Video video = createVideo("hidden", "You won't believe this");
        video.deArrowTitle = "Reaction to the new phone";

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(video);

        assertEquals(0, home.getSize());
    }

    @Test
    public void listsTheUserMadeAreNotFiltered() {
        mData.addKeyword("reaction");

        VideoGroup history = createGroup(MediaGroup.TYPE_HISTORY);
        history.add(createVideo("kept", "My reaction"));

        VideoGroup queue = createGroup(MediaGroup.TYPE_PLAYBACK_QUEUE);
        queue.add(createVideo("kept", "My reaction"));

        assertEquals(1, history.getSize());
        assertEquals(1, queue.getSize());
    }

    @Test
    public void cardsWithoutVideoAreNotFiltered() {
        mData.addKeyword("reaction");

        Video playlist = createVideo(null, "Reaction videos");
        playlist.playlistId = "PL1";

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(playlist);

        assertEquals(1, home.getSize());
    }

    @Test
    public void supportedSections() {
        assertTrue(KeywordFilter.isSupportedSection(AiSListFilterData.SECTION_HOME));
        assertTrue(KeywordFilter.isSupportedSection(AiSListFilterData.SECTION_SEARCH));
        assertTrue(KeywordFilter.isSupportedSection(AiSListFilterData.SECTION_SUBSCRIPTIONS));
        assertTrue(KeywordFilter.isSupportedSection(AiSListFilterData.SECTION_SUGGESTIONS));
        assertTrue(KeywordFilter.isSupportedSection(AiSListFilterData.SECTION_CHANNELS));
        assertTrue(KeywordFilter.isSupportedSection(AiSListManager.SECTION_CHANNEL_PAGE));
        assertFalse(KeywordFilter.isSupportedSection(AiSListFilterData.SECTION_HISTORY));
        assertFalse(KeywordFilter.isSupportedSection(AiSListFilterData.SECTION_PLAYLISTS));
        assertFalse(KeywordFilter.isSupportedSection(AiSListFilterData.SECTION_WATCH_LATER));
        assertFalse(KeywordFilter.isSupportedSection(-1));
    }

    @Test
    public void commonKeywordsAreNormalized() {
        for (String keyword : KeywordFilter.COMMON_KEYWORDS) {
            assertEquals(keyword, KeywordFilter.normalize(keyword));
        }
    }

    private void clearKeywords() {
        for (String keyword : mData.getKeywords()) {
            mData.removeKeyword(keyword);
        }
    }

    private static VideoGroup createGroup(int type) {
        return VideoGroup.from(new BrowseSection(type, "Section", BrowseSection.TYPE_GRID, 0));
    }

    private static Video createVideo(String videoId, String title) {
        Video video = new Video();
        video.videoId = videoId;
        video.title = title;
        return video;
    }
}
