package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.CollaborationsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.FilterExclusionData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SectionFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.StreamedData;
import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLaterData;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hide or mark content filters set to hide that a channel is excluded from, picked in the video context menu.
 * The videos of the channel are marked instead (see FilterExclusionData).
 */
public class FilterExclusionSettingsPresenter extends BasePresenter<Void> {
    private final FilterExclusionData mData;

    private FilterExclusionSettingsPresenter(Context context) {
        super(context);
        mData = FilterExclusionData.instance(context);
    }

    public static FilterExclusionSettingsPresenter instance(Context context) {
        return new FilterExclusionSettingsPresenter(context);
    }

    /**
     * The filters a channel can be excluded from (FilterExclusionData.FILTER_*) that are set to hide
     */
    public static List<Integer> getHidingFilters(Context context) {
        List<Integer> filters = new ArrayList<>();

        for (int filter : FilterExclusionData.FILTERS) {
            if (getFilterData(context, filter).getMode() == SectionFilterData.MODE_HIDE) {
                filters.add(filter);
            }
        }

        return filters;
    }

    private static SectionFilterData getFilterData(Context context, int filter) {
        switch (filter) {
            case FilterExclusionData.FILTER_COLLABORATIONS:
                return CollaborationsData.instance(context);
            case FilterExclusionData.FILTER_WATCH_LATER:
                return WatchLaterData.instance(context);
            default:
                return StreamedData.instance(context);
        }
    }

    private static int getTitleResId(int filter) {
        switch (filter) {
            case FilterExclusionData.FILTER_COLLABORATIONS:
                return R.string.collaborations;
            case FilterExclusionData.FILTER_WATCH_LATER:
                return R.string.watch_later;
            default:
                return R.string.streamed;
        }
    }

    /**
     * A checked filter doesn't hide the videos of the channel. It's checked already when the channel is excluded from it.
     *
     * @param onChanged runs when the whole dialog closes, if the channel was excluded from a filter or included again. Might be null.
     */
    public void show(String channelId, String channelName, Runnable onChanged) {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        int excludedBefore = getExcludedFilters(channelId, channelName);

        List<OptionItem> options = new ArrayList<>();

        for (int filter : getHidingFilters(getContext())) {
            options.add(UiOptionItem.from(getContext().getString(getTitleResId(filter)),
                    option -> mData.setExcluded(filter, channelId, channelName, option.isSelected()),
                    mData.isExcluded(filter, channelId, channelName)));
        }

        String title = getContext().getString(R.string.exclude_channel_from_hiding_title, channelName);
        presenter.appendCheckedCategory(title, options);
        presenter.showDialog(title, () -> {
            if (onChanged != null && getExcludedFilters(channelId, channelName) != excludedBefore) {
                onChanged.run();
            }
        });
    }

    private int getExcludedFilters(String channelId, String channelName) {
        int filters = 0;

        for (int filter : FilterExclusionData.FILTERS) {
            if (mData.isExcluded(filter, channelId, channelName)) {
                filters |= filter;
            }
        }

        return filters;
    }
}
