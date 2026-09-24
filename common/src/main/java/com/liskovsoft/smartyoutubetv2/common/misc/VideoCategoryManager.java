package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;

/**
 * Hides music, gaming, sports, news and tech videos from Home.<br/>
 * Home cards don't carry the category. It's looked up once per video (one player request) before the row is shown,
 * so the hidden videos never appear, and cached on disk by the video id.<br/>
 * Whole rows go too: the ones marked with a hidden topic (e.g. music shelves) and the ones left with a single video or none.
 */
public class VideoCategoryManager {
    private static final String TAG = VideoCategoryManager.class.getSimpleName();
    // Content flag, the category YouTube gives the video (always in English), the topic of a row (none for tech)
    private static final int[] CONTENT = {
            MediaServiceData.CONTENT_MUSIC_HOME, MediaServiceData.CONTENT_GAMING_HOME,
            MediaServiceData.CONTENT_SPORTS_HOME, MediaServiceData.CONTENT_NEWS_HOME, MediaServiceData.CONTENT_TECH_HOME};
    private static final String[] CATEGORIES = {"Music", "Gaming", "Sports", "News & Politics", "Science & Technology"};
    private static final int[] TOPICS = {
            MediaGroup.TOPIC_MUSIC, MediaGroup.TOPIC_GAMING, MediaGroup.TOPIC_SPORTS, MediaGroup.TOPIC_NEWS, MediaGroup.TOPIC_NONE};
    private static final String CATEGORIES_FILE = "videocategory/categories.tsv";
    private static final String NO_CATEGORY = ""; // the video has no category, don't repeat the lookup this session
    private static final int MAX_CATEGORIES = 5_000;
    private static final long SAVE_DELAY_MS = 10_000;
    @SuppressLint("StaticFieldLeak")
    private static VideoCategoryManager sInstance;
    private final MediaItemService mItemService;
    private final File mCategoriesFile;
    // Access order, so the least recently seen video is dropped first
    private final Map<String, String> mCategoryById = new LinkedHashMap<String, String>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_CATEGORIES;
        }
    };
    private final Runnable mSaveCategories = () -> RxHelper.runAsync(this::saveCategories);

    private VideoCategoryManager(Context context) {
        mItemService = YouTubeServiceManager.instance().getMediaItemService();
        mCategoriesFile = new File(FileHelpers.getFilesDir(context), CATEGORIES_FILE);
        RxHelper.runAsync(this::restoreCategories);
    }

    public static VideoCategoryManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new VideoCategoryManager(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * Some category should be removed from the group
     */
    public boolean isGroupEnabled(int groupType) {
        if (groupType != MediaGroup.TYPE_HOME) {
            return false;
        }

        for (int content : CONTENT) {
            if (isContentHidden(content)) {
                return true;
            }
        }

        return false;
    }

    /**
     * The category is hidden from Home
     */
    public boolean isHidden(String category) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equals(category)) {
                return isContentHidden(CONTENT[i]);
            }
        }

        return false;
    }

    /**
     * The row is about a hidden topic (e.g. a music shelf)
     */
    private boolean isTopicHidden(int topic) {
        if (topic == MediaGroup.TOPIC_NONE) {
            return false;
        }

        for (int i = 0; i < TOPICS.length; i++) {
            if (TOPICS[i] == topic) {
                return isContentHidden(CONTENT[i]);
            }
        }

        return false;
    }

    private static boolean isContentHidden(int content) {
        return MediaServiceData.instance().isContentHidden(content);
    }

    /**
     * @return the category found earlier for the video or null
     */
    public String getCachedCategory(String videoId) {
        if (videoId == null) {
            return null;
        }

        String category;

        synchronized (mCategoryById) {
            category = mCategoryById.get(videoId);
        }

        return NO_CATEGORY.equals(category) ? null : category;
    }

    /**
     * Holds each emission until the categories of its videos are known, so the rows are built without the hidden videos
     */
    public Observable<List<MediaGroup>> resolveGroups(Observable<List<MediaGroup>> groups) {
        if (groups == null) {
            return null;
        }

        // The top row (Recommended) mixes everything, a single video left in it says nothing about the row
        AtomicBoolean isTopRowPending = new AtomicBoolean(true);

        return groups.concatMap(mediaGroups -> resolve(getUnknownVideoIds(mediaGroups), "rows " + getTitles(mediaGroups))
                .map(unused -> removeHiddenRows(mediaGroups, isTopRowPending)));
    }

    /**
     * Holds each emission until the categories of its videos are known, so the group is built without the hidden videos
     */
    public Observable<MediaGroup> resolveGroup(Observable<MediaGroup> group) {
        if (group == null) {
            return null;
        }

        return group.concatMap(mediaGroup -> resolve(getUnknownVideoIds(Collections.singletonList(mediaGroup)),
                "page type=" + (mediaGroup != null ? mediaGroup.getType() : null) + " " + getTitles(Collections.singletonList(mediaGroup))).map(unused -> mediaGroup));
    }

    // CATDBG: temporary logging
    private static String getTitles(List<MediaGroup> mediaGroups) {
        List<String> titles = new ArrayList<>();

        if (mediaGroups != null) {
            for (MediaGroup mediaGroup : mediaGroups) {
                titles.add(mediaGroup != null ? mediaGroup.getTitle() + "(" + (mediaGroup.getMediaItems() != null ? mediaGroup.getMediaItems().size() : 0) + ")" : "null");
            }
        }

        return titles.toString();
    }

    private Observable<Boolean> resolve(List<String> videoIds, String what) {
        Log.d(TAG, "CATDBG resolve %s: %s unknown videos %s", what, videoIds.size(), videoIds);

        if (videoIds.isEmpty()) {
            return Observable.just(true);
        }

        long startMs = System.currentTimeMillis();

        return mItemService.getVideoCategoriesObserve(videoIds)
                .map(categories -> {
                    Log.d(TAG, "CATDBG resolved %s: %s of %s in %s ms", what, categories != null ? categories.size() : 0, videoIds.size(),
                            System.currentTimeMillis() - startMs);
                    onResolved(categories);
                    return true;
                })
                .doOnDispose(() -> Log.d(TAG, "CATDBG resolve %s DISPOSED after %s ms (results dropped)", what, System.currentTimeMillis() - startMs))
                // Show the videos rather than nothing
                .onErrorReturn(error -> {
                    Log.e(TAG, "Can't find the categories: %s", error.getMessage());
                    return true;
                });
    }

    private List<String> getUnknownVideoIds(List<MediaGroup> mediaGroups) {
        List<String> result = new ArrayList<>();

        if (mediaGroups == null) {
            return result;
        }

        synchronized (mCategoryById) {
            for (MediaGroup mediaGroup : mediaGroups) {
                // The videos of a hidden row don't matter
                if (mediaGroup == null || mediaGroup.getMediaItems() == null || !isGroupEnabled(mediaGroup.getType()) ||
                        isTopicHidden(mediaGroup.getTopic())) {
                    continue;
                }

                for (MediaItem item : mediaGroup.getMediaItems()) {
                    String videoId = item != null ? item.getVideoId() : null;

                    if (videoId != null && !mCategoryById.containsKey(videoId)) {
                        result.add(videoId);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Drops the rows that shouldn't be shown at all. Their videos are dropped anyway (see VideoGroup).
     */
    private List<MediaGroup> removeHiddenRows(List<MediaGroup> mediaGroups, AtomicBoolean isTopRowPending) {
        if (mediaGroups == null) {
            return null;
        }

        List<MediaGroup> result = new ArrayList<>();

        for (MediaGroup mediaGroup : mediaGroups) {
            // Empty groups aren't shown (see BrowsePresenter)
            boolean isTopRow = mediaGroup != null && !mediaGroup.isEmpty() && isTopRowPending.getAndSet(false);

            if (!isRowHidden(mediaGroup, isTopRow)) {
                result.add(mediaGroup);
            } else {
                Log.d(TAG, "CATDBG row hidden: %s (topic %s)", mediaGroup.getTitle(), mediaGroup.getTopic());
            }
        }

        return result;
    }

    boolean isRowHidden(MediaGroup mediaGroup) {
        return isRowHidden(mediaGroup, false);
    }

    /**
     * The row is about a hidden topic, or lost videos and has a single video left or none
     * (e.g. a game genre row left with one People & Blogs video, or only "More music" and mix cards).<br/>
     * The top row keeps its single video. Other rows only lose their hidden videos.
     */
    boolean isRowHidden(MediaGroup mediaGroup, boolean isTopRow) {
        if (mediaGroup == null || !isGroupEnabled(mediaGroup.getType())) {
            return false;
        }

        if (isTopicHidden(mediaGroup.getTopic())) {
            return true;
        }

        if (mediaGroup.getMediaItems() == null) {
            return false;
        }

        int videoCount = 0;
        int hiddenCount = 0;

        for (MediaItem item : mediaGroup.getMediaItems()) {
            String videoId = item != null ? item.getVideoId() : null;

            if (videoId == null) {
                continue;
            }

            videoCount++;

            if (isHidden(getCachedCategory(videoId))) {
                hiddenCount++;
            }
        }

        // A row without hidden videos stays, even when it has a single video or none (e.g. channels)
        if (hiddenCount == 0) {
            return false;
        }

        int leftCount = videoCount - hiddenCount;

        return leftCount == 0 || (leftCount == 1 && !isTopRow);
    }

    private void onResolved(Map<String, String> categories) {
        if (categories == null || categories.isEmpty()) {
            return;
        }

        synchronized (mCategoryById) {
            mCategoryById.putAll(categories);
        }

        Utils.postDelayed(mSaveCategories, SAVE_DELAY_MS);
    }

    private void restoreCategories() {
        String content = FileHelpers.getFileContents(mCategoriesFile);

        if (content == null) {
            return;
        }

        synchronized (mCategoryById) {
            for (String line : content.split("\n")) {
                String[] pair = line.split("\t");

                // Entries found this session are newer
                if (pair.length == 2 && !pair[0].isEmpty() && !pair[1].isEmpty() && !mCategoryById.containsKey(pair[0])) {
                    mCategoryById.put(pair[0], pair[1]);
                }
            }
        }
    }

    private void saveCategories() {
        StringBuilder content = new StringBuilder();

        synchronized (mCategoryById) {
            for (Map.Entry<String, String> entry : mCategoryById.entrySet()) {
                if (NO_CATEGORY.equals(entry.getValue()) || entry.getValue().contains("\t") || entry.getValue().contains("\n")) {
                    continue;
                }

                content.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
            }
        }

        File parent = mCategoriesFile.getParentFile();

        if (parent != null && (parent.exists() || parent.mkdirs())) {
            FileHelpers.stringToFile(content.toString(), mCategoriesFile);
        }
    }

    /**
     * Fills the cache without a lookup.
     */
    void setCategoryForTesting(String videoId, String category) {
        synchronized (mCategoryById) {
            mCategoryById.put(videoId, category);
        }
    }
}
