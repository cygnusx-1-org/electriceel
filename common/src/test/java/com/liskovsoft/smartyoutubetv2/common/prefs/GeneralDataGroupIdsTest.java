package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The last playlist, the playlists' sort orders and the remembered positions follow the ids the groups and the playlists kept on
 * the device have now (see GroupReferences)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GeneralDataGroupIdsTest {
    private static final String OLD_ID = "Ab12Cd34Ef56";
    private static final String NEW_ID = "p-3333333333333333";

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
    public void playlistReferencesTakeTheNewIds() {
        GeneralData data = getData();
        data.setLastPlaylistId(OLD_ID);
        data.setPlaylistOrder(OLD_ID, 2);
        data.setPlaylistOrder("PLyoutubeplaylist", 1);
        data.setSelectedItem(10, playlistCard(OLD_ID));
        data.setSelectedItem(20, videoCard("video1"));

        Map<String, String> ids = new HashMap<>();
        ids.put(OLD_ID, NEW_ID);
        data.normalizeGroupIds(id -> null, ids::get, Collections.singletonMap(20, 21));

        for (int i = 0; i < 2; i++) {
            assertEquals(NEW_ID, data.getLastPlaylistId());
            assertEquals(2, data.getPlaylistOrder(NEW_ID));
            assertEquals(-1, data.getPlaylistOrder(OLD_ID));
            assertEquals(1, data.getPlaylistOrder("PLyoutubeplaylist"));
            assertEquals(NEW_ID, data.getSelectedItem(10).playlistId);
            assertEquals(NEW_ID, data.getSelectedItem(10).channelId);
            assertNull(data.getSelectedItem(20));
            assertEquals("video1", data.getSelectedItem(21).videoId);

            data.persistPendingData();
            data.onProfileChanged(); // read again
        }
    }

    /**
     * Set after the change on this device, the new id's order is the newer
     */
    @Test
    public void newIdsOrderIsKept() {
        GeneralData data = getData();
        data.setPlaylistOrder(OLD_ID, 2);
        data.setPlaylistOrder(NEW_ID, 3);

        data.normalizeGroupIds(id -> null, id -> OLD_ID.equals(id) ? NEW_ID : null, Collections.emptyMap());

        assertEquals(3, data.getPlaylistOrder(NEW_ID));
        assertEquals(-1, data.getPlaylistOrder(OLD_ID));
    }

    private static Video playlistCard(String playlistId) {
        Video item = new Video();
        item.playlistId = playlistId;
        item.channelId = playlistId;
        item.title = "Climbing";
        return item;
    }

    private static Video videoCard(String videoId) {
        Video item = new Video();
        item.videoId = videoId;
        return item;
    }

    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), GeneralData.GENERAL_DATA);
        getData().onProfileChanged();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static GeneralData getData() {
        return GeneralData.instance(RuntimeEnvironment.getApplication());
    }
}
