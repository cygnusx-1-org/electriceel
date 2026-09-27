package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.prefs.OldVideosData;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hides videos older than the period of the Hide content setting (see OldVideosData).<br/>
 * The age comes from the relative date on the card (e.g. "1.2M views • 3 years ago"). Only English dates are known:
 * a video without one (e.g. live, upcoming, another app language) is never hidden.
 */
public class OldVideoFilter {
    // "3 years ago", "Streamed 2 months ago", "1 week ago". YouTube might put a no-break space between the words.
    private static final Pattern RELATIVE_DATE =
            Pattern.compile("(\\d{1,4})[\\s\\u00A0]+(second|minute|hour|day|week|month|year)s?[\\s\\u00A0]+ago", Pattern.CASE_INSENSITIVE);
    private static final int DAYS_IN_WEEK = 7;
    private static final int DAYS_IN_MONTH = 30;
    private static final int DAYS_IN_YEAR = 365;

    /**
     * @param sectionId the browse section the video is shown in (MediaGroup.TYPE_*)
     * @param info the second title of the card
     */
    public static boolean isHidden(Context context, int sectionId, CharSequence info) {
        OldVideosData data = OldVideosData.instance(context);

        return data.isHidingEnabled(sectionId) && isOlderThan(info, data.getPeriodMonths());
    }

    /**
     * The section can be picked in the settings. History is left alone, and the local lists (e.g. the playback queue) are never filtered.
     */
    public static boolean isSupportedSection(int sectionId) {
        switch (sectionId) {
            case MediaGroup.TYPE_HISTORY:
            case MediaGroup.TYPE_SETTINGS:
            case MediaGroup.TYPE_PLAYBACK_QUEUE:
            case MediaGroup.TYPE_BLOCKED_CHANNELS:
            case MediaGroup.TYPE_BLOCKED_AI_CHANNELS:
                return false;
            default:
                return true;
        }
    }

    /**
     * @return e.g. "3 months" or "1 year"
     */
    public static String getPeriodTitle(Context context, int months) {
        if (months % 12 == 0) {
            return context.getResources().getQuantityString(R.plurals.years, months / 12, String.valueOf(months / 12));
        }

        return context.getResources().getQuantityString(R.plurals.months, months, String.valueOf(months));
    }

    /**
     * YouTube rounds the age down (e.g. "1 year ago" lasts until the second year), so a video shown as the period old is hidden.
     * A month is 30 days: "4 weeks ago" stays with the 1 month period, "11 months ago" with the 1 year one.
     */
    static boolean isOlderThan(CharSequence info, int months) {
        int ageDays = getAgeDays(info);

        return ageDays != -1 && ageDays >= months * DAYS_IN_MONTH;
    }

    /**
     * @return the age in days or -1 when the text has no relative date
     */
    static int getAgeDays(CharSequence info) {
        if (info == null) {
            return -1;
        }

        Matcher matcher = RELATIVE_DATE.matcher(info);

        if (!matcher.find()) {
            return -1;
        }

        int count = Integer.parseInt(matcher.group(1));

        switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
            case "day":
                return count;
            case "week":
                return count * DAYS_IN_WEEK;
            case "month":
                return count * DAYS_IN_MONTH;
            case "year":
                return count * DAYS_IN_YEAR;
            default: // seconds, minutes, hours
                return 0;
        }
    }
}
