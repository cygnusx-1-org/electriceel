package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Pair;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsRows;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.TunnelingFallback;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;
import com.liskovsoft.smartyoutubetv2.common.misc.VideoCategoryManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.EndScreenData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.InfoCardData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MusicAutoplayData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.util.ArrayList;
import java.util.List;

public class PlayerSettingsPresenter extends BasePresenter<Void> {
    private final PlayerData mPlayerData;
    private final PlayerTweaksData mPlayerTweaksData;
    private final SearchData mSearchData;
    private final GeneralData mGeneralData;
    private final SidebarService mSidebarService;
    private final MediaServiceData mMediaServiceData;
    private boolean mRestartApp;
    private final Runnable mOnFinish = () -> {
        if (mRestartApp) {
            mRestartApp = false;
            MessageHelpers.showLongMessage(getContext(), R.string.msg_restart_app);
        }
    };

    private PlayerSettingsPresenter(Context context) {
        super(context);
        mPlayerData = PlayerData.instance(context);
        mPlayerTweaksData = PlayerTweaksData.instance(context);
        mSearchData = SearchData.instance(context);
        mGeneralData = GeneralData.instance(context);
        mSidebarService = SidebarService.instance(context);
        mMediaServiceData = MediaServiceData.instance();
    }

    public static PlayerSettingsPresenter instance(Context context) {
        return new PlayerSettingsPresenter(context);
    }

    /**
     * The Playback mode card of the Player settings, in General (was in the General panel)
     */
    public void showPlaybackMode() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.action_repeat_mode),
                this::appendPlaybackModeRows, mOnFinish);
    }

    /**
     * The rows of the panel showPlaybackMode shows (see SettingsPanel)
     */
    public void appendPlaybackModeRows(SettingsRows rows) {
        appendPlaybackModeCategory(rows);
    }

    /**
     * The Music autoplay card of the Player settings, in General. Plays on from a music video where the playback mode stops.
     */
    public void showMusicAutoplay() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.music_autoplay),
                this::appendMusicAutoplayRows, mOnFinish);
    }

    /**
     * The rows of the panel showMusicAutoplay shows (see SettingsPanel)
     */
    public void appendMusicAutoplayRows(SettingsRows rows) {
        appendMusicAutoplayCategory(rows);
    }

    /**
     * The Network engine card of the Player settings, in General (was in the General panel)
     */
    public void showNetworkEngine() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.player_network_stack),
                this::appendNetworkEngineRows, mOnFinish);
    }

    /**
     * The rows of the panel showNetworkEngine shows (see SettingsPanel)
     */
    public void appendNetworkEngineRows(SettingsRows rows) {
        appendNetworkEngineCategory(rows);
    }

    /**
     * The YouTube clients' order card of the Player settings, in General: the clients are tried in turn to get the formats of a
     * video, until one plays
     */
    public void showClientOrder() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.client_order),
                this::appendClientOrderRows, mOnFinish);
    }

    /**
     * The rows of the panel showClientOrder shows (see SettingsPanel)
     */
    public void appendClientOrderRows(SettingsRows rows) {
        appendClientOrderCategory(rows);
    }

    /**
     * The Auto-hide UI card of the Player settings, in General (was in the General panel)
     */
    public void showUIAutoHide() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.player_ui_hide_behavior),
                this::appendUIAutoHideRows, mOnFinish);
    }

    /**
     * The rows of the panel showUIAutoHide shows (see SettingsPanel)
     */
    public void appendUIAutoHideRows(SettingsRows rows) {
        appendUIAutoHideCategory(rows);
    }

    /**
     * The Pixel ratio card of the Player settings, in General (was in the General panel)
     */
    public void showPixelRatio() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.player_pixel_ratio),
                this::appendPixelRatioRows, mOnFinish);
    }

    /**
     * The rows of the panel showPixelRatio shows (see SettingsPanel)
     */
    public void appendPixelRatioRows(SettingsRows rows) {
        appendPixelRatioCategory(rows);
    }

    /**
     * The Sleep timer card of the Player settings, in General (was in the General panel)
     */
    public void showSleepTimer() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.player_sleep_timer),
                this::appendSleepTimerRows, mOnFinish);
    }

    /**
     * The rows of the panel showSleepTimer shows (see SettingsPanel)
     */
    public void appendSleepTimerRows(SettingsRows rows) {
        appendSleepTimerCategory(rows);
    }

    /**
     * The Video card of the Player settings (was in General)
     */
    public void showVideo() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_video),
                this::appendVideoRows, mOnFinish);
    }

    /**
     * The rows of the panel showVideo shows (see SettingsPanel)
     */
    public void appendVideoRows(SettingsRows settingsPresenter) {
        appendVideoPresetsCategory(settingsPresenter);
        appendVideoBufferCategory(settingsPresenter);
        appendVideoZoomCategory(settingsPresenter);
        appendVideoSpeedCategory(settingsPresenter);
    }

    /**
     * The Audio card of the Player settings (was in General and its Misc)
     */
    public void showAudio() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_audio),
                this::appendAudioRows, mOnFinish);
    }

    /**
     * The rows of the panel showAudio shows (see SettingsPanel)
     */
    public void appendAudioRows(SettingsRows settingsPresenter) {
        appendAudioFormatsCategory(settingsPresenter);
        appendAudioLanguageCategory(settingsPresenter);
        appendAudioDelayCategory(settingsPresenter);
        appendMasterVolumeCategory(settingsPresenter);
        appendAudioOptionsCategory(settingsPresenter);
    }

    /**
     * The Show options card of the Player settings: what the controls show (was in General and its Misc)
     */
    public void showShows() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_show_options),
                this::appendShowsRows, mOnFinish);
    }

    /**
     * The rows of the panel showShows shows (see SettingsPanel)
     */
    public void appendShowsRows(SettingsRows settingsPresenter) {
        appendEndingTimeCategory(settingsPresenter);

        List<OptionItem> options = new ArrayList<>();

        // No clock and quality info options: the controls show neither (see PlaybackTransportRowPresenter)

        options.add(UiOptionItem.from(getContext().getString(R.string.player_ui_on_next),
                option -> mPlayerTweaksData.setPlayerUiOnNextEnabled(option.isSelected()),
                mPlayerTweaksData.isPlayerUiOnNextEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_likes_count),
                option -> mPlayerTweaksData.setLikesCounterEnabled(option.isSelected()),
                mPlayerTweaksData.isLikesCounterEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_tooltips),
                option -> mPlayerData.setTooltipsEnabled(option.isSelected()),
                mPlayerData.isTooltipsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.real_channel_icon),
                option -> mPlayerTweaksData.setRealChannelIconEnabled(option.isSelected()),
                mPlayerTweaksData.isRealChannelIconEnabled()));

        EndScreenData endScreenData = EndScreenData.instance(getContext());
        options.add(UiOptionItem.from(getContext().getString(R.string.player_end_screen),
                getContext().getString(R.string.player_end_screen_desc),
                option -> endScreenData.setShowEnabled(option.isSelected()),
                endScreenData.isShowEnabled()));

        InfoCardData infoCardData = InfoCardData.instance(getContext());
        options.add(UiOptionItem.from(getContext().getString(R.string.player_info_cards),
                getContext().getString(R.string.player_info_cards_desc),
                option -> infoCardData.setShowEnabled(option.isSelected()),
                infoCardData.isShowEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.settings_show_options), options);
    }

    /**
     * The Place to the left card of the Player settings, in General: chat and comments on the left (was in Misc)
     */
    public void showPlaces() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_places),
                this::appendPlacesRows, mOnFinish);
    }

    /**
     * The rows of the panel showPlaces shows (see SettingsPanel)
     */
    public void appendPlacesRows(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.place_chat_left),
                option -> mPlayerTweaksData.setChatPlacedLeft(option.isSelected()),
                mPlayerTweaksData.isChatPlacedLeft()));

        options.add(UiOptionItem.from(getContext().getString(R.string.place_comments_left),
                option -> mPlayerTweaksData.setCommentsPlacedLeft(option.isSelected()),
                mPlayerTweaksData.isCommentsPlacedLeft()));

        String title = getContext().getString(R.string.settings_places);
        settingsPresenter.appendCheckedCategory(title, options);
    }

    /**
     * The Disable options card of the Player settings (was in General: Misc and Developer options)
     */
    public void showDisables() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_disable_options),
                this::appendDisablesRows, mOnFinish);
    }

    /**
     * The rows of the panel showDisables shows (see SettingsPanel)
     */
    public void appendDisablesRows(SettingsRows rows) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.player_disable_suggestions),
                option -> mPlayerTweaksData.setSuggestionsDisabled(option.isSelected()),
                mPlayerTweaksData.isSuggestionsDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.playback_notifications_fix),
                getContext().getString(R.string.playback_notifications_fix_desc),
                option -> mPlayerTweaksData.setPlaybackNotificationsDisabled(option.isSelected()),
                mPlayerTweaksData.isPlaybackNotificationsDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.disable_network_error_fixing),
                getContext().getString(R.string.disable_network_error_fixing_desc),
                option -> mPlayerTweaksData.setNetworkErrorFixingDisabled(option.isSelected()),
                mPlayerTweaksData.isNetworkErrorFixingDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.disable_stream_buffer),
                getContext().getString(R.string.disable_stream_buffer_desc),
                option -> mPlayerTweaksData.setBufferOnStreamsDisabled(option.isSelected()),
                mPlayerTweaksData.isBufferOnStreamsDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.disable_vsync),
                getContext().getString(R.string.disable_vsync_desc),
                option -> mPlayerTweaksData.setSnappingToVsyncDisabled(option.isSelected()),
                mPlayerTweaksData.isSnappingToVsyncDisabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.disable_channels_service),
                option -> {
                    GlobalPreferences.instance(getContext()).setChannelsServiceEnabled(!option.isSelected());
                    // Cancels the channels update work when disabled, starts it now when enabled
                    Utils.updateChannels(getContext());
                },
                !GlobalPreferences.instance(getContext()).isChannelsServiceEnabled()));

        rows.appendCheckedCategory(getContext().getString(R.string.settings_disable_options), options);
    }

    /**
     * The Unlock options card of the Player settings (was in General: Misc and Developer options)
     */
    public void showUnlocks() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_unlock_options),
                this::appendUnlocksRows, mOnFinish);
    }

    /**
     * The rows of the panel showUnlocks shows (see SettingsPanel)
     */
    public void appendUnlocksRows(SettingsRows rows) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.unlock_high_bitrate_formats) + " " + TrackSelectorUtil.HIGH_BITRATE_MARK,
                option -> mPlayerTweaksData.setHighBitrateFormatsEnabled(option.isSelected()),
                mPlayerTweaksData.isHighBitrateFormatsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.unlock_all_formats),
                getContext().getString(R.string.unlock_all_formats_desc),
                option -> mPlayerTweaksData.setAllFormatsUnlocked(option.isSelected()),
                mPlayerTweaksData.isAllFormatsUnlocked()));

        rows.appendCheckedCategory(getContext().getString(R.string.settings_unlock_options), options);
    }

    /**
     * The Frame options card of the Player settings (was in General: Misc and Developer options)
     */
    public void showFrameDrops() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_frame_options),
                this::appendFrameDropsRows, mOnFinish);
    }

    /**
     * The rows of the panel showFrameDrops shows (see SettingsPanel)
     */
    public void appendFrameDropsRows(SettingsRows rows) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.amlogic_fix),
                getContext().getString(R.string.amlogic_fix_desc),
                option -> mPlayerTweaksData.setAmlogicFixEnabled(option.isSelected()),
                mPlayerTweaksData.isAmlogicFixEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.sony_frame_drop_fix),
                getContext().getString(R.string.sony_frame_drop_fix_desc),
                option -> mPlayerTweaksData.setSonyFrameDropFixEnabled(option.isSelected()),
                mPlayerTweaksData.isSonyFrameDropFixEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.amazon_frame_drop_fix),
                getContext().getString(R.string.amazon_frame_drop_fix_desc),
                option -> mPlayerTweaksData.setAmazonFrameDropFixEnabled(option.isSelected()),
                mPlayerTweaksData.isAmazonFrameDropFixEnabled()));

        rows.appendCheckedCategory(getContext().getString(R.string.settings_frame_options), options);
    }

    /**
     * The Fix options card of the Player settings (was in General: Misc and Developer options)
     */
    public void showFixes() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_fix_options),
                this::appendFixesRows, mOnFinish);
    }

    /**
     * The rows of the panel showFixes shows (see SettingsPanel)
     */
    public void appendFixesRows(SettingsRows rows) {
        List<OptionItem> options = new ArrayList<>();

        // Oculus Quest fix: back button not closing the activity
        options.add(UiOptionItem.from(getContext().getString(R.string.oculus_quest_fix),
                option -> {
                    mPlayerTweaksData.setOculusQuestFixEnabled(option.isSelected());
                    mRestartApp = true;
                },
                mPlayerTweaksData.isOculusQuestFixEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.ambilight_ratio_fix),
                getContext().getString(R.string.ambilight_ratio_fix_desc),
                option -> {
                    mPlayerTweaksData.setTextureViewEnabled(option.isSelected());
                    if (option.isSelected()) {
                        // Tunneled playback works only with SurfaceView
                        mPlayerTweaksData.setTunneledPlaybackEnabled(false);
                    }
                },
                mPlayerTweaksData.isTextureViewEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.live_stream_fix),
                getContext().getString(R.string.live_stream_fix_desc),
                option -> {
                    mPlayerTweaksData.setHlsStreamsForced(option.isSelected());
                },
                mPlayerTweaksData.isHlsStreamsForced()));

        options.add(UiOptionItem.from(getContext().getString(R.string.live_stream_fix_4k),
                getContext().getString(R.string.live_stream_fix_4k_desc),
                option -> {
                    mPlayerTweaksData.setDashUrlStreamsForced(option.isSelected());
                },
                mPlayerTweaksData.isDashUrlStreamsForced()));

        options.add(UiOptionItem.from(getContext().getString(R.string.fix_empty_subs_and_channels),
                option -> mMediaServiceData.setLegacyUIEnabled(option.isSelected()),
                mMediaServiceData.isLegacyUIEnabled()));

        rows.appendCheckedCategory(getContext().getString(R.string.settings_fix_options), options);
    }

    /**
     * The Button options card of the Player settings, in General: the player buttons and what OK does (was in General)
     */
    public void showButtons() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_button_options),
                this::appendButtonsRows, mOnFinish);
    }

    /**
     * The rows of the panel showButtons shows (see SettingsPanel)
     */
    public void appendButtonsRows(SettingsRows settingsPresenter) {
        appendPlayerButtonsCategory(settingsPresenter);
        appendOKButtonCategory(settingsPresenter);
    }

    /**
     * The Misc options card of the Player settings, in General (was Misc in General)
     */
    public void showMisc() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_misc_options),
                this::appendMiscRows, mOnFinish);
    }

    /**
     * The rows of the panel showMisc shows (see SettingsPanel)
     */
    public void appendMiscRows(SettingsRows settingsPresenter) {
        appendMiscCategory(settingsPresenter);
    }

    /**
     * The Developer options card of the Player settings, in General (was Developer options in General)
     */
    public void showDeveloper() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.player_tweaks),
                this::appendDeveloperRows, mOnFinish);
    }

    /**
     * The rows of the panel showDeveloper shows (see SettingsPanel)
     */
    public void appendDeveloperRows(SettingsRows settingsPresenter) {
        appendDeveloperCategory(settingsPresenter);
    }

    /**
     * The Seek options card of the Player settings (was in General and its Misc)
     */
    public void showSeeks() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_seek_options),
                this::appendSeeksRows, mOnFinish);
    }

    /**
     * The rows of the panel showSeeks shows (see SettingsPanel)
     */
    public void appendSeeksRows(SettingsRows settingsPresenter) {
        appendSeekTypeCategory(settingsPresenter);
        appendSeekingPreviewCategory(settingsPresenter);
        AppDialogUtil.appendSeekIntervalDialogItems(getContext(), settingsPresenter, mPlayerData, false);

        // A switch right in the list
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.player_number_key_seek),
                option -> mPlayerData.setNumberKeySeekEnabled(option.isSelected()),
                mPlayerData.isNumberKeySeekEnabled()));
    }

    private void appendAudioOptionsCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.player_audio_focus),
                option -> mPlayerTweaksData.setAudioFocusEnabled(option.isSelected()),
                mPlayerTweaksData.isAudioFocusEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_auto_volume),
                option -> mPlayerTweaksData.setPlayerAutoVolumeEnabled(option.isSelected()),
                mPlayerTweaksData.isPlayerAutoVolumeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_time_stretching),
                getContext().getString(R.string.player_time_stretching_desc),
                option -> mPlayerTweaksData.setAudioTimeStretchingEnabled(option.isSelected()),
                mPlayerTweaksData.isAudioTimeStretchingEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.audio_sync_fix),
                getContext().getString(R.string.audio_sync_fix_desc),
                option -> mPlayerTweaksData.setAudioSyncFixEnabled(option.isSelected()),
                mPlayerTweaksData.isAudioSyncFixEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.unlock_high_bitrate_audio_formats),
                option -> mPlayerTweaksData.setUnsafeAudioFormatsEnabled(option.isSelected()),
                mPlayerTweaksData.isUnsafeAudioFormatsEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.settings_audio), options);
    }

    private void appendOKButtonCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.player_only_ui),
                option -> mPlayerData.setOKButtonBehavior(PlayerData.OK_ONLY_UI),
                mPlayerData.getOKButtonBehavior() == PlayerData.OK_ONLY_UI));

        options.add(UiOptionItem.from(
                getContext().getString(R.string.player_ui_and_pause),
                option -> mPlayerData.setOKButtonBehavior(PlayerData.OK_UI_AND_PAUSE),
                mPlayerData.getOKButtonBehavior() == PlayerData.OK_UI_AND_PAUSE));

        options.add(UiOptionItem.from(
                getContext().getString(R.string.player_only_pause),
                option -> mPlayerData.setOKButtonBehavior(PlayerData.OK_ONLY_PAUSE),
                mPlayerData.getOKButtonBehavior() == PlayerData.OK_ONLY_PAUSE));

        options.add(UiOptionItem.from(
                getContext().getString(R.string.player_toggle_speed),
                option -> mPlayerData.setOKButtonBehavior(PlayerData.OK_TOGGLE_SPEED),
                mPlayerData.getOKButtonBehavior() == PlayerData.OK_TOGGLE_SPEED));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_ok_button_behavior), options);
    }

    @SuppressLint("StringFormatMatches")
    private void appendUIAutoHideCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.option_never),
                option -> mPlayerData.setUiHideTimeoutSec(PlayerData.AUTO_HIDE_NEVER),
                mPlayerData.getUiHideTimeoutSec() == PlayerData.AUTO_HIDE_NEVER));

        for (int i = 1; i <= 15; i++) {
            int timeoutSec = i;
            options.add(UiOptionItem.from(
                    getContext().getString(R.string.ui_hide_timeout_sec, i),
                    option -> mPlayerData.setUiHideTimeoutSec(timeoutSec),
                    mPlayerData.getUiHideTimeoutSec() == i));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_ui_hide_behavior), options);
    }

    private void appendVideoBufferCategory(SettingsRows settingsPresenter) {
        OptionCategory category = AppDialogUtil.createVideoBufferCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    private void appendVideoPresetsCategory(SettingsRows settingsPresenter) {
        OptionCategory category = AppDialogUtil.createVideoPresetsCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    private void appendVideoZoomCategory(SettingsRows settingsPresenter) {
        OptionCategory category = AppDialogUtil.createVideoZoomCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    private void appendAudioFormatsCategory(SettingsRows settingsPresenter) {
        OptionCategory category = AppDialogUtil.createAudioFormatsCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    private void appendAudioLanguageCategory(SettingsRows settingsPresenter) {
        OptionCategory category = AppDialogUtil.createAudioLanguageCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    private void appendAudioDelayCategory(SettingsRows settingsPresenter) {
        OptionCategory category = AppDialogUtil.createAudioDelayCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    private void appendMasterVolumeCategory(SettingsRows settingsPresenter) {
        OptionCategory category = AppDialogUtil.createAudioVolumeCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    private void appendSeekingPreviewCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.player_seek_preview_none, PlayerData.SEEK_PREVIEW_NONE},
                {R.string.player_seek_preview_single, PlayerData.SEEK_PREVIEW_SINGLE},
                {R.string.player_seek_preview_carousel_slow, PlayerData.SEEK_PREVIEW_CAROUSEL_SLOW},
                {R.string.player_seek_preview_carousel_fast, PlayerData.SEEK_PREVIEW_CAROUSEL_FAST}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mPlayerData.setSeekPreviewMode(pair[1]),
                    mPlayerData.getSeekPreviewMode() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_seek_preview), options);
    }

    private void appendRememberSpeedCategory(SettingsRows settingsPresenter) {
        OptionCategory category = AppDialogUtil.createRememberSpeedCategory(getContext());

        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendVideoSpeedCategory(SettingsRows settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.video_speed), optionItem -> {
            AppDialogPresenter settingsPresenter2 = AppDialogPresenter.instance(getContext());
            settingsPresenter2.appendCategory(AppDialogUtil.createSpeedListCategory(getContext(), null));
            settingsPresenter2.appendCategory(AppDialogUtil.createRememberSpeedCategory(getContext()));
            settingsPresenter2.appendCategory(AppDialogUtil.createSpeedMiscCategory(getContext()));
            settingsPresenter2.showDialog(getContext().getString(R.string.video_speed));
        }));
    }

    private void appendScreenOffTimeoutCategory(SettingsRows settingsPresenter) {
        OptionCategory category = AppDialogUtil.createPlayerScreenOffTimeoutCategory(getContext(), null);

        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendPlayerButtonsCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (long[] pair : new long[][] {
                {R.string.auto_frame_rate, PlayerTweaksData.PLAYER_BUTTON_AFR},
                {R.string.action_sound_off, PlayerTweaksData.PLAYER_BUTTON_SOUND_OFF},
                {R.string.video_rotate, PlayerTweaksData.PLAYER_BUTTON_VIDEO_ROTATE},
                {R.string.video_flip, PlayerTweaksData.PLAYER_BUTTON_VIDEO_FLIP},
                {R.string.open_chat, PlayerTweaksData.PLAYER_BUTTON_CHAT},
                {R.string.content_block_provider, PlayerTweaksData.PLAYER_BUTTON_CONTENT_BLOCK},
                {R.string.seek_interval, PlayerTweaksData.PLAYER_BUTTON_SEEK_INTERVAL},
                {R.string.action_next_frame, PlayerTweaksData.PLAYER_BUTTON_NEXT_FRAME},
                {R.string.share_link, PlayerTweaksData.PLAYER_BUTTON_SHARE},
                {R.string.action_video_info, PlayerTweaksData.PLAYER_BUTTON_VIDEO_INFO},
                {R.string.action_timestamps, PlayerTweaksData.PLAYER_BUTTON_TIMESTAMPS},
                {R.string.action_video_links, PlayerTweaksData.PLAYER_BUTTON_VIDEO_LINKS},
                {R.string.action_debug_info, PlayerTweaksData.PLAYER_BUTTON_VIDEO_STATS},
                {R.string.action_playback_queue, PlayerTweaksData.PLAYER_BUTTON_PLAYBACK_QUEUE},
                {R.string.screen_dimming, PlayerTweaksData.PLAYER_BUTTON_SCREEN_DIMMING},
                {R.string.action_video_zoom, PlayerTweaksData.PLAYER_BUTTON_VIDEO_ZOOM},
                {R.string.action_channel, PlayerTweaksData.PLAYER_BUTTON_OPEN_CHANNEL},
                {R.string.action_search, PlayerTweaksData.PLAYER_BUTTON_SEARCH},
                {R.string.run_in_background, PlayerTweaksData.PLAYER_BUTTON_PIP},
                {R.string.action_video_speed, PlayerTweaksData.PLAYER_BUTTON_VIDEO_SPEED},
                {R.string.action_subtitles, PlayerTweaksData.PLAYER_BUTTON_SUBTITLES},
                {R.string.action_subscribe, PlayerTweaksData.PLAYER_BUTTON_SUBSCRIBE},
                {R.string.action_like, PlayerTweaksData.PLAYER_BUTTON_LIKE},
                {R.string.action_dislike, PlayerTweaksData.PLAYER_BUTTON_DISLIKE},
                {R.string.action_playlist_add, PlayerTweaksData.PLAYER_BUTTON_ADD_TO_PLAYLIST},
                {R.string.action_play_pause, PlayerTweaksData.PLAYER_BUTTON_PLAY_PAUSE},
                {R.string.action_repeat_mode, PlayerTweaksData.PLAYER_BUTTON_REPEAT_MODE},
                {R.string.action_next, PlayerTweaksData.PLAYER_BUTTON_NEXT},
                {R.string.action_previous, PlayerTweaksData.PLAYER_BUTTON_PREVIOUS},
                {R.string.playback_settings, PlayerTweaksData.PLAYER_BUTTON_HIGH_QUALITY}
        }) {
            options.add(UiOptionItem.from(getContext().getString((int) pair[0]), optionItem -> {
                if (optionItem.isSelected()) {
                    mPlayerTweaksData.setPlayerButtonEnabled(pair[1]);
                } else {
                    mPlayerTweaksData.setPlayerButtonDisabled(pair[1]);
                }
            }, mPlayerTweaksData.isPlayerButtonEnabled(pair[1])));
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_buttons), options);
    }

    private void appendSeekTypeCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.player_seek_regular),
                option -> {
                    mPlayerData.setSeekConfirmPauseEnabled(false);
                    mPlayerData.setSeekConfirmPlayEnabled(false);
                },
                !mPlayerData.isSeekConfirmPauseEnabled() && !mPlayerData.isSeekConfirmPlayEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_seek_confirmation_pause),
                option -> {
                    mPlayerData.setSeekConfirmPauseEnabled(true);
                    mPlayerData.setSeekConfirmPlayEnabled(false);
                },
                mPlayerData.isSeekConfirmPauseEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_seek_confirmation_play),
                option -> {
                    mPlayerData.setSeekConfirmPauseEnabled(false);
                    mPlayerData.setSeekConfirmPlayEnabled(true);
                },
                mPlayerData.isSeekConfirmPlayEnabled()));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_seek_type), options);
    }

    private void appendEndingTimeCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.option_disabled),
                option -> {
                    mPlayerData.setRemainingTimeEnabled(false);
                    mPlayerData.setEndingTimeEnabled(false);
                },
                !mPlayerData.isRemainingTimeEnabled() && !mPlayerData.isEndingTimeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_remaining_time),
                option -> {
                    mPlayerData.setRemainingTimeEnabled(true);
                    mPlayerData.setEndingTimeEnabled(false);
                },
                mPlayerData.isRemainingTimeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_show_ending_time),
                option -> {
                    mPlayerData.setEndingTimeEnabled(true);
                    mPlayerData.setRemainingTimeEnabled(false);
                },
                mPlayerData.isEndingTimeEnabled()));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_show_ending_time), options);
    }

    private void appendPixelRatioCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        ArrayList<Pair<String, Float>> pairs = new ArrayList<>();
        pairs.add(new Pair<>("1:1 (16:9 display)", 1.0f));
        pairs.add(new Pair<>("1.11111:1 (16:10 display)", 1.11111f));
        pairs.add(new Pair<>("1.3333:1 (4:3 display)", 1.3333f));
        // There is no display with exact 21:9 proportion???
        //pairs.add(new Pair<>("0.7619:1 (21:9 display)", 0.7619f));
        pairs.add(new Pair<>("0.75:1 (64:27 display)", 0.75f));
        pairs.add(new Pair<>("0.7442:1 (43:18 display)", 0.7442f));
        pairs.add(new Pair<>("0.7407:1 (12:5 display)", 0.7407f));

        for (Pair<String, Float> pair : pairs) {
            options.add(UiOptionItem.from(pair.first,
                    optionItem -> mPlayerTweaksData.setPixelRatio(pair.second),
                    Helpers.floatEquals(mPlayerTweaksData.getPixelRatio(), pair.second)));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_pixel_ratio), options);
    }

    private void appendPlaybackModeCategory(SettingsRows settingsPresenter) {
        OptionCategory category = AppDialogUtil.createPlaybackModeCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    /**
     * Only with the user's own Data API key (see VideoCategoryManager), the option keeps its state without one
     */
    private void appendMusicAutoplayCategory(SettingsRows settingsPresenter) {
        MusicAutoplayData musicAutoplayData = MusicAutoplayData.instance(getContext());
        List<OptionItem> options = new ArrayList<>();

        OptionItem musicAutoplay = UiOptionItem.from(getContext().getString(R.string.music_autoplay_enable),
                getContext().getString(R.string.music_autoplay_description),
                option -> musicAutoplayData.setEnabled(option.isSelected()),
                musicAutoplayData.isEnabled());
        options.add(musicAutoplay);

        if (!VideoCategoryManager.isAvailable()) {
            musicAutoplay.setEnabled(false);
            OptionItem note = UiOptionItem.from(getContext().getString(R.string.music_autoplay_needs_key));
            note.setNote(true);
            options.add(note);
        }

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.music_autoplay), options);
    }

    private void appendNetworkEngineCategory(SettingsRows settingsPresenter) {
        OptionCategory category = AppDialogUtil.createNetworkEngineCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    /**
     * The clients put in order with the remote (see OptionCategory.reorderList). Read again each time it's opened.
     */
    private void appendClientOrderCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (String client : mMediaServiceData.getClientOrder()) {
            options.add(UiOptionItem.from(AppDialogUtil.getClientTitle(getContext(), client), null, false, client));
        }

        settingsPresenter.appendReorderCategory(getContext().getString(R.string.client_order), options, items -> {
            List<String> clients = new ArrayList<>();

            for (OptionItem item : items) {
                clients.add((String) item.getData());
            }

            mMediaServiceData.setClientOrder(clients);
        });
    }

    private void appendPlayerExitCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.app_double_back_exit, GeneralData.EXIT_DOUBLE_BACK},
                {R.string.app_single_back_exit, GeneralData.EXIT_SINGLE_BACK}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mGeneralData.setPlayerExitShortcut(pair[1]),
                    mGeneralData.getPlayerExitShortcut() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.player_exit_shortcut), options);
    }

    private void appendSleepTimerCategory(SettingsRows settingsPresenter) {
        OptionCategory category = AppDialogUtil.createSleepTimerCategory(getContext());
        settingsPresenter.appendCategory(category);
    }

    private void appendMiscCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.suggestions_horizontally_scrolled),
                option -> mPlayerTweaksData.setSuggestionsHorizontallyScrolled(option.isSelected()),
                mPlayerTweaksData.isSuggestionsHorizontallyScrolled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.dont_resize_video_to_fit_dialog),
                option -> mPlayerTweaksData.setDontResizeVideoToFitDialogEnabled(option.isSelected()),
                mPlayerTweaksData.isDontResizeVideoToFitDialogEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_section_playlist),
                option -> mPlayerTweaksData.setSectionPlaylistEnabled(option.isSelected()),
                mPlayerTweaksData.isSectionPlaylistEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_chapter_notification2),
                option -> mPlayerTweaksData.setChapterNotificationEnabled(option.isSelected()),
                mPlayerTweaksData.isChapterNotificationEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.search_background_playback),
                option -> mSearchData.setTempBackgroundModeEnabled(option.isSelected()),
                mSearchData.isTempBackgroundModeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_loop_shorts),
                option -> mPlayerTweaksData.setLoopShortsEnabled(option.isSelected()),
                mPlayerTweaksData.isLoopShortsEnabled()));

        //options.add(UiOptionItem.from(getContext().getString(R.string.app_corner_clock),
        //        option -> {
        //            mGeneralData.enableGlobalClock(option.isSelected());
        //            mRestartApp = true;
        //        },
        //        mGeneralData.isGlobalClockEnabled()));
        //
        //options.add(UiOptionItem.from(getContext().getString(R.string.player_corner_clock),
        //        option -> mPlayerData.enableGlobalClock(option.isSelected()),
        //        mPlayerData.isGlobalClockEnabled()));
        //
        //options.add(UiOptionItem.from(getContext().getString(R.string.player_corner_ending_time),
        //        option -> mPlayerData.enableGlobalEndingTime(option.isSelected()),
        //        mPlayerData.isGlobalEndingTimeEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.remember_position_of_live_videos),
                option -> mPlayerTweaksData.setRememberPositionOfLiveVideosEnabled(option.isSelected()),
                mPlayerTweaksData.isRememberPositionOfLiveVideosEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.remember_position_of_short_videos),
                option -> mPlayerTweaksData.setRememberPositionOfShortVideosEnabled(option.isSelected()),
                mPlayerTweaksData.isRememberPositionOfShortVideosEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_speed_button_old_behavior),
                option -> mPlayerTweaksData.setSpeedButtonOldBehaviorEnabled(option.isSelected()),
                mPlayerTweaksData.isSpeedButtonOldBehaviorEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_global_focus),
                getContext().getString(R.string.player_global_focus_desc),
                option -> mPlayerTweaksData.setSyncRowButtonIndexEnabled(option.isSelected()),
                mPlayerTweaksData.isSyncRowButtonIndexEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_ui_animations),
                option -> mPlayerTweaksData.setUIAnimationsEnabled(option.isSelected()),
                mPlayerTweaksData.isUIAnimationsEnabled()));

        // See: Utils.updateTooltip
        //options.add(UiOptionItem.from(getContext().getString(R.string.player_show_tooltips) + ": " + getContext().getString(R.string.long_press_for_options),
        //        option -> {
        //            mGeneralData.enableFirstUseTooltip(option.isSelected());
        //            mRestartApp = true;
        //        },
        //        mGeneralData.isFirstUseTooltipEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_button_long_click),
                option -> mPlayerTweaksData.setButtonLongClickEnabled(option.isSelected()),
                mPlayerTweaksData.isButtonLongClickEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.queue_respects_playback_mode),
                option -> mPlayerTweaksData.setQueueRespectsPlaybackMode(option.isSelected()),
                mPlayerTweaksData.isQueueRespectsPlaybackMode()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.settings_misc_options), options);
    }

    private void appendDeveloperCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.force_legacy_codecs),
                getContext().getString(R.string.force_legacy_codecs_desc),
                option -> mPlayerData.setLegacyCodecsForced(option.isSelected()),
                mPlayerData.isLegacyCodecsForced()));

        options.add(UiOptionItem.from(getContext().getString(R.string.alt_presets_behavior),
                getContext().getString(R.string.alt_presets_behavior_desc),
                option -> mPlayerTweaksData.setAltPresetsEnabled(option.isSelected()),
                mPlayerTweaksData.isAltPresetsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.prefer_avc_over_vp9),
                getContext().getString(R.string.prefer_avc_over_vp9_desc),
                option -> mPlayerTweaksData.setAvcOverVp9Preferred(option.isSelected()),
                mPlayerTweaksData.isAvcOverVp9Preferred()));

        // On, but suspended on the device after it froze the picture in two runs (see TunnelingFallback): said, and set again, tried again
        // till it freezes once more (see TunnelingFallback#reset, which keeps when)
        boolean isTunnelingSuspended = mPlayerTweaksData.isTunneledPlaybackEnabled() && TunnelingFallback.isSuspended(getContext());
        options.add(UiOptionItem.from(getContext().getString(R.string.tunneled_video_playback),
                getContext().getString(isTunnelingSuspended ? R.string.tunneled_video_playback_suspended_desc
                        : R.string.tunneled_video_playback_desc),
                option -> {
                    mPlayerTweaksData.setTunneledPlaybackEnabled(option.isSelected());
                    TunnelingFallback.reset(getContext());
                    if (option.isSelected()) {
                        // Tunneled playback works only with SurfaceView
                        mPlayerTweaksData.setTextureViewEnabled(false);
                    }
                },
                mPlayerTweaksData.isTunneledPlaybackEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.skip_codec_profile_check),
                getContext().getString(R.string.skip_codec_profile_check_desc),
                option -> mPlayerTweaksData.setProfileLevelCheckSkipped(option.isSelected()),
                mPlayerTweaksData.isProfileLevelCheckSkipped()));

        options.add(UiOptionItem.from(getContext().getString(R.string.force_sw_codec),
                getContext().getString(R.string.force_sw_codec_desc),
                option -> mPlayerTweaksData.setSWDecoderForced(option.isSelected()),
                mPlayerTweaksData.isSWDecoderForced()));

        options.add(UiOptionItem.from(getContext().getString(R.string.keep_finished_activities),
                option -> mPlayerTweaksData.setKeepFinishedActivityEnabled(option.isSelected()),
                mPlayerTweaksData.isKeepFinishedActivityEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.hide_settings_section),
                option -> {
                    mSidebarService.enableSettingsSection(!option.isSelected());
                    mRestartApp = true;
                },
                !mSidebarService.isSettingsSectionEnabled()));

        // Disabled inside RetrofitHelper
        //options.add(UiOptionItem.from("Prefer IPv4 DNS",
        //        option -> GlobalPreferences.instance(getContext()).preferIPv4Dns(option.isSelected()),
        //        GlobalPreferences.instance(getContext()).isIPv4DnsPreferred()));
        //
        //options.add(UiOptionItem.from("Enable DNS over HTTPS",
        //        option -> GlobalPreferences.instance(getContext()).enableDnsOverHttps(option.isSelected()),
        //        GlobalPreferences.instance(getContext()).isDnsOverHttpsEnabled()));

        // Need to be enabled on older version of ExoPlayer (e.g. 2.10.6).
        // It's because there's no tweaks for modern devices.
        //options.add(UiOptionItem.from("Enable set output surface workaround",
        //        option -> mPlayerTweaksData.enableSetOutputSurfaceWorkaround(option.isSelected()),
        //        mPlayerTweaksData.isSetOutputSurfaceWorkaroundEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.player_tweaks), options);
    }
}
