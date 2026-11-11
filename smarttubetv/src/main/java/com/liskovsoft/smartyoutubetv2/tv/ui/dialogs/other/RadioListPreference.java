package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.content.Context;
import androidx.preference.ListPreference;

import java.util.HashSet;
import java.util.Set;

/**
 * A radio list with entries that are greyed out and can't be chosen (see LeanbackListPreferenceDialogFragment.AdapterSingle)
 */
public class RadioListPreference extends ListPreference {
    private final Set<String> mDisabled = new HashSet<>(); // entry values
    private CharSequence mSummary;

    public RadioListPreference(Context context) {
        super(context);
    }

    @Override
    public void setSummary(CharSequence summary) {
        mSummary = summary; // Before super: it notifies the change, and a rebind reads getSummary()
        super.setSummary(summary);
    }

    /**
     * The summary as set. ListPreference runs it through String.format, so a value shown as the summary
     * (the modern skin's lists) with a % in it, such as the volume's "100%", threw while its row was bound
     * and the rows after it never showed.
     */
    @Override
    public CharSequence getSummary() {
        return mSummary;
    }

    public void setDisabled(String entryValue) {
        mDisabled.add(entryValue);
    }

    public boolean isDisabled(String entryValue) {
        return mDisabled.contains(entryValue);
    }
}
