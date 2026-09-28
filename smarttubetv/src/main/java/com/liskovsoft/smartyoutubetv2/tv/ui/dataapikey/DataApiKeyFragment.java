package com.liskovsoft.smartyoutubetv2.tv.ui.dataapikey;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.widget.GuidanceStylist;
import androidx.leanback.widget.GuidedAction;
import androidx.leanback.widget.VerticalGridView;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.DataApiKeyPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.DataApiKeyView;
import com.liskovsoft.smartyoutubetv2.common.misc.GoogleCloudAuthorizer;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.List;

/**
 * One row per step, and the same rows in every state: only their text changes
 */
public class DataApiKeyFragment extends GuidedStepSupportFragment implements DataApiKeyView {
    private static final int RETRY = 10;
    private static final int CLOSE = 11;
    private DataApiKeyPresenter mPresenter;
    private GoogleCloudAuthorizer mAuthorizer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPresenter = DataApiKeyPresenter.instance(getContext());
        mPresenter.setView(this);
        mAuthorizer = new GoogleCloudAuthorizer(this, new GoogleCloudAuthorizer.Callback() {
            @Override
            public void onAuthorized(String accessToken) {
                mPresenter.onAuthorized(accessToken);
            }

            @Override
            public void onError(String message) {
                mPresenter.onAuthorizationError(message);
            }

            @Override
            public void onCancelled() {
                mPresenter.onAuthorizationCancelled();
            }
        });
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // The rows fit on the screen. The default alignment scrolls the list to keep the selected button on the keyline,
        // which pushed the first steps off the top and moved every row when the focus went between the buttons.
        getGuidedActionsStylist().getActionsGridView().setWindowAlignment(VerticalGridView.WINDOW_ALIGN_BOTH_EDGE);
        setSelectedActionPosition(findActionPositionById(CLOSE));
        mPresenter.onViewInitialized();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAuthorizer.dispose();
        mPresenter.onViewDestroyed();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (!mAuthorizer.onActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void authorize() {
        mAuthorizer.authorize();
    }

    @Override
    public void showStep(int step, CharSequence status) {
        GuidedAction action = findActionById(step);

        if (action != null) {
            action.setDescription(status);
            notifyActionChanged(findActionPositionById(step));
        }
    }

    @Override
    public void showDescription(CharSequence description) {
        if (getGuidanceStylist().getDescriptionView() != null) {
            getGuidanceStylist().getDescriptionView().setText(description);
        }
    }

    @Override
    public void enableRetry(boolean enable) {
        GuidedAction retry = findActionById(RETRY);

        if (retry == null) {
            return;
        }

        retry.setEnabled(enable);
        retry.setFocusable(enable);
        notifyActionChanged(findActionPositionById(RETRY));
        setSelectedActionPosition(findActionPositionById(enable ? RETRY : CLOSE));
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
        return new GuidanceStylist.Guidance(getString(R.string.data_api_key), getString(R.string.data_api_key_auto_description), "", null);
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        actions.add(createStep(STEP_PROJECT, R.string.data_api_key_auto_step_project));
        actions.add(createStep(STEP_API, R.string.data_api_key_auto_step_api));
        actions.add(createStep(STEP_KEY, R.string.data_api_key_auto_step_key));
        actions.add(createStep(STEP_TEST, R.string.data_api_key_auto_step_test));
        // Always there, so the rows never move: enabled after a failure
        actions.add(new GuidedAction.Builder()
                .id(RETRY)
                .title(getString(R.string.data_api_key_auto_retry))
                .enabled(false)
                .focusable(false)
                .build());
        actions.add(new GuidedAction.Builder()
                .id(CLOSE)
                .title(getString(R.string.data_api_key_auto_close))
                .build());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId() == RETRY) {
            mPresenter.onRetryClicked();
        } else if (action.getId() == CLOSE) {
            mPresenter.onCloseClicked();
        }
    }

    private GuidedAction createStep(int step, int titleResId) {
        return new GuidedAction.Builder()
                .id(step)
                .title(getString(titleResId))
                .description(getString(R.string.data_api_key_auto_waiting))
                .infoOnly(true)
                .focusable(false)
                .build();
    }
}
