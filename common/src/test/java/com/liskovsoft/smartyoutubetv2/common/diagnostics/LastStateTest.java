package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.content.Context;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LastStateTest {
    private static final long T0 = 1_792_000_000_000L;
    private final long[] mNow = {0};

    @Before
    public void setUp() {
        LastState.resetForTesting(getContext());
        LastState.sClock = () -> mNow[0];
        LastState.sWallClock = () -> T0 + mNow[0];
        LastState.sPid = () -> 100;
    }

    @After
    public void tearDown() {
        LastState.resetForTesting(getContext());
    }

    /**
     * What the process did before it exited: its services, whether a video played, how long before
     */
    @Test
    public void theExitOfAProcessHasWhatItWasDoing() throws Exception {
        LastState.onServiceStarted(getContext(), LastState.SERVICE_REMOTE_CONTROL);
        LastState.onPlayer(getContext(), true, true);

        JSONObject state = LastState.find(getContext(), 100, T0 + 60_000);

        assertNotNull(state);
        assertEquals(LastState.SERVICE_REMOTE_CONTROL, state.getJSONArray("services").getString(0));
        assertTrue(state.getBoolean("player"));
        assertTrue(state.getBoolean("playing"));
        assertEquals(60_000, state.getLong("agoMs"));
        assertTrue(state.has("heapUsedMb"));
        assertTrue(state.has("importance"));
        // Said by agoMs, and of the exit
        assertFalse(state.has("ts"));
        assertFalse(state.has("pid"));
    }

    @Test
    public void anotherProcessesStateIsntItsExits() {
        LastState.onPlayer(getContext(), true, true);

        assertNull(LastState.find(getContext(), 200, T0 + 1_000));
    }

    /**
     * The next process saves its own: the exit found on its start is of the one before, kept
     */
    @Test
    public void theProcessBeforesStateIsKept() throws Exception {
        LastState.onPlayer(getContext(), true, true);
        LastState.sPid = () -> 101;
        mNow[0] = 5_000;
        LastState.onServiceStarted(getContext(), LastState.SERVICE_REMOTE_CONTROL);

        assertTrue(LastState.find(getContext(), 100, T0 + 10_000).getBoolean("playing"));
        assertEquals(1, LastState.find(getContext(), 101, T0 + 10_000).getJSONArray("services").length());
    }

    /**
     * Saved after the exit: not of it. Long before it is: nothing changed since, or it would have been saved (e.g. idle for hours
     * with the remote control's service running).
     */
    @Test
    public void anOldStateStillTellsButNotOneAfterTheExit() throws Exception {
        LastState.onServiceStarted(getContext(), LastState.SERVICE_REMOTE_CONTROL);

        assertNull(LastState.find(getContext(), 100, T0 - 1));
        assertEquals(3_600_000, LastState.find(getContext(), 100, T0 + 3_600_000).getLong("agoMs"));
    }

    /**
     * While nothing changes, at most every 30 s: a check of the playback is every 2 s
     */
    @Test
    public void thePlayersStateIsSavedWhenItChangesOrNowAndThen() throws Exception {
        LastState.onPlayer(getContext(), true, false);
        mNow[0] = 10_000;
        LastState.onPlayer(getContext(), true, false);
        assertEquals(20_000, LastState.find(getContext(), 100, T0 + 20_000).getLong("agoMs"));

        LastState.onPlayer(getContext(), true, true);
        assertEquals(10_000, LastState.find(getContext(), 100, T0 + 20_000).getLong("agoMs"));

        mNow[0] = 10_000 + LastState.MIN_INTERVAL_MS;
        LastState.onPlayer(getContext(), true, true);
        assertEquals(0, LastState.find(getContext(), 100, T0 + mNow[0]).getLong("agoMs"));
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }
}
