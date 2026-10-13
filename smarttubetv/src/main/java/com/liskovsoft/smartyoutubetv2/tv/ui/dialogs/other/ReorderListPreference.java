package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.content.Context;
import android.util.AttributeSet;
import androidx.preference.MultiSelectListPreference;

/**
 * A list put in order (see ReorderListPreferenceDialogFragment). Its change listener gets the values in their new order, as a List.
 */
public class ReorderListPreference extends MultiSelectListPreference {
    public ReorderListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ReorderListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ReorderListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ReorderListPreference(Context context) {
        super(context);
    }
}
