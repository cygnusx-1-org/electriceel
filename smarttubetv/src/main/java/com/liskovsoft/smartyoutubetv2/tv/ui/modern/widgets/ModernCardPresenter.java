package com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernTheme;
import com.liskovsoft.smartyoutubetv2.tv.presenter.CardHold;
import com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.LongClickPresenter;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Binds {@link Video} items to {@link ModernVideoCard}.<br/>
 * Clicks are handled by the host, long press by {@link LongClickPresenter}.
 */
public class ModernCardPresenter extends LongClickPresenter {
    private final int mWidthResId;
    private final int mThumbHeightResId;
    private int mThumbQuality = -1;
    private int mPreviewType;
    private boolean mMultilineTitle;
    private float mTitleScrollSpeed;
    private boolean mTitleAutoScroll;
    private boolean mRoundedCorners;
    private CardHold mHold;
    // The image a card shows: the url asked for, and the one it came from (the original, when that one failed)
    private final Map<View, Pair<String, String>> mShownImages = new WeakHashMap<>();

    public ModernCardPresenter() {
        this(0, 0);
    }

    /**
     * Fixed tile size, for hosts that don't size the cards themselves.
     */
    public ModernCardPresenter(int widthResId, int thumbHeightResId) {
        mWidthResId = widthResId;
        mThumbHeightResId = thumbHeightResId;
    }

    /**
     * As the classic cards (see VideoCardPresenter): a new card shows when its image is in, together with the others bound with it
     */
    public void enableImageHold(CardHold hold) {
        mHold = hold;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();

        if (mThumbQuality == -1) {
            mThumbQuality = MainUIData.instance(context).getThumbQuality();
            mPreviewType = MainUIData.instance(context).getCardPreviewType();
            mMultilineTitle = MainUIData.instance(context).isCardMultilineTitleEnabled();
            mTitleAutoScroll = MainUIData.instance(context).isCardTextAutoScrollEnabled();
            mTitleScrollSpeed = MainUIData.instance(context).getCardTextScrollSpeed();
            mRoundedCorners = MainUIData.instance(context).isUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS);
        }

        ModernVideoCard card = (ModernVideoCard) LayoutInflater.from(context).inflate(R.layout.modern_video_card, parent, false);

        if (mWidthResId != 0) {
            card.setThumbSize(context.getResources().getDimensionPixelSize(mWidthResId),
                    context.getResources().getDimensionPixelSize(mThumbHeightResId));
        }

        return new ViewHolder(card);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        super.onBindViewHolder(viewHolder, item);

        ModernVideoCard card = (ModernVideoCard) viewHolder.view;
        Video video = (Video) item;
        Context context = card.getContext();

        card.setPreviewType(mPreviewType);
        card.setTitleOptions(mMultilineTitle, mTitleAutoScroll, mTitleScrollSpeed);
        card.setRoundedCorners(mRoundedCorners);
        card.bind(video);

        Activity activity = findActivity(context);

        if (activity != null && activity.isDestroyed()) {
            return;
        }

        // The card clips the thumbnail with its outline (API 21+): no costly bitmap rounding per image
        // Electric Eel's min SDK is 24: the outline is always there
        Transformation<Bitmap> shape = new CenterCrop();

        String imageUrl = ClickbaitRemover.updateThumbnail(video, mThumbQuality);

        // Bound again in place (e.g. synced, DeArrow): the image it shows stays, rather than blinking while it's loaded again
        if (isImageShown(card, imageUrl)) {
            return;
        }

        mShownImages.remove(card);

        // A new or recycled card has no image (see onUnbindViewHolder). One bound again in place has it, so it isn't hidden.
        boolean isHeld = mHold != null && card.getThumbView().getDrawable() == null;

        if (isHeld) {
            mHold.hold(card);
        }

        // As the classic cards: each failed url falls back to the next one (a dead one is skipped from then on), the last to none
        List<String> urls = VideoCardPresenter.getImageUrls().get(imageUrl, video.cardImageUrl, video.videoId);
        RequestBuilder<Drawable> request = null;

        for (int i = urls.size() - 1; i >= 0; i--) {
            RequestBuilder<Drawable> fallback = request;

            request = Glide.with(context)
                    .load(urls.get(i))
                    .transform(shape)
                    // Network loads fade in, memory cache hits show at once
                    .transition(DrawableTransitionOptions.withCrossFade(ModernTheme.ANIM_FAST_MS))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .listener(new ImageListener(card, imageUrl, isHeld, fallback == null));

            if (card.getKind() == ModernVideoCard.KIND_PLAYLIST) {
                request = request.addListener(new StackColorListener(card));
            }

            if (fallback != null) {
                request = request.error(fallback);
            }
        }

        request.into(card.getThumbView());
    }

    /**
     * The activity of a context, also through the wrappers (e.g. a theme overlay of a section)
     */
    private static Activity findActivity(Context context) {
        Context current = context;

        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }

        return null;
    }

    private boolean isImageShown(ModernVideoCard card, String imageUrl) {
        Pair<String, String> shown = mShownImages.get(card);

        return imageUrl != null && shown != null && card.getThumbView().getDrawable() != null &&
                (imageUrl.equals(shown.first) || imageUrl.equals(shown.second));
    }

    /**
     * Notes the image the card shows, and shows the held card when it's in. Only the failure of the last url counts.
     */
    private final class ImageListener implements RequestListener<Drawable> {
        private final ModernVideoCard mCard;
        private final String mImageUrl;
        private final boolean mIsHeld;
        private final boolean mIsLast;

        ImageListener(ModernVideoCard card, String imageUrl, boolean isHeld, boolean isLast) {
            mCard = card;
            mImageUrl = imageUrl;
            mIsHeld = isHeld;
            mIsLast = isLast;
        }

        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
            if (model instanceof String && VideoCardPresenter.isNotFound(e)) {
                VideoCardPresenter.getImageUrls().setMissing((String) model);
            }

            if (mIsLast && mIsHeld) {
                mHold.ready(mCard);
            }

            return false;
        }

        @Override
        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
            mShownImages.put(mCard, new Pair<>(mImageUrl, model instanceof String ? (String) model : null));

            if (mIsHeld) {
                mHold.ready(mCard);
            }

            return false;
        }
    }

    /**
     * Playlist stack takes the darkened average color of the thumbnail.
     */
    private static final class StackColorListener implements RequestListener<Drawable> {
        private final ModernVideoCard mCard;

        StackColorListener(ModernVideoCard card) {
            mCard = card;
        }

        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
            return false;
        }

        @Override
        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
            if (resource instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) resource).getBitmap();
                if (bitmap != null && !bitmap.isRecycled()) {
                    Bitmap pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
                    int color = pixel.getPixel(0, 0);
                    float darken = 0.6f;
                    mCard.setStackColor(Color.rgb(Math.round(Color.red(color) * darken),
                            Math.round(Color.green(color) * darken), Math.round(Color.blue(color) * darken)));
                }
            }
            return false;
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        super.onUnbindViewHolder(viewHolder);

        ModernVideoCard card = (ModernVideoCard) viewHolder.view;

        if (mHold != null) {
            mHold.drop(card);
        }

        mShownImages.remove(card);
        Glide.with(card.getContext().getApplicationContext()).clear(card.getThumbView());
        card.getThumbView().setImageDrawable(null);
    }
}
