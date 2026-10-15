package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.ImageView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.liskovsoft.smartyoutubetv2.common.utils.QrCodeEncoder;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * A QR code with its title above it. Nothing to press.
 */
public class QrCodePreference extends Preference {
    private String mText;
    private Bitmap mCode;

    public QrCodePreference(Context context) {
        super(context);

        setLayoutResource(R.layout.qr_code_preference);
        setSelectable(false);
    }

    /**
     * Made into the code on the device, with the row: no loading
     */
    public void setQrCodeText(String text) {
        mText = text;
        mCode = null;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        if (mCode == null && mText != null) {
            // Exactly the size of the view: a whole number of pixels per module, nothing scaled
            mCode = QrCodeEncoder.toBitmap(mText, getContext().getResources().getDimensionPixelSize(R.dimen.qr_code_preference_size));
        }

        ImageView imageView = (ImageView) holder.findViewById(R.id.qr_code_preference_image);
        imageView.setImageBitmap(mCode);
    }
}
