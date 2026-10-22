package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.complexcardview.ComplexImageCardView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the cards bound together (the first screen of a grid, a row scrolling in) hidden till all their images are in,
 * so they show complete at once rather than with the images popping in. No time limit: a slow image keeps its cards hidden
 * till it's in, or failed for good (Glide's timeouts, then the placeholder).<br/>
 * A held card keeps its place and its focus (see ComplexImageCardView.setContentHidden).
 */
final class CardHold {
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Map<ComplexImageCardView, Batch> mBatchByCard = new HashMap<>();
    // The cards bound in this pass (e.g. a layout) join it
    private Batch mOpenBatch;
    private final Runnable mCloseBatch = () -> {
        Batch batch = mOpenBatch;
        mOpenBatch = null;

        if (batch != null) {
            batch.close();
        }
    };

    /**
     * Hides the card till its image is in (see {@link #ready})
     */
    void hold(ComplexImageCardView card) {
        drop(card); // bound again before its image came in

        if (mOpenBatch == null) {
            mOpenBatch = new Batch();
            mHandler.post(mCloseBatch);
        }

        mOpenBatch.add(card);
        mBatchByCard.put(card, mOpenBatch);
        card.setContentHidden(true);
    }

    /**
     * The image of the card is in, or failed for good
     */
    void ready(ComplexImageCardView card) {
        Batch batch = mBatchByCard.get(card);

        if (batch != null) {
            batch.ready(card);
        }
    }

    /**
     * Shows the card at once, e.g. it's unbound
     */
    void drop(ComplexImageCardView card) {
        Batch batch = mBatchByCard.remove(card);

        if (batch != null) {
            card.setContentHidden(false);
            batch.ready(card);
        }
    }

    private final class Batch {
        private final List<ComplexImageCardView> mCards = new ArrayList<>();
        private final Set<ComplexImageCardView> mPending = new HashSet<>();
        private boolean mClosed;

        void add(ComplexImageCardView card) {
            mCards.add(card);
            mPending.add(card);
        }

        void ready(ComplexImageCardView card) {
            mPending.remove(card);
            revealIfDone();
        }

        void close() {
            mClosed = true;
            revealIfDone();
        }

        private void revealIfDone() {
            if (mClosed && mPending.isEmpty()) {
                reveal();
            }
        }

        private void reveal() {
            for (ComplexImageCardView card : mCards) {
                if (mBatchByCard.get(card) == this) {
                    mBatchByCard.remove(card);
                    card.setContentHidden(false);
                }
            }

            mCards.clear();
        }
    }
}
