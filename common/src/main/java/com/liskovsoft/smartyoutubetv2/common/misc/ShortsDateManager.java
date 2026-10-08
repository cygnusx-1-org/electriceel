package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;

/**
 * Shows when the shorts were published, with the Dates on shorts card option (see MainUIData).<br/>
 * Their cards come without the date (see MediaItem.isDateMissing). It's looked up before the cards are shown (see HiddenVideoResolver):
 * with the user's own Data API key 50 videos a request, without one a player request per video.
 * It never changes, so it's kept on disk by the video id and looked up once.<br/>
 * The card shows it the way YouTube does on the other cards (e.g. "3 days ago"), so Hide old videos reads it too (see OldVideoFilter).
 */
public class ShortsDateManager {
    private static final String TAG = ShortsDateManager.class.getSimpleName();
    private static final String DATES_FILE = "shortsdate/dates.tsv";
    private static final long NO_DATE = 0; // the video has none (e.g. private), not looked up again this session
    private static final int MAX_DATES = 5_000;
    private static final long SAVE_DELAY_MS = 10_000;
    private static final String DELIM = " " + Video.TERTIARY_TEXT_DELIM + " ";
    // The marks that wrap mixed RTL and LTR text (see ServiceHelper.createInfo)
    private static final Pattern BIDI_MARKS = Pattern.compile("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]");
    private static final long MINUTE_MS = 60 * 1_000;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final long DAY_MS = 24 * HOUR_MS;
    // The same as OldVideoFilter's
    private static final int DAYS_IN_WEEK = 7;
    private static final int DAYS_IN_MONTH = 30;
    private static final int DAYS_IN_YEAR = 365;
    @SuppressLint("StaticFieldLeak")
    private static ShortsDateManager sInstance;
    private final Context mContext;
    private final File mDatesFile;
    private Lookup mLookup;
    // Access order, so the least recently seen video is dropped first
    private final Map<String, Long> mDateById = new LinkedHashMap<String, Long>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_DATES;
        }
    };
    // The dates saved on disk are known
    private final BehaviorSubject<Boolean> mRestored = BehaviorSubject.create();
    private final Runnable mSaveDates = () -> RxHelper.runAsync(this::saveDates);

    interface Lookup {
        /**
         * @return video id -> publish time (ms), {@link #NO_DATE} when the video has none. Failed videos are left out.
         */
        Observable<Map<String, Long>> getDates(List<String> videoIds);
    }

    private ShortsDateManager(Context context) {
        mContext = context;
        mLookup = YouTubeServiceManager.instance().getMediaItemService()::getPublishedDatesObserve;
        mDatesFile = new File(FileHelpers.getFilesDir(context), DATES_FILE);
        RxHelper.runAsync(this::restoreDates);
    }

    public static ShortsDateManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new ShortsDateManager(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * Not kept: the option is the account's (see MainUIData)
     */
    private boolean isEnabled() {
        return MainUIData.instance(mContext).isShortsDateEnabled();
    }

    /**
     * Holds until the dates of the shorts that come without are known (see HiddenVideoResolver).
     * A failed lookup doesn't hold the cards back: they're shown without, and it's tried again with the next ones.
     */
    public Observable<Boolean> resolve(List<MediaGroup> mediaGroups) {
        if (mediaGroups == null || !isEnabled()) {
            return Observable.just(true);
        }

        return mRestored.take(1).concatMap(restored -> {
            List<String> videoIds = getUnknownVideoIds(mediaGroups);

            if (videoIds.isEmpty()) {
                return Observable.just(true);
            }

            return mLookup.getDates(videoIds)
                    .map(dates -> {
                        onResolved(dates);
                        return true;
                    })
                    // Show the cards rather than nothing
                    .onErrorReturn(error -> {
                        Log.e(TAG, "Can't find the dates: %s", error.getMessage());
                        return true;
                    });
        });
    }

    /**
     * The shorts without the date that weren't looked up yet
     */
    private List<String> getUnknownVideoIds(List<MediaGroup> mediaGroups) {
        Set<String> result = new LinkedHashSet<>();

        synchronized (mDateById) {
            for (MediaGroup mediaGroup : mediaGroups) {
                if (mediaGroup == null || mediaGroup.getMediaItems() == null) {
                    continue;
                }

                for (MediaItem item : mediaGroup.getMediaItems()) {
                    String videoId = item != null && item.isDateMissing() ? item.getVideoId() : null;

                    if (videoId != null && !mDateById.containsKey(videoId)) {
                        result.add(videoId);
                    }
                }
            }
        }

        return new ArrayList<>(result);
    }

    private void onResolved(Map<String, Long> dates) {
        if (dates == null || dates.isEmpty()) {
            return;
        }

        synchronized (mDateById) {
            mDateById.putAll(dates);
        }

        Utils.postDelayed(mSaveDates, SAVE_DELAY_MS);
    }

    /**
     * Puts the date in the second title of a short that comes without it, once it's known (see VideoGroup)
     */
    public void addDate(Video video) {
        if (video == null || video.isDateAdded || video.mediaItem == null || !video.mediaItem.isDateMissing() || !isEnabled()) {
            return;
        }

        Long publishedMs = getCached(video.videoId);

        if (publishedMs == null || publishedMs == NO_DATE) {
            return;
        }

        video.secondTitle = addDate(video.secondTitle, getDateText(mContext, publishedMs, System.currentTimeMillis()));
        video.isDateAdded = true;
    }

    private Long getCached(String videoId) {
        if (videoId == null) {
            return null;
        }

        synchronized (mDateById) {
            return mDateById.get(videoId);
        }
    }

    /**
     * The date goes after the channel or the views, before the handle that ends the line (e.g. "Channel • 3 days ago • @handle"),
     * so a narrow card still shows it
     */
    static CharSequence addDate(CharSequence secondTitle, CharSequence date) {
        if (TextUtils.isEmpty(secondTitle)) {
            return date;
        }

        String text = secondTitle.toString();
        int index = text.lastIndexOf(DELIM);
        int lastStart = index != -1 ? index + DELIM.length() : 0;

        if (!isHandle(text.substring(lastStart))) {
            return TextUtils.concat(secondTitle, DELIM, date);
        }

        if (index == -1) {
            return TextUtils.concat(date, DELIM, secondTitle);
        }

        return TextUtils.concat(secondTitle.subSequence(0, index), DELIM, date, secondTitle.subSequence(index, secondTitle.length()));
    }

    private static boolean isHandle(String text) {
        return BIDI_MARKS.matcher(text).replaceAll("").trim().startsWith("@");
    }

    /**
     * The age as YouTube shows it on the other cards (e.g. "3 days ago"): rounded down, a month is 30 days
     */
    static String getDateText(Context context, long publishedMs, long nowMs) {
        long ageMs = Math.max(0, nowMs - publishedMs);
        long days = ageMs / DAY_MS;

        if (days >= DAYS_IN_YEAR) {
            return getQuantityText(context, R.plurals.years_ago, days / DAYS_IN_YEAR);
        }

        if (days >= DAYS_IN_MONTH) {
            return getQuantityText(context, R.plurals.months_ago, days / DAYS_IN_MONTH);
        }

        if (days >= DAYS_IN_WEEK) {
            return getQuantityText(context, R.plurals.weeks_ago, days / DAYS_IN_WEEK);
        }

        if (days > 0) {
            return getQuantityText(context, R.plurals.days_ago, days);
        }

        if (ageMs >= HOUR_MS) {
            return getQuantityText(context, R.plurals.hours_ago, ageMs / HOUR_MS);
        }

        if (ageMs >= MINUTE_MS) {
            return getQuantityText(context, R.plurals.minutes_ago, ageMs / MINUTE_MS);
        }

        return getQuantityText(context, R.plurals.seconds_ago, ageMs / 1_000);
    }

    private static String getQuantityText(Context context, int resId, long count) {
        return context.getResources().getQuantityString(resId, (int) count, String.valueOf(count));
    }

    /**
     * Looks up with the given one from now on and forgets the dates
     */
    void resetForTesting(Lookup lookup) {
        mLookup = lookup;

        synchronized (mDateById) {
            mDateById.clear();
        }

        mRestored.onNext(true);
    }

    File getDatesFileForTesting() {
        return mDatesFile;
    }

    void saveDatesForTesting() {
        saveDates();
    }

    void restoreDatesForTesting() {
        synchronized (mDateById) {
            mDateById.clear();
        }

        restoreDates();
    }

    private void restoreDates() {
        Map<String, Long> restored = new LinkedHashMap<>();

        try {
            String content = FileHelpers.getFileContents(mDatesFile);

            for (String line : content != null ? content.split("\n") : new String[0]) {
                // Video id, publish time (ms)
                String[] fields = line.split("\t", -1);
                long publishedMs = fields.length == 2 ? Helpers.parseLong(fields[1], NO_DATE) : NO_DATE;

                if (!fields[0].isEmpty() && publishedMs != NO_DATE) {
                    restored.put(fields[0], publishedMs);
                }
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Can't read %s: %s", mDatesFile, e.getMessage());
        }

        // Always emitted, on the main thread like the lookups: the cards wait for it
        Utils.post(() -> {
            synchronized (mDateById) {
                for (Map.Entry<String, Long> entry : restored.entrySet()) {
                    // Found this session meanwhile: the same date
                    if (!mDateById.containsKey(entry.getKey())) {
                        mDateById.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            mRestored.onNext(true);
        });
    }

    private void saveDates() {
        StringBuilder content = new StringBuilder();

        synchronized (mDateById) {
            for (Map.Entry<String, Long> entry : mDateById.entrySet()) {
                // Without a date the video is looked up again next session
                if (entry.getValue() == null || entry.getValue() == NO_DATE || entry.getKey().contains("\t") || entry.getKey().contains("\n")) {
                    continue;
                }

                content.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
            }
        }

        File parent = mDatesFile.getParentFile();

        if (parent != null && (parent.exists() || parent.mkdirs())) {
            FileHelpers.stringToFile(content.toString(), mDatesFile);
        }
    }
}
