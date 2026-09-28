package com.liskovsoft.smartyoutubetv2.common.app.views;

/**
 * Getting the user's own Data API key from their Google account, one row per step
 */
public interface DataApiKeyView {
    int STEP_PROJECT = 0;
    int STEP_API = 1;
    int STEP_KEY = 2;
    int STEP_TEST = 3;

    /**
     * Asks Play services for the cloud-platform token (see GoogleCloudAuthorizer)
     */
    void authorize();
    void showStep(int step, CharSequence status);
    void showDescription(CharSequence description);
    void enableRetry(boolean enable);
    void close();
}
