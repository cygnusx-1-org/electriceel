package com.liskovsoft.smartyoutubetv2.tv.ui.modern.signin;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.QrCodeEncoder;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Sign in: actions on the start side, QR code and user code on the end side.<br/>
 * Same presenter flow as the classic sign in screen.
 */
public class ModernSignInFragment extends Fragment implements SignInView {
    private SignInPresenter mPresenter;
    private ImageView mQr;
    private TextView mDescription;
    private TextView mCode;
    private String mFullSignInUrl;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPresenter = SignInPresenter.instance(getContext());
        mPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.modern_signin, container, false);
        mQr = root.findViewById(R.id.modern_signin_qr);
        mDescription = root.findViewById(R.id.modern_signin_description);
        mCode = root.findViewById(R.id.modern_signin_code);

        LinearLayout actions = root.findViewById(R.id.modern_signin_actions);
        View done = addAction(inflater, actions, R.drawable.modern_ic_smartphone, R.string.signin_view_action_text);
        done.setOnClickListener(v -> mPresenter.onActionClicked());
        addAction(inflater, actions, R.drawable.modern_ic_open_in_browser, R.string.login_from_browser).setOnClickListener(v -> {
            if (mFullSignInUrl != null) {
                Utils.openLinkExt(getContext(), mFullSignInUrl);
            }
        });
        done.requestFocus();

        showPlaceholder();

        return root;
    }

    /**
     * Brought back by a new start (see ModernSignInActivity#onNewIntent). The code shown stays unless a sign-in waits for a new one.
     */
    public void onRestarted() {
        if (mPresenter.onViewRestarted() && mCode != null) {
            showPlaceholder();
        }
    }

    /**
     * The sign-in page until the code arrives
     */
    private void showPlaceholder() {
        mFullSignInUrl = null;
        mCode.setText(R.string.signin_view_title);
        mDescription.setText(getString(R.string.signin_view_description, ""));
        showQrCode(YTSignInPresenter.SIGN_IN_URL);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPresenter.onViewInitialized();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // The activity of another skin may have taken its place already (see Skins)
        if (!mPresenter.isReplacedView(this)) {
            mPresenter.onViewDestroyed();
        }
    }

    @Override
    public void showCode(String userCode, String signInUrl) {
        showCode(userCode, signInUrl, null);
    }

    @Override
    public void showCode(String userCode, String signInUrl, String fullSignInUrl) {
        if (getContext() == null || mCode == null) {
            return;
        }

        if (TextUtils.isEmpty(userCode)) {
            // No code and no error message: not left on "User Code is loading…" as if still loading
            userCode = getString(R.string.signin_view_error);
        }

        mCode.setText(userCode);

        if (signInUrl == null) { // e.g. Google's response without the address
            signInUrl = "";
        }

        mFullSignInUrl = fullSignInUrl != null ? fullSignInUrl : signInUrl;

        // An error has no address: the code of the sign-in page stays
        showQrCode(mFullSignInUrl);

        String description = getString(R.string.signin_view_description, signInUrl);
        int start = description.indexOf(signInUrl);
        int end = start + signInUrl.length();
        mDescription.setText(Utils.color(description, ContextCompat.getColor(getContext(), R.color.red), start, end));
    }

    /**
     * Made on the device, as in the classic sign in: there at once, offline too
     */
    private void showQrCode(@Nullable String url) {
        Bitmap qrCode = QrCodeEncoder.toBitmap(url, getResources().getDimensionPixelSize(R.dimen.modern_signin_qr_size));

        if (qrCode != null) {
            Drawable drawable = new BitmapDrawable(getResources(), qrCode);
            mQr.setImageDrawable(drawable);
        }
    }

    @Override
    public void close() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private static View addAction(LayoutInflater inflater, LinearLayout parent, int iconResId, int textResId) {
        View item = inflater.inflate(R.layout.modern_signin_item, parent, false);
        ((ImageView) item.findViewById(R.id.modern_signin_item_icon)).setImageResource(iconResId);
        ((TextView) item.findViewById(R.id.modern_signin_item_text)).setText(textResId);
        parent.addView(item);
        return item;
    }
}
