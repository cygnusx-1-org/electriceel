package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.HashSet;
import java.util.Set;

/**
 * The videos already in the rows of a section, so each is shown once (see DuplicatesData). A load of the section has its own.<br/>
 * The first card stays: the rows are created top to bottom, and the pages a row gets later come after the rows below it,
 * so they're left out before they're shown, never taken out of a row.
 */
public class DuplicateFilter {
    private final Set<String> mKeys = new HashSet<>();

    /**
     * Remembers the card, which is added unless a card of the same video was
     */
    public synchronized boolean isDuplicate(Video video) {
        String key = getKey(video);

        return key != null && !mKeys.add(key);
    }

    /**
     * A mix or a playlist by its playlist, so it isn't taken for its first video (see Video.equals), a video by its id
     * (also a video of a playlist row). None for the cards without a video (e.g. a channel or "More music") or a chapter.
     */
    static String getKey(Video video) {
        if (video.isChapter || video.videoId == null) {
            return null;
        }

        if (video.playlistId != null && (video.itemType == MediaItem.TYPE_PLAYLIST || video.isMix())) {
            return "playlist:" + video.playlistId;
        }

        return "video:" + video.videoId;
    }
}
