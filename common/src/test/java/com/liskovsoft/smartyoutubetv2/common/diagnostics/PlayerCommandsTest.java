package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.content.Context;

import com.google.android.exoplayer2.Player;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;

import org.json.JSONArray;
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
import org.robolectric.shadows.ShadowSystemClock;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * What the player was told and did before a failure: e.g. 9lKpEOEbr0U stopped at its end (a seek there), never ready, and the
 * stall check reloaded it from 0:00
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PlayerCommandsTest {
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();
    private PlayerFailureLog mLog;
    private int mSavedEngine;

    @Before
    public void setUp() {
        mLog = PlayerFailureLog.useForTesting(mFolder.getRoot());
        PlayerCommands.resetForTesting();
        // Cronet can't load under Robolectric
        mSavedEngine = PlayerTweaksData.instance(getContext()).getPlayerDataSource();
        PlayerTweaksData.instance(getContext()).setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP);
    }

    @After
    public void tearDown() {
        PlayerFailureLog.useForTesting(null);
        PlayerCommands.resetForTesting();
        PlayerTweaksData.instance(getContext()).setPlayerDataSource(mSavedEngine);
    }

    @Test
    public void aFailureHasWhatThePlayerWasToldAndDid() throws Exception {
        PlayerCommands.onNewVideo("9lKpEOEbr0U");
        PlayerCommands.onSeek(PlayerCommands.SOURCE_APP, 1_972_000);
        PlayerCommands.onPlayWhenReady(false, PlayerCommands.SOURCE_APP, 1_972_000);
        PlayerCommands.onState(Player.STATE_BUFFERING, 1_972_000);
        PlayerCommands.onState(Player.STATE_BUFFERING, 1_972_000); // the same state: not again
        PlayerCommands.onState(Player.STATE_ENDED, 1_972_000);
        PlayerCommands.onPlayWhenReady(true, PlayerCommands.SOURCE_MEDIA_SESSION, 1_972_000);
        ShadowSystemClock.advanceBy(20, TimeUnit.SECONDS);

        failure("9lKpEOEbr0U").record();

        JSONArray commands = mLog.readAll().get(0).getJSONArray("commands");

        assertEquals(5, commands.length());
        assertCommand(commands.getJSONObject(0), "seek", PlayerCommands.SOURCE_APP, 1_972_000);
        assertCommand(commands.getJSONObject(1), "pause", PlayerCommands.SOURCE_APP, 1_972_000);
        assertCommand(commands.getJSONObject(2), "buffering", PlayerCommands.SOURCE_PLAYER, 1_972_000);
        assertCommand(commands.getJSONObject(3), "ended", PlayerCommands.SOURCE_PLAYER, 1_972_000);
        assertCommand(commands.getJSONObject(4), "play", PlayerCommands.SOURCE_MEDIA_SESSION, 1_972_000);
        assertEquals(20_000, commands.getJSONObject(4).getLong("msAgo"));
    }

    /**
     * A reload of the video goes on with them, another video has its own
     */
    @Test
    public void anotherVideoHasItsOwn() throws Exception {
        PlayerCommands.onNewVideo("first");
        PlayerCommands.onSeek(PlayerCommands.SOURCE_APP, 1_000);
        PlayerCommands.onNewVideo("first");
        PlayerCommands.onPlayWhenReady(true, PlayerCommands.SOURCE_APP, 1_000);

        assertEquals(2, PlayerCommands.snapshot().length());

        PlayerCommands.onNewVideo("second");

        assertNull(PlayerCommands.snapshot());
        failure("second").record();
        assertFalse(mLog.readAll().get(0).has("commands"));
    }

    @Test
    public void onlyTheLastOnesAreKept() throws Exception {
        PlayerCommands.onNewVideo("first");

        for (int i = 0; i < PlayerCommands.MAX_COMMANDS + 5; i++) {
            PlayerCommands.onSeek(PlayerCommands.SOURCE_APP, i);
        }

        JSONArray commands = PlayerCommands.snapshot();

        assertEquals(PlayerCommands.MAX_COMMANDS, commands.length());
        assertEquals(5, commands.getJSONObject(0).getLong("positionMs"));
    }

    private static void assertCommand(JSONObject command, String event, String source, long positionMs) throws Exception {
        assertEquals(event, command.getString("event"));
        assertEquals(source, command.getString("source"));
        assertEquals(positionMs, command.getLong("positionMs"));
    }

    private static PlayerFailure failure(String videoId) {
        Video video = new Video();
        video.videoId = videoId;

        return PlayerFailure.create(getContext(), PlayerFailure.KIND_STALL).video(video);
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }
}
