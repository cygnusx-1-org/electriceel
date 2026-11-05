package com.liskovsoft.smartyoutubetv2.common.storage;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Finds electriceel-storage servers on the LAN: DNS-SD (mDNS) _eestorage._tcp, with Android's NsdManager. Blocking: call it
 * off the main thread. A server in a container without the host's network isn't found: its address is entered by hand then.
 */
final class StorageDiscovery {
    private static final String TAG = StorageDiscovery.class.getSimpleName();
    private static final String SERVICE_TYPE = "_eestorage._tcp";
    private static final long BROWSE_MS = 4_000;
    private static final long RESOLVE_MS = 3_000;

    static final class Found {
        final String name;
        final String url;
        @Nullable
        final String instanceId;

        Found(String name, String url, @Nullable String instanceId) {
            this.name = name;
            this.url = url;
            this.instanceId = instanceId;
        }
    }

    private StorageDiscovery() {
    }

    /**
     * The servers that answered within a few seconds, each checked with /api/v1/info
     */
    static List<Found> find(Context context) {
        NsdManager nsd = (NsdManager) context.getApplicationContext().getSystemService(Context.NSD_SERVICE);
        List<Found> result = new ArrayList<>();

        if (nsd == null) {
            return result;
        }

        Map<String, NsdServiceInfo> services = new LinkedHashMap<>();
        CountDownLatch started = new CountDownLatch(1);
        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.w(TAG, "Discovery failed: %s", errorCode);
                started.countDown();
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                started.countDown();
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo info) {
                synchronized (services) {
                    services.put(info.getServiceName(), info);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo info) {
            }
        };

        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener);
            started.await(2, TimeUnit.SECONDS);
            Thread.sleep(BROWSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            Log.e(TAG, "Discovery: %s", e.getMessage());
        } finally {
            try {
                nsd.stopServiceDiscovery(listener);
            } catch (RuntimeException e) {
                // Never started
            }
        }

        List<NsdServiceInfo> found;

        synchronized (services) {
            found = new ArrayList<>(services.values());
        }

        // One at a time: before Android 14 a second resolve while one runs fails (FAILURE_ALREADY_ACTIVE)
        for (NsdServiceInfo info : found) {
            NsdServiceInfo resolved = resolve(nsd, info);
            Found server = resolved != null ? toFound(resolved) : null;

            if (server == null) {
                continue;
            }

            try {
                StorageClient.ServerInfo serverInfo = new StorageClient(server.url, null).info();
                String url = serverInfo.publicUrl != null && !serverInfo.publicUrl.isEmpty() ? serverInfo.publicUrl : server.url;
                result.add(new Found(serverInfo.name, url, serverInfo.instanceId));
            } catch (Exception e) {
                Log.w(TAG, "%s doesn't answer: %s", server.url, e.getMessage());
            }
        }

        return result;
    }

    @Nullable
    private static NsdServiceInfo resolve(NsdManager nsd, NsdServiceInfo info) {
        NsdServiceInfo[] out = new NsdServiceInfo[1];
        CountDownLatch done = new CountDownLatch(1);

        try {
            nsd.resolveService(info, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.w(TAG, "Resolve of %s failed: %s", serviceInfo.getServiceName(), errorCode);
                    done.countDown();
                }

                @Override
                public void onServiceResolved(NsdServiceInfo serviceInfo) {
                    out[0] = serviceInfo;
                    done.countDown();
                }
            });
            done.await(RESOLVE_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            Log.w(TAG, "Resolve: %s", e.getMessage());
        }

        return out[0];
    }

    @Nullable
    private static Found toFound(NsdServiceInfo info) {
        Map<String, byte[]> txt = info.getAttributes();
        String url = attribute(txt, "url");
        String id = attribute(txt, "id");

        if (url == null) {
            InetAddress host = Build.VERSION.SDK_INT >= 34 && !info.getHostAddresses().isEmpty() ? info.getHostAddresses().get(0) : info.getHost();

            if (host == null) {
                return null;
            }

            String scheme = "https".equals(attribute(txt, "scheme")) ? "https" : "http";
            String address = host instanceof Inet6Address ? "[" + host.getHostAddress().replaceAll("%.*$", "") + "]" : host.getHostAddress();
            url = scheme + "://" + address + ":" + info.getPort();
        }

        return new Found(info.getServiceName(), url, id);
    }

    @Nullable
    private static String attribute(@Nullable Map<String, byte[]> txt, String key) {
        byte[] value = txt != null ? txt.get(key) : null;
        return value != null && value.length > 0 ? new String(value, StandardCharsets.UTF_8) : null;
    }
}
