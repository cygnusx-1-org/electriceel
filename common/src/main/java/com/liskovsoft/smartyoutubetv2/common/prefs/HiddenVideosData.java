package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The videos hidden with the Hide (local) of the context menu: kept on the device, not on the account of YouTube. Hidden from every
 * section (see isSupportedSection), shown again from the Unhide section.<br/>
 * Each is kept with the section it was hidden in (see VideoGroup.getHiddenVideosSection), its row of the Unhide section: a sidebar
 * section, all the subscription groups as one (SectionFilterData.SECTION_CHANNEL_GROUPS), search, the channel pages or the player's
 * suggestions.<br/>
 * Each account has its own. Not settings: copying the settings of another account leaves them alone (see AppPrefs.copyProfileData).
 */
public class HiddenVideosData implements ProfileChangeListener {
    static final String HIDDEN_VIDEOS_DATA = "hidden_videos_data";
    // Past it, the ones hidden first are shown again: the feeds have long moved on from them
    static final int MAX_VIDEOS = 500;
    @SuppressLint("StaticFieldLeak")
    private static HiddenVideosData sInstance;
    private final AppPrefs mPrefs;
    // Read for each video of the rows, off the main thread too. Replaced, never changed.
    private volatile Snapshot mSnapshot = new Snapshot(Collections.emptyList());

    /**
     * A section and its hidden videos, the newest hidden first
     */
    public static final class Section {
        public final int sectionId;
        @Nullable
        public final String title; // the section's when it has no title of its own (see getTitle), e.g. a pinned channel
        public final List<Video> videos;

        Section(int sectionId, @Nullable String title, List<Video> videos) {
            this.sectionId = sectionId;
            this.title = title;
            this.videos = Collections.unmodifiableList(videos);
        }
    }

    private static final class Entry {
        final int sectionId;
        final String title;
        final Video video;

        Entry(int sectionId, String title, Video video) {
            this.sectionId = sectionId;
            this.title = title;
            this.video = video;
        }
    }

    /**
     * The entries, the oldest first, one a video, and the ids of their videos
     */
    private static final class Snapshot {
        final List<Entry> entries;
        final Set<String> videoIds = new HashSet<>();

        Snapshot(List<Entry> entries) {
            this.entries = Collections.unmodifiableList(entries);

            for (Entry entry : entries) {
                videoIds.add(entry.video.videoId);
            }
        }
    }

    private HiddenVideosData(Context context) {
        mPrefs = AppPrefs.instance(context);
        mPrefs.addListener(this);
        restoreState();
    }

    /**
     * Synchronized: first asked for by the rows being built in the background as well as by the menu
     */
    public static synchronized HiddenVideosData instance(Context context) {
        if (sInstance == null) {
            sInstance = new HiddenVideosData(context.getApplicationContext());
        }

        return sInstance;
    }

    /**
     * A video can be hidden from it, and the hidden ones are left out of it. Not the Unhide section, the local lists (e.g. the
     * playback queue) or a place that's no section (e.g. a playlist page).
     */
    public static boolean isSupportedSection(int sectionId) {
        switch (sectionId) {
            case MediaGroup.TYPE_UNDEFINED:
            case MediaGroup.TYPE_HIDDEN_VIDEOS:
            case MediaGroup.TYPE_SETTINGS:
            case MediaGroup.TYPE_PLAYBACK_QUEUE:
            case MediaGroup.TYPE_BLOCKED_CHANNELS:
            case MediaGroup.TYPE_BLOCKED_AI_CHANNELS:
                return false;
            default:
                return true;
        }
    }

    /**
     * Hidden from whichever section, it's left out of every one (see isSupportedSection)
     */
    public boolean isHidden(int sectionId, String videoId) {
        return videoId != null && isSupportedSection(sectionId) && mSnapshot.videoIds.contains(videoId);
    }

    /**
     * A card of its own is kept, as the video was shown: the Unhide section shows it as it was, in the row of the section.
     * Hidden again, it's the newest, in the row of the section it's hidden from now. Past {@link #MAX_VIDEOS}, the ones hidden first
     * are shown again.
     * @param title the section's, kept for a section without a title of its own (e.g. a pinned channel), or null
     */
    public void hide(int sectionId, @Nullable String title, Video video) {
        if (video == null || video.videoId == null || !isSupportedSection(sectionId)) {
            return;
        }

        List<Entry> entries = new ArrayList<>(mSnapshot.entries);
        removeEntry(entries, video.videoId);
        entries.add(new Entry(sectionId, title, createCard(video)));

        while (entries.size() > MAX_VIDEOS) {
            entries.remove(0);
        }

        update(entries);
    }

    /**
     * Shown again in every section
     */
    public void unhide(String videoId) {
        if (videoId == null || !mSnapshot.videoIds.contains(videoId)) {
            return;
        }

        List<Entry> entries = new ArrayList<>(mSnapshot.entries);
        removeEntry(entries, videoId);
        update(entries);
    }

    public boolean isEmpty() {
        return mSnapshot.entries.isEmpty();
    }

    /**
     * The videos hidden from a pinned section whose id changed go with it (see SidebarService.normalizeGroupIds)
     * @param sectionIds the old id to the new one
     */
    public void moveSections(Map<Integer, Integer> sectionIds) {
        if (sectionIds.isEmpty()) {
            return;
        }

        boolean isChanged = false;
        List<Entry> entries = new ArrayList<>();

        for (Entry entry : mSnapshot.entries) {
            Integer newId = sectionIds.get(entry.sectionId);

            if (newId != null) {
                entries.add(new Entry(newId, entry.title, entry.video));
                isChanged = true;
            } else {
                entries.add(entry);
            }
        }

        if (isChanged) {
            update(entries);
        }
    }

    /**
     * Each section with hidden videos, in the order of the oldest one still hidden from each, the newest hidden first.
     * New cards each time: the rows own them.
     */
    public List<Section> getSections() {
        Map<Integer, List<Video>> videos = new LinkedHashMap<>();
        Map<Integer, String> titles = new HashMap<>();

        for (Entry entry : mSnapshot.entries) {
            List<Video> sectionVideos = videos.get(entry.sectionId);

            if (sectionVideos == null) {
                sectionVideos = new ArrayList<>();
                videos.put(entry.sectionId, sectionVideos);
            }

            sectionVideos.add(0, createCard(entry.video));
            titles.put(entry.sectionId, entry.title); // the newest: a pinned section may have been renamed
        }

        List<Section> result = new ArrayList<>();

        for (Map.Entry<Integer, List<Video>> section : videos.entrySet()) {
            result.add(new Section(section.getKey(), titles.get(section.getKey()), section.getValue()));
        }

        return result;
    }

    /**
     * In the order of the rows of the Unhide section: the sidebar's sections in their default order, then the subscription groups,
     * the pinned sections (e.g. a channel), search, the channel pages and the player's suggestions
     * @param sidebarOrder the ids of the sidebar's sections in their default order (see SidebarService.getDefaultSections)
     */
    public static List<Section> sortForRows(List<Section> sections, Collection<Integer> sidebarOrder) {
        List<Integer> order = new ArrayList<>(sidebarOrder);
        List<Section> result = new ArrayList<>(sections);

        // Stable: the pinned ones stay in their order (see getSections)
        Collections.sort(result, (section1, section2) -> Integer.compare(getRowRank(section1.sectionId, order),
                getRowRank(section2.sectionId, order)));

        return result;
    }

    private static int getRowRank(int sectionId, List<Integer> sidebarOrder) {
        int index = sidebarOrder.indexOf(sectionId);

        if (index != -1) {
            return index;
        }

        int size = sidebarOrder.size();

        switch (sectionId) {
            case SectionFilterData.SECTION_CHANNEL_GROUPS:
                return size;
            case MediaGroup.TYPE_SEARCH:
                return size + 2;
            case MediaGroup.TYPE_CHANNEL:
                return size + 3;
            case MediaGroup.TYPE_SUGGESTIONS:
                return size + 4;
            default: // pinned
                return size + 1;
        }
    }

    @Override
    public void onProfileChanged() {
        restoreState();
    }

    @Override
    public void onProfileDataSynced(Set<String> keys) {
        if (keys.contains(HIDDEN_VIDEOS_DATA)) {
            restoreState();
        }
    }

    private static void removeEntry(List<Entry> entries, String videoId) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (videoId.equals(entries.get(i).video.videoId)) {
                entries.remove(i);
            }
        }
    }

    /**
     * Written right away: a write still pending when the account changes would go to the other account
     */
    private void update(List<Entry> entries) {
        mSnapshot = new Snapshot(entries);

        List<Integer> sectionIds = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<String> cards = new ArrayList<>();

        for (Entry entry : entries) {
            sectionIds.add(entry.sectionId);
            titles.add(entry.title);
            cards.add(entry.video.toString());
        }

        mPrefs.setProfileData(HIDDEN_VIDEOS_DATA, Helpers.mergeData(Helpers.mergeList(sectionIds), Helpers.mergeList(titles),
                Helpers.mergeList(cards)));
    }

    private void restoreState() {
        String[] split = Helpers.splitData(mPrefs.getProfileData(HIDDEN_VIDEOS_DATA));
        List<String> sectionIds = Helpers.parseStrList(split, 0);
        String[] titles = Helpers.parseArray(split, 1);
        String[] cards = Helpers.parseArray(split, 2);
        // By the video: saved before a video was hidden from every section, it may be there for more than one. The newest is kept.
        Map<String, Entry> entries = new LinkedHashMap<>();

        for (int i = 0; i < sectionIds.size(); i++) {
            Video video = cards != null && i < cards.length ? Video.fromString(cards[i]) : null;

            if (video == null || video.videoId == null) {
                continue;
            }

            String title = titles != null && i < titles.length ? Helpers.parseStr(titles[i]) : null;
            entries.remove(video.videoId); // the newest goes last
            entries.put(video.videoId, new Entry(Helpers.parseInt(sectionIds.get(i)), title, video));
        }

        mSnapshot = new Snapshot(new ArrayList<>(entries.values()));
    }

    /**
     * What the card shows, nothing of the row it was in (e.g. its continuation key) or of the marks of Hide or mark content
     */
    private static Video createCard(Video video) {
        Video card = new Video();
        card.videoId = video.videoId;
        card.title = video.getTitle();
        card.secondTitle = video.getSecondTitle() != null ? video.getSecondTitle().toString() : null;
        card.channelId = video.channelId;
        card.cardImageUrl = video.cardImageUrl;
        card.badge = video.badge;
        card.isLive = video.isLive;
        card.itemType = video.itemType;
        return card;
    }

    /**
     * The next instance reads the saved values again
     */
    static void resetInstanceForTesting() {
        sInstance = null;
    }
}
