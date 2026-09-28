package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;

import com.liskovsoft.googleapi.cloudapi.CloudKeyException;
import com.liskovsoft.googleapi.cloudapi.CloudKeyResult;
import com.liskovsoft.googleapi.cloudapi.CloudKeyStep;
import com.liskovsoft.googleapi.service.CloudKeyService;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.GeneralSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.DataApiKeyView;
import com.liskovsoft.smartyoutubetv2.common.misc.GoogleCloudAuthorizer;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.disposables.Disposable;

/**
 * Gets the user's own Data API key from their Google account: Play services signs them in (cloud-platform scope),
 * then their existing key is found, or a project and key are made.<br/>
 * Runs by itself after a YouTube sign-in, and from the settings. Only where Play services is on the device.
 * The key can always be entered by hand too.
 */
public class DataApiKeyPresenter extends BasePresenter<DataApiKeyView> {
    private static final String TAG = DataApiKeyPresenter.class.getSimpleName();
    private static final int[] STEPS = {DataApiKeyView.STEP_PROJECT, DataApiKeyView.STEP_API, DataApiKeyView.STEP_KEY, DataApiKeyView.STEP_TEST};
    private static final Pattern URL = Pattern.compile("https?://[^\\s)]+");
    @SuppressLint("StaticFieldLeak")
    private static DataApiKeyPresenter sInstance;
    private Disposable mKeyAction;
    // The step that's working, to show it stopped when the flow fails
    private CloudKeyStep.Step mCurrentStep;

    private DataApiKeyPresenter(Context context) {
        super(context);
    }

    public static DataApiKeyPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new DataApiKeyPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        RxHelper.disposeActions(mKeyAction);
        sInstance = null;
    }

    /**
     * The settings item: also the repair, e.g. for a key deleted in the Cloud console or an account signed in before this
     */
    public void start() {
        getViewManager().startView(DataApiKeyView.class);
    }

    /**
     * Right after a YouTube sign-in, with no trip to the settings. A key the user already has is left alone.
     */
    public void startAfterSignIn() {
        if (MediaServiceData.instance().getDataApiKey() != null || !GoogleCloudAuthorizer.isAvailable(getContext())
                || !Utils.isAppInForegroundFixed()) {
            unhold(); // no view will release it
            return;
        }

        start();
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        authorize();
    }

    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();
        unhold();
    }

    public void onRetryClicked() {
        authorize();
    }

    public void onCloseClicked() {
        if (getView() != null) {
            getView().close();
        }
    }

    public void onAuthorized(String accessToken) {
        RxHelper.disposeActions(mKeyAction);
        mKeyAction = CloudKeyService.getKeyObserve(accessToken)
                .subscribe(
                        this::onStep,
                        this::onKeyError
                );
    }

    public void onAuthorizationError(String message) {
        showError(getContext().getString(R.string.data_api_key_auto_sign_in_error, message != null ? message : ""));
    }

    public void onAuthorizationCancelled() {
        showError(getContext().getString(R.string.data_api_key_auto_cancelled));
    }

    private void authorize() {
        RxHelper.disposeActions(mKeyAction);
        mCurrentStep = null;

        if (getView() == null) {
            return;
        }

        for (int step : STEPS) {
            getView().showStep(step, getContext().getString(R.string.data_api_key_auto_waiting));
        }

        getView().showDescription(getContext().getString(R.string.data_api_key_auto_description));
        getView().enableRetry(false);
        getView().authorize();
    }

    private void onStep(CloudKeyStep step) {
        mCurrentStep = step.isDone() ? null : step.getStep();

        if (getView() != null) {
            getView().showStep(toViewStep(step.getStep()), getStatus(step));
        }

        CloudKeyResult result = step.getResult();

        if (result != null) {
            onKey(result);
        }
    }

    private void onKey(CloudKeyResult result) {
        Log.d(TAG, "Got the key of project %s, reused: %s", result.getProjectId(), result.isKeyReused());

        MediaServiceData.instance().setDataApiKey(result.getKeyString());
        GeneralSettingsPresenter.instance(getContext()).checkDataApiKey(MediaServiceData.instance().getDataApiKey());

        if (getView() != null) {
            getView().showDescription(getContext().getString(
                    result.isKeyReused() ? R.string.data_api_key_auto_done_reused : R.string.data_api_key_auto_done_created));
        }
    }

    private void onKeyError(Throwable error) {
        if (error instanceof CloudKeyException) {
            CloudKeyException cloudError = (CloudKeyException) error;
            Log.e(TAG, "Couldn't get the key: %s, kind %s, status %s, reason %s", error.getMessage(), cloudError.getKind(),
                    cloudError.getStatus(), cloudError.getReason());
        } else {
            Log.e(TAG, "Couldn't get the key: %s", error);
        }

        if (mCurrentStep != null && getView() != null) {
            getView().showStep(toViewStep(mCurrentStep), getContext().getString(R.string.data_api_key_auto_failed));
        }

        mCurrentStep = null;
        showError(getErrorMessage(error));
    }

    private void showError(String message) {
        if (getView() != null) {
            getView().showDescription(message);
            getView().enableRetry(true);
        }
    }

    private String getErrorMessage(Throwable error) {
        Context context = getContext();

        if (!(error instanceof CloudKeyException)) {
            return context.getString(R.string.data_api_key_auto_error, error.getMessage());
        }

        switch (((CloudKeyException) error).getKind()) {
            case TERMS_OF_SERVICE:
                // e.g. the YouTube API's own terms, at console.developers.google.com/terms/youtube: Google's message has the page
                String termsUrl = findUrl(error.getMessage());
                return termsUrl != null ? context.getString(R.string.data_api_key_auto_terms_url, termsUrl)
                        : context.getString(R.string.data_api_key_auto_terms);
            case PROJECT_QUOTA:
                return context.getString(R.string.data_api_key_auto_project_quota);
            case ORG_POLICY:
                return context.getString(R.string.data_api_key_auto_org_policy);
            case OWNER_SETUP:
                return context.getString(R.string.data_api_key_auto_owner_setup, error.getMessage());
            case TIMEOUT:
                return context.getString(R.string.data_api_key_auto_timeout);
            case NETWORK:
                return context.getString(R.string.data_api_key_auto_network);
            default:
                return context.getString(R.string.data_api_key_auto_error, error.getMessage());
        }
    }

    private String getStatus(CloudKeyStep step) {
        Context context = getContext();

        if (!step.isDone()) {
            return context.getString(step.getStep() == CloudKeyStep.Step.PROJECT ?
                    R.string.data_api_key_auto_looking : R.string.data_api_key_auto_working);
        }

        switch (step.getStep()) {
            case PROJECT:
                return context.getString(step.isReused() ? R.string.data_api_key_auto_project_reused : R.string.data_api_key_auto_created);
            case API:
                return context.getString(step.isReused() ? R.string.data_api_key_auto_api_reused : R.string.data_api_key_auto_api_enabled);
            case KEY:
                return context.getString(step.isReused() ? R.string.data_api_key_auto_key_reused : R.string.data_api_key_auto_created);
            default:
                return context.getString(R.string.data_api_key_auto_key_works);
        }
    }

    /**
     * The first link in the text, without its scheme (it's read off a TV screen)
     */
    private static String findUrl(String text) {
        if (text == null) {
            return null;
        }

        Matcher matcher = URL.matcher(text);

        return matcher.find() ? matcher.group().replaceFirst("^https?://", "") : null;
    }

    private static int toViewStep(CloudKeyStep.Step step) {
        switch (step) {
            case PROJECT:
                return DataApiKeyView.STEP_PROJECT;
            case API:
                return DataApiKeyView.STEP_API;
            case KEY:
                return DataApiKeyView.STEP_KEY;
            default:
                return DataApiKeyView.STEP_TEST;
        }
    }
}
