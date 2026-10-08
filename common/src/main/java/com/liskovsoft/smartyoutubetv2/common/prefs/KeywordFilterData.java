package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.misc.KeywordFilter;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The keywords that hide videos by their title (see KeywordFilter). Kept as lower case words split by a space.<br/>
 * Each account has its own keywords.
 */
public class KeywordFilterData implements ProfileChangeListener {
    static final String KEYWORD_FILTER_DATA = "keyword_filter_data";
    @SuppressLint("StaticFieldLeak")
    private static KeywordFilterData sInstance;
    private final AppPrefs mPrefs;
    private final Set<String> mKeywords = new TreeSet<>();
    // Read while the videos are added, maybe off the main thread. Replaced on each change.
    private volatile Set<String> mWords = Collections.emptySet();
    private volatile List<List<String>> mPhrases = Collections.emptyList();

    private KeywordFilterData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    public static KeywordFilterData instance(Context context) {
        if (sInstance == null) {
            sInstance = new KeywordFilterData(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * @return false when there's no word in it or it's there already
     */
    public boolean addKeyword(CharSequence keyword) {
        String normalized = KeywordFilter.normalize(keyword);

        if (normalized == null || !mKeywords.add(normalized)) {
            return false;
        }

        onChanged();
        return true;
    }

    public void removeKeyword(CharSequence keyword) {
        String normalized = KeywordFilter.normalize(keyword);

        if (normalized != null && mKeywords.remove(normalized)) {
            onChanged();
        }
    }

    public boolean containsKeyword(CharSequence keyword) {
        String normalized = KeywordFilter.normalize(keyword);

        return normalized != null && mKeywords.contains(normalized);
    }

    /**
     * In the alphabetical order
     */
    public List<String> getKeywords() {
        return new ArrayList<>(mKeywords);
    }

    public boolean isEmpty() {
        return mKeywords.isEmpty();
    }

    /**
     * The title has one of the keywords
     */
    public boolean isHidden(CharSequence title) {
        Set<String> words = mWords;
        List<List<String>> phrases = mPhrases;

        if (title == null || (words.isEmpty() && phrases.isEmpty())) {
            return false;
        }

        List<String> titleWords = KeywordFilter.getWords(title);

        for (String word : titleWords) {
            if (words.contains(word)) {
                return true;
            }
        }

        for (List<String> phrase : phrases) {
            if (Collections.indexOfSubList(titleWords, phrase) != -1) {
                return true;
            }
        }

        return false;
    }

    private void onChanged() {
        updateMatchers();
        persistState();
    }

    private void updateMatchers() {
        Set<String> words = new HashSet<>();
        List<List<String>> phrases = new ArrayList<>();

        for (String keyword : mKeywords) {
            List<String> keywordWords = KeywordFilter.getWords(keyword);

            if (keywordWords.size() == 1) {
                words.add(keywordWords.get(0));
            } else {
                phrases.add(keywordWords);
            }
        }

        mWords = words;
        mPhrases = phrases;
    }

    private void restoreState() {
        String[] split = Helpers.splitData(mPrefs.getProfileData(KEYWORD_FILTER_DATA));
        // Not parsed as strings: that would drop the keyword "null"
        String[] keywords = split != null && split.length > 0 ? Helpers.splitArray(split[0]) : new String[0];

        mKeywords.clear();

        for (String keyword : keywords) {
            // Saved normalized. Normalized again in case the rules change.
            String normalized = KeywordFilter.normalize(keyword);

            if (normalized != null) {
                mKeywords.add(normalized);
            }
        }

        updateMatchers();
    }

    /**
     * Saved right away: a save still pending when the account changes would go to the new account
     */
    private void persistState() {
        mPrefs.setProfileData(KEYWORD_FILTER_DATA, Helpers.mergeData(mKeywords));
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
