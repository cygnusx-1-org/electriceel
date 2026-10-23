package com.liskovsoft.smartyoutubetv2.common.utils;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class CardImageUrlsTest {
    // The url of the feed of a video in a thumbnail test that ended: gone
    private static final String VARIANT_URL = "https://i.ytimg.com/vi/fHY0sjAejcA/sddefault_custom_2.jpg?sqp=CKCM79YG&rs=AOn4CLBRCdnyZy9_n0VFUz7cfleOu3J-BQ";
    private static final String MAIN_THUMB_URL = "https://i.ytimg.com/vi/fHY0sjAejcA/hqdefault.jpg";
    private static final String DEARROW_URL = "https://dearrow-thumb.ajay.app/api/v1/getThumbnail?videoID=fHY0sjAejcA&time=12.5";

    @Test
    public void theMainThumbnailIsTheLastResort() {
        CardImageUrls urls = new CardImageUrls();

        assertEquals(Arrays.asList(VARIANT_URL, MAIN_THUMB_URL), urls.get(VARIANT_URL, VARIANT_URL, "fHY0sjAejcA"));
        assertEquals(Arrays.asList(DEARROW_URL, VARIANT_URL, MAIN_THUMB_URL), urls.get(DEARROW_URL, VARIANT_URL, "fHY0sjAejcA"));
    }

    /**
     * The url of the feed is the main thumbnail already (e.g. "hqdefault.jpg" of the WEB client): it isn't asked for twice
     */
    @Test
    public void eachUrlIsTriedOnce() {
        CardImageUrls urls = new CardImageUrls();

        assertEquals(Collections.singletonList(MAIN_THUMB_URL), urls.get(MAIN_THUMB_URL, MAIN_THUMB_URL, "fHY0sjAejcA"));
    }

    /**
     * A channel, a chapter, a group has no video: no main thumbnail
     */
    @Test
    public void noVideoNoMainThumbnail() {
        CardImageUrls urls = new CardImageUrls();
        String iconUrl = "https://yt3.ggpht.com/ytc/channel-icon=s176-c-k-c0x00ffffff-no-rj";

        assertEquals(Collections.singletonList(iconUrl), urls.get(iconUrl, iconUrl, null));
    }

    @Test
    public void theMissingOnesAreLeftOut() {
        CardImageUrls urls = new CardImageUrls();

        urls.setMissing(VARIANT_URL);

        assertEquals(Collections.singletonList(MAIN_THUMB_URL), urls.get(VARIANT_URL, VARIANT_URL, "fHY0sjAejcA"));
        assertEquals(Arrays.asList(DEARROW_URL, MAIN_THUMB_URL), urls.get(DEARROW_URL, VARIANT_URL, "fHY0sjAejcA"));
    }

    /**
     * Even when it's missing too (e.g. a video taken down), the card ends with the last one: there's always a url to load
     */
    @Test
    public void theLastIsTriedAnyway() {
        CardImageUrls urls = new CardImageUrls();

        urls.setMissing(VARIANT_URL);
        urls.setMissing(MAIN_THUMB_URL);

        assertEquals(Collections.singletonList(MAIN_THUMB_URL), urls.get(VARIANT_URL, VARIANT_URL, "fHY0sjAejcA"));
    }

    /**
     * Nothing to load: a null, so Glide shows the placeholder
     */
    @Test
    public void noUrlAtAll() {
        assertEquals(Collections.singletonList(null), new CardImageUrls().get(null, null, null));
    }
}
