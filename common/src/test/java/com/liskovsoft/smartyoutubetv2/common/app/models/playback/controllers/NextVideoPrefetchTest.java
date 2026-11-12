package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;

import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.misc.VideoCategoryManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.MusicAutoplayData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The formats of the video the end opens by itself are fetched about 20 s before it (see VideoLoaderController#prefetchNextIfNeeded)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NextVideoPrefetchTest {
    private static final String MUSIC_ID = "prefetchMusic";
    private static final String MUSIC_NEXT_ID = "prefetchMusicNext";
    private static final String VLOG_ID = "prefetchVlog";
    private static final long DURATION_MS = 600_000;
    private int mPlaybackMode;
    private boolean mIsMusicAutoplay;
    private int mMusicPlaybackMode;

    @Before
    public void setUp() throws Exception {
        Context context = getContext();
        GlobalPreferences.instance(context);
        // AppPrefs outlives the test, so do the saved values
        mPlaybackMode = PlayerData.instance(context).getPlaybackMode();
        mIsMusicAutoplay = MusicAutoplayData.instance(context).isEnabled();
        mMusicPlaybackMode = MusicAutoplayData.instance(context).getMusicPlaybackMode();

        PlayerData.instance(context).setPlaybackMode(PlayerConstants.PLAYBACK_MODE_ALL);
        MusicAutoplayData.instance(context).setEnabled(false);
        // No queue (it outlives a test): Close and Pause would go on to its next video
        Playlist.instance().clear();

        setCategory(MUSIC_ID, "Music");
        setCategory(MUSIC_NEXT_ID, "Music");
        setCategory(VLOG_ID, "People & Blogs");
    }

    @After
    public void tearDown() {
        Context context = getContext();
        PlayerData.instance(context).setPlaybackMode(mPlaybackMode);
        MusicAutoplayData.instance(context).setEnabled(mIsMusicAutoplay);
        MusicAutoplayData.instance(context).setMusicPlaybackMode(mMusicPlaybackMode);
        MediaServiceData.instance().setDataApiKey(null);
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    /**
     * At 9:30 of 10:00 it waits for 9:40, then fetches the next video's once
     */
    @Test
    public void theNextVideosAreFetched20SecondsBeforeTheEnd() {
        PrefetchController controller = new PrefetchController(video("current"), video("next"), 570_000, 1.0f);

        controller.onPlay();
        idle(9_999);
        assertTrue(controller.mFetched.isEmpty());

        idle(1);
        assertEquals(Collections.singletonList("next"), controller.mFetched);

        // Played on, sought, at another speed: fetched already
        controller.onSeekEnd();
        controller.onSpeedChanged(1.5f);
        idle(30_000);
        assertEquals(1, controller.mFetched.size());
    }

    /**
     * The 20 s are of its media: at 2x, 10 s before its end in real time
     */
    @Test
    public void atTwiceTheSpeedItsHalfTheTime() {
        PrefetchController controller = new PrefetchController(video("current"), video("next"), 570_000, 2.0f);

        controller.onPlay();
        idle(4_999);
        assertTrue(controller.mFetched.isEmpty());

        idle(1);
        assertEquals(Collections.singletonList("next"), controller.mFetched);
    }

    /**
     * A seek into the last 20 s fetches them at once
     */
    @Test
    public void aSeekNearTheEndFetchesThemAtOnce() {
        PrefetchController controller = new PrefetchController(video("current"), video("next"), 60_000, 1.0f);

        controller.onPlay();
        assertTrue(controller.mFetched.isEmpty());

        controller.mPositionMs = 590_000;
        controller.onSeekEnd();
        assertEquals(Collections.singletonList("next"), controller.mFetched);
    }

    /**
     * Paused: nothing's fetched till it plays again
     */
    @Test
    public void notWhilePaused() {
        PrefetchController controller = new PrefetchController(video("current"), video("next"), 590_000, 1.0f);
        controller.setPlaying(false);

        controller.onSeekEnd();
        idle(60_000);
        assertTrue(controller.mFetched.isEmpty());
        assertEquals(590_000, controller.getPositionMs());

        controller.setPlaying(true);
        controller.onPlay();
        assertEquals(Collections.singletonList("next"), controller.mFetched);
    }

    /**
     * The end doesn't open another video in these modes
     */
    @Test
    public void notWhenTheEndOpensNoVideo() {
        for (int mode : new int[] {PlayerConstants.PLAYBACK_MODE_ONE, PlayerConstants.PLAYBACK_MODE_PAUSE,
                PlayerConstants.PLAYBACK_MODE_CLOSE, PlayerConstants.PLAYBACK_MODE_LIST}) {
            PlayerData.instance(getContext()).setPlaybackMode(mode);
            PrefetchController controller = new PrefetchController(video("current"), video("next"), 590_000, 1.0f);

            controller.onPlay();

            assertTrue("mode " + mode, controller.mFetched.isEmpty());
        }
    }

    /**
     * A live stream has no end to fetch ahead of, nor is a live stream fetched ahead
     */
    @Test
    public void notForALiveStream() {
        Video live = video("live");
        live.isLive = true;
        PrefetchController controller = new PrefetchController(live, video("next"), 590_000, 1.0f);
        controller.onPlay();
        assertTrue(controller.mFetched.isEmpty());

        Video liveNext = video("liveNext");
        liveNext.isLive = true;
        controller = new PrefetchController(video("current"), liveNext, 590_000, 1.0f);
        controller.onPlay();
        assertTrue(controller.mFetched.isEmpty());
    }

    /**
     * Offline, or a poToken to make (its WebView runs on the main thread): the start of the video fetches them, as before
     */
    @Test
    public void notWhenTheFetchWouldNeedWhatsMissing() {
        PrefetchController controller = new PrefetchController(video("current"), video("next"), 590_000, 1.0f);
        controller.mIsAllowed = false;

        controller.onPlay();

        assertTrue(controller.mFetched.isEmpty());
    }

    /**
     * Music autoplay: a music video goes on in the mode of the music videos only to music. To another video the mode of the others
     * applies (here Pause after each video): the end opens nothing.
     */
    @Test
    public void aMusicVideoWithMusicAutoplayFetchesOnlyTheMusicAfterIt() {
        enableMusicAutoplay(PlayerConstants.PLAYBACK_MODE_PAUSE, PlayerConstants.PLAYBACK_MODE_ALL);

        PrefetchController toMusic = new PrefetchController(video(MUSIC_ID), video(MUSIC_NEXT_ID), 590_000, 1.0f);
        toMusic.onPlay();
        assertEquals(Collections.singletonList(MUSIC_NEXT_ID), toMusic.mFetched);

        PrefetchController toVlog = new PrefetchController(video(MUSIC_ID), video(VLOG_ID), 590_000, 1.0f);
        toVlog.onPlay();
        assertTrue(toVlog.mFetched.isEmpty());

        // Its category isn't known: not music, as at the end
        PrefetchController toUnknown = new PrefetchController(video(MUSIC_ID), video("prefetchUnknown"), 590_000, 1.0f);
        toUnknown.onPlay();
        assertTrue(toUnknown.mFetched.isEmpty());
    }

    /**
     * The mode of the music videos that doesn't go on to another video applies whatever comes next (here Repeat one), and a video
     * that isn't music has the mode of the others (here Play all) whatever comes next
     */
    @Test
    public void aMusicVideoInARepeatModeFetchesNothing() {
        enableMusicAutoplay(PlayerConstants.PLAYBACK_MODE_ALL, PlayerConstants.PLAYBACK_MODE_ONE);

        PrefetchController toMusic = new PrefetchController(video(MUSIC_ID), video(MUSIC_NEXT_ID), 590_000, 1.0f);
        toMusic.onPlay();
        assertTrue(toMusic.mFetched.isEmpty());

        PrefetchController toVlog = new PrefetchController(video(MUSIC_ID), video(VLOG_ID), 590_000, 1.0f);
        toVlog.onPlay();
        assertTrue(toVlog.mFetched.isEmpty());

        PrefetchController vlog = new PrefetchController(video(VLOG_ID), video(MUSIC_NEXT_ID), 590_000, 1.0f);
        vlog.onPlay();
        assertEquals(Collections.singletonList(MUSIC_NEXT_ID), vlog.mFetched);
    }

    /**
     * One at a time: a next video that changed meanwhile (e.g. one added to the queue) is fetched when the one running is done
     */
    @Test
    public void oneAtATimeAndTheLatestNextVideoWins() {
        PrefetchController controller = new PrefetchController(video("current"), video("next"), 590_000, 1.0f);
        PublishSubject<Void> running = PublishSubject.create();
        controller.mFetch = running;

        controller.onPlay();
        assertEquals(Collections.singletonList("next"), controller.mFetched);

        controller.mNext = video("queued");
        controller.mFetch = Observable.empty();
        controller.onSeekEnd();
        assertEquals(Collections.singletonList("next"), controller.mFetched);

        running.onComplete();
        assertEquals(Arrays.asList("next", "queued"), controller.mFetched);
    }

    /**
     * One that failed isn't fetched again while the video plays: its start fetches it, as without the prefetch
     */
    @Test
    public void aFailedOneIsntFetchedAgain() {
        PrefetchController controller = new PrefetchController(video("current"), video("next"), 590_000, 1.0f);
        controller.mFetch = Observable.error(new IllegalStateException("no client had it"));

        controller.onPlay();
        controller.onSeekEnd();
        controller.onPlay();

        assertEquals(Collections.singletonList("next"), controller.mFetched);
    }

    private void enableMusicAutoplay(int otherMode, int musicMode) {
        MediaServiceData.instance().setDataApiKey("key");
        PlayerData.instance(getContext()).setPlaybackMode(otherMode);
        MusicAutoplayData.instance(getContext()).setEnabled(true);
        MusicAutoplayData.instance(getContext()).setMusicPlaybackMode(musicMode);
    }

    private static void idle(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    private static void setCategory(String videoId, String category) throws Exception {
        Method method = VideoCategoryManager.class.getDeclaredMethod("setCategoryForTesting", String.class, String.class);
        method.setAccessible(true);
        method.invoke(VideoCategoryManager.instance(getContext()), videoId, category);
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }

    private static Video video(String videoId) {
        Video video = new Video();
        video.videoId = videoId;
        return video;
    }

    /**
     * Playing a 10:00 video, its position going on with the clock at its speed. Its fetches are recorded, not done.
     */
    private static class PrefetchController extends VideoLoaderController {
        private final Video mVideo;
        private final PlaybackView mPlayer;
        private final List<String> mFetched = new ArrayList<>();
        private Video mNext;
        private long mPositionMs;
        private long mPositionTimeMs;
        private final float mSpeed;
        private boolean mIsPlaying = true;
        private boolean mIsAllowed = true;
        private Observable<Void> mFetch = Observable.empty();

        private PrefetchController(Video video, Video next, long positionMs, float speed) {
            mVideo = video;
            mNext = next;
            mPositionMs = positionMs;
            mPositionTimeMs = SystemClock.uptimeMillis();
            mSpeed = speed;
            setAltContext(RuntimeEnvironment.getApplication());
            mPlayer = (PlaybackView) Proxy.newProxyInstance(PlaybackView.class.getClassLoader(), new Class<?>[]{PlaybackView.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "isPlaying":
                                return mIsPlaying;
                            case "getDurationMs":
                                return DURATION_MS;
                            case "getPositionMs":
                                return getPositionMs();
                            case "getSpeed":
                                return mSpeed;
                        }

                        Class<?> returned = method.getReturnType();

                        if (returned == boolean.class) {
                            return false;
                        } else if (returned == long.class) {
                            return 0L;
                        } else if (returned == int.class) {
                            return 0;
                        } else if (returned == float.class) {
                            return 0f;
                        }

                        return null;
                    });
            setSuggestions(new SuggestionsController() {
                @Override
                public Video getNext() {
                    return mNext;
                }

                @Override
                public Video getPrevious() {
                    return null;
                }
            });
        }

        /**
         * From where it is now: a paused one stays there
         */
        private void setPlaying(boolean isPlaying) {
            getPositionMs();
            mIsPlaying = isPlaying;
        }

        private long getPositionMs() {
            long now = SystemClock.uptimeMillis();

            if (mIsPlaying) {
                mPositionMs = Math.min(DURATION_MS, mPositionMs + (long) ((now - mPositionTimeMs) * mSpeed));
            }

            mPositionTimeMs = now;

            return mPositionMs;
        }

        private void setSuggestions(SuggestionsController suggestions) {
            try {
                Field field = VideoLoaderController.class.getDeclaredField("mSuggestionsController");
                field.setAccessible(true);
                field.set(this, suggestions);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public Video getVideo() {
            return mVideo;
        }

        @Override
        public PlaybackView getPlayer() {
            return mPlayer;
        }

        @Override
        boolean isPrefetchAllowed() {
            return mIsAllowed;
        }

        @Override
        Observable<Void> getPrefetchObserve(String videoId) {
            mFetched.add(videoId);
            return mFetch;
        }
    }
}
