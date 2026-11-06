package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.DebuggingData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DataSubmitterTest {
    private static final long NOW = 1_792_000_000_000L;
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();
    private int mSavedEngine;
    private long mSavedLastTs;
    private long mSavedLastStartTs;
    private String mSavedLastId;
    private long mSavedLastTime;

    @Before
    public void setUp() {
        PlayerFailureLog.useForTesting(mFolder.getRoot());
        // Cronet can't load under Robolectric
        mSavedEngine = PlayerTweaksData.instance(getContext()).getPlayerDataSource();
        PlayerTweaksData.instance(getContext()).setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP);
        DebuggingData data = DebuggingData.instance(getContext());
        mSavedLastTs = data.getLastSubmittedFailureTs();
        mSavedLastStartTs = data.getLastSubmittedStartTs();
        mSavedLastId = data.getLastSubmissionId();
        mSavedLastTime = data.getLastSubmissionTime();
    }

    @After
    public void tearDown() {
        PlayerFailureLog.useForTesting(null);
        VideoStartLog.useForTesting(null);
        PlayerTweaksData.instance(getContext()).setPlayerDataSource(mSavedEngine);
        DebuggingData.instance(getContext()).setLastSubmission(mSavedLastId, mSavedLastTs, mSavedLastStartTs, mSavedLastTime);
    }

    /**
     * On startup: nothing sent (it would reach the network) when every failure was submitted already
     */
    @Test
    public void nothingNewIsntSentOnStartup() throws Exception {
        Video video = new Video();
        video.videoId = "video";
        PlayerFailure.create(getContext(), PlayerFailure.KIND_STALL).video(video).record();
        long newest = PlayerFailureLog.instance(getContext()).readAll().get(0).getLong("ts");

        DebuggingData.instance(getContext()).setLastSubmission("K7M2QX9P", newest, 0, System.currentTimeMillis());

        assertNull(DataSubmitter.submit(getContext(), true));
    }

    @Test
    public void noFailuresArentSentOnStartup() throws Exception {
        DebuggingData.instance(getContext()).setLastSubmission("K7M2QX9P", 0, 0, System.currentTimeMillis());

        assertNull(DataSubmitter.submit(getContext(), true));
    }

    /**
     * What a submission sent isn't sent again: only the failures and the video starts recorded after it
     */
    @Test
    public void onlyTheOnesAfterTheLastSubmissionAreSent() throws Exception {
        DataSubmitter.Unsent unsent = DataSubmitter.Unsent.read(items(100, 200, 300), 200, items(150, 250), 150, NOW);

        assertEquals(Collections.singletonList(300L), times(unsent.failures));
        assertEquals(Collections.singletonList(250L), times(unsent.videoStarts));
        assertEquals(300, unsent.newestFailureTs);
        assertEquals(250, unsent.newestStartTs);
    }

    @Test
    public void everythingIsSentTheFirstTime() throws Exception {
        DataSubmitter.Unsent unsent = DataSubmitter.Unsent.read(items(100, 200), 0, items(150), 0, NOW);

        assertEquals(Arrays.asList(100L, 200L), times(unsent.failures));
        assertEquals(Collections.singletonList(150L), times(unsent.videoStarts));
    }

    /**
     * The next one starts from the same place: nothing's sent again, and nothing new is skipped
     */
    @Test
    public void nothingNewKeepsTheLastSubmissionsPlace() throws Exception {
        DataSubmitter.Unsent unsent = DataSubmitter.Unsent.read(items(100, 200), 200, items(150), 150, NOW);

        assertTrue(unsent.failures.isEmpty());
        assertTrue(unsent.videoStarts.isEmpty());
        assertEquals(200, unsent.newestFailureTs);
        assertEquals(150, unsent.newestStartTs);
    }

    /**
     * Submitted with the clock ahead: the ones recorded once it's set right would be held back till it catches up
     */
    @Test
    public void aLastSubmissionInTheFutureDoesntHoldBackTheNewOnes() throws Exception {
        DataSubmitter.Unsent unsent = DataSubmitter.Unsent.read(items(100, 200), NOW + 1, items(150), NOW + 1, NOW);

        assertEquals(Arrays.asList(100L, 200L), times(unsent.failures));
        assertEquals(Collections.singletonList(150L), times(unsent.videoStarts));
        assertEquals(200, unsent.newestFailureTs);
        assertEquals(150, unsent.newestStartTs);
    }

    /**
     * From the logs, with the last submission's places
     */
    @Test
    public void theLogsAreReadFromTheLastSubmissionsPlaces() throws Exception {
        PlayerFailure.create(getContext(), PlayerFailure.KIND_STALL).videoId("sent").at(NOW - 2_000).record();
        PlayerFailure.create(getContext(), PlayerFailure.KIND_STALL).videoId("new").at(NOW - 1_000).record();
        VideoStartLog.useForTesting(mFolder.getRoot()).record(start(NOW - 2_000, "sent"));
        VideoStartLog.instance(getContext()).record(start(NOW - 1_000, "new"));
        DebuggingData.instance(getContext()).setLastSubmission("K7M2QX9P", NOW - 2_000, NOW - 2_000, NOW - 1_500);

        DataSubmitter.Unsent unsent = DataSubmitter.Unsent.read(getContext());

        assertEquals(1, unsent.failures.size());
        assertEquals("new", unsent.failures.get(0).getString("videoId"));
        assertEquals(1, unsent.videoStarts.size());
        assertEquals("new", unsent.videoStarts.get(0).getString("videoId"));
    }

    private static List<JSONObject> items(long... times) throws Exception {
        List<JSONObject> result = new ArrayList<>();

        for (long ts : times) {
            result.add(new JSONObject().put("ts", ts));
        }

        return result;
    }

    private static List<Long> times(List<JSONObject> items) {
        List<Long> result = new ArrayList<>();

        for (JSONObject item : items) {
            result.add(item.optLong("ts"));
        }

        return result;
    }

    private static VideoStart start(long ts, String videoId) {
        return new VideoStart(ts, videoId, VideoStart.TRIGGER_OPEN, true, 5_000, "TV", "sabr", false, 2_500,
                Collections.singletonList(new VideoStart.Stage("buffering", null, 0, 0, 2_500)));
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }
}
