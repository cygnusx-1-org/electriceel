package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.content.Context;
import android.widget.ImageView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

/**
 * A picture with its title above it, e.g. a QR code. Nothing to press.
 */
public class ImagePreference extends Preference {
    private String mImageUrl;

    public ImagePreference(Context context) {
        super(context);

        setLayoutResource(R.layout.image_preference);
        setSelectable(false);
    }

    public void setImageUrl(String imageUrl) {
        mImageUrl = imageUrl;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ImageView imageView = (ImageView) holder.findViewById(R.id.image_preference_image);

        // The view keeps its size while the picture loads
        Glide.with(getContext())
                .load(mImageUrl)
                .apply(ViewUtil.glideOptions())
                .into(imageView);
    }
}
