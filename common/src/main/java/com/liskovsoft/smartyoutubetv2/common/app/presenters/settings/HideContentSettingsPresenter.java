package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.prefs.ChannelGroupFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.DuplicatesData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.HideContentData;
import com.liskovsoft.smartyoutubetv2.common.prefs.OpenedVideosData;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

/**
 * The Hide content card of the Content Filtering settings (was a part of General)
 */
public class HideContentSettingsPresenter extends BasePresenter<Void> {
    private final GeneralData mGeneralData;
    private final MediaServiceData mMediaServiceData;
    private final HideContentData mHideContentData;

    private HideContentSettingsPresenter(Context context) {
        super(context);
        mGeneralData = GeneralData.instance(context);
        mMediaServiceData = MediaServiceData.instance();
        mHideContentData = HideContentData.instance(context);
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
        options.add(createMenuItem(R.string.hide_content_upcoming, this::showUpcomingMenu));
        options.add(createMenuItem(R.string.hide_content_duplicates, this::showDuplicatesMenu));
        options.add(createMenuItem(R.string.subscription_groups, this::showChannelGroupsMenu));
        options.add(createMenuItem(R.string.hide_old_videos, () -> OldVideosSettingsPresenter.instance(getContext()).show()));
        options.add(createMenuItem(R.string.keyword_filtering, () -> KeywordFilterSettingsPresenter.instance(getContext()).show()));

        OptionItem categories = createMenuItem(R.string.hide_content_categories, this::showCategoriesMenu);
        options.add(categories);
        FilteringSettingsPresenter.appendCategoriesKeyNote(getContext(), categories, options);

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
                        mHideContentData.setShortsQuickToggleHiding(true);
                    }

                    mHideContentData.setShortsQuickToggleEnabled(option.isSelected());
                },
                mMediaServiceData.isShortsQuickToggleEnabled());
        quickToggle.setToggle(true);
        options.add(quickToggle);

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts_everywhere),
                option -> {
                    mHideContentData.setContentHidden(MediaServiceData.CONTENT_SHORTS_ALL, option.isSelected());
                    BrowsePresenter.instance(getContext()).enableSection(MediaGroup.TYPE_SHORTS, !option.isSelected());
                },
                mMediaServiceData.isContentHiddenInSettings(MediaServiceData.CONTENT_SHORTS_ALL)));
        options.add(createContentItem(R.string.hide_shorts, MediaServiceData.CONTENT_SHORTS_SUBSCRIPTIONS));
        options.add(createContentItem(R.string.hide_shorts_from_search, MediaServiceData.CONTENT_SHORTS_SEARCH));
        options.add(createContentItem(R.string.hide_shorts_from_home, MediaServiceData.CONTENT_SHORTS_HOME));
        options.add(createContentItem(R.string.hide_shorts_channel, MediaServiceData.CONTENT_SHORTS_CHANNEL));
        options.add(createContentItem(R.string.hide_shorts_from_history, MediaServiceData.CONTENT_SHORTS_HISTORY));
        options.add(createContentItem(R.string.hide_shorts_from_sports, MediaServiceData.CONTENT_SHORTS_SPORTS));
        options.add(createContentItem(R.string.hide_shorts_from_live, MediaServiceData.CONTENT_SHORTS_LIVE));
        options.add(createContentItem(R.string.hide_shorts_from_my_videos, MediaServiceData.CONTENT_SHORTS_MY_VIDEOS));
        options.add(createContentItem(R.string.hide_shorts_from_gaming, MediaServiceData.CONTENT_SHORTS_GAMING));
        options.add(createContentItem(R.string.hide_shorts_from_news, MediaServiceData.CONTENT_SHORTS_NEWS));
        options.add(createContentItem(R.string.hide_shorts_from_music, MediaServiceData.CONTENT_SHORTS_MUSIC));

        showSubmenu(R.string.header_shorts, options);
    }

    private void showMixesMenu() {
        List<OptionItem> options = new ArrayList<>();

        options.add(createContentItem(R.string.hide_mixes, MediaServiceData.CONTENT_MIXES));

        showSubmenu(R.string.hide_content_mixes, options);
    }

    private void showWatchedMenu() {
        List<OptionItem> options = new ArrayList<>();

        options.add(createContentItem(R.string.hide_watched_from_home, MediaServiceData.CONTENT_WATCHED_HOME));
        options.add(createContentItem(R.string.hide_watched_from_subscriptions, MediaServiceData.CONTENT_WATCHED_SUBSCRIPTIONS));
        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_notifications),
                option -> mGeneralData.setHideWatchedFromNotificationsEnabled(option.isSelected()),
                mGeneralData.isHideWatchedFromNotificationsEnabled()));
        ChannelGroupFilterData channelGroups = ChannelGroupFilterData.instance(getContext());
        options.add(UiOptionItem.from(getContext().getString(R.string.hide_watched_from_subscription_groups),
                option -> channelGroups.setWatchedHidden(option.isSelected()),
                channelGroups.isWatchedHidden()));
        OpenedVideosData openedVideos = OpenedVideosData.instance(getContext());
        options.add(UiOptionItem.from(getContext().getString(R.string.hide_opened_videos),
                getContext().getString(R.string.hide_opened_videos_desc),
                option -> openedVideos.setHidden(option.isSelected()),
                openedVideos.isHidden()));

        showSubmenu(R.string.hide_content_watched, options);
    }

    private void showUpcomingMenu() {
        List<OptionItem> options = new ArrayList<>();

        options.add(createContentItem(R.string.hide_upcoming, MediaServiceData.CONTENT_UPCOMING_SUBSCRIPTIONS));
        options.add(createContentItem(R.string.hide_upcoming_home, MediaServiceData.CONTENT_UPCOMING_HOME));
        options.add(createContentItem(R.string.hide_upcoming_channel, MediaServiceData.CONTENT_UPCOMING_CHANNEL));

        showSubmenu(R.string.hide_content_upcoming, options);
    }

    /**
     * The sections of rows from the feeds (see DuplicatesData), in the sidebar's order
     */
    private void showDuplicatesMenu() {
        DuplicatesData data = DuplicatesData.instance(getContext());
        List<OptionItem> options = new ArrayList<>();

        for (Entry<Integer, Integer> section : SidebarService.instance(getContext()).getDefaultSections().entrySet()) {
            int sectionId = section.getValue();

            if (!DuplicatesData.isSupportedSection(sectionId)) {
                continue;
            }

            options.add(UiOptionItem.from(getContext().getString(section.getKey()),
                    option -> data.setSectionEnabled(sectionId, option.isSelected()),
                    data.isSectionEnabled(sectionId)));
        }

        OptionItem note = UiOptionItem.from(getContext().getString(R.string.hide_content_duplicates_desc));
        note.setNote(true);
        options.add(note);

        showSubmenu(R.string.hide_content_duplicates, options);
    }

    /**
     * The areas the channels of the subscription groups are hidden from (see ChannelGroupFilterData), in the sidebar's order,
     * the Subscriptions row of Home after Home
     */
    private void showChannelGroupsMenu() {
        ChannelGroupFilterData data = ChannelGroupFilterData.instance(getContext());
        List<OptionItem> options = new ArrayList<>();

        for (Entry<Integer, Integer> section : SidebarService.instance(getContext()).getDefaultSections().entrySet()) {
            int sectionId = section.getValue();

            if (!ChannelGroupFilterData.isSupportedArea(sectionId)) {
                continue;
            }

            options.add(createChannelGroupAreaItem(data, section.getKey(), sectionId));

            if (sectionId == MediaGroup.TYPE_HOME) {
                options.add(createChannelGroupAreaItem(data, R.string.hide_content_channel_groups_subscriptions_row,
                        ChannelGroupFilterData.AREA_HOME_SUBSCRIPTIONS_ROW));
            }
        }

        OptionItem note = UiOptionItem.from(getContext().getString(R.string.hide_content_channel_groups_desc));
        note.setNote(true);
        options.add(note);

        showSubmenu(R.string.subscription_groups, options);
    }

    private OptionItem createChannelGroupAreaItem(ChannelGroupFilterData data, int titleResId, int area) {
        return UiOptionItem.from(getContext().getString(titleResId),
                option -> data.setHidden(area, option.isSelected()),
                data.isHidden(area));
    }

    /**
     * The non-gaming videos of Gaming. The categories of Home are marked or hidden in Hide or mark content (see FilteringSettingsPresenter).
     * Only with the user's own Data API key (see VideoCategoryManager).
     */
    private void showCategoriesMenu() {
        List<OptionItem> options = new ArrayList<>();

        options.add(createContentItem(R.string.hide_non_gaming_from_gaming, MediaServiceData.CONTENT_NON_GAMING_GAMING));

        showSubmenu(R.string.hide_content_categories, options);
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
                option -> mHideContentData.setContentHidden(content, option.isSelected()),
                mMediaServiceData.isContentHiddenInSettings(content));
    }

    private OptionItem createMenuItem(int titleResId, Runnable onSelect) {
        OptionItem item = UiOptionItem.from(getContext().getString(titleResId), option -> onSelect.run());
        item.setMenu(true);
        return item;
    }
}
