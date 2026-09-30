package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.prefs.CollaborationsData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.Observable;

/**
 * Tells a collaboration by the channel name on its card. TV shows one as a plain "X and Y", without the channels,
 * so it's taken as one when X and Y are both channel names but "X and Y" itself isn't (e.g. "Dan and Phil").<br/>
 * A name is a channel name when a channel search finds a channel called exactly that. Each name is searched once,
 * signed out (it stays out of the search history), before the cards are shown, and the answer is cached on disk.<br/>
 * Only the English "and" is known: in another app language no collaboration is found.
 */
public class CollaborationManager {
    private static final String TAG = CollaborationManager.class.getSimpleName();
    private static final String SEPARATOR = " and ";
    private static final String NAMES_FILE = "collaborations/channel_names.tsv";
    private static final String CHANNEL = "1";
    private static final String NOT_CHANNEL = "0";
    private static final int MAX_NAMES = 5_000;
    private static final long SAVE_DELAY_MS = 10_000;
    private static final int MAX_PARALLEL_SEARCHES = 4;
    @SuppressLint("StaticFieldLeak")
    private static CollaborationManager sInstance;
    private final Context mContext;
    private final File mNamesFile;
    private ChannelSearch mChannelSearch;
    // Name key -> it's a channel name. Access order, so the names unused the longest are dropped first.
    private final Map<String, Boolean> mIsChannelName = Collections.synchronizedMap(new LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_NAMES;
        }
    });
    // The search failed, don't repeat it this session
    private final Set<String> mFailedNames = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, Observable<Boolean>> mRunningSearches = new ConcurrentHashMap<>();
    private final Runnable mSaveNames = () -> RxHelper.runAsync(this::saveNames);

    interface ChannelSearch {
        /**
         * @return the names of the channels found, an error when the search failed
         */
        Observable<List<String>> getChannelNames(String query);
    }

    interface NameCheck {
        /**
         * @return null while it isn't known
         */
        Boolean isChannelName(String name);
    }

    /**
     * What the names known so far tell about a card
     */
    static final class Verdict {
        static final Verdict NO = new Verdict(false, null);
        static final Verdict YES = new Verdict(true, null);
        final Boolean isCollaboration; // null while undecided
        final String neededName; // the name to look up next while undecided

        private Verdict(Boolean isCollaboration, String neededName) {
            this.isCollaboration = isCollaboration;
            this.neededName = neededName;
        }

        static Verdict need(String name) {
            return new Verdict(null, name);
        }
    }

    private CollaborationManager(Context context) {
        mContext = context;
        ContentService contentService = YouTubeServiceManager.instance().getContentService();
        mChannelSearch = contentService::getChannelNamesObserve;
        mNamesFile = new File(FileHelpers.getFilesDir(context), NAMES_FILE);
        RxHelper.runAsync(this::restoreNames);
    }

    public static CollaborationManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new CollaborationManager(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * Collaborations are looked for in the section (MediaGroup.TYPE_*)
     */
    public boolean isEnabled(int sectionId) {
        return getData().isEnabled(sectionId);
    }

    public boolean isHidden(int sectionId) {
        return getData().isHidingEnabled(sectionId);
    }

    public boolean isMarked(int sectionId) {
        return getData().isMarkingEnabled(sectionId);
    }

    /**
     * Not kept, like in OldVideoFilter: after CollaborationsData.resetInstanceForTesting a kept one would be stale
     */
    private CollaborationsData getData() {
        return CollaborationsData.instance(mContext);
    }

    /**
     * The channel name on the card might be a collaboration's. Others are never looked up.
     */
    public static boolean isCandidate(String author) {
        return author != null && author.contains(SEPARATOR);
    }

    /**
     * @return null while a name it needs hasn't been looked up
     */
    public Boolean getCachedResult(String author) {
        return judge(author, this::getKnown).isCollaboration;
    }

    /**
     * Looks up the names the cards need, a few at a time, before they're shown (see HiddenVideoResolver).
     * Completes once every card is answered or its search failed.
     */
    public Observable<Boolean> resolve(Collection<String> authors) {
        List<String> undecided = new ArrayList<>();

        for (String author : authors) {
            if (needsSearch(judge(author, this::getKnown))) {
                undecided.add(author);
            }
        }

        if (undecided.isEmpty()) {
            return Observable.just(true);
        }

        return Observable.fromIterable(undecided)
                .flatMap(this::resolveAuthor, MAX_PARALLEL_SEARCHES)
                .toList()
                .map(results -> true)
                .toObservable();
    }

    /**
     * A card might need three searches, one after the other: its whole name, then each side of the "and"
     */
    private Observable<Boolean> resolveAuthor(String author) {
        Verdict verdict = judge(author, this::getKnown);

        if (!needsSearch(verdict)) {
            return Observable.just(true);
        }

        return search(verdict.neededName).concatMap(done -> resolveAuthor(author));
    }

    private boolean needsSearch(Verdict verdict) {
        return verdict.isCollaboration == null && !mFailedNames.contains(getKey(verdict.neededName));
    }

    /**
     * One search per name at a time, shared by the cards that need it. A failed one isn't repeated this session.
     */
    private Observable<Boolean> search(String name) {
        String key = getKey(name);

        return mRunningSearches.computeIfAbsent(key, unused -> mChannelSearch.getChannelNames(name)
                .map(names -> {
                    mIsChannelName.put(key, containsName(names, name));
                    Utils.postDelayed(mSaveNames, SAVE_DELAY_MS);
                    return true;
                })
                .onErrorReturn(error -> {
                    Log.e(TAG, "Can't search for the channel %s: %s", name, error.getMessage());
                    mFailedNames.add(key);
                    return true;
                })
                .doFinally(() -> mRunningSearches.remove(key))
                .cache());
    }

    private Boolean getKnown(String name) {
        return mIsChannelName.get(getKey(name));
    }

    /**
     * The rule, over the names known so far: "X and Y" is a collaboration when it isn't a channel name
     * itself and both sides of one of its "and"s are
     * @return the answer or, while undecided, the name to look up next
     */
    static Verdict judge(String author, NameCheck check) {
        if (!isCandidate(author)) {
            return Verdict.NO;
        }

        String whole = author.trim();
        Boolean isChannel = check.isChannelName(whole);

        if (isChannel == null) {
            return Verdict.need(whole);
        }

        if (isChannel) {
            return Verdict.NO; // one channel with "and" in its name
        }

        // Each "and", in case a collaborator's own name has one
        for (int index = author.indexOf(SEPARATOR); index != -1; index = author.indexOf(SEPARATOR, index + 1)) {
            String first = author.substring(0, index).trim();
            String second = author.substring(index + SEPARATOR.length()).trim();

            if (first.isEmpty() || second.isEmpty()) {
                continue;
            }

            Boolean isFirstChannel = check.isChannelName(first);

            if (isFirstChannel == null) {
                return Verdict.need(first);
            }

            if (!isFirstChannel) {
                continue;
            }

            Boolean isSecondChannel = check.isChannelName(second);

            if (isSecondChannel == null) {
                return Verdict.need(second);
            }

            if (isSecondChannel) {
                return Verdict.YES;
            }
        }

        return Verdict.NO;
    }

    /**
     * A found channel is called exactly that, without case. The rest are only like it (e.g. "Jesser Gaming" for "Jesser").
     */
    static boolean containsName(List<String> names, String name) {
        String key = getKey(name);

        for (String found : names) {
            if (key.equals(getKey(found))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Without case, and the spaces around. YouTube might put a no-break space inside.
     */
    static String getKey(String name) {
        return name != null ? name.replace(' ', ' ').trim().toLowerCase(Locale.ROOT) : null;
    }

    /**
     * Searches with the given one from now on and forgets every name
     */
    void resetForTesting(ChannelSearch search) {
        mChannelSearch = search;
        mIsChannelName.clear();
        mFailedNames.clear();
        mRunningSearches.clear();
    }

    private void restoreNames() {
        String content = FileHelpers.getFileContents(mNamesFile);

        if (content == null) {
            return;
        }

        for (String line : content.split("\n")) {
            String[] pair = line.split("\t");

            if (pair.length == 2 && !pair[0].isEmpty() && (CHANNEL.equals(pair[1]) || NOT_CHANNEL.equals(pair[1]))) {
                // A search done while the file was read is newer
                mIsChannelName.putIfAbsent(pair[0], CHANNEL.equals(pair[1]));
            }
        }
    }

    private void saveNames() {
        StringBuilder content = new StringBuilder();

        synchronized (mIsChannelName) {
            for (Map.Entry<String, Boolean> entry : mIsChannelName.entrySet()) {
                if (entry.getKey().contains("\t") || entry.getKey().contains("\n")) {
                    continue;
                }

                content.append(entry.getKey()).append('\t').append(entry.getValue() ? CHANNEL : NOT_CHANNEL).append('\n');
            }
        }

        File parent = mNamesFile.getParentFile();

        if (parent != null && (parent.exists() || parent.mkdirs())) {
            FileHelpers.stringToFile(content.toString(), mNamesFile);
        }
    }
}
