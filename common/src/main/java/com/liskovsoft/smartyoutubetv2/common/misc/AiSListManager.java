package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.AiSListData;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.AiSListFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.BehaviorSubject;

/**
 * Hides videos from channels listed by AiSList (https://github.com/Override92/AiSList).<br/>
 * Lists are matched by channel handle. They're downloaded at start even while every filter is off,
 * so the settings show them ready.<br/>
 * Hidden channels are collected for the Blocked AI Channels section (this session only).<br/>
 * Most TV Home cards carry only the channel name. The handle of such a channel is looked up once
 * (one watch-next request for one of its videos), before the card is shown (see HiddenVideoResolver),
 * and cached on disk by the channel name. A collaboration ("X and Y") gets the handle of each of its channels.<br/>
 * A channel card is never hidden, only marked (see {@link #getChannelMarkedList}).
 */
public class AiSListManager implements OnDataChange {
    private static final String TAG = AiSListManager.class.getSimpleName();
    private static final long CHECK_PERIOD_MS = 60 * 60 * 1_000; // the service itself refreshes the lists every 4 hours
    private static final long RETRY_PERIOD_MS = 60 * 1_000;
    private static final int MAX_BLOCKED_CHANNELS = 500;
    /**
     * A channel page is never hidden (it would be empty), only marked. Uses the Channels switches.
     */
    public static final int SECTION_CHANNEL_PAGE = 100;
    private static final String PLAYLIST_CHANNEL_ID_PREFIX = "VL"; // a playlist page is opened as a channel "VL<playlist id>"
    private static final String WATCH_LATER_CHANNEL_ID = "VLWL";
    private static final String HANDLES_FILE = "aislist/handles.tsv";
    private static final Map<String, String> NO_HANDLES = Collections.emptyMap(); // the lookup failed, don't repeat it this session
    private static final long SAVE_DELAY_MS = 10_000;
    private static final int MAX_PARALLEL_LOOKUPS = 4;
    @SuppressLint("StaticFieldLeak")
    private static AiSListManager sInstance;
    private final AiSListFilterData mFilterData;
    private final MediaItemService mItemService;
    private HandleLookup mHandleLookup;
    private final File mHandlesFile;
    // Channel name -> the handles of its channels (one, or one per channel of a collaboration) -> their channel ids or null
    private final Map<String, Map<String, String>> mHandlesByAuthor = new ConcurrentHashMap<>();
    private final Map<String, Observable<Map<String, String>>> mRunningLookups = new ConcurrentHashMap<>(); // see resolveHandles
    private final Runnable mSaveHandles = () -> RxHelper.runAsync(this::saveHandles);
    private volatile Set<String> mBlocklist = Collections.emptySet();
    private volatile Set<String> mWarnlist = Collections.emptySet();
    private long mUpdatedTimeMs;
    private long mLastCheckMs;
    private Disposable mLoadAction;
    // The first download ended, well or not: the lists the cards are checked against are known
    private final BehaviorSubject<Boolean> mFirstLoadEnded = BehaviorSubject.create();
    private boolean mIsBlocklistMarked;
    private boolean mIsWarnlistMarked;
    // Access order, so the most recently hidden channel is the last one
    private final Map<String, Video> mBlockedChannels = new LinkedHashMap<String, Video>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Video> eldest) {
            return size() > MAX_BLOCKED_CHANNELS;
        }
    };

    private AiSListManager(Context context) {
        mFilterData = AiSListFilterData.instance(context);
        mFilterData.setOnChange(this);
        mIsBlocklistMarked = isMarkedEverywhere(AiSListFilterData.LIST_BLOCKLIST);
        mIsWarnlistMarked = isMarkedEverywhere(AiSListFilterData.LIST_WARNLIST);
        mItemService = YouTubeServiceManager.instance().getMediaItemService();
        mHandleLookup = mItemService::getChannelHandlesObserve;
        mHandlesFile = new File(FileHelpers.getFilesDir(context), HANDLES_FILE);
        RxHelper.runAsync(this::restoreHandles);
        loadIfNeeded();
    }

    interface HandleLookup {
        /**
         * @return the handles of the video's owners (one per channel of a collaboration) -> their channel ids or null,
         * an error when none is found
         */
        Observable<Map<String, String>> getChannelHandles(String videoId);
    }

    public static AiSListManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new AiSListManager(context.getApplicationContext());
        }

        return sInstance;
    }

    @Override
    public void onDataChange() {
        boolean isBlocklistMarked = isMarkedEverywhere(AiSListFilterData.LIST_BLOCKLIST);
        boolean isWarnlistMarked = isMarkedEverywhere(AiSListFilterData.LIST_WARNLIST);

        if ((isBlocklistMarked && !mIsBlocklistMarked) || (isWarnlistMarked && !mIsWarnlistMarked)) {
            // Marked channels aren't blocked
            synchronized (mBlockedChannels) {
                mBlockedChannels.clear();
            }
        }

        mIsBlocklistMarked = isBlocklistMarked;
        mIsWarnlistMarked = isWarnlistMarked;

        loadIfNeeded();
    }

    /**
     * The video should be removed from the section
     * @param handle channel handle with the leading "@"
     * @param section one of AiSListFilterData.SECTION_* or -1
     */
    public boolean isHidden(String handle, int section) {
        int list = getListedIn(handle, section);

        return list != -1 && !isMarkOnly(section) && !isMarkedEverywhere(list);
    }

    /**
     * The video should stay in the section with a label
     * @param handle channel handle with the leading "@"
     * @param section one of AiSListFilterData.SECTION_* or -1
     */
    public boolean isMarked(String handle, int section) {
        return getMarkedList(handle, section) != -1;
    }

    /**
     * The list the video is marked for (the blocklist wins when the channel is on both)
     * @return AiSListFilterData.LIST_* or -1 when not marked
     */
    public int getMarkedList(String handle, int section) {
        int list = getListedIn(handle, section);

        return list != -1 && isMarkedIn(list, section) ? list : -1;
    }

    /**
     * The list a channel card is marked for. A channel card is never hidden: it's marked wherever the list marks anything,
     * as in a mark only section.
     * @return AiSListFilterData.LIST_* or -1 when not marked
     */
    public int getChannelMarkedList(String handle, int section) {
        int list = getListedIn(handle, section);

        return list != -1 && mFilterData.getMarkMode(list) != AiSListFilterData.MARK_MODE_OFF ? list : -1;
    }

    /**
     * The list's videos are marked in the section instead of hidden (or left alone in a mark only section)
     */
    private boolean isMarkedIn(int list, int section) {
        int mode = mFilterData.getMarkMode(list);

        return mode == AiSListFilterData.MARK_MODE_ALL || (mode == AiSListFilterData.MARK_MODE_MARK_ONLY_SECTIONS && isMarkOnly(section));
    }

    private boolean isMarkedEverywhere(int list) {
        return mFilterData.getMarkMode(list) == AiSListFilterData.MARK_MODE_ALL;
    }

    /**
     * Remember the channel of a hidden video for the Blocked AI Channels section
     */
    public void onHidden(Video video) {
        if (video == null || video.channelHandle == null) {
            return;
        }

        String key = video.channelHandle.toLowerCase(Locale.ROOT);

        synchronized (mBlockedChannels) {
            if (mBlockedChannels.get(key) != null) { // refresh the order
                return;
            }

            String lookupKey = getLookupKey(video);
            Map<String, String> handles = lookupKey != null ? mHandlesByAuthor.get(lookupKey) : null;
            // A collaboration's card has the names of all its channels and the channel of the uploader
            boolean isCollaboration = handles != null && handles.size() > 1;
            String author = isCollaboration ? null : video.getAuthor();

            Video channel = new Video();
            channel.channelId = !isCollaboration && video.channelId != null ? video.channelId : handles != null ? handles.get(video.channelHandle) : null;
            channel.channelHandle = video.channelHandle;
            channel.title = author != null ? author : video.channelHandle;
            channel.secondTitle = author != null ? video.channelHandle : null;
            mBlockedChannels.put(key, channel);
        }
    }

    /**
     * Channels hidden during this session, the most recent first
     */
    public List<Video> getBlockedChannels() {
        List<Video> result;

        synchronized (mBlockedChannels) {
            result = new ArrayList<>(mBlockedChannels.values());
        }

        Collections.reverse(result);
        return result;
    }

    /**
     * Any list is enabled for the section
     */
    public boolean isSectionEnabled(int section) {
        return isListEnabled(AiSListFilterData.LIST_BLOCKLIST, section) || isListEnabled(AiSListFilterData.LIST_WARNLIST, section);
    }

    /**
     * The list applies to the section. A mark only section needs the list's marking there.
     */
    private boolean isListEnabled(int list, int section) {
        if (section == -1 || (isMarkOnly(section) && !isMarkedIn(list, section))) {
            return false;
        }

        return mFilterData.isHideEnabled(list, toPrefsSection(section));
    }

    /**
     * The key of the handle cache: the channel name or, when a card has no name, the channel id
     */
    public static String getLookupKey(Video video) {
        String author = video.getAuthor();

        if (author != null) {
            return author;
        }

        return video.channelId != null ? "id:" + video.channelId : null;
    }

    /**
     * The handle found earlier for the channel name. A collaboration has one per channel: the listed one counts
     * (the blocklist first), otherwise the uploader's.
     * @param section one of AiSListFilterData.SECTION_* or -1
     * @return null when none is known
     */
    public String getCachedHandle(String author, int section) {
        Map<String, String> handles = author != null ? mHandlesByAuthor.get(author) : null;

        if (handles == null || handles.isEmpty()) {
            return null;
        }

        String uploader = null;
        String warnlisted = null;

        for (String handle : handles.keySet()) {
            int list = getListedIn(handle, section);

            if (list == AiSListFilterData.LIST_BLOCKLIST) {
                return handle;
            }

            if (list == AiSListFilterData.LIST_WARNLIST && warnlisted == null) {
                warnlisted = handle;
            }

            if (uploader == null) {
                uploader = handle;
            }
        }

        return warnlisted != null ? warnlisted : uploader;
    }

    /**
     * Looks up the handles of the channel names not known yet, a few at a time, before the cards are shown
     * (see HiddenVideoResolver). Completes once every lookup is done, the failed ones too.
     * @param videoIdByKey the key of the handle cache (see {@link #getLookupKey}) -> a video of the channel
     */
    public Observable<Boolean> resolveHandles(Map<String, String> videoIdByKey) {
        List<Observable<Map<String, String>>> lookups = new ArrayList<>();

        for (Map.Entry<String, String> entry : videoIdByKey.entrySet()) {
            if (!mHandlesByAuthor.containsKey(entry.getKey())) {
                lookups.add(getHandleLookup(entry.getKey(), entry.getValue()));
            }
        }

        if (lookups.isEmpty()) {
            return Observable.just(true);
        }

        return Observable.fromIterable(lookups)
                .flatMap(lookup -> lookup, MAX_PARALLEL_LOOKUPS)
                .toList()
                .map(handles -> true)
                .toObservable();
    }

    /**
     * One lookup per channel name at a time, shared by the groups that need it
     */
    private Observable<Map<String, String>> getHandleLookup(String author, String videoId) {
        return mRunningLookups.computeIfAbsent(author, key -> mHandleLookup.getChannelHandles(videoId)
                .map(AiSListManager::copyHandles)
                .onErrorReturn(error -> {
                    Log.e(TAG, "Can't find the handle of %s: %s", author, error.getMessage());
                    return NO_HANDLES;
                })
                .doOnNext(handles -> {
                    mHandlesByAuthor.put(author, handles);

                    if (!handles.isEmpty()) {
                        Utils.postDelayed(mSaveHandles, SAVE_DELAY_MS);
                    }
                })
                .doFinally(() -> mRunningLookups.remove(author))
                .cache());
    }

    private void restoreHandles() {
        String content = FileHelpers.getFileContents(mHandlesFile);

        if (content == null) {
            return;
        }

        for (String line : content.split("\n")) {
            String[] pair = line.split("\t");
            Map<String, String> handles = pair.length == 2 && !pair[0].isEmpty() ? parseHandles(pair[1]) : null;

            if (handles != null) {
                mHandlesByAuthor.put(pair[0], handles);
            }
        }
    }

    private void saveHandles() {
        StringBuilder content = new StringBuilder();

        for (Map.Entry<String, Map<String, String>> entry : mHandlesByAuthor.entrySet()) {
            if (entry.getValue().isEmpty() || entry.getKey().contains("\t") || entry.getKey().contains("\n")) {
                continue;
            }

            content.append(entry.getKey()).append('\t').append(formatHandles(entry.getValue())).append('\n');
        }

        File parent = mHandlesFile.getParentFile();

        if (parent != null && (parent.exists() || parent.mkdirs())) {
            FileHelpers.stringToFile(content.toString(), mHandlesFile);
        }
    }

    private static boolean isMarkOnly(int section) {
        return section == SECTION_CHANNEL_PAGE || AiSListFilterData.isMarkOnly(section);
    }

    /**
     * The handles of a lookup, without the ones that aren't handles, in their order
     */
    private static Map<String, String> copyHandles(Map<String, String> handles) {
        Map<String, String> result = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : handles.entrySet()) {
            if (isHandle(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return result.isEmpty() ? NO_HANDLES : Collections.unmodifiableMap(result);
    }

    /**
     * The saved form: "@handle:channelId", the channel id being optional, one per channel joined by ",".
     * A handle can't have either sign, nor can a channel id.
     */
    static String formatHandles(Map<String, String> handles) {
        StringBuilder result = new StringBuilder();

        for (Map.Entry<String, String> entry : handles.entrySet()) {
            if (result.length() > 0) {
                result.append(',');
            }

            result.append(entry.getKey());

            if (entry.getValue() != null) {
                result.append(':').append(entry.getValue());
            }
        }

        return result.toString();
    }

    /**
     * @return the handles of the saved form (see formatHandles), the older "@handle" too, or null when it isn't one
     */
    static Map<String, String> parseHandles(String value) {
        Map<String, String> result = new LinkedHashMap<>();

        for (String part : value.split(",")) {
            int separator = part.indexOf(':');
            String handle = separator != -1 ? part.substring(0, separator) : part;
            String channelId = separator != -1 && separator < part.length() - 1 ? part.substring(separator + 1) : null;

            if (!isHandle(handle)) {
                return null;
            }

            result.put(handle, channelId);
        }

        return result.isEmpty() ? null : Collections.unmodifiableMap(result);
    }

    private static boolean isHandle(String text) {
        return text != null && text.length() > 1 && text.startsWith("@") && text.indexOf(',') == -1 && text.indexOf(':') == -1;
    }

    /**
     * Like {@link #getSection(int, boolean)} but also tells playlists (Watch later included) from channel pages
     */
    public static int getSection(int groupType, boolean hasBrowseSection, String groupChannelId) {
        int section = getSection(groupType, hasBrowseSection);

        // Only a playlist page or a Playlists row. E.g. the Home row "From your Watch later playlist" stays Home.
        boolean isPlaylistGroup = section == SECTION_CHANNEL_PAGE || section == AiSListFilterData.SECTION_PLAYLISTS;

        if (!isPlaylistGroup || groupChannelId == null) {
            return section;
        }

        if (WATCH_LATER_CHANNEL_ID.equals(groupChannelId)) {
            return AiSListFilterData.SECTION_WATCH_LATER;
        }

        return groupChannelId.startsWith(PLAYLIST_CHANNEL_ID_PREFIX) ? AiSListFilterData.SECTION_PLAYLISTS : section;
    }

    private static int toPrefsSection(int section) {
        return section == SECTION_CHANNEL_PAGE ? AiSListFilterData.SECTION_CHANNELS : section;
    }

    /**
     * @return the enabled list that contains the channel (the blocklist first) or -1
     */
    private int getListedIn(String handle, int section) {
        if (handle == null || section == -1) {
            return -1;
        }

        boolean hideBlocklisted = isListEnabled(AiSListFilterData.LIST_BLOCKLIST, section);
        boolean hideWarnlisted = isListEnabled(AiSListFilterData.LIST_WARNLIST, section);

        if (!hideBlocklisted && !hideWarnlisted) {
            return -1;
        }

        loadIfNeeded();

        String key = handle.toLowerCase(Locale.ROOT);

        if (hideBlocklisted && mBlocklist.contains(key)) {
            return AiSListFilterData.LIST_BLOCKLIST;
        }

        return hideWarnlisted && mWarnlist.contains(key) ? AiSListFilterData.LIST_WARNLIST : -1;
    }

    /**
     * Maps a group type to the filter section.
     * @param hasBrowseSection the group is a row of a sidebar section (as opposed to a channel page)
     * @return one of AiSListFilterData.SECTION_*, {@link #SECTION_CHANNEL_PAGE} or -1 when the group is never filtered
     */
    public static int getSection(int groupType, boolean hasBrowseSection) {
        switch (groupType) {
            case MediaGroup.TYPE_HOME:
            case MediaGroup.TYPE_RECOMMENDED:
            case MediaGroup.TYPE_MUSIC:
            case MediaGroup.TYPE_NEWS:
            case MediaGroup.TYPE_GAMING:
            case MediaGroup.TYPE_KIDS_HOME:
            case MediaGroup.TYPE_TRENDING:
            case MediaGroup.TYPE_SHORTS:
            case MediaGroup.TYPE_SPORTS:
            case MediaGroup.TYPE_MOVIES:
            case MediaGroup.TYPE_LIVE:
                return AiSListFilterData.SECTION_HOME;
            case MediaGroup.TYPE_SEARCH:
                return AiSListFilterData.SECTION_SEARCH;
            case MediaGroup.TYPE_SUBSCRIPTIONS:
                return AiSListFilterData.SECTION_SUBSCRIPTIONS;
            case MediaGroup.TYPE_SUGGESTIONS:
                return AiSListFilterData.SECTION_SUGGESTIONS;
            case MediaGroup.TYPE_CHANNEL_UPLOADS:
                // The sidebar Channels section vs a single channel page
                return hasBrowseSection ? AiSListFilterData.SECTION_CHANNELS : SECTION_CHANNEL_PAGE;
            case MediaGroup.TYPE_CHANNEL:
                return SECTION_CHANNEL_PAGE;
            case MediaGroup.TYPE_HISTORY:
                return AiSListFilterData.SECTION_HISTORY;
            case MediaGroup.TYPE_USER_PLAYLISTS:
                return AiSListFilterData.SECTION_PLAYLISTS;
            default:
                return -1;
        }
    }

    public int getBlocklistSize() {
        return mBlocklist.size();
    }

    public int getWarnlistSize() {
        return mWarnlist.size();
    }

    public long getUpdatedTimeMs() {
        return mUpdatedTimeMs;
    }

    /**
     * Downloads the lists unless they're fresh (the service caches them on disk for 4 hours)
     */
    public void loadIfNeeded() {
        if (RxHelper.isAnyActionRunning(mLoadAction)) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean isLoaded = !mBlocklist.isEmpty() || !mWarnlist.isEmpty();

        if (mLastCheckMs != 0 && now - mLastCheckMs < (isLoaded ? CHECK_PERIOD_MS : RETRY_PERIOD_MS)) {
            return;
        }

        mLastCheckMs = now;

        mLoadAction = RxHelper.execute(mItemService.getAiSListDataObserve(),
                data -> {
                    applyData(data);
                    mFirstLoadEnded.onNext(true);
                },
                error -> {
                    Log.e(TAG, "Can't load AiSList: %s", error.getMessage());
                    mFirstLoadEnded.onNext(true);
                });
    }

    /**
     * Completes once the first download ended (see HiddenVideoResolver), right away after that
     */
    public Observable<Boolean> awaitLists() {
        return mFirstLoadEnded.take(1);
    }

    /**
     * Looks up with the given one from now on and forgets every handle found
     */
    void setHandleLookupForTesting(HandleLookup lookup) {
        mHandleLookup = lookup;
        mHandlesByAuthor.clear();
        mRunningLookups.clear();
    }

    /**
     * Replaces the lists without a download.
     */
    void setListsForTesting(Set<String> blocklist, Set<String> warnlist) {
        RxHelper.disposeActions(mLoadAction); // the download started at creation would replace them
        mBlocklist = blocklist;
        mWarnlist = warnlist;
        mUpdatedTimeMs = System.currentTimeMillis();
        mLastCheckMs = mUpdatedTimeMs;
        mFirstLoadEnded.onNext(true);
    }

    private void applyData(AiSListData data) {
        mBlocklist = data.getBlocklist();
        mWarnlist = data.getWarnlist();
        mUpdatedTimeMs = data.getUpdatedTimeMs();
    }
}
