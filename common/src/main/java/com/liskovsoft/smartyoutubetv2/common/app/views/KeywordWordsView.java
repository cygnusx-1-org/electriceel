package com.liskovsoft.smartyoutubetv2.common.app.views;

import java.util.List;
import java.util.Map;

/**
 * The words to pick keywords from on the whole screen, a tab per letter (see KeywordWordsPresenter)
 */
public interface KeywordWordsView {
    /**
     * @param wordsByLetter the tabs in order
     */
    void update(String title, String description, Map<String, List<String>> wordsByLetter);
}
