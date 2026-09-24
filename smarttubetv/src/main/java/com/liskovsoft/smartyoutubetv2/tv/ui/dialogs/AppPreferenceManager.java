package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs;

import android.content.Context;
import android.text.TextUtils;
import androidx.preference.DialogPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.ChatPreference;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.CommentsPreference;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.DependentListPreference;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.StringListPreference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppPreferenceManager {
    private final Context mContext;
    private final Runnable mOnChange;
    // A switch or a radio option -> the preferences that are enabled only while it's on (see OptionItem.setRequired)
    private final Map<OptionItem, List<Preference>> mDependents = new HashMap<>();
    // A preference -> the items it requires, any one of them enables it
    private final Map<Preference, OptionItem[]> mRequirements = new HashMap<>();

    public static class ListPreferenceData {
        public final CharSequence[] entries;
        public final CharSequence[] values;
        public final String defaultValue;
        public final Set<String> defaultValues;

        public ListPreferenceData(CharSequence[] entries, CharSequence[] values, String defaultValue, Set<String> defaultValues) {
            this.entries = entries;
            this.values = values;
            this.defaultValue = defaultValue;
            this.defaultValues = defaultValues;
        }
    }

    public AppPreferenceManager(Context context) {
        this(context, null);
    }

    public AppPreferenceManager(Context context, Runnable onChange) {
        mContext = context;
        mOnChange = onChange;
    }

    public Preference createPreference(OptionCategory category) {
        Preference pref = createPreferenceInt(category);
        bindRequiredSwitch(category, pref);
        return pref;
    }

    /**
     * Grey out a preference while none of the switches or radio options its items require is on.<br/>
     * Checkbox lists keep their per item check instead.
     */
    private void bindRequiredSwitch(OptionCategory category, Preference pref) {
        if (pref == null || category.type == OptionCategory.TYPE_CHECKBOX_LIST || category.options == null || category.options.isEmpty()) {
            return;
        }

        OptionItem[] required = category.options.get(0).getRequired();

        if (required == null || required.length == 0 || required[0] == null) {
            return;
        }

        mRequirements.put(pref, required);
        pref.setEnabled(isAnySelected(required, null, null));

        for (OptionItem requiredItem : required) {
            List<Preference> dependents = mDependents.get(requiredItem);

            if (dependents == null) {
                dependents = new ArrayList<>();
                mDependents.put(requiredItem, dependents);
            }

            dependents.add(pref);
        }
    }

    /**
     * Re-check the preferences that require the changed items
     * @param radioOptions the radio list the change came from (its options keep a stale selection) or null
     * @param radioValue the newly selected radio option
     */
    private void updateDependents(List<OptionItem> changedItems, List<OptionItem> radioOptions, Object radioValue) {
        for (OptionItem changedItem : changedItems) {
            List<Preference> dependents = mDependents.get(changedItem);

            if (dependents == null) {
                continue;
            }

            for (Preference dependent : dependents) {
                dependent.setEnabled(isAnySelected(mRequirements.get(dependent), radioOptions, radioValue));
            }
        }
    }

    private static boolean isAnySelected(OptionItem[] items, List<OptionItem> radioOptions, Object radioValue) {
        if (items == null) {
            return true;
        }

        for (OptionItem item : items) {
            boolean isSelected = radioOptions != null && radioOptions.contains(item) ? radioValue.equals(item.toString()) : item.isSelected();

            if (isSelected) {
                return true;
            }
        }

        return false;
    }

    private Preference createPreferenceInt(OptionCategory category) {
        switch (category.type) {
            case OptionCategory.TYPE_CHECKBOX_LIST:
                return createCheckedListPreference(category);
            case OptionCategory.TYPE_RADIO_LIST:
                return createRadioListPreference(category);
            case OptionCategory.TYPE_STRING_LIST:
                return createStringListPreference(category);
            case OptionCategory.TYPE_SINGLE_SWITCH:
                return createSwitchPreference(category);
            case OptionCategory.TYPE_SINGLE_BUTTON:
                return createButtonPreference(category);
            case OptionCategory.TYPE_LONG_TEXT:
                return createLongTextPreference(category);
            case OptionCategory.TYPE_CHAT:
                return createChatPreference(category);
            case OptionCategory.TYPE_COMMENTS:
                return createCommentsPreference(category);
        }

        throw  new IllegalStateException("Can't find matched preference for type: " + category.type);
    }

    private Preference createStringListPreference(OptionCategory category) {
        MultiSelectListPreference pref = new StringListPreference(mContext);

        initMultiSelectListPreference(category, pref);

        return pref;
    }

    private Preference createLongTextPreference(OptionCategory category) {
        MultiSelectListPreference pref = new StringListPreference(mContext);

        pref.setDialogMessage(category.options.get(0).getTitle());

        initMultiSelectListPreference(category, pref);

        return pref;
    }

    private Preference createChatPreference(OptionCategory category) {
        ChatPreference pref = new ChatPreference(mContext);

        OptionItem optionItem = category.options.get(0);
        pref.setChatReceiver(optionItem.getChatReceiver());

        initDialogPreference(category, pref);

        return pref;
    }

    private Preference createCommentsPreference(OptionCategory category) {
        CommentsPreference pref = new CommentsPreference(mContext);

        OptionItem optionItem = category.options.get(0);
        pref.setCommentsReceiver(optionItem.getCommentsReceiver());

        initDialogPreference(category, pref);

        return pref;
    }

    public Preference createButtonPreference(OptionCategory category) {
        Preference result = null;

        if (category.options.size() == 1) {
            OptionItem item = category.options.get(0);
            Preference preference = new Preference(mContext);
            preference.setPersistent(false);
            preference.setTitle(item.getTitle());
            preference.setOnPreferenceClickListener(pref -> {
                item.onSelect(true);
                return true;
            });

            result = preference;
        }

        return result;
    }

    public Preference createSwitchPreference(OptionCategory category) {
        Preference result = null;

        if (category.options.size() == 1) {
            OptionItem item = category.options.get(0);
            Preference preference = new SwitchPreference(mContext);
            preference.setPersistent(false);
            preference.setTitle(item.getTitle());
            preference.setDefaultValue(item.isSelected());
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                item.onSelect((boolean) newValue);

                updateDependents(Collections.singletonList(item), null, null);

                return true;
            });

            result = preference;
        }

        return result;
    }

    public Preference createRadioListPreference(OptionCategory category) {
        ListPreference pref = new ListPreference(mContext);

        initSingleSelectListPreference(category, pref);

        return pref;
    }

    public Preference createCheckedListPreference(OptionCategory category) {
        MultiSelectListPreference pref = hasDependentItems(category) ? new DependentListPreference(mContext) : new MultiSelectListPreference(mContext);

        initMultiSelectListPreference(category, pref);

        if (pref instanceof DependentListPreference) {
            for (OptionItem item : category.options) {
                if (item.getDisabledBy() != null) {
                    ((DependentListPreference) pref).setDisabledBy(item.toString(), item.getDisabledBy().toString());
                }
            }
        }

        return pref;
    }

    private static boolean hasDependentItems(OptionCategory category) {
        for (OptionItem item : category.options) {
            if (item.getDisabledBy() != null) {
                return true;
            }
        }

        return false;
    }

    private void initSingleSelectListPreference(OptionCategory category, ListPreference pref) {
        initDialogPreference(category, pref);

        ListPreferenceData prefData = createListPreferenceData(category.options);

        pref.setEntries(prefData.entries);
        pref.setEntryValues(prefData.values);
        pref.setValue(prefData.defaultValue);

        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            for (OptionItem optionItem : category.options) {
                if (newValue.equals(optionItem.toString())) {
                    optionItem.onSelect(true);
                    break;
                }
            }

            updateDependents(category.options, category.options, newValue);

            return true;
        });
    }

    private void initMultiSelectListPreference(OptionCategory category, MultiSelectListPreference pref) {
        initDialogPreference(category, pref);

        ListPreferenceData prefData = createListPreferenceData(category.options);

        pref.setEntries(prefData.entries);
        pref.setEntryValues(prefData.values);
        pref.setValues(prefData.defaultValues);

        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Set) {
                Set<?> values = ((Set<?>) newValue); // All checked items. That don't means that this items is pressed recently.
                for (OptionItem item : category.options) {
                    boolean isSelected = false;
                    for (Object value : values) {
                        isSelected = value.equals(item.toString());
                        if (isSelected) {
                            break;
                        }
                    }

                    if (item.isSelected() != isSelected) {
                        if (isSelected) {
                            OptionItem[] requiredItems = item.getRequired();

                            if (requiredItems != null) {
                                for (OptionItem requiredItem : requiredItems) {
                                    if (!requiredItem.isSelected()) {
                                        MessageHelpers.showMessage(mContext, mContext.getString(R.string.require_checked, requiredItem.getTitle()));
                                    }
                                }
                            }

                            OptionItem[] radioItems = item.getRadio();

                            if (radioItems != null) {
                                for (OptionItem radioItem : radioItems) {
                                    radioItem.onSelect(false);
                                }

                                if (mOnChange != null) {
                                    mOnChange.run();
                                }
                            }
                        }

                        item.onSelect(isSelected);

                        return true;
                    }
                }
            }

            return false;
        });
    }

    public ListPreferenceData createListPreferenceData(List<OptionItem> items) {
        CharSequence[] titles = new CharSequence[items.size()];
        CharSequence[] hashes = new CharSequence[items.size()];
        String defaultValue = null;
        Set<String> defaultValues = new HashSet<>(); // used in multi set lists

        for (int i = 0; i < items.size(); i++) {
            OptionItem optionItem = items.get(i);

            CharSequence title = optionItem.getTitle();
            String value = optionItem.toString();

            // Note, multi lists don't have individual (per item) descriptions. So, append description to title.
            if (optionItem.getDescription() != null) {
                title = TextUtils.concat(title, "\n", Utils.italic(optionItem.getDescription()));
            }

            titles[i] = title;
            hashes[i] = value;

            if (optionItem.isSelected()) {
                defaultValue = value;
                defaultValues.add(value);
            }
        }

        return new ListPreferenceData(titles, hashes, defaultValue, defaultValues);
    }

    private void initDialogPreference(OptionCategory category, DialogPreference pref) {
        pref.setPersistent(false);
        pref.setTitle(category.title);
        pref.setDialogTitle(category.title);
        pref.setKey(category.toString());
    }
}
