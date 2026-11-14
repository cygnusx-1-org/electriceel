package com.liskovsoft.smartyoutubetv2.tv.ui.modern.playback;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Placement and icons of the player actions in the modern controls row.
 */
public final class ModernActionStyle {
    /** Left side, in the start pill (avatar, description, timestamps, subscription, auto-play, next frame). */
    public static final int GROUP_LEFT = 0;
    /** Social actions, in the pill of the side that keeps the two pills closest in width: after the left group or before
     * the other group. */
    public static final int GROUP_SOCIAL = 1;
    /** Right side, in the end pill (everything else). */
    public static final int GROUP_OTHER = 2;
    /** Center (previous, play/pause, next). */
    public static final int GROUP_TRANSPORT = 3;

    public static final int SHAPE_ICON = 0;
    public static final int SHAPE_AVATAR = 1;

    private static final Map<Integer, int[]> ICONS = new HashMap<>();
    private static final Map<Integer, Integer> GROUPS = new HashMap<>();
    /** Auto-play button: the icon of each mode, the glyphs of the classic player (PlaybackModeAction) */
    private static final Map<Integer, Integer> PLAYBACK_MODE_ICONS = new HashMap<>();

    static {
        group(GROUP_LEFT, R.id.action_channel, R.id.action_info, R.id.action_timestamps, R.id.action_subscribe, R.id.action_repeat,
                R.id.action_next_frame);
        group(GROUP_SOCIAL, R.id.action_thumbs_up, R.id.action_thumbs_down, R.id.action_chat, R.id.action_playlist_add,
                R.id.action_share, R.id.action_playback_queue);

        // Video description: the "i" icon (its label shows under it when focused)
        icon(R.id.action_info, R.drawable.modern_ic_info, R.drawable.modern_ic_info_filled);
        // Subscription state as a bell: outlined when not subscribed, filled when subscribed (no ringing waves)
        icon(R.id.action_subscribe, R.drawable.modern_ic_notifications, R.drawable.modern_ic_notifications_filled);
        icon(R.id.action_thumbs_up, R.drawable.modern_ic_thumb_up, R.drawable.modern_ic_thumb_up_filled);
        icon(R.id.action_thumbs_down, R.drawable.modern_ic_thumb_down, R.drawable.modern_ic_thumb_down_filled);
        icon(R.id.action_chat, R.drawable.modern_ic_chat, R.drawable.modern_ic_chat_filled);
        icon(R.id.action_playlist_add, R.drawable.modern_ic_playlist_add, R.drawable.modern_ic_playlist_add_check);
        icon(R.id.action_share, R.drawable.modern_ic_share, R.drawable.modern_ic_share_filled);
        icon(R.id.action_playback_queue, R.drawable.modern_ic_queue_music, R.drawable.modern_ic_queue_music_filled);
        icon(R.id.lb_control_closed_captioning, R.drawable.modern_ic_closed_caption, R.drawable.modern_ic_closed_caption_filled);
        // Video quality and playback settings: the HQ sign of the classic player
        icon(R.id.lb_control_high_quality, R.drawable.modern_ic_high_quality, R.drawable.modern_ic_high_quality_filled);
        icon(R.id.action_video_speed, R.drawable.modern_ic_speed, R.drawable.modern_ic_speed_filled);
        icon(R.id.action_pip, R.drawable.modern_ic_picture_in_picture, R.drawable.modern_ic_picture_in_picture_filled);
        icon(R.id.action_screen_dimming, R.drawable.modern_ic_dark_mode, R.drawable.modern_ic_dark_mode_filled);
        icon(R.id.action_search, R.drawable.modern_ic_search, R.drawable.modern_ic_search_filled);
        icon(R.id.action_seek_interval, R.drawable.modern_ic_timer, R.drawable.modern_ic_timer_filled);
        icon(R.id.action_video_zoom, R.drawable.modern_ic_aspect_ratio, R.drawable.modern_ic_aspect_ratio_filled);
        icon(R.id.action_rotate, R.drawable.modern_ic_screen_rotation, R.drawable.modern_ic_screen_rotation_filled);
        icon(R.id.action_flip, R.drawable.modern_ic_flip, R.drawable.modern_ic_flip_filled);
        icon(R.id.action_sound_off, R.drawable.modern_ic_volume_off, R.drawable.modern_ic_volume_off_filled);
        icon(R.id.action_afr, R.drawable.modern_ic_hd, R.drawable.modern_ic_hd_filled);
        // SponsorBlock: the shield with a play sign of the classic player, filled when on
        icon(R.id.action_content_block, R.drawable.modern_ic_sponsor_block, R.drawable.modern_ic_sponsor_block_filled);
        icon(R.id.action_video_stats, R.drawable.modern_ic_bar_chart, R.drawable.modern_ic_bar_chart_filled);
        // Frame by frame (Electric Eel)
        icon(R.id.action_next_frame, R.drawable.modern_ic_next_frame, R.drawable.modern_ic_next_frame);
        // The time codes of the description (Electric Eel): a table of contents
        icon(R.id.action_timestamps, R.drawable.modern_ic_timestamps, R.drawable.modern_ic_timestamps);

        PLAYBACK_MODE_ICONS.put(PlayerConstants.PLAYBACK_MODE_CLOSE, R.drawable.modern_ic_repeat_off);
        PLAYBACK_MODE_ICONS.put(PlayerConstants.PLAYBACK_MODE_ONE, R.drawable.modern_ic_repeat_one);
        PLAYBACK_MODE_ICONS.put(PlayerConstants.PLAYBACK_MODE_ALL, R.drawable.modern_ic_repeat);
        PLAYBACK_MODE_ICONS.put(PlayerConstants.PLAYBACK_MODE_PAUSE, R.drawable.modern_ic_repeat_pause);
        PLAYBACK_MODE_ICONS.put(PlayerConstants.PLAYBACK_MODE_LIST, R.drawable.modern_ic_repeat_list);
        PLAYBACK_MODE_ICONS.put(PlayerConstants.PLAYBACK_MODE_SHUFFLE, R.drawable.modern_ic_shuffle);
        PLAYBACK_MODE_ICONS.put(PlayerConstants.PLAYBACK_MODE_REVERSE_LIST, R.drawable.modern_ic_replay);
    }

    private ModernActionStyle() {
    }

    public static int groupOf(int actionId, boolean isTransport) {
        if (isTransport) {
            return GROUP_TRANSPORT;
        }

        Integer group = GROUPS.get(actionId);
        return group != null ? group : GROUP_OTHER;
    }

    public static int shapeOf(int actionId) {
        if (actionId == R.id.action_channel) {
            return SHAPE_AVATAR;
        }

        return SHAPE_ICON;
    }

    /**
     * Order of the start group: avatar, description, timestamps, subscription, auto-play, next frame.
     */
    public static int startOrder(int actionId) {
        if (actionId == R.id.action_channel) {
            return 0;
        }
        if (actionId == R.id.action_info) {
            return 1;
        }
        if (actionId == R.id.action_timestamps) {
            return 2;
        }
        if (actionId == R.id.action_subscribe) {
            return 3;
        }
        if (actionId == R.id.action_repeat) {
            return 4;
        }
        if (actionId == R.id.action_next_frame) {
            return 5;
        }
        return 6;
    }

    /**
     * @return icon for the action state or 0 to keep the action's own icon
     */
    public static int iconOf(int actionId, boolean active) {
        int[] icons = ICONS.get(actionId);

        if (icons == null) {
            return 0;
        }

        return active ? icons[1] : icons[0];
    }

    /**
     * @return icon of the auto-play button for its mode (index of the action, a PlayerConstants.PLAYBACK_MODE_* value)
     */
    public static int playbackModeIconOf(int mode) {
        Integer icon = PLAYBACK_MODE_ICONS.get(mode);
        return icon != null ? icon : R.drawable.modern_ic_repeat;
    }

    private static void group(int group, int... actionIds) {
        for (int id : actionIds) {
            GROUPS.put(id, group);
        }
    }

    private static void icon(int actionId, int normal, int active) {
        ICONS.put(actionId, new int[] {normal, active});
    }
}
