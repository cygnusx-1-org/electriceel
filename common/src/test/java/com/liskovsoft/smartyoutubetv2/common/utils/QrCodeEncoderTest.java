package com.liskovsoft.smartyoutubetv2.common.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.decoder.Decoder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class QrCodeEncoderTest {
    private static final String SHARE_URL = "https://youtu.be/dQw4w9WgXcQ?t=42";
    private static final String FEEDBACK_URL = "https://github.com/cygnusx-1-org/electriceel/issues";
    private static final int SIZE_PX = 500;

    @Test
    public void codeHasTheText() throws Exception {
        assertEquals(SHARE_URL, new Decoder().decode(QrCodeEncoder.encode(SHARE_URL)).getText());
    }

    /**
     * Error correction L, like api.qrserver.com: About's feedback address is version 3 (29 modules), as it was
     * pre-rendered. With M it would be version 4.
     */
    @Test
    public void fewestModules() {
        assertEquals(29, QrCodeEncoder.encode(FEEDBACK_URL).getWidth());
    }

    @Test
    public void bitmapReadsBack() throws Exception {
        Bitmap bitmap = QrCodeEncoder.toBitmap(SHARE_URL, SIZE_PX);

        assertNotNull(bitmap);
        assertEquals(SIZE_PX, bitmap.getWidth());
        assertEquals(SIZE_PX, bitmap.getHeight());

        int[] pixels = new int[SIZE_PX * SIZE_PX];
        bitmap.getPixels(pixels, 0, SIZE_PX, 0, 0, SIZE_PX, SIZE_PX);
        BinaryBitmap image = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(SIZE_PX, SIZE_PX, pixels)));

        assertEquals(SHARE_URL, new QRCodeReader().decode(image).getText());
    }

    /**
     * 29 modules and 1 of white on each side in 500 pixels: 16 pixels a module, the code 464 pixels, 18 of white before it.
     * The finder pattern in the corner is a dark module, then a white one.
     */
    @Test
    public void modulesAreWholePixelsInTheMiddle() {
        Bitmap bitmap = QrCodeEncoder.toBitmap(SHARE_URL, SIZE_PX);

        assertNotNull(bitmap);
        assertEquals(Color.WHITE, bitmap.getPixel(17, 17));
        assertEquals(Color.BLACK, bitmap.getPixel(18, 18));
        assertEquals(Color.BLACK, bitmap.getPixel(18 + 15, 18 + 15));
        assertEquals(Color.WHITE, bitmap.getPixel(18 + 16, 18 + 16));
        assertEquals(Color.WHITE, bitmap.getPixel(SIZE_PX - 18, SIZE_PX - 18));
        assertEquals(Color.WHITE, bitmap.getPixel(SIZE_PX - 1, SIZE_PX - 1));
    }

    /**
     * Each code is the same size, whatever the text: it stays in place
     */
    @Test
    public void sameSizeForAnyText() {
        assertEquals(SIZE_PX, QrCodeEncoder.toBitmap("https://youtu.be/dQw4w9WgXcQ?t=0", SIZE_PX).getWidth());
        assertEquals(SIZE_PX, QrCodeEncoder.toBitmap(FEEDBACK_URL + "?q=" + repeat('a', 200), SIZE_PX).getWidth());
    }

    @Test
    public void tooLongForACode() {
        assertNull(QrCodeEncoder.toBitmap(repeat('a', 3000), SIZE_PX));
    }

    /**
     * 31 modules with the white don't fit in 30 pixels
     */
    @Test
    public void tooSmallForTheCode() {
        assertNull(QrCodeEncoder.toBitmap(SHARE_URL, 30));
    }

    @Test
    public void emptyHasNoCode() {
        assertNull(QrCodeEncoder.toBitmap("", SIZE_PX));
    }

    /**
     * E.g. a sign-in error: no address
     */
    @Test
    public void nullHasNoCode() {
        assertNull(QrCodeEncoder.toBitmap(null, SIZE_PX));
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
