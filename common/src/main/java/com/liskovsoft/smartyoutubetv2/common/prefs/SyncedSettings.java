package com.liskovsoft.smartyoutubetv2.common.prefs;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.youtubeapi.service.ServiceProfileData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings the storage sync shares between devices (see StorageSync), each under a key that never changes: the key is the
 * setting's name on the server, whatever the field is called or where it's saved. A setting whose meaning or type changes
 * gets a new key (the old one stays listed while older app versions may still send it). Whatever isn't listed stays on the
 * device: the passwords, Child mode, the device's history of the app, and the settings that depend on its hardware, display,
 * network or audio setup (AFR, codecs, buffers, the UI scale, the audio delay, the decoder workarounds and so on).<br/>
 * Most settings are each account's own (see AppPrefs.PROFILE_DATA_KEYS), so they're synced per account; only a few are global.
 */
public final class SyncedSettings {
    /**
     * Raised when the meaning of the keys changes in a way the server's records of older versions should know of
     */
    public static final int SCHEMA = 1;
    /**
     * The global settings' data key: its owner isn't a profile listener (see StorageSync.applyWrites)
     */
    public static final String REMOTE_CONTROL_DATA = RemoteControlData.DEVICE_LINK_DATA;

    public enum Type {
        BOOL("bool"), INT("int"), LONG("long"), FLOAT("float"), STRING("string"),
        /**
         * Saved as the app saves it (a list, an encoded item): synced as text, not to be edited by hand
         */
        RAW("raw");

        public final String name;

        Type(String name) {
            this.name = name;
        }
    }

    /**
     * One value of a setting that has a fixed set, for the server's editor (a list of names rather than a number)
     */
    public static final class Option {
        public final long value;
        public final String label;

        Option(long value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    /**
     * Where a data key is kept
     */
    public enum Store {
        /**
         * The app's own (AppPrefs, files/app_prefs)
         */
        APP,
        /**
         * The services' (MediaServiceCore's ServiceProfileData, files/yt_service_prefs)
         */
        SERVICE
    }

    /**
     * One synced setting: an index of a data key (see Helpers.mergeData), or the whole data key (index WHOLE)
     */
    public static final class Field {
        public static final int WHOLE = -1;
        public final String key;
        public final String dataKey;
        public final int index;
        public final Type type;
        /**
         * Each account's own (the data key is per profile), else global
         */
        public final boolean isAccount;
        public final String section;
        /**
         * For the server's editor: English, not translated (the web interface is English)
         */
        public final String label;
        public final Store store;
        /**
         * The values the app's own settings offer, empty for a setting without a fixed set
         */
        public final List<Option> options = new ArrayList<>();

        Field(String key, String dataKey, int index, Type type, boolean isAccount, String section, String label, Store store) {
            this.store = store;
            this.key = key;
            this.dataKey = dataKey;
            this.index = index;
            this.type = type;
            this.isAccount = isAccount;
            this.section = section;
            this.label = label;
        }

        public boolean isWhole() {
            return index == WHOLE;
        }
    }

    private static final List<Field> sFields = new ArrayList<>();
    private static final Map<String, Field> sByKey = new LinkedHashMap<>();

    private static String sSection;
    private static String sDataKey;
    private static boolean sIsAccount;

    private static void section(String section, String dataKey, boolean isAccount) {
        sSection = section;
        sDataKey = dataKey;
        sIsAccount = isAccount;
    }

    private static void add(String key, int index, Type type, String label) {
        add(key, index, type, label, Store.APP);
    }

    private static void add(String key, int index, Type type, String label, Store store) {
        Field field = new Field(key, sDataKey, index, type, sIsAccount, sSection, label, store);

        if (sByKey.put(key, field) != null) {
            throw new IllegalStateException("Synced setting listed twice: " + key);
        }

        sFields.add(field);
    }

    /**
     * The values of a setting with a fixed set: value, label, value, label... As the app's settings offer them (see e.g.
     * GeneralSettingsPresenter, PlayerSettingsPresenter, MainUISettingsPresenter, AppDialogUtil).
     */
    private static void choices(String key, Object... pairs) {
        Field field = sByKey.get(key);

        if (field == null || pairs.length % 2 != 0) {
            throw new IllegalStateException("Choices of an unknown setting: " + key);
        }

        for (int i = 0; i < pairs.length; i += 2) {
            field.options.add(new Option(((Number) pairs[i]).longValue(), (String) pairs[i + 1]));
        }
    }

    private static void whole(String key, String dataKey, String label) {
        sDataKey = dataKey;
        add(key, Field.WHOLE, Type.RAW, label);
    }

    private static void serviceWhole(String key, String dataKey, String label) {
        sDataKey = dataKey;
        add(key, Field.WHOLE, Type.RAW, label, Store.SERVICE);
    }

    static {
        section("General", GeneralData.GENERAL_DATA, true);
        // Not: 0 backup zip name, 6 old pinned items (moved to the sidebar long ago), 10 proxy, 11 bridge check, 27 VPN,
        // 33 settings password, 34 Child mode, 38 app icon, 39 version code, 41 master password, 57 changelog,
        // 64 first use tooltip, 65 device specific backup, 69 and 70 backup frequency
        add("general.appExitShortcut", 3, Type.INT, "App exit shortcut");
        add("general.returnToLauncher", 4, Type.BOOL, "Return to the launcher on exit");
        add("general.backgroundPlaybackShortcut", 5, Type.INT, "Background playback shortcut");
        add("general.hideShortsFromSubscriptions", 7, Type.BOOL, "Hide Shorts in Subscriptions");
        add("general.fastForwardRewindAction", 8, Type.INT, "Fast forward / rewind keys");
        add("general.okButtonLongPressDisabled", 12, Type.BOOL, "OK long press disabled");
        add("general.hideUpcoming", 15, Type.BOOL, "Hide upcoming videos");
        add("general.pageUpDownAction", 16, Type.INT, "Page up / down keys");
        add("general.channelUpDownAction", 18, Type.INT, "Channel up / down keys");
        add("general.hideShortsFromHome", 24, Type.BOOL, "Hide Shorts on Home");
        add("general.hideShortsFromHistory", 25, Type.BOOL, "Hide Shorts in History");
        add("general.screensaverDisabled", 26, Type.BOOL, "Screensaver disabled");
        add("general.globalClock", 31, Type.BOOL, "Clock everywhere");
        add("general.historyEnabled", 35, Type.BOOL, "History on (older versions)");
        add("general.screensaverTimeoutMs", 36, Type.INT, "Screensaver timeout (ms)");
        add("general.selectChannelSection", 40, Type.BOOL, "Select the channel section");
        add("general.oldUpdateNotifications", 43, Type.BOOL, "Old update notifications");
        add("general.screensaverDimmingPercents", 44, Type.INT, "Screensaver dimming (%)");
        add("general.nextPreviousAction", 45, Type.INT, "Next / previous keys");
        add("general.remapPlayToOk", 46, Type.BOOL, "Play key as OK");
        add("general.historyState", 47, Type.INT, "History");
        add("general.rememberSubscriptionsPosition", 48, Type.BOOL, "Remember the Subscriptions position");
        add("general.remapNumbersToSpeed", 50, Type.BOOL, "Number keys change the speed");
        add("general.dpadUpDownAction", 51, Type.INT, "D-pad up / down in the player");
        add("general.remapDpadLeftToVolume", 54, Type.BOOL, "D-pad left / right change the volume");
        add("general.hideWatchedFromNotifications", 56, Type.BOOL, "Hide watched in Notifications");
        add("general.playerExitShortcut", 58, Type.INT, "Player exit shortcut");
        add("general.fullscreenMode", 60, Type.BOOL, "Full screen mode");
        add("general.rememberPinnedPosition", 62, Type.BOOL, "Remember the pinned sections' position");
        add("general.searchExitShortcut", 68, Type.INT, "Search exit shortcut");
        add("general.remapSToSpeedToggle", 72, Type.BOOL, "S key toggles the speed");
        // The account's own state, not settings: on every device of the account
        add("general.lastPlaylistId", 13, Type.STRING, "Last playlist added to");
        add("general.lastPlaylistTitle", 28, Type.STRING, "Last playlist added to (title)");
        add("general.playlistOrder", 29, Type.RAW, "Sort order of each playlist");
        add("general.streamReminders", 30, Type.RAW, "Stream reminders");
        add("general.rememberedPositions", 63, Type.RAW, "Remembered positions of the sections");

        section("Player", PlayerData.VIDEO_PLAYER_DATA, true);
        // Not: 9 and 10 the video and audio formats (what the device and its display or receiver can play), 12 buffer, 14 resize mode,
        // 16-18, 35, 44, 45 AFR, 20 and 62 audio delay, 24 legacy codecs, 25 sleep timer, 30 aspect ratio, 32 time correction,
        // 40 volume, 47 last subtitles, 48 last speed, 49 rotation, 50 zoom, 57 speed of each video, 60 last audio languages,
        // 61 flip, 63 sound off. Nor 15 the speed: the one playing, read back as 1.0 unless the same speed is kept for every video
        // (see PlayerData.restoreState), so a device would send 1.0 back after each other device's change
        add("player.okButtonBehavior", 0, Type.INT, "OK button");
        add("player.uiHideTimeoutSec", 1, Type.INT, "Hide the controls after (s)");
        add("player.seekPreviewMode", 3, Type.INT, "Seek preview");
        add("player.seekConfirmPause", 4, Type.BOOL, "Pause while seeking");
        add("player.clock", 5, Type.BOOL, "Clock");
        add("player.remainingTime", 6, Type.BOOL, "Remaining time");
        add("player.backgroundMode", 7, Type.INT, "Background playback");
        add("player.subtitleFormat", 11, Type.RAW, "Subtitles");
        add("player.subtitleStyle", 13, Type.INT, "Subtitle style");
        add("player.allSpeed", 21, Type.BOOL, "Same speed for every video");
        add("player.qualityInfo", 28, Type.BOOL, "Quality info");
        add("player.speedPerVideo", 29, Type.BOOL, "Remember the speed of each video");
        add("player.globalClock", 31, Type.BOOL, "Clock everywhere");
        add("player.globalEndingTime", 33, Type.BOOL, "Ending time everywhere");
        add("player.endingTime", 34, Type.BOOL, "Ending time");
        add("player.seekConfirmPlay", 36, Type.BOOL, "Play after seeking");
        add("player.seekIncrementMs", 37, Type.INT, "Seek step (ms)");
        add("player.subtitleScale", 39, Type.FLOAT, "Subtitle size");
        add("player.tooltips", 41, Type.BOOL, "Tooltips");
        add("player.subtitlePosition", 42, Type.FLOAT, "Subtitle position");
        add("player.numberKeySeek", 43, Type.BOOL, "Number keys seek");
        add("player.liveChat", 46, Type.BOOL, "Live chat");
        add("player.playbackMode", 51, Type.INT, "Playback mode");
        add("player.audioLanguage", 52, Type.STRING, "Audio language");
        add("player.subtitleLanguage", 53, Type.STRING, "Subtitle language");
        add("player.subtitlesOfEachChannel", 54, Type.RAW, "Subtitles of each channel");
        add("player.subtitlesPerChannel", 55, Type.BOOL, "Remember the subtitles of each channel");
        add("player.speedPerChannel", 56, Type.BOOL, "Remember the speed of each channel");
        add("player.pitch", 58, Type.FLOAT, "Pitch");
        add("player.skipShorts", 59, Type.BOOL, "Skip Shorts");

        section("Player tweaks", PlayerTweaksData.VIDEO_PLAYER_TWEAKS_DATA, true);
        // Not the workarounds of hardware, decoders, streams and audio output: 0-5, 7-10, 12, 18, 21, 26-30, 34, 38, 42, 47, 51, 54, 59
        add("tweaks.playbackNotificationsDisabled", 11, Type.BOOL, "Playback notifications disabled");
        add("tweaks.playerButtons", 13, Type.INT, "Player buttons");
        add("tweaks.altPresets", 15, Type.BOOL, "Alternative quality presets");
        add("tweaks.rememberPositionOfShorts", 16, Type.BOOL, "Remember the position of Shorts");
        add("tweaks.suggestionsDisabled", 17, Type.BOOL, "Suggestions disabled");
        add("tweaks.chatPlacedLeft", 19, Type.BOOL, "Chat on the left");
        add("tweaks.realChannelIcon", 20, Type.BOOL, "Real channel icon");
        add("tweaks.qualityInfoBitrate", 22, Type.BOOL, "Bitrate in the quality info");
        add("tweaks.speedButtonOldBehavior", 23, Type.BOOL, "Speed button: old behavior");
        add("tweaks.buttonLongClick", 24, Type.BOOL, "Long click on the buttons");
        add("tweaks.longSpeedList", 25, Type.BOOL, "Long speed list");
        add("tweaks.sectionPlaylist", 31, Type.BOOL, "Section as a playlist");
        add("tweaks.screenOffTimeout", 32, Type.BOOL, "Screen off timeout");
        add("tweaks.screenOffTimeoutSec", 33, Type.INT, "Screen off after (s)");
        add("tweaks.likesCounter", 35, Type.BOOL, "Likes counter");
        add("tweaks.chapterNotification", 36, Type.BOOL, "Chapter notification");
        add("tweaks.screenOffDimmingPercents", 37, Type.INT, "Screen off dimming (%)");
        add("tweaks.playerUiOnNext", 39, Type.BOOL, "Controls on the next video");
        add("tweaks.playerAutoVolume", 40, Type.BOOL, "Auto volume");
        add("tweaks.syncRowButtonIndex", 41, Type.BOOL, "Keep the button row position");
        add("tweaks.loopShorts", 44, Type.BOOL, "Loop Shorts");
        add("tweaks.quickSkipShorts", 45, Type.BOOL, "Quick skip of Shorts");
        add("tweaks.rememberPositionOfLive", 46, Type.BOOL, "Remember the position of live videos");
        add("tweaks.extraLongSpeedList", 49, Type.BOOL, "Extra long speed list");
        add("tweaks.quickSkipVideos", 50, Type.BOOL, "Quick skip of videos");
        add("tweaks.commentsPlacedLeft", 52, Type.BOOL, "Comments on the left");
        add("tweaks.dontResizeVideoToFitDialog", 55, Type.BOOL, "Don't resize the video for dialogs");
        add("tweaks.suggestionsHorizontallyScrolled", 56, Type.BOOL, "Suggestions scroll horizontally");
        add("tweaks.quickSkipShortsAlt", 57, Type.BOOL, "Quick skip of Shorts (other keys)");
        add("tweaks.quickSkipVideosAlt", 58, Type.BOOL, "Quick skip of videos (other keys)");
        add("tweaks.queueRespectsPlaybackMode", 60, Type.BOOL, "The queue follows the playback mode");

        section("Interface", MainUIData.MAIN_UI_DATA, true);
        // Not: 1 and 2 the grid and UI scales (the screen's), 31 a marker
        add("ui.colorScheme", 3, Type.INT, "Color scheme");
        add("ui.cardMultilineTitle", 4, Type.BOOL, "Card titles on several lines");
        add("ui.channelCategorySorting", 5, Type.INT, "Channels sorting");
        add("ui.playlistsStyle", 6, Type.INT, "Playlists style");
        add("ui.cardTitleLines", 7, Type.INT, "Card title lines");
        add("ui.cardTextAutoScroll", 8, Type.BOOL, "Card text scrolls");
        add("ui.uploadsOldLook", 9, Type.BOOL, "Uploads: old look");
        add("ui.uploadsAutoLoad", 10, Type.BOOL, "Uploads load by themselves");
        add("ui.cardTextScrollSpeed", 11, Type.FLOAT, "Card text scroll speed");
        add("ui.menuItems", 12, Type.LONG, "Context menu items");
        add("ui.topButtons", 13, Type.INT, "Top buttons");
        add("ui.thumbQuality", 15, Type.INT, "Thumbnail quality");
        add("ui.cardMultilineSubtitle", 16, Type.BOOL, "Card subtitles on several lines");
        add("ui.menuItemsOrder", 17, Type.RAW, "Context menu order");
        add("ui.channelsFilter", 18, Type.BOOL, "Channels filter");
        add("ui.channelSearchBar", 19, Type.BOOL, "Channel search bar");
        add("ui.pinnedChannelRows", 20, Type.BOOL, "Pinned channel rows");
        add("ui.cardPreviewType", 21, Type.INT, "Card preview");
        add("ui.unlocalizedTitles", 22, Type.BOOL, "Original titles");
        add("ui.tweaks", 23, Type.LONG, "Interface tweaks");
        add("ui.shortsDate", 24, Type.BOOL, "Date of Shorts");
        add("ui.menuItemsOfEachMenu", 25, Type.RAW, "Items of each menu");
        add("ui.menuOrderVideos", 26, Type.RAW, "Video menu order");
        add("ui.menuOrderSubscriptions", 27, Type.RAW, "Subscriptions menu order");
        add("ui.menuOrderHistory", 28, Type.RAW, "History menu order");
        add("ui.menuOrderPlaylists", 29, Type.RAW, "Playlists menu order");
        add("ui.menuOrderSidebar", 30, Type.RAW, "Sidebar menu order");

        section("SponsorBlock", SponsorBlockData.SPONSOR_BLOCK_DATA, true);
        add("sponsorBlock.enabled", 0, Type.BOOL, "SponsorBlock");
        add("sponsorBlock.actions", 6, Type.RAW, "Segment actions");
        add("sponsorBlock.colorCategories", 7, Type.RAW, "Segments shown on the seek bar");
        add("sponsorBlock.dontSkipSegmentAgain", 8, Type.BOOL, "Don't skip a segment again");
        add("sponsorBlock.excludedChannels", 9, Type.RAW, "Excluded channels");
        add("sponsorBlock.paidContentNotification", 10, Type.BOOL, "Paid content notification");
        add("sponsorBlock.ignoredDurationMs", 11, Type.LONG, "Ignore segments shorter than (ms)");

        section("Search", SearchData.SEARCH_DATA, true);
        // Not: 6 the speech recognizer and 10 the keyboard fix (the device's)
        add("search.instantVoiceSearch", 0, Type.BOOL, "Instant voice search");
        add("search.options", 1, Type.INT, "Search options");
        add("search.focusOnResults", 2, Type.BOOL, "Focus on the results");
        add("search.keyboardAutoShow", 3, Type.BOOL, "Show the keyboard");
        add("search.tempBackgroundMode", 4, Type.BOOL, "Background mode while searching");
        add("search.trendingSearches", 7, Type.BOOL, "Trending searches");
        add("search.historyDisabled", 8, Type.BOOL, "Search history off");
        add("search.popularSearchesDisabled", 9, Type.BOOL, "Popular searches off");
        add("search.typingCorrectionDisabled", 11, Type.BOOL, "Typing correction off");

        section("Sidebar", AppPrefs.SIDEBAR_DATA, true);
        add("sidebar.pinnedItems", 0, Type.RAW, "Sections and pinned items");
        add("sidebar.bootSection", 1, Type.INT, "Section at start");
        add("sidebar.settingsSection", 2, Type.BOOL, "Settings in the sidebar");

        // Small settings groups, each synced as a whole
        section("Content filters", null, true);
        whole("filters.aisList", AiSListFilterData.class.getSimpleName(), "AiSList filter");
        whole("filters.censoredWords", CensoredWordsData.class.getSimpleName(), "Censored words");
        whole("filters.channelGroups", ChannelGroupFilterData.class.getSimpleName(), "Channel group filter");
        whole("filters.deArrow", DeArrowData.class.getSimpleName(), "DeArrow");
        whole("filters.duplicates", DuplicatesData.class.getSimpleName(), "Duplicates");
        whole("filters.musicAutoplay", MusicAutoplayData.class.getSimpleName(), "Music autoplay");
        whole("filters.oldVideos", OldVideosData.class.getSimpleName(), "Old videos");
        whole("filters.openedVideos", OpenedVideosData.class.getSimpleName(), "Opened videos");
        whole("filters.collaborations", CollaborationsData.class.getSimpleName(), "Collaborations");
        whole("filters.shows", ShowsData.class.getSimpleName(), "Shows");
        whole("filters.streamed", StreamedData.class.getSimpleName(), "Streamed");
        whole("filters.topChannels", TopChannelsData.class.getSimpleName(), "Top channels");
        whole("filters.watchLater", WatchLaterData.class.getSimpleName(), "Watch later");
        whole("filters.exploreTopics", ExploreTopicsData.class.getSimpleName(), "Explore topics");
        whole("filters.musicCategory", CategoryFilterData.MUSIC_KEY, "Music category");
        whole("filters.gamingCategory", CategoryFilterData.GAMING_KEY, "Gaming category");
        whole("filters.sportsCategory", CategoryFilterData.SPORTS_KEY, "Sports category");
        whole("filters.newsCategory", CategoryFilterData.NEWS_KEY, "News & Politics category");
        whole("filters.techCategory", CategoryFilterData.TECH_KEY, "Science & Technology category");
        whole("filters.blockedChannels", BlockedChannelData.BLOCKED_CHANNEL_DATA, "Blocked channels");
        whole("filters.keywords", KeywordFilterData.KEYWORD_FILTER_DATA, "Keyword filter");
        whole("filters.exclusions", FilterExclusionData.FILTER_EXCLUSION_DATA, "Filter exclusions");
        whole("filters.hideContent", HideContentData.HIDE_CONTENT_DATA, "Hidden content");
        whole("ui.homeRows", HomeRowsData.HOME_ROWS_DATA, "Home rows");
        whole("ui.locale", LocaleData.LOCALE_DATA, "Language and country");

        // The account's own content of the app
        section("Account content", null, true);
        whole("content.hiddenVideos", HiddenVideosData.HIDDEN_VIDEOS_DATA, "Hidden videos (local)");
        whole("launcher.playlists", AppPrefs.HOME_SCREEN_PLAYLISTS_DATA, "Playlists on the launcher's home screen");
        whole("launcher.channels", AppPrefs.HOME_SCREEN_CHANNELS_DATA, "App channels on the launcher's home screen");
        // Kept by the services (MediaServiceCore), not by the app: the Home rows and the sidebar refer to the groups by id
        serviceWhole("content.subscriptionGroups", ServiceProfileData.SUBSCRIPTION_GROUPS, "Subscription groups");
        serviceWhole("content.playlistGroups", ServiceProfileData.PLAYLIST_GROUPS, "Playlist groups");
        serviceWhole("content.searchHistory", ServiceProfileData.SEARCH_TAGS, "Search history");

        section("Remote control", RemoteControlData.DEVICE_LINK_DATA, false);
        // Not: 2 device link (the device's), 6 last video, 7 connected before
        add("remote.finishOnDisconnect", 3, Type.BOOL, "Close on disconnect");
        add("remote.connectMessages", 4, Type.BOOL, "Connection messages");
        add("remote.remoteHistoryDisabled", 5, Type.BOOL, "History off for remote videos");

        choices("general.appExitShortcut", GeneralData.EXIT_NONE, "Don't exit", GeneralData.EXIT_DOUBLE_BACK, "Double back",
                GeneralData.EXIT_SINGLE_BACK, "Single back");
        choices("general.playerExitShortcut", GeneralData.EXIT_DOUBLE_BACK, "Double back", GeneralData.EXIT_SINGLE_BACK, "Single back");
        choices("general.searchExitShortcut", GeneralData.EXIT_DOUBLE_BACK, "Double back", GeneralData.EXIT_SINGLE_BACK, "Single back");
        choices("general.backgroundPlaybackShortcut", GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME, "By pressing HOME",
                GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK, "By pressing HOME or BACK",
                GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_BACK, "By pressing BACK");
        choices("general.historyState", GeneralData.HISTORY_AUTO, "Auto (the account's setting)", GeneralData.HISTORY_ENABLED, "On",
                GeneralData.HISTORY_DISABLED, "Off on this account, here", GeneralData.HISTORY_PAUSED, "Paused on the account");
        choices("general.dpadUpDownAction", GeneralData.ACTION_UNDEFINED, "Off", GeneralData.ACTION_VOLUME_UP_DOWN, "Volume up / down",
                GeneralData.ACTION_SPEED_UP_DOWN, "Speed up / down");
        choices("general.nextPreviousAction", GeneralData.ACTION_UNDEFINED, "Off", GeneralData.ACTION_FAST_FORWARD_REWIND,
                "Fast forward / rewind", GeneralData.ACTION_SPEED_UP_DOWN, "Speed up / down");
        choices("general.fastForwardRewindAction", GeneralData.ACTION_UNDEFINED, "Off", GeneralData.ACTION_NEXT_PREVIOUS,
                "Next / previous video", GeneralData.ACTION_SPEED_UP_DOWN, "Speed up / down", GeneralData.ACTION_SPEED_TOGGLE, "Speed on / off");
        choices("general.pageUpDownAction", GeneralData.ACTION_UNDEFINED, "Off", GeneralData.ACTION_NEXT_PREVIOUS, "Next / previous video",
                GeneralData.ACTION_LIKE_DISLIKE, "Like / dislike", GeneralData.ACTION_SPEED_UP_DOWN, "Speed up / down",
                GeneralData.ACTION_SPEED_DOWN_UP, "Speed down / up");
        choices("general.channelUpDownAction", GeneralData.ACTION_UNDEFINED, "Off", GeneralData.ACTION_VOLUME_UP_DOWN, "Volume up / down",
                GeneralData.ACTION_NEXT_PREVIOUS, "Next / previous video");
        choices("player.okButtonBehavior", PlayerData.OK_ONLY_UI, "Only the controls", PlayerData.OK_UI_AND_PAUSE, "Controls and pause",
                PlayerData.OK_ONLY_PAUSE, "Only pause", PlayerData.OK_TOGGLE_SPEED, "Speed on / off");
        choices("player.seekPreviewMode", PlayerData.SEEK_PREVIEW_NONE, "Off", PlayerData.SEEK_PREVIEW_SINGLE, "Single frame",
                PlayerData.SEEK_PREVIEW_CAROUSEL_SLOW, "Carousel (slow, by keyframes)", PlayerData.SEEK_PREVIEW_CAROUSEL_FAST,
                "Carousel (fast, not precise)");
        choices("player.backgroundMode", PlayerData.BACKGROUND_MODE_DEFAULT, "Off", PlayerData.BACKGROUND_MODE_SOUND, "Sound only",
                PlayerData.BACKGROUND_MODE_PIP, "Picture in picture", PlayerData.BACKGROUND_MODE_PLAY_BEHIND, "Play behind");
        choices("player.playbackMode", PlayerData.PLAYBACK_MODE_ALL, "Play videos continuously", PlayerData.PLAYBACK_MODE_ONE,
                "Repeat the video", PlayerData.PLAYBACK_MODE_SHUFFLE, "Shuffle any playlist", PlayerData.PLAYBACK_MODE_LIST,
                "Play only the playlist's videos", PlayerData.PLAYBACK_MODE_REVERSE_LIST, "Playlist or channel in reverse order",
                PlayerData.PLAYBACK_MODE_PAUSE, "Pause after each video", PlayerData.PLAYBACK_MODE_CLOSE, "Stop after one video");
        // MainUIData's color schemes, by id
        choices("ui.colorScheme", 8, "Electric Blue", 0, "Teal", 4, "Teal (OLED)", 2, "Red", 1, "Dark Grey", 3, "Dark Grey (OLED)",
                5, "Dark Grey (monochrome)", 6, "Dark Blue", 7, "Dark Blue (OLED)");
        choices("ui.channelCategorySorting", MainUIData.CHANNEL_SORTING_NEW_CONTENT, "New content", MainUIData.CHANNEL_SORTING_NAME,
                "Alphabetically", MainUIData.CHANNEL_SORTING_LAST_VIEWED, "Last viewed", MainUIData.CHANNEL_SORTING_DEFAULT, "Default");
        choices("ui.playlistsStyle", MainUIData.PLAYLISTS_STYLE_GRID, "Grid", MainUIData.PLAYLISTS_STYLE_ROWS, "Rows");
        choices("ui.cardPreviewType", MainUIData.CARD_PREVIEW_DISABLED, "Off", MainUIData.CARD_PREVIEW_MUTED, "Video without sound",
                MainUIData.CARD_PREVIEW_FULL, "Video with sound");
        choices("ui.thumbQuality", ClickbaitRemover.THUMB_QUALITY_DEFAULT, "Default", ClickbaitRemover.THUMB_QUALITY_START,
                "Start of the video", ClickbaitRemover.THUMB_QUALITY_MIDDLE, "Middle of the video", ClickbaitRemover.THUMB_QUALITY_END,
                "End of the video");
    }

    private SyncedSettings() {
    }

    public static List<Field> all() {
        return Collections.unmodifiableList(sFields);
    }

    @Nullable
    public static Field get(String key) {
        return sByKey.get(key);
    }

    /**
     * Child mode of a profile, from its general data (see GeneralData): while it's on, the account's settings aren't synced
     */
    public static boolean isChildMode(@Nullable String generalData) {
        String[] split = com.liskovsoft.sharedutils.helpers.Helpers.splitData(generalData);
        return com.liskovsoft.sharedutils.helpers.Helpers.parseBoolean(split, 34, false);
    }

    public static String generalDataKey() {
        return GeneralData.GENERAL_DATA;
    }

    /**
     * The launcher's data: a change of it updates the launcher's channels (see Utils.updateChannels)
     */
    public static boolean isLauncherData(String dataKey) {
        return AppPrefs.HOME_SCREEN_PLAYLISTS_DATA.equals(dataKey) || AppPrefs.HOME_SCREEN_CHANNELS_DATA.equals(dataKey);
    }
}
