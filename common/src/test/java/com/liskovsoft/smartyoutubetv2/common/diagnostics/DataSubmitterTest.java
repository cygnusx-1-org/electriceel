package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.DebuggingData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DataSubmitterTest {
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();
    private int mSavedEngine;
    private long mSavedLastTs;
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
        mSavedLastId = data.getLastSubmissionId();
        mSavedLastTime = data.getLastSubmissionTime();
    }

    @After
    public void tearDown() {
        PlayerFailureLog.useForTesting(null);
        PlayerTweaksData.instance(getContext()).setPlayerDataSource(mSavedEngine);
        DebuggingData.instance(getContext()).setLastSubmission(mSavedLastId, mSavedLastTs, mSavedLastTime);
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

        DebuggingData.instance(getContext()).setLastSubmission("K7M2QX9P", newest, System.currentTimeMillis());

        assertNull(DataSubmitter.submit(getContext(), true));
    }

    @Test
    public void noFailuresArentSentOnStartup() throws Exception {
        DebuggingData.instance(getContext()).setLastSubmission("K7M2QX9P", 0, System.currentTimeMillis());

        assertNull(DataSubmitter.submit(getContext(), true));
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }
}
