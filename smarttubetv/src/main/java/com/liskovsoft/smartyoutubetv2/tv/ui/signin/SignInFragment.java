package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.QrCodeEncoder;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.List;

public class SignInFragment extends GuidedStepSupportFragment implements SignInView {
    private static final int CONTINUE = 2;
    private static final int OPEN_BROWSER = 3;
    private SignInPresenter mSignInPresenter;
    private String mFullSignInUrl;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSignInPresenter = SignInPresenter.instance(getContext());
        mSignInPresenter.setView(this);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSignInPresenter.onViewInitialized();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSignInPresenter.onViewDestroyed();
    }

    @Override
    public void showCode(String userCode, String signInUrl) {
        setTitle(userCode, signInUrl, null);
    }

    @Override
    public void showCode(String userCode, String signInUrl, String fullSignInUrl) {
        setTitle(userCode, signInUrl, fullSignInUrl);
    }

    private void setTitle(String userCode, String signInUrl, String fullSignInUrl) {
        if (getContext() == null || TextUtils.isEmpty(userCode)) {
            return;
        }

        getGuidanceStylist().getTitleView().setText(userCode);

        if (signInUrl == null) { // e.g. Google's response without the address
            signInUrl = "";
        }

        mFullSignInUrl = fullSignInUrl != null ? fullSignInUrl : signInUrl;

        // An error has no address: the code of the sign-in page stays
        Drawable qrCode = createQrCode(mFullSignInUrl);

        if (qrCode != null) {
            getGuidanceStylist().getIconView().setImageDrawable(qrCode);
        }

        String description = getString(R.string.signin_view_description, signInUrl);
        int start = description.indexOf(signInUrl);
        int end = start + signInUrl.length();
        CharSequence coloredDescription = Utils.color(description, ContextCompat.getColor(getContext(), R.color.red), start, end);

        getGuidanceStylist().getDescriptionView().setText(coloredDescription);
    }

    @Override
    public void close() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    @NonNull
    public GuidanceStylist.Guidance onCreateGuidance(@NonNull Bundle savedInstanceState) {
        String title = getString(R.string.signin_view_title);
        String description = getString(R.string.signin_view_description, "");
        // The sign-in page until the code arrives
        return new GuidanceStylist.Guidance(title, description, "", createQrCode(YTSignInPresenter.SIGN_IN_URL));
    }

    /**
     * Made on the device: there at once, offline too. The same size before and after the code arrives, so it doesn't move.
     */
    @Nullable
    private Drawable createQrCode(@Nullable String url) {
        Bitmap qrCode = QrCodeEncoder.toBitmap(url, getResources().getDimensionPixelSize(R.dimen.sign_in_qr_code_size));

        return qrCode != null ? new BitmapDrawable(getResources(), qrCode) : null;
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        GuidedAction login = new GuidedAction.Builder()
                .id(CONTINUE)
                .title(getString(R.string.signin_view_action_text))
                .build();
        GuidedAction openBrowser = new GuidedAction.Builder()
                .id(OPEN_BROWSER)
                .title(getString(R.string.login_from_browser))
                .build();
        actions.add(login);
        actions.add(openBrowser);
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId() == CONTINUE) {
            mSignInPresenter.onActionClicked();
        } else if (action.getId() == OPEN_BROWSER) {
            if (mFullSignInUrl != null) {
                Utils.openLinkExt(getContext(), mFullSignInUrl);
            }
        }
    }
}
