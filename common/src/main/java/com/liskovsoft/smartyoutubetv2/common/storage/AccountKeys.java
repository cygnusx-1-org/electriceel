package com.liskovsoft.smartyoutubetv2.common.storage;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * The key an account's settings are synced under: the same on every device the account is signed in on, derived from its
 * email and name (a brand account shares its owner's email). Only the key and the name reach the server, not the email.
 */
public final class AccountKeys {
    /**
     * The settings used while signed out
     */
    public static final String SIGNED_OUT = hash("signed-out");

    private AccountKeys() {
    }

    /**
     * A signed-in account: not a null one, nor the one with only a token while signing in (see
     * YouTubeAccountManager.persistRefreshToken), which has no email, name or profile yet
     */
    public static boolean isAccount(@Nullable Account account) {
        return account != null && !account.isEmpty();
    }

    public static String of(@Nullable Account account) {
        if (account == null) {
            return SIGNED_OUT;
        }

        String email = account.getEmail() != null ? account.getEmail().trim().toLowerCase(Locale.ROOT) : "";
        String name = account.getName() != null ? account.getName().trim() : "";

        return hash("account\n" + email + "\n" + name);
    }

    private static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(("electriceel\n" + text).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", digest[i]));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
