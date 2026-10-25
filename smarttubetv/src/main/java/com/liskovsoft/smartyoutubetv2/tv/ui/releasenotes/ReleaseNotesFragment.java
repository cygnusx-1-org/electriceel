package com.liskovsoft.smartyoutubetv2.tv.ui.releasenotes;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ReleaseNotesPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ReleaseNotesView;
import com.liskovsoft.smartyoutubetv2.common.misc.GitHubReleases.Release;
import com.liskovsoft.smartyoutubetv2.common.misc.ReleaseNotesPage;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.text.DateFormat;
import java.util.Date;

/**
 * The notes of a release as GitHub shows them (see ReleaseNotesPage). The remote moves between the links and scrolls
 * the text between them. A picture opens on the whole screen, another link as a QR code.
 */
public class ReleaseNotesFragment extends Fragment implements ReleaseNotesView {
    private ReleaseNotesPresenter mPresenter;
    private WebView mWebView;
    private boolean mIsImageShown;
    /**
     * Its renderer stopped while the screen was in the background: it closes when it's back
     */
    private boolean mIsWebViewGone;
    private int mFocusedBackgroundColor;
    private int mFocusedTextColor;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPresenter = ReleaseNotesPresenter.instance(getContext());
        mPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.release_notes, container, false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = view.getContext();
        mFocusedBackgroundColor = getThemeColor(context, R.attr.cardSelectedBackground, R.color.card_selected_background_white);
        mFocusedTextColor = ContextCompat.getColor(context, R.color.card_selected_text_grey);

        mWebView = view.findViewById(R.id.release_notes_webview);
        mWebView.setBackgroundColor(Color.TRANSPARENT);

        WebSettings webSettings = mWebView.getSettings();
        // Only the page's own script: GitHub leaves none in the notes
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(false);

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return onLink(view, request.getUrl().toString());
            }

            /**
             * E.g. out of memory for a big picture on a TV: the screen closes, not the app
             */
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                onWebViewGone(view);
                return true;
            }
        });
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mPresenter.onViewInitialized();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mIsWebViewGone) {
            mIsWebViewGone = false;
            closeGone();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
    }

    public void onFinish() {
        mPresenter.onFinish();
    }

    @Override
    public void update(Release release) {
        if (mWebView == null) {
            return;
        }

        String date = release.publishedMs > 0 ? DateFormat.getDateInstance(DateFormat.LONG).format(new Date(release.publishedMs)) : "";

        mIsImageShown = false;
        mWebView.loadDataWithBaseURL(ReleaseNotesPage.BASE_URL,
                ReleaseNotesPage.build(release.version, date, release.bodyHtml, mFocusedBackgroundColor, mFocusedTextColor),
                "text/html", "utf-8", null);
        mWebView.requestFocus();
    }

    private void onWebViewGone(WebView webView) {
        if (webView.getParent() instanceof ViewGroup) {
            ((ViewGroup) webView.getParent()).removeView(webView);
        }

        webView.destroy();

        if (webView == mWebView) {
            mWebView = null;
        }

        mIsImageShown = false;

        // In the background (e.g. under a QR code of a link) only once it's back: closing starts the screen under it,
        // which would bring the app to the front
        if (isResumed()) {
            closeGone();
        } else {
            mIsWebViewGone = true;
        }
    }

    private void closeGone() {
        Activity activity = getActivity();

        if (activity != null) {
            MessageHelpers.showLongMessage(activity, R.string.release_notes_gone);
            activity.finish();
        }
    }

    public boolean isImageShown() {
        return mIsImageShown;
    }

    /**
     * @return a picture was on the whole screen
     */
    public boolean hideImage() {
        if (!mIsImageShown || mWebView == null) {
            return false;
        }

        mIsImageShown = false;
        mWebView.evaluateJavascript(ReleaseNotesPage.hideImage(), null);

        return true;
    }

    /**
     * @return the page stays
     */
    private boolean onLink(WebView webView, String url) {
        int imageIndex = ReleaseNotesPage.getImageIndex(url);

        if (imageIndex >= 0) {
            // Right away, not when the script answers: the next key is the picture's already
            mIsImageShown = true;
            webView.evaluateJavascript(ReleaseNotesPage.showImage(imageIndex), null);
            return true;
        }

        // Within the page (e.g. a footnote)
        if (url.startsWith(ReleaseNotesPage.BASE_URL + "#")) {
            return false;
        }

        mPresenter.openLink(url);

        return true;
    }

    /**
     * The color scheme sets the colors
     */
    private static int getThemeColor(Context context, int attr, int fallbackResId) {
        TypedValue value = new TypedValue();

        if (context.getTheme().resolveAttribute(attr, value, true) &&
                value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }

        return ContextCompat.getColor(context, fallbackResId);
    }
}
