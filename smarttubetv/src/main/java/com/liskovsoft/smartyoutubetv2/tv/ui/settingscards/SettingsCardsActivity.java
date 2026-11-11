package com.liskovsoft.smartyoutubetv2.tv.ui.settingscards;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class SettingsCardsActivity extends LeanbackActivity {
    private SettingsCardsFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_cards);
        mFragment = (SettingsCardsFragment) getSupportFragmentManager().findFragmentById(R.id.settings_cards_fragment);
    }

    @Override
    public void onReplaced() {
        // The screen of the other skin focuses the same card (see SettingsCardsPresenter.onViewSaved)
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
