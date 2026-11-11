package com.liskovsoft.smartyoutubetv2.tv.ui.modern.signin;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;

public class ModernSignInActivity extends LeanbackActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new ModernSignInFragment())
                    .commitNow();
        }
    }

    @Override
    protected void initTheme() {
        setTheme(R.style.Theme_Modern);
        ModernColorScheme.apply(this);
    }

    @Override
    protected boolean isRecreatedOnThemeOrLocaleChange() {
        // As the classic SignInActivity: the new account may come just before the sign-in completes, recreating would dispose it
        return false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // singleInstance: a start while the screen is open brings it back without creating it again
        Fragment fragment = getSupportFragmentManager().findFragmentById(android.R.id.content);

        if (fragment instanceof ModernSignInFragment) {
            ((ModernSignInFragment) fragment).onRestarted();
        }
    }

    @Override
    public void finish() {
        // Not super.finish(): LeanbackActivity.finish() runs finishReally() as well (see SignInActivity)
        finishReally();
    }
}
