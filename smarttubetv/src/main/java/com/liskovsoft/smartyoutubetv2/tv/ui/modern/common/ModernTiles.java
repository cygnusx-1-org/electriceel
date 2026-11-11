package com.liskovsoft.smartyoutubetv2.tv.ui.modern.common;

import android.content.Context;
import android.content.res.Resources;

import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Tile sizes of the browse pages, scaled by the "Video grid scale" setting (UI scale settings).<br/>
 * In the hybrid skin a shelf tile is as wide as a classic card ({@code card_width}) at the same setting: the modern dimens
 * are scaled down to it, the channel tiles keeping their proportion to the shelf tile. A Shorts tile is the size of a classic
 * Shorts card there (see {@link #shortsWidth}).
 */
public final class ModernTiles {
    private ModernTiles() {
    }

    public static float scale(Context context) {
        float gridScale = gridScale(context);

        if (!ModernStyle.isHybrid()) {
            return gridScale;
        }

        Resources res = context.getResources();
        float classic = res.getDimension(R.dimen.card_width) / res.getDimension(R.dimen.modern_tile_width_row);
        return gridScale * classic;
    }

    /**
     * A tile dimension (width, thumbnail height, channel size) multiplied by the grid scale.
     */
    public static int size(Context context, int dimenResId) {
        return Math.round(context.getResources().getDimensionPixelSize(dimenResId) * scale(context));
    }

    /**
     * The width of a Shorts tile: in the hybrid skin the one of a classic Shorts card ({@code shorts_card_width}) at the same
     * Video grid scale, else the modern one.
     */
    public static int shortsWidth(Context context) {
        return shortsSize(context, R.dimen.shorts_card_width, R.dimen.modern_shorts_tile_width);
    }

    /**
     * The thumbnail height of a Shorts tile (see {@link #shortsWidth}).
     */
    public static int shortsHeight(Context context) {
        return shortsSize(context, R.dimen.shorts_card_height, R.dimen.modern_shorts_tile_height);
    }

    private static int shortsSize(Context context, int classicResId, int modernResId) {
        if (!ModernStyle.isHybrid()) {
            return size(context, modernResId);
        }

        return Math.round(context.getResources().getDimensionPixelSize(classicResId) * gridScale(context));
    }

    private static float gridScale(Context context) {
        float scale = MainUIData.instance(context).getVideoGridScale();
        return scale > 0 ? scale : 1f;
    }

    /**
     * How many whole tiles fit in the available width.
     */
    public static int columns(int availableWidth, int tileWidth, int gap) {
        return Math.max(1, (availableWidth + gap) / (tileWidth + gap));
    }

    /**
     * How many tiles of about this width fill the available width: the nearest number (at least one), for tiles sized to
     * fill it (see {@link #fill}).
     */
    public static int nearestColumns(int availableWidth, int tileWidth, int gap) {
        return Math.max(1, Math.round((availableWidth + gap) / (float) (tileWidth + gap)));
    }

    /**
     * The width of each of these columns when they fill the available width.
     */
    public static int fill(int availableWidth, int columns, int gap) {
        return (availableWidth - (columns - 1) * gap) / columns;
    }
}
