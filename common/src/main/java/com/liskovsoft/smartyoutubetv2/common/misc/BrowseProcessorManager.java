package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

import java.util.ArrayList;

public class BrowseProcessorManager implements BrowseProcessor {
    private final ArrayList<BrowseProcessor> mProcessors;

    public BrowseProcessorManager(Context context, OnItemReady onItemReady) {
        this(context, onItemReady, null);
    }

    /**
     * @param onItemRemoved removes a card from the view (null if the view can't do that)
     */
    public BrowseProcessorManager(Context context, OnItemReady onItemReady, OnItemRemoved onItemRemoved) {
        mProcessors = new ArrayList<>();
        mProcessors.add(new DeArrowProcessor(context, onItemReady));
        mProcessors.add(new UnlocalizedTitleProcessor(context, onItemReady));
        mProcessors.add(new AiSListProcessor(context, onItemReady, onItemRemoved));
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
