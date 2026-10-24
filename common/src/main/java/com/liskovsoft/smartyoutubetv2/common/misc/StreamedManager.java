package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.StreamedData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;

/**
 * Finds the live streams that ended among the History cards, for the Streamed setting (see StreamedData).
 * Elsewhere the date on the card tells them ("Streamed 2 days ago", see CompactInfo.isStreamed), but History cards come without one.<br/>
 * It's looked up before the cards are shown (see HiddenVideoResolver), a player request per video, while the setting applies to History.
 * It never changes once the stream ended, so it's kept on disk by the video id and looked up once.
 */
public class StreamedManager {
    private static final String TAG = StreamedManager.class.getSimpleName();
    private static final String VIDEOS_FILE = "streamed/videos.tsv";
    private static final String STREAMED = "1";
    private static final String NOT_STREAMED = "0";
    private static final int MAX_VIDEOS = 5_000;
    private static final long SAVE_DELAY_MS = 10_000;
    @SuppressLint("StaticFieldLeak")
    private static StreamedManager sInstance;
    private final Context mContext;
    private final File mVideosFile;
    private Lookup mLookup;
    // Video id -> it's a live stream that ended. Access order, so the least recently seen video is dropped first.
    private final Map<String, Boolean> mIsStreamedById = new LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_VIDEOS;
        }
    };
    // The videos saved on disk are known
    private final BehaviorSubject<Boolean> mRestored = BehaviorSubject.create();
    private final Runnable mSaveVideos = () -> RxHelper.runAsync(this::saveVideos);

    interface Lookup {
        /**
         * @return video id -> it's a live stream that ended. The live, upcoming and failed videos are left out.
         */
        Observable<Map<String, Boolean>> getStreamed(List<String> videoIds);
    }

    private StreamedManager(Context context) {
        mContext = context;
        mLookup = YouTubeServiceManager.instance().getMediaItemService()::getStreamedObserve;
        mVideosFile = new File(FileHelpers.getFilesDir(context), VIDEOS_FILE);
        RxHelper.runAsync(this::restoreVideos);
    }

    /**
     * Synchronized: the groups are resolved off the main thread (see HiddenVideoResolver) and built on it (see VideoGroup)
     */
    public static synchronized StreamedManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new StreamedManager(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * The cards of the section are looked up: History's, while the setting applies there (see VideoGroup.getFilterSectionId).
     * Not kept: the setting is the account's.
     */
    public boolean isLookedUp(int sectionId) {
        return sectionId == MediaGroup.TYPE_HISTORY && StreamedData.instance(mContext).isEnabled(sectionId);
    }

    /**
     * A video card the setting reads: the live and upcoming ones are left alone (see VideoGroup), shorts are never streams
     */
    public static boolean isCandidate(Video video) {
        return video != null && video.videoId != null && !video.isChapter && !video.isLive && !video.isUpcoming && !video.isShorts;
    }

    /**
     * Holds until the videos are known (see HiddenVideoResolver).
     * A failed lookup doesn't hold the cards back: they're shown unmarked, and it's tried again with the next ones.
     */
    public Observable<Boolean> resolve(Collection<String> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            return Observable.just(true);
        }

        return mRestored.take(1).concatMap(restored -> {
            List<String> unknownIds = getUnknownVideoIds(videoIds);

            if (unknownIds.isEmpty()) {
                return Observable.just(true);
            }

            return mLookup.getStreamed(unknownIds)
                    .map(streamed -> {
                        onResolved(streamed);
                        return true;
                    })
                    // Show the cards rather than nothing
                    .onErrorReturn(error -> {
                        Log.e(TAG, "Can't find the live streams: %s", error.getMessage());
                        return true;
                    });
        });
    }

    private List<String> getUnknownVideoIds(Collection<String> videoIds) {
        Set<String> result = new LinkedHashSet<>();

        synchronized (mIsStreamedById) {
            for (String videoId : videoIds) {
                if (videoId != null && !mIsStreamedById.containsKey(videoId)) {
                    result.add(videoId);
                }
            }
        }

        return new ArrayList<>(result);
    }

    private void onResolved(Map<String, Boolean> streamed) {
        if (streamed == null || streamed.isEmpty()) {
            return;
        }

        synchronized (mIsStreamedById) {
            mIsStreamedById.putAll(streamed);
        }

        Utils.postDelayed(mSaveVideos, SAVE_DELAY_MS);
    }

    /**
     * A lookup found it's a live stream that ended (see resolve)
     */
    public boolean isStreamed(String videoId) {
        if (videoId == null) {
            return false;
        }

        synchronized (mIsStreamedById) {
            return Boolean.TRUE.equals(mIsStreamedById.get(videoId));
        }
    }

    /**
     * Looks up with the given one from now on and forgets the videos
     */
    void resetForTesting(Lookup lookup) {
        mLookup = lookup;

        synchronized (mIsStreamedById) {
            mIsStreamedById.clear();
        }

        mRestored.onNext(true);
    }

    File getVideosFileForTesting() {
        return mVideosFile;
    }

    void saveVideosForTesting() {
        saveVideos();
    }

    void restoreVideosForTesting() {
        synchronized (mIsStreamedById) {
            mIsStreamedById.clear();
        }

        restoreVideos();
    }

    private void restoreVideos() {
        Map<String, Boolean> restored = new LinkedHashMap<>();

        try {
            String content = FileHelpers.getFileContents(mVideosFile);

            for (String line : content != null ? content.split("\n") : new String[0]) {
                // Video id, it's a live stream that ended
                String[] fields = line.split("\t", -1);

                if (fields.length == 2 && !fields[0].isEmpty() && (STREAMED.equals(fields[1]) || NOT_STREAMED.equals(fields[1]))) {
                    restored.put(fields[0], STREAMED.equals(fields[1]));
                }
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Can't read %s: %s", mVideosFile, e.getMessage());
        }

        // Always emitted, on the main thread like the lookups: the cards wait for it
        Utils.post(() -> {
            synchronized (mIsStreamedById) {
                for (Map.Entry<String, Boolean> entry : restored.entrySet()) {
                    // Found this session meanwhile: the same answer
                    if (!mIsStreamedById.containsKey(entry.getKey())) {
                        mIsStreamedById.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            mRestored.onNext(true);
        });
    }

    private void saveVideos() {
        StringBuilder content = new StringBuilder();

        synchronized (mIsStreamedById) {
            for (Map.Entry<String, Boolean> entry : mIsStreamedById.entrySet()) {
                if (entry.getValue() == null || entry.getKey().contains("\t") || entry.getKey().contains("\n")) {
                    continue;
                }

                content.append(entry.getKey()).append('\t').append(entry.getValue() ? STREAMED : NOT_STREAMED).append('\n');
            }
        }

        File parent = mVideosFile.getParentFile();

        if (parent != null && (parent.exists() || parent.mkdirs())) {
            FileHelpers.stringToFile(content.toString(), mVideosFile);
        }
    }
}
