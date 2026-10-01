package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.OldVideoFilter;
import com.liskovsoft.smartyoutubetv2.common.prefs.OldVideosData;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

/**
 * The quick toggle button on the main screen, next to the account button. It toggles Hide videos older than
 * (Content Filtering | Hide content) or the Shorts options (Content Filtering | Hide content | Shorts), whichever has its quick toggle enabled.
 * With both enabled, it opens a panel with a switch for each.
 */
public class QuickTogglePresenter extends BasePresenter<Void> {
    private QuickTogglePresenter(Context context) {
        super(context);
    }

    public static QuickTogglePresenter instance(Context context) {
        return new QuickTogglePresenter(context);
    }

    public boolean isOldVideosEnabled() {
        return OldVideosData.instance(getContext()).isQuickToggleEnabled();
    }

    public boolean isShortsEnabled() {
        return MediaServiceData.instance().isShortsQuickToggleEnabled();
    }

    /**
     * The quick toggle is on: the Shorts options apply, as they're set
     */
    public boolean isShortsHiding() {
        return MediaServiceData.instance().isShortsQuickToggleHiding();
    }

    /**
     * The button is shown
     */
    public boolean isEnabled() {
        return isOldVideosEnabled() || isShortsEnabled();
    }

    /**
     * The button toggles its filter, or opens the panel with both
     */
    public void onClick() {
        boolean isOldVideosEnabled = isOldVideosEnabled();
        boolean isShortsEnabled = isShortsEnabled();

        if (isOldVideosEnabled && isShortsEnabled) {
            showToggles();
        } else if (isOldVideosEnabled) {
            OldVideosData data = OldVideosData.instance(getContext());
            data.setEnabled(!data.isEnabled());
            MessageHelpers.showMessage(getContext(), getOldVideosState());
            refreshSection(true, false);
        } else if (isShortsEnabled) {
            MediaServiceData data = MediaServiceData.instance();
            data.setShortsQuickToggleHiding(!data.isShortsQuickToggleHiding());
            MessageHelpers.showMessage(getContext(), getShortsState());
            refreshSection(false, true);
        }
    }

    public String getOldVideosState() {
        OldVideosData data = OldVideosData.instance(getContext());

        return data.isEnabled() ?
                getContext().getString(R.string.hide_old_videos_on, OldVideoFilter.getPeriodTitle(getContext(), data.getPeriodMonths())) :
                getContext().getString(R.string.hide_old_videos_off);
    }

    public String getShortsState() {
        return getContext().getString(isShortsHiding() ? R.string.hide_shorts_on : R.string.hide_shorts_off);
    }

    /**
     * A switch per filter. The section is reloaded when the panel closes, if a switch of it has changed.
     */
    private void showToggles() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        OldVideosData oldVideosData = OldVideosData.instance(getContext());
        MediaServiceData shortsData = MediaServiceData.instance();
        boolean isOldVideosHiding = oldVideosData.isEnabled();
        boolean isShortsHiding = shortsData.isShortsQuickToggleHiding();

        presenter.appendSingleSwitch(UiOptionItem.from(
                getContext().getString(R.string.hide_old_videos_period, OldVideoFilter.getPeriodTitle(getContext(), oldVideosData.getPeriodMonths())),
                option -> oldVideosData.setEnabled(option.isSelected()),
                isOldVideosHiding));

        presenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.enable_hide_shorts_options),
                option -> shortsData.setShortsQuickToggleHiding(option.isSelected()),
                isShortsHiding));

        presenter.showDialog(getContext().getString(R.string.quick_toggles), () -> refreshSection(
                oldVideosData.isEnabled() != isOldVideosHiding, shortsData.isShortsQuickToggleHiding() != isShortsHiding));
    }

    /**
     * Reloads the section if a filter that has changed applies to it. Keeps the focus on the button, so it can be pressed again.
     */
    private void refreshSection(boolean isOldVideosChanged, boolean isShortsChanged) {
        BrowsePresenter presenter = BrowsePresenter.instance(getContext());
        BrowseSection section = presenter.getCurrentSection();

        if (section == null) {
            return;
        }

        if ((isOldVideosChanged && OldVideosData.instance(getContext()).isSectionEnabled(section.getId())) ||
                (isShortsChanged && isShortsSection(section.getId()))) {
            presenter.refresh(false);
        }
    }

    /**
     * The sections the Shorts are hidden from (see MediaGroupOptions of MediaServiceCore)
     */
    private static boolean isShortsSection(int sectionId) {
        return sectionId == MediaGroup.TYPE_HOME || sectionId == MediaGroup.TYPE_SUBSCRIPTIONS || sectionId == MediaGroup.TYPE_HISTORY ||
                sectionId == MediaGroup.TYPE_TRENDING || sectionId == MediaGroup.TYPE_CHANNEL_UPLOADS || sectionId == MediaGroup.TYPE_NEWS ||
                sectionId == MediaGroup.TYPE_GAMING || sectionId == MediaGroup.TYPE_MUSIC || sectionId == MediaGroup.TYPE_SPORTS ||
                sectionId == MediaGroup.TYPE_LIVE || sectionId == MediaGroup.TYPE_MY_VIDEOS;
    }
}
