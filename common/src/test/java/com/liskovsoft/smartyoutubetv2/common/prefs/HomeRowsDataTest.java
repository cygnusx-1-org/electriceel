package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.HomeRowsData.Row;
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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HomeRowsDataTest {
    private static final List<Row> DEFAULT_ORDER = Arrays.asList(
            Row.kind(MediaGroup.ROW_KIND_RECOMMENDED), Row.kind(MediaGroup.ROW_KIND_RECENTLY_UPLOADED),
            Row.kind(MediaGroup.ROW_KIND_CONTINUE_WATCHING), Row.kind(MediaGroup.ROW_KIND_WATCH_LATER), Row.kind(MediaGroup.ROW_KIND_SHOWS),
            Row.kind(MediaGroup.ROW_KIND_TOP_CHANNELS), Row.kind(MediaGroup.ROW_KIND_SEARCH_TOPICS), Row.kind(MediaGroup.ROW_KIND_SHORTS),
            Row.youTube());

    @Before
    public void setUp() {
        getPrefs().onAccountChanged(null); // the anonymous account
        TestAccounts.clearSaved(getPrefs(), HomeRowsData.HOME_ROWS_DATA);
        HomeRowsData.resetInstanceForTesting();
    }

    @After
    public void tearDown() {
        Utils.sHandler.removeCallbacksAndMessages(null);
        getPrefs().onAccountChanged(null);
        TestAccounts.clearSaved(getPrefs(), HomeRowsData.HOME_ROWS_DATA);
        HomeRowsData.resetInstanceForTesting();
    }

    @Test
    public void youTubesRowsInYouTubesOrderByDefault() {
        HomeRowsData data = getData();

        assertEquals(Collections.singletonList(Row.youTube()), data.getRows());
        assertTrue(data.isDefault());
        assertFalse(data.isYouTubeOrdered());
        assertFalse(data.isSubscriptionsAdded());
        assertFalse(data.isChannelAdded("UCfirst"));
        assertFalse(data.isChannelAdded(null));
    }

    /**
     * At the top, after the ones added before, in the order added
     */
    @Test
    public void addedRowsGoToTheTop() {
        HomeRowsData data = getData();

        data.addChannel("UCfirst", "First");
        data.setSubscriptionsAdded(true);
        data.addChannel("UCsecond", "Second");

        assertEquals(Arrays.asList(Row.channel("UCfirst", null), Row.subscriptions(), Row.channel("UCsecond", null), Row.youTube()),
                data.getRows());
        assertTrue(data.isChannelAdded("UCfirst"));
        assertTrue(data.isSubscriptionsAdded());
        assertFalse(data.isDefault());
    }

    /**
     * Moved below YouTube's rows, the next ones still go to the top
     */
    @Test
    public void addedRowsGoAboveYouTubesRows() {
        HomeRowsData data = getData();

        data.setSubscriptionsAdded(true);
        data.moveRow(Row.subscriptions(), 1);
        data.addChannel("UCfirst", "First");

        assertEquals(Arrays.asList(Row.channel("UCfirst", null), Row.youTube(), Row.subscriptions()), data.getRows());
    }

    @Test
    public void addedAgainKeepsItsPlaceWithTheNewTitle() {
        HomeRowsData data = getData();

        data.addChannel("UCfirst", "First");
        data.addChannel("UCsecond", "Second");
        data.addChannel("UCfirst", "Renamed");

        List<Row> rows = data.getRows();
        assertEquals(Arrays.asList(Row.channel("UCfirst", null), Row.channel("UCsecond", null), Row.youTube()), rows);
        assertEquals("Renamed", rows.get(0).title);
    }

    @Test
    public void addedRowsAreRemoved() {
        HomeRowsData data = getData();

        data.addChannel("UCfirst", "First");
        data.setSubscriptionsAdded(true);
        data.removeChannel("UCfirst");
        data.setSubscriptionsAdded(false);
        data.removeChannel(null);

        assertTrue(data.isDefault());
    }

    /**
     * YouTube's rows aren't removed, they can only be moved
     */
    @Test
    public void youTubesRowsStay() {
        HomeRowsData data = getData();

        data.orderYouTubeRows();
        data.remove(Row.youTube());
        data.remove(Row.kind(MediaGroup.ROW_KIND_RECOMMENDED));

        assertEquals(DEFAULT_ORDER, data.getRows());
    }

    @Test
    public void youTubesRowsGetPlacesOfTheirOwnWhereYouTubesRowsWere() {
        HomeRowsData data = getData();

        data.setSubscriptionsAdded(true);
        data.orderYouTubeRows();
        data.orderYouTubeRows(); // once

        List<Row> expected = new java.util.ArrayList<>();
        expected.add(Row.subscriptions());
        expected.addAll(DEFAULT_ORDER);
        assertEquals(expected, data.getRows());
        assertTrue(data.isYouTubeOrdered());
    }

    @Test
    public void backToYouTubesOrderKeepsTheAddedRowsInPlace() {
        HomeRowsData data = getData();

        data.addChannel("UCfirst", "First");
        data.orderYouTubeRows();
        data.moveRow(Row.channel("UCfirst", null), DEFAULT_ORDER.size()); // below YouTube's rows
        data.resetYouTubeOrder();

        assertEquals(Arrays.asList(Row.youTube(), Row.channel("UCfirst", null)), data.getRows());
        assertFalse(data.isYouTubeOrdered());
    }

    @Test
    public void movedToItsPlace() {
        HomeRowsData data = getData();

        data.orderYouTubeRows();
        data.moveRow(Row.kind(MediaGroup.ROW_KIND_WATCH_LATER), 0);
        data.moveRow(Row.youTube(), 2);
        // Not there, or no such place: nothing
        data.moveRow(Row.subscriptions(), 0);
        data.moveRow(Row.kind(MediaGroup.ROW_KIND_SHOWS), 20);
        data.moveRow(Row.kind(MediaGroup.ROW_KIND_SHOWS), -1);

        List<Row> rows = data.getRows();
        assertEquals(Row.kind(MediaGroup.ROW_KIND_WATCH_LATER), rows.get(0));
        assertEquals(Row.kind(MediaGroup.ROW_KIND_RECOMMENDED), rows.get(1));
        assertEquals(Row.youTube(), rows.get(2));
        assertEquals(Row.kind(MediaGroup.ROW_KIND_RECENTLY_UPLOADED), rows.get(3));
        assertEquals(DEFAULT_ORDER.size(), rows.size());
    }

    /**
     * As the list put in order with the remote gives them (see MainUISettingsPresenter.showHomeRowsOrder)
     */
    @Test
    public void putInANewOrder() {
        HomeRowsData data = getData();

        data.addChannel("UCfirst", "First");
        data.setSubscriptionsAdded(true);
        data.setRows(Arrays.asList(Row.youTube(), Row.subscriptions(), Row.channel("UCfirst", "First")));

        assertEquals(Arrays.asList(Row.youTube(), Row.subscriptions(), Row.channel("UCfirst", null)), data.getRows());

        HomeRowsData.resetInstanceForTesting();

        assertEquals(Arrays.asList(Row.youTube(), Row.subscriptions(), Row.channel("UCfirst", null)), getData().getRows());
    }

    /**
     * A row was removed since the order was read, or it's there twice: the order isn't of these rows
     */
    @Test
    public void newOrderOfOtherRowsIsLeftOut() {
        HomeRowsData data = getData();

        data.addChannel("UCfirst", "First");
        data.setRows(Arrays.asList(Row.youTube(), Row.subscriptions()));
        data.setRows(Arrays.asList(Row.youTube(), Row.channel("UCfirst", null), Row.youTube()));
        data.setRows(null);

        assertEquals(Arrays.asList(Row.channel("UCfirst", null), Row.youTube()), data.getRows());
    }

    /**
     * The order of the rows in Home: the ones taken out come after them, in their order
     */
    @Test
    public void rowsNotInTheNewOrderComeAfterIt() {
        HomeRowsData data = getData();

        data.orderYouTubeRows();
        data.setYouTubeRowShown(MediaGroup.ROW_KIND_SHOWS, false);
        data.setRows(Arrays.asList(Row.kind(MediaGroup.ROW_KIND_SHORTS), Row.youTube()));

        List<Row> rows = data.getRows();
        assertEquals(Row.kind(MediaGroup.ROW_KIND_SHORTS), rows.get(0));
        assertEquals(Row.youTube(), rows.get(1));
        assertEquals(Row.kind(MediaGroup.ROW_KIND_RECOMMENDED), rows.get(2));
        assertEquals(Row.kind(MediaGroup.ROW_KIND_SHOWS), rows.get(6));
        assertEquals(DEFAULT_ORDER.size(), rows.size());
    }

    @Test
    public void youTubeRowsAreTakenOutOfHome() {
        HomeRowsData data = getData();

        data.setYouTubeRowShown(MediaGroup.ROW_KIND_SHORTS, false);
        data.setYouTubeRowShown(MediaGroup.ROW_KIND_NONE, false);

        assertFalse(data.isYouTubeRowShown(MediaGroup.ROW_KIND_SHORTS));
        assertFalse(data.isShown(Row.youTube()));
        assertTrue(data.isYouTubeRowShown(MediaGroup.ROW_KIND_RECOMMENDED));
        assertTrue(data.isShown(Row.subscriptions()));
        // Not as YouTube shows it anymore
        assertFalse(data.isDefault());

        HomeRowsData.resetInstanceForTesting();

        assertEquals(new java.util.HashSet<>(Arrays.asList(MediaGroup.ROW_KIND_SHORTS, MediaGroup.ROW_KIND_NONE)), getData().getHiddenKinds());

        getData().setYouTubeRowShown(MediaGroup.ROW_KIND_SHORTS, true);
        getData().setYouTubeRowShown(MediaGroup.ROW_KIND_NONE, true);

        assertTrue(getData().isDefault());
    }

    @Test
    public void keptWhenTheAppIsStartedAgain() {
        HomeRowsData data = getData();

        data.addChannel("UCfirst", "First | of all");
        data.setSubscriptionsAdded(true);
        data.orderYouTubeRows();
        data.moveRow(Row.youTube(), 0);
        List<Row> rows = data.getRows();

        HomeRowsData.resetInstanceForTesting();

        assertEquals(rows, getData().getRows());
        assertEquals("First | of all", getData().getRows().get(1).title);
    }

    @Test
    public void eachAccountHasItsOwn() {
        AppPrefs prefs = getPrefs();
        HomeRowsData data = getData();

        prefs.onAccountChanged(TestAccounts.FIRST);
        data.addChannel("UCfirst", "First");

        prefs.onAccountChanged(TestAccounts.SECOND);
        assertTrue(data.isDefault());
        data.setSubscriptionsAdded(true);

        prefs.onAccountChanged(TestAccounts.FIRST);
        assertEquals(Arrays.asList(Row.channel("UCfirst", null), Row.youTube()), data.getRows());

        prefs.onAccountChanged(TestAccounts.SECOND);
        assertEquals(Arrays.asList(Row.subscriptions(), Row.youTube()), data.getRows());
    }

    /**
     * A row of another version, or broken: left out. YouTube's rows are always there.
     */
    @Test
    public void unreadableRowsAreLeftOut() {
        getPrefs().setProfileData(HomeRowsData.HOME_ROWS_DATA, Helpers.mergeData(Helpers.mergeList(Arrays.asList(
                Row.subscriptions().toString(), Helpers.mergeObj(9, 0, null, null), Helpers.mergeObj(3, 0, null, "No id"), "broken",
                Row.subscriptions().toString()))));
        HomeRowsData.resetInstanceForTesting();

        assertEquals(Arrays.asList(Row.subscriptions(), Row.youTube()), getData().getRows());
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.application);
    }

    private static HomeRowsData getData() {
        return HomeRowsData.instance(RuntimeEnvironment.application);
    }
}
