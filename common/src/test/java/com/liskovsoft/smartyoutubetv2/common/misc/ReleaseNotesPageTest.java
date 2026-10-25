package com.liskovsoft.smartyoutubetv2.common.misc;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ReleaseNotesPageTest {
    @Test
    public void build_hasTheNotesAsGitHubRenderedThem() {
        String body = "<h1>Screenshots</h1><p><a href=\"/o/r/blob/1.0/a.png\"><img src=\"/o/r/raw/1.0/a.png\"></a></p>";
        String page = ReleaseNotesPage.build("1.0", "October 25, 2026", body, 0xFFFFFFFF, 0xFF343434);

        assertTrue(page.contains("<div class=\"markdown-body\">" + body + "</div>"));
        assertTrue(page.contains("<h1 class=\"release-title\">1.0</h1>"));
        assertTrue(page.contains("<div class=\"release-date\">October 25, 2026</div>"));
    }

    /**
     * The title and the date are text, the notes are HTML already
     */
    @Test
    public void build_escapesTheTitleAndDate() {
        String page = ReleaseNotesPage.build("<b>1.0</b>", "a & b", "", 0xFFFFFFFF, 0xFF343434);

        assertTrue(page.contains("&lt;b&gt;1.0&lt;/b&gt;"));
        assertTrue(page.contains("a &amp; b"));
        assertFalse(page.contains("<b>1.0</b>"));
    }

    @Test
    public void build_focusedLinksHaveTheColorsOfTheApp() {
        String page = ReleaseNotesPage.build("1.0", "", "", 0xFF112233, 0x80445566);

        assertTrue(page.contains("a:focus{background:rgba(17,34,51,1.000);color:rgba(68,85,102,0.502)}"));
        assertTrue(page.contains("a.image-link:focus{background:rgba(17,34,51,1.000)}"));
        assertFalse(page.contains("{focused"));
    }

    /**
     * The style has percentages (e.g. 100%): they stay as they are
     */
    @Test
    public void build_keepsThePercentagesOfTheStyle() {
        String page = ReleaseNotesPage.build("1.0", "", "", 0xFFFFFFFF, 0xFF343434);

        assertTrue(page.contains("img{max-width:100%;"));
        assertTrue(page.contains("font-size:85%;"));
    }

    @Test
    public void getImageIndex_ofTheImageLinks() {
        assertEquals(0, ReleaseNotesPage.getImageIndex(ReleaseNotesPage.IMAGE_SCHEME + "0"));
        assertEquals(12, ReleaseNotesPage.getImageIndex(ReleaseNotesPage.IMAGE_SCHEME + "12"));
    }

    @Test
    public void getImageIndex_ofOtherLinksIsNone() {
        assertEquals(-1, ReleaseNotesPage.getImageIndex(null));
        assertEquals(-1, ReleaseNotesPage.getImageIndex("https://github.com/cygnusx-1-org/electriceel/issues/50"));
        assertEquals(-1, ReleaseNotesPage.getImageIndex(ReleaseNotesPage.IMAGE_SCHEME));
        assertEquals(-1, ReleaseNotesPage.getImageIndex(ReleaseNotesPage.IMAGE_SCHEME + "x"));
    }

    /**
     * The links of the pictures are made by the page's script with the same scheme
     */
    @Test
    public void build_imageLinksUseTheScheme() {
        String page = ReleaseNotesPage.build("1.0", "", "", 0xFFFFFFFF, 0xFF343434);

        assertTrue(page.contains("'" + ReleaseNotesPage.IMAGE_SCHEME + "'+i"));
    }

    /**
     * Up and down without a link to go to: a third of the screen, not the WebView's small step
     */
    @Test
    public void build_scrollsAThirdOfTheScreen() {
        String page = ReleaseNotesPage.build("1.0", "", "", 0xFFFFFFFF, 0xFF343434);

        assertTrue(page.contains("e.preventDefault();window.scrollBy(0,(down?1:-1)*Math.round(height/3));"));
    }

    @Test
    public void toCss_isRgba() {
        assertEquals("rgba(255,255,255,1.000)", ReleaseNotesPage.toCss(0xFFFFFFFF));
        assertEquals("rgba(52,52,52,0.000)", ReleaseNotesPage.toCss(0x00343434));
        assertEquals("rgba(0,0,255,0.502)", ReleaseNotesPage.toCss(0x800000FF));
    }

    /**
     * The WebView of an old TV may not know #RRGGBBAA (Chrome 62+)
     */
    @Test
    public void build_hasNoEightDigitColors() {
        String page = ReleaseNotesPage.build("1.0", "", "", 0xFFFFFFFF, 0xFF343434);

        assertFalse(page.matches("(?s).*#[0-9a-fA-F]{8}\\b.*"));
    }
}
