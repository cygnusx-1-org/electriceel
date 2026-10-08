package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for pausing and showing the next frame.
 */
public class NextFrameAction extends Action {
    public NextFrameAction(Context context) {
        super(R.id.action_next_frame);
        Drawable uncoloredDrawable = ContextCompat.getDrawable(context, R.drawable.action_next_frame);

        setIcon(uncoloredDrawable);
        setLabel1(context.getString(
                R.string.action_next_frame));
    }
}
