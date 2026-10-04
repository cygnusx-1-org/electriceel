package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.content.Context;
import androidx.preference.MultiSelectListPreference;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A checked list with special entries: ones disabled while another entry is checked (e.g. "Everything") or for good,
 * ones that open a menu instead of being checked, radio buttons that uncheck each other, "All" entries that check or uncheck
 * other entries, and notes (a line of text)
 */
public class DependentListPreference extends MultiSelectListPreference {
    private final Map<String, String> mDisabledBy = new HashMap<>(); // entry value -> entry value
    private final Set<String> mDisabled = new HashSet<>(); // entry values disabled whatever is checked
    private final Map<String, Runnable> mMenus = new HashMap<>(); // entry value -> opens the menu
    private final Map<String, Set<String>> mRadio = new HashMap<>(); // entry value -> entry values unchecked by it
    private final Map<String, Set<String>> mSelectAll = new HashMap<>(); // entry value -> entry values checked and unchecked with it
    private final Set<String> mToggles = new HashSet<>(); // entry values shown as a switch
    private final Set<String> mNotes = new HashSet<>(); // entry values shown as a line of text

    public DependentListPreference(Context context) {
        super(context);
    }

    public void setDisabledBy(String entryValue, String masterEntryValue) {
        mDisabledBy.put(entryValue, masterEntryValue);
    }

    public void setDisabled(String entryValue) {
        mDisabled.add(entryValue);
    }

    public void setNote(String entryValue) {
        mNotes.add(entryValue);
    }

    /**
     * Shown as a line of text, never checked
     */
    public boolean isNote(String entryValue) {
        return mNotes.contains(entryValue);
    }

    public void setToggle(String entryValue) {
        mToggles.add(entryValue);
    }

    /**
     * Shown as a switch: set so, or an entry that disables others
     */
    public boolean isToggle(String entryValue) {
        return mToggles.contains(entryValue) || mDisabledBy.containsValue(entryValue);
    }

    public boolean isDisabled(String entryValue, Set<String> selections) {
        if (mDisabled.contains(entryValue)) {
            return true;
        }

        String master = mDisabledBy.get(entryValue);

        return master != null && selections.contains(master);
    }

    public void setMenu(String entryValue, Runnable openMenu) {
        mMenus.put(entryValue, openMenu);
    }

    public boolean isMenu(String entryValue) {
        return mMenus.containsKey(entryValue);
    }

    public void openMenu(String entryValue) {
        Runnable openMenu = mMenus.get(entryValue);

        if (openMenu != null) {
            openMenu.run();
        }
    }

    public void setRadio(String entryValue, Set<String> otherEntryValues) {
        mRadio.put(entryValue, otherEntryValues);
    }

    /**
     * Shown as a radio button
     */
    public boolean isRadio(String entryValue) {
        return mRadio.containsKey(entryValue);
    }

    /**
     * The entries unchecked when this one is checked
     */
    public Set<String> getRadio(String entryValue) {
        Set<String> result = mRadio.get(entryValue);

        return result != null ? result : Collections.emptySet();
    }

    public void setSelectAll(String entryValue, Set<String> otherEntryValues) {
        mSelectAll.put(entryValue, otherEntryValues);
    }

    /**
     * After the entry was checked or unchecked: an "All" entry checks or unchecks its entries along with it,
     * and an "All" entry is checked while all its entries are
     */
    public void updateSelectAll(String entryValue, Set<String> selections) {
        Set<String> entries = mSelectAll.get(entryValue);

        if (entries != null) {
            if (selections.contains(entryValue)) {
                selections.addAll(entries);
            } else {
                selections.removeAll(entries);
            }

            return;
        }

        for (Map.Entry<String, Set<String>> all : mSelectAll.entrySet()) {
            if (!all.getValue().contains(entryValue)) {
                continue;
            }

            if (selections.containsAll(all.getValue())) {
                selections.add(all.getKey());
            } else {
                selections.remove(all.getKey());
            }
        }
    }
}
