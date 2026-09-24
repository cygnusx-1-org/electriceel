package com.liskovsoft.smartyoutubetv2.common.misc;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;

import com.liskovsoft.sharedutils.helpers.DateHelper;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.BackupSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.OnError;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity.OnResult;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BackupAndRestoreHelper implements OnResult {
    public static final String BACKUP_FOLDER_NAME = "SmartTubeBackup";
    private static final int REQ_PICK_FILES = 1001;
    private static final int REQ_ALL_FILES_ACCESS = 1002;
    // <app_id>_<yyyyMMdd-HHmmss>.zip
    private static final Pattern BACKUP_ZIP_PATTERN = Pattern.compile("^([A-Za-z]\\w*(?:\\.[A-Za-z]\\w*)+)_(\\d{8}-\\d{6})\\.zip$");
    private final Context mContext;
    private Runnable mOnSuccess;
    private Runnable mOnAllFilesAccess;
    private final String[] mPreferredFileManagers = {
            "com.ghisler.android.TotalCommander",
            "com.lonelycatgames.Xplore",
            "com.alphainventor.filemanager",
            "pl.solidexplorer2"
    };

    public BackupAndRestoreHelper(Context context) {
        mContext = context;
    }

    public void exportAppMediaFolder() {
        File mediaDir = FileHelpers.getExternalMediaDirectory(mContext);
        File dataDir = new File(mediaDir, "data");
        if (!dataDir.exists() || FileHelpers.isEmpty(dataDir) || VERSION.SDK_INT < 29) return;

        String backupZipName = getSavedBackupZipName();

        MediaStoreFile file = new MediaStoreFile(mContext, backupZipName, BACKUP_FOLDER_NAME);
        if (!file.isWritable()) {
            backupZipName = createBackupZipNameWithTimestamp();
            getGeneralData().setBackupZipName(backupZipName);
            file = new MediaStoreFile(mContext, backupZipName, BACKUP_FOLDER_NAME);
        }

        if (!file.isWritable()) {
            deleteTimeStamp(); // User copied full old media directory (with the old timestamp)
            backupZipName = createBackupZipNameWithTimestamp();
            getGeneralData().setBackupZipName(backupZipName);
            file = new MediaStoreFile(mContext, backupZipName, BACKUP_FOLDER_NAME);
        }

        if (file.isWritable()) {
            final File zipFile = new File(mediaDir, backupZipName);
            ZipHelper2.zipDirectory(dataDir, zipFile);

            if (zipFile.exists()) {
                file.copyFrom(zipFile);
                // Delete temporary zip
                zipFile.delete();
            }
        }

        //Uri uri = FileProvider.getUriForFile(
        //        mContext,
        //        mContext.getPackageName() + ".update_provider",
        //        zipFile
        //);
        //
        //try {
        //    openFileManager(uri);
        //} catch (Exception e) {
        //    // Activity launch may fail if called from background (e.g. WorkManager)
        //    e.printStackTrace();
        //}
    }

    private void openFileManager(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        //intent.setType("application/zip");
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PackageManager pm = mContext.getPackageManager();

        for (String pkg : mPreferredFileManagers) {
            Intent targeted = new Intent(intent);
            targeted.setPackage(pkg);
            if (targeted.resolveActivity(pm) != null) {
                mContext.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                mContext.startActivity(targeted);
                return;
            }
        }

        mContext.startActivity(Intent.createChooser(intent, mContext.getString(R.string.app_backup)));
    }

    /**
     * NOTE: The file picker relies on apps that support the Storage Access Framework (SAF).
     * At the moment, no known third-party file manager properly supports selecting ZIP
     * archives through this API, so the backup file may not appear in the picker.
     */
    public void importAppMediaFolder(Runnable onSuccess) {
        if (VERSION.SDK_INT < 19 || onSuccess == null) {
            return;
        }

        mOnSuccess = onSuccess;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        //intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        //intent.addCategory(Intent.CATEGORY_OPENABLE);
        //intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
        //        "application/zip",
        //        "application/x-zip-compressed"
        //});

        ((MotherActivity) mContext).addOnResult(this);

        ((Activity) mContext).startActivityForResult(intent, REQ_PICK_FILES);
    }

    @Override
    public void onResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK_FILES && resultCode == Activity.RESULT_OK) {
            if (data == null) return;

            Uri uri = data.getData();
            if (uri == null && data.getClipData() != null) {
                uri = data.getClipData().getItemAt(0).getUri();
            }

            unpackTempZip(uri, () -> mOnSuccess.run(), null);
        } else if (requestCode == REQ_ALL_FILES_ACCESS) {
            // The settings page always returns RESULT_CANCELED. Show what we have either way.
            Runnable onDone = mOnAllFilesAccess;
            mOnAllFilesAccess = null;
            if (onDone != null) {
                onDone.run();
            }
        }
    }

    /**
     * Every app id's zips in Documents/SmartTubeBackup are readable as plain files
     */
    public boolean hasBackupDirAccess() {
        return hasAllFilesAccess() || hasLegacyBackupDirAccess();
    }

    /**
     * Android 11+: Settings - Apps - Special app access - All files access
     */
    public boolean hasAllFilesAccess() {
        return VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager();
    }

    /**
     * Opens the "All files access" settings page of this app
     * @return false if the page couldn't be shown (onDone won't be called)
     */
    public boolean requestAllFilesAccess(Runnable onDone) {
        if (VERSION.SDK_INT < 30 || !(mContext instanceof MotherActivity)) {
            return false;
        }

        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", mContext.getPackageName(), null));

        if (intent.resolveActivity(mContext.getPackageManager()) == null) {
            return false;
        }

        mOnAllFilesAccess = onDone;
        ((MotherActivity) mContext).addOnResult(this);

        try {
            ((Activity) mContext).startActivityForResult(intent, REQ_ALL_FILES_ACCESS);
        } catch (ActivityNotFoundException e) {
            mOnAllFilesAccess = null;
            return false;
        }

        return true;
    }

    /**
     * Legacy storage (targetSdk &lt; 29) + the storage permission
     */
    public boolean hasLegacyBackupDirAccess() {
        return hasLegacyStorage() && (VERSION.SDK_INT < 23 ||
                mContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    public boolean hasLegacyStorage() {
        return VERSION.SDK_INT < 29 || Environment.isExternalStorageLegacy();
    }

    public void handleIncomingZip(Intent intent) {
        if (intent == null) {
            return;
        }

        Uri zipUri = null;

        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            zipUri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            zipUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }

        unpackTempZip(
                zipUri,
                () -> BackupSettingsPresenter.instance(mContext).showLocalRestoreDialogApi30(),
                error -> MessageHelpers.showLongMessage(mContext, "Failed to restore backup: " + error.getMessage())
        );
    }

    public void unpackTempZip(File tempZip) {
        if (!tempZip.exists()) {
            return;
        }

        // Target folder: /Android/media/<package>/data
        File mediaDir = FileHelpers.getExternalMediaDirectory(mContext);
        File dataDir = new File(mediaDir, "data");

        // Remove old data
        if (dataDir.exists()) FileHelpers.delete(dataDir);

        if (ZipHelper2.hasRootDir(tempZip, "data")) {
            // Unpack ZIP with data folder
            ZipHelper2.unzip(tempZip, mediaDir);
        } else {
            // Seems we've packed the contents of the data dir not data itself
            ZipHelper2.unzip(tempZip, dataDir);
        }

        // Delete the temporary ZIP
        tempZip.delete();
    }

    private void unpackTempZip(Uri zipUri, Runnable onSuccess, OnError onError) {
        if (zipUri == null) {
            MessageHelpers.showLongMessage(mContext, "No ZIP received");
            return;
        }

        // Cannot use async calls because the app usually isn't started yet.
        try {
            unpackTempZip(zipUri);
            if (onSuccess != null) {
                onSuccess.run();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            if (onError != null) {
                onError.onError(ex);
            }
        }
    }

    private void unpackTempZip(Uri zipUri) {
        if (zipUri == null) {
            return;
        }

        File mediaDir = FileHelpers.getExternalMediaDirectory(mContext);

        // Copy ZIP from URI to the temporary file
        String backupZipName = getSavedBackupZipName();
        File tempZip = new File(mediaDir, backupZipName);
        copyUriToFile(zipUri, tempZip);

        unpackTempZip(tempZip);
    }

    private void copyUriToDir(Uri uri, File targetDir) {
        try {
            String fileName = getFileName(uri);
            if (fileName == null) fileName = "imported_" + DateHelper.createTimestamp();

            File outFile = new File(targetDir, fileName);

            InputStream in = mContext.getContentResolver().openInputStream(uri);
            OutputStream out = new FileOutputStream(outFile);

            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }

            in.close();
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Failed to copyUriToDir", e);
        }
    }

    private void copyUriToFile(Uri uri, File outFile) {
        try {
            InputStream in = mContext.getContentResolver().openInputStream(uri);
            OutputStream out = new FileOutputStream(outFile);

            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }

            in.close();
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Failed to copyUriToFile", e);
        }
    }

    private String getFileName(Uri uri) {
        Cursor cursor = mContext.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            cursor.moveToFirst();
            String name = cursor.getString(nameIndex);
            cursor.close();
            return name;
        }
        return null;
    }

    /**
     * Backup zips in Documents/SmartTubeBackup named &lt;app_id&gt;_&lt;timestamp&gt;.zip (any app id), newest first
     */
    public List<String> getBackupZipNames() {
        Map<String, Long> zips = new HashMap<>();

        // Direct access (All files access or legacy storage): sees zips of every app id
        File[] files = getBackupZipDir().listFiles();

        if (files != null) {
            for (File file : files) {
                if (isRestorableZip(file.getName())) {
                    zips.put(file.getName(), file.lastModified());
                }
            }
        }

        // MediaStore: always sees zips made by this app id
        if (VERSION.SDK_INT >= 29) {
            String[] projection = { MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_MODIFIED };
            String selection = MediaStore.MediaColumns.RELATIVE_PATH + "=?";
            String[] selectionArgs = { Environment.DIRECTORY_DOCUMENTS + "/" + BACKUP_FOLDER_NAME + "/" };

            try (Cursor cursor = mContext.getContentResolver().query(
                    MediaStore.Files.getContentUri("external"), projection, selection, selectionArgs, null)) {
                while (cursor != null && cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    if (isRestorableZip(name) && !zips.containsKey(name)) {
                        zips.put(name, cursor.getLong(1) * 1_000);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        List<String> result = new ArrayList<>(zips.keySet());
        Collections.sort(result, (a, b) -> Long.compare(zips.get(b), zips.get(a)));

        return result;
    }

    /**
     * Unpack a zip from Documents/SmartTubeBackup into /Android/media/&lt;package&gt;/data
     */
    public boolean unpackBackupZip(String zipName) {
        File tempZip = new File(FileHelpers.getExternalMediaDirectory(mContext), zipName);
        File source = new File(getBackupZipDir(), zipName);

        if (tempZip.exists()) {
            tempZip.delete();
        }

        try {
            if (source.canRead()) {
                FileHelpers.copy(source, tempZip);
            } else if (VERSION.SDK_INT >= 29) {
                new MediaStoreFile(mContext, zipName, BACKUP_FOLDER_NAME).copyTo(tempZip);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!tempZip.exists()) {
            return false;
        }

        unpackTempZip(tempZip);

        return true;
    }

    /**
     * Zip name format: &lt;app_id&gt;_&lt;timestamp&gt;.zip, e.g. org.smarttube.stable_20260923-211959.zip
     */
    public static String getZipPackageName(String zipName) {
        if (zipName == null) {
            return null;
        }

        Matcher matcher = BACKUP_ZIP_PATTERN.matcher(zipName);

        return matcher.matches() ? matcher.group(1) : null;
    }

    private static boolean isRestorableZip(String name) {
        return getZipPackageName(name) != null;
    }

    private static File getBackupZipDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), BACKUP_FOLDER_NAME);
    }

    private GeneralData getGeneralData() {
        return GeneralData.instance(mContext);
    }
    
    private String createBackupZipNameWithTimestamp() {
        return mContext.getPackageName() + "_" + getTimeStamp() + ".zip";
    }

    private String getTimeStamp() {
        File timestampFile = getTimestampFile();
        if (timestampFile.exists()) {
            return FileHelpers.getFileContents(timestampFile);
        }

        String timestamp = DateHelper.createTimestamp();
        FileHelpers.stringToFile(timestamp, timestampFile);
        return timestamp;
    }

    private void deleteTimeStamp() {
        File timestampFile = getTimestampFile();
        if (timestampFile.exists()) {
            timestampFile.delete();
        }
    }

    private File getTimestampFile() {
        File mediaDir = FileHelpers.getExternalMediaDirectory(mContext);
        File timestampFile = new File(mediaDir, "timestamp.txt");
        return timestampFile;
    }

    private String getSavedBackupZipName() {
        String oldBackupZipName = getGeneralData().getBackupZipName();
        if (oldBackupZipName == null || !oldBackupZipName.endsWith(".zip")) {
            oldBackupZipName = createBackupZipNameWithTimestamp();
            getGeneralData().setBackupZipName(oldBackupZipName);
        }
        return oldBackupZipName;
    }
}
