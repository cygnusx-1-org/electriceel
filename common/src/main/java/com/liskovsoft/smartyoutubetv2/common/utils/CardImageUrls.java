package com.liskovsoft.smartyoutubetv2.common.utils;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The urls a card's image is tried from, in order, and the ones that aren't there (404). Only touched on the main thread (see Glide's listeners).
 */
public final class CardImageUrls {
    // The thumbnail the video has now: there as long as the video is (480x360, 4:3 like the feed's), unlike the url of the feed,
    // which can be gone, e.g. "sddefault_custom_2.jpg?sqp=...", the variant of a thumbnail test that ended
    private static final String MAIN_THUMB_URL = "https://i.ytimg.com/vi/%s/hqdefault.jpg";
    private final Set<String> mMissingUrls = new HashSet<>();

    /**
     * @param imageUrl the url asked for (e.g. DeArrow's, a short's vertical thumbnail, a frame)
     * @param originalUrl the url of the feed
     * @param videoId of the video, null for a channel, a chapter...
     * @return each url once, the ones known to be missing left out but the last, so there's always one (null when there's none at all)
     */
    public List<String> get(@Nullable String imageUrl, @Nullable String originalUrl, @Nullable String videoId) {
        List<String> urls = new ArrayList<>();

        for (String url : new String[] {imageUrl, originalUrl, videoId != null ? MAIN_THUMB_URL.replace("%s", videoId) : null}) {
            if (url != null && !urls.contains(url)) {
                urls.add(url);
            }
        }

        // A card bound again doesn't wait for the ones that 404. The last is tried anyway: it's the one the card ends with.
        for (int i = urls.size() - 2; i >= 0; i--) {
            if (mMissingUrls.contains(urls.get(i))) {
                urls.remove(i);
            }
        }

        if (urls.isEmpty()) {
            urls.add(null); // Glide shows the error image
        }

        return urls;
    }

    /**
     * The url isn't there (404): it's left out from now on
     */
    public void setMissing(String url) {
        mMissingUrls.add(url);
    }
}
