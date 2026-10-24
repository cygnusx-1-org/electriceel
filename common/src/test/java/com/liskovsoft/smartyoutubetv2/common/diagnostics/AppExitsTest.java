package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.content.Context;

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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AppExitsTest {
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();
    private int mSavedEngine;

    @Before
    public void setUp() {
        PlayerFailureLog.useForTesting(mFolder.getRoot());
        FormatAttempts.resetForTesting();
        // Cronet can't load under Robolectric
        mSavedEngine = PlayerTweaksData.instance(getContext()).getPlayerDataSource();
        PlayerTweaksData.instance(getContext()).setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP);
    }

    @After
    public void tearDown() {
        FormatAttempts.resetForTesting();
        PlayerFailureLog.useForTesting(null);
        PlayerTweaksData.instance(getContext()).setPlayerDataSource(mSavedEngine);
    }

    /**
     * On the disk before the handler returns (the app dies next), with the video playing
     */
    @Test
    public void aCrashIsWrittenRightAway() throws Exception {
        FormatAttempts.onNewVideo(getContext(), "playing");

        AppExits.recordCrash(getContext(), new Thread("worker"), new IllegalStateException("Boom"));

        String written = new String(Files.readAllBytes(new File(mFolder.getRoot(), PlayerFailureLog.CURRENT).toPath()), StandardCharsets.UTF_8);
        assertTrue(written, written.contains("\"kind\":\"crash\""));
        assertTrue(written, written.contains("\"videoId\":\"playing\""));
        assertTrue(written, written.contains("\"thread\":\"worker\""));
        assertTrue(written, written.contains("Boom"));
    }

    @Test
    public void noExitsBeforeAndroid11() {
        // Robolectric runs as Android 9: nothing read, nothing thrown
        AppExits.recordNew(getContext());
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }
}
