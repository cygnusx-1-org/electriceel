package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsPanel;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsRows;
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
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.hide_unwanted_content),
                this::appendRows, null);
    }

    /**
     * The rows of the panel show shows (see SettingsPanel)
     */
    public void appendRows(SettingsRows presenter) {
        List<OptionItem> options = new ArrayList<>();
        options.add(createMenuItem(R.string.header_shorts, this::showShortsMenu, this::appendShortsMenuRows));
        options.add(createMenuItem(R.string.hide_content_mixes, this::showMixesMenu, this::appendMixesMenuRows));
        options.add(createMenuItem(R.string.hide_content_watched, this::showWatchedMenu, this::appendWatchedMenuRows));
        options.add(createMenuItem(R.string.hide_content_upcoming, this::showUpcomingMenu, this::appendUpcomingMenuRows));
        options.add(createMenuItem(R.string.hide_content_duplicates, this::showDuplicatesMenu, this::appendDuplicatesMenuRows));
        options.add(createMenuItem(R.string.subscription_groups, this::showChannelGroupsMenu, this::appendChannelGroupsMenuRows));
        options.add(createMenuItem(R.string.hide_old_videos, () -> OldVideosSettingsPresenter.instance(getContext()).show(),
                rows -> OldVideosSettingsPresenter.instance(getContext()).appendRows(rows)));
        options.add(createMenuItem(R.string.keyword_filtering, () -> KeywordFilterSettingsPresenter.instance(getContext()).show(),
                rows -> KeywordFilterSettingsPresenter.instance(getContext()).appendRows(rows)));

        OptionItem categories = createMenuItem(R.string.hide_content_categories, this::showCategoriesMenu, this::appendCategoriesMenuRows);
        options.add(categories);
        FilteringSettingsPresenter.appendCategoriesKeyNote(getContext(), categories, options);

        String title = getContext().getString(R.string.hide_unwanted_content);
        presenter.appendCheckedCategory(title, options);
    }

    /**
     * With the quick toggle enabled, its button on the main screen turns the other options off and on, as they're set
     */
    private void showShortsMenu() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.header_shorts),
                this::appendShortsMenuRows, null);
    }

    /**
     * The rows of the menu showShortsMenu shows (see SettingsPanel)
     */
    private void appendShortsMenuRows(SettingsRows rows) {
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
        // History's row of shorts (see ContentService.getHistoryRows): History is the grid of its videos instead
        options.add(UiOptionItem.from(getContext().getString(R.string.hide_shorts_history),
                option -> {
                    mHideContentData.setContentHidden(MediaServiceData.CONTENT_SHORTS_HISTORY_ROW, option.isSelected());
                    BrowsePresenter.instance(getContext()).updateHistoryStyle();
                },
                mMediaServiceData.isContentHiddenInSettings(MediaServiceData.CONTENT_SHORTS_HISTORY_ROW)));
        options.add(createContentItem(R.string.hide_shorts_from_sports, MediaServiceData.CONTENT_SHORTS_SPORTS));
        options.add(createContentItem(R.string.hide_shorts_from_live, MediaServiceData.CONTENT_SHORTS_LIVE));
        options.add(createContentItem(R.string.hide_shorts_from_my_videos, MediaServiceData.CONTENT_SHORTS_MY_VIDEOS));
        options.add(createContentItem(R.string.hide_shorts_from_gaming, MediaServiceData.CONTENT_SHORTS_GAMING));
        options.add(createContentItem(R.string.hide_shorts_from_news, MediaServiceData.CONTENT_SHORTS_NEWS));
        options.add(createContentItem(R.string.hide_shorts_from_music, MediaServiceData.CONTENT_SHORTS_MUSIC));

        rows.appendCheckedCategory(getContext().getString(R.string.header_shorts), options);
    }

    private void showMixesMenu() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.hide_content_mixes),
                this::appendMixesMenuRows, null);
    }

    /**
     * The rows of the menu showMixesMenu shows (see SettingsPanel)
     */
    private void appendMixesMenuRows(SettingsRows rows) {
        List<OptionItem> options = new ArrayList<>();

        options.add(createContentItem(R.string.hide_mixes, MediaServiceData.CONTENT_MIXES));

        rows.appendCheckedCategory(getContext().getString(R.string.hide_content_mixes), options);
    }

    private void showWatchedMenu() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.hide_content_watched),
                this::appendWatchedMenuRows, null);
    }

    /**
     * The rows of the menu showWatchedMenu shows (see SettingsPanel)
     */
    private void appendWatchedMenuRows(SettingsRows rows) {
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

        rows.appendCheckedCategory(getContext().getString(R.string.hide_content_watched), options);
    }

    private void showUpcomingMenu() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.hide_content_upcoming),
                this::appendUpcomingMenuRows, null);
    }

    /**
     * The rows of the menu showUpcomingMenu shows (see SettingsPanel)
     */
    private void appendUpcomingMenuRows(SettingsRows rows) {
        List<OptionItem> options = new ArrayList<>();

        options.add(createContentItem(R.string.hide_upcoming, MediaServiceData.CONTENT_UPCOMING_SUBSCRIPTIONS));
        options.add(createContentItem(R.string.hide_upcoming_home, MediaServiceData.CONTENT_UPCOMING_HOME));
        options.add(createContentItem(R.string.hide_upcoming_channel, MediaServiceData.CONTENT_UPCOMING_CHANNEL));

        rows.appendCheckedCategory(getContext().getString(R.string.hide_content_upcoming), options);
    }

    /**
     * The sections of rows from the feeds (see DuplicatesData), in the sidebar's order
     */
    private void showDuplicatesMenu() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.hide_content_duplicates),
                this::appendDuplicatesMenuRows, null);
    }

    /**
     * The rows of the menu showDuplicatesMenu shows (see SettingsPanel)
     */
    private void appendDuplicatesMenuRows(SettingsRows rows) {
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

        rows.appendCheckedCategory(getContext().getString(R.string.hide_content_duplicates), options);
    }

    /**
     * The areas the channels of the subscription groups are hidden from (see ChannelGroupFilterData), in the sidebar's order,
     * the Subscriptions row of Home after Home
     */
    private void showChannelGroupsMenu() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.subscription_groups),
                this::appendChannelGroupsMenuRows, null);
    }

    /**
     * The rows of the menu showChannelGroupsMenu shows (see SettingsPanel)
     */
    private void appendChannelGroupsMenuRows(SettingsRows rows) {
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

        rows.appendCheckedCategory(getContext().getString(R.string.subscription_groups), options);
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
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.hide_content_categories),
                this::appendCategoriesMenuRows, null);
    }

    /**
     * The rows of the menu showCategoriesMenu shows (see SettingsPanel)
     */
    private void appendCategoriesMenuRows(SettingsRows rows) {
        List<OptionItem> options = new ArrayList<>();

        options.add(createContentItem(R.string.hide_non_gaming_from_gaming, MediaServiceData.CONTENT_NON_GAMING_GAMING));

        rows.appendCheckedCategory(getContext().getString(R.string.hide_content_categories), options);
    }

    /**
     * @param content one of MediaServiceData.CONTENT_*
     */
    private OptionItem createContentItem(int titleResId, int content) {
        return UiOptionItem.from(getContext().getString(titleResId),
                option -> mHideContentData.setContentHidden(content, option.isSelected()),
                mMediaServiceData.isContentHiddenInSettings(content));
    }

    /**
     * @param panel the rows of the menu, for the settings search
     */
    private OptionItem createMenuItem(int titleResId, Runnable onSelect, SettingsPanel panel) {
        OptionItem item = UiOptionItem.from(getContext().getString(titleResId), option -> onSelect.run());
        item.setMenu(true);
        item.setPanel(panel);
        return item;
    }
}
