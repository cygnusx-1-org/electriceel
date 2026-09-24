package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.content.Context;
import androidx.preference.MultiSelectListPreference;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A checked list where some entries are disabled while another entry is checked (e.g. "Everything")
 */
public class DependentListPreference extends MultiSelectListPreference {
    private final Map<String, String> mDisabledBy = new HashMap<>(); // entry value -> entry value

    public DependentListPreference(Context context) {
        super(context);
    }

    public void setDisabledBy(String entryValue, String masterEntryValue) {
        mDisabledBy.put(entryValue, masterEntryValue);
    }

    /**
     * An entry that disables others is shown as a switch
     */
    public boolean isToggle(String entryValue) {
        return mDisabledBy.containsValue(entryValue);
    }

    public boolean isDisabled(String entryValue, Set<String> selections) {
        String master = mDisabledBy.get(entryValue);

        return master != null && selections.contains(master);
    }
}
