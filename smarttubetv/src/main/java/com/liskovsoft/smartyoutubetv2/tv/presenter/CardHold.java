package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.complexcardview.ComplexImageCardView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the cards bound together (the first screen of a grid or of the rows, a row scrolling in) hidden till all their images are in,
 * so they show complete at once rather than with the images popping in. No time limit: a slow image keeps its cards hidden
 * till it's in, or failed for good (Glide's timeouts, then the placeholder).<br/>
 * A held card keeps its place and its focus (see ComplexImageCardView.setContentHidden). The title of a row bound with its cards
 * shows with them (see {@link #holdWith}).<br/>
 * One hold is shared by the presenters of a view (e.g. of the videos, the shorts and the rows), so all it binds together shows together.
 */
public final class CardHold {
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Map<View, Batch> mBatchByView = new HashMap<>();
    // The views bound in this pass (e.g. a layout) join it
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

        getOpenBatch().add(card, true);
    }

    /**
     * Hides the view (e.g. the title of a row) with the cards bound with it, till they're in. When none of them is held,
     * it isn't hidden, not even till the batch closes. Transparent rather than invisible, so it keeps its place.
     */
    void holdWith(View view) {
        drop(view); // bound again before the cards came in

        getOpenBatch().add(view, false);
    }

    /**
     * The image of the card is in, or failed for good
     */
    void ready(ComplexImageCardView card) {
        Batch batch = mBatchByView.get(card);

        if (batch != null) {
            batch.ready(card);
        }
    }

    /**
     * Shows the view at once, e.g. it's unbound
     */
    void drop(View view) {
        Batch batch = mBatchByView.remove(view);

        if (batch != null) {
            show(view);
            batch.ready(view);
        }
    }

    private Batch getOpenBatch() {
        if (mOpenBatch == null) {
            mOpenBatch = new Batch();
            mHandler.post(mCloseBatch);
        }

        return mOpenBatch;
    }

    private static void hide(View view) {
        if (view instanceof ComplexImageCardView) {
            ((ComplexImageCardView) view).setContentHidden(true);
        } else {
            view.setAlpha(0);
        }
    }

    private static void show(View view) {
        if (view instanceof ComplexImageCardView) {
            ((ComplexImageCardView) view).setContentHidden(false);
        } else {
            view.setAlpha(1);
        }
    }

    private final class Batch {
        private final List<View> mViews = new ArrayList<>();
        private final Set<View> mPending = new HashSet<>();
        private boolean mClosed;
        // A card is held: the views of the batch are hidden
        private boolean mIsHidden;

        /**
         * @param isPending the batch shows when it's ready (a card), rather than with the others (the title of a row)
         */
        void add(View view, boolean isPending) {
            mViews.add(view);
            mBatchByView.put(view, this);

            if (isPending) {
                mPending.add(view);
            }

            if (mIsHidden) {
                hide(view);
            } else if (isPending) {
                // The first card: the views bound before it hide with it
                mIsHidden = true;

                for (View added : mViews) {
                    if (mBatchByView.get(added) == this) {
                        hide(added);
                    }
                }
            }
        }

        void ready(View view) {
            mPending.remove(view);
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
            for (View view : mViews) {
                if (mBatchByView.get(view) == this) {
                    mBatchByView.remove(view);
                    show(view);
                }
            }

            mViews.clear();
        }
    }
}
