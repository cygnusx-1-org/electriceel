package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.source.sabr.GoogleVideoHosts;

/**
 * The host a track's DASH requests go to, as the YouTube app moves them (21.44): the one given, then the fallback host
 * (r{fvip}---{mn[1]}), then the redirector, which answers with a redirect to a host that works (a GET only). One step per host
 * that doesn't answer, so all of them are tried in the loader's attempts before its error reaches the player (see
 * DashDefaultLoadErrorHandlingPolicy: an error is thrown at the 4th failure of a load in a row, the first retry is at once).<br/>
 * A step holds for the track's next requests of the same media. A media network left is skipped for a while by every request
 * (see GoogleVideoHosts#markNetworkFailed), the SABR ones too. Not thread safe: a data source loads on one thread.
 */
final class GoogleVideoFallback {
    @VisibleForTesting
    static final int STEP_GIVEN = 0;
    @VisibleForTesting
    static final int STEP_FALLBACK = 1;
    @VisibleForTesting
    static final int STEP_REDIRECTOR = 2;
    /**
     * Failures in a row on the given host before the fallback host is asked: the retry of a quick failure (e.g. a connection
     * reset) costs nothing, a timeout counts as all of them, as it took seconds already. On the fallback host, one failure is enough.
     */
    @VisibleForTesting
    static final int GIVEN_HOST_FAILURES = 2;
    @Nullable
    private String mMediaUrl;
    private int mStep;
    private int mFailures;

    /**
     * The URL to ask in place of the given one
     */
    String resolve(String url, boolean isGet) {
        if (!GoogleVideoHosts.isSameMedia(url, mMediaUrl)) {
            mMediaUrl = url;
            mStep = STEP_GIVEN;
            mFailures = 0;
        }

        // Another track, or an earlier playback, left its network
        if (mStep == STEP_GIVEN && GoogleVideoHosts.isNetworkFailed(url) && GoogleVideoHosts.toFallbackUrl(url) != null) {
            mStep = STEP_FALLBACK;
            mFailures = 0;
        }

        String result = mStep == STEP_REDIRECTOR && isGet ? GoogleVideoHosts.toRedirectorUrl(url) : null;

        if (result == null && mStep != STEP_GIVEN) {
            result = GoogleVideoHosts.toFallbackUrl(url);
        }

        return result != null ? result : url;
    }

    void onSuccess() {
        mFailures = 0;
    }

    /**
     * A request to the URL last resolved got no answer: no connection, or none in the read timeout.
     *
     * @return the URL the next request goes to, when it's another host's
     */
    @Nullable
    String onHostFailure(String requestedUrl, boolean isTimeout, boolean isGet) {
        if (mMediaUrl == null) {
            return null;
        }

        mFailures += isTimeout ? GIVEN_HOST_FAILURES : 1;

        if (mStep == STEP_GIVEN && mFailures < GIVEN_HOST_FAILURES) {
            return null;
        }

        int nextStep = getNextStep(mMediaUrl, mStep, isGet);

        if (nextStep == mStep) {
            return null;
        }

        GoogleVideoHosts.markNetworkFailed(requestedUrl);
        mStep = nextStep;
        mFailures = 0;

        return resolve(mMediaUrl, isGet);
    }

    @VisibleForTesting
    int getStep() {
        return mStep;
    }

    private static int getNextStep(String url, int step, boolean isGet) {
        boolean hasRedirector = isGet && GoogleVideoHosts.toRedirectorUrl(url) != null;

        if (step == STEP_GIVEN && GoogleVideoHosts.toFallbackUrl(url) != null) {
            return STEP_FALLBACK;
        }

        return step != STEP_REDIRECTOR && hasRedirector ? STEP_REDIRECTOR : step;
    }
}
