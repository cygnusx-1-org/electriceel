package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataSaverBase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Settings → Debugging. The same for every account.<br/>
 * Synchronized: read and numbered on the main thread when a failure is recorded, and in the background when the report is built.
 */
public class DebuggingData extends DataSaverBase {
    // A value not set yet before one that is gets saved as "null", which reads back as false, not as the default (see
    // DataSaverBase#checkCapacity): every flag here is off by default
    private static final int ACCOUNT_KEYS = 0;
    /**
     * Video IDs are included by default: it's kept as excluded
     */
    private static final int VIDEO_IDS_EXCLUDED = 1;
    private static final int AUTO_SUBMIT = 2;
    private static final int LAST_SUBMITTED_FAILURE_TS = 3;
    private static final int LAST_SUBMISSION_ID = 4;
    private static final int LAST_SUBMISSION_TIME = 5;
    private static final int LAST_APP_EXIT_TS = 6;
    private static final int START_GRAPH = 7;
    private static final String KEYS_DELIM = ",";
    private static DebuggingData sInstance;

    private DebuggingData(Context context) {
        super(context, true);
    }

    public static synchronized DebuggingData instance(Context context) {
        if (sInstance == null) {
            sInstance = new DebuggingData(context);
        }

        return sInstance;
    }

    static void resetInstanceForTesting() {
        sInstance = null;
    }

    /**
     * The submitted player failures have their video IDs. They're always recorded on the device.
     */
    public synchronized boolean isVideoIdsIncluded() {
        return !getBoolean(VIDEO_IDS_EXCLUDED, false);
    }

    public synchronized void setVideoIdsIncluded(boolean include) {
        setBoolean(VIDEO_IDS_EXCLUDED, !include);
    }

    /**
     * When the app starts, the data is submitted if player failures were recorded since the last submission
     */
    public synchronized boolean isAutoSubmitEnabled() {
        return getBoolean(AUTO_SUBMIT, false);
    }

    public synchronized void setAutoSubmitEnabled(boolean enable) {
        setBoolean(AUTO_SUBMIT, enable);
    }

    /**
     * The bug button in the player shows where the time of the video's start went, with its stats (see VideoStartController)
     */
    public synchronized boolean isStartGraphShown() {
        return getBoolean(START_GRAPH, false);
    }

    public synchronized void setStartGraphShown(boolean show) {
        setBoolean(START_GRAPH, show);
    }

    /**
     * The time of the newest player failure submitted, 0 when none was
     */
    public synchronized long getLastSubmittedFailureTs() {
        return Helpers.parseLong(getString(LAST_SUBMITTED_FAILURE_TS, null), 0);
    }

    /**
     * The ID the server gave the last submission, null when there was none
     */
    @Nullable
    public synchronized String getLastSubmissionId() {
        return getString(LAST_SUBMISSION_ID, null);
    }

    public synchronized long getLastSubmissionTime() {
        return Helpers.parseLong(getString(LAST_SUBMISSION_TIME, null), 0);
    }

    public synchronized void setLastSubmission(String id, long newestFailureTs, long time) {
        setString(LAST_SUBMITTED_FAILURE_TS, String.valueOf(newestFailureTs));
        setString(LAST_SUBMISSION_ID, id);
        setString(LAST_SUBMISSION_TIME, String.valueOf(time));
    }

    /**
     * Of the newest app exit recorded (see AppExits), 0 when none was
     */
    public synchronized long getLastAppExitTs() {
        return Helpers.parseLong(getString(LAST_APP_EXIT_TS, null), 0);
    }

    public synchronized void setLastAppExitTs(long ts) {
        setString(LAST_APP_EXIT_TS, String.valueOf(ts));
    }

    /**
     * Of the account in use, see {@link #getAccountNumber(Account)}
     */
    public int getAccountNumber() {
        return getAccountNumber(MediaServiceManager.instance().getSelectedAccount());
    }

    /**
     * Tells the accounts apart without naming them: 1, 2, 3… in the order this device first saw them, 0 when signed out.
     * Only a hash of the account's profile name is kept, to know it again.
     */
    public synchronized int getAccountNumber(@Nullable Account account) {
        String profileName = account != null ? account.getProfileName() : null;

        if (profileName == null) {
            return 0;
        }

        String key = hash(profileName);
        List<String> keys = getAccountKeys();
        int index = keys.indexOf(key);

        if (index == -1) {
            keys.add(key);
            setString(ACCOUNT_KEYS, Helpers.join(KEYS_DELIM, keys.toArray(new CharSequence[0])));
            index = keys.size() - 1;
        }

        return index + 1;
    }

    private List<String> getAccountKeys() {
        String keys = getString(ACCOUNT_KEYS, null);

        return keys == null || keys.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(keys.split(KEYS_DELIM)));
    }

    /**
     * The first 64 bits of SHA-256, in hex: enough to tell a device's few accounts apart
     */
    private static String hash(String profileName) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(profileName.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < 8; i++) {
                result.append(Character.forDigit((digest[i] >> 4) & 0xF, 16)).append(Character.forDigit(digest[i] & 0xF, 16));
            }

            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every Android has SHA-256
        }
    }
}
