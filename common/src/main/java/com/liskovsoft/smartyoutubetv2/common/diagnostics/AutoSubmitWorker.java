package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.liskovsoft.leanbackassistant.utils.SafeWorkManager;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.DebuggingData;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Settings → Debugging → Submit data on startup: the data is sent when the app starts and each day it stays running, once there's
 * a network, if player failures were recorded since the last submission. Tried again later when the network or the server fails.
 */
public class AutoSubmitWorker extends Worker {
    private static final String TAG = AutoSubmitWorker.class.getSimpleName();
    private static final String WORK_NAME_ONCE = TAG;
    private static final String WORK_NAME_DAILY = TAG + "Daily";
    /**
     * Past it, the next start or day tries again
     */
    private static final int MAX_RUN_ATTEMPTS = 5;

    public AutoSubmitWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * When the app starts: now (once there's a network), and each day. Turned off: both are cancelled.
     */
    public static void schedule(Context context) {
        SafeWorkManager workManager = SafeWorkManager.getInstance(context);

        if (!DebuggingData.instance(context).isAutoSubmitEnabled()) {
            workManager.cancelUniqueWork(WORK_NAME_ONCE);
            workManager.cancelUniqueWork(WORK_NAME_DAILY);
            return;
        }

        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();

        workManager.enqueueUniqueWork(WORK_NAME_ONCE, ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(AutoSubmitWorker.class)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                        .addTag(TAG)
                        .build());

        workManager.enqueueUniquePeriodicWork(WORK_NAME_DAILY, ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(AutoSubmitWorker.class, 1, TimeUnit.DAYS)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                        .addTag(TAG)
                        .build());
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        if (!DebuggingData.instance(context).isAutoSubmitEnabled()) {
            return Result.success();
        }

        try {
            String id = DataSubmitter.submit(context, true);
            Log.i(TAG, id != null ? "Submitted. ID: " + id : "Nothing new to submit");
            return Result.success();
        } catch (DataSubmitter.SubmitException e) {
            // Too many, or the server's at fault: later. The submission itself was refused (e.g. too big): not again.
            boolean isTemporary = e.code == 429 || e.code >= 500;
            Log.e(TAG, "Can't submit: " + e.getMessage());
            return isTemporary && getRunAttemptCount() < MAX_RUN_ATTEMPTS ? Result.retry() : Result.failure();
        } catch (IOException | RuntimeException e) {
            // The network: later
            Log.e(TAG, "Can't submit: " + e.getMessage());
            return getRunAttemptCount() < MAX_RUN_ATTEMPTS ? Result.retry() : Result.failure();
        }
    }
}
