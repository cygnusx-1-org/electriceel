package com.liskovsoft.smartyoutubetv2.common.misc;

import android.text.TextUtils;

import com.liskovsoft.smartyoutubetv2.common.prefs.AiSListFilterData;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hides videos whose title has a keyword of the Hide content setting (see KeywordFilterData).<br/>
 * A keyword is one or more whole words, matched without case: "live" hides "LIVE: the match" but not "Delivery day".
 */
public class KeywordFilter {
    // Letters and digits, with an apostrophe inside ("don't"). The rest splits the words, e.g. "#shorts" is "shorts".
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}\\p{N}]+(?:'[\\p{L}\\p{M}\\p{N}]+)*");
    // The letter of the words that start with a digit
    public static final String DIGITS_LETTER = "#";
    /**
     * Often unwanted, picked in the settings instead of entered
     */
    public static final String[] COMMON_KEYWORDS = {
            "asmr", "challenge", "compilation", "drama", "exposed", "gone wrong", "highlights", "live", "meme", "memes",
            "mukbang", "podcast", "prank", "reaction", "reactions", "reacts", "shorts", "spoiler", "spoilers", "tier list",
            "tiktok", "trailer", "unboxing"
    };

    /**
     * @return the lower case words of the text, in order and with repeats. Empty for null.
     */
    public static List<String> getWords(CharSequence text) {
        if (text == null) {
            return Collections.emptyList();
        }

        List<String> words = new ArrayList<>();
        // The typographic apostrophe is the same word: "don’t" is "don't"
        Matcher matcher = WORD.matcher(text.toString().replace('\u2019', '\''));

        while (matcher.find()) {
            words.add(matcher.group().toLowerCase(Locale.ROOT));
        }

        return words;
    }

    /**
     * @return the keyword as it's kept: lower case words split by a space, e.g. "Tier-List!" is "tier list". Null without words.
     */
    public static String normalize(CharSequence keyword) {
        List<String> words = getWords(keyword);

        return words.isEmpty() ? null : TextUtils.join(" ", words);
    }

    /**
     * The words of a title to pick from, once each in the title order
     */
    public static List<String> getTitleWords(CharSequence title) {
        return new ArrayList<>(new LinkedHashSet<>(getWords(title)));
    }

    /**
     * Every word of the titles once, in the alphabetical order. The case doesn't matter.
     */
    public static List<String> getUniqueWords(Iterable<? extends CharSequence> titles) {
        Set<String> words = new TreeSet<>();

        for (CharSequence title : titles) {
            words.addAll(getWords(title));
        }

        return new ArrayList<>(words);
    }

    /**
     * The words by their first letter, e.g. for tabs. The letters are in the alphabetical order after {@link #DIGITS_LETTER}.
     * The words keep their order.
     */
    public static Map<String, List<String>> groupByLetter(List<String> words) {
        Map<String, List<String>> groups = new TreeMap<>((letter1, letter2) -> {
            if (letter1.equals(letter2)) {
                return 0;
            }

            if (DIGITS_LETTER.equals(letter1)) {
                return -1;
            }

            if (DIGITS_LETTER.equals(letter2)) {
                return 1;
            }

            return letter1.compareTo(letter2);
        });

        for (String word : words) {
            if (word == null || word.isEmpty()) {
                continue;
            }

            String letter = getLetter(word);
            List<String> group = groups.get(letter);

            if (group == null) {
                group = new ArrayList<>();
                groups.put(letter, group);
            }

            group.add(word);
        }

        return groups;
    }

    /**
     * @return the upper case first letter without its accent ("église" is "E"), or {@link #DIGITS_LETTER}
     */
    static String getLetter(String word) {
        int first = word.codePointAt(0);

        if (Character.isDigit(first)) {
            return DIGITS_LETTER;
        }

        // An accented letter is the letter and the accent
        String base = Normalizer.normalize(new String(Character.toChars(first)), Normalizer.Form.NFD);

        // Not String.toUpperCase: "ß" would be "SS"
        return new String(Character.toChars(Character.toUpperCase(base.codePointAt(0))));
    }

    /**
     * The feeds and search. The lists the user made (history, playlists, Watch later) and the local ones (e.g. the playback queue) are left alone.
     *
     * @param section the section of the group (see AiSListManager.getSection)
     */
    public static boolean isSupportedSection(int section) {
        switch (section) {
            case AiSListFilterData.SECTION_HOME:
            case AiSListFilterData.SECTION_SEARCH:
            case AiSListFilterData.SECTION_SUBSCRIPTIONS:
            case AiSListFilterData.SECTION_SUGGESTIONS:
            case AiSListFilterData.SECTION_CHANNELS:
            case AiSListManager.SECTION_CHANNEL_PAGE:
                return true;
            default:
                return false;
        }
    }
}
