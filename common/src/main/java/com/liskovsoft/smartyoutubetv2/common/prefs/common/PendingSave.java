package com.liskovsoft.smartyoutubetv2.common.prefs.common;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * A save posted to the main thread that knows whether it's still waiting: flush saves now only what's waiting. Saving
 * unasked would write the values never saved (the defaults) too, and the storage sync would take them for changes made here
 * (see AppPrefs.persistPendingData).
 */
public final class PendingSave implements Runnable {
    private final Runnable mSave;
    private volatile boolean mIsPending; // set by a save posted from another thread too

    public PendingSave(Runnable save) {
        mSave = save;
    }

    public void post() {
        mIsPending = true;
        Utils.post(this);
    }

    public void postDelayed(long delayMs) {
        mIsPending = true;
        Utils.postDelayed(this, delayMs);
    }

    /**
     * Dropped: the values are read again (another account, or the sync changed them)
     */
    public void cancel() {
        mIsPending = false;
        Utils.removeCallbacks(this);
    }

    /**
     * Saves now, if a save is waiting
     */
    public void flush() {
        if (mIsPending) {
            cancel();
            mSave.run();
        }
    }

    @Override
    public void run() {
        mIsPending = false;
        mSave.run();
    }
}
