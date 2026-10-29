package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import com.liskovsoft.youtubeapi.videoinfo.V2.FetchStages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Drives VideoStarts as the player does, on a clock of its own: a video opened from a card, the first of the run, while the warm-up
 * held the lock, with the display mode switched
 */
final class VideoStartFixture {
    static final long T0 = 10_000;
    static final String VIDEO_ID = "dQw4w9WgXcQ";
    long now = T0;
    final List<VideoStart> recorded = new ArrayList<>();
    final List<VideoStart> shown = new ArrayList<>();
    int starts;
    List<FetchStages.Stage> fetchStages = new ArrayList<>();
    /**
     * Of a fetch going on: what peeking at it gives
     */
    List<FetchStages.Stage> fetchStagesSoFar = new ArrayList<>();
    /**
     * The give-up scheduled (see VideoStarts#sGiveUp), null when cancelled
     */
    Runnable giveUp;
    long giveUpDelayMs = -1;
    /**
     * Of the player not loading, in ms since T0: {startMs, endMs}
     */
    final List<long[]> loadHolds = new ArrayList<>();

    /**
     * VideoStarts reset to it: its clock, fetch stages, recorder and listener
     */
    VideoStartFixture() {
        VideoStarts.resetForTesting();
        VideoStarts.sClock = () -> now;
        VideoStarts.sWallClock = () -> 1_792_100_000_000L + now;
        VideoStarts.sProcessStart = () -> 4_000;
        VideoStarts.sFetchStages = new VideoStarts.FetchStageSource() {
            @Override
            public List<FetchStages.Stage> take(String videoId) {
                List<FetchStages.Stage> result = fetchStages;
                fetchStages = new ArrayList<>();
                return result;
            }

            @Override
            public List<FetchStages.Stage> peek(String videoId) {
                return fetchStagesSoFar;
            }
        };
        VideoStarts.sScheduler = new VideoStarts.Scheduler() {
            @Override
            public void schedule(Runnable task, long delayMs) {
                giveUp = task;
                giveUpDelayMs = delayMs;
            }

            @Override
            public void cancel(Runnable task) {
                if (giveUp == task) {
                    giveUp = null;
                }
            }
        };
        VideoStarts.sLoadHolds = (fromMs, toMs) -> {
            List<VideoStart.Stage> result = new ArrayList<>();

            for (long[] hold : loadHolds) {
                long startMs = Math.max(T0 + hold[0], fromMs);
                long endMs = Math.min(T0 + hold[1], toMs);

                if (endMs > startMs) {
                    result.add(new VideoStart.Stage(VideoStarts.STAGE_LOADING_HELD, "buffered 0.0 s, 37 MB", 1, startMs, endMs));
                }
            }

            return result;
        };
        VideoStarts.sRecorder = recorded::add;
        VideoStarts.setListener(new VideoStarts.Listener() {
            @Override
            public void onStart() {
                starts++;
            }

            @Override
            public void onTimed(VideoStart start) {
                shown.add(start);
            }
        });
    }

    /**
     * The time the start has to play is up
     */
    void fireGiveUp() {
        Runnable task = giveUp;
        giveUp = null;
        task.run();
    }

    VideoStartFixture at(long ms) {
        now = T0 + ms;
        return this;
    }

    /**
     * 3420 ms: the formats fetched while the player started, buffered, the display switched, then it played
     */
    VideoStart slowStart() {
        at(0);
        VideoStarts.start(VIDEO_ID, VideoStart.TRIGGER_OPEN);
        VideoStarts.onEngineWait(VIDEO_ID);
        at(4);
        VideoStarts.onFetch(VIDEO_ID);
        fetchStages = stages(
                stage("fetch", null, 0, 9, 1590),
                stage("lock wait", "warm-up", 1, 9, 219),
                stage("sign-in", null, 1, 219, 281),
                stage("TV attempt", "ok", 1, 281, 1211),
                stage("poToken", "TV", 2, 283, 893),
                stage("player JS", null, 2, 893, 893),
                stage("request", null, 2, 951, 1211),
                stage("subtitles", "WEB_DUMMY", 1, 1213, 1513),
                stage("decipher", null, 1, 1515, 1585));
        at(52);
        VideoStarts.onEngineInitialized();
        at(1844);
        VideoStarts.onFormats(VIDEO_ID, "TV");
        at(1859);
        VideoStarts.onPrepare();
        VideoStarts.onProtocol("sabr");
        at(2700);
        VideoStarts.onLoad(false, true, false, T0 + 1870, T0 + 2700, 2_100_000);
        at(2771);
        VideoStarts.onDecoder(true, "OMX.Nvidia.vp9.decode", 40);
        at(2790);
        VideoStarts.onModeSwitchStart();
        at(3105);
        VideoStarts.onFirstFrame();
        at(3410);
        VideoStarts.onModeSwitchEnd();
        at(3420);
        VideoStarts.onPlaying(true);

        return recorded.get(recorded.size() - 1);
    }

    /**
     * A stage of the fetch, in ms since T0
     */
    static FetchStages.Stage stage(String name, String detail, int depth, long startMs, long endMs) {
        return new FetchStages.Stage(name, detail, depth, T0 + startMs, T0 + endMs);
    }

    /**
     * A stage of the fetch still going, from ms since T0
     */
    static FetchStages.Stage openStage(String name, int depth, long startMs) {
        return new FetchStages.Stage(name, null, depth, T0 + startMs, FetchStages.Stage.OPEN);
    }

    static List<FetchStages.Stage> stages(FetchStages.Stage... stages) {
        return new ArrayList<>(Arrays.asList(stages));
    }
}
