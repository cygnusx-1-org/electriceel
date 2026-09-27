package com.liskovsoft.smartyoutubetv2.tv.ui.keywords;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class KeywordWordsActivity extends LeanbackActivity {
    private KeywordWordsFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_keyword_words);
        mFragment = (KeywordWordsFragment) getSupportFragmentManager().findFragmentById(R.id.keyword_words_fragment);
    }

    @Override
    public void onBackPressed() {
        // The words first go back to the tabs, the tabs close the screen
        if (mFragment != null && mFragment.focusLetters()) {
            return;
        }

        super.onBackPressed();
    }

    @Override
    public void finishReally() {
        super.finishReally();

        if (mFragment != null) {
            mFragment.onFinish();
        }
    }
}
