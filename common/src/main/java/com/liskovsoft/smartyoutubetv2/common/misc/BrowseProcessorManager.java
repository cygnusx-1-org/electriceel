package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;

public class BrowseProcessorManager implements BrowseProcessor {
    private final ArrayList<BrowseProcessor> mProcessors;

    /**
     * Only changes cards, never removes one: what hides a video is known before its card is shown (see HiddenVideoResolver)
     */
    public BrowseProcessorManager(Context context, OnItemReady onItemReady) {
        mProcessors = new ArrayList<>();
        mProcessors.add(new DeArrowProcessor(context, onItemReady));
        mProcessors.add(new UnlocalizedTitleProcessor(context, onItemReady));
    }

    @Override
    public void process(VideoGroup videoGroup) {
        for (BrowseProcessor processor : mProcessors) {
            processor.process(videoGroup);
        }
    }

    @Override
    public void dispose() {
        for (BrowseProcessor processor : mProcessors) {
            processor.dispose();
        }
    }
}
