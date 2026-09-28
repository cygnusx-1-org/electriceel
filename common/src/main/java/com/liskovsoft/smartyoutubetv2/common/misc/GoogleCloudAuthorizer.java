package com.liskovsoft.smartyoutubetv2.common.misc;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Scope;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.disposables.Disposable;

/**
 * A token with the cloud-platform scope, for getting the user's own Data API key.<br/>
 * Play services shows its own account picker and consent screen, on the TV: no browser, no second device.
 * The YouTube sign-in can't give this token: its device flow doesn't allow the scope, and its OAuth client isn't ours.<br/>
 * Where the Authorization API isn't on the device, the older GoogleAuthUtil, with an account picked from the AccountManager.<br/>
 * The fragment passes on its {@link Fragment#onActivityResult} and must be in a task of its own making
 * (not singleInstance): otherwise the consent screen's result comes back cancelled at once.<br/>
 * Logs enough to tell why it failed: the package and signing SHA-1 that Google matches the OAuth client against,
 * Play services' whole status, and after a failure Google's own reason from GoogleAuthUtil.
 */
public class GoogleCloudAuthorizer {
    private static final String TAG = GoogleCloudAuthorizer.class.getSimpleName();
    private static final String CLOUD_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    // A fragment's request codes are 16 bit
    private static final int REQUEST_AUTHORIZATION = 3001;
    private static final int REQUEST_ACCOUNT = 3002;
    private static final int REQUEST_CONSENT = 3003;
    private final Fragment mFragment;
    private final Callback mCallback;
    private Account mAccount;
    private Disposable mTokenAction;
    private Disposable mProbeAction;

    public interface Callback {
        void onAuthorized(String accessToken);
        void onError(String message);
        void onCancelled();
    }

    public GoogleCloudAuthorizer(Fragment fragment, Callback callback) {
        mFragment = fragment;
        mCallback = callback;
    }

    /**
     * Not on Fire TV or on a box without Google: the key is entered by hand there
     */
    public static boolean isAvailable(Context context) {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
    }

    public void authorize() {
        Activity activity = mFragment.getActivity();

        if (activity == null) {
            return;
        }

        Log.d(TAG, "Authorizing %s for %s, signed with SHA-1 %s, Play services %s", CLOUD_SCOPE, activity.getPackageName(),
                getSigningSha1(activity), getPlayServicesVersion(activity));

        AuthorizationRequest request = AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(CLOUD_SCOPE)))
                .build();

        Identity.getAuthorizationClient(activity)
                .authorize(request)
                .addOnSuccessListener(this::onAuthorizationResult)
                .addOnFailureListener(this::onAuthorizationFailure);
    }

    /**
     * @return the result was the authorizer's
     */
    public boolean onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case REQUEST_AUTHORIZATION:
                onConsentResult(resultCode, data);
                return true;
            case REQUEST_ACCOUNT:
                onAccountPicked(resultCode, data);
                return true;
            case REQUEST_CONSENT:
                if (resultCode == Activity.RESULT_OK) {
                    requestLegacyToken();
                } else {
                    mCallback.onCancelled();
                }
                return true;
        }

        return false;
    }

    public void dispose() {
        RxHelper.disposeActions(mTokenAction, mProbeAction);
    }

    private void onAuthorizationResult(AuthorizationResult result) {
        if (!mFragment.isAdded()) {
            return;
        }

        PendingIntent consent = result.getPendingIntent();
        Log.d(TAG, "Authorization result: consent needed %s, granted scopes %s, token %s", result.hasResolution(),
                result.getGrantedScopes(), result.getAccessToken() != null);

        if (!result.hasResolution() || consent == null) {
            onToken(result.getAccessToken()); // granted before
            return;
        }

        try {
            mFragment.startIntentSenderForResult(consent.getIntentSender(), REQUEST_AUTHORIZATION, null, 0, 0, 0, null);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Can't show the consent screen: %s", e.getMessage());
            mCallback.onError(e.getMessage());
        }
    }

    private void onAuthorizationFailure(Exception error) {
        if (!mFragment.isAdded()) {
            return;
        }

        // "API: Auth.Api.Identity.Authorization.API is not available on this device"
        if (error instanceof ApiException && ((ApiException) error).getStatusCode() == CommonStatusCodes.API_NOT_CONNECTED) {
            Log.d(TAG, "No Authorization API, using GoogleAuthUtil: %s", error.getMessage());
            pickAccount();
            return;
        }

        Log.e(TAG, "Authorization failed: %s", describe(error));
        mCallback.onError(error.getMessage());
        logTokenProbe();
    }

    private void onConsentResult(int resultCode, @Nullable Intent data) {
        Activity activity = mFragment.getActivity();

        if (activity == null) {
            return;
        }

        Log.d(TAG, "Consent screen result %s, extras %s", resultCode, data != null ? describe(data.getExtras()) : null);

        if (data == null) {
            if (resultCode == Activity.RESULT_OK) {
                mCallback.onError("No result from Play services");
            } else {
                mCallback.onCancelled();
            }
            return;
        }

        try {
            onToken(Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data).getAccessToken());
        } catch (ApiException e) {
            int status = e.getStatusCode();

            if (status == CommonStatusCodes.CANCELED || status == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                mCallback.onCancelled();
            } else {
                Log.e(TAG, "Consent failed: %s", describe(e));
                mCallback.onError(e.getMessage());
                logTokenProbe();
            }
        }
    }

    private void pickAccount() {
        Intent intent = AccountManager.newChooseAccountIntent(
                null, null, new String[] {GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE}, null, null, null, null);

        try {
            mFragment.startActivityForResult(intent, REQUEST_ACCOUNT);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "No account picker: %s", e.getMessage());
            mCallback.onError(e.getMessage());
        }
    }

    private void onAccountPicked(int resultCode, @Nullable Intent data) {
        String name = data != null ? data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) : null;

        if (resultCode != Activity.RESULT_OK || name == null) {
            mCallback.onCancelled();
            return;
        }

        mAccount = new Account(name, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
        requestLegacyToken();
    }

    /**
     * Off the main thread. The first time, the consent screen comes back as an exception.
     */
    private void requestLegacyToken() {
        Context context = mFragment.getContext();
        Account account = mAccount;

        if (context == null || account == null) {
            return;
        }

        Context appContext = context.getApplicationContext();

        RxHelper.disposeActions(mTokenAction);
        mTokenAction = RxHelper.execute(
                RxHelper.fromCallable(() -> GoogleAuthUtil.getToken(appContext, account, "oauth2:" + CLOUD_SCOPE)),
                this::onToken,
                this::onLegacyTokenError
        );
    }

    private void onLegacyTokenError(Throwable error) {
        if (!mFragment.isAdded()) {
            return;
        }

        if (error instanceof UserRecoverableAuthException) {
            Log.d(TAG, "GoogleAuthUtil needs the user: %s", describe(error));
            Intent consent = ((UserRecoverableAuthException) error).getIntent();

            if (consent != null) {
                try {
                    mFragment.startActivityForResult(consent, REQUEST_CONSENT);
                    return;
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "No consent screen: %s", e.getMessage());
                }
            }
        }

        Log.e(TAG, "GoogleAuthUtil failed: %s", describe(error));
        mCallback.onError(error.getMessage());
    }

    private void onToken(@Nullable String accessToken) {
        if (!mFragment.isAdded()) {
            return;
        }

        if (accessToken == null || accessToken.isEmpty()) {
            mCallback.onError("No access token");
        } else {
            mCallback.onAuthorized(accessToken);
        }
    }

    /**
     * Diagnostics only, never shown and the token never used. The Authorization API reduces a failure to a status code
     * (e.g. 8, "Unknown error"), while GoogleAuthUtil passes on Google's own reason: e.g. UNREGISTERED_ON_API_CONSOLE
     * when no Android OAuth client matches the package and SHA-1, NeedPermission when only the consent is missing.
     */
    private void logTokenProbe() {
        Context context = mFragment.getContext();

        if (context == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        Account[] visible = AccountManager.get(appContext).getAccountsByType(GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
        String youTubeEmail = getYouTubeEmail();
        Log.d(TAG, "Probe: %s Google accounts visible to the app, YouTube account email known: %s", visible.length, youTubeEmail != null);

        String name = visible.length > 0 ? visible[0].name : youTubeEmail;

        if (name == null) {
            Log.d(TAG, "Probe: no account to try");
            return;
        }

        Account account = new Account(name, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);

        RxHelper.disposeActions(mProbeAction);
        mProbeAction = RxHelper.execute(
                RxHelper.fromCallable(() -> GoogleAuthUtil.getToken(appContext, account, "oauth2:" + CLOUD_SCOPE)),
                token -> Log.d(TAG, "Probe: GoogleAuthUtil got a token"),
                error -> Log.e(TAG, "Probe: GoogleAuthUtil failed: %s", describe(error))
        );
    }

    @Nullable
    private static String getYouTubeEmail() {
        com.liskovsoft.mediaserviceinterfaces.oauth.Account account = MediaServiceManager.instance().getSelectedAccount();

        return account != null ? account.getEmail() : null;
    }

    private static String describe(Throwable error) {
        if (error instanceof ApiException) {
            int status = ((ApiException) error).getStatusCode();

            return String.format("%s (%s), %s", CommonStatusCodes.getStatusCodeString(status), status, ((ApiException) error).getStatus());
        }

        return error.getClass().getSimpleName() + ": " + error.getMessage();
    }

    /**
     * Keys and the type of each value, never the value: the result holds the token.
     * The log can be a file the user shares (no email address either).
     */
    private String describe(@Nullable Bundle extras) {
        if (extras == null) {
            return null;
        }

        List<String> result = new ArrayList<>();

        try {
            extras.setClassLoader(getClass().getClassLoader());

            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                result.add(key + "=" + (value instanceof byte[] ? ((byte[]) value).length + " bytes" : value != null ? value.getClass().getSimpleName() : null));
            }
        } catch (RuntimeException e) { // e.g. a parcelable of Play services' own
            result.add("unreadable: " + e.getMessage());
        }

        return result.toString();
    }

    /**
     * As the Cloud console shows it, for the Android OAuth client
     */
    private static String getSigningSha1(Context context) {
        try {
            PackageManager manager = context.getPackageManager();
            Signature[] signatures;

            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo info = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                signatures = info.signingInfo != null ? info.signingInfo.getApkContentsSigners() : null;
            } else {
                PackageInfo info = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
                signatures = info.signatures;
            }

            if (signatures == null || signatures.length == 0) {
                return null;
            }

            List<String> result = new ArrayList<>();

            for (Signature signature : signatures) {
                StringBuilder hex = new StringBuilder();

                for (byte b : MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())) {
                    hex.append(hex.length() > 0 ? ":" : "").append(String.format("%02X", b));
                }

                result.add(hex.toString());
            }

            return TextUtils.join(", ", result);
        } catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException e) {
            return e.getMessage();
        }
    }

    private static String getPlayServicesVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE, 0);

            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
