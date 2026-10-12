package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

/**
 * The content hidden in the sections (Shorts, watched, upcoming, mixes, streams, Home categories) and the Shorts quick toggle.<br/>
 * The ones in use stay in MediaServiceData: the shared modules hide the content by them.
 * This keeps a copy per account and puts the account's into MediaServiceData when the account changes (see LocaleData).
 */
public class HideContentData extends DataChangeBase implements ProfileChangeListener {
    static final String HIDE_CONTENT_DATA = "hide_content_data";
    @SuppressLint("StaticFieldLeak")
    private static HideContentData sInstance;
    private final AppPrefs mPrefs;

    private HideContentData(Context context) {
        mPrefs = AppPrefs.instance(context);

        // They were for all the accounts before: each account that has been used keeps them. Empty once that's done.
        if (mPrefs.getData(HIDE_CONTENT_DATA) == null) {
            mPrefs.setData(HIDE_CONTENT_DATA, getCurrentData());
        }

        mPrefs.migrateToProfileData(HIDE_CONTENT_DATA);

        // E.g. copied from the profile the account shared since the last start (see AppPrefs.copySharedProfiles)
        restoreState();

        mPrefs.addListener(this);
    }

    public static HideContentData instance(Context context) {
        if (sInstance == null) {
            sInstance = new HideContentData(context.getApplicationContext());
        }

        return sInstance;
    }

    public void setContentHidden(int content, boolean hide) {
        MediaServiceData.instance().setContentHidden(content, hide);
        persistState();
    }

    public void setShortsQuickToggleEnabled(boolean enable) {
        MediaServiceData.instance().setShortsQuickToggleEnabled(enable);
        persistState();
    }

    public void setShortsQuickToggleHiding(boolean hide) {
        MediaServiceData.instance().setShortsQuickToggleHiding(hide);
        persistState();
    }

    /**
     * The account (or the per account switch) has changed: its hidden content and Shorts quick toggle are the ones in use now
     */
    @Override
    public void onProfileChanged() {
        restoreState();
        onDataChange();
    }

    private void restoreState() {
        String[] split = Helpers.splitData(mPrefs.getProfileData(HIDE_CONTENT_DATA));
        int hiddenContent = Helpers.parseInt(split, 0, MediaServiceData.CONTENT_HIDDEN_DEFAULT);
        boolean isShortsQuickToggleEnabled = Helpers.parseBoolean(split, 1, false);
        boolean isShortsQuickToggleHiding = Helpers.parseBoolean(split, 2, false);
        MediaServiceData data = MediaServiceData.instance();

        if (data.getHiddenContentInSettings() != hiddenContent) {
            data.setHiddenContentInSettings(hiddenContent);
        }

        if (data.isShortsQuickToggleEnabled() != isShortsQuickToggleEnabled) {
            data.setShortsQuickToggleEnabled(isShortsQuickToggleEnabled);
        }

        if (data.isShortsQuickToggleHiding() != isShortsQuickToggleHiding) {
            data.setShortsQuickToggleHiding(isShortsQuickToggleHiding);
        }
    }

    private void persistState() {
        mPrefs.setProfileData(HIDE_CONTENT_DATA, getCurrentData());
        onDataChange();
    }

    private static String getCurrentData() {
        MediaServiceData data = MediaServiceData.instance();

        return Helpers.mergeData(data.getHiddenContentInSettings(), data.isShortsQuickToggleEnabled(), data.isShortsQuickToggleHiding());
    }

    /**
     * The next instance reads the saved values again. This one stops putting them in MediaServiceData, which the other tests use too.
     */
    static void resetInstanceForTesting() {
        if (sInstance != null) {
            sInstance.mPrefs.removeListener(sInstance);
        }

        sInstance = null;
    }
}
