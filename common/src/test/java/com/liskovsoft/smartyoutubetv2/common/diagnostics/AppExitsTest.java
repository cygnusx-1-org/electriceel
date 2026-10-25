package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ApplicationExitInfo;
import android.content.Context;

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
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AppExitsTest {
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();
    private PlayerFailureLog mLog;
    private int mSavedEngine;

    @Before
    public void setUp() {
        mLog = PlayerFailureLog.useForTesting(mFolder.getRoot());
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

    /**
     * Killed in the background, e.g. by the low memory killer of a device that doesn't report its kills: how important the
     * process was, the signal, and whether the device reports them
     */
    @Test
    @Config(sdk = 30)
    public void aSignaledExitHasItsImportanceAndSignal() throws Exception {
        ActivityManager activityManager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        shadowOf(activityManager).addApplicationExitInfo(ApplicationExitInfoBuilder.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .setReason(ApplicationExitInfo.REASON_SIGNALED)
                .setStatus(9)
                .setImportance(RunningAppProcessInfo.IMPORTANCE_CACHED)
                .setProcessName(getContext().getPackageName())
                .build());

        AppExits.recordNew(getContext());

        JSONObject details = mLog.readAll().get(0).getJSONObject("details");
        assertEquals("signaled", details.getString("reason"));
        assertFalse(details.getBoolean("foreground"));
        assertEquals(RunningAppProcessInfo.IMPORTANCE_CACHED, details.getInt("importance"));
        assertEquals(9, details.getInt("status"));
        assertEquals(ActivityManager.isLowMemoryKillReportSupported(), details.getBoolean("lowMemoryKillReport"));
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }
}
