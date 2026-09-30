package com.liskovsoft.smartyoutubetv2.tv.util;

import com.liskovsoft.smartyoutubetv2.tv.adapter.VideoGroupObjectAdapter;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tells when a row needs its next page. Moving right, it's loaded well before the end, so it's pulled and filtered
 * while there are still cards to show. At the end it's asked for on each card, as before (e.g. after a failed load).
 */
public class RowContinuation {
    public static final int NONE = 0;
    public static final int NEAR_END = 1;
    public static final int END = 2;
    // Row -> its last card when its next page was asked for near the end. Once per page.
    private final Map<VideoGroupObjectAdapter, Object> mAskedNearEnd = new WeakHashMap<>();
    private VideoGroupObjectAdapter mSelectedRow;
    private int mSelectedIndex = -1;

    /**
     * @param index the index of the selected card in the row
     * @return NONE, NEAR_END to load the next page before it's needed or END
     */
    public int onItemSelected(VideoGroupObjectAdapter row, int index) {
        boolean isMovingRight = row == mSelectedRow && index > mSelectedIndex;
        mSelectedRow = row;
        mSelectedIndex = index;

        int size = row.size();

        if (index > size - ViewUtil.ROW_SCROLL_CONTINUE_NUM) {
            return END;
        }

        Object last = row.get(size - 1);

        if (isMovingRight && index > size - ViewUtil.ROW_PRELOAD_NUM && mAskedNearEnd.get(row) != last) {
            mAskedNearEnd.put(row, last);
            return NEAR_END;
        }

        return NONE;
    }
}
