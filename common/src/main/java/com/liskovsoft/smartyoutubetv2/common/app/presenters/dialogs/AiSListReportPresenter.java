package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.AiSListReport;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.AiSListManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.AiSListFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.reactivex.disposables.Disposable;

/**
 * Reports the channel of a video to AiSList as one that publishes AI-generated content, as the AiBlock extension does:
 * the video is the evidence, and a channel on the blocklist or reported from this device before isn't sent again.<br/>
 * Each report is confirmed first, the dialog showing the handle and who the report is credited to.
 */
public class AiSListReportPresenter extends BasePresenter<Void> {
    private static final String TAG = AiSListReportPresenter.class.getSimpleName();
    static final String ANONYMOUS = "Anonymous"; // what the AiBlock extension sends without a name
    private static final String VIDEO_URL = "https://www.youtube.com/watch?v=%s";
    private final AiSListManager mManager;
    private final AiSListFilterData mFilterData;
    private final MediaItemService mItemService;
    private Disposable mAction;

    private AiSListReportPresenter(Context context) {
        super(context);
        mManager = AiSListManager.instance(context);
        mFilterData = AiSListFilterData.instance(context);
        mItemService = YouTubeServiceManager.instance().getMediaItemService();
    }

    public static AiSListReportPresenter instance(Context context) {
        return new AiSListReportPresenter(context);
    }

    /**
     * Over the menu the report is chosen in. A collaboration asks which of its channels first.
     * @param onBlocked the channel was blocked too (see {@link AiSListFilterData#isBlockOnReportEnabled})
     */
    public void report(Video video, Runnable onBlocked) {
        if (video == null || video.videoId == null) {
            return;
        }

        RxHelper.disposeActions(mAction);
        mAction = mManager.getChannelHandles(video)
                .subscribe(
                        handles -> {
                            if (handles.isEmpty()) {
                                MessageHelpers.showMessage(getContext(), R.string.aislist_report_no_handle);
                            } else if (handles.size() == 1) {
                                confirm(video, handles.keySet().iterator().next(), onBlocked);
                            } else {
                                pickChannel(video, handles, onBlocked);
                            }
                        },
                        error -> {
                            Log.e(TAG, "Can't find the handle: %s", error.getMessage());
                            MessageHelpers.showMessage(getContext(), R.string.aislist_report_no_handle);
                        }
                );
    }

    private void pickChannel(Video video, Map<String, String> handles, Runnable onBlocked) {
        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getContext());
        List<OptionItem> options = new ArrayList<>();

        for (String handle : handles.keySet()) {
            options.add(UiOptionItem.from(handle, option -> confirm(video, handle, onBlocked)));
        }

        String title = getContext().getString(R.string.aislist_report_pick_channel);
        dialogPresenter.appendStringsCategory(title, options);
        dialogPresenter.showDialog(title);
    }

    private void confirm(Video video, String handle, Runnable onBlocked) {
        if (mManager.isBlocklisted(handle)) {
            MessageHelpers.showMessage(getContext(), R.string.aislist_report_blocklisted, handle);
            return;
        }

        if (mManager.isReported(handle)) {
            MessageHelpers.showMessage(getContext(), R.string.aislist_report_already_reported, handle);
            return;
        }

        String username = getUsername();

        AppDialogUtil.showConfirmationDialog(getContext(), getContext().getString(R.string.aislist_report_confirm, handle, username),
                () -> send(video, handle, username, onBlocked));
    }

    private void send(Video video, String handle, String username, Runnable onBlocked) {
        // Like Block the channel: the menu is closed at once, the answer comes as a message
        AppDialogPresenter.instance(getContext()).closeDialog();

        RxHelper.disposeActions(mAction);
        mAction = mItemService.reportAiSListChannelObserve(handle, String.format(VIDEO_URL, video.videoId), username)
                .subscribe(
                        report -> onReport(video, handle, report, onBlocked),
                        error -> {
                            Log.e(TAG, "Can't report %s: %s", handle, error.getMessage());
                            MessageHelpers.showMessage(getContext(), R.string.aislist_report_offline);
                        }
                );
    }

    private void onReport(Video video, String handle, AiSListReport report, Runnable onBlocked) {
        if (!report.isAccepted()) {
            MessageHelpers.showMessage(getContext(), getErrorMessage(report));
            return;
        }

        mManager.onReported(handle);

        String message = report.getMessage() != null ? report.getMessage() : getContext().getString(R.string.aislist_report_sent, handle);

        if (mFilterData.isBlockOnReportEnabled() && block(video)) {
            message += "\n" + getContext().getString(R.string.channel_blocked);

            if (onBlocked != null) {
                onBlocked.run();
            }
        }

        MessageHelpers.showMessage(getContext(), message);
    }

    /**
     * As Block the channel does it
     * @return the channel wasn't blocked before
     */
    private boolean block(Video video) {
        String channelName = video.getAuthor();
        BlockedChannelData blockedChannelData = BlockedChannelData.instance(getContext());

        if (channelName == null || blockedChannelData.containsChannel(video.channelId, channelName)) {
            return false;
        }

        blockedChannelData.addChannel(video.channelId, channelName);

        if (blockedChannelData.getChannelCount() == 1) { // show on add first channel
            BrowsePresenter.instance(getContext()).enableSection(MediaGroup.TYPE_BLOCKED_CHANNELS, true);
        }

        return true;
    }

    private String getErrorMessage(AiSListReport report) {
        switch (report.getStatus()) {
            case 0:
            case 502:
            case 503:
            case 504:
                return getContext().getString(R.string.aislist_report_offline);
            case 403:
                return getContext().getString(R.string.aislist_report_banned);
            case 429:
                return getContext().getString(R.string.aislist_report_rate_limited);
            default:
                return report.getMessage() != null ? report.getMessage() :
                        getContext().getString(R.string.aislist_report_failed, report.getStatus());
        }
    }

    /**
     * The name of the selected YouTube account when the reports are credited to it, else {@value #ANONYMOUS}
     */
    private String getUsername() {
        if (mFilterData.getReportAs() != AiSListFilterData.REPORT_AS_ACCOUNT_NAME) {
            return ANONYMOUS;
        }

        Account account = MediaServiceManager.instance().getSelectedAccount();
        String name = account != null && account.getName() != null ? account.getName().trim() : null;

        return name != null && !name.isEmpty() ? name : ANONYMOUS;
    }
}
