package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.KeywordWordsView;
import com.liskovsoft.smartyoutubetv2.common.misc.KeywordFilter;
import com.liskovsoft.smartyoutubetv2.common.prefs.KeywordFilterData;

import java.util.Collections;
import java.util.List;

/**
 * Many words to pick keywords from (e.g. the words of a feed) on the whole screen, a tab per letter.
 * A checked word hides the videos with it in the title.
 */
public class KeywordWordsPresenter extends BasePresenter<KeywordWordsView> {
    @SuppressLint("StaticFieldLeak")
    private static KeywordWordsPresenter sInstance;
    private final KeywordFilterData mData;
    private String mTitle;
    private List<String> mWords = Collections.emptyList();
    private Runnable mOnClose;

    private KeywordWordsPresenter(Context context) {
        super(context);
        mData = KeywordFilterData.instance(context);
    }

    public static KeywordWordsPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new KeywordWordsPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    /**
     * @param onClose runs after Back, e.g. to open the menu that led here again
     */
    public void show(String title, List<String> words, Runnable onClose) {
        mTitle = title;
        mWords = words;
        mOnClose = onClose;

        // Still open (e.g. the activity is reused)
        if (getView() != null) {
            updateView();
        }

        getViewManager().startView(KeywordWordsView.class);
    }

    @Override
    public void onViewInitialized() {
        updateView();
    }

    @Override
    public void onFinish() {
        super.onFinish();

        Runnable onClose = mOnClose;
        mOnClose = null;

        if (onClose != null) {
            onClose.run();
        }
    }

    public boolean isKeyword(String word) {
        return mData.containsKeyword(word);
    }

    /**
     * @return the word is a keyword now
     */
    public boolean toggleKeyword(String word) {
        if (mData.containsKeyword(word)) {
            mData.removeKeyword(word);
            return false;
        }

        mData.addKeyword(word);
        return true;
    }

    private void updateView() {
        if (getView() == null) {
            return;
        }

        String count = getContext().getResources().getQuantityString(R.plurals.keyword_filter_words, mWords.size(), String.valueOf(mWords.size()));
        getView().update(mTitle, getContext().getString(R.string.keyword_filter_words_description, count), KeywordFilter.groupByLetter(mWords));
    }
}
