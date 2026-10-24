package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The part of JSON Schema 2020-12 the submission's schema uses (ElectricEelDataRestAPI/schema/submission.schema.json): type,
 * enum, const, minLength, properties, required, additionalProperties, items, anyOf and $ref to #/$defs. Unknown keywords fail,
 * so that the schema can't use one this doesn't check.
 */
final class JsonSchemaValidator {
    private static final java.util.Set<String> KEYWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "$schema", "$id", "$defs", "title", "description", "type", "enum", "const", "minLength", "properties", "required",
            "additionalProperties", "items", "anyOf", "$ref"));
    private final JSONObject mRoot;

    JsonSchemaValidator(JSONObject root) {
        mRoot = root;
    }

    /**
     * Each one as "$.path: why", none when it's valid
     */
    List<String> validate(Object value) {
        List<String> errors = new ArrayList<>();

        try {
            validate(mRoot, value, "$", errors);
        } catch (JSONException e) {
            errors.add("$: the schema can't be read: " + e.getMessage());
        }

        return errors;
    }

    private void validate(JSONObject schema, Object value, String path, List<String> errors) throws JSONException {
        for (Iterator<String> keys = schema.keys(); keys.hasNext(); ) {
            String key = keys.next();

            if (!KEYWORDS.contains(key)) {
                errors.add(path + ": the schema uses " + key + ", which isn't checked");
            }
        }

        if (schema.has("$ref")) {
            String ref = schema.getString("$ref");

            if (!ref.startsWith("#/$defs/")) {
                errors.add(path + ": unknown $ref " + ref);
                return;
            }

            validate(mRoot.getJSONObject("$defs").getJSONObject(ref.substring("#/$defs/".length())), value, path, errors);
            return;
        }

        if (schema.has("anyOf")) {
            JSONArray options = schema.getJSONArray("anyOf");

            for (int i = 0; i < options.length(); i++) {
                List<String> optionErrors = new ArrayList<>();
                validate(options.getJSONObject(i), value, path, optionErrors);

                if (optionErrors.isEmpty()) {
                    return;
                }
            }

            errors.add(path + ": matches none of anyOf");
            return;
        }

        if (schema.has("type") && !isType(schema.get("type"), value)) {
            errors.add(path + ": " + describe(value) + " isn't " + schema.get("type"));
            return;
        }

        if (schema.has("const") && !schema.get("const").equals(value)) {
            errors.add(path + ": " + value + " isn't " + schema.get("const"));
        }

        if (schema.has("enum") && !contains(schema.getJSONArray("enum"), value)) {
            errors.add(path + ": " + value + " isn't one of " + schema.get("enum"));
        }

        if (schema.has("minLength") && value instanceof String && ((String) value).length() < schema.getInt("minLength")) {
            errors.add(path + ": shorter than " + schema.getInt("minLength"));
        }

        if (value instanceof JSONObject) {
            validateObject(schema, (JSONObject) value, path, errors);
        } else if (value instanceof JSONArray && schema.has("items")) {
            JSONArray array = (JSONArray) value;

            for (int i = 0; i < array.length(); i++) {
                validate(schema.getJSONObject("items"), array.get(i), path + "[" + i + "]", errors);
            }
        }
    }

    private void validateObject(JSONObject schema, JSONObject value, String path, List<String> errors) throws JSONException {
        JSONObject properties = schema.optJSONObject("properties");
        JSONArray required = schema.optJSONArray("required");

        if (required != null) {
            for (int i = 0; i < required.length(); i++) {
                if (!value.has(required.getString(i))) {
                    errors.add(path + ": " + required.getString(i) + " is missing");
                }
            }
        }

        Object additional = schema.opt("additionalProperties");

        for (Iterator<String> keys = value.keys(); keys.hasNext(); ) {
            String key = keys.next();
            String keyPath = path + "." + key;

            if (properties != null && properties.has(key)) {
                validate(properties.getJSONObject(key), value.get(key), keyPath, errors);
            } else if (Boolean.FALSE.equals(additional)) {
                errors.add(keyPath + ": not in the schema");
            } else if (additional instanceof JSONObject) {
                validate((JSONObject) additional, value.get(key), keyPath, errors);
            }
        }
    }

    private static boolean isType(Object type, Object value) throws JSONException {
        if (type instanceof JSONArray) {
            JSONArray types = (JSONArray) type;

            for (int i = 0; i < types.length(); i++) {
                if (isType(types.get(i), value)) {
                    return true;
                }
            }

            return false;
        }

        switch ((String) type) {
            case "null":
                return value == null || value == JSONObject.NULL;
            case "string":
                return value instanceof String;
            case "boolean":
                return value instanceof Boolean;
            case "integer":
                return value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte;
            case "number":
                return value instanceof Number;
            case "object":
                return value instanceof JSONObject;
            case "array":
                return value instanceof JSONArray;
            default:
                return false;
        }
    }

    private static boolean contains(JSONArray values, Object value) throws JSONException {
        for (int i = 0; i < values.length(); i++) {
            Object option = values.get(i);

            if (option.equals(value) || (option == JSONObject.NULL && value == null)) {
                return true;
            }
        }

        return false;
    }

    private static String describe(Object value) {
        return value == null || value == JSONObject.NULL ? "null" : value.getClass().getSimpleName() + " " + value;
    }
}
