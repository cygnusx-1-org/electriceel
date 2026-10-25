package com.liskovsoft.smartyoutubetv2.common.misc;

import android.text.TextUtils;

import java.util.Locale;

/**
 * The page of a release's notes (see GitHubReleases): GitHub's HTML of them, styled as GitHub's dark theme.
 * A picture is a link that opens it on its own, on the whole screen: {@link #IMAGE_SCHEME} and its number,
 * shown with {@link #showImage} and hidden with {@link #hideImage}.
 */
public final class ReleaseNotesPage {
    /**
     * Of the addresses relative to github.com in the notes, e.g. the images of the repo (/owner/repo/raw/tag/image.png)
     */
    public static final String BASE_URL = "https://github.com/";
    /**
     * The link of a picture: this, then its number in the page
     */
    public static final String IMAGE_SCHEME = "electriceel-image:";

    private static final String FOCUSED_BACKGROUND = "{focusedBackground}";
    private static final String FOCUSED_TEXT = "{focusedText}";
    /**
     * GitHub's dark theme (github-markdown-css). The background is the app's.
     */
    private static final String STYLE =
            "html{-webkit-text-size-adjust:100%}" +
            "body{margin:0;padding:27px 48px;background:transparent;color:#f0f6fc;" +
                    "font-family:sans-serif;font-size:16px;line-height:1.5;word-wrap:break-word}" +
            // Not the rule under the h1 of the notes: the date has the one under the title
            ".release-title{margin:0 0 4px;padding-bottom:0;border-bottom:0;font-size:2em;font-weight:600;line-height:1.25}" +
            ".release-date{margin-bottom:16px;padding-bottom:16px;border-bottom:1px solid rgba(61,68,77,0.702);color:#9198a1;font-size:14px}" +
            ".markdown-body>*:first-child{margin-top:0!important}" +
            ".markdown-body>*:last-child{margin-bottom:0!important}" +
            // The padding is there unfocused too: a link chosen with the remote only changes its colors
            "a{color:#4493f8;text-decoration:none;padding:0 2px;margin:0 -2px;border-radius:4px;outline:none}" +
            "a:focus{background:" + FOCUSED_BACKGROUND + ";color:" + FOCUSED_TEXT + "}" +
            "a.image-link{display:inline-block;padding:4px;margin:-4px;border-radius:6px;vertical-align:top}" +
            "a.image-link:focus{background:" + FOCUSED_BACKGROUND + "}" +
            "a.anchor{display:none}" +
            "h1,h2,h3,h4,h5,h6{margin-top:24px;margin-bottom:16px;font-weight:600;line-height:1.25}" +
            "h1{font-size:2em;padding-bottom:.3em;border-bottom:1px solid rgba(61,68,77,0.702)}" +
            "h2{font-size:1.5em;padding-bottom:.3em;border-bottom:1px solid rgba(61,68,77,0.702)}" +
            "h3{font-size:1.25em}h4{font-size:1em}h5{font-size:.875em}h6{font-size:.85em;color:#9198a1}" +
            "p,blockquote,ul,ol,dl,table,pre,details{margin-top:0;margin-bottom:16px}" +
            "ul,ol{padding-left:2em}ul ul,ul ol,ol ol,ol ul{margin-top:0;margin-bottom:0}" +
            "li+li{margin-top:.25em}li>p{margin-top:16px}" +
            ".task-list-item{list-style-type:none}.task-list-item input{margin:0 .2em .25em -1.4em;vertical-align:middle}" +
            "strong,b{font-weight:600}" +
            "code,kbd,pre,tt,samp{font-family:monospace}" +
            "code,tt{padding:.2em .4em;margin:0;font-size:85%;white-space:break-spaces;background:rgba(101,108,118,0.2);border-radius:6px}" +
            "pre{padding:16px;overflow:auto;font-size:85%;line-height:1.45;background:rgba(255,255,255,0.039);border-radius:6px}" +
            "pre code,pre tt{padding:0;margin:0;font-size:100%;white-space:pre;background:transparent;border:0}" +
            "kbd{display:inline-block;padding:3px 5px;font-size:11px;line-height:10px;vertical-align:middle;" +
                    "background:rgba(255,255,255,0.039);border:1px solid rgba(61,68,77,0.702);border-bottom-color:rgba(61,68,77,0.702);border-radius:6px}" +
            "blockquote{margin-left:0;margin-right:0;padding:0 1em;color:#9198a1;border-left:.25em solid #3d444d}" +
            "hr{height:.25em;padding:0;margin:24px 0;background:#3d444d;border:0}" +
            "table{display:block;width:max-content;max-width:100%;overflow:auto;border-spacing:0;border-collapse:collapse}" +
            "td,th{padding:6px 13px;border:1px solid #3d444d}th{font-weight:600}" +
            "tr{border-top:1px solid rgba(61,68,77,0.702)}tr:nth-child(2n){background:rgba(255,255,255,0.039)}" +
            "img{max-width:100%;box-sizing:content-box;border-style:none;vertical-align:top}" +
            "del{color:#9198a1}" +
            "mark{background:rgba(187,128,9,0.149);color:#f0f6fc}" +
            // The title of a collapsed section is chosen with the remote as a link is
            "details summary{cursor:pointer;border-radius:4px;outline:none}" +
            "details summary:focus{background:" + FOCUSED_BACKGROUND + ";color:" + FOCUSED_TEXT + "}" +
            ".octicon{display:inline-block;overflow:visible!important;vertical-align:text-bottom;fill:currentColor}" +
            ".markdown-alert{padding:8px 16px;margin-bottom:16px;border-left:.25em solid #3d444d}" +
            ".markdown-alert>:last-child{margin-bottom:0}" +
            ".markdown-alert-title{display:flex;align-items:center;font-weight:500;line-height:1}" +
            ".markdown-alert-title .octicon{margin-right:8px}" +
            ".markdown-alert-note{border-left-color:#1f6feb}.markdown-alert-note .markdown-alert-title{color:#4493f8}" +
            ".markdown-alert-tip{border-left-color:#238636}.markdown-alert-tip .markdown-alert-title{color:#3fb950}" +
            ".markdown-alert-important{border-left-color:#8957e5}.markdown-alert-important .markdown-alert-title{color:#ab7df8}" +
            ".markdown-alert-warning{border-left-color:#9e6a03}.markdown-alert-warning .markdown-alert-title{color:#d29922}" +
            ".markdown-alert-caution{border-left-color:#da3633}.markdown-alert-caution .markdown-alert-title{color:#f85149}" +
            ".user-mention,.team-mention{font-weight:600;white-space:nowrap}" +
            "#viewer{display:none;position:fixed;top:0;right:0;bottom:0;left:0;z-index:100;background:#000;" +
                    "align-items:center;justify-content:center}" +
            "#viewer.shown{display:flex}" +
            "#viewer img{max-width:100%;max-height:100%;object-fit:contain}";

    /**
     * Of the screen, a press of the remote up or down with no link to go to scrolls this much. The WebView's own step is
     * about a 14th.
     */
    private static final int SCROLL_FRACTION = 3;

    /**
     * Every picture is a link to it on its own: in its link already (GitHub links them), or in one made for it.
     * Not GitHub's emoji pictures (e.g. :shipit:): they're text. The other links stay as they are.
     * Up and down go to the next link (or another thing to choose) on the screen, as the WebView does on a TV. Without
     * one they scroll by {@link #SCROLL_FRACTION}: from the link chosen, or from the edge of the screen once it's
     * scrolled out of it.
     */
    private static final String SCRIPT =
            "(function(){" +
            "var images=document.querySelectorAll('.markdown-body img:not(.emoji)');" +
            "for(var i=0;i<images.length;i++){" +
                "var image=images[i];" +
                "var link=image.closest('a');" +
                "if(!link){link=document.createElement('a');image.parentNode.insertBefore(link,image);link.appendChild(image);}" +
                "link.classList.add('image-link');" +
                "link.removeAttribute('target');" +
                "link.setAttribute('href','" + IMAGE_SCHEME + "'+i);" +
            "}" +
            "var viewer=document.getElementById('viewer');" +
            "var viewerImage=document.getElementById('viewer-image');" +
            "window.eeShowImage=function(i){" +
                "var image=images[i];" +
                "if(!image){return false;}" +
                "viewerImage.src=image.currentSrc||image.src;" +
                "viewerImage.alt=image.alt;" +
                "viewer.classList.add('shown');" +
                "return true;" +
            "};" +
            "window.eeHideImage=function(){" +
                "viewer.classList.remove('shown');" +
                "viewerImage.removeAttribute('src');" +
            "};" +
            "document.addEventListener('keydown',function(e){" +
                "var down=e.key==='ArrowDown';" +
                "if((!down&&e.key!=='ArrowUp')||e.defaultPrevented){return;}" +
                "var height=window.innerHeight;" +
                "var focused=document.activeElement;" +
                "var from=down?0:height;" +
                "if(focused&&focused!==document.body){" +
                    "var rect=focused.getBoundingClientRect();" +
                    "if(rect.bottom>0&&rect.top<height){from=down?rect.bottom:rect.top;}" +
                "}" +
                // What the WebView can go to: e.g. the title of a collapsed section (<details>), a code block wider than
                // the screen (scrolled left and right)
                "var targets=document.querySelectorAll('a[href],summary,button,input,select,textarea,video[controls],audio[controls],[tabindex],pre,table');" +
                "for(var j=0;j<targets.length;j++){" +
                    "var target=targets[j];" +
                    "if(target===focused||target.disabled||target.getAttribute('tabindex')==='-1'||!target.getClientRects().length){continue;}" +
                    "if((target.tagName==='PRE'||target.tagName==='TABLE')&&target.scrollWidth<=target.clientWidth){continue;}" +
                    "var box=target.getBoundingClientRect();" +
                    "if(box.bottom<=0||box.top>=height){continue;}" +
                    "if(down?box.top>=from-1:box.bottom<=from+1){return;}" +
                "}" +
                "e.preventDefault();" +
                "window.scrollBy(0,(down?1:-1)*Math.round(height/" + SCROLL_FRACTION + "));" +
            "});" +
            "})();";

    private ReleaseNotesPage() {
    }

    /**
     * @param focusedBackgroundColor of a link chosen with the remote, e.g. white as the cards of the app
     * @param focusedTextColor of the text of a link chosen with the remote
     */
    public static String build(String title, String date, String bodyHtml, int focusedBackgroundColor, int focusedTextColor) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                "<style>" + STYLE.replace(FOCUSED_BACKGROUND, toCss(focusedBackgroundColor)).replace(FOCUSED_TEXT, toCss(focusedTextColor)) + "</style>" +
                "</head><body>" +
                "<h1 class=\"release-title\">" + TextUtils.htmlEncode(title) + "</h1>" +
                "<div class=\"release-date\">" + TextUtils.htmlEncode(date) + "</div>" +
                "<div class=\"markdown-body\">" + bodyHtml + "</div>" +
                "<div id=\"viewer\"><img id=\"viewer-image\"></div>" +
                "<script>" + SCRIPT + "</script>" +
                "</body></html>";
    }

    /**
     * @return the picture of a link of the page, -1 for another link
     */
    public static int getImageIndex(String url) {
        if (url == null || !url.startsWith(IMAGE_SCHEME)) {
            return -1;
        }

        try {
            return Integer.parseInt(url.substring(IMAGE_SCHEME.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * The script that shows a picture on the whole screen. It's false when there's no such picture.
     */
    public static String showImage(int index) {
        return "eeShowImage(" + index + ")";
    }

    public static String hideImage() {
        return "eeHideImage()";
    }

    /**
     * rgba(): the WebView of an old TV may not know #RRGGBBAA (Chrome 62+)
     */
    static String toCss(int color) {
        return String.format(Locale.US, "rgba(%d,%d,%d,%.3f)",
                (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, ((color >>> 24) & 0xFF) / 255f);
    }
}
