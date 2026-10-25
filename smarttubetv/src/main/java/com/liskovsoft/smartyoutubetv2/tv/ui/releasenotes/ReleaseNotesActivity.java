package com.liskovsoft.smartyoutubetv2.tv.ui.releasenotes;

import android.os.Bundle;
import android.view.KeyEvent;

import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class ReleaseNotesActivity extends LeanbackActivity {
    private ReleaseNotesFragment mFragment;
    private boolean mIsConfirmPressed;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The fragment can't be made without a WebView (see WebBrowserActivity)
        try {
            setContentView(R.layout.fragment_release_notes);
        } catch (Exception e) {
            e.printStackTrace();
            MessageHelpers.showMessage(this, e.getMessage());
            finish();
            return;
        }

        mFragment = (ReleaseNotesFragment) getSupportFragmentManager().findFragmentById(R.id.release_notes_fragment);
    }

    /**
     * A picture on the whole screen keeps the keys: the links under it don't move. OK closes it, as Back.
     * Not the release of the press that opened it: only a press that started over the picture.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        if (mFragment == null || !mFragment.isImageShown()) {
            mIsConfirmPressed = false;
            return super.dispatchKeyEvent(event);
        }

        if (KeyHelpers.isConfirmKey(keyCode)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                mIsConfirmPressed = true;
            } else if (event.getAction() == KeyEvent.ACTION_UP && mIsConfirmPressed) {
                mIsConfirmPressed = false;
                mFragment.hideImage();
            }

            return true;
        }

        return KeyHelpers.isNavigationKey(keyCode) || super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        // The picture first, the notes close the screen
        if (mFragment != null && mFragment.hideImage()) {
            return;
        }

        super.onBackPressed();
    }

    @Override
    public void finishReally() {
        super.finishReally();

        if (mFragment != null) {
            mFragment.onFinish();
        }
    }
}
