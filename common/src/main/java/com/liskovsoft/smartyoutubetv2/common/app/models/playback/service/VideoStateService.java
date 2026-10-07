package com.liskovsoft.smartyoutubetv2.common.app.models.playback.service;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class VideoStateService implements ProfileChangeListener {
    @SuppressLint("StaticFieldLeak")
    private static VideoStateService sInstance;
    private static final int LOW_RAM_STATE_MAX_SIZE = 500;
    private static final int HIGH_RAM_STATE_MAX_SIZE = 5_000;
    private static final long PERSIST_DELAY_MS = 10_000;
    // The app finishes a second after persistNow(). A full history took ~250 ms to write on the emulator.
    private static final long PERSIST_NOW_TIMEOUT_MS = 1_000;
    // Don't store state inside Video object.
    // As one video might correspond to multiple Video objects.
    //private final Map<String, State> mStates = Helpers.createLRUMap(MAX_PERSISTENT_STATE_SIZE);
    private final List<State> mStates;
    private final int mMaxSize;
    private final AppPrefs mPrefs;
    private static final String DELIM = "&si;";
    private static final byte[] DELIM_BYTES = DELIM.getBytes(UTF_8);
    private static final byte[] STATE_DELIM_BYTES = State.DELIM.getBytes(UTF_8);
    private static final byte[] DATA_DELIM_BYTES = "%OB%".getBytes(UTF_8); // Helpers.mergeData's
    private static final byte[] NULL_BYTES = "null".getBytes(UTF_8);
    private boolean mIsHistoryBroken;
    // The history is written off the main thread, one write at a time, in order
    private final ExecutorService mPersistExecutor = Executors.newSingleThreadExecutor();
    // Reading the history waits for a write of it to finish
    private final Object mFileLock = new Object();
    private final Runnable mPersistStateInt = () -> persistStateInt();
    private final Runnable mPersistNowInt = this::persistNowInt;
    private long mSessionStartTimeMs;

    private VideoStateService(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        mMaxSize = Utils.isEnoughRam() ? HIGH_RAM_STATE_MAX_SIZE : LOW_RAM_STATE_MAX_SIZE;
        mStates = Helpers.createSafeLRUList(mMaxSize);
        restoreState();
    }

    public static VideoStateService instance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new VideoStateService(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * The next instance reads the saved states again. A write under way finishes first, so it can't land in the next test.
     */
    static void resetInstanceForTesting() throws InterruptedException {
        if (sInstance != null) {
            sInstance.mPrefs.removeListener(sInstance);
            sInstance.mPersistExecutor.shutdown();
            sInstance.mPersistExecutor.awaitTermination(PERSIST_NOW_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        sInstance = null;
    }

    public List<State> getStates() {
        return mStates;
    }

    public @Nullable State getLastState() {
        if (isEmpty()) {
            return null;
        }

        return mStates.get(mStates.size() - 1);
    }

    public State getByVideoId(String videoId) {
        for (State state : mStates) {
            if (Helpers.equals(videoId, state.video.videoId)) {
                return state;
            }
        }

        return null;
    }

    public void removeByVideoId(String videoId) {
        Helpers.removeIf(mStates, state -> Helpers.equals(state.video.videoId, videoId));
        persistState();
    }

    public boolean isEmpty() {
        return mStates.isEmpty();
    }

    public void save(State state) {
        mStates.add(state);
        persistState();
    }

    public void clear() {
        mStates.clear();
        persistState();
    }

    public void setHistoryBroken(boolean isBroken) {
        mIsHistoryBroken = isBroken;
    }

    public boolean isHistoryBroken() {
        return mIsHistoryBroken;
    }

    public long getSessionStartTimeMs() {
        return mSessionStartTimeMs;
    }

    private void restoreState() {
        mStates.clear();
        mIsHistoryBroken = false;
        byte[] data;

        synchronized (mFileLock) {
            data = mPrefs.getStateUpdaterBytes();
        }

        // Like Helpers.splitData: nothing
        if (data != null && !isBlank(data, 0, data.length)) {
            setStateData(data);
        }

        mSessionStartTimeMs = System.currentTimeMillis();
    }

    /**
     * Takes the states as they are now, on the main thread, and writes them off it
     */
    private Future<?> persistStateInt() {
        List<State> states = new ArrayList<>(mStates);
        String key = mPrefs.getStateUpdaterKey();
        boolean isHistoryBroken = mIsHistoryBroken;

        return mPersistExecutor.submit(() -> {
            try {
                String data = isHistoryBroken ? Helpers.mergeData(getStateData(states), isHistoryBroken)
                        : getStateData(states); // Eliminate additional string creation with the merge

                synchronized (mFileLock) {
                    mPrefs.setData(key, data);
                }
            } catch (OutOfMemoryError e) {
                // NOP
            }
        });
    }

    /**
     * Writes the states before the app finishes or restarts, and holds the main thread until the write is done
     */
    public void persistNow() {
        Utils.removeCallbacks(mPersistStateInt);
        Utils.post(mPersistNowInt);
    }

    private void persistNowInt() {
        try {
            persistStateInt().get(PERSIST_NOW_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void persistState() {
        // Improve memory and disc usage
        Utils.postDelayed(mPersistStateInt, PERSIST_DELAY_MS);
    }

    public static class State {
        private static final String DELIM = "&sf;";
        public final Video video;
        public final long positionMs;
        public final long durationMs;
        public final float speed;
        public final long timestamp = System.currentTimeMillis();

        public State(Video video, long positionMs) {
            this(video, positionMs, -1);
        }

        public State(Video video, long positionMs, long durationMs) {
            this(video, positionMs, durationMs, 1.0f);
        }

        public State(Video video, long positionMs, long durationMs, float speed) {
            this.video = video;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.speed = speed;
        }

        public static State from(String spec) {
            if (spec == null) {
                return null;
            }

            return from(Helpers.split(spec, DELIM));
        }

        private static State from(String[] split) {
            String videoId = Helpers.parseStr(split, 0);
            long positionMs = Helpers.parseLong(split, 1);
            long durationMs = Helpers.parseLong(split, 2);
            float speed = Helpers.parseFloat(split, 3);

            Video video = Video.fromString(videoId);

            // backward compatibility
            if (video == null) {
                video = new Video();
                video.videoId = videoId;
            }

            if (durationMs == -1) {
                durationMs = video.getDurationMs();
            }

            if (durationMs > 0) {
                video.percentWatched = (positionMs * 100f) / durationMs;
            }

            return new State(video, positionMs, durationMs, speed);
        }

        @NonNull
        @Override
        public String toString() {
            return Helpers.merge(DELIM, video, positionMs, durationMs, speed);
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj instanceof State) {
                return Helpers.equals(video, ((State) obj).video);
            }

            return false;
        }

        /**
         * Equal videos have equal hash codes (Video.equals compares them)
         */
        @Override
        public int hashCode() {
            return video != null ? video.hashCode() : 0;
        }
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }

    /**
     * Read as Helpers.splitData, Helpers.split(DELIM) and State.from read the text, but from the bytes it's decoded from, over the cores:
     * decoding and splitting thousands of states took most of the restore. The delimiters are ASCII, so they split the UTF-8 bytes
     * where they split the text.
     */
    private void setStateData(byte[] data) {
        int chunkCount = Runtime.getRuntime().availableProcessors() * 4;
        int chunkSize = data.length / chunkCount + 1;
        int[][] delims = new int[chunkCount][];
        int[] dataDelims = new int[chunkCount];

        IntStream.range(0, chunkCount).parallel().forEach(i -> {
            int from = Math.min(data.length, i * chunkSize);
            int to = Math.min(data.length, from + chunkSize);
            delims[i] = findAll(data, DELIM_BYTES, from, to);
            dataDelims[i] = indexOf(data, DATA_DELIM_BYTES, from, to);
        });

        // The states, then whether the history is broken
        int end = data.length;

        for (int dataDelim : dataDelims) {
            if (dataDelim != -1) {
                end = dataDelim;
                int flagStart = dataDelim + DATA_DELIM_BYTES.length;
                int flagEnd = indexOf(data, DATA_DELIM_BYTES, flagStart, data.length);
                mIsHistoryBroken = Boolean.parseBoolean(new String(data, flagStart, (flagEnd != -1 ? flagEnd : data.length) - flagStart, UTF_8));
                break;
            }
        }

        // Like Helpers.parseStr and Helpers.split: no states
        if (isBlank(data, 0, end) || (end == NULL_BYTES.length && startsWith(data, NULL_BYTES, 0))) {
            return;
        }

        int count = 1;

        for (int[] chunk : delims) {
            for (int delim : chunk) {
                if (delim < end) {
                    count++;
                }
            }
        }

        int[] starts = new int[count];
        int[] ends = new int[count];
        int index = 0;
        int start = 0;

        for (int[] chunk : delims) {
            for (int delim : chunk) {
                if (delim < end) {
                    starts[index] = start;
                    ends[index++] = delim;
                    start = delim + DELIM_BYTES.length;
                }
            }
        }

        starts[index] = start;
        ends[index] = end;

        // Like String.split: no empty parts at the end
        while (count > 0 && starts[count - 1] == ends[count - 1]) {
            count--;
        }

        State[] parsed = new State[count];

        // Each has its own place, so they stay in order
        IntStream.range(0, count).parallel().forEach(i -> parsed[i] = parseState(data, starts[i], ends[i]));

        List<State> states = new ArrayList<>(parsed.length);

        for (State state : parsed) {
            if (state != null) {
                states.add(state);
            }
        }

        // Adding them one by one compares each with all the states before it, millions of times for thousands of states
        mStates.addAll(getLatestDistinct(states));
    }

    /**
     * State.from of the text in [start, end), split as Helpers.split splits it, so each part is decoded once
     */
    private static State parseState(byte[] data, int start, int end) {
        if (isBlank(data, start, end)) {
            return State.from(new String[0]);
        }

        int[] delims = findAll(data, STATE_DELIM_BYTES, start, end);
        String[] split = new String[delims.length + 1];
        int partStart = start;

        for (int i = 0; i < delims.length; i++) {
            split[i] = new String(data, partStart, delims[i] - partStart, UTF_8);
            partStart = delims[i] + STATE_DELIM_BYTES.length;
        }

        split[delims.length] = new String(data, partStart, end - partStart, UTF_8);
        int count = split.length;

        // Like String.split: no empty parts at the end
        while (count > 0 && split[count - 1].isEmpty()) {
            count--;
        }

        return State.from(count == split.length ? split : Arrays.copyOf(split, count));
    }

    /**
     * Where the target starts in [from, to)
     */
    private static int[] findAll(byte[] data, byte[] target, int from, int to) {
        List<Integer> found = new ArrayList<>();
        int last = Math.min(to, data.length - target.length + 1);
        byte first = target[0];

        for (int i = from; i < last; i++) {
            if (data[i] == first && startsWith(data, target, i)) {
                found.add(i);
                i += target.length - 1; // as String.split, though the delimiter can't overlap itself
            }
        }

        int[] result = new int[found.size()];

        for (int i = 0; i < result.length; i++) {
            result[i] = found.get(i);
        }

        return result;
    }

    /**
     * Where the target starts first in [from, to)
     */
    private static int indexOf(byte[] data, byte[] target, int from, int to) {
        int last = Math.min(to, data.length - target.length + 1);
        byte first = target[0];

        for (int i = from; i < last; i++) {
            if (data[i] == first && startsWith(data, target, i)) {
                return i;
            }
        }

        return -1;
    }

    private static boolean startsWith(byte[] data, byte[] target, int at) {
        for (int i = 0; i < target.length; i++) {
            if (data[at + i] != target[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Like String.trim().isEmpty(): no char above a space. UTF-8 bytes of other chars are all above it.
     */
    private static boolean isBlank(byte[] data, int from, int to) {
        for (int i = from; i < to; i++) {
            if ((data[i] & 0xFF) > ' ') {
                return false;
            }
        }

        return true;
    }

    /**
     * The states the list keeps when they're added one by one: the last of the equal ones, in order, the latest that fit.
     * It holds one over its size, as it drops the eldest only when it's over its size before an add.
     */
    private List<State> getLatestDistinct(List<State> states) {
        Set<State> added = new HashSet<>();
        List<State> result = new ArrayList<>();

        for (int i = states.size() - 1; i >= 0 && result.size() <= mMaxSize; i--) {
            State state = states.get(i);

            if (added.add(state)) {
                result.add(state);
            }
        }

        Collections.reverse(result);

        return result;
    }

    private static String getStateData(List<State> states) {
        StringBuilder sb = new StringBuilder();

        for (State state : states) {
            if (sb.length() != 0) {
                sb.append(DELIM);
            }

            sb.append(state);
        }

        return sb.toString();
    }
}
