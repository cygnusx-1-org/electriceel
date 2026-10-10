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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;

/**
 * Hides music, gaming, sports, news and tech videos from Home. Only with the user's own Data API key (see {@link #isAvailable}).<br/>
 * Home cards don't carry the category. It's looked up once per video before the row is shown,
 * so the hidden videos never appear, and cached on disk by the video id.<br/>
 * The category is whatever the uploader picked (e.g. many music mixes are People & Blogs). The lookup also brings
 * the topics YouTube finds in the video itself (e.g. Music), and either one hides the video.<br/>
 * Whole rows go too: the ones marked with a hidden topic (e.g. music shelves) and the ones left with a single video or none.<br/>
 * The Gaming section can drop its non-gaming videos the same way (see {@link #isNonGaming}).<br/>
 * The player uses the same lookup to tell music videos for Music autoplay (see {@link #isMusic}).
 */
public class VideoCategoryManager {
    private static final String TAG = VideoCategoryManager.class.getSimpleName();
    // Content flag, the category YouTube gives the video (always in English), the topic of a row (none for tech)
    private static final int[] CONTENT = {
            MediaServiceData.CONTENT_MUSIC_HOME, MediaServiceData.CONTENT_GAMING_HOME,
            MediaServiceData.CONTENT_SPORTS_HOME, MediaServiceData.CONTENT_NEWS_HOME, MediaServiceData.CONTENT_TECH_HOME};
    private static final String MUSIC_CATEGORY = "Music";
    private static final String MUSIC_TOPIC = "Music";
    private static final String GAMING_CATEGORY = "Gaming";
    private static final String[] CATEGORIES = {MUSIC_CATEGORY, GAMING_CATEGORY, "Sports", "News & Politics", "Science & Technology"};
    // The parent topics of the Data API (Wikipedia page names), every sub-genre carries them too (e.g. Electronic_music + Music)
    private static final String[] VIDEO_TOPICS = {MUSIC_TOPIC, "Video_game_culture", "Sport", "Politics", "Technology"};
    // The sub-genres of Video_game_culture. A video about something else with game footage gets the parent alone
    // (e.g. an AI video: Science & Technology, Video_game_culture).
    private static final List<String> GAME_GENRE_TOPICS = Arrays.asList(
            "Action_game", "Action-adventure_game", "Casual_game", "Music_video_game", "Puzzle_video_game",
            "Racing_video_game", "Role-playing_video_game", "Simulation_video_game", "Sports_game", "Strategy_video_game");
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
        if (!isAvailable()) {
            return false;
        }

        if (groupType == MediaGroup.TYPE_GAMING) {
            return isContentHidden(MediaServiceData.CONTENT_NON_GAMING_GAMING);
        }

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
     * The video found earlier is hidden from the group: by its category or one of its topics in Home, as non-gaming in Gaming
     */
    public boolean isVideoHidden(String videoId, int groupType) {
        VideoCategory category = getCached(videoId);

        if (category == null) {
            return false;
        }

        if (groupType == MediaGroup.TYPE_GAMING) {
            return isContentHidden(MediaServiceData.CONTENT_NON_GAMING_GAMING) && isNonGaming(category.getCategory(), category.getTopics());
        }

        return isHidden(category.getCategory(), category.getTopics());
    }

    /**
     * Neither the Gaming category nor a game genre topic. Every gaming video has one or the other
     * (e.g. a Minecraft video in People & Blogs has Action-adventure_game).
     * Without topics (found by the player, see {@link #needsTopics}) the video stays.
     */
    static boolean isNonGaming(String category, List<String> topics) {
        if (GAMING_CATEGORY.equals(category) || topics == null) {
            return false;
        }

        for (String topic : topics) {
            if (GAME_GENRE_TOPICS.contains(topic)) {
                return false;
            }
        }

        return true;
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
     * The category or one of the topics of the video found earlier is music, as "Hide music from Home" sees it
     * (e.g. a mix the uploader put in People & Blogs). Unknown videos aren't.
     */
    public boolean isMusic(String videoId) {
        VideoCategory category = getCached(videoId);

        return category != null && isMusic(category.getCategory(), category.getTopics());
    }

    static boolean isMusic(String category, List<String> topics) {
        return MUSIC_CATEGORY.equals(category) || (topics != null && topics.contains(MUSIC_TOPIC));
    }

    /**
     * The Home row is about a hidden topic (e.g. a music shelf)
     */
    private boolean isTopicHidden(int groupType, int topic) {
        if (groupType != MediaGroup.TYPE_HOME || topic == MediaGroup.TOPIC_NONE) {
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

    /**
     * The user has entered their own Data API key: the Home categories are hidden only then.
     * The hide options keep their state without one.
     */
    public static boolean isAvailable() {
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
     * Holds until the categories of the videos are known (e.g. the current and the next video of the player, see {@link #isMusic}).
     * The ones known without topics get them, unless their category is music already.
     */
    public Observable<Boolean> resolveVideos(List<String> videoIds) {
        List<String> unknownIds = new ArrayList<>();
        List<String> topicVideoIds = new ArrayList<>();

        getUnknownMusicVideoIds(videoIds, unknownIds, topicVideoIds);

        return resolve(unknownIds, topicVideoIds);
    }

    private Observable<Boolean> resolve(List<MediaGroup> mediaGroups) {
        List<String> videoIds = new ArrayList<>();
        List<String> topicVideoIds = new ArrayList<>();

        getUnknownVideoIds(mediaGroups, videoIds, topicVideoIds);

        return resolve(videoIds, topicVideoIds);
    }

    /**
     * The unknown videos get the full lookup. The ones known without topics only get the Data API:
     * the player would only repeat the category they have (e.g. while the key is out of quota).
     */
    private Observable<Boolean> resolve(List<String> videoIds, List<String> topicVideoIds) {
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
     * @param topicResult the videos found by the player, without topics (e.g. while the key was out of quota)
     */
    private void getUnknownVideoIds(List<MediaGroup> mediaGroups, List<String> result, List<String> topicResult) {
        if (mediaGroups == null) {
            return;
        }

        synchronized (mCategoryById) {
            for (MediaGroup mediaGroup : mediaGroups) {
                // The videos of a hidden row don't matter
                if (mediaGroup == null || mediaGroup.getMediaItems() == null || !isGroupEnabled(mediaGroup.getType()) ||
                        isTopicHidden(mediaGroup.getType(), mediaGroup.getTopic())) {
                    continue;
                }

                for (MediaItem item : mediaGroup.getMediaItems()) {
                    String videoId = item != null ? item.getVideoId() : null;

                    if (videoId == null) {
                        continue;
                    }

                    if (!mCategoryById.containsKey(videoId)) {
                        result.add(videoId);
                    } else if (needsTopics(videoId, mediaGroup.getType())) {
                        topicResult.add(videoId);
                    }
                }
            }
        }
    }

    /**
     * @param result the videos not looked up yet
     * @param topicResult the videos found by the player, without topics, whose category isn't music
     */
    private void getUnknownMusicVideoIds(List<String> videoIds, List<String> result, List<String> topicResult) {
        synchronized (mCategoryById) {
            for (String videoId : videoIds) {
                if (videoId == null || result.contains(videoId) || topicResult.contains(videoId)) {
                    continue;
                }

                VideoCategory category = mCategoryById.get(videoId);

                if (category == null) {
                    result.add(videoId);
                } else if (category.getTopics() == null && !isMusic(category.getCategory(), null) && tryTopicLookup(videoId)) {
                    topicResult.add(videoId);
                }
            }
        }
    }

    /**
     * Found by the player (no topics), not told by the category alone (hidden from Home, gaming in Gaming), not tried this session
     */
    private boolean needsTopics(String videoId, int groupType) {
        VideoCategory category = mCategoryById.get(videoId);

        if (category == null || category.getTopics() != null) {
            return false;
        }

        if (groupType == MediaGroup.TYPE_GAMING ? GAMING_CATEGORY.equals(category.getCategory()) : isHidden(category.getCategory(), null)) {
            return false;
        }

        return tryTopicLookup(videoId);
    }

    /**
     * The topic lookup of the video wasn't tried this session
     */
    private boolean tryTopicLookup(String videoId) {
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

        if (isTopicHidden(mediaGroup.getType(), mediaGroup.getTopic())) {
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

            if (isVideoHidden(videoId, mediaGroup.getType())) {
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

    /**
     * @return the videos not looked up yet, then the ones that need topics
     */
    List<String> getUnknownMusicVideoIdsForTesting(String... videoIds) {
        List<String> result = new ArrayList<>();
        List<String> topicResult = new ArrayList<>();
        getUnknownMusicVideoIds(Arrays.asList(videoIds), result, topicResult);
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
