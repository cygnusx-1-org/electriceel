package com.liskovsoft.smartyoutubetv2.common.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * A QR code made on the device (e.g. About, Share link (QR code)): there at once, offline too
 */
public final class QrCodeEncoder {
    /**
     * Modules of white around the code at least, like the codes of api.qrserver.com
     */
    private static final int MIN_QUIET_ZONE = 1;

    private QrCodeEncoder() {
    }

    /**
     * Error correction L, like api.qrserver.com: the fewest modules, so the largest ones
     *
     * @return the modules without any white around them, or null if there's no text or it doesn't fit in a QR code
     */
    @Nullable
    static BitMatrix encode(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        hints.put(EncodeHintType.MARGIN, 0);

        try {
            // Zero width and height: a pixel per module
            return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);
        } catch (WriterException e) { // too long
            return null;
        }
    }

    /**
     * A white square of sizePx with the code in the middle, as large as fits with a whole number of pixels per module:
     * sharp, nothing scaled
     *
     * @return null if there's no text, it doesn't fit in a QR code, or the code doesn't fit in sizePx
     */
    @Nullable
    public static Bitmap toBitmap(@Nullable String text, int sizePx) {
        BitMatrix modules = encode(text);

        if (modules == null) {
            return null;
        }

        int moduleCount = modules.getWidth();
        int modulePx = sizePx / (moduleCount + MIN_QUIET_ZONE * 2);

        if (modulePx < 1) {
            return null;
        }

        int offsetPx = (sizePx - moduleCount * modulePx) / 2;
        int[] pixels = new int[sizePx * sizePx];
        Arrays.fill(pixels, Color.WHITE);

        for (int y = 0; y < moduleCount; y++) {
            for (int x = 0; x < moduleCount; x++) {
                if (modules.get(x, y)) {
                    fillModule(pixels, sizePx, offsetPx + x * modulePx, offsetPx + y * modulePx, modulePx);
                }
            }
        }

        return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888);
    }

    private static void fillModule(int[] pixels, int sizePx, int left, int top, int modulePx) {
        for (int y = top; y < top + modulePx; y++) {
            Arrays.fill(pixels, y * sizePx + left, y * sizePx + left + modulePx, Color.BLACK);
        }
    }
}
