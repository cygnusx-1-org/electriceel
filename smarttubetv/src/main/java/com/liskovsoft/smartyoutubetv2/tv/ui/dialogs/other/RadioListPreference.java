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

    public RadioListPreference(Context context) {
        super(context);
    }

    public void setDisabled(String entryValue) {
        mDisabled.add(entryValue);
    }

    public boolean isDisabled(String entryValue) {
        return mDisabled.contains(entryValue);
    }
}
