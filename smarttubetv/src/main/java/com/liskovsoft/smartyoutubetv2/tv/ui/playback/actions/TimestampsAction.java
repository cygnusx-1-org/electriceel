package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for the time codes of the video description.
 */
public class TimestampsAction extends Action {
    public TimestampsAction(Context context) {
        super(R.id.action_timestamps);
        Drawable uncoloredDrawable = ContextCompat.getDrawable(context, R.drawable.action_timestamps);

        setIcon(uncoloredDrawable);
        setLabel1(context.getString(
                R.string.action_timestamps));
    }
}
