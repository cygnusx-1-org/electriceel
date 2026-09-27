package com.liskovsoft.smartyoutubetv2.tv.ui.keywords;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.KeywordWordsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.KeywordWordsView;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The words on the whole screen: the letters are tabs above a grid of the words that start with the focused one.
 * The items look the same size in every state, only the colors change.
 */
public class KeywordWordsFragment extends Fragment implements KeywordWordsView {
    private static final int WORD_COLUMNS = 4;
    private KeywordWordsPresenter mPresenter;
    private TextView mTitleView;
    private TextView mDescriptionView;
    private HorizontalGridView mLettersView;
    private VerticalGridView mWordsView;
    private final List<String> mLetters = new ArrayList<>();
    private final List<List<String>> mWordsByLetter = new ArrayList<>();
    private final LetterAdapter mLetterAdapter = new LetterAdapter();
    private final WordAdapter mWordAdapter = new WordAdapter();
    private int mCurrentLetter;
    private int mAccentColor;
    private int mFocusedBackgroundColor;
    private int mFocusedTextColor;
    private int mTextColor;
    private int mDimTextColor;
    private int mWordBackgroundColor;
    private float mCornerRadius;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPresenter = KeywordWordsPresenter.instance(getContext());
        mPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.keyword_words, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initColors(view.getContext());

        mTitleView = view.findViewById(R.id.keyword_words_title);
        mDescriptionView = view.findViewById(R.id.keyword_words_description);
        mLettersView = view.findViewById(R.id.keyword_words_letters);
        mWordsView = view.findViewById(R.id.keyword_words_grid);

        mLettersView.setHorizontalSpacing(getResources().getDimensionPixelSize(R.dimen.keyword_letter_tab_spacing));
        mLettersView.setItemAnimator(null); // the focused tab changes its colors in place
        mLettersView.setAdapter(mLetterAdapter);
        // The tabs switch on focus, so the words are there before moving down to them
        mLettersView.setOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(RecyclerView parent, RecyclerView.ViewHolder child, int position, int subposition) {
                selectLetter(position);
            }
        });

        int wordSpacing = getResources().getDimensionPixelSize(R.dimen.keyword_word_spacing);
        mWordsView.setNumColumns(WORD_COLUMNS);
        mWordsView.setColumnWidth(getResources().getDimensionPixelSize(R.dimen.keyword_word_width));
        mWordsView.setHorizontalSpacing(wordSpacing);
        mWordsView.setVerticalSpacing(wordSpacing);
        mWordsView.setItemAnimator(null); // a checked word changes its colors in place
        mWordsView.setAdapter(mWordAdapter);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mPresenter.onViewInitialized();
    }

    public void onFinish() {
        mPresenter.onFinish();
    }

    /**
     * Back from the words goes to the tabs, the grid remembers the current one
     *
     * @return the focus moved to the tabs
     */
    public boolean focusLetters() {
        if (mWordsView == null || !mWordsView.hasFocus()) {
            return false;
        }

        return mLettersView.requestFocus();
    }

    @Override
    public void update(String title, String description, Map<String, List<String>> wordsByLetter) {
        if (mTitleView == null) {
            return;
        }

        mTitleView.setText(title);
        mDescriptionView.setText(description);

        mLetters.clear();
        mWordsByLetter.clear();

        for (Map.Entry<String, List<String>> letter : wordsByLetter.entrySet()) {
            mLetters.add(letter.getKey());
            mWordsByLetter.add(letter.getValue());
        }

        mCurrentLetter = 0;
        mLetterAdapter.notifyDataSetChanged();
        mWordAdapter.setWords(mWordsByLetter.isEmpty() ? Collections.<String>emptyList() : mWordsByLetter.get(0));
        mLettersView.setSelectedPosition(0);
        mLettersView.requestFocus();
    }

    private void selectLetter(int position) {
        if (position < 0 || position >= mWordsByLetter.size() || position == mCurrentLetter) {
            return;
        }

        int previous = mCurrentLetter;
        mCurrentLetter = position;

        updateLetter(previous);
        updateLetter(position);

        mWordAdapter.setWords(mWordsByLetter.get(position));
        mWordsView.setSelectedPosition(0);
    }

    private void updateLetter(int position) {
        RecyclerView.ViewHolder holder = mLettersView.findViewHolderForAdapterPosition(position);

        if (holder instanceof LetterViewHolder) {
            ((LetterViewHolder) holder).updateState();
        }
    }

    private void initColors(Context context) {
        mAccentColor = getThemeColor(context, R.attr.brandAccentColor, R.color.fastlane_background);
        mFocusedBackgroundColor = getThemeColor(context, R.attr.cardSelectedBackground, R.color.card_selected_background_white);
        mFocusedTextColor = ContextCompat.getColor(context, R.color.card_selected_text_grey);
        mTextColor = ContextCompat.getColor(context, R.color.white);
        mDimTextColor = ContextCompat.getColor(context, R.color.keyword_words_dim_text);
        mWordBackgroundColor = ContextCompat.getColor(context, R.color.keyword_word_background);
        mCornerRadius = context.getResources().getDimension(R.dimen.keyword_tab_corner_radius);
    }

    /**
     * The color scheme sets the colors
     */
    private static int getThemeColor(Context context, int attr, int fallbackResId) {
        TypedValue value = new TypedValue();

        if (context.getTheme().resolveAttribute(attr, value, true) &&
                value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }

        return ContextCompat.getColor(context, fallbackResId);
    }

    private GradientDrawable createBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(mCornerRadius);
        return background;
    }

    private class LetterViewHolder extends RecyclerView.ViewHolder {
        private final TextView mLetterView;
        private final View mUnderlineView;
        private final GradientDrawable mBackground;

        LetterViewHolder(View itemView) {
            super(itemView);
            mLetterView = itemView.findViewById(R.id.keyword_letter);
            mUnderlineView = itemView.findViewById(R.id.keyword_letter_underline);
            mUnderlineView.setBackgroundColor(mAccentColor);
            mBackground = createBackground();
            itemView.setBackground(mBackground);
            itemView.setOnFocusChangeListener((v, hasFocus) -> updateState());
        }

        void bind(String letter) {
            mLetterView.setText(letter);
            updateState();
        }

        void updateState() {
            boolean isCurrent = getAdapterPosition() == mCurrentLetter;
            boolean isFocused = itemView.hasFocus();

            mBackground.setColor(isFocused ? mFocusedBackgroundColor : 0);
            mLetterView.setTextColor(isFocused ? mFocusedTextColor : isCurrent ? mTextColor : mDimTextColor);
            mUnderlineView.setVisibility(isCurrent ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private class LetterAdapter extends RecyclerView.Adapter<LetterViewHolder> {
        @NonNull
        @Override
        public LetterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new LetterViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.keyword_letter_tab, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull LetterViewHolder holder, int position) {
            holder.bind(mLetters.get(position));
        }

        @Override
        public int getItemCount() {
            return mLetters.size();
        }
    }

    private class WordViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mCheckView;
        private final TextView mWordView;
        private final GradientDrawable mBackground;
        private String mWord;

        WordViewHolder(View itemView) {
            super(itemView);
            mCheckView = itemView.findViewById(R.id.keyword_word_check);
            mWordView = itemView.findViewById(R.id.keyword_word);
            mBackground = createBackground();
            itemView.setBackground(mBackground);
            itemView.setOnFocusChangeListener((v, hasFocus) -> updateState());
            itemView.setOnClickListener(v -> {
                if (mWord != null) {
                    mPresenter.toggleKeyword(mWord);
                    updateState();
                }
            });
        }

        void bind(String word) {
            mWord = word;
            mWordView.setText(word);
            updateState();
        }

        void updateState() {
            boolean isChecked = mWord != null && mPresenter.isKeyword(mWord);
            boolean isFocused = itemView.hasFocus();
            int textColor = isFocused ? mFocusedTextColor : mTextColor;

            mBackground.setColor(isFocused ? mFocusedBackgroundColor : isChecked ? mAccentColor : mWordBackgroundColor);
            mWordView.setTextColor(textColor);
            mCheckView.setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
            mCheckView.setVisibility(isChecked ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private class WordAdapter extends RecyclerView.Adapter<WordViewHolder> {
        private List<String> mWords = Collections.emptyList();

        void setWords(List<String> words) {
            mWords = words;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new WordViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.keyword_word_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
            holder.bind(mWords.get(position));
        }

        @Override
        public int getItemCount() {
            return mWords.size();
        }
    }
}
