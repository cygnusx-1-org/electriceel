package com.liskovsoft.smartyoutubetv2.common.storage;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * What the sync knows of each scope ("global", "account:<key>"): the revision it pulled to (the cursor), and of each setting
 * the revision it last had from the server (the base of its next change) and the value then (the synced value: a value
 * that differs from it was changed here). Saved after each batch, so a crash or a kill costs at most a batch done again,
 * which the server recognizes (the same value is "unchanged"). A broken file starts the sync over (see SyncState()).
 * Used by the sync's worker thread only.
 */
final class SyncState {
    private static final String TAG = SyncState.class.getSimpleName();
    private static final String FILE = "state.json";
    private static final int VERSION = 1;
    private final File mFile;
    private final Map<String, Scope> mScopes = new HashMap<>();

    static final class Key {
        long base;
        @Nullable
        String value;
        /**
         * When the value was first seen changed here and not sent yet: the change's time, kept until it's accepted
         */
        long dirtyAt;
    }

    static final class Scope {
        /**
         * Joined: pulled whole once, the server's values taken (see StorageSync.reconcile). Until then nothing is sent.
         */
        boolean joined;
        long cursor;
        /**
         * Child mode was on for the account: on its end the account takes the server's values again
         */
        boolean paused;
        final Map<String, Key> keys = new HashMap<>();

        Key key(String key) {
            Key k = keys.get(key);

            if (k == null) {
                k = new Key();
                keys.put(key, k);
            }

            return k;
        }
    }

    SyncState(File dir) {
        mFile = new File(dir, FILE);
        load();
    }

    private void load() {
        String content = FileHelpers.getFileContents(mFile);

        if (content == null) {
            return;
        }

        try {
            JSONObject json = new JSONObject(content);

            if (json.optInt("version") != VERSION) {
                return;
            }

            JSONObject scopes = json.getJSONObject("scopes");

            for (Iterator<String> it = scopes.keys(); it.hasNext(); ) {
                String name = it.next();
                JSONObject s = scopes.getJSONObject(name);
                Scope scope = new Scope();
                scope.joined = s.optBoolean("joined");
                scope.cursor = s.optLong("cursor");
                scope.paused = s.optBoolean("paused");
                JSONObject keys = s.getJSONObject("keys");

                for (Iterator<String> kit = keys.keys(); kit.hasNext(); ) {
                    String key = kit.next();
                    JSONObject k = keys.getJSONObject(key);
                    Key entry = new Key();
                    entry.base = k.optLong("base");
                    entry.value = k.isNull("value") ? null : k.optString("value", null);
                    entry.dirtyAt = k.optLong("dirtyAt");
                    scope.keys.put(key, entry);
                }

                mScopes.put(name, scope);
            }
        } catch (JSONException e) {
            // Starts over: every scope is pulled whole again and the server's values win (see StorageSync.reconcile)
            Log.e(TAG, "Unreadable sync state, starting over: %s", e.getMessage());
            mScopes.clear();
        }
    }

    void save() {
        try {
            JSONObject scopes = new JSONObject();

            for (Map.Entry<String, Scope> e : mScopes.entrySet()) {
                JSONObject keys = new JSONObject();

                for (Map.Entry<String, Key> k : e.getValue().keys.entrySet()) {
                    JSONObject entry = new JSONObject();
                    entry.put("base", k.getValue().base);
                    entry.put("value", k.getValue().value != null ? k.getValue().value : JSONObject.NULL);

                    if (k.getValue().dirtyAt != 0) {
                        entry.put("dirtyAt", k.getValue().dirtyAt);
                    }

                    keys.put(k.getKey(), entry);
                }

                JSONObject scope = new JSONObject();
                scope.put("joined", e.getValue().joined);
                scope.put("cursor", e.getValue().cursor);
                scope.put("paused", e.getValue().paused);
                scope.put("keys", keys);
                scopes.put(e.getKey(), scope);
            }

            JSONObject json = new JSONObject();
            json.put("version", VERSION);
            json.put("scopes", scopes);
            FileHelpers.stringToFileAtomic(json.toString(), mFile);
        } catch (JSONException e) {
            Log.e(TAG, "Can't save the sync state: %s", e.getMessage());
        }
    }

    Scope scope(String name) {
        Scope scope = mScopes.get(name);

        if (scope == null) {
            scope = new Scope();
            mScopes.put(name, scope);
        }

        return scope;
    }

    /**
     * Forgets what's known of the scope: it's pulled whole again, and the server's values win
     */
    void reset(String name) {
        mScopes.remove(name);
    }

    java.util.Set<String> names() {
        return new java.util.HashSet<>(mScopes.keySet());
    }

    void resetAll() {
        mScopes.clear();
    }
}
