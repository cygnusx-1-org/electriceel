package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.VideoCategory;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;

/**
 * Hides music, gaming, sports, news and tech videos from Home.<br/>
 * Home cards don't carry the category. It's looked up once per video before the row is shown,
 * so the hidden videos never appear, and cached on disk by the video id.<br/>
 * The category is whatever the uploader picked (e.g. many music mixes are People & Blogs). With the user's own Data API key,
 * the lookup also brings the topics YouTube finds in the video itself (e.g. Music), and either one hides the video.<br/>
 * Whole rows go too: the ones marked with a hidden topic (e.g. music shelves) and the ones left with a single video or none.
 */
public class VideoCategoryManager {
    private static final String TAG = VideoCategoryManager.class.getSimpleName();
    // Content flag, the category YouTube gives the video (always in English), the topic of a row (none for tech)
    private static final int[] CONTENT = {
            MediaServiceData.CONTENT_MUSIC_HOME, MediaServiceData.CONTENT_GAMING_HOME,
            MediaServiceData.CONTENT_SPORTS_HOME, MediaServiceData.CONTENT_NEWS_HOME, MediaServiceData.CONTENT_TECH_HOME};
    private static final String[] CATEGORIES = {"Music", "Gaming", "Sports", "News & Politics", "Science & Technology"};
    // The parent topics of the Data API (Wikipedia page names), every sub-genre carries them too (e.g. Electronic_music + Music)
    private static final String[] VIDEO_TOPICS = {"Music", "Video_game_culture", "Sport", "Politics", "Technology"};
    private static final int[] TOPICS = {
            MediaGroup.TOPIC_MUSIC, MediaGroup.TOPIC_GAMING, MediaGroup.TOPIC_SPORTS, MediaGroup.TOPIC_NEWS, MediaGroup.TOPIC_NONE};
    private static final String CATEGORIES_FILE = "videocategory/categories.tsv";
    private static final String NO_CATEGORY = ""; // the video has no category, don't repeat the lookup this session
    private static final String TOPIC_DELIM = "|"; // not in Wikipedia page names
    private static final int MAX_CATEGORIES = 5_000;
    private static final long SAVE_DELAY_MS = 10_000;
    @SuppressLint("StaticFieldLeak")
    private static VideoCategoryManager sInstance;
    private final MediaItemService mItemService;
    private final File mCategoriesFile;
    // Access order, so the least recently seen video is dropped first
    private final Map<String, VideoCategory> mCategoryById = new LinkedHashMap<String, VideoCategory>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, VideoCategory> eldest) {
            return size() > MAX_CATEGORIES;
        }
    };
    // Videos known without topics whose topic lookup was tried this session (e.g. the key is out of quota), not to repeat it on every load
    private final Set<String> mTopicLookupTried = new HashSet<>();
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
     * The category or one of the topics of the video found earlier is hidden from Home
     */
    public boolean isVideoHidden(String videoId) {
        VideoCategory category = getCached(videoId);

        return category != null && isHidden(category.getCategory(), category.getTopics());
    }

    /**
     * The category or one of the topics is hidden from Home
     */
    boolean isHidden(String category, List<String> topics) {
        for (int i = 0; i < CONTENT.length; i++) {
            if (isContentHidden(CONTENT[i]) && (CATEGORIES[i].equals(category) || (topics != null && topics.contains(VIDEO_TOPICS[i])))) {
                return true;
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
        VideoCategory category = getCached(videoId);

        return category == null || NO_CATEGORY.equals(category.getCategory()) ? null : category.getCategory();
    }

    private VideoCategory getCached(String videoId) {
        if (videoId == null) {
            return null;
        }

        synchronized (mCategoryById) {
            return mCategoryById.get(videoId);
        }
    }

    private static boolean isDataApiKeySet() {
        return MediaServiceData.instance().getDataApiKey() != null;
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

        return groups.concatMap(mediaGroups -> resolve(mediaGroups)
                .map(unused -> removeHiddenRows(mediaGroups, isTopRowPending)));
    }

    /**
     * Holds each emission until the categories of its videos are known, so the group is built without the hidden videos
     */
    public Observable<MediaGroup> resolveGroup(Observable<MediaGroup> group) {
        if (group == null) {
            return null;
        }

        return group.concatMap(mediaGroup -> resolve(Collections.singletonList(mediaGroup)).map(unused -> mediaGroup));
    }

    /**
     * The unknown videos get the full lookup. The ones known without topics only get the Data API:
     * the player would only repeat the category they have (e.g. while the key is out of quota).
     */
    private Observable<Boolean> resolve(List<MediaGroup> mediaGroups) {
        List<String> videoIds = new ArrayList<>();
        List<String> topicVideoIds = new ArrayList<>();

        getUnknownVideoIds(mediaGroups, videoIds, topicVideoIds);

        if (videoIds.isEmpty() && topicVideoIds.isEmpty()) {
            return Observable.just(true);
        }

        return Observable.zip(
                videoIds.isEmpty() ? Observable.just(true) : resolve(mItemService.getVideoCategoriesObserve(videoIds)),
                topicVideoIds.isEmpty() ? Observable.just(true) : resolve(mItemService.getVideoTopicsObserve(topicVideoIds)),
                (unused, unused2) -> true);
    }

    private Observable<Boolean> resolve(Observable<Map<String, VideoCategory>> lookup) {
        return lookup
                .map(categories -> {
                    onResolved(categories);
                    return true;
                })
                // Show the videos rather than nothing
                .onErrorReturn(error -> {
                    Log.e(TAG, "Can't find the categories: %s", error.getMessage());
                    return true;
                });
    }

    /**
     * @param result the videos not looked up yet
     * @param topicResult the videos found by the player before the user's key was set
     */
    private void getUnknownVideoIds(List<MediaGroup> mediaGroups, List<String> result, List<String> topicResult) {
        if (mediaGroups == null) {
            return;
        }

        boolean isKeySet = isDataApiKeySet();

        synchronized (mCategoryById) {
            for (MediaGroup mediaGroup : mediaGroups) {
                // The videos of a hidden row don't matter
                if (mediaGroup == null || mediaGroup.getMediaItems() == null || !isGroupEnabled(mediaGroup.getType()) ||
                        isTopicHidden(mediaGroup.getTopic())) {
                    continue;
                }

                for (MediaItem item : mediaGroup.getMediaItems()) {
                    String videoId = item != null ? item.getVideoId() : null;

                    if (videoId == null) {
                        continue;
                    }

                    if (!mCategoryById.containsKey(videoId)) {
                        result.add(videoId);
                    } else if (isKeySet && needsTopics(videoId)) {
                        topicResult.add(videoId);
                    }
                }
            }
        }
    }

    /**
     * Found by the player (no topics) and not hidden by the category alone, not tried this session
     */
    private boolean needsTopics(String videoId) {
        VideoCategory category = mCategoryById.get(videoId);

        if (category == null || category.getTopics() != null || isHidden(category.getCategory(), null)) {
            return false;
        }

        if (mTopicLookupTried.size() > MAX_CATEGORIES) {
            mTopicLookupTried.clear();
        }

        return mTopicLookupTried.add(videoId);
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

            if (isVideoHidden(videoId)) {
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

    private void onResolved(Map<String, VideoCategory> categories) {
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
                // Video id, category, topics (only when they were looked up)
                String[] fields = line.split("\t", -1);

                if (fields.length < 2 || fields.length > 3 || fields[0].isEmpty()) {
                    continue;
                }

                List<String> topics = fields.length == 3 ? parseTopics(fields[2]) : null;

                // Entries found this session are newer
                if ((!fields[1].isEmpty() || topics != null) && !mCategoryById.containsKey(fields[0])) {
                    mCategoryById.put(fields[0], new CachedCategory(fields[1], topics));
                }
            }
        }
    }

    private void saveCategories() {
        StringBuilder content = new StringBuilder();

        synchronized (mCategoryById) {
            for (Map.Entry<String, VideoCategory> entry : mCategoryById.entrySet()) {
                String category = entry.getValue().getCategory();
                List<String> topics = entry.getValue().getTopics();

                // Without topics a video without category is looked up again next session
                if (category == null || (NO_CATEGORY.equals(category) && topics == null) || !isValidField(category)) {
                    continue;
                }

                content.append(entry.getKey()).append('\t').append(category);

                if (topics != null) {
                    content.append('\t').append(joinTopics(topics));
                }

                content.append('\n');
            }
        }

        File parent = mCategoriesFile.getParentFile();

        if (parent != null && (parent.exists() || parent.mkdirs())) {
            FileHelpers.stringToFile(content.toString(), mCategoriesFile);
        }
    }

    private static List<String> parseTopics(String topics) {
        List<String> result = new ArrayList<>();

        for (String topic : topics.split("\\" + TOPIC_DELIM)) {
            if (!topic.isEmpty()) {
                result.add(topic);
            }
        }

        return result;
    }

    private static String joinTopics(List<String> topics) {
        StringBuilder result = new StringBuilder();

        for (String topic : topics) {
            if (topic == null || topic.isEmpty() || topic.contains(TOPIC_DELIM) || !isValidField(topic)) {
                continue;
            }

            if (result.length() > 0) {
                result.append(TOPIC_DELIM);
            }

            result.append(topic);
        }

        return result.toString();
    }

    private static boolean isValidField(String field) {
        return !field.contains("\t") && !field.contains("\n");
    }

    /**
     * Fills the cache without a lookup.
     */
    void setCategoryForTesting(String videoId, String category) {
        setCategoryForTesting(videoId, category, null);
    }

    void setCategoryForTesting(String videoId, String category, List<String> topics) {
        synchronized (mCategoryById) {
            mCategoryById.put(videoId, new CachedCategory(category, topics));
        }
    }

    /**
     * @return the videos not looked up yet, then the ones that need topics
     */
    List<String> getUnknownVideoIdsForTesting(MediaGroup mediaGroup) {
        List<String> result = new ArrayList<>();
        List<String> topicResult = new ArrayList<>();
        getUnknownVideoIds(Collections.singletonList(mediaGroup), result, topicResult);
        result.addAll(topicResult);
        return result;
    }

    File getCategoriesFileForTesting() {
        return mCategoriesFile;
    }

    void saveCategoriesForTesting() {
        saveCategories();
    }

    void restoreCategoriesForTesting() {
        synchronized (mCategoryById) {
            mCategoryById.clear();
        }

        restoreCategories();
    }

    private static final class CachedCategory implements VideoCategory {
        private final String mCategory;
        private final List<String> mTopics;

        CachedCategory(String category, List<String> topics) {
            mCategory = category;
            mTopics = topics != null ? Collections.unmodifiableList(new ArrayList<>(topics)) : null;
        }

        @Override
        public String getCategory() {
            return mCategory;
        }

        @Override
        public List<String> getTopics() {
            return mTopics;
        }
    }
}
