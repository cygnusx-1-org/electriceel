package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.content.Context;
import android.util.AttributeSet;
import androidx.preference.MultiSelectListPreference;

public class StringListPreference extends MultiSelectListPreference {
    private boolean mIsLinkified = true;

    public StringListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public StringListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public StringListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StringListPreference(Context context) {
        super(context);
    }

    /**
     * The addresses and time codes of the dialog message are links (see LeanbackListPreferenceDialogFragment)
     */
    public boolean isLinkified() {
        return mIsLinkified;
    }

    public void setLinkified(boolean linkified) {
        mIsLinkified = linkified;
    }
}
