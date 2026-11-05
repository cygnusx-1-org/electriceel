package com.liskovsoft.smartyoutubetv2.common.storage;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.SyncedSettings.Field;
import com.liskovsoft.smartyoutubetv2.common.prefs.SyncedSettings.Type;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A synced setting between the app's saved data (see Helpers.mergeData) and its JSON value on the server. Each value has one
 * JSON form (see canonical), so a value is recognized as unchanged whoever wrote it.
 */
final class SyncValues {
    /**
     * Between the values of a data key (Helpers' DATA_DELIM): a value with it would split into two
     */
    private static final String DATA_DELIM = "%OB%";

    private SyncValues() {
    }

    /**
     * The field's value in the data, as JSON. Null when the data hasn't any: the app's default applies, and nothing is sent.
     */
    @Nullable
    static String read(Field field, @Nullable String data) {
        if (data == null || data.trim().isEmpty()) {
            return null;
        }

        if (field.isWhole()) {
            return JSONObject.quote(data);
        }

        String[] split = Helpers.splitData(data);

        if (split == null || field.index >= split.length) {
            return null;
        }

        return encode(field.type, split[field.index]);
    }

    @Nullable
    private static String encode(Type type, String raw) {
        switch (type) {
            case BOOL:
                return "true".equals(raw) || "false".equals(raw) ? raw : null;
            case INT:
            case LONG:
                try {
                    return String.valueOf(Long.parseLong(raw));
                } catch (NumberFormatException e) {
                    return null;
                }
            case FLOAT:
                try {
                    double d = Double.parseDouble(raw);
                    return Double.isNaN(d) || Double.isInfinite(d) ? null : JSONObject.numberToString(d);
                } catch (NumberFormatException | JSONException e) {
                    return null;
                }
            default:
                return "null".equals(raw) ? "null" : JSONObject.quote(raw);
        }
    }

    /**
     * The value as the app saves it, or null when the JSON doesn't fit the field (another type, out of range, a delimiter
     * that would split the data): such a value from the server is ignored
     */
    @Nullable
    static String decode(Field field, @Nullable String json) {
        if (json == null) {
            return null;
        }

        Object value;

        try {
            value = new JSONTokener(json).nextValue();
        } catch (JSONException e) {
            return null;
        }

        switch (field.type) {
            case BOOL:
                return value instanceof Boolean ? value.toString() : null;
            case INT:
            case LONG:
                if (!(value instanceof Number)) {
                    return null;
                }
                double d = ((Number) value).doubleValue();
                long n = ((Number) value).longValue();
                if (d != n || field.type == Type.INT && (n > Integer.MAX_VALUE || n < Integer.MIN_VALUE)) {
                    return null;
                }
                return String.valueOf(n);
            case FLOAT:
                if (!(value instanceof Number)) {
                    return null;
                }
                float f = ((Number) value).floatValue();
                return Float.isNaN(f) || Float.isInfinite(f) ? null : Float.toString(f);
            default:
                if (value == JSONObject.NULL && !field.isWhole()) {
                    return "null";
                }
                if (!(value instanceof String)) {
                    return null;
                }
                String s = (String) value;
                return !field.isWhole() && s.contains(DATA_DELIM) ? null : s;
        }
    }

    /**
     * The JSON form a value has here, or null when it doesn't fit the field
     */
    @Nullable
    static String canonical(Field field, @Nullable String json) {
        String raw = decode(field, json);

        if (raw == null) {
            return null;
        }

        return field.isWhole() ? JSONObject.quote(raw) : encode(field.type, raw);
    }

    /**
     * The data with the field set to the value, or null when the value doesn't fit the field. The other values stay as they
     * are, the missing ones before it are saved as none ("null", as Helpers.mergeData saves them).
     */
    @Nullable
    static String write(Field field, @Nullable String data, String json) {
        String raw = decode(field, json);

        if (raw == null) {
            return null;
        }

        if (field.isWhole()) {
            return raw;
        }

        String[] split = Helpers.splitData(data);
        List<String> values = split != null ? new ArrayList<>(Arrays.asList(split)) : new ArrayList<>();

        while (values.size() <= field.index) {
            values.add("null");
        }

        values.set(field.index, raw);

        return Helpers.mergeData(values.toArray());
    }
}
