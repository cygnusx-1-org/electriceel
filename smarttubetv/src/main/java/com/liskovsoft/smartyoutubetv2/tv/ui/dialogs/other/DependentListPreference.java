package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.content.Context;
import androidx.preference.MultiSelectListPreference;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A checked list with special entries: ones disabled while another entry is checked (e.g. "Everything"),
 * ones that open a menu instead of being checked, and radio buttons that uncheck each other
 */
public class DependentListPreference extends MultiSelectListPreference {
    private final Map<String, String> mDisabledBy = new HashMap<>(); // entry value -> entry value
    private final Map<String, Runnable> mMenus = new HashMap<>(); // entry value -> opens the menu
    private final Map<String, Set<String>> mRadio = new HashMap<>(); // entry value -> entry values unchecked by it
    private final Set<String> mToggles = new HashSet<>(); // entry values shown as a switch

    public DependentListPreference(Context context) {
        super(context);
    }

    public void setDisabledBy(String entryValue, String masterEntryValue) {
        mDisabledBy.put(entryValue, masterEntryValue);
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
}
