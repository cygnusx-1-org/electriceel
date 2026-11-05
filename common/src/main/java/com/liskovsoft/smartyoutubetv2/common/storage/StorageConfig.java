package com.liskovsoft.smartyoutubetv2.common.storage;

import android.annotation.SuppressLint;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The storage sync's own settings (Settings → System → Storage sync): the server, this device's authorization, which accounts
 * are synced and whether the global settings are. They're the device's: kept outside the data that's backed up and synced
 * (files/storage_sync, not app_prefs), so a backup restored on another device doesn't make it this one.
 */
public final class StorageConfig {
    private static final String TAG = StorageConfig.class.getSimpleName();
    static final String DIR = "storage_sync";
    private static final String FILE = "config.json";
    private final File mFile;
    private final String mDeviceBinding;

    private boolean mIsEnabled;
    private String mServerUrl;
    private String mServerName;
    private String mServerInstanceId;
    private String mDeviceId;
    private String mToken;
    private String mGroupId;
    private String mGroupName;
    private String mDeviceName;
    private boolean mIsAllAccounts;
    private final Set<String> mAccounts = new LinkedHashSet<>();
    private boolean mIsGlobalSynced;
    private String mPairingId;
    private String mPairingSecret;
    private String mPairingCode;
    private long mPairingExpiresAt;

    StorageConfig(Context context) {
        this(new File(FileHelpers.getFilesDir(context), DIR + "/" + FILE), deviceBinding(context));
    }

    StorageConfig(File file, String deviceBinding) {
        mFile = file;
        mDeviceBinding = deviceBinding;
        load();
    }

    /**
     * Android's ID of the app on this device (per signing key and user since Android 8): an authorization restored from
     * another device's backup isn't this device's
     */
    @SuppressLint("HardwareIds")
    private static String deviceBinding(Context context) {
        String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return id != null ? id : "";
    }

    private synchronized void load() {
        String content = FileHelpers.getFileContents(mFile);

        if (content == null) {
            return;
        }

        try {
            JSONObject json = new JSONObject(content);
            mIsEnabled = json.optBoolean("enabled");
            mServerUrl = json.optString("serverUrl", null);
            mServerName = json.optString("serverName", null);
            mServerInstanceId = json.optString("serverInstanceId", null);
            mIsAllAccounts = json.optBoolean("allAccounts");
            mIsGlobalSynced = json.optBoolean("syncGlobal");
            JSONArray accounts = json.optJSONArray("accounts");

            if (accounts != null) {
                for (int i = 0; i < accounts.length(); i++) {
                    mAccounts.add(accounts.getString(i));
                }
            }

            // Authorized on another device (restored from its backup): this one has to be authorized itself
            if (mDeviceBinding.equals(json.optString("boundTo"))) {
                mDeviceId = json.optString("deviceId", null);
                mToken = json.optString("token", null);
                mGroupId = json.optString("groupId", null);
                mGroupName = json.optString("groupName", null);
                mDeviceName = json.optString("deviceName", null);
                JSONObject pairing = json.optJSONObject("pairing");

                if (pairing != null) {
                    mPairingId = pairing.optString("id", null);
                    mPairingSecret = pairing.optString("secret", null);
                    mPairingCode = pairing.optString("code", null);
                    mPairingExpiresAt = pairing.optLong("expiresAt");
                }
            } else if (json.has("token")) {
                Log.w(TAG, "The authorization is another device's: dropped");
            }
        } catch (JSONException e) {
            // A broken file (e.g. cut short by a crash during an older write): the sync starts over, the settings are untouched
            Log.e(TAG, "Unreadable %s: %s", mFile, e.getMessage());
        }
    }

    private synchronized void save() {
        try {
            JSONObject json = new JSONObject();
            json.put("enabled", mIsEnabled);
            json.putOpt("serverUrl", mServerUrl);
            json.putOpt("serverName", mServerName);
            json.putOpt("serverInstanceId", mServerInstanceId);
            json.put("allAccounts", mIsAllAccounts);
            json.put("accounts", new JSONArray(mAccounts));
            json.put("syncGlobal", mIsGlobalSynced);
            json.put("boundTo", mDeviceBinding);
            json.putOpt("deviceId", mDeviceId);
            json.putOpt("token", mToken);
            json.putOpt("groupId", mGroupId);
            json.putOpt("groupName", mGroupName);
            json.putOpt("deviceName", mDeviceName);

            if (mPairingId != null) {
                JSONObject pairing = new JSONObject();
                pairing.put("id", mPairingId);
                pairing.put("secret", mPairingSecret);
                pairing.put("code", mPairingCode);
                pairing.put("expiresAt", mPairingExpiresAt);
                json.put("pairing", pairing);
            }

            FileHelpers.stringToFileAtomic(json.toString(), mFile);
        } catch (JSONException e) {
            Log.e(TAG, "Can't save: %s", e.getMessage());
        }
    }

    public synchronized boolean isEnabled() {
        return mIsEnabled;
    }

    synchronized void setEnabled(boolean enabled) {
        mIsEnabled = enabled;
        save();
    }

    @Nullable
    public synchronized String getServerUrl() {
        return mServerUrl;
    }

    @Nullable
    public synchronized String getServerName() {
        return mServerName;
    }

    @Nullable
    synchronized String getServerInstanceId() {
        return mServerInstanceId;
    }

    /**
     * Another server: the authorization (and a pending one) was the other server's
     */
    synchronized void setServer(String url, @Nullable String name, @Nullable String instanceId) {
        boolean isSame = url.equals(mServerUrl) || instanceId != null && instanceId.equals(mServerInstanceId);

        mServerUrl = url;
        mServerName = name;

        if (!isSame) {
            clearAuthorizationInt();
        }

        mServerInstanceId = instanceId;
        save();
    }

    public synchronized boolean isAuthorized() {
        return !TextUtils.isEmpty(mToken) && !TextUtils.isEmpty(mServerUrl);
    }

    @Nullable
    synchronized String getToken() {
        return mToken;
    }

    @Nullable
    public synchronized String getGroupName() {
        return mGroupName;
    }

    @Nullable
    synchronized String getGroupId() {
        return mGroupId;
    }

    @Nullable
    public synchronized String getDeviceName() {
        return mDeviceName;
    }

    synchronized void setAuthorization(String deviceId, String token, String groupId, String groupName) {
        mDeviceId = deviceId;
        mToken = token;
        mGroupId = groupId;
        mGroupName = groupName;
        clearPairingInt();
        save();
    }

    /**
     * The server's view of the device (renamed, its group renamed)
     */
    synchronized void setDeviceInfo(String deviceName, String groupId, String groupName) {
        if (TextUtils.equals(deviceName, mDeviceName) && TextUtils.equals(groupId, mGroupId) && TextUtils.equals(groupName, mGroupName)) {
            return;
        }

        mDeviceName = deviceName;
        mGroupId = groupId;
        mGroupName = groupName;
        save();
    }

    synchronized void clearAuthorization() {
        clearAuthorizationInt();
        save();
    }

    private void clearAuthorizationInt() {
        mDeviceId = null;
        mToken = null;
        mGroupId = null;
        mGroupName = null;
        mDeviceName = null;
        clearPairingInt();
    }

    @Nullable
    synchronized String getPairingId() {
        return mPairingId != null && System.currentTimeMillis() < mPairingExpiresAt ? mPairingId : null;
    }

    @Nullable
    synchronized String getPairingSecret() {
        return mPairingSecret;
    }

    @Nullable
    public synchronized String getPairingCode() {
        return getPairingId() != null ? mPairingCode : null;
    }

    synchronized void setPairing(String id, String secret, String code, long expiresAt) {
        mPairingId = id;
        mPairingSecret = secret;
        mPairingCode = code;
        mPairingExpiresAt = expiresAt;
        save();
    }

    synchronized void clearPairing() {
        clearPairingInt();
        save();
    }

    private void clearPairingInt() {
        mPairingId = null;
        mPairingSecret = null;
        mPairingCode = null;
        mPairingExpiresAt = 0;
    }

    public synchronized boolean isAllAccounts() {
        return mIsAllAccounts;
    }

    synchronized void setAllAccounts(boolean all) {
        mIsAllAccounts = all;
        save();
    }

    public synchronized Set<String> getAccounts() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(mAccounts));
    }

    synchronized void setAccountSynced(String accountKey, boolean synced) {
        if (synced) {
            mAccounts.add(accountKey);
        } else {
            mAccounts.remove(accountKey);
        }

        save();
    }

    public synchronized boolean isAccountSynced(String accountKey) {
        return mIsAllAccounts || mAccounts.contains(accountKey);
    }

    public synchronized boolean isGlobalSynced() {
        return mIsGlobalSynced;
    }

    synchronized void setGlobalSynced(boolean synced) {
        mIsGlobalSynced = synced;
        save();
    }
}
