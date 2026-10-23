package com.liskovsoft.smartyoutubetv2.common.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ClickbaitRemoverTest {
    private static final String QUERY = "?sqp=CKCM79YG&rs=AOn4CLBRCdnyZy9_n0VFUz7cfleOu3J-BQ";

    @Test
    public void framesReplaceTheThumbnail() {
        assertEquals("https://i.ytimg.com/vi/fHY0sjAejcA/hq1.jpg" + QUERY,
                ClickbaitRemover.updateThumbnail("https://i.ytimg.com/vi/fHY0sjAejcA/sddefault.jpg" + QUERY, ClickbaitRemover.THUMB_QUALITY_START));
        assertEquals("https://i.ytimg.com/vi/fHY0sjAejcA/hq3.jpg",
                ClickbaitRemover.updateThumbnail("https://i.ytimg.com/vi/fHY0sjAejcA/hqdefault.jpg", ClickbaitRemover.THUMB_QUALITY_END));
    }

    /**
     * The variant of a thumbnail test (gone when the test ends) is replaced too
     */
    @Test
    public void framesReplaceTheVariantOfAThumbnailTest() {
        assertEquals("https://i.ytimg.com/vi/fHY0sjAejcA/hq2.jpg" + QUERY,
                ClickbaitRemover.updateThumbnail("https://i.ytimg.com/vi/fHY0sjAejcA/sddefault_custom_2.jpg" + QUERY, ClickbaitRemover.THUMB_QUALITY_MIDDLE));
    }

    @Test
    public void theDefaultQualityKeepsTheUrl() {
        String url = "https://i.ytimg.com/vi/fHY0sjAejcA/sddefault_custom_2.jpg" + QUERY;

        assertEquals(url, ClickbaitRemover.updateThumbnail(url, ClickbaitRemover.THUMB_QUALITY_DEFAULT));
    }
}
