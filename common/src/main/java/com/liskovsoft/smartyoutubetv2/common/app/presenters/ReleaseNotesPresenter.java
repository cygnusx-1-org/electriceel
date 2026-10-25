package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ReleaseNotesView;
import com.liskovsoft.smartyoutubetv2.common.misc.GitHubReleases.Release;

/**
 * The notes of a release on the whole screen, rendered by GitHub (Settings → Updates → Release notes → a version).
 * Its pictures open on their own, its links as QR codes.
 */
public class ReleaseNotesPresenter extends BasePresenter<ReleaseNotesView> {
    @SuppressLint("StaticFieldLeak")
    private static ReleaseNotesPresenter sInstance;
    private Release mRelease;
    private Runnable mOnClose;

    private ReleaseNotesPresenter(Context context) {
        super(context);
    }

    public static ReleaseNotesPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new ReleaseNotesPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    /**
     * @param onClose runs after Back, e.g. to open the list of the releases again
     */
    public void show(Release release, Runnable onClose) {
        mRelease = release;
        mOnClose = onClose;

        // Still open (e.g. the activity is reused)
        if (getView() != null) {
            getView().update(release);
        }

        getViewManager().startView(ReleaseNotesView.class);
    }

    @Override
    public void onViewInitialized() {
        if (mRelease != null && getView() != null) {
            getView().update(mRelease);
        }
    }

    @Override
    public void onFinish() {
        super.onFinish();

        Runnable onClose = mOnClose;
        mOnClose = null;

        if (onClose != null) {
            onClose.run();
        }
    }

    /**
     * A link of the notes, e.g. of an issue: on a phone
     */
    public void openLink(String url) {
        QrCodePresenter.instance(getContext()).show(url);
    }
}
