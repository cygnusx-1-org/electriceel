package com.liskovsoft.smartyoutubetv2.common.settingssearch;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The settings search: the settings whose name has the text typed in it, then the ones with a choice that has it (e.g. "Music"
 * in the sections of the sidebar). Names that start with it come first, the others keep the order of the settings. Case and
 * accents don't matter.
 */
public final class SettingsSearch {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final int TITLE_STARTS = 0;
    private static final int TITLE_HAS = 1;
    private static final int CHOICE_HAS = 2;
    private static final int NO_MATCH = 3;

    private SettingsSearch() {
    }

    /**
     * @return at most max: none when no setting has the text
     */
    public static List<SettingEntry> find(List<SettingEntry> entries, String text, int max) {
        String query = normalize(text);

        if (query.isEmpty() || entries.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<SettingEntry>> groups = new ArrayList<>();

        for (int i = 0; i < NO_MATCH; i++) {
            groups.add(new ArrayList<>());
        }

        for (SettingEntry entry : entries) {
            int match = match(entry, query);

            if (match != NO_MATCH) {
                groups.get(match).add(entry);
            }
        }

        List<SettingEntry> result = new ArrayList<>();
        Set<String> shown = new HashSet<>();

        for (List<SettingEntry> group : groups) {
            for (SettingEntry entry : group) {
                if (result.size() >= max) {
                    return result;
                }

                // A card and the untitled list of its panel are one setting: the list is named as its card
                if (shown.add(entry.sameKey)) {
                    result.add(entry);
                }
            }
        }

        return result;
    }

    private static int match(SettingEntry entry, String query) {
        String title = entry.searchTitle;

        if (title.startsWith(query)) {
            return TITLE_STARTS;
        }

        if (title.contains(query)) {
            return TITLE_HAS;
        }

        for (String choice : entry.searchChoices) {
            if (choice.contains(query)) {
                return CHOICE_HAS;
            }
        }

        return NO_MATCH;
    }

    /**
     * Lower case, without accents, spaces as one, e.g. " Vidéo  buffer" → "video buffer"
     */
    static String normalize(@Nullable String text) {
        if (text == null) {
            return "";
        }

        String result = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);

        return SPACES.matcher(result).replaceAll(" ").trim();
    }

    /**
     * The title and the place of the setting, the place without a last part named as the setting (e.g. the untitled list of the
     * Video buffer card is Player, Video buffer, Video buffer: the same as the card)
     */
    static String getSameKey(String searchTitle, List<String> path) {
        if (!path.isEmpty() && normalize(path.get(path.size() - 1)).equals(searchTitle)) {
            path = path.subList(0, path.size() - 1);
        }

        return searchTitle + "\n" + normalize(TextUtils.join("\n", path));
    }
}
