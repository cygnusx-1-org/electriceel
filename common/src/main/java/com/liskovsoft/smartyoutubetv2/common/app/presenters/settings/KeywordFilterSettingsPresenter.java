package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.KeywordWordsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.KeywordFilter;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.prefs.KeywordFilterData;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

/**
 * The Keyword filtering menu of the Hide content setting. The keywords are picked from lists (the common ones, the words of a feed),
 * said or typed. The video context menu picks them from the title of the video.
 */
public class KeywordFilterSettingsPresenter extends BasePresenter<Void> {
    private static final String TAG = KeywordFilterSettingsPresenter.class.getSimpleName();
    // A grid feed (e.g. Subscriptions) gets more pages until it has this many videos. Rows (e.g. Home) come whole.
    private static final int FEED_MIN_VIDEOS = 100;
    private static final int FEED_MAX_PAGES = 5;
    private static final int VOICE_MAX_RESULTS = 5;
    // One at a time. The menu that started them might be gone when they finish.
    @SuppressLint("StaticFieldLeak") // made with the application context
    private static SpeechRecognizer sRecognizer;
    private static Disposable sFeedAction;
    private final KeywordFilterData mData;

    /**
     * The titles of a feed, page by page
     */
    private static class FeedTitles {
        final List<String> titles = new ArrayList<>();
        MediaGroup lastGroup;
        int pageGroups;
        int pages;
    }

    private KeywordFilterSettingsPresenter(Context context) {
        super(context);
        mData = KeywordFilterData.instance(context);
    }

    public static KeywordFilterSettingsPresenter instance(Context context) {
        return new KeywordFilterSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());

        List<OptionItem> options = new ArrayList<>();
        options.add(createMenuItem(R.string.keyword_filter_keywords, this::showKeywords));
        options.add(createMenuItem(R.string.keyword_filter_common, this::showCommonKeywords));
        options.add(createMenuItem(R.string.keyword_filter_feed_words, this::showFeeds));
        options.add(createMenuItem(R.string.keyword_filter_voice, this::startVoiceInput));
        options.add(createMenuItem(R.string.keyword_filter_type, this::showKeywordEditor));

        String title = getContext().getString(R.string.keyword_filtering);
        presenter.appendCheckedCategory(title, options);
        presenter.showDialog(title);
    }

    /**
     * A checked word hides the videos with it in the title. It's checked already when it's a keyword.
     *
     * @param onFinish runs when the whole dialog closes
     */
    public void showWords(CharSequence title, List<String> words, Runnable onFinish) {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());

        List<OptionItem> options = new ArrayList<>();

        for (String word : words) {
            options.add(UiOptionItem.from(word, option -> {
                if (option.isSelected()) {
                    mData.addKeyword(word);
                } else {
                    mData.removeKeyword(word);
                }
            }, mData.containsKeyword(word)));
        }

        presenter.appendCheckedCategory(title, options);
        presenter.showDialog(title, onFinish);
    }

    /**
     * Over the Keyword filtering menu, so Back goes there. The menu opens again when the settings were closed (e.g. for the mic prompt).
     */
    private void showOverMenu(Runnable showList) {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());

        if (presenter.isDialogShown()) {
            showList.run();
            return;
        }

        // Before the menu is shown: a dialog that's still there shows it right away.
        // Posted: the menu is added to the stack first.
        presenter.setOnStart(() -> Utils.post(showList));
        show();
    }

    private OptionItem createMenuItem(int titleResId, Runnable onSelect) {
        OptionItem item = UiOptionItem.from(getContext().getString(titleResId), option -> onSelect.run());
        item.setMenu(true);
        return item;
    }

    /**
     * Unchecking removes a keyword. It stays in the list until the list is opened again, so it can be checked back.
     */
    private void showKeywords() {
        if (mData.isEmpty()) {
            MessageHelpers.showMessage(getContext(), R.string.keyword_filter_no_keywords);
            return;
        }

        showWords(getContext().getString(R.string.keyword_filter_keywords), mData.getKeywords(), null);
    }

    private void showCommonKeywords() {
        showWords(getContext().getString(R.string.keyword_filter_common), Arrays.asList(KeywordFilter.COMMON_KEYWORDS), null);
    }

    /**
     * Over the settings, so they stay on the Keyword filtering menu. A keyword can be a few words, e.g. "funny cats".
     */
    private void showKeywordEditor() {
        // The dialog's own activity: the keyboard dialog opens behind the settings otherwise
        Context context = AppDialogPresenter.instance(getContext()).getContext();

        SimpleEditDialog.show(
                context,
                context.getString(R.string.keyword_filter_type),
                context.getString(R.string.keyword_filter_type_hint),
                null,
                newValue -> {
                    String keyword = KeywordFilter.normalize(newValue);

                    if (keyword == null) {
                        MessageHelpers.showMessage(context, R.string.keyword_filter_no_word);
                        return false;
                    }

                    mData.addKeyword(keyword);
                    MessageHelpers.showMessage(context, context.getString(R.string.keyword_filter_hiding, keyword));
                    return true;
                });
    }

    private void showFeeds() {
        List<BrowseSection> feeds = BrowsePresenter.instance(getContext()).getVideoSections();

        if (feeds.isEmpty()) {
            MessageHelpers.showMessage(getContext(), R.string.keyword_filter_no_feeds);
            return;
        }

        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());

        List<OptionItem> options = new ArrayList<>();

        for (BrowseSection feed : feeds) {
            OptionItem item = UiOptionItem.from(feed.getTitle(), option -> loadFeedWords(feed));
            item.setMenu(true);
            options.add(item);
        }

        String title = getContext().getString(R.string.keyword_filter_feed_words);
        presenter.appendCheckedCategory(title, options);
        presenter.showDialog(title);
    }

    private void loadFeedWords(BrowseSection feed) {
        Observable<List<MediaGroup>> content = BrowsePresenter.instance(getContext()).getSectionContentObserve(feed.getId());

        if (content == null) {
            return;
        }

        RxHelper.disposeActions(sFeedAction);
        MessageHelpers.showMessage(getContext(), getContext().getString(R.string.keyword_filter_feed_loading, feed.getTitle()));
        loadFeedPage(feed, content, new FeedTitles());
    }

    private void loadFeedPage(BrowseSection feed, Observable<List<MediaGroup>> page, FeedTitles feedTitles) {
        feedTitles.pages++;
        feedTitles.pageGroups = 0;

        // Rows come in parts
        sFeedAction = RxHelper.execute(page,
                groups -> addFeedTitles(groups, feedTitles),
                error -> {
                    Log.e(TAG, "Can't load the feed %s: %s", feed.getTitle(), error.getMessage());

                    if (feedTitles.titles.isEmpty()) {
                        MessageHelpers.showLongMessage(getContext(), error.getMessage());
                    } else {
                        showFeedWords(feed, feedTitles.titles);
                    }
                },
                () -> onFeedPageLoaded(feed, feedTitles));
    }

    private static void addFeedTitles(List<MediaGroup> groups, FeedTitles feedTitles) {
        if (groups == null) {
            return;
        }

        for (MediaGroup group : groups) {
            if (group == null) {
                continue;
            }

            feedTitles.lastGroup = group;
            feedTitles.pageGroups++;

            if (group.getMediaItems() == null) {
                continue;
            }

            for (MediaItem item : group.getMediaItems()) {
                // Channel and playlist cards aren't filtered
                if (item != null && item.getVideoId() != null && item.getTitle() != null) {
                    feedTitles.titles.add(item.getTitle());
                }
            }
        }
    }

    private void onFeedPageLoaded(BrowseSection feed, FeedTitles feedTitles) {
        MediaGroup grid = feedTitles.pageGroups == 1 ? feedTitles.lastGroup : null;

        if (grid != null && grid.getNextPageKey() != null && feedTitles.titles.size() < FEED_MIN_VIDEOS && feedTitles.pages < FEED_MAX_PAGES) {
            loadFeedPage(feed, getContentService().continueGroupObserve(grid).map(Collections::singletonList), feedTitles);
            return;
        }

        showFeedWords(feed, feedTitles.titles);
    }

    private void showFeedWords(BrowseSection feed, List<String> titles) {
        // Closed while loading
        if (!AppDialogPresenter.instance(getContext()).isDialogShown()) {
            return;
        }

        List<String> words = KeywordFilter.getUniqueWords(titles);

        if (words.isEmpty()) {
            MessageHelpers.showMessage(getContext(), getContext().getString(R.string.keyword_filter_feed_empty, feed.getTitle()));
            return;
        }

        // The whole screen: a feed has too many words for the settings panel
        KeywordWordsPresenter.instance(getContext()).show(getContext().getString(R.string.keyword_filter_feed_words_title, feed.getTitle()), words,
                () -> showOverMenu(this::showFeeds)); // Back to the feeds
    }

    /**
     * The mic is asked for the first time. The settings close for the prompt and open again on the Keyword filtering menu.
     */
    private void startVoiceInput() {
        Context context = getContext();

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            MessageHelpers.showLongMessage(context, R.string.keyword_filter_voice_unavailable);
            return;
        }

        if (PermissionHelpers.hasMicPermissions(context)) {
            startListening();
            return;
        }

        if (!(context instanceof MotherActivity)) {
            return;
        }

        MotherActivity activity = (MotherActivity) context;

        // The prompt opens behind the settings otherwise
        AppDialogPresenter.instance(context).closeDialog();

        activity.addOnPermissions((requestCode, permissions, grantResults) -> {
            if (requestCode != PermissionHelpers.REQUEST_MIC) {
                return;
            }

            show();

            if (grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            }
        });
        PermissionHelpers.verifyMicPermissions(activity);
    }

    private void startListening() {
        Context context = getContext();

        destroyRecognizer();

        sRecognizer = SpeechRecognizer.createSpeechRecognizer(context.getApplicationContext());
        sRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                MessageHelpers.showMessage(context, R.string.keyword_filter_voice_listening);
            }

            @Override
            public void onResults(Bundle results) {
                destroyRecognizer();
                onVoiceResults(results != null ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null);
            }

            @Override
            public void onError(int error) {
                destroyRecognizer();
                Log.e(TAG, "Voice input error: %s", error);

                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    MessageHelpers.showMessage(context, R.string.keyword_filter_voice_no_match);
                } else {
                    MessageHelpers.showLongMessage(context, context.getString(R.string.keyword_filter_voice_error, error));
                }
            }

            @Override
            public void onBeginningOfSpeech() {
                // NOP
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // NOP
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                // NOP
            }

            @Override
            public void onEndOfSpeech() {
                // NOP
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                // NOP
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
                // NOP
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, VOICE_MAX_RESULTS);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        sRecognizer.startListening(intent);
    }

    /**
     * The best guess is added right away. The others are there to check instead when it's misheard.
     * All the words said are one keyword, e.g. "funny cats".
     */
    private void onVoiceResults(List<String> results) {
        Set<String> keywords = new LinkedHashSet<>();

        if (results != null) {
            for (String result : results) {
                String keyword = KeywordFilter.normalize(result);

                if (keyword != null) {
                    keywords.add(keyword);
                }
            }
        }

        if (keywords.isEmpty()) {
            MessageHelpers.showMessage(getContext(), R.string.keyword_filter_voice_no_match);
            return;
        }

        List<String> heard = new ArrayList<>(keywords);
        mData.addKeyword(heard.get(0));
        showOverMenu(() -> showWords(getContext().getString(R.string.keyword_filter_voice_title), heard, null));
    }

    private static void destroyRecognizer() {
        if (sRecognizer != null) {
            sRecognizer.destroy();
            sRecognizer = null;
        }
    }
}
