package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.content.Context;
import android.widget.ImageView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * A picture with its title above it, e.g. a QR code. Nothing to press.
 */
public class ImagePreference extends Preference {
    private int mImageResId;

    public ImagePreference(Context context) {
        super(context);

        setLayoutResource(R.layout.image_preference);
        setSelectable(false);
    }

    /**
     * A drawable, drawn with the row: no loading
     */
    public void setImageResource(int imageResId) {
        mImageResId = imageResId;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ImageView imageView = (ImageView) holder.findViewById(R.id.image_preference_image);
        imageView.setImageResource(mImageResId);
    }
}
