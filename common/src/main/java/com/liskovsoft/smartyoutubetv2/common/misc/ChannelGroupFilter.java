package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.channelgroup.ChannelGroupServiceWrapper;
import com.liskovsoft.smartyoutubetv2.common.prefs.ChannelGroupFilterData;

/**
 * Hides the videos of the channels in any subscription group from the areas picked (see ChannelGroupFilterData).<br/>
 * A channel is known by its id, or by its name when the card has none (most TV cards): the name it had when it was added to the group.
 * A collaboration ("X and Y") by the name of each of its channels too, unless it's known to be one channel (see CollaborationManager).
 */
public final class ChannelGroupFilter {
    private ChannelGroupFilter() {
    }

    /**
     * @param area a section id or ChannelGroupFilterData.AREA_HOME_SUBSCRIPTIONS_ROW
     */
    public static boolean isHidden(Context context, int area, String channelId, String author) {
        if (!ChannelGroupFilterData.instance(context).isHidden(area)) {
            return false;
        }

        ChannelGroupServiceWrapper service = ChannelGroupServiceWrapper.instance(context);

        if (service.containsChannel(channelId, author)) {
            return true;
        }

        if (!CollaborationManager.isCandidate(author) || Boolean.FALSE.equals(CollaborationManager.instance(context).getCachedResult(author))) {
            return false;
        }

        for (String name : CollaborationManager.getChannelNames(author)) {
            if (service.containsChannel(null, name)) {
                return true;
            }
        }

        return false;
    }
}
