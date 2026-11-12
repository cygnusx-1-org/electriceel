package com.google.android.exoplayer2.source.sabr;

import androidx.annotation.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Whether the device has a network that reaches the internet, as the app knows it (the library has no Context). With none, a
 * request that can't connect says nothing about its host: no other host is tried and none is marked failed, or one Wi-Fi drop
 * would keep the player off its media network for half an hour (see GoogleVideoHosts#markNetworkFailed).
 */
public final class ValidatedNetwork {
    private static volatile @Nullable BooleanSupplier sSupplier;

    private ValidatedNetwork() {
    }

    public static void setSupplier(@Nullable BooleanSupplier supplier) {
        sSupplier = supplier;
    }

    /**
     * True when the app didn't say (e.g. in tests): today's behaviour
     */
    public static boolean isAvailable() {
        BooleanSupplier supplier = sSupplier;

        return supplier == null || supplier.getAsBoolean();
    }
}
