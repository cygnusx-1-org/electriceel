package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.HiddenVideosData.Section;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HiddenVideosDataTest {
    private static final int PINNED_CHANNEL = "Climbing channel".hashCode();

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
    public void nothingIsHiddenByDefault() {
        HiddenVideosData data = getData();

        assertTrue(data.isEmpty());
        assertFalse(data.isHidden(MediaGroup.TYPE_HOME, "video1"));
        assertFalse(data.isHidden(MediaGroup.TYPE_HOME, null));
        assertTrue(data.getSections().isEmpty());
    }

    /**
     * Local to the device, not to the section
     */
    @Test
    public void videoIsHiddenFromEverySection() {
        HiddenVideosData data = getData();

        data.hide(MediaGroup.TYPE_HOME, null, createVideo("video1"));

        int[] sections = {MediaGroup.TYPE_HOME, MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_SEARCH,
                MediaGroup.TYPE_CHANNEL, MediaGroup.TYPE_SUGGESTIONS, SectionFilterData.SECTION_CHANNEL_GROUPS, PINNED_CHANNEL};

        for (int sectionId : sections) {
            assertTrue("section " + sectionId, data.isHidden(sectionId, "video1"));
            assertFalse("section " + sectionId, data.isHidden(sectionId, "video2"));
        }
    }

    /**
     * The Unhide section itself, the local lists and a place that's no section: nothing is hidden from them, nor left out of them
     */
    @Test
    public void unsupportedSectionsHideNothing() {
        HiddenVideosData data = getData();
        int[] sections = {MediaGroup.TYPE_HIDDEN_VIDEOS, MediaGroup.TYPE_PLAYBACK_QUEUE, MediaGroup.TYPE_UNDEFINED, MediaGroup.TYPE_SETTINGS,
                MediaGroup.TYPE_BLOCKED_CHANNELS, MediaGroup.TYPE_BLOCKED_AI_CHANNELS};

        for (int sectionId : sections) {
            assertFalse("section " + sectionId, HiddenVideosData.isSupportedSection(sectionId));

            data.hide(sectionId, null, createVideo("video1"));

            assertFalse("section " + sectionId, data.isHidden(sectionId, "video1"));
        }

        assertTrue(data.isEmpty());

        data.hide(MediaGroup.TYPE_HOME, null, createVideo("video1"));

        for (int sectionId : sections) {
            assertFalse("section " + sectionId, data.isHidden(sectionId, "video1"));
        }

        assertTrue(HiddenVideosData.isSupportedSection(SectionFilterData.SECTION_CHANNEL_GROUPS));
        assertTrue(HiddenVideosData.isSupportedSection(MediaGroup.TYPE_SEARCH));
        assertTrue(HiddenVideosData.isSupportedSection(PINNED_CHANNEL));
    }

    @Test
    public void videoWithoutIdIsNotHidden() {
        HiddenVideosData data = getData();

        data.hide(MediaGroup.TYPE_HOME, null, createVideo(null));
        data.hide(MediaGroup.TYPE_HOME, null, null);

        assertTrue(data.isEmpty());
    }

    @Test
    public void unhideShowsItAgainEverywhere() {
        HiddenVideosData data = getData();

        data.hide(MediaGroup.TYPE_HOME, null, createVideo("video1"));
        data.hide(MediaGroup.TYPE_HOME, null, createVideo("video2"));

        data.unhide("video1");
        data.unhide("video3");
        data.unhide(null);

        assertFalse(data.isHidden(MediaGroup.TYPE_HOME, "video1"));
        assertFalse(data.isHidden(MediaGroup.TYPE_SEARCH, "video1"));
        assertTrue(data.isHidden(MediaGroup.TYPE_SEARCH, "video2"));

        data.unhide("video2");

        assertTrue(data.isEmpty());
    }

    /**
     * One row of the Unhide section has it: the one of the section it was hidden from last
     */
    @Test
    public void hiddenAgainFromAnotherSectionMovesToItsRow() {
        HiddenVideosData data = getData();

        data.hide(MediaGroup.TYPE_HOME, null, createVideo("video1"));
        data.hide(MediaGroup.TYPE_HOME, null, createVideo("video2"));
        data.hide(PINNED_CHANNEL, "Climbing channel", createVideo("video1"));

        for (HiddenVideosData current : Arrays.asList(data, getDataReadAgain())) {
            List<Section> sections = current.getSections();

            assertEquals(2, sections.size());
            assertEquals(MediaGroup.TYPE_HOME, sections.get(0).sectionId);
            assertEquals(Collections.singletonList("video2"), getIds(sections.get(0)));
            assertEquals(PINNED_CHANNEL, sections.get(1).sectionId);
            assertEquals(Collections.singletonList("video1"), getIds(sections.get(1)));
        }

        getData().unhide("video1");

        assertEquals(1, getData().getSections().size());
        assertFalse(getData().isHidden(MediaGroup.TYPE_HOME, "video1"));
    }

    /**
     * Saved while each section had its own, a video can be there for more than one: it's one, in the row it was hidden from last
     */
    @Test
    public void videoSavedForMoreThanOneSectionIsRestoredOnce() {
        List<Integer> sectionIds = Arrays.asList(MediaGroup.TYPE_SEARCH, MediaGroup.TYPE_HOME, MediaGroup.TYPE_SEARCH);
        List<String> titles = Arrays.asList(null, null, null);
        List<String> cards = Arrays.asList(createVideo("video1").toString(), createVideo("video2").toString(),
                createVideo("video1").toString());
        getPrefs().setProfileData(HiddenVideosData.HIDDEN_VIDEOS_DATA, Helpers.mergeData(Helpers.mergeList(sectionIds),
                Helpers.mergeList(titles), Helpers.mergeList(cards)));
        HiddenVideosData.resetInstanceForTesting();

        HiddenVideosData data = getData();
        List<Section> sections = data.getSections();

        assertEquals(2, sections.size());
        assertEquals(MediaGroup.TYPE_HOME, sections.get(0).sectionId);
        assertEquals(Collections.singletonList("video2"), getIds(sections.get(0)));
        assertEquals(MediaGroup.TYPE_SEARCH, sections.get(1).sectionId);
        assertEquals(Collections.singletonList("video1"), getIds(sections.get(1)));

        data.unhide("video1");

        assertFalse(data.isHidden(MediaGroup.TYPE_SEARCH, "video1"));
        assertEquals(1, data.getSections().size());
    }

    /**
     * A row per section, in the order of the oldest video still hidden from each, the newest hidden first.
     * Hidden again, it's the newest.
     */
    @Test
    public void sectionsHaveTheNewestHiddenFirst() {
        HiddenVideosData data = getData();

        data.hide(MediaGroup.TYPE_SEARCH, null, createVideo("search1"));
        data.hide(MediaGroup.TYPE_HOME, null, createVideo("home1"));
        data.hide(MediaGroup.TYPE_SEARCH, null, createVideo("search2"));
        data.hide(MediaGroup.TYPE_HOME, null, createVideo("home2"));

        List<Section> sections = data.getSections();

        assertEquals(2, sections.size());
        assertEquals(MediaGroup.TYPE_SEARCH, sections.get(0).sectionId);
        assertEquals(Arrays.asList("search2", "search1"), getIds(sections.get(0)));
        assertEquals(MediaGroup.TYPE_HOME, sections.get(1).sectionId);
        assertEquals(Arrays.asList("home2", "home1"), getIds(sections.get(1)));

        data.hide(MediaGroup.TYPE_SEARCH, null, createVideo("search1"));
        data.hide(MediaGroup.TYPE_SEARCH, null, createVideo("search2"));
        sections = data.getSections();

        assertEquals(MediaGroup.TYPE_HOME, sections.get(0).sectionId);
        assertEquals(Arrays.asList("search2", "search1"), getIds(sections.get(1)));
    }

    /**
     * What the card showed, nothing of the row it was in
     */
    @Test
    public void cardSurvivesRestart() {
        Video video = createVideo("video1");
        video.secondTitle = "Climber · 3 days ago";
        video.channelId = "UCclimber";
        video.cardImageUrl = "https://i.ytimg.com/vi/video1/hqdefault.jpg";
        video.badge = "12:34";
        video.reloadPageKey = "continuation";
        video.percentWatched = 50;
        getData().hide(MediaGroup.TYPE_HOME, null, video);
        HiddenVideosData.resetInstanceForTesting();

        HiddenVideosData restored = getData();

        assertTrue(restored.isHidden(MediaGroup.TYPE_HOME, "video1"));

        Video card = restored.getSections().get(0).videos.get(0);

        assertEquals("Video video1", card.title);
        assertEquals("Climber · 3 days ago", card.secondTitle.toString());
        assertEquals("UCclimber", card.channelId);
        assertEquals("https://i.ytimg.com/vi/video1/hqdefault.jpg", card.cardImageUrl);
        assertEquals("12:34", card.badge);
        assertEquals(MediaItem.TYPE_VIDEO, card.itemType);
        assertNull(card.reloadPageKey);
        assertEquals(-1, card.percentWatched, 0);
    }

    /**
     * A section without a title of its own keeps the one it had (e.g. a pinned channel)
     */
    @Test
    public void titleOfThePinnedSectionSurvivesRestart() {
        getData().hide(PINNED_CHANNEL, "Climbing channel", createVideo("video1"));
        getData().hide(MediaGroup.TYPE_HOME, null, createVideo("video2"));
        HiddenVideosData.resetInstanceForTesting();

        List<Section> sections = getData().getSections();

        assertEquals("Climbing channel", sections.get(0).title);
        assertNull(sections.get(1).title);
    }

    @Test
    public void oldestHiddenAreShownAgainPastTheLimit() {
        HiddenVideosData data = getData();

        for (int i = 0; i < HiddenVideosData.MAX_VIDEOS; i++) {
            data.hide(MediaGroup.TYPE_HOME, null, createVideo("video" + i));
        }

        data.hide(MediaGroup.TYPE_HOME, null, createVideo("video0")); // the newest again
        data.hide(MediaGroup.TYPE_SEARCH, null, createVideo("newest"));

        assertTrue(data.isHidden(MediaGroup.TYPE_HOME, "video0"));
        assertFalse(data.isHidden(MediaGroup.TYPE_HOME, "video1"));
        assertTrue(data.isHidden(MediaGroup.TYPE_HOME, "video2"));
        assertTrue(data.isHidden(MediaGroup.TYPE_SEARCH, "newest"));

        HiddenVideosData.resetInstanceForTesting();
        HiddenVideosData restored = getData();

        assertEquals(HiddenVideosData.MAX_VIDEOS, restored.getSections().get(0).videos.size() + restored.getSections().get(1).videos.size());
        assertFalse(restored.isHidden(MediaGroup.TYPE_HOME, "video1"));
        assertTrue(restored.isHidden(MediaGroup.TYPE_SEARCH, "newest"));
    }

    @Test
    public void eachAccountHasItsOwn() {
        AppPrefs prefs = getPrefs();
        HiddenVideosData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.hide(MediaGroup.TYPE_HOME, null, createVideo("first"));

        prefs.onAccountChanged(TestAccounts.SECOND);

        assertFalse(data.isHidden(MediaGroup.TYPE_HOME, "first"));

        data.hide(MediaGroup.TYPE_HOME, null, createVideo("second"));

        prefs.onAccountChanged(TestAccounts.FIRST);

        assertTrue(data.isHidden(MediaGroup.TYPE_HOME, "first"));
        assertFalse(data.isHidden(MediaGroup.TYPE_HOME, "second"));

        prefs.onAccountChanged(TestAccounts.SECOND);

        assertFalse(data.isHidden(MediaGroup.TYPE_HOME, "first"));
        assertTrue(data.isHidden(MediaGroup.TYPE_HOME, "second"));
    }

    /**
     * Not settings: copying another account's settings leaves them alone
     */
    @Test
    public void copyingSettingsKeepsTheHiddenVideos() {
        AppPrefs prefs = getPrefs();
        HiddenVideosData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.hide(MediaGroup.TYPE_HOME, null, createVideo("first"));
        prefs.onAccountChanged(TestAccounts.SECOND);
        data.hide(MediaGroup.TYPE_HOME, null, createVideo("second"));

        prefs.copyProfileData(TestAccounts.FIRST);
        HiddenVideosData.resetInstanceForTesting();

        assertTrue(getData().isHidden(MediaGroup.TYPE_HOME, "second"));
        assertFalse(getData().isHidden(MediaGroup.TYPE_HOME, "first"));
    }

    /**
     * The sidebar's sections in their default order, then the subscription groups, the pinned sections, search, the channel pages
     * and the player's suggestions
     */
    @Test
    public void rowsAreInTheOrderOfTheSidebar() {
        List<Integer> sidebarOrder = Arrays.asList(MediaGroup.TYPE_HOME, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_SUBSCRIPTIONS,
                MediaGroup.TYPE_HISTORY);
        int otherPinned = "Other".hashCode();
        List<Section> sections = new ArrayList<>();

        for (int sectionId : new int[] {MediaGroup.TYPE_SUGGESTIONS, PINNED_CHANNEL, MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_CHANNEL,
                SectionFilterData.SECTION_CHANNEL_GROUPS, MediaGroup.TYPE_SEARCH, otherPinned, MediaGroup.TYPE_HOME}) {
            sections.add(new Section(sectionId, null, new ArrayList<>()));
        }

        List<Integer> result = new ArrayList<>();

        for (Section section : HiddenVideosData.sortForRows(sections, sidebarOrder)) {
            result.add(section.sectionId);
        }

        assertEquals(Arrays.asList(MediaGroup.TYPE_HOME, MediaGroup.TYPE_HISTORY, SectionFilterData.SECTION_CHANNEL_GROUPS, PINNED_CHANNEL,
                otherPinned, MediaGroup.TYPE_SEARCH, MediaGroup.TYPE_CHANNEL, MediaGroup.TYPE_SUGGESTIONS), result);
    }

    /**
     * A pinned section whose id changed (see SidebarService.normalizeGroupIds) takes its row of the Unhide section
     */
    @Test
    public void hiddenVideosGoWithTheirSectionsNewId() {
        HiddenVideosData data = getData();
        int newSection = "New climbing channel".hashCode();

        data.hide(PINNED_CHANNEL, "Climbing", createVideo("video1"));
        data.hide(PINNED_CHANNEL, "Climbing", createVideo("video2"));
        data.hide(newSection, "Climbing again", createVideo("video4"));
        data.hide(MediaGroup.TYPE_HOME, null, createVideo("video3"));

        data.moveSections(Collections.singletonMap(PINNED_CHANNEL, newSection));

        for (HiddenVideosData current : Arrays.asList(data, getDataReadAgain())) {
            List<Integer> sectionIds = new ArrayList<>();
            Section moved = null;

            for (Section section : current.getSections()) {
                sectionIds.add(section.sectionId);

                if (section.sectionId == newSection) {
                    moved = section;
                }
            }

            assertEquals(Arrays.asList(newSection, MediaGroup.TYPE_HOME), sectionIds);
            assertEquals(Arrays.asList("video4", "video2", "video1"), getIds(moved));
            assertEquals("Climbing again", moved.title);
            assertTrue(current.isHidden(MediaGroup.TYPE_HOME, "video1"));
        }
    }

    private static HiddenVideosData getDataReadAgain() {
        HiddenVideosData.resetInstanceForTesting();
        return getData();
    }

    private static List<String> getIds(Section section) {
        List<String> result = new ArrayList<>();

        for (Video video : section.videos) {
            result.add(video.videoId);
        }

        return result;
    }

    private static Video createVideo(String videoId) {
        Video video = new Video();
        video.itemType = MediaItem.TYPE_VIDEO;
        video.videoId = videoId;
        video.title = "Video " + videoId;
        return video;
    }

    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), HiddenVideosData.HIDDEN_VIDEOS_DATA);
        HiddenVideosData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static HiddenVideosData getData() {
        return HiddenVideosData.instance(RuntimeEnvironment.getApplication());
    }
}
