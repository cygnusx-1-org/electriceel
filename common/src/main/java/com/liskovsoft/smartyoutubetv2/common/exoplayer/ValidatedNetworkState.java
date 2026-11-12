package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.google.android.exoplayer2.source.sabr.ValidatedNetwork;

/**
 * Tells the player's requests whether the device has a network that reaches the internet (see ValidatedNetwork): the active one,
 * validated by Android's check. Asked when a request fails, so no callback is kept.
 */
final class ValidatedNetworkState {
    private ValidatedNetworkState() {
    }

    static void install(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (manager != null) {
            ValidatedNetwork.setSupplier(() -> isValidated(manager));
        }
    }

    private static boolean isValidated(ConnectivityManager manager) {
        try {
            Network network = manager.getActiveNetwork();
            NetworkCapabilities capabilities = network != null ? manager.getNetworkCapabilities(network) : null;

            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (RuntimeException e) { // e.g. a SecurityException of a vendor's build: unknown, as before
            return true;
        }
    }
}
