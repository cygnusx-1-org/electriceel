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
import com.liskovsoft.smartyoutubetv2.common.misc.AiSListManager;
import com.liskovsoft.smartyoutubetv2.common.misc.OldVideoFilter;
import com.liskovsoft.smartyoutubetv2.common.prefs.AiSListFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.CollaborationsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.HideContentData;
import com.liskovsoft.smartyoutubetv2.common.prefs.OldVideosData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SectionFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.StreamedData;
import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLaterData;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.util.ArrayList;
import java.util.List;

/**
 * The quick toggle button on the main screen, next to the account button. It toggles the filters that have their quick toggle enabled:
 * Hide videos older than (Content Filtering | Hide content), the Shorts options (Content Filtering | Hide content | Shorts),
 * and the hiding of Watch later, AiSList, Streamed or Live and Collaborations (Content Filtering | Hide or mark content, AiSList).
 * With one enabled, the button toggles it. With more, it opens a panel with a switch for each.
 */
public class QuickTogglePresenter extends BasePresenter<Void> {
    public static final int TOGGLE_OLD_VIDEOS = 0;
    public static final int TOGGLE_SHORTS = 1;
    public static final int TOGGLE_WATCH_LATER = 2;
    public static final int TOGGLE_AI = 3;
    public static final int TOGGLE_STREAMED = 4;
    public static final int TOGGLE_COLLABORATIONS = 5;
    // In the order of the panel
    private static final int[] TOGGLES = {TOGGLE_OLD_VIDEOS, TOGGLE_SHORTS, TOGGLE_WATCH_LATER, TOGGLE_AI, TOGGLE_STREAMED, TOGGLE_COLLABORATIONS};

    private QuickTogglePresenter(Context context) {
        super(context);
    }

    public static QuickTogglePresenter instance(Context context) {
        return new QuickTogglePresenter(context);
    }

    /**
     * The quick toggle of the filter is enabled. The hiding of Hide or mark content and AiSList also has to be set to hide:
     * the toggle would change nothing otherwise.
     */
    public boolean isToggleEnabled(int toggle) {
        switch (toggle) {
            case TOGGLE_OLD_VIDEOS:
                return OldVideosData.instance(getContext()).isQuickToggleEnabled();
            case TOGGLE_SHORTS:
                return MediaServiceData.instance().isShortsQuickToggleEnabled();
            case TOGGLE_AI:
                AiSListFilterData aiData = AiSListFilterData.instance(getContext());
                return aiData.isQuickToggleEnabled() && aiData.isSetToHide();
            default:
                SectionFilterData data = getFilterData(toggle);
                return data.isQuickToggleEnabled() && data.getMode() == SectionFilterData.MODE_HIDE;
        }
    }

    /**
     * The button is shown
     */
    public boolean isEnabled() {
        return !getEnabledToggles().isEmpty();
    }

    /**
     * The toggle the button turns off and on, or -1 when it opens the panel (or isn't shown)
     */
    public int getSingleToggle() {
        List<Integer> toggles = getEnabledToggles();

        return toggles.size() == 1 ? toggles.get(0) : -1;
    }

    /**
     * The toggle is on: its filter hides the videos, as it's set
     */
    public boolean isHiding(int toggle) {
        switch (toggle) {
            case TOGGLE_OLD_VIDEOS:
                return OldVideosData.instance(getContext()).isEnabled();
            case TOGGLE_SHORTS:
                return MediaServiceData.instance().isShortsQuickToggleHiding();
            case TOGGLE_AI:
                return AiSListFilterData.instance(getContext()).isQuickToggleHiding();
            default:
                return getFilterData(toggle).isQuickToggleHiding();
        }
    }

    private void setHiding(int toggle, boolean hide) {
        switch (toggle) {
            case TOGGLE_OLD_VIDEOS:
                OldVideosData.instance(getContext()).setEnabled(hide);
                break;
            case TOGGLE_SHORTS:
                HideContentData.instance(getContext()).setShortsQuickToggleHiding(hide);
                break;
            case TOGGLE_AI:
                AiSListFilterData.instance(getContext()).setQuickToggleHiding(hide);
                break;
            default:
                getFilterData(toggle).setQuickToggleHiding(hide);
                break;
        }
    }

    /**
     * The button toggles its filter, or opens the panel with a switch for each
     */
    public void onClick() {
        List<Integer> toggles = getEnabledToggles();

        if (toggles.size() > 1) {
            showToggles(toggles);
        } else if (toggles.size() == 1) {
            int toggle = toggles.get(0);
            setHiding(toggle, !isHiding(toggle));
            MessageHelpers.showMessage(getContext(), getState(toggle));
            refreshSection(toggles);
        }
    }

    /**
     * What the toggle does now, e.g. "Hiding Shorts"
     */
    public String getState(int toggle) {
        boolean isHiding = isHiding(toggle);

        switch (toggle) {
            case TOGGLE_OLD_VIDEOS:
                OldVideosData data = OldVideosData.instance(getContext());

                return isHiding ?
                        getContext().getString(R.string.hide_old_videos_on, OldVideoFilter.getPeriodTitle(getContext(), data.getPeriodMonths())) :
                        getContext().getString(R.string.hide_old_videos_off);
            case TOGGLE_SHORTS:
                return getContext().getString(isHiding ? R.string.hide_shorts_on : R.string.hide_shorts_off);
            case TOGGLE_WATCH_LATER:
                return getContext().getString(isHiding ? R.string.quick_toggle_watch_later_on : R.string.quick_toggle_watch_later_off);
            case TOGGLE_AI:
                return getContext().getString(isHiding ? R.string.quick_toggle_ai_on : R.string.quick_toggle_ai_off);
            case TOGGLE_STREAMED:
                return getContext().getString(isHiding ? R.string.quick_toggle_streamed_on : R.string.quick_toggle_streamed_off);
            default:
                return getContext().getString(isHiding ? R.string.quick_toggle_collaborations_on : R.string.quick_toggle_collaborations_off);
        }
    }

    private String getSwitchTitle(int toggle) {
        switch (toggle) {
            case TOGGLE_OLD_VIDEOS:
                return getContext().getString(R.string.hide_old_videos_period,
                        OldVideoFilter.getPeriodTitle(getContext(), OldVideosData.instance(getContext()).getPeriodMonths()));
            case TOGGLE_SHORTS:
                return getContext().getString(R.string.enable_hide_shorts_options);
            case TOGGLE_WATCH_LATER:
                return getContext().getString(R.string.quick_toggle_watch_later);
            case TOGGLE_AI:
                return getContext().getString(R.string.quick_toggle_ai);
            case TOGGLE_STREAMED:
                return getContext().getString(R.string.quick_toggle_streamed);
            default:
                return getContext().getString(R.string.quick_toggle_collaborations);
        }
    }

    private List<Integer> getEnabledToggles() {
        List<Integer> toggles = new ArrayList<>();

        for (int toggle : TOGGLES) {
            if (isToggleEnabled(toggle)) {
                toggles.add(toggle);
            }
        }

        return toggles;
    }

    /**
     * A switch per filter. The section is reloaded when the panel closes, if a switch of it has changed.
     */
    private void showToggles(List<Integer> toggles) {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        boolean[] isHidingBefore = new boolean[toggles.size()];

        for (int i = 0; i < toggles.size(); i++) {
            int toggle = toggles.get(i);
            isHidingBefore[i] = isHiding(toggle);

            presenter.appendSingleSwitch(UiOptionItem.from(getSwitchTitle(toggle),
                    option -> setHiding(toggle, option.isSelected()),
                    isHidingBefore[i]));
        }

        presenter.showDialog(getContext().getString(R.string.quick_toggles), () -> {
            List<Integer> changed = new ArrayList<>();

            for (int i = 0; i < toggles.size(); i++) {
                if (isHiding(toggles.get(i)) != isHidingBefore[i]) {
                    changed.add(toggles.get(i));
                }
            }

            refreshSection(changed);
        });
    }

    /**
     * Reloads the section if a filter that has changed applies to it. Keeps the focus on the button, so it can be pressed again.
     */
    private void refreshSection(List<Integer> changed) {
        BrowsePresenter presenter = BrowsePresenter.instance(getContext());
        BrowseSection section = presenter.getCurrentSection();

        if (section == null) {
            return;
        }

        for (int toggle : changed) {
            if (appliesTo(toggle, section.getId())) {
                presenter.refresh(false);
                return;
            }
        }
    }

    /**
     * The filter of the toggle changes what the section shows
     */
    private boolean appliesTo(int toggle, int sectionId) {
        switch (toggle) {
            case TOGGLE_OLD_VIDEOS:
                return OldVideosData.instance(getContext()).isSectionEnabled(sectionId);
            case TOGGLE_SHORTS:
                return isShortsSection(sectionId);
            case TOGGLE_AI:
                return AiSListManager.instance(getContext()).isSetToHide(AiSListManager.getSection(sectionId, true));
            default:
                return getFilterData(toggle).isSetToHide(sectionId);
        }
    }

    /**
     * The filter of a toggle of Hide or mark content
     */
    private SectionFilterData getFilterData(int toggle) {
        switch (toggle) {
            case TOGGLE_WATCH_LATER:
                return WatchLaterData.instance(getContext());
            case TOGGLE_STREAMED:
                return StreamedData.instance(getContext());
            default:
                return CollaborationsData.instance(getContext());
        }
    }

    /**
     * The sections the Shorts are hidden from (see MediaGroupOptions of MediaServiceCore)
     */
    private static boolean isShortsSection(int sectionId) {
        return sectionId == MediaGroup.TYPE_HOME || sectionId == MediaGroup.TYPE_SUBSCRIPTIONS || sectionId == MediaGroup.TYPE_HISTORY ||
                sectionId == MediaGroup.TYPE_CHANNEL_UPLOADS || sectionId == MediaGroup.TYPE_NEWS ||
                sectionId == MediaGroup.TYPE_GAMING || sectionId == MediaGroup.TYPE_MUSIC || sectionId == MediaGroup.TYPE_SPORTS ||
                sectionId == MediaGroup.TYPE_LIVE || sectionId == MediaGroup.TYPE_MY_VIDEOS;
    }
}
