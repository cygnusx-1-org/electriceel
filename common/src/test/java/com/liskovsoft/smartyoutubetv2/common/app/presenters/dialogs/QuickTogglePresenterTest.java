package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.prefs.AiSListFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.CollaborationsData;
import com.liskovsoft.smartyoutubetv2.common.prefs.OldVideosData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SectionFilterData;
import com.liskovsoft.smartyoutubetv2.common.prefs.StreamedData;
import com.liskovsoft.smartyoutubetv2.common.prefs.WatchLaterData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Which quick toggles the button has: it's shown with one, toggles it alone, opens the panel with more
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class QuickTogglePresenterTest {
    @Before
    public void setUp() {
        disableAll();
    }

    @After
    public void tearDown() {
        disableAll();
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    @Test
    public void noButtonWithoutQuickToggles() {
        QuickTogglePresenter presenter = getPresenter();

        assertFalse(presenter.isEnabled());
        assertEquals(-1, presenter.getSingleToggle());
    }

    /**
     * Marked or shown, the videos of a filter of Hide or mark content wouldn't change: its quick toggle waits for Hide
     */
    @Test
    public void filterOfHideOrMarkContentNeedsHide() {
        WatchLaterData data = WatchLaterData.instance(getContext());
        data.setQuickToggleEnabled(true);

        assertFalse(getPresenter().isToggleEnabled(QuickTogglePresenter.TOGGLE_WATCH_LATER));
        assertFalse(getPresenter().isEnabled());

        data.setMode(SectionFilterData.MODE_HIDE);

        assertTrue(getPresenter().isToggleEnabled(QuickTogglePresenter.TOGGLE_WATCH_LATER));
        assertEquals(QuickTogglePresenter.TOGGLE_WATCH_LATER, getPresenter().getSingleToggle());

        data.setMode(SectionFilterData.MODE_SHOW);

        assertFalse(getPresenter().isEnabled());
    }

    @Test
    public void aiNeedsAListSetToHide() {
        AiSListFilterData data = AiSListFilterData.instance(getContext());
        data.setQuickToggleEnabled(true);

        assertFalse(getPresenter().isToggleEnabled(QuickTogglePresenter.TOGGLE_AI));

        data.setHideEnabled(AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.SECTION_HOME, true);

        assertTrue(getPresenter().isToggleEnabled(QuickTogglePresenter.TOGGLE_AI));
        assertEquals(QuickTogglePresenter.TOGGLE_AI, getPresenter().getSingleToggle());
    }

    @Test
    public void severalQuickTogglesOpenThePanel() {
        enableHiding(StreamedData.instance(getContext()));
        enableHiding(CollaborationsData.instance(getContext()));
        OldVideosData.instance(getContext()).setQuickToggleEnabled(true);

        QuickTogglePresenter presenter = getPresenter();

        assertTrue(presenter.isEnabled());
        assertEquals(-1, presenter.getSingleToggle());
        assertTrue(presenter.isToggleEnabled(QuickTogglePresenter.TOGGLE_OLD_VIDEOS));
        assertTrue(presenter.isToggleEnabled(QuickTogglePresenter.TOGGLE_STREAMED));
        assertTrue(presenter.isToggleEnabled(QuickTogglePresenter.TOGGLE_COLLABORATIONS));
        assertFalse(presenter.isToggleEnabled(QuickTogglePresenter.TOGGLE_SHORTS));
    }

    /**
     * The state of the button (its icon and tooltip) follows the filter's quick toggle
     */
    @Test
    public void stateFollowsTheFilter() {
        CollaborationsData data = CollaborationsData.instance(getContext());
        enableHiding(data);

        QuickTogglePresenter presenter = getPresenter();

        assertTrue(presenter.isHiding(QuickTogglePresenter.TOGGLE_COLLABORATIONS));
        assertEquals("Hiding collaborations", presenter.getState(QuickTogglePresenter.TOGGLE_COLLABORATIONS));

        data.setQuickToggleHiding(false);

        assertFalse(presenter.isHiding(QuickTogglePresenter.TOGGLE_COLLABORATIONS));
        assertEquals("Marking collaborations", presenter.getState(QuickTogglePresenter.TOGGLE_COLLABORATIONS));
        // Still the button's toggle: the filter is set to hide
        assertEquals(QuickTogglePresenter.TOGGLE_COLLABORATIONS, presenter.getSingleToggle());
    }

    private static void enableHiding(SectionFilterData data) {
        data.setMode(SectionFilterData.MODE_HIDE);
        data.setQuickToggleEnabled(true);
    }

    private static void disableAll() {
        Context context = getContext();

        for (SectionFilterData data : new SectionFilterData[] {WatchLaterData.instance(context), StreamedData.instance(context),
                CollaborationsData.instance(context)}) {
            data.setMode(SectionFilterData.MODE_MARK);
            data.setQuickToggleEnabled(false);
            data.setQuickToggleHiding(true);
        }

        AiSListFilterData aiData = AiSListFilterData.instance(context);
        aiData.setQuickToggleEnabled(false);
        aiData.setQuickToggleHiding(true);
        for (int list : new int[] {AiSListFilterData.LIST_BLOCKLIST, AiSListFilterData.LIST_WARNLIST}) {
            aiData.setMarkMode(list, AiSListFilterData.MARK_MODE_OFF);
            aiData.setEverythingEnabled(list, false);
            for (int section = 0; section < AiSListFilterData.SECTION_COUNT; section++) {
                aiData.setHideEnabled(list, section, false);
            }
        }

        OldVideosData.instance(context).setQuickToggleEnabled(false);
        MediaServiceData.instance().setShortsQuickToggleEnabled(false);
    }

    private static QuickTogglePresenter getPresenter() {
        return QuickTogglePresenter.instance(getContext());
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }
}
