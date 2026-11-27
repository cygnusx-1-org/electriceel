package com.liskovsoft.smartyoutubetv2.common.settingssearch;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The settings search on the device, offline: the settings whose words are in the question, the rare words first (e.g.
 * "subtitles" counts more than "video", which is in many settings). A word matches the start of a word of the setting, or a part
 * of it when long enough (e.g. "sponsor" in "SponsorBlock"). In the language of the app, without its accents.
 */
public final class SettingsSearch {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final float TITLE_WEIGHT = 3;
    private static final float CHOICES_WEIGHT = 2;
    private static final float PATH_WEIGHT = 1.5f;
    private static final float DESCRIPTION_WEIGHT = 1;
    /**
     * Below this part of the best score, a setting isn't shown: it matched a word or two of many
     */
    private static final float MIN_SCORE_PART = 0.3f;
    /**
     * Words of a question in English that aren't about any setting
     */
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "i", "me", "my", "to", "of", "in", "on", "for", "is", "it", "be", "and", "or", "how", "do", "does",
            "can", "where", "what", "which", "want", "wanna", "would", "like", "make", "set", "change", "turn", "get", "find",
            "with", "from", "this", "that", "there", "please", "settings", "setting", "option", "options", "app"));

    private SettingsSearch() {
    }

    /**
     * @return the best first, at most max: none when no word of the question is in a setting
     */
    public static List<SettingEntry> find(List<SettingEntry> entries, String question, int max) {
        List<String> words = getQuestionWords(question);

        if (words.isEmpty() || entries.isEmpty()) {
            return Collections.emptyList();
        }

        List<Fields> allFields = new ArrayList<>();

        for (SettingEntry entry : entries) {
            allFields.add(new Fields(entry));
        }

        // How rare each word is in the settings
        Map<String, Float> weights = new HashMap<>();

        for (String word : words) {
            int count = 0;

            for (Fields fields : allFields) {
                if (fields.matchesAny(word)) {
                    count++;
                }
            }

            weights.put(word, (float) Math.log(1 + (double) allFields.size() / (1 + count)));
        }

        List<Scored> scored = new ArrayList<>();
        float best = 0;

        for (Fields fields : allFields) {
            float score = 0;
            int matched = 0;

            for (String word : words) {
                float match = fields.match(word);

                if (match > 0) {
                    score += weights.get(word) * match;
                    matched++;
                }
            }

            if (matched == 0) {
                continue;
            }

            if (matched == words.size() && words.size() > 1) {
                score *= 1.5f;
            }

            scored.add(new Scored(fields.entry, score));
            best = Math.max(best, score);
        }

        // Stable: the same score keeps the order of the settings
        Collections.sort(scored, (scored1, scored2) -> Float.compare(scored2.score, scored1.score));

        List<SettingEntry> result = new ArrayList<>();
        Set<String> shown = new HashSet<>();

        for (Scored item : scored) {
            if (result.size() >= max || item.score < best * MIN_SCORE_PART) {
                break;
            }

            // A card and the untitled list of its panel are one setting: the list is named as its card
            if (shown.add(getSameKey(item.entry))) {
                result.add(item.entry);
            }
        }

        return result;
    }

    /**
     * The words of the question, without the ones about no setting. All of them when there are only those (e.g. "like", a
     * setting's name too).
     */
    static List<String> getQuestionWords(String question) {
        List<String> words = new ArrayList<>();
        List<String> all = new ArrayList<>();

        for (String word : split(question)) {
            if (!STOP_WORDS.contains(word) && !words.contains(word)) {
                words.add(word);
            }

            if (!all.contains(word)) {
                all.add(word);
            }
        }

        return words.isEmpty() ? all : words;
    }

    /**
     * Lower case, without accents, e.g. "Vidéo" → "video"
     */
    static String normalize(@Nullable String text) {
        if (text == null) {
            return "";
        }

        return MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    static List<String> split(@Nullable String text) {
        List<String> words = new ArrayList<>();

        for (String word : NOT_WORD.split(normalize(text))) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }

        return words;
    }

    /**
     * The title and the place of the setting, the place without a last part named as the setting (e.g. the untitled list of the
     * Video buffer card is Player, Video buffer, Video buffer: the same as the card)
     */
    private static String getSameKey(SettingEntry entry) {
        List<String> path = entry.path;

        if (!path.isEmpty() && normalize(path.get(path.size() - 1)).equals(normalize(entry.title))) {
            path = path.subList(0, path.size() - 1);
        }

        return normalize(entry.title) + "\n" + normalize(TextUtils.join("\n", path));
    }

    /**
     * A word of the question in a setting's text: 1 for a whole word, less for the start of a word, less again for a part
     */
    private static float match(String word, List<String> textWords, String text) {
        float result = 0;

        for (String textWord : textWords) {
            if (textWord.equals(word)) {
                return 1;
            }

            // "subtitle" and "subtitles" both ways, "auto" for "autoplay"
            if (textWord.startsWith(word) && word.length() >= 3 || word.startsWith(textWord) && textWord.length() >= 4) {
                result = Math.max(result, 0.8f);
            }
        }

        // E.g. "sponsor" in "sponsorblock", or a word of a language written without spaces (Chinese, Japanese, Thai)
        if (result == 0 && (word.length() >= 4 || isWithoutSpaces(word)) && text.contains(word)) {
            result = 0.5f;
        }

        return result;
    }

    private static boolean isWithoutSpaces(String word) {
        int codePoint = word.codePointAt(0);
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);

        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.THAI
                || script == Character.UnicodeScript.HANGUL;
    }

    /**
     * The texts of a setting, as matched
     */
    private static final class Fields {
        private final SettingEntry entry;
        private final String title;
        private final List<String> titleWords;
        private final String choices;
        private final List<String> choicesWords;
        private final String path;
        private final List<String> pathWords;
        private final String description;
        private final List<String> descriptionWords;

        private Fields(SettingEntry entry) {
            this.entry = entry;
            title = normalize(entry.title);
            titleWords = split(entry.title);
            choices = normalize(TextUtils.join("\n", entry.choices));
            choicesWords = split(choices);
            path = normalize(TextUtils.join("\n", entry.path));
            pathWords = split(path);
            description = normalize(entry.description);
            descriptionWords = split(description);
        }

        private boolean matchesAny(String word) {
            return match(word) > 0;
        }

        /**
         * The best of its texts, weighted
         */
        private float match(String word) {
            return Math.max(Math.max(TITLE_WEIGHT * SettingsSearch.match(word, titleWords, title),
                            CHOICES_WEIGHT * SettingsSearch.match(word, choicesWords, choices)),
                    Math.max(PATH_WEIGHT * SettingsSearch.match(word, pathWords, path),
                            DESCRIPTION_WEIGHT * SettingsSearch.match(word, descriptionWords, description)));
        }
    }

    private static final class Scored {
        private final SettingEntry entry;
        private final float score;

        private Scored(SettingEntry entry, float score) {
            this.entry = entry;
            this.score = score;
        }
    }
}
