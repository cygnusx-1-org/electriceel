package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.QrCodeView;

/**
 * The QR code of an address on the whole screen (e.g. Share link (QR code)), to open it on a phone.
 * Made on the device (see QrCodeEncoder): there at once, offline too.
 */
public class QrCodePresenter extends BasePresenter<QrCodeView> {
    @SuppressLint("StaticFieldLeak")
    private static QrCodePresenter sInstance;
    private String mUrl;

    private QrCodePresenter(Context context) {
        super(context);
    }

    public static QrCodePresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new QrCodePresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void show(String url) {
        mUrl = url;

        // Still open (e.g. the activity is reused)
        if (getView() != null) {
            getView().update(url);
        }

        getViewManager().startView(QrCodeView.class);
    }

    @Override
    public void onViewInitialized() {
        if (mUrl != null && getView() != null) {
            getView().update(mUrl);
        }
    }
}
