package com.liskovsoft.smartyoutubetv2.tv.ui.signin;

import android.content.Intent;
import android.os.Bundle;
import androidx.leanback.app.GuidedStepSupportFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class SignInActivity extends LeanbackActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == savedInstanceState) {
            GuidedStepSupportFragment.addAsRoot(this, new SignInFragment(), android.R.id.content);
        }
    }

    @Override
    protected boolean isRecreatedOnThemeOrLocaleChange() {
        // The new account (another profile) may come just before the sign-in completes. Recreating would dispose the sign-in
        // (YTSignInPresenter.onViewDestroyed), so the screen wouldn't close and would show a new code.
        return false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // singleInstance: a start while the screen is open brings it back without creating it again
        GuidedStepSupportFragment fragment = GuidedStepSupportFragment.getCurrentGuidedStepSupportFragment(getSupportFragmentManager());

        if (fragment instanceof SignInFragment) {
            ((SignInFragment) fragment).onRestarted();
        }
    }

    @Override
    public void finish() {
        // Not super.finish(): LeanbackActivity.finish() runs finishReally() as well. Run twice, it takes two views off the
        // ViewManager stack and starts two parents (settings, then browse), so the settings cards stop opening until a restart.
        finishReally();
    }
}
