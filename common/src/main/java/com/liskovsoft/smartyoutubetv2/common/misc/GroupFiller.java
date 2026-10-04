package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;

/**
 * Gives a group that lost its videos to Hide content its next pages until it has enough cards to fill the screen.<br/>
 * A group collects its pages (see VideoGroup.from), so its size is all its cards.
 */
public class GroupFiller {
    // Hide content might hide every video of an endless feed (e.g. Home)
    static final int MAX_PAGES = 5;
    // The longest the groups are held back, e.g. on a slow network. They get the rest after they're shown.
    static final long MAX_HOLD_MS = 5_000;
    // Group -> the pages it got while it was short
    private final Map<VideoGroup, Integer> mPages = new WeakHashMap<>();
    private final Scheduler mScheduler;

    public interface Continuation {
        Observable<MediaGroup> continueGroup(VideoGroup group);
    }

    public GroupFiller() {
        this(AndroidSchedulers.mainThread());
    }

    GroupFiller(Scheduler scheduler) {
        mScheduler = scheduler;
    }

    /**
     * Counts the page when the group needs it
     */
    public boolean shouldContinue(VideoGroup group, int minSize) {
        if (group == null || group.getMediaGroup() == null) {
            return false;
        }

        if (group.getSize() >= minSize) {
            return false;
        }

        Integer pages = mPages.get(group);
        int pageCount = pages != null ? pages : 0;

        if (pageCount >= MAX_PAGES) {
            return false;
        }

        mPages.put(group, pageCount + 1);

        return true;
    }

    /**
     * Emits the groups once the short ones have their next pages, so a row is shown complete rather than with a few cards
     * that get the rest after a pause. The groups are filled in parallel and emitted in their order.<br/>
     * A failed or slow page doesn't hold the groups back: they're shown with the cards they have.
     */
    public Observable<List<VideoGroup>> fill(List<VideoGroup> groups, int minSize, Continuation continuation) {
        return Observable.defer(() -> {
            List<Completable> fills = new ArrayList<>();

            for (VideoGroup group : groups) {
                fills.add(fill(group, minSize, continuation));
            }

            return Completable.merge(fills)
                    .timeout(MAX_HOLD_MS, TimeUnit.MILLISECONDS, mScheduler)
                    .onErrorComplete()
                    .andThen(Observable.just(groups));
        });
    }

    private Completable fill(VideoGroup group, int minSize, Continuation continuation) {
        if (!shouldContinue(group, minSize)) {
            return Completable.complete();
        }

        return continuation.continueGroup(group)
                .concatMapCompletable(mediaGroup -> fill(VideoGroup.from(group, mediaGroup), minSize, continuation))
                .onErrorComplete();
    }
}
