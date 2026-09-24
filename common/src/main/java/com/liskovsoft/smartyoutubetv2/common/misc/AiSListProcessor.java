package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the handles of cards that carry only the channel name, then hides or marks the listed ones.<br/>
 * Cards with a known handle are handled earlier, while the group is created (see VideoGroup).
 */
public class AiSListProcessor implements BrowseProcessor {
    private static final String TAG = AiSListProcessor.class.getSimpleName();
    private final OnItemReady mOnItemReady;
    private final OnItemRemoved mOnItemRemoved;
    private final AiSListManager mManager;
    private final List<AiSListManager.OnHandle> mCallbacks = new ArrayList<>();

    public AiSListProcessor(Context context, OnItemReady onItemReady, OnItemRemoved onItemRemoved) {
        mOnItemReady = onItemReady;
        mOnItemRemoved = onItemRemoved;
        mManager = AiSListManager.instance(context);
    }

    @Override
    public void process(VideoGroup videoGroup) {
        if (videoGroup == null || videoGroup.isEmpty()) {
            return;
        }

        int section = videoGroup.getAiSListSection();

        if (!mManager.isSectionEnabled(section)) {
            return;
        }

        for (Video video : new ArrayList<>(videoGroup.getVideos())) {
            if (video.isChapter || video.videoId == null || video.channelHandle != null) {
                continue;
            }

            String key = AiSListManager.getLookupKey(video);

            if (key == null) {
                continue;
            }

            AiSListManager.OnHandle callback = handle -> apply(video, handle, section);
            mCallbacks.add(callback);
            mManager.lookupHandle(key, video.videoId, callback);
        }
    }

    @Override
    public void dispose() {
        for (AiSListManager.OnHandle callback : mCallbacks) {
            mManager.removeCallback(callback);
        }

        mCallbacks.clear();
    }

    private void apply(Video video, String handle, int section) {
        video.channelHandle = handle;

        if (mManager.isHidden(handle, section)) {
            mManager.onHidden(video);

            if (mOnItemRemoved != null) {
                mOnItemRemoved.onItemRemoved(video);
            }
        } else if (mManager.isMarked(handle, section)) {
            video.aiMarkList = mManager.getMarkedList(handle, section);
            mOnItemReady.onItemReady(video);
        }
    }
}
