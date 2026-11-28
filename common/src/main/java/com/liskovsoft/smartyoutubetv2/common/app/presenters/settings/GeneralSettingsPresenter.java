package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.okhttp.DnsCheck;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.sharedutils.okhttp.PreferredDns;
import com.liskovsoft.sharedutils.okhttp.PublicDnsResolver;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsRows;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.NetworkData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.common.proxy.ProxyManager;
import com.liskovsoft.smartyoutubetv2.common.proxy.WebProxyDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class GeneralSettingsPresenter extends BasePresenter<Void> {
    private final GeneralData mGeneralData;
    private final PlayerData mPlayerData;
    private final PlayerTweaksData mPlayerTweaksData;
    private final MainUIData mMainUIData;
    private final MediaServiceData mMediaServiceData;
    private final NetworkData mNetworkData;
    private boolean mRestartApp;
    /**
     * The check of the DNS servers: its button in the card shown, its lines and the settings they're of (see appendDnsCheck)
     */
    private OptionItem mDnsCheckItem;
    private CharSequence mDnsCheckResult;
    private String mDnsCheckKey;
    private boolean mIsDnsChecking;
    private final Runnable mOnFinish = () -> {
        if (mRestartApp) {
            mRestartApp = false;
            MessageHelpers.showLongMessage(getContext(), R.string.msg_restart_app);
        }
    };

    private GeneralSettingsPresenter(Context context) {
        super(context);
        mGeneralData = GeneralData.instance(context);
        mPlayerData = PlayerData.instance(context);
        mPlayerTweaksData = PlayerTweaksData.instance(context);
        mMainUIData = MainUIData.instance(context);
        mMediaServiceData = MediaServiceData.instance();
        mNetworkData = NetworkData.instance(context);
    }

    public static GeneralSettingsPresenter instance(Context context) {
        return new GeneralSettingsPresenter(context);
    }

    /**
     * The Background playback card of the System settings, in General (was in the General panel)
     */
    public void showBackgroundPlayback() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.category_background_playback),
                this::appendBackgroundPlaybackRows, mOnFinish);
    }

    /**
     * The rows of the panel showBackgroundPlayback shows (see SettingsPanel)
     */
    public void appendBackgroundPlaybackRows(SettingsRows settingsPresenter) {
        appendBackgroundPlaybackCategory(settingsPresenter);
    }

    /**
     * The Screen dimming card of the System settings, in General (was in the General panel)
     */
    public void showScreenDimming() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.screen_dimming),
                this::appendScreenDimmingRows, mOnFinish);
    }

    /**
     * The rows of the panel showScreenDimming shows (see SettingsPanel)
     */
    public void appendScreenDimmingRows(SettingsRows settingsPresenter) {
        appendScreenDimmingAmountCategory(settingsPresenter);
        appendScreenDimmingTimeoutCategory(settingsPresenter);
    }

    /**
     * The Key remapping card of the System settings, in General (was in the General panel)
     */
    public void showKeyRemapping() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.key_remapping),
                this::appendKeyRemappingRows, mOnFinish);
    }

    /**
     * The rows of the panel showKeyRemapping shows (see SettingsPanel)
     */
    public void appendKeyRemappingRows(SettingsRows settingsPresenter) {
        appendKeyRemappingCategory(settingsPresenter);
    }

    /**
     * The Network settings card of the System settings, in General (was in the General panel)
     */
    public void showNetwork() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.network_settings),
                this::appendNetworkRows, mOnFinish);
    }

    /**
     * The rows of the panel showNetwork shows (see SettingsPanel)
     */
    public void appendNetworkRows(SettingsRows settingsPresenter) {
        appendInternetCensorship(settingsPresenter);
    }

    /**
     * The History card of the System settings, in General (was in the General panel)
     */
    public void showHistory() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.header_history),
                this::appendHistoryRows, mOnFinish);
    }

    /**
     * The rows of the panel showHistory shows (see SettingsPanel)
     */
    public void appendHistoryRows(SettingsRows settingsPresenter) {
        appendHistoryCategory(settingsPresenter);
    }

    /**
     * The API keys card of the System settings, in General (was in the General panel)
     */
    public void showApiKeys() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.api_keys),
                this::appendApiKeysRows, mOnFinish);
    }

    /**
     * The rows of the panel showApiKeys shows (see SettingsPanel)
     */
    public void appendApiKeysRows(SettingsRows settingsPresenter) {
        appendDataApiKeySwitch(settingsPresenter);
        appendDataApiKeyButton(settingsPresenter);
    }

    /**
     * The DNS card of the System settings (was Prefers in the Player settings)
     */
    public void showDns() {
        showDns(0);
    }

    /**
     * Its rows are the server's: the protocol for a public DNS only (the system's has its own, Private DNS), and the servers asked,
     * the custom ones as buttons (the addresses for plain DNS, the URL for DNS-over-HTTPS), then the check of the servers
     *
     * @param focusedResId the title of the row that has the focus when it opens (the one just changed), 0 for the first row
     */
    private void showDns(int focusedResId) {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_dns),
                rows -> appendDnsRows(rows, focusedResId), mOnFinish);
    }

    /**
     * The rows of the DNS card (see SettingsPanel)
     */
    public void appendDnsRows(SettingsRows rows) {
        appendDnsRows(rows, 0);
    }

    private void appendDnsRows(SettingsRows settingsPresenter, int focusedResId) {
        int dnsType = mPlayerTweaksData.getPreferredDnsType();

        appendDnsServer(settingsPresenter, focusedResId);

        if (isPublicDns(dnsType)) {
            appendDnsProtocol(settingsPresenter, focusedResId);
        }

        if (dnsType == PlayerTweaksData.DNS_TYPE_CUSTOM) {
            appendCustomDns(settingsPresenter, focusedResId);
        } else if (isPublicDns(dnsType) && mPlayerTweaksData.getPreferredDnsProtocol() == PlayerTweaksData.DNS_PROTOCOL_DOH) {
            appendDohUrl(settingsPresenter, dnsType);
        } else {
            appendDnsAddresses(settingsPresenter, dnsType);
        }

        appendDnsCheck(settingsPresenter, focusedResId);
        appendDnsFix(settingsPresenter);
    }

    /**
     * Built again, titled as the System card opens it, "System - DNS" (see ModernSettingsCardsFragment)
     *
     * @param isReplace in place of the card shown (e.g. after a change of the server, which changes its rows): the card is closed
     *                  otherwise
     */
    private void showDnsAgain(int focusedResId, boolean isReplace) {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        settingsPresenter.enableReplace(isReplace);
        settingsPresenter.setTitlePrefix(getContext().getString(R.string.settings_system));
        settingsPresenter.enableNestedTitles(true);
        showDns(focusedResId);
        // Only this card
        settingsPresenter.setTitlePrefix(null);
        settingsPresenter.enableNestedTitles(false);
    }

    /**
     * Asked by the app itself (see PreferredDns in SharedModules): the system's DNS (System, System IPv4-first, a type of a newer
     * version) isn't
     */
    private static boolean isPublicDns(int dnsType) {
        switch (dnsType) {
            case PlayerTweaksData.DNS_TYPE_GOOGLE:
            case PlayerTweaksData.DNS_TYPE_CLOUDFLARE:
            case PlayerTweaksData.DNS_TYPE_QUAD9:
            case PlayerTweaksData.DNS_TYPE_ADGUARD:
            case PlayerTweaksData.DNS_TYPE_CUSTOM:
                return true;
            default:
                return false;
        }
    }

    /**
     * One DNS: they were checkboxes of one setting, so two could look chosen at once (#93). A choice closes the list: the card is
     * built again with the rows of the server chosen.
     */
    private void appendDnsServer(SettingsRows settingsPresenter, int focusedResId) {
        int[][] servers = {
                {PlayerTweaksData.DNS_TYPE_SYSTEM, R.string.dns_system},
                {PlayerTweaksData.DNS_TYPE_IPV4, R.string.dns_system_ipv4_first},
                {PlayerTweaksData.DNS_TYPE_GOOGLE, R.string.dns_google},
                {PlayerTweaksData.DNS_TYPE_CLOUDFLARE, R.string.dns_cloudflare},
                {PlayerTweaksData.DNS_TYPE_QUAD9, R.string.dns_quad9},
                {PlayerTweaksData.DNS_TYPE_ADGUARD, R.string.dns_adguard},
                {PlayerTweaksData.DNS_TYPE_CUSTOM, R.string.dns_custom}
        };
        List<OptionItem> options = new ArrayList<>();

        for (int[] server : servers) {
            int dnsType = server[0];
            options.add(UiOptionItem.from(getContext().getString(server[1]),
                    option -> {
                        setDnsType(dnsType);
                        showDnsAgain(R.string.dns_server, true);
                    },
                    mPlayerTweaksData.getPreferredDnsType() == dnsType));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.dns_server), options);

        if (focusedResId == R.string.dns_server) {
            settingsPresenter.setFocusedItem(options.get(0));
        }
    }

    /**
     * A DNS of the app's own is OkHttp's only: the player is switched to it, and to the faster engine for the system's DNS. The DNS
     * is read at each lookup (see PreferredDns in SharedModules): only a switch of the engine asks for a restart.
     */
    private void setDnsType(int dnsType) {
        int dataSource = dnsType == PlayerTweaksData.DNS_TYPE_SYSTEM ? Utils.getFasterDataSource() : PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP;

        if (mPlayerTweaksData.getPreferredDnsType() != dnsType && mPlayerTweaksData.getPlayerDataSource() != dataSource) {
            mPlayerTweaksData.setPlayerDataSource(dataSource);
            mRestartApp = true;
        }

        mPlayerTweaksData.setPreferredDnsType(dnsType);
    }

    /**
     * Of the public DNS servers (the system's has its own). DNS-over-TLS isn't one: it's Android's Private DNS, for every app. A
     * choice closes the list, as the server's does: the custom server's rows are the protocol's.
     */
    private void appendDnsProtocol(SettingsRows settingsPresenter, int focusedResId) {
        int protocol = mPlayerTweaksData.getPreferredDnsProtocol();
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.dns_protocol_plain),
                option -> setDnsProtocol(PlayerTweaksData.DNS_PROTOCOL_PLAIN),
                protocol == PlayerTweaksData.DNS_PROTOCOL_PLAIN));
        options.add(UiOptionItem.from(getContext().getString(R.string.dns_protocol_doh),
                option -> setDnsProtocol(PlayerTweaksData.DNS_PROTOCOL_DOH),
                protocol == PlayerTweaksData.DNS_PROTOCOL_DOH));

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.dns_protocol), options);

        if (focusedResId == R.string.dns_protocol) {
            settingsPresenter.setFocusedItem(options.get(0));
        }
    }

    private void setDnsProtocol(int protocol) {
        mPlayerTweaksData.setPreferredDnsProtocol(protocol);
        showDnsAgain(R.string.dns_protocol, true);
    }

    /**
     * A public DNS over HTTPS: its URL, as a line of text, the server asked (as the check asks it). Its host's addresses aren't the
     * servers: only where the URL's host is.
     */
    private void appendDohUrl(SettingsRows settingsPresenter, int dnsType) {
        OptionItem item = UiOptionItem.from(getContext().getString(R.string.dns_doh_url),
                getValueOrNotSet(PreferredDns.getDohUrl(dnsType)), option -> {});
        item.setNote(true);
        settingsPresenter.appendSingleButton(item);
    }

    /**
     * The servers asked, as lines of text (see getDnsAddresses): two at least, "Not set" without them, four at most
     */
    private void appendDnsAddresses(SettingsRows settingsPresenter, int dnsType) {
        List<String> addresses = getDnsAddresses(dnsType);
        int[] titles = {R.string.dns_primary_server, R.string.dns_secondary_server, R.string.dns_third_server, R.string.dns_fourth_server};
        int count = Math.max(2, Math.min(titles.length, addresses.size()));

        for (int i = 0; i < count; i++) {
            OptionItem item = UiOptionItem.from(getContext().getString(titles[i]),
                    getValueOrNotSet(i < addresses.size() ? addresses.get(i) : null), option -> {});
            item.setNote(true);
            settingsPresenter.appendSingleButton(item);
        }
    }

    /**
     * The servers asked, in the order they're asked: a public DNS's own (see PreferredDns in SharedModules), the network's for the
     * system's. Their IPv6 ones only while IPv6 works (see isIpv6Working): the lookups don't get to them otherwise.
     */
    private List<String> getDnsAddresses(int dnsType) {
        boolean isIpv6Working = isIpv6Working();

        if (isPublicDns(dnsType)) {
            return PreferredDns.getAddresses(dnsType, isIpv6Working);
        }

        List<String> result = new ArrayList<>();

        for (String address : getSystemDnsServers()) {
            if (isIpv6Working || PublicDnsResolver.isIpv4Address(address)) {
                result.add(address);
            }
        }

        return result;
    }

    /**
     * As the app's lookups found it (see PreferredDns.isIpv6Working in SharedModules), or, till they test it, the network has an IPv6
     * route out
     */
    private boolean isIpv6Working() {
        Boolean isWorking = PreferredDns.isIpv6Working();

        if (isWorking != null) {
            return isWorking;
        }

        LinkProperties properties = getActiveLinkProperties();

        if (properties != null) {
            for (RouteInfo route : properties.getRoutes()) {
                if (route.isDefaultRoute() && route.getDestination().getAddress() instanceof Inet6Address) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * The DNS servers of the network the device is on (a VPN's while it's on), the primary first: none without a network
     */
    private List<String> getSystemDnsServers() {
        List<String> result = new ArrayList<>();
        LinkProperties properties = getActiveLinkProperties();

        if (properties != null) {
            for (InetAddress server : properties.getDnsServers()) {
                result.add(server.getHostAddress());
            }
        }

        return result;
    }

    /**
     * Of the network the device is on (a VPN's while it's on): null without a network
     */
    private LinkProperties getActiveLinkProperties() {
        try {
            ConnectivityManager manager = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            Network network = manager != null ? manager.getActiveNetwork() : null;
            return network != null ? manager.getLinkProperties(network) : null;
        } catch (RuntimeException e) {
            // E.g. a SecurityException of a firmware that asks for more than ACCESS_NETWORK_STATE: shown as not set
            e.printStackTrace();
            return null;
        }
    }

    /**
     * The host the system's DNS is asked over TLS (Android 9+): null when Private DNS is off, or automatic (the network's servers,
     * over TLS when they can)
     */
    private String getPrivateDnsServerName() {
        if (Build.VERSION.SDK_INT < 28) {
            return null;
        }

        LinkProperties properties = getActiveLinkProperties();

        return properties != null && properties.isPrivateDnsActive() ? properties.getPrivateDnsServerName() : null;
    }

    /**
     * The custom server's: its addresses for plain DNS (typed on the number pad), its URL for DNS-over-HTTPS
     */
    private void appendCustomDns(SettingsRows settingsPresenter, int focusedResId) {
        if (mPlayerTweaksData.getPreferredDnsProtocol() == PlayerTweaksData.DNS_PROTOCOL_DOH) {
            appendCustomDnsButton(settingsPresenter, R.string.dns_custom_doh_url, mPlayerTweaksData.getCustomDohUrl(), focusedResId,
                    () -> showCustomDnsDialog(AppDialogPresenter.instance(getContext()), R.string.dns_custom_doh_url, R.string.dns_custom_doh_url_hint,
                            mPlayerTweaksData.getCustomDohUrl(), mPlayerTweaksData::setCustomDohUrl, PublicDnsResolver::isDohUrl,
                            R.string.dns_invalid_doh_url, false));
            return;
        }

        appendCustomDnsButton(settingsPresenter, R.string.dns_custom_primary, mPlayerTweaksData.getCustomDnsPrimary(), focusedResId,
                () -> showCustomDnsDialog(AppDialogPresenter.instance(getContext()), R.string.dns_custom_primary, R.string.dns_custom_address_hint,
                        mPlayerTweaksData.getCustomDnsPrimary(), mPlayerTweaksData::setCustomDnsPrimary, PublicDnsResolver::isIpv4Address,
                        R.string.dns_invalid_address, true));
        appendCustomDnsButton(settingsPresenter, R.string.dns_custom_secondary, mPlayerTweaksData.getCustomDnsSecondary(), focusedResId,
                () -> showCustomDnsDialog(AppDialogPresenter.instance(getContext()), R.string.dns_custom_secondary, R.string.dns_custom_address_hint,
                        mPlayerTweaksData.getCustomDnsSecondary(), mPlayerTweaksData::setCustomDnsSecondary, PublicDnsResolver::isIpv4Address,
                        R.string.dns_invalid_address, true));
    }

    private void appendCustomDnsButton(SettingsRows settingsPresenter, int titleResId, String value, int focusedResId,
                                       Runnable onClick) {
        OptionItem item = UiOptionItem.from(getContext().getString(titleResId), getValueOrNotSet(value), option -> onClick.run());
        settingsPresenter.appendSingleButton(item);

        if (titleResId == focusedResId) {
            settingsPresenter.setFocusedItem(item);
        }
    }

    private interface DnsValueSetter {
        void set(String value);
    }

    private interface DnsValueCheck {
        boolean isValid(String value);
    }

    /**
     * Over the System settings, the card closed: the field would be behind it otherwise. Empty clears it. Not valid: the field stays,
     * with a message. Closed by OK, Cancel or Back: the DNS card again, at the row edited, with its value.
     *
     * @param isIpv4Address typed on the number pad: the URL keyboard otherwise
     */
    private void showCustomDnsDialog(AppDialogPresenter settingsPresenter, int titleResId, int hintResId, String value,
                                     DnsValueSetter setter, DnsValueCheck check, int invalidResId, boolean isIpv4Address) {
        settingsPresenter.closeDialog();

        SimpleEditDialog.OnChange onChange = newValue -> {
            String trimmed = newValue != null ? newValue.trim() : "";

            if (!trimmed.isEmpty() && !check.isValid(trimmed)) {
                MessageHelpers.showMessage(getContext(), invalidResId);
                return false;
            }

            setter.set(trimmed.isEmpty() ? null : trimmed);
            return true;
        };
        Runnable onDismiss = () -> showDnsAgain(titleResId, false);
        String title = getContext().getString(titleResId);
        String hint = getContext().getString(hintResId);

        // An optional server is cleared with an empty field
        if (isIpv4Address) {
            SimpleEditDialog.showIpv4Address(getContext(), title, hint, value, onChange, onDismiss, true);
        } else {
            SimpleEditDialog.showUrl(getContext(), title, hint, value, onChange, onDismiss, true);
        }
    }

    private String getValueOrNotSet(String value) {
        return value != null ? value : getContext().getString(R.string.dns_not_set);
    }

    /**
     * Whether the servers of the settings answer, each asked for www.youtube.com as the app's lookups ask them (see DnsCheck in
     * SharedModules). Pressed: a line for each server in its place, a green check or a red cross before each as it answers or fails.
     * They stay while the settings are the ones checked (see getDnsCheckKey).
     */
    private void appendDnsCheck(SettingsRows settingsPresenter, int focusedResId) {
        CharSequence result = getDnsCheckKey().equals(mDnsCheckKey) ? mDnsCheckResult : null;
        OptionItem item = UiOptionItem.from(getContext().getString(R.string.dns_check),
                result != null ? result : getContext().getString(R.string.dns_check_desc, DnsCheck.HOSTNAME), option -> startDnsCheck());
        mDnsCheckItem = item;
        settingsPresenter.appendSingleButton(item);

        if (focusedResId == R.string.dns_check) {
            settingsPresenter.setFocusedItem(item);
        }
    }

    /**
     * Each server asked at once, its line filled in as it answers: in the button shown then (the card may be built again meanwhile)
     */
    private void startDnsCheck() {
        if (mIsDnsChecking) {
            return;
        }

        mIsDnsChecking = true;
        DnsCheck check = getDnsCheck();
        List<String> servers = check.getServers();
        CharSequence[] lines = new CharSequence[servers.size()];
        int[] remaining = {servers.size()};
        String key = getDnsCheckKey();

        for (int i = 0; i < lines.length; i++) {
            lines[i] = getDnsCheckLine(servers.get(i), null);
        }

        showDnsCheckResult(key, lines);

        Handler handler = new Handler(Looper.getMainLooper());
        check.start((index, result) -> handler.post(() -> {
            lines[index] = getDnsCheckLine(servers.get(index), result);
            remaining[0]--;
            mIsDnsChecking = remaining[0] > 0;
            showDnsCheckResult(key, lines);
        }));
    }

    /**
     * Of the servers the card shows, in their order (see getDnsAddresses): over UDP. The custom server's as it's set, over HTTPS its
     * URL, and when it isn't set, the system's in its place.
     */
    private DnsCheck getDnsCheck() {
        int dnsType = mPlayerTweaksData.getPreferredDnsType();
        boolean isDoh = mPlayerTweaksData.getPreferredDnsProtocol() == PlayerTweaksData.DNS_PROTOCOL_DOH;

        if (isPublicDns(dnsType) && (isDoh || dnsType == PlayerTweaksData.DNS_TYPE_CUSTOM)) {
            DnsCheck check = DnsCheck.ofSettings();

            if (check.getServers().get(0) != null) {
                return check;
            }

            dnsType = PlayerTweaksData.DNS_TYPE_SYSTEM;
        }

        // None: the system's lookup
        return DnsCheck.ofServers(getDnsAddresses(dnsType));
    }

    /**
     * Kept for the card built again, and shown in the button while its settings are the ones checked
     */
    private void showDnsCheckResult(String key, CharSequence[] lines) {
        SpannableStringBuilder result = new SpannableStringBuilder();

        for (CharSequence line : lines) {
            if (result.length() > 0) {
                result.append('\n');
            }

            result.append(line);
        }

        mDnsCheckKey = key;
        mDnsCheckResult = result;

        if (mDnsCheckItem != null && key.equals(getDnsCheckKey())) {
            mDnsCheckItem.setDescription(result);
        }
    }

    /**
     * The settings a check is of: its results are another's once they change
     */
    private String getDnsCheckKey() {
        return mPlayerTweaksData.getPreferredDnsType() + " " + mPlayerTweaksData.getPreferredDnsProtocol() + " " +
                mPlayerTweaksData.getCustomDnsPrimary() + " " + mPlayerTweaksData.getCustomDnsSecondary() + " " +
                mPlayerTweaksData.getCustomDohUrl();
    }

    /**
     * The server's name and its status: a green check before it when it works, a red cross when it doesn't, nothing while it's asked
     *
     * @param server its address or URL, null for the system's DNS (see DnsCheck.getServers)
     * @param result null while it's asked
     */
    @VisibleForTesting
    CharSequence getDnsCheckLine(String server, DnsCheck.Result result) {
        String status = result != null ? describeDnsCheck(result, server == null) : getContext().getString(R.string.dns_check_checking);
        CharSequence line = getContext().getString(R.string.dns_check_line, getDnsCheckServerName(server), status);

        if (result == null) {
            return line;
        }

        boolean isWorking = result.status == DnsCheck.Status.WORKS;
        CharSequence mark = Utils.color(getContext().getString(isWorking ? R.string.dns_check_mark_works : R.string.dns_check_mark_fails),
                ContextCompat.getColor(getContext(), isWorking ? R.color.dns_check_works : R.color.dns_check_fails));

        return TextUtils.concat(mark, " ", line);
    }

    /**
     * The system's DNS: the Private DNS host when it has one, and when the custom server's in its place, that it isn't set
     */
    private String getDnsCheckServerName(String server) {
        if (server != null) {
            return server;
        }

        if (mPlayerTweaksData.getPreferredDnsType() == PlayerTweaksData.DNS_TYPE_CUSTOM) {
            return getContext().getString(R.string.dns_check_custom_not_set);
        }

        String privateDns = getPrivateDnsServerName();

        return privateDns != null ? getContext().getString(R.string.dns_check_private_dns, privateDns) :
                getContext().getString(R.string.dns_system);
    }

    /**
     * @param isSystem the system's DNS: waited for longer (see DnsCheck.SYSTEM_TIMEOUT_MS)
     */
    @VisibleForTesting
    String describeDnsCheck(DnsCheck.Result result, boolean isSystem) {
        switch (result.status) {
            case WORKS:
                return getContext().getString(R.string.dns_check_works, result.timeMs, getHostAddress(result.address));
            case BLOCKED:
                return getContext().getString(R.string.dns_check_blocked,
                        result.address != null ? getHostAddress(result.address) : getContext().getString(R.string.dns_check_no_address));
            case TIMEOUT:
                return getContext().getString(R.string.dns_check_timeout,
                        (isSystem ? DnsCheck.SYSTEM_TIMEOUT_MS : DnsCheck.SERVER_TIMEOUT_MS) / 1_000);
            case UNREACHABLE:
                return getContext().getString(R.string.dns_check_unreachable);
            default:
                return getContext().getString(R.string.dns_check_failed, result.detail);
        }
    }

    private static String getHostAddress(InetAddress address) {
        return address != null ? address.getHostAddress() : "";
    }

    private void appendDnsFix(SettingsRows settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.dns_fix),
                getContext().getString(R.string.dns_fix_desc),
                option -> mPlayerTweaksData.setDnsFixEnabled(option.isSelected()),
                mPlayerTweaksData.isDnsFixEnabled()));
    }

    /**
     * The Misc options card of the System settings, in General (was Misc in the General panel)
     */
    public void showMisc() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_misc_options),
                this::appendMiscRows, mOnFinish);
    }

    /**
     * The rows of the panel showMisc shows (see SettingsPanel)
     */
    public void appendMiscRows(SettingsRows settingsPresenter) {
        appendMiscCategory(settingsPresenter);
    }

    /**
     * The Child mode card of the System settings, in General (was in Misc options)
     */
    public void showChildMode() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.child_mode),
                this::appendChildModeRows, mOnFinish);
    }

    /**
     * The rows of the panel showChildMode shows (see SettingsPanel)
     */
    public void appendChildModeRows(SettingsRows settingsPresenter) {
        appendChildModeCategory(settingsPresenter);
    }

    /**
     * The Remember options card of the System settings, in General (was in the General panel)
     */
    public void showRemember() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_remember_options),
                this::appendRememberRows, mOnFinish);
    }

    /**
     * The rows of the panel showRemember shows (see SettingsPanel)
     */
    public void appendRememberRows(SettingsRows rows) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.remember_position_subscriptions),
                option -> mGeneralData.setRememberSubscriptionsPositionEnabled(option.isSelected()),
                mGeneralData.isRememberSubscriptionsPositionEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.remember_position_pinned),
                option -> mGeneralData.setRememberPinnedPositionEnabled(option.isSelected()),
                mGeneralData.isRememberPinnedPositionEnabled()));

        rows.appendCheckedCategory(getContext().getString(R.string.settings_remember_options), options);
    }

    /**
     * The Disable options card of the System settings, in General (was in the General panel)
     */
    public void showDisable() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_disable_options),
                this::appendDisableRows, mOnFinish);
    }

    /**
     * The rows of the panel showDisable shows (see SettingsPanel)
     */
    public void appendDisableRows(SettingsRows rows) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.disable_screensaver),
                option -> mGeneralData.setScreensaverDisabled(option.isSelected()),
                mGeneralData.isScreensaverDisabled()));

        // Disable long press on buggy controllers.
        options.add(UiOptionItem.from(getContext().getString(R.string.disable_ok_long_press),
                getContext().getString(R.string.disable_ok_long_press_desc),
                option -> mGeneralData.setOkButtonLongPressDisabled(option.isSelected()),
                mGeneralData.isOkButtonLongPressDisabled()));

        rows.appendCheckedCategory(getContext().getString(R.string.settings_disable_options), options);
    }

    /**
     * The Password options card of the System settings, in General (was in the General panel)
     */
    public void showPasswords() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_password_options),
                this::appendPasswordsRows, mOnFinish);
    }

    /**
     * The rows of the panel showPasswords shows (see SettingsPanel)
     */
    public void appendPasswordsRows(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.protect_settings_with_password),
                option -> {
                    if (option.isSelected()) {
                        showPasswordDialog(AppDialogPresenter.instance(getContext()), null);
                    } else {
                        mGeneralData.setSettingsPassword(null);
                    }
                },
                mGeneralData.getSettingsPassword() != null));

        options.add(UiOptionItem.from(getContext().getString(R.string.enable_master_password),
                option -> {
                    if (option.isSelected()) {
                        showMasterPasswordDialog(AppDialogPresenter.instance(getContext()), null);
                    } else {
                        mGeneralData.setMasterPassword(null);
                    }
                },
                mGeneralData.getMasterPassword() != null));

        String title = getContext().getString(R.string.settings_password_options);
        settingsPresenter.appendCheckedCategory(title, options);
    }

    /**
     * The Exit from options card of the System settings, in General (was in the General panel): one list, the app exit opens from it
     */
    public void showExit() {
        AppDialogPresenter.instance(getContext()).showPanel(getContext().getString(R.string.settings_exit_options),
                this::appendExitRows, mOnFinish);
    }

    /**
     * The rows of the panel showExit shows (see SettingsPanel)
     */
    public void appendExitRows(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        OptionItem appExit = UiOptionItem.from(getContext().getString(R.string.app_exit_shortcut), option -> showAppExitMenu());
        appExit.setMenu(true);
        options.add(appExit);

        options.add(UiOptionItem.from( getContext().getString(R.string.player_exit_shortcut) + ": " + getContext().getString(R.string.app_double_back_exit),
                option -> mGeneralData.setPlayerExitShortcut(option.isSelected() ? GeneralData.EXIT_DOUBLE_BACK : GeneralData.EXIT_SINGLE_BACK),
                mGeneralData.getPlayerExitShortcut() == GeneralData.EXIT_DOUBLE_BACK));

        options.add(UiOptionItem.from(getContext().getString(R.string.search_exit_shortcut) + ": " + getContext().getString(R.string.app_double_back_exit),
                option -> mGeneralData.setSearchExitShortcut(option.isSelected() ? GeneralData.EXIT_DOUBLE_BACK : GeneralData.EXIT_SINGLE_BACK),
                mGeneralData.getSearchExitShortcut() == GeneralData.EXIT_DOUBLE_BACK));

        String title = getContext().getString(R.string.settings_exit_options);
        settingsPresenter.appendCheckedCategory(title, options);
    }

    private void showAppExitMenu() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        appendAppExitCategory(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.app_exit_shortcut));
    }

    private void appendAppExitCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.app_exit_none, GeneralData.EXIT_NONE},
                {R.string.app_double_back_exit, GeneralData.EXIT_DOUBLE_BACK},
                {R.string.app_single_back_exit, GeneralData.EXIT_SINGLE_BACK}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> mGeneralData.setAppExitShortcut(pair[1]),
                    mGeneralData.getAppExitShortcut() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.app_exit_shortcut), options);
    }

    private void appendBackgroundPlaybackCategory(SettingsRows settingsPresenter) {
        OptionCategory category = AppDialogUtil.createBackgroundPlaybackCategory(getContext(), mPlayerData, mGeneralData);
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendKeyRemappingCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from("OK -> " + getContext().getString(R.string.player_toggle_speed),
                option -> mPlayerData.setOKButtonBehavior(option.isSelected() ? PlayerData.OK_TOGGLE_SPEED : PlayerData.OK_ONLY_UI),
                mPlayerData.getOKButtonBehavior() == PlayerData.OK_TOGGLE_SPEED));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_quick_shorts_skip_alt),
                option -> {
                    mPlayerTweaksData.setQuickSkipShortsAltEnabled(option.isSelected());
                    mGeneralData.setDpadUpDownAction(GeneralData.ACTION_UNDEFINED);
                },
                mPlayerTweaksData.isQuickSkipShortsAltEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_quick_shorts_skip),
                option -> {
                    mPlayerTweaksData.setQuickSkipShortsEnabled(option.isSelected());
                    mGeneralData.resetDpadLeftRightSettings();
                },
                mPlayerTweaksData.isQuickSkipShortsEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_quick_skip_videos_alt),
                option -> {
                    mPlayerTweaksData.setQuickSkipVideosAltEnabled(option.isSelected());
                    mGeneralData.setDpadUpDownAction(GeneralData.ACTION_UNDEFINED);
                },
                mPlayerTweaksData.isQuickSkipVideosAltEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_quick_skip_chapters),
                option -> {
                    mPlayerTweaksData.setQuickSkipChaptersEnabled(option.isSelected());
                    // Unchecked: up/down may be set to volume or speed since (the list doesn't uncheck this one)
                    if (option.isSelected()) {
                        mGeneralData.setDpadUpDownAction(GeneralData.ACTION_UNDEFINED);
                    }
                },
                mPlayerTweaksData.isQuickSkipChaptersEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.player_quick_skip_videos),
                option -> {
                    mPlayerTweaksData.setQuickSkipVideosEnabled(option.isSelected());
                    mGeneralData.resetDpadLeftRightSettings();
                },
                mPlayerTweaksData.isQuickSkipVideosEnabled()));

        options.add(UiOptionItem.from("Play/Pause -> OK",
                option -> mGeneralData.setRemapPlayToOKEnabled(option.isSelected()),
                mGeneralData.isRemapPlayToOKEnabled()));

        options.add(UiOptionItem.from("DPAD RIGHT/LEFT -> Volume Up/Down",
                option -> {
                    mGeneralData.setRemapDpadLeftToVolumeEnabled(option.isSelected());
                    mPlayerTweaksData.resetDpadLeftRightSettings();
                },
                mGeneralData.isRemapDpadLeftToVolumeEnabled()));

        options.add(UiOptionItem.from("DPAD UP/DOWN -> Volume Up/Down",
                option -> {
                    mGeneralData.setDpadUpDownAction(option.isSelected() ? GeneralData.ACTION_VOLUME_UP_DOWN : GeneralData.ACTION_UNDEFINED);
                    if (option.isSelected()) {
                        mPlayerTweaksData.resetDpadUpDownSettings();
                    }
                },
                mGeneralData.getDpadUpDownAction() == GeneralData.ACTION_VOLUME_UP_DOWN));

        options.add(UiOptionItem.from("DPAD UP/DOWN -> Speed Up/Down",
                option -> {
                    mGeneralData.setDpadUpDownAction(option.isSelected() ? GeneralData.ACTION_SPEED_UP_DOWN : GeneralData.ACTION_UNDEFINED);
                    if (option.isSelected()) {
                        mPlayerTweaksData.resetDpadUpDownSettings();
                    }
                },
                mGeneralData.getDpadUpDownAction() == GeneralData.ACTION_SPEED_UP_DOWN));

        options.add(UiOptionItem.from("Numbers 3/1 -> Speed Up/Down",
                option -> mGeneralData.setRemapNumbersToSpeedEnabled(option.isSelected()),
                mGeneralData.isRemapNumbersToSpeedEnabled()));

        options.add(UiOptionItem.from("Next/Previous -> Fast Forward/Rewind",
                option -> mGeneralData.setNextPreviousAction(option.isSelected() ? GeneralData.ACTION_FAST_FORWARD_REWIND : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getNextPreviousAction() == GeneralData.ACTION_FAST_FORWARD_REWIND));

        options.add(UiOptionItem.from("Next/Previous -> Speed Up/Down",
                option -> mGeneralData.setNextPreviousAction(option.isSelected() ? GeneralData.ACTION_SPEED_UP_DOWN : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getNextPreviousAction() == GeneralData.ACTION_SPEED_UP_DOWN));

        options.add(UiOptionItem.from("Fast Forward/Rewind -> Next/Previous",
                option -> mGeneralData.setFastForwardRewindAction(option.isSelected() ? GeneralData.ACTION_NEXT_PREVIOUS : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getFastForwardRewindAction() == GeneralData.ACTION_NEXT_PREVIOUS));

        options.add(UiOptionItem.from("Fast Forward/Rewind -> Speed Up/Down",
                option -> mGeneralData.setFastForwardRewindAction(option.isSelected() ? GeneralData.ACTION_SPEED_UP_DOWN : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getFastForwardRewindAction() == GeneralData.ACTION_SPEED_UP_DOWN));

        options.add(UiOptionItem.from("Fast Forward/Rewind -> Speed Toggle",
                option -> mGeneralData.setFastForwardRewindAction(option.isSelected() ? GeneralData.ACTION_SPEED_TOGGLE : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getFastForwardRewindAction() == GeneralData.ACTION_SPEED_TOGGLE));

        options.add(UiOptionItem.from("S -> Speed Toggle",
                option -> mGeneralData.setRemapSToSpeedToggleEnabled(option.isSelected()),
                mGeneralData.isRemapSToSpeedToggleEnabled()));

        options.add(UiOptionItem.from("Page Up/Down -> Next/Previous",
                option -> mGeneralData.setPageUpDownAction(option.isSelected() ? GeneralData.ACTION_NEXT_PREVIOUS : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getPageUpDownAction() == GeneralData.ACTION_NEXT_PREVIOUS));

        options.add(UiOptionItem.from("Page Up/Down -> Like/Dislike",
                option -> mGeneralData.setPageUpDownAction(option.isSelected() ? GeneralData.ACTION_LIKE_DISLIKE : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getPageUpDownAction() == GeneralData.ACTION_LIKE_DISLIKE));

        options.add(UiOptionItem.from("Page Up/Down -> Speed Up/Down",
                option -> mGeneralData.setPageUpDownAction(option.isSelected() ? GeneralData.ACTION_SPEED_UP_DOWN : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getPageUpDownAction() == GeneralData.ACTION_SPEED_UP_DOWN));
        
        options.add(UiOptionItem.from("Page Up/Down -> Speed Down/Up",
                option -> mGeneralData.setPageUpDownAction(option.isSelected() ? GeneralData.ACTION_SPEED_DOWN_UP : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getPageUpDownAction() == GeneralData.ACTION_SPEED_DOWN_UP));

        options.add(UiOptionItem.from("Channel Up/Down -> Volume Up/Down",
                option -> mGeneralData.setChannelUpDownAction(option.isSelected() ? GeneralData.ACTION_VOLUME_UP_DOWN : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getChannelUpDownAction() == GeneralData.ACTION_VOLUME_UP_DOWN));

        options.add(UiOptionItem.from("Channel Up/Down -> Next/Previous",
                option -> mGeneralData.setChannelUpDownAction(option.isSelected() ? GeneralData.ACTION_NEXT_PREVIOUS : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getChannelUpDownAction() == GeneralData.ACTION_NEXT_PREVIOUS));

        options.add(UiOptionItem.from("Channel Up/Down -> Like/Dislike",
                option -> mGeneralData.setChannelUpDownAction(option.isSelected() ? GeneralData.ACTION_LIKE_DISLIKE : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getChannelUpDownAction() == GeneralData.ACTION_LIKE_DISLIKE));

        options.add(UiOptionItem.from("Channel Up/Down -> Speed Up/Down",
                option -> mGeneralData.setChannelUpDownAction(option.isSelected() ? GeneralData.ACTION_SPEED_UP_DOWN : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getChannelUpDownAction() == GeneralData.ACTION_SPEED_UP_DOWN));

        options.add(UiOptionItem.from("Channel Up/Down -> Speed Toggle",
                option -> mGeneralData.setChannelUpDownAction(option.isSelected() ? GeneralData.ACTION_SPEED_TOGGLE : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getChannelUpDownAction() == GeneralData.ACTION_SPEED_TOGGLE));

        options.add(UiOptionItem.from("Channel Up/Down -> Search",
                option -> mGeneralData.setChannelUpDownAction(option.isSelected() ? GeneralData.ACTION_SEARCH : GeneralData.ACTION_UNDEFINED),
                mGeneralData.getChannelUpDownAction() == GeneralData.ACTION_SEARCH));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.key_remapping), options);
    }

    private void appendScreenDimmingAmountCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        int activeMode = mGeneralData.getScreensaverDimmingPercents();

        for (int dimPercents : Helpers.range(10, 80, 10)) {
            options.add(UiOptionItem.from(
                    dimPercents + "%",
                    option -> mGeneralData.setScreensaverDimmingPercents(dimPercents),
                    activeMode == dimPercents));
        }

        for (int dimPercents : Helpers.range(85, 100, 5)) {
            options.add(UiOptionItem.from(
                    dimPercents + "%",
                    option -> mGeneralData.setScreensaverDimmingPercents(dimPercents),
                    activeMode == dimPercents));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.screen_dimming_amount), options);
    }

    @SuppressLint("StringFormatMatches")
    private void appendScreenDimmingTimeoutCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        int screensaverTimeoutMs = mGeneralData.getScreensaverTimeoutMs();

        options.add(UiOptionItem.from(
                getContext().getString(R.string.option_never),
                option -> mGeneralData.setScreensaverTimeoutMs(GeneralData.SCREENSAVER_TIMEOUT_NEVER),
                screensaverTimeoutMs == GeneralData.SCREENSAVER_TIMEOUT_NEVER));

        for (int timeoutSec : new int[] {5, 15, 30}) {
            int timeoutMs = timeoutSec * 1_000;
            options.add(UiOptionItem.from(
                    getContext().getString(R.string.ui_hide_timeout_sec, timeoutSec),
                    option -> mGeneralData.setScreensaverTimeoutMs(timeoutMs),
                    screensaverTimeoutMs == timeoutMs));
        }

        for (int i = 1; i <= 15; i++) {
            int timeoutMs = i * 60 * 1_000;
            options.add(UiOptionItem.from(
                    getContext().getString(R.string.screen_dimming_timeout_min, i),
                    option -> mGeneralData.setScreensaverTimeoutMs(timeoutMs),
                    screensaverTimeoutMs == timeoutMs));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.screen_dimming_timeout), options);
    }

    //private void appendTimeFormatCategory(SettingsRows settingsPresenter) {
    //    List<OptionItem> options = new ArrayList<>();
    //
    //    options.add(UiOptionItem.from(
    //            getContext().getString(R.string.time_format_24),
    //            option -> {
    //                mGeneralData.setTimeFormat(GeneralData.TIME_FORMAT_24);
    //                mRestartApp = true;
    //            },
    //            mGeneralData.getTimeFormat() == GeneralData.TIME_FORMAT_24));
    //
    //    options.add(UiOptionItem.from(
    //            getContext().getString(R.string.time_format_12),
    //            option -> {
    //                mGeneralData.setTimeFormat(GeneralData.TIME_FORMAT_12);
    //                mRestartApp = true;
    //            },
    //            mGeneralData.getTimeFormat() == GeneralData.TIME_FORMAT_12));
    //
    //    settingsPresenter.appendRadioCategory(getContext().getString(R.string.time_format), options);
    //}

    private void appendHistoryCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        for (int[] pair : new int[][] {
                {R.string.auto_history, GeneralData.HISTORY_AUTO},
                {R.string.enable_history, GeneralData.HISTORY_ENABLED},
                {R.string.disable_history_local, GeneralData.HISTORY_DISABLED},
                {R.string.disable_history_global, GeneralData.HISTORY_PAUSED}}) {
            options.add(UiOptionItem.from(getContext().getString(pair[0]),
                    optionItem -> MediaServiceManager.instance().applyHistoryState(getContext(), pair[1]),
                    mGeneralData.getHistoryState() == pair[1]));
        }

        settingsPresenter.appendRadioCategory(getContext().getString(R.string.header_history), options);
    }

    private void appendChildModeCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.child_mode),
                getContext().getString(R.string.child_mode_desc),
                option -> {
                    if (option.isSelected()) {
                        AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.lost_setting_warning),
                                () -> showPasswordDialog(AppDialogPresenter.instance(getContext()), () -> enableChildMode(option.isSelected())),
                                () -> AppDialogPresenter.instance(getContext()).closeDialog());
                    } else {
                        mGeneralData.setSettingsPassword(null);
                        enableChildMode(option.isSelected());
                        AppDialogPresenter.instance(getContext()).closeDialog();
                    }
                },
                mGeneralData.isChildModeEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.child_mode), options);
    }

    private void appendMiscCategory(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.return_to_launcher),
                option -> mGeneralData.setReturnToLauncherEnabled(option.isSelected()),
                mGeneralData.isReturnToLauncherEnabled()));

        options.add(UiOptionItem.from(getContext().getString(R.string.select_channel_section),
                option -> mGeneralData.setSelectChannelSectionEnabled(option.isSelected()),
                mGeneralData.isSelectChannelSectionEnabled()));

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.settings_misc_options), options);
    }

    private void appendDataApiKeySwitch(SettingsRows settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.data_api_key),
                option -> {
                    if (option.isSelected()) {
                        showDataApiKeyDialog(AppDialogPresenter.instance(getContext()));
                    } else {
                        mMediaServiceData.setDataApiKey(null);
                    }
                },
                mMediaServiceData.getDataApiKey() != null));
    }

    /**
     * Opens the key in the editor: the switch can only enter a new key or remove the current one
     */
    private void appendDataApiKeyButton(SettingsRows settingsPresenter) {
        settingsPresenter.appendSingleButton(UiOptionItem.from(getContext().getString(R.string.data_api_key_change),
                option -> showDataApiKeyDialog(AppDialogPresenter.instance(getContext()))));
    }

    private void appendInternetCensorship(SettingsRows settingsPresenter) {
        List<OptionItem> options = new ArrayList<>();

        appendProxyManager(settingsPresenter, options);

        appendConscrypt(settingsPresenter, options);

        appendIpv6Test(options);

        settingsPresenter.appendCheckedCategory(getContext().getString(R.string.network_settings), options);
    }

    private void appendProxyManager(SettingsRows settingsPresenter, List<OptionItem> options) {
        ProxyManager proxyManager = new ProxyManager(getContext());

        if (proxyManager.isProxySupported()) {
            options.add(UiOptionItem.from(getContext().getString(R.string.enable_web_proxy),
                    option -> {
                        // Proxy with authentication supported only by OkHttp
                        mPlayerTweaksData.setPlayerDataSource(
                                option.isSelected() ? PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP : PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET);
                        mGeneralData.setProxyEnabled(option.isSelected());
                        new WebProxyDialog(getContext()).enable(option.isSelected());
                        if (option.isSelected()) {
                            AppDialogPresenter.instance(getContext()).closeDialog();
                        }

                        OkHttpManager.unhold();
                    },
                    mGeneralData.isProxyEnabled()));
        }
    }

    private void appendConscrypt(SettingsRows settingsPresenter, List<OptionItem> options) {
        options.add(UiOptionItem.from(getContext().getString(R.string.enable_conscrypt),
                getContext().getString(R.string.enable_conscrypt_desc),
                option -> {
                    mNetworkData.setConscryptEnabled(option.isSelected());
                    mRestartApp = true;
                },
                mNetworkData.isConscryptEnabled()));
    }

    private void appendIpv6Test(List<OptionItem> options) {
        options.add(UiOptionItem.from(getContext().getString(R.string.ipv6_test),
                getContext().getString(R.string.ipv6_test_desc),
                // Applies at the next connection, no restart (see Ipv6CheckDns)
                option -> mPlayerTweaksData.setIpv6TestEnabled(option.isSelected()),
                mPlayerTweaksData.isIpv6TestEnabled()));
    }

    private void enableChildMode(boolean enable) {
        mGeneralData.setChildModeEnabled(enable);

        int playerButtons = PlayerTweaksData.PLAYER_BUTTON_PLAY_PAUSE | PlayerTweaksData.PLAYER_BUTTON_NEXT | PlayerTweaksData.PLAYER_BUTTON_PREVIOUS |
                    PlayerTweaksData.PLAYER_BUTTON_DISLIKE | PlayerTweaksData.PLAYER_BUTTON_LIKE | PlayerTweaksData.PLAYER_BUTTON_SCREEN_DIMMING |
                    PlayerTweaksData.PLAYER_BUTTON_SEEK_INTERVAL | PlayerTweaksData.PLAYER_BUTTON_PLAYBACK_QUEUE | PlayerTweaksData.PLAYER_BUTTON_OPEN_CHANNEL |
                    PlayerTweaksData.PLAYER_BUTTON_PIP | PlayerTweaksData.PLAYER_BUTTON_VIDEO_SPEED | PlayerTweaksData.PLAYER_BUTTON_SUBTITLES |
                    PlayerTweaksData.PLAYER_BUTTON_VIDEO_ZOOM | PlayerTweaksData.PLAYER_BUTTON_ADD_TO_PLAYLIST;
        long menuItems = MainUIData.MENU_ITEM_SHOW_QUEUE | MainUIData.MENU_ITEM_ADD_TO_QUEUE | MainUIData.MENU_ITEM_PLAY_NEXT |
                    MainUIData.MENU_ITEM_STREAM_REMINDER | MainUIData.MENU_ITEM_SAVE_REMOVE_PLAYLIST;

        PlayerTweaksData tweaksData = PlayerTweaksData.instance(getContext());
        SearchData searchData = SearchData.instance(getContext());

        // Remove all
        mMainUIData.setTopButtonDisabled(Integer.MAX_VALUE);
        tweaksData.setPlayerButtonDisabled(Long.MAX_VALUE);
        mMainUIData.setAllMenuItemsDisabled();
        BrowsePresenter.instance(getContext()).enableAllSections(false);
        searchData.setPopularSearchesDisabled(true);

        if (enable) {
            // apply child tweaks. No top buttons: no search and no other account (see NavigateTitleView)
            tweaksData.setPlayerButtonEnabled(playerButtons);
            mMainUIData.setMenuItemsEnabled(menuItems);
            mPlayerData.setPlaybackMode(PlayerConstants.PLAYBACK_MODE_LIST);
            BrowsePresenter.instance(getContext()).enableSection(MediaGroup.TYPE_HISTORY, true);
            BrowsePresenter.instance(getContext()).enableSection(MediaGroup.TYPE_USER_PLAYLISTS, true);
            BrowsePresenter.instance(getContext()).enableSection(MediaGroup.TYPE_SUBSCRIPTIONS, true);
            BrowsePresenter.instance(getContext()).enableSection(MediaGroup.TYPE_CHANNEL_UPLOADS, true);
        } else {
            // apply default tweaks
            mMainUIData.setTopButtonEnabled(MainUIData.TOP_BUTTON_DEFAULT);
            tweaksData.setPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_DEFAULT);
            mMainUIData.setDefaultMenuItemsEnabled();
            BrowsePresenter.instance(getContext()).enableAllSections(true);
            // Each one is enabled again at its default index in a shorter list (see SidebarService.enableSection), so they're out of order
            BrowsePresenter.instance(getContext()).sortSectionsByDefault();
            tweaksData.setSuggestionsDisabled(false);
            mPlayerData.setPlaybackMode(PlayerConstants.PLAYBACK_MODE_ALL);
            searchData.setPopularSearchesDisabled(false);
        }
    }

    private void showPasswordDialog(AppDialogPresenter settingsPresenter, Runnable onSuccess) {
        if (mGeneralData.getSettingsPassword() != null) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        settingsPresenter.closeDialog();
        SimpleEditDialog.showPassword(
                getContext(),
                getContext().getString(R.string.protect_settings_with_password),
                null,
                newValue -> {
                    mGeneralData.setSettingsPassword(newValue);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                    return true;
                });
    }

    private void showDataApiKeyDialog(AppDialogPresenter settingsPresenter) {
        settingsPresenter.closeDialog();
        SimpleEditDialog.show(
                getContext(),
                getContext().getString(R.string.data_api_key),
                getContext().getString(R.string.data_api_key_hint),
                mMediaServiceData.getDataApiKey(),
                newValue -> {
                    mMediaServiceData.setDataApiKey(newValue);
                    checkDataApiKey(mMediaServiceData.getDataApiKey());
                    return true;
                });
    }

    private void checkDataApiKey(String key) {
        if (key == null) {
            return;
        }

        Context context = getContext();

        RxHelper.execute(getMediaItemService().checkDataApiKeyObserve(key),
                reason -> MessageHelpers.showLongMessage(context, reason.isEmpty() ?
                        context.getString(R.string.data_api_key_works) : context.getString(R.string.data_api_key_fails, reason)),
                error -> MessageHelpers.showLongMessage(context, context.getString(R.string.data_api_key_fails, error.getMessage())));
    }

    private void showMasterPasswordDialog(AppDialogPresenter settingsPresenter, Runnable onSuccess) {
        if (mGeneralData.getMasterPassword() != null) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        settingsPresenter.closeDialog();
        SimpleEditDialog.showPassword(
                getContext(),
                getContext().getString(R.string.enable_master_password),
                null,
                newValue -> {
                    mGeneralData.setMasterPassword(newValue);
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                    return true;
                });
    }
}
