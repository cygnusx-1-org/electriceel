package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.content.Context;
import android.util.Pair;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.GridFragmentHelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShortsCardPresenter extends VideoCardPresenter {
    // A landscape thumbnail of YouTube's (e.g. "sd2.jpg" of a short of a playlist: 4:3 with black sides)
    private static final Pattern LANDSCAPE_THUMB =
            Pattern.compile("^(https://i\\.ytimg\\.com/vi/[^/]+/)(?:(?:sd|hq|mq)(?:default|[123])|hq720)\\.jpg(?:\\?.*)?$");

    /**
     * The vertical thumbnail of the short rather than a landscape one, unless frames are picked as thumbnails (see ClickbaitRemover).
     * When it doesn't load, the card shows the original.
     */
    @Override
    protected String getCardImageUrl(Video video, int thumbQuality) {
        String url = super.getCardImageUrl(video, thumbQuality);
        Matcher matcher = url != null && thumbQuality == ClickbaitRemover.THUMB_QUALITY_DEFAULT ? LANDSCAPE_THUMB.matcher(url) : null;

        return matcher != null && matcher.matches() ? matcher.group(1) + "oardefault.jpg" : url;
    }

    @Override
    protected Pair<Integer, Integer> getCardDimensPx(Context context) {
        return GridFragmentHelper.getCardDimensPx(context, R.dimen.shorts_card_width, R.dimen.shorts_card_height, MainUIData.instance(context).getVideoGridScale());
    }
}
