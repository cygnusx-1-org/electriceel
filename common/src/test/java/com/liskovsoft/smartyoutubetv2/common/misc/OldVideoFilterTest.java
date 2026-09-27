package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.OldVideosData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OldVideoFilterTest {
    private static final String OLD_INFO = "Some Channel • 1.2M views • 2 years ago";
    private static final String NEW_INFO = "Some Channel • 5K views • 3 weeks ago";
    private OldVideosData mData;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        GlobalPreferences.instance(context);
        mData = OldVideosData.instance(context);
        mData.setEnabled(false);
        mData.setPeriodMonths(12);
        mData.setSectionEnabled(MediaGroup.TYPE_HOME, true);
        mData.setSectionEnabled(MediaGroup.TYPE_SUBSCRIPTIONS, true);
        mData.setSectionEnabled(MediaGroup.TYPE_GAMING, false);
    }

    @After
    public void tearDown() {
        mData.setEnabled(false);
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void ageIsReadFromTheCardText() {
        assertEquals(730, OldVideoFilter.getAgeDays(OLD_INFO));
        assertEquals(21, OldVideoFilter.getAgeDays(NEW_INFO));
        assertEquals(60, OldVideoFilter.getAgeDays("Streamed 2 months ago"));
        assertEquals(1, OldVideoFilter.getAgeDays("1 day ago"));
        assertEquals(0, OldVideoFilter.getAgeDays("5 hours ago"));
        assertEquals(365, OldVideoFilter.getAgeDays("1 Year Ago"));
        assertEquals(90, OldVideoFilter.getAgeDays("3 months ago"));
    }

    @Test
    public void cardWithoutRelativeDateHasNoAge() {
        assertEquals(-1, OldVideoFilter.getAgeDays(null));
        assertEquals(-1, OldVideoFilter.getAgeDays("Some Channel • 1.2K watching"));
        assertEquals(-1, OldVideoFilter.getAgeDays("Some Channel • il y a 2 ans"));
        assertEquals(-1, OldVideoFilter.getAgeDays("Top 10 of 2 years"));
    }

    /**
     * YouTube rounds the age down, so the video shown as the period old is hidden
     */
    @Test
    public void videoAsOldAsThePeriodIsHidden() {
        assertTrue(OldVideoFilter.isOlderThan("1 month ago", 1));
        assertFalse(OldVideoFilter.isOlderThan("4 weeks ago", 1));
        assertTrue(OldVideoFilter.isOlderThan("3 months ago", 3));
        assertFalse(OldVideoFilter.isOlderThan("2 months ago", 3));
        assertTrue(OldVideoFilter.isOlderThan("6 months ago", 6));
        assertFalse(OldVideoFilter.isOlderThan("5 months ago", 6));
        assertTrue(OldVideoFilter.isOlderThan("1 year ago", 12));
        assertFalse(OldVideoFilter.isOlderThan("11 months ago", 12));
        assertTrue(OldVideoFilter.isOlderThan("2 years ago", 24));
        assertFalse(OldVideoFilter.isOlderThan("1 year ago", 24));
        assertFalse(OldVideoFilter.isOlderThan("LIVE", 1));
    }

    @Test
    public void oldVideosAreHiddenOnlyWhileOn() {
        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        home.add(createVideo("old", OLD_INFO));
        assertEquals(1, home.getSize());

        mData.setEnabled(true);

        VideoGroup home2 = createGroup(MediaGroup.TYPE_HOME);
        home2.add(createVideo("old", OLD_INFO));
        home2.add(createVideo("new", NEW_INFO));
        assertEquals(1, home2.getSize());
        assertEquals("new", home2.get(0).videoId);
    }

    @Test
    public void oldVideosAreHiddenOnlyInPickedSections() {
        mData.setEnabled(true);

        VideoGroup subscriptions = createGroup(MediaGroup.TYPE_SUBSCRIPTIONS);
        VideoGroup gaming = createGroup(MediaGroup.TYPE_GAMING);
        subscriptions.add(createVideo("old", OLD_INFO));
        gaming.add(createVideo("old", OLD_INFO));

        assertEquals(0, subscriptions.getSize());
        assertEquals(1, gaming.getSize());

        mData.setSectionEnabled(MediaGroup.TYPE_GAMING, true);

        VideoGroup gaming2 = createGroup(MediaGroup.TYPE_GAMING);
        gaming2.add(createVideo("old", OLD_INFO));

        assertEquals(0, gaming2.getSize());
    }

    @Test
    public void groupWithoutSectionIsNotFiltered() {
        mData.setEnabled(true);

        // E.g. a channel page or the search results
        VideoGroup channel = VideoGroup.from(new ArrayList<>());
        channel.add(createVideo("old", OLD_INFO));

        assertEquals(1, channel.getSize());
    }

    @Test
    public void cardsWithoutVideoAreNotFiltered() {
        mData.setEnabled(true);

        VideoGroup home = createGroup(MediaGroup.TYPE_HOME);
        Video playlist = createVideo(null, OLD_INFO);
        playlist.playlistId = "PL1";
        home.add(playlist);

        assertEquals(1, home.getSize());
    }

    @Test
    public void rowLeftWithCardsOnlyIsReported() {
        mData.setEnabled(true);

        // E.g. "Listen again": old videos and a "More music" card
        VideoGroup cardsLeft = createGroup(MediaGroup.TYPE_HOME);
        cardsLeft.add(createVideo("old", OLD_INFO));
        cardsLeft.add(createCard());

        VideoGroup videoLeft = createGroup(MediaGroup.TYPE_HOME);
        videoLeft.add(createVideo("old", OLD_INFO));
        videoLeft.add(createVideo("new", NEW_INFO));
        videoLeft.add(createCard());

        // Nothing was hidden: a row of cards (e.g. channels) stays
        VideoGroup cardsOnly = createGroup(MediaGroup.TYPE_HOME);
        cardsOnly.add(createCard());

        VideoGroup allHidden = createGroup(MediaGroup.TYPE_HOME);
        allHidden.add(createVideo("old", OLD_INFO));

        assertTrue(cardsLeft.hasOnlyHiddenVideos());
        assertFalse(videoLeft.hasOnlyHiddenVideos());
        assertFalse(cardsOnly.hasOnlyHiddenVideos());
        assertFalse(allHidden.hasOnlyHiddenVideos()); // empty, the view doesn't show it anyway
    }

    @Test
    public void historyAndLocalListsCantBePicked() {
        assertFalse(OldVideoFilter.isSupportedSection(MediaGroup.TYPE_HISTORY));
        assertFalse(OldVideoFilter.isSupportedSection(MediaGroup.TYPE_PLAYBACK_QUEUE));
        assertFalse(OldVideoFilter.isSupportedSection(MediaGroup.TYPE_SETTINGS));
        assertTrue(OldVideoFilter.isSupportedSection(MediaGroup.TYPE_HOME));
        assertTrue(OldVideoFilter.isSupportedSection(MediaGroup.TYPE_KIDS_HOME));
        assertTrue(OldVideoFilter.isSupportedSection(MediaGroup.TYPE_LIVE));
    }

    @Test
    public void periodTitles() {
        Context context = RuntimeEnvironment.getApplication();

        assertEquals("1 month", OldVideoFilter.getPeriodTitle(context, 1));
        assertEquals("3 months", OldVideoFilter.getPeriodTitle(context, 3));
        assertEquals("6 months", OldVideoFilter.getPeriodTitle(context, 6));
        assertEquals("1 year", OldVideoFilter.getPeriodTitle(context, 12));
        assertEquals("2 years", OldVideoFilter.getPeriodTitle(context, 24));
    }

    private static VideoGroup createGroup(int type) {
        return VideoGroup.from(new BrowseSection(type, "Section", BrowseSection.TYPE_GRID, 0));
    }

    private static Video createCard() {
        Video card = createVideo(null, null);
        card.title = "More music";
        card.playlistId = "RDmore";
        return card;
    }

    private static Video createVideo(String videoId, String info) {
        Video video = new Video();
        video.videoId = videoId;
        video.title = "Video " + videoId;
        video.secondTitle = info;
        return video;
    }
}
