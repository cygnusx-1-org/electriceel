package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.reactivex.Observable;

/**
 * Holds each emission until what hides its videos after a lookup is known: the channel handles of AiSList and
 * the collaborations, in the sections they apply to. The groups are then built without those videos (see VideoGroup),
 * so a hidden video never appears first and vanishes after. The categories of Home are held the same way (see VideoCategoryManager).<br/>
 * A failed lookup doesn't hold the group back: its videos are shown.
 */
public class HiddenVideoResolver {
    private static final String TAG = HiddenVideoResolver.class.getSimpleName();

    /**
     * @param section the sidebar section of the groups built from the emission or null (e.g. search, a channel)
     */
    public static Observable<List<MediaGroup>> resolveGroups(Context context, Observable<List<MediaGroup>> groups, BrowseSection section) {
        if (groups == null) {
            return null;
        }

        return groups.concatMap(mediaGroups -> resolve(context, mediaGroups, section).map(unused -> mediaGroups));
    }

    /**
     * @param section the sidebar section of the group built from the emission or null (e.g. search, a channel)
     */
    public static Observable<MediaGroup> resolveGroup(Context context, Observable<MediaGroup> group, BrowseSection section) {
        if (group == null) {
            return null;
        }

        return group.concatMap(mediaGroup -> resolve(context, Collections.singletonList(mediaGroup), section).map(unused -> mediaGroup));
    }

    private static Observable<Boolean> resolve(Context context, List<MediaGroup> mediaGroups, BrowseSection section) {
        // No context before the app is initialized (GlobalPreferences)
        if (context == null || mediaGroups == null) {
            return Observable.just(true);
        }

        AiSListManager aiSListManager = AiSListManager.instance(context);
        CollaborationManager collaborationManager = CollaborationManager.instance(context);
        // Collaborations apply to the sidebar sections only (see VideoGroup)
        boolean isCollaborationEnabled = section != null && collaborationManager.isEnabled(section.getId());
        Map<String, String> videoIdByKey = new LinkedHashMap<>();
        Set<String> authors = new LinkedHashSet<>();
        boolean isAiSListUsed = false;

        for (MediaGroup mediaGroup : mediaGroups) {
            if (mediaGroup == null || mediaGroup.getMediaItems() == null) {
                continue;
            }

            // The section VideoGroup finds for the group
            int aiSListSection = AiSListManager.getSection(mediaGroup.getType(), section != null, mediaGroup.getChannelId());
            boolean isAiSListEnabled = aiSListManager.isSectionEnabled(aiSListSection);

            if (!isAiSListEnabled && !isCollaborationEnabled) {
                continue;
            }

            isAiSListUsed |= isAiSListEnabled;

            for (MediaItem item : mediaGroup.getMediaItems()) {
                // The card as the group sees it: the same channel name
                Video video = Video.from(item);

                // Channel cards aren't filtered
                if (video == null || video.videoId == null) {
                    continue;
                }

                if (isAiSListEnabled && video.channelHandle == null) {
                    String key = AiSListManager.getLookupKey(video);

                    if (key != null && !videoIdByKey.containsKey(key)) {
                        videoIdByKey.put(key, video.videoId);
                    }
                }

                String author = isCollaborationEnabled ? video.getAuthor() : null;

                if (CollaborationManager.isCandidate(author)) {
                    authors.add(author);
                }
            }
        }

        if (!isAiSListUsed && authors.isEmpty()) {
            return Observable.just(true);
        }

        return Observable.zip(
                // Known handles are checked against the lists too, e.g. at the first start
                isAiSListUsed ? aiSListManager.awaitLists() : Observable.just(true),
                aiSListManager.resolveHandles(videoIdByKey),
                collaborationManager.resolve(authors),
                (lists, handles, collaborations) -> true)
                // Show the videos rather than nothing
                .onErrorReturn(error -> {
                    Log.e(TAG, "Can't resolve the hidden videos: %s", error.getMessage());
                    return true;
                });
    }
}
