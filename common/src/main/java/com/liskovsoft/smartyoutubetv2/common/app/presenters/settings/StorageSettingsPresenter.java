package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.storage.AccountKeys;
import com.liskovsoft.smartyoutubetv2.common.storage.StorageConfig;
import com.liskovsoft.smartyoutubetv2.common.storage.StorageSettings;
import com.liskovsoft.smartyoutubetv2.common.storage.StorageSync;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.reactivex.disposables.Disposable;

/**
 * Settings → System → Storage sync: the server, this device's authorization, the accounts synced and the global settings.
 * These choices are the device's own: never synced (see StorageConfig).
 */
public class StorageSettingsPresenter extends BasePresenter<Void> {
    /**
     * Of the panel and of the dialogs opened over it
     */
    private static final int STORAGE_ID = 152;
    private static Disposable sAction;
    private final StorageSync mSync;
    private StorageSync.State mShownState;
    private String mShownDetail;
    private final StorageSync.Listener mListener = this::onStatusChanged;

    private StorageSettingsPresenter(Context context) {
        super(context);
        mSync = StorageSync.instance(context);
    }

    public static StorageSettingsPresenter instance(Context context) {
        return new StorageSettingsPresenter(context);
    }

    public void show() {
        show(false);
    }

    private void show(boolean replace) {
        Context context = getContext();

        if (context == null) {
            return;
        }

        StorageConfig config = mSync.getConfig();
        StorageSync.Status status = mSync.getStatus();
        mShownState = status.state;
        mShownDetail = status.detail;
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        dialog.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.storage_sync),
                config.isEnabled() ? statusText(context, status) : context.getString(R.string.storage_sync_desc),
                option -> {
                    StorageSettings.setEnabled(context, option.isSelected());
                    show(true);
                },
                config.isEnabled()));

        if (config.isEnabled()) {
            String server = config.getServerUrl() == null ? context.getString(R.string.storage_server_none)
                    : (config.getServerName() != null ? config.getServerName() + "\n" : "") + config.getServerUrl();
            dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.storage_server), server, option -> showServers()));

            if (config.getServerUrl() != null) {
                appendAuthorization(context, dialog, config);
            }

            dialog.appendCheckedCategory(context.getString(R.string.storage_accounts), accountItems(context, config));

            dialog.appendSingleSwitch(UiOptionItem.from(context.getString(R.string.storage_sync_global),
                    context.getString(R.string.storage_sync_global_desc),
                    option -> StorageSettings.setGlobalSynced(context, option.isSelected()),
                    config.isGlobalSynced()));

            if (config.isAuthorized()) {
                dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.storage_sync_now), lastSync(context, status),
                        option -> mSync.wake()));
            }
        }

        mSync.addListener(mListener);
        // With sync off the switch is its only row: a single row would be opened as a dialog of its own, empty
        dialog.enableExpandable(false);
        dialog.enableReplace(replace);
        dialog.setId(STORAGE_ID);

        if (replace) {
            // Built again: titled as the System card opens it, "System - Storage sync" (see SettingsCardsFragment)
            dialog.setTitlePrefix(context.getString(R.string.settings_system));
            dialog.enableNestedTitles(true);
        }

        dialog.showDialog(context.getString(R.string.storage_sync), () -> mSync.removeListener(mListener));

        if (replace) {
            // Only this panel
            dialog.setTitlePrefix(null);
            dialog.enableNestedTitles(false);
        }
    }

    private void appendAuthorization(Context context, AppDialogPresenter dialog, StorageConfig config) {
        if (config.isAuthorized()) {
            dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.storage_group, config.getGroupName()),
                    context.getString(R.string.storage_forget_desc) + "\n" + context.getString(R.string.storage_forget),
                    option -> AppDialogUtil.showConfirmationDialog(context, context.getString(R.string.storage_forget),
                            () -> runAsync(() -> StorageSettings.forgetServer(context)))));
        } else if (config.getPairingCode() != null) {
            dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.storage_code, config.getPairingCode()),
                    context.getString(R.string.storage_code_desc),
                    option -> {
                        StorageSettings.cancelAuthorization(context);
                        show(true);
                    }));
        } else {
            dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.storage_authorize),
                    context.getString(R.string.storage_authorize_desc),
                    option -> runAsync(() -> StorageSettings.requestAuthorization(context))));
        }
    }

    private List<OptionItem> accountItems(Context context, StorageConfig config) {
        List<OptionItem> items = new ArrayList<>();
        GeneralData general = GeneralData.instance(context);

        items.add(UiOptionItem.from(context.getString(R.string.storage_all_accounts),
                option -> {
                    StorageSettings.setAllAccounts(context, option.isSelected());
                    show(true);
                },
                config.isAllAccounts()));

        List<Account> accounts = MediaServiceManager.instance().getAccounts();

        if (accounts != null) {
            Account selected = MediaServiceManager.instance().getSelectedAccount();

            for (Account account : accounts) {
                if (!AccountKeys.isAccount(account)) {
                    continue;
                }

                String key = AccountKeys.of(account);
                String name = account.getName() != null ? account.getName() : key;
                boolean isChildMode = account.equals(selected) && general.isChildModeEnabled();
                items.add(UiOptionItem.from(name, isChildMode ? context.getString(R.string.storage_child_mode) : null,
                        option -> StorageSettings.setAccountSynced(context, key, option.isSelected()),
                        config.isAccountSynced(key)));
            }
        }

        items.add(UiOptionItem.from(context.getString(R.string.storage_signed_out),
                option -> StorageSettings.setAccountSynced(context, AccountKeys.SIGNED_OUT, option.isSelected()),
                config.isAccountSynced(AccountKeys.SIGNED_OUT)));

        return items;
    }

    private static String statusText(Context context, StorageSync.Status status) {
        StorageConfig config = StorageSync.instance(context).getConfig();

        switch (status.state) {
            case NO_SERVER:
                return context.getString(R.string.storage_status_no_server);
            case NOT_AUTHORIZED:
                if ("revoked".equals(status.detail)) {
                    return context.getString(R.string.storage_status_revoked);
                } else if ("denied".equals(status.detail)) {
                    return context.getString(R.string.storage_status_denied);
                } else if ("expired".equals(status.detail)) {
                    return context.getString(R.string.storage_status_expired);
                }
                return context.getString(R.string.storage_status_not_authorized);
            case PAIRING:
                return context.getString(R.string.storage_status_pairing);
            case SYNCED:
                if (config.getAccounts().isEmpty() && !config.isAllAccounts() && !config.isGlobalSynced()) {
                    return context.getString(R.string.storage_accounts_none);
                }
                return context.getString(R.string.storage_status_synced, config.getGroupName(), time(status.lastSyncAt));
            case OFFLINE:
                return context.getString(R.string.storage_status_offline, status.detail != null ? status.detail : "");
            case CONNECTING:
            default:
                return context.getString(R.string.storage_status_connecting);
        }
    }

    private static String lastSync(Context context, StorageSync.Status status) {
        return status.state == StorageSync.State.OFFLINE ? statusText(context, status) : time(status.lastSyncAt);
    }

    private static String time(long ms) {
        return ms == 0 ? "—" : DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(ms));
    }

    /**
     * A change of state while the panel is shown: built again (not for each sync: the focus would jump every few seconds)
     */
    private void onStatusChanged(StorageSync.Status status) {
        Context context = getContext();

        if (context == null) {
            return;
        }

        boolean isAuthorizedNow = mShownState == StorageSync.State.PAIRING && status.state != StorageSync.State.PAIRING
                && mSync.getConfig().isAuthorized();

        if (isAuthorizedNow) {
            MessageHelpers.showMessage(context, context.getString(R.string.storage_authorized, mSync.getConfig().getGroupName()));
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        if (status.state != mShownState || !equalsDetail(status.detail)) {
            if (dialog.isDialogShown() && dialog.getId() == STORAGE_ID) {
                show(true);
            } else {
                mSync.removeListener(mListener);
            }
        }
    }

    private boolean equalsDetail(@Nullable String detail) {
        // The offline reason changes with each retry: not a new state for the panel
        return mShownState == StorageSync.State.OFFLINE || (detail == null ? mShownDetail == null : detail.equals(mShownDetail));
    }

    private void showServers() {
        Context context = getContext();

        if (context == null || RxHelper.isAnyActionRunning(sAction)) {
            return;
        }

        MessageHelpers.showMessage(context, R.string.storage_server_searching);
        sAction = RxHelper.execute(RxHelper.fromCallable(() -> StorageSettings.findServers(context)), this::showServers,
                error -> showServers(new ArrayList<>()));
    }

    private void showServers(List<StorageSettings.Server> servers) {
        Context context = getContext();

        if (context == null) {
            return;
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        String current = mSync.getConfig().getServerUrl();
        List<OptionItem> items = new ArrayList<>();

        for (StorageSettings.Server server : servers) {
            items.add(UiOptionItem.from(server.name + "\n" + server.url, option -> pickServer(server.url), server.url.equals(current)));
        }

        if (items.isEmpty()) {
            dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.storage_server_none_found), option -> {
                dialog.goBack();
                showServers();
            }));
        } else {
            dialog.appendRadioCategory(context.getString(R.string.storage_server), items);
        }

        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.storage_server_enter),
                context.getString(R.string.storage_server_enter_desc), option -> enterServer()));
        dialog.appendSingleButton(UiOptionItem.from(context.getString(R.string.storage_server_search_again), option -> {
            dialog.goBack();
            showServers();
        }));
        dialog.setId(STORAGE_ID);
        dialog.showDialog(context.getString(R.string.storage_server));
    }

    private void enterServer() {
        Context context = getContext();

        if (context == null) {
            return;
        }

        // The text field is behind the panel otherwise, and the panel keeps the keys. Opened again once the address is checked.
        AppDialogPresenter.instance(context).closeDialog();
        String current = mSync.getConfig().getServerUrl();
        SimpleEditDialog.showUrl(context, context.getString(R.string.storage_server_enter), context.getString(R.string.storage_server_enter_desc),
                current != null ? current : "", newValue -> {
                    pickServer(newValue);
                    return true;
                });
    }

    private void pickServer(String input) {
        Context context = getContext();

        if (context == null || RxHelper.isAnyActionRunning(sAction)) {
            return;
        }

        sAction = RxHelper.execute(RxHelper.fromCallable(() -> StorageSettings.setServer(context, input)),
                name -> {
                    AppDialogPresenter dialog = AppDialogPresenter.instance(context);

                    if (dialog.isDialogShown()) {
                        dialog.goBack();
                    }

                    show(true);
                },
                error -> MessageHelpers.showLongMessage(context, context.getString(R.string.storage_server_invalid, message(error))));
    }

    private void runAsync(StorageSettings.Action action) {
        Context context = getContext();

        if (context == null || RxHelper.isAnyActionRunning(sAction)) {
            return;
        }

        sAction = RxHelper.execute(RxHelper.fromCallable(() -> {
                    action.run();
                    return true;
                }), result -> show(true),
                error -> MessageHelpers.showLongMessage(context, message(error)));
    }

    private static String message(Throwable error) {
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }
}
