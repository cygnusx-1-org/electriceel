package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.LoadingManager;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

/**
 * Keeps Child mode to the channels the account is subscribed to: another channel doesn't open (e.g. from the featured
 * channels of a channel page or with the channel button of the player), and its card is hidden on a channel page (see VideoGroup).<br/>
 * The subscriptions are read again when a channel opens and they're {@link #REFRESH_PERIOD_MS} old,
 * or {@link #RETRY_PERIOD_MS} old when the channel isn't one of them (it might have just been subscribed to).<br/>
 * When they can't be read, only the channels known to be subscribed to open (see ContentService.getSubscribedChannelIdsObserve).
 */
public class ChildModeManager {
    private static final String TAG = ChildModeManager.class.getSimpleName();
    private static final long REFRESH_PERIOD_MS = 5 * 60 * 1_000;
    private static final long RETRY_PERIOD_MS = 30 * 1_000;
    // A channel id, or a handle from a link (see IntentExtractor.extractChannelId). Not a playlist opened as a channel ("VL" + its id).
    private static final String CHANNEL_ID_PREFIX = "UC";
    private static final String HANDLE_PREFIX = "@";
    @SuppressLint("StaticFieldLeak")
    private static ChildModeManager sInstance;
    private final Context mContext;
    private Loader mLoader;
    private AccountSource mAccountSource;
    private volatile Snapshot mSnapshot; // null while no list is known
    private Disposable mCheckAction;

    interface Loader {
        /**
         * @return the ids of the subscribed channels, an error when they can't be read
         */
        Observable<List<String>> getChannelIds();
    }

    interface AccountSource {
        /**
         * @return the signed-in account or null
         */
        String getAccount();
    }

    /**
     * The subscriptions of one account (null when signed out)
     */
    private static final class Snapshot {
        final String account;
        final Set<String> channelIds;
        final long loadedTimeMs;

        Snapshot(String account, Set<String> channelIds, long loadedTimeMs) {
            this.account = account;
            this.channelIds = channelIds;
            this.loadedTimeMs = loadedTimeMs;
        }
    }

    private ChildModeManager(Context context) {
        mContext = context;
        mLoader = YouTubeServiceManager.instance().getContentService()::getSubscribedChannelIdsObserve;
        mAccountSource = () -> WatchLaterManager.getAccountKey(MediaServiceManager.instance().getSelectedAccount());
    }

    public static ChildModeManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new ChildModeManager(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * The card of the channel isn't shown: Child mode is on and the channel isn't known to be subscribed to
     */
    public boolean isHidden(String channelId) {
        return isChecked(channelId) && !contains(channelId);
    }

    /**
     * Runs the action when the channel may be opened. Otherwise tells it isn't available.<br/>
     * Without the id there's nothing to check: it's checked once it's known (see ChannelPresenter.openChannel).
     * A new check drops the one still waiting for the subscriptions.
     */
    public void checkChannel(Context context, String channelId, Runnable onAllowed) {
        RxHelper.disposeActions(mCheckAction);

        if (!isChecked(channelId)) {
            onAllowed.run();
            return;
        }

        String account = mAccountSource.getAccount();
        Snapshot snapshot = getSnapshot(account);
        boolean isKnown = snapshot != null && snapshot.channelIds.contains(channelId);
        long ageMs = snapshot != null ? System.currentTimeMillis() - snapshot.loadedTimeMs : Long.MAX_VALUE;

        if (ageMs < (isKnown ? REFRESH_PERIOD_MS : RETRY_PERIOD_MS)) {
            apply(context, isKnown, onAllowed);
            return;
        }

        LoadingManager.showLoading(context, true);

        mCheckAction = load(account)
                .doOnDispose(() -> LoadingManager.showLoading(context, false))
                .subscribe(
                        loaded -> {
                            // Before the action: it might show a loading of its own (e.g. of the channel rows)
                            LoadingManager.showLoading(context, false);
                            apply(context, contains(channelId), onAllowed);
                        },
                        error -> Log.e(TAG, "checkChannel error: %s", error.getMessage())
                );
    }

    private boolean isChecked(String channelId) {
        return channelId != null && (channelId.startsWith(CHANNEL_ID_PREFIX) || channelId.startsWith(HANDLE_PREFIX))
                && GeneralData.instance(mContext).isChildModeEnabled();
    }

    private boolean contains(String channelId) {
        Snapshot snapshot = getSnapshot(mAccountSource.getAccount());

        return snapshot != null && snapshot.channelIds.contains(channelId);
    }

    /**
     * The known subscriptions of the account
     */
    private Snapshot getSnapshot(String account) {
        Snapshot snapshot = mSnapshot;

        return snapshot != null && Helpers.equals(snapshot.account, account) ? snapshot : null;
    }

    /**
     * Completes with whether they were read. When they weren't, the ones known before stay.
     */
    private Observable<Boolean> load(String account) {
        return mLoader.getChannelIds()
                .map(channelIds -> {
                    // Another account was picked while it loaded
                    if (Helpers.equals(account, mAccountSource.getAccount())) {
                        mSnapshot = new Snapshot(account, Collections.unmodifiableSet(new HashSet<>(channelIds)), System.currentTimeMillis());
                    }

                    return true;
                })
                .onErrorReturn(error -> {
                    Log.e(TAG, "Can't read the subscriptions: %s", error.getMessage());
                    return false;
                });
    }

    private static void apply(Context context, boolean isAllowed, Runnable onAllowed) {
        if (isAllowed) {
            onAllowed.run();
        } else {
            MessageHelpers.showMessage(context, R.string.child_mode_channel_unavailable);
        }
    }

    /**
     * Reads with the given ones from now on and forgets the subscriptions
     */
    void resetForTesting(Loader loader, AccountSource accountSource) {
        RxHelper.disposeActions(mCheckAction);
        mLoader = loader;
        mAccountSource = accountSource;
        mSnapshot = null;
    }

    /**
     * Makes the subscriptions look read the given time ago
     */
    void setLoadedTimeForTesting(long agoMs) {
        Snapshot snapshot = mSnapshot;

        if (snapshot != null) {
            mSnapshot = new Snapshot(snapshot.account, snapshot.channelIds, System.currentTimeMillis() - agoMs);
        }
    }
}
