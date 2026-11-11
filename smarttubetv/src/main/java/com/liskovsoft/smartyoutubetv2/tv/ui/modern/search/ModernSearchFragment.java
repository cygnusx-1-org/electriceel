package com.liskovsoft.smartyoutubetv2.tv.ui.modern.search;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.SinglePresenterSelector;

import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.misc.CrashRestorer;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CardHold;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse.ModernRowsView;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernSpinner;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernTheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets.ModernCardPresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * Search page: voice button and query field on top (typed with the system keyboard), suggestions, results below.
 */
public class ModernSearchFragment extends Fragment implements SearchView {
    private static final int REQUEST_VOICE = 1;
    private SearchPresenter mPresenter;
    private MediaServiceSearchTagProvider mTagsProvider;
    private CrashRestorer mCrashRestorer;
    private View mPage;
    private EditText mField;
    private LinearLayout mSuggestions;
    /**
     * The previous searches or suggestions loaded for the query, shown as far as they fit (see showSuggestions)
     */
    private List<Tag> mTags;
    private ModernRowsView mResults;
    private View mProgress;
    private String mQuery = "";
    private String mSearchedQuery;
    private ModernVoiceSearch mVoice;
    private View mMic;
    private boolean mFocusResultsPending;
    private boolean mIsFragmentCreated;
    private final ViewTreeObserver.OnGlobalFocusChangeListener mFocusListener = (oldFocus, newFocus) -> onFocusMoved(newFocus);
    /**
     * Search setting "Show keyboard automatically": the keyboard is shown once the window has the focus
     */
    private boolean mShowKeyboardPending;
    private final ViewTreeObserver.OnWindowFocusChangeListener mWindowFocusListener = hasFocus -> {
        if (hasFocus && mShowKeyboardPending) {
            mShowKeyboardPending = false;
            requestKeyboard();
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(null);
        mCrashRestorer = new CrashRestorer(getContext(), savedInstanceState);
        mIsFragmentCreated = true;
        mPresenter = SearchPresenter.instance(getContext());
        mPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.modern_search, container, false);
        mPage = root.findViewById(R.id.modern_search_page);
        mField = root.findViewById(R.id.modern_search_field);
        mSuggestions = root.findViewById(R.id.modern_search_suggestions);
        mProgress = root.findViewById(R.id.modern_search_progress);
        ModernSpinner.apply((ProgressBar) mProgress);

        ModernCardPresenter cardPresenter = new ModernCardPresenter();
        // As the classic screens: the cards bound together show together, with their images (see CardHold)
        cardPresenter.enableImageHold(new CardHold());
        cardPresenter.setOnItemViewLongPressedListener((viewHolder, item) -> {
            if (item instanceof Video) {
                mPresenter.onVideoItemLongClicked((Video) item);
            }
        });
        mResults = new ModernRowsView(requireContext(), mPresenter, new SinglePresenterSelector(cardPresenter));
        ((FrameLayout) root.findViewById(R.id.modern_search_results)).addView(mResults);
        updateResultsFocusability();

        mMic = root.findViewById(R.id.modern_search_mic);
        mMic.setBackground(ModernColorScheme.micBackground(requireContext()));
        mMic.setOnClickListener(v -> startVoiceRecognition());
        mVoice = new ModernVoiceSearch(requireContext(), new ModernVoiceSearch.Callback() {
            @Override
            public void onListening(boolean listening) {
                mMic.setActivated(listening);
                mField.setHint(listening ? R.string.lb_search_bar_hint_speech : R.string.settings_search);
            }

            @Override
            public void onPartialResult(String text) {
                mField.setText(text);
            }

            @Override
            public void onResult(String text) {
                startSearch(text);
            }

            @Override
            public void onStartIntent(Intent intent) {
                try {
                    startActivityForResult(intent, REQUEST_VOICE);
                } catch (ActivityNotFoundException e) {
                    showSystemKeyboard();
                }
            }
        });
        setupSystemInput();

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.getViewTreeObserver().addOnGlobalFocusChangeListener(mFocusListener);
        view.getViewTreeObserver().addOnWindowFocusChangeListener(mWindowFocusListener);
        mPresenter.onViewInitialized();
        mCrashRestorer.restorePlayback();

        focusField();
    }

    @Override
    public void onPause() {
        super.onPause();
        mVoice.stop();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mCrashRestorer.persistVideo(outState, mPresenter.getCurrentVideo());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mIsFragmentCreated) {
            mPresenter.onViewResumed();
        }
        mIsFragmentCreated = false;
    }

    @Override
    public void onDestroyView() {
        if (getView() != null) {
            getView().getViewTreeObserver().removeOnGlobalFocusChangeListener(mFocusListener);
            getView().getViewTreeObserver().removeOnWindowFocusChangeListener(mWindowFocusListener);
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // The activity of another skin may have taken its place already (see Skins)
        if (!mPresenter.isReplacedView(this)) {
            mPresenter.onViewDestroyed();
        }
    }

    public void onFinish() {
        mPresenter.onFinish();
    }

    // SearchView

    @Override
    public void updateSearch(VideoGroup group) {
        mResults.update(group);
        updateResultsFocusability();
        fitSuggestions();

        // Search setting: the results take the focus once they arrive
        if (mFocusResultsPending && !mResults.isEmpty()) {
            mFocusResultsPending = false;
            if (SearchData.instance(getContext()).isFocusOnResultsEnabled()) {
                mResults.post(mResults::focusContent);
            }
        }
    }

    @Override
    public void clearSearch() {
        mSearchedQuery = null;
        mResults.clear();
        updateResultsFocusability();
        fitSuggestions();
    }

    /**
     * Down from the suggestions doesn't land on an empty results area (the page would scroll to nothing).
     */
    private void updateResultsFocusability() {
        mResults.setDescendantFocusability(mResults.isEmpty() ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    @Override
    public void clearSearchTags() {
        mTags = null;
        mSuggestions.removeAllViews();
    }

    @Override
    public void removeSearchTag(Tag tag) {
        for (int i = 0; i < mSuggestions.getChildCount(); i++) {
            View child = mSuggestions.getChildAt(i);
            if (child.getTag() == tag) {
                mSuggestions.removeViewAt(i);
                break;
            }
        }

        // The next one, hidden so far, takes the freed row
        if (mTags != null && mTags.remove(tag) && mSuggestions.getChildCount() < Math.min(mTags.size(), getMaxSuggestions())) {
            addSuggestion(mTags.get(mSuggestions.getChildCount()));
        }
    }

    @Override
    public void setTagsProvider(MediaServiceSearchTagProvider provider) {
        mTagsProvider = provider;
        loadSuggestions(mQuery);
    }

    @Override
    public void showProgressBar(boolean show) {
        if (mProgress != null) {
            mProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void startSearch(String searchText) {
        if (searchText != null) {
            setQuery(searchText);
            submit();
        } else {
            setQuery("");
            focusField();
        }
    }

    @Override
    public String getSearchText() {
        return mQuery;
    }

    @Override
    public void startVoiceRecognition() {
        mVoice.start();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_VOICE && resultCode == Activity.RESULT_OK && data != null) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                startSearch(results.get(0));
            }
        }
    }

    @Override
    public void finishReally() {
        if (getActivity() instanceof LeanbackActivity) {
            ((LeanbackActivity) getActivity()).finishReally();
        }
    }

    // Internal

    private void setQuery(String query) {
        mQuery = query != null ? query : "";

        if (!TextUtils.equals(mField.getText(), mQuery)) {
            mField.setText(mQuery);
            mField.setSelection(mQuery.length());
        }

        loadSuggestions(mQuery);
    }

    private void submit() {
        hideSystemKeyboard();

        if (!TextUtils.isEmpty(mQuery) && !mQuery.equals(mSearchedQuery)) {
            mSearchedQuery = mQuery;
            mFocusResultsPending = true;
            mPresenter.onSearch(mQuery);
        }
    }

    private void loadSuggestions(String query) {
        if (mTagsProvider == null) {
            return;
        }

        mTagsProvider.search(query, results -> {
            if (getContext() == null || !TextUtils.equals(query, mQuery)) {
                return;
            }

            mTags = results != null ? new ArrayList<>(results) : null;
            showSuggestions();
        });
    }

    /**
     * As many as fit: down to the bottom of the screen, or to the results when there are some below
     */
    private void showSuggestions() {
        mSuggestions.removeAllViews();

        if (mTags == null) {
            return;
        }

        int max = getMaxSuggestions();

        for (Tag tag : mTags) {
            if (mSuggestions.getChildCount() == max) {
                break;
            }
            addSuggestion(tag);
        }
    }

    /**
     * Whole rows only, the last one ends before the bottom edge of the screen (or the top of the results)
     */
    private int getMaxSuggestions() {
        int rowHeight = getResources().getDimensionPixelSize(R.dimen.modern_search_suggestion_height);
        int top = getResources().getDimensionPixelSize(R.dimen.modern_search_panel_top);
        int bottom;

        if (!mResults.isEmpty()) {
            bottom = getResources().getDimensionPixelSize(R.dimen.modern_search_results_top);
        } else {
            bottom = mPage != null && mPage.getHeight() > 0 ? mPage.getHeight() : getResources().getDisplayMetrics().heightPixels;
        }

        return Math.max(1, (bottom - top) / rowHeight);
    }

    /**
     * The results came or went: the suggestions take the room left
     */
    private void fitSuggestions() {
        if (mTags != null && mSuggestions.getChildCount() != Math.min(mTags.size(), getMaxSuggestions())) {
            showSuggestions();
        }
    }

    private void addSuggestion(Tag tag) {
        View item = LayoutInflater.from(getContext()).inflate(R.layout.modern_search_suggestion, mSuggestions, false);
        ((TextView) item.findViewById(R.id.modern_suggestion_text)).setText(tag.tag);
        item.setTag(tag);
        item.setOnClickListener(v -> startSearch(tag.tag));
        item.setOnLongClickListener(v -> {
            mPresenter.onTagLongClicked(tag);
            return true;
        });
        mSuggestions.addView(item);
    }

    private void setupSystemInput() {
        if (SearchData.instance(getContext()).isTypingCorrectionDisabled()) {
            // Search setting, same trick as the classic search: no suggestions or corrections from the keyboard
            mField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        }

        if (SearchData.instance(getContext()).isKeyboardFixEnabled()) {
            // Search setting, as the classic search: OK doesn't open the keyboard (G20s and other remotes)
            KeyHelpers.fixEnterKey(mField);
        }

        mField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!TextUtils.equals(s, mQuery)) {
                    setQuery(s.toString());
                }
            }
        });
        mField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                submit();
                return true;
            }
            return false;
        });
    }

    /**
     * The field takes the focus. With the search setting "Show keyboard automatically", the keyboard opens too: once the
     * window has the focus (shown before, the request is dropped, and the activity's soft input mode hides the keyboard
     * when its window gets the focus), posted after the field gets its input connection.
     */
    private void focusField() {
        mField.requestFocus();

        if (SearchData.instance(getContext()).isKeyboardAutoShowEnabled()) {
            if (mField.hasWindowFocus()) {
                requestKeyboard();
            } else {
                mShowKeyboardPending = true;
            }
        }
    }

    private void requestKeyboard() {
        mField.post(() -> {
            if (isAdded() && mField.hasFocus()) {
                showSystemKeyboard();
            }
        });
    }

    private void showSystemKeyboard() {
        mField.requestFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mField, 0);
        }
    }

    private void hideSystemKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null && mField.hasFocus()) {
            imm.hideSoftInputFromWindow(mField.getWindowToken(), 0);
        }
    }

    /**
     * Results scroll up to the top of the page when focused, like the other sections.
     */
    private void onFocusMoved(View focused) {
        if (mPage == null || focused == null) {
            return;
        }

        float target = isInside(focused, mResults) ?
                getResources().getDimension(R.dimen.modern_first_shelf_y) - getResources().getDimension(R.dimen.modern_search_results_top) : 0;

        if (mPage.getTranslationY() != target) {
            mPage.animate().translationY(target).setDuration(ModernTheme.ANIM_SCROLL_MS).setInterpolator(ModernTheme.scrollInterpolator());

            // Query panel makes room for the results
            float alpha = target == 0 ? 1f : 0f;
            for (View view : new View[] {mSuggestions, (View) mField.getParent()}) {
                view.animate().alpha(alpha).setDuration(ModernTheme.ANIM_FAST_MS);
            }
        }
    }

    private static boolean isInside(View view, View parent) {
        View current = view;
        while (current != null) {
            if (current == parent) {
                return true;
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return false;
    }
}
