package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Environment;

import com.liskovsoft.mediaserviceinterfaces.data.ItemGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.DateHelper;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.BackupAndRestoreHelper;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaStoreFile;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeMediaItemService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import arte.programar.materialfile.MaterialFilePicker;
import arte.programar.materialfile.ui.FilePickerActivity;
import arte.programar.materialfile.utils.FileUtils;
import io.reactivex.disposables.Disposable;

/**
 * Exports the playlists the user picks to a file next to the backups, and imports the ones he picks from such a file
 * (or from a list of YouTube links) to this device or to YouTube, each a new playlist under a title of its own
 */
public class PlaylistsBackupPresenter extends BasePresenter<Void> {
    private static final String TAG = PlaylistsBackupPresenter.class.getSimpleName();
    private static final int FILE_PICKER_REQUEST_CODE = 206;
    @SuppressLint("StaticFieldLeak")
    private static PlaylistsBackupPresenter sInstance;
    private Disposable mAction;

    private PlaylistsBackupPresenter(Context context) {
        super(context);
    }

    public static PlaylistsBackupPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new PlaylistsBackupPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void exportPlaylists() {
        RxHelper.disposeActions(mAction);

        // No playlists (null) is no result
        List<MediaItem> playlists = new ArrayList<>();
        mAction = RxHelper.execute(getContentService().getPlaylistsObserve(),
                group -> {
                    if (group.getMediaItems() != null) {
                        playlists.addAll(group.getMediaItems());
                    }
                },
                this::showError,
                () -> showExportDialog(playlists));
    }

    private void showExportDialog(List<MediaItem> playlists) {
        if (playlists.isEmpty()) {
            MessageHelpers.showMessage(getContext(), R.string.nothing_found);
            return;
        }

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());
        boolean[] picked = new boolean[playlists.size()];

        for (int i = 0; i < playlists.size(); i++) {
            MediaItem playlist = playlists.get(i);
            int index = i;
            dialogPresenter.appendSingleSwitch(UiOptionItem.from(playlist.getTitle(), playlist.getBadgeText(),
                    option -> picked[index] = option.isSelected(), false));
        }

        dialogPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.export_playlists), option -> {
            List<MediaItem> result = new ArrayList<>();

            for (int i = 0; i < playlists.size(); i++) {
                if (picked[i]) {
                    result.add(playlists.get(i));
                }
            }

            if (result.isEmpty()) {
                MessageHelpers.showMessage(getContext(), R.string.no_playlists_picked);
                return;
            }

            dialogPresenter.goBack();
            exportPlaylists(result);
        }));

        dialogPresenter.showDialog(getContext().getString(R.string.export_playlists));
    }

    private void exportPlaylists(List<MediaItem> playlists) {
        // Before 10 the file is written to the storage directly
        if (VERSION.SDK_INT < 29 && !PermissionHelpers.hasStoragePermissions(getContext())) {
            requestStoragePermissions(() -> exportPlaylists(playlists));
            return;
        }

        MessageHelpers.showMessage(getContext(), R.string.loading);

        RxHelper.disposeActions(mAction);
        mAction = RxHelper.execute(YouTubeMediaItemService.instance().exportPlaylistsObserve(playlists)
                        .flatMap(content -> RxHelper.fromCallable(() -> writeExportFile(content))),
                name -> MessageHelpers.showLongMessage(getContext(), getContext().getString(R.string.playlists_exported, name)),
                this::showError);
    }

    /**
     * Documents/SmartTubeBackup/&lt;app_id&gt;_playlists_&lt;yyyyMMdd-HHmmss&gt;.json, by the backup zips
     *
     * @return the name of the file (the folder is shown on the button, see BackupSettingsPresenter)
     */
    private String writeExportFile(String content) throws IOException {
        String fileName = String.format("%s_playlists_%s.json", getContext().getPackageName(), DateHelper.createTimestamp());
        byte[] data = content.getBytes(StandardCharsets.UTF_8);

        if (VERSION.SDK_INT >= 29) {
            MediaStoreFile file = new MediaStoreFile(getContext(), fileName, BackupAndRestoreHelper.BACKUP_FOLDER_NAME);

            try (OutputStream out = file.openOutputStream()) {
                if (out == null) {
                    throw new IOException("Can't create " + fileName);
                }

                out.write(data);
            }

            // MediaStore renames it when there is one with the name
            String storedName = file.getStoredName();

            return storedName != null ? storedName : fileName;
        }

        File dir = getBackupDir();

        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Can't create " + dir.getAbsolutePath());
        }

        File file = new File(dir, fileName);

        try (OutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }

        return file.getName();
    }

    public void importPlaylists() {
        // Before 11 the storage permission is enough. Since 13 it can't be granted.
        if (VERSION.SDK_INT < 30) {
            if (PermissionHelpers.hasStoragePermissions(getContext())) {
                runFilePicker();
            } else {
                requestStoragePermissions(this::runFilePicker);
            }
            return;
        }

        BackupAndRestoreHelper helper = new BackupAndRestoreHelper(getContext());

        // A file copied here from another device isn't listed without it, as for Restore.
        // Posted: a result listener added while the result is handed out is dropped (see MotherActivity.onActivityResult).
        if (helper.hasAllFilesAccess() || !helper.requestAllFilesAccess(() -> Utils.post(this::runFilePicker))) {
            runFilePicker();
        }
    }

    private void runFilePicker() {
        MotherActivity activity = getContext() instanceof MotherActivity ? (MotherActivity) getContext() : null;
        File storageDir = activity != null ? FileUtils.getFile(activity, null) : null;

        if (storageDir == null) {
            Log.e(TAG, "Storage device is unavailable");
            return;
        }

        activity.addOnResult((requestCode, resultCode, data) -> {
            String filePath = data != null ? data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH) : null;

            if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK && filePath != null) {
                readPlaylists(new File(filePath));
            }
        });

        MaterialFilePicker picker = new MaterialFilePicker()
                .withActivity(activity)
                .withTitle(activity.getString(R.string.import_playlists))
                .withRootPath(storageDir.getAbsolutePath());

        if (getBackupDir().isDirectory()) {
            picker.withPath(getBackupDir().getAbsolutePath());
        }

        picker.start(FILE_PICKER_REQUEST_CODE);
    }

    private void readPlaylists(File file) {
        RxHelper.disposeActions(mAction);
        mAction = RxHelper.execute(YouTubeMediaItemService.instance().readPlaylistsObserve(file), this::showImportDialog, this::showError);
    }

    private void showImportDialog(List<ItemGroup> playlists) {
        if (playlists.isEmpty()) {
            MessageHelpers.showMessage(getContext(), R.string.nothing_found);
            return;
        }

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());
        boolean[] picked = new boolean[playlists.size()];
        // The file was made to be imported
        Arrays.fill(picked, true);

        for (int i = 0; i < playlists.size(); i++) {
            ItemGroup playlist = playlists.get(i);
            int index = i;
            int videoCount = playlist.getItems().size();
            dialogPresenter.appendSingleSwitch(UiOptionItem.from(playlist.getTitle(),
                    getContext().getResources().getQuantityString(R.plurals.playlist_videos, videoCount, videoCount),
                    option -> picked[index] = option.isSelected(), true));
        }

        dialogPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.import_to_device),
                option -> importPlaylists(playlists, picked, false)));

        Account account = getSignInService().isSigned() ? getSignInService().getSelectedAccount() : null;
        // YouTube refuses to create a playlist for an account without a channel
        String unavailableReason = account == null ? getContext().getString(R.string.msg_signed_users_only) :
                !account.hasChannel() ? getContext().getString(R.string.create_playlist_requires_channel) : null;
        OptionItem toYouTubeButton = UiOptionItem.from(getContext().getString(R.string.import_to_youtube), unavailableReason,
                option -> importPlaylists(playlists, picked, true));
        toYouTubeButton.setEnabled(unavailableReason == null);
        dialogPresenter.appendSingleButton(toYouTubeButton);

        dialogPresenter.showDialog(getContext().getString(R.string.import_playlists));
    }

    private void importPlaylists(List<ItemGroup> playlists, boolean[] picked, boolean toYouTube) {
        List<ItemGroup> result = new ArrayList<>();

        for (int i = 0; i < playlists.size(); i++) {
            if (picked[i]) {
                result.add(playlists.get(i));
            }
        }

        if (result.isEmpty()) {
            MessageHelpers.showMessage(getContext(), R.string.no_playlists_picked);
            return;
        }

        // A rename dialog may follow
        AppDialogPresenter.instance(getContext()).closeDialog();

        RxHelper.disposeActions(mAction);
        mAction = RxHelper.execute(YouTubeMediaItemService.instance().getPlaylistTitlesObserve(),
                takenTitles -> pickTitles(result, 0, takenTitles, new LinkedHashMap<>(), toYouTube),
                this::showError);
    }

    /**
     * Each playlist under a title of its own: one taken by a playlist here or on YouTube, or by one picked before it,
     * is renamed by the user or skipped, never merged
     */
    private void pickTitles(List<ItemGroup> playlists, int index, Set<String> takenTitles, Map<String, ItemGroup> result, boolean toYouTube) {
        for (int i = index; i < playlists.size(); i++) {
            ItemGroup playlist = playlists.get(i);
            String title = playlist.getTitle().trim();

            if (!isTaken(title, takenTitles, result)) {
                result.put(title, playlist);
                continue;
            }

            int next = i + 1;
            SimpleEditDialog.show(getContext(), getContext().getString(R.string.playlist_exists_rename, title),
                    getContext().getString(R.string.rename_playlist), title,
                    newValue -> {
                        String newTitle = newValue.trim();

                        if (newTitle.isEmpty()) {
                            return false;
                        }

                        if (isTaken(newTitle, takenTitles, result)) {
                            MessageHelpers.showMessage(getContext(), getContext().getString(R.string.playlist_exists, newTitle));
                            return false;
                        }

                        result.put(newTitle, playlist);
                        return true;
                    },
                    // Renamed or skipped
                    () -> pickTitles(playlists, next, takenTitles, result, toYouTube));
            return;
        }

        if (result.isEmpty()) {
            return;
        }

        MessageHelpers.showMessage(getContext(), R.string.loading);

        RxHelper.disposeActions(mAction);
        mAction = RxHelper.execute(YouTubeMediaItemService.instance().importPlaylistsObserve(result, toYouTube),
                error -> {
                    // The ones before it are added
                    showError(error);
                    refreshPlaylistsSection();
                },
                () -> {
                    MessageHelpers.showMessage(getContext(), R.string.msg_done);
                    refreshPlaylistsSection();
                });
    }

    private static boolean isTaken(String title, Set<String> takenTitles, Map<String, ItemGroup> result) {
        return takenTitles.contains(title) || result.containsKey(title);
    }

    private void refreshPlaylistsSection() {
        BrowsePresenter presenter = BrowsePresenter.instance(getContext());

        if (presenter.isPlaylistsSection()) {
            presenter.refresh();
        }
    }

    private void requestStoragePermissions(Runnable onGranted) {
        if (!(getContext() instanceof MotherActivity)) {
            return;
        }

        MotherActivity activity = (MotherActivity) getContext();
        activity.addOnPermissions((requestCode, permissions, grantResults) -> {
            if (requestCode == PermissionHelpers.REQUEST_EXTERNAL_STORAGE
                    && grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onGranted.run();
            }
        });
        PermissionHelpers.verifyStoragePermissions(activity);
    }

    private void showError(Throwable error) {
        Log.e(TAG, error.getMessage());
        MessageHelpers.showLongMessage(getContext(), error.getMessage());
    }

    /**
     * Where the playlists are exported to: by the backup zips
     */
    static File getBackupDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), BackupAndRestoreHelper.BACKUP_FOLDER_NAME);
    }
}
