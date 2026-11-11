package com.liskovsoft.smartyoutubetv2.tv.ui.modern.settings;

import android.os.Bundle;
import android.widget.FrameLayout;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;

/**
 * A settings section on its own screen in the modern skin (see the classic SettingsCardsActivity)
 */
public class ModernSettingsCardsActivity extends LeanbackActivity {
    private ModernSettingsCardsFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout content = new FrameLayout(this);
        content.setId(R.id.modern_settings_cards_content);
        setContentView(content);

        if (savedInstanceState == null) {
            mFragment = new ModernSettingsCardsFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.modern_settings_cards_content, mFragment)
                    .commitNow();
        } else {
            mFragment = (ModernSettingsCardsFragment) getSupportFragmentManager().findFragmentById(R.id.modern_settings_cards_content);
        }
    }

    @Override
    protected void initTheme() {
        setTheme(R.style.Theme_Modern);
        ModernColorScheme.apply(this);
    }

    @Override
    public void onReplaced() {
        // The screen of the other skin focuses the same card
        if (mFragment != null) {
            mFragment.saveSelectedPosition();
        }
    }

    @Override
    protected boolean isRestartedInPlace() {
        // The new instance focuses the same card (onReplaced)
        return true;
    }

    @Override
    public void onBackPressed() {
        // A section opened from a card (e.g. Player - Options) goes back to its parent first
        if (mFragment != null && mFragment.goBack()) {
            return;
        }

        super.onBackPressed();
    }
}
