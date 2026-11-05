package com.liskovsoft.smartyoutubetv2.common.storage;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * What the Storage sync panel changes (see StorageSettingsPresenter). The methods that reach the server block: call them off
 * the main thread.
 */
public final class StorageSettings {
    public interface Action {
        void run() throws IOException;
    }

    public static final class Server {
        public final String name;
        public final String url;

        Server(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    private StorageSettings() {
    }

    private static StorageSync sync(Context context) {
        return StorageSync.instance(context);
    }

    public static void setEnabled(Context context, boolean enabled) {
        sync(context).getConfig().setEnabled(enabled);

        if (enabled) {
            sync(context).start();
        } else {
            sync(context).stop();
        }
    }

    /**
     * Blocking: the servers on the LAN that answer
     */
    public static List<Server> findServers(Context context) {
        List<Server> servers = new ArrayList<>();

        for (StorageDiscovery.Found found : StorageDiscovery.find(context)) {
            servers.add(new Server(found.name, found.url));
        }

        return servers;
    }

    /**
     * Blocking: checks that the address is an electriceel-storage server, then picks it. Returns its name.
     */
    public static String setServer(Context context, String input) throws IOException {
        String url = StorageClient.normalizeUrl(input);

        if (url == null) {
            throw new IOException(input);
        }

        StorageClient.ServerInfo info = new StorageClient(url, null).info();
        sync(context).getConfig().setServer(url, info.name, info.instanceId);
        sync(context).onOptionsChanged();
        return info.name;
    }

    /**
     * Blocking
     */
    public static void requestAuthorization(Context context) throws IOException {
        sync(context).requestAuthorization();
    }

    public static void cancelAuthorization(Context context) {
        sync(context).cancelAuthorization();
    }

    /**
     * Blocking
     */
    public static void forgetServer(Context context) {
        sync(context).forgetServer();
    }

    public static void setAllAccounts(Context context, boolean all) {
        StorageConfig config = sync(context).getConfig();
        boolean wasAll = config.isAllAccounts();
        config.setAllAccounts(all);

        if (wasAll && !all) {
            // The accounts not ticked one by one stop being synced: each joins again if picked later
            sync(context).onOptionsChanged();
            sync(context).onAllAccountsDropped();
        } else {
            sync(context).onOptionsChanged();
        }
    }

    public static void setAccountSynced(Context context, String accountKey, boolean synced) {
        sync(context).getConfig().setAccountSynced(accountKey, synced);

        if (!synced && !sync(context).getConfig().isAccountSynced(accountKey)) {
            sync(context).onScopeDropped("account:" + accountKey);
        } else {
            sync(context).onOptionsChanged();
        }
    }

    public static void setGlobalSynced(Context context, boolean synced) {
        sync(context).getConfig().setGlobalSynced(synced);

        if (synced) {
            sync(context).onOptionsChanged();
        } else {
            sync(context).onScopeDropped("global");
        }
    }
}
