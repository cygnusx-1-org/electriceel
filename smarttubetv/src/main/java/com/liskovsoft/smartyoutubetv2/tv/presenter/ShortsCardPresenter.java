package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.content.Context;
import android.util.Pair;
import androidx.annotation.Nullable;
import com.bumptech.glide.load.HttpException;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.GridFragmentHelper;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShortsCardPresenter extends VideoCardPresenter {
    // A landscape thumbnail of YouTube's (e.g. "sd2.jpg" of a short of a playlist: 4:3 with black sides),
    // or the first frame of the video, which the shorts of the Shorts section come with rather than the thumbnail (see BrowseService2.getShortsWeb)
    private static final Pattern REPLACED_THUMB =
            Pattern.compile("^(https://i\\.ytimg\\.com/vi/[^/]+/)(?:(?:sd|hq|mq)(?:default|[123])|hq720|frame0)\\.jpg(?:\\?.*)?$");
    // The vertical thumbnail in full size (e.g. 1080x1920): only the urls YouTube signs are resized
    private static final String ORIGINAL_THUMB = "oardefault.jpg";
    // The vertical thumbnails that aren't there (e.g. of a sixth of the shorts of the Shorts section): their cards show the original
    // straight away from then on, rather than after it's asked for again each time. Only touched on the main thread (see Glide's listeners).
    private static final Set<String> sMissingThumbs = new HashSet<>();

    /**
     * The vertical thumbnail of the short rather than a landscape one or a frame, unless frames are picked as thumbnails (see ClickbaitRemover).
     * When it doesn't load, the card shows the original.
     */
    @Override
    protected String getCardImageUrl(Video video, int thumbQuality) {
        String url = super.getCardImageUrl(video, thumbQuality);
        Matcher matcher = url != null && thumbQuality == ClickbaitRemover.THUMB_QUALITY_DEFAULT ? REPLACED_THUMB.matcher(url) : null;
        String thumbUrl = matcher != null && matcher.matches() ? matcher.group(1) + ORIGINAL_THUMB : null;

        return thumbUrl != null && !sMissingThumbs.contains(thumbUrl) ? thumbUrl : url;
    }

    @Override
    protected void onCardImageFailed(@Nullable GlideException e, Object model) {
        if (model instanceof String && ((String) model).endsWith("/" + ORIGINAL_THUMB) && isNotFound(e)) {
            sMissingThumbs.add((String) model);
        }
    }

    private static boolean isNotFound(@Nullable GlideException e) {
        if (e == null) {
            return false;
        }

        for (Throwable cause : e.getRootCauses()) {
            if (cause instanceof HttpException && ((HttpException) cause).getStatusCode() == 404) {
                return true;
            }
        }

        return false;
    }

    /**
     * Only the card's size of the full size thumbnail (100 KB to 1 MB) is kept, so it doesn't push the other images out of the small cache
     */
    @Override
    protected DiskCacheStrategy getDiskCacheStrategy(String url) {
        return url != null && url.endsWith("/" + ORIGINAL_THUMB) ? DiskCacheStrategy.RESOURCE : super.getDiskCacheStrategy(url);
    }

    @Override
    protected Pair<Integer, Integer> getCardDimensPx(Context context) {
        return GridFragmentHelper.getCardDimensPx(context, R.dimen.shorts_card_width, R.dimen.shorts_card_height, MainUIData.instance(context).getVideoGridScale());
    }
}
