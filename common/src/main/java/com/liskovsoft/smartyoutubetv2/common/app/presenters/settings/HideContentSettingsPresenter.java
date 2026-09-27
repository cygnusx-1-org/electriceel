package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hide content card of the Content Filtering settings (was a part of General)
 */
public class HideContentSettingsPresenter extends BasePresenter<Void> {
    private final GeneralData mGeneralData;
    private final MediaServiceData mMediaServiceData;

    private HideContentSettingsPresenter(Context context) {
        super(context);
        mGeneralData = GeneralData.instance(context);
        mMediaServiceData = MediaServiceData.instance();
    }

    public static HideContentSettingsPresenter instance(Context context) {
        return new HideContentSettingsPresenter(context);
    }

    /**
     * A submenu per kind of content
     */
    public void show() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());

        List<OptionItem> options = new ArrayList<>();
        options.add(createMenuItem(R.string.header_shorts, this::showShortsMenu));
        options.add(createMenuItem(R.string.hide_content_mixes, this::showMixesMenu));
        options.add(createMenuItem(R.string.hide_content_watched, this::showWatchedMenu));
        options.add(createMenuItem(R.string.hide_content_streams, this::showStreamsMenu));
        options.add(createMenuItem(R.string.hide_content_upcoming, this::showUpcomingMenu));
        options.add(createMenuItem(R.string.hide_content_home_categories, this::showHomeCategoriesMenu));

        String title = getContext().getString(R.string.hide_unwanted_content);
        presenter.appendCheckedCategory(title, options);
        presenter.showDialog(title);
    }

    /**
     * With the quick toggle enabled, its button on the main screen turns the other options off and on, as they're set
     */
    private void showShortsMenu() {
        List<OptionItem> options = new ArrayList<>();

        OptionItem quickToggle = UiOptionItem.from(getContext().getString(R.string.quick_toggle),
                option -> {
                    if (option.isSelected()) {
                        // Starts on: the options apply as before
                        mMediaServiceData.setShortsQuickToggleHiding(true);
                    }

                    mMediaServiceData.setShortsQuickToggleEnabled(option.isSelected());
                },
                mMediaServiceData.isShortsQuickToggleEnabled());
        quickToggle.setToggle(true);
        options.add(quickToggle);

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts_everywhere),
                option -> {
                    mMediaServiceData.setContentHidden(MediaServiceData.CONTENT_SHORTS_ALL, option.isSelected());
                    BrowsePresenter.instance(getContext()).enableSection(MediaGroup.TYPE_SHORTS, !option.isSelected());
                },
                mMediaServiceData.isContentHiddenInSettings(MediaServiceData.CONTENT_SHORTS_ALL)));
        options.add(createContentItem(R.string.hide_shorts, MediaServiceData.CONTENT_SHORTS_SUBSCRIPTIONS));
        options.add(createContentItem(R.string.hide_shorts_from_search, MediaServiceData.CONTENT_SHORTS_SEARCH));
        options.add(createContentItem(R.string.hide_shorts_from_home, MediaServiceData.CONTENT_SHORTS_HOME));
        options.add(createContentItem(R.string.hide_shorts_channel, MediaServiceData.CONTENT_SHORTS_CHANNEL));
        options.add(createContentItem(R.string.hide_shorts_from_history, MediaServiceData.CONTENT_SHORTS_HISTORY));
        options.add(createContentItem(R.string.hide_shorts_from_trending, MediaServiceData.CONTENT_SHORTS_TRENDING));

        showSubmenu(R.string.header_shorts, options);
    }

    private void showMixesMenu() {
        List<OptionItem> options = new ArrayList<>();

        options.add(createContentItem(R.string.hide_mixes, MediaServiceData.CONTENT_MIXES));

        showSubmenu(R.string.hide_content_mixes, options);
    }

    private void showWatchedMenu() {
        List<OptionItem> options = new ArrayList<>();

        options.add(createContentItem(R.string.hide_watched_from_watch_later, MediaServiceData.CONTENT_WATCHED_WATCH_LATER));
        options.add(createContentItem(R.string.hide_watched_from_home, MediaServiceData.CONTENT_WATCHED_HOME));
        options.add(createContentItem(R.string.hide_watched_from_subscriptions, MediaServiceData.CONTENT_WATCHED_SUBSCRIPTIONS));
        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_notifications),
                option -> mGeneralData.setHideWatchedFromNotificationsEnabled(option.isSelected()),
                mGeneralData.isHideWatchedFromNotificationsEnabled()));

        showSubmenu(R.string.hide_content_watched, options);
    }

    private void showStreamsMenu() {
        List<OptionItem> options = new ArrayList<>();

        options.add(createContentItem(R.string.hide_streams, MediaServiceData.CONTENT_STREAMS_SUBSCRIPTIONS));

        showSubmenu(R.string.hide_content_streams, options);
    }

    private void showUpcomingMenu() {
        List<OptionItem> options = new ArrayList<>();

        options.add(createContentItem(R.string.hide_upcoming, MediaServiceData.CONTENT_UPCOMING_SUBSCRIPTIONS));
        options.add(createContentItem(R.string.hide_upcoming_home, MediaServiceData.CONTENT_UPCOMING_HOME));
        options.add(createContentItem(R.string.hide_upcoming_channel, MediaServiceData.CONTENT_UPCOMING_CHANNEL));

        showSubmenu(R.string.hide_content_upcoming, options);
    }

    /**
     * The music, gaming, sports, news and tech videos of Home
     */
    private void showHomeCategoriesMenu() {
        List<OptionItem> options = new ArrayList<>();

        options.add(createContentItem(R.string.hide_music_from_home, MediaServiceData.CONTENT_MUSIC_HOME));
        options.add(createContentItem(R.string.hide_gaming_from_home, MediaServiceData.CONTENT_GAMING_HOME));
        options.add(createContentItem(R.string.hide_sports_from_home, MediaServiceData.CONTENT_SPORTS_HOME));
        options.add(createContentItem(R.string.hide_news_from_home, MediaServiceData.CONTENT_NEWS_HOME));
        options.add(createContentItem(R.string.hide_tech_from_home, MediaServiceData.CONTENT_TECH_HOME));

        showSubmenu(R.string.hide_content_home_categories, options);
    }

    private void showSubmenu(int titleResId, List<OptionItem> options) {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());

        String title = getContext().getString(titleResId);
        presenter.appendCheckedCategory(title, options);
        presenter.showDialog(title);
    }

    /**
     * @param content one of MediaServiceData.CONTENT_*
     */
    private OptionItem createContentItem(int titleResId, int content) {
        return UiOptionItem.from(getContext().getString(titleResId),
                option -> mMediaServiceData.setContentHidden(content, option.isSelected()),
                mMediaServiceData.isContentHiddenInSettings(content));
    }

    private OptionItem createMenuItem(int titleResId, Runnable onSelect) {
        OptionItem item = UiOptionItem.from(getContext().getString(titleResId), option -> onSelect.run());
        item.setMenu(true);
        return item;
    }
}
