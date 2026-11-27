package com.liskovsoft.smartyoutubetv2.common.settingssearch;

import android.app.Application;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.BackupSettingsPresenter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SettingsCatalogTest {
    /**
     * Every card is clicked, and nothing shows: no screen starts, no dialog is left to show, no message
     */
    @Test
    public void build_showsNothing() {
        Application app = RuntimeEnvironment.getApplication();

        SettingsCatalog catalog = SettingsCatalog.build(app);

        assertNull(Shadows.shadowOf(app).getNextStartedActivity());
        assertEquals(0, ShadowToast.shownToastCount());
        assertTrue(AppDialogPresenter.instance(app).isEmpty());
        assertTrue("Only " + catalog.getEntries().size(), catalog.getEntries().size() > 150);
    }

    /**
     * Each panel is closed as it would be: what it does as it closes is done (e.g. Backup lets the auto-backups run again, they
     * wait while it's held)
     */
    @Test
    public void build_closesThePanels() {
        Application app = RuntimeEnvironment.getApplication();

        SettingsCatalog.build(app);

        assertFalse(BackupSettingsPresenter.hasInstance());
        assertFalse(SettingsCapture.isActive());
    }

    /**
     * A question of words about no setting alone is searched all the same
     */
    @Test
    public void search_onlyCommonWords() {
        SettingsCatalog catalog = SettingsCatalog.build(RuntimeEnvironment.getApplication());

        assertFalse(SettingsSearch.find(catalog.getEntries(), "like", 10).isEmpty());
    }

    @Test
    public void build_idsAreUnique() {
        SettingsCatalog catalog = SettingsCatalog.build(RuntimeEnvironment.getApplication());
        Set<String> ids = new HashSet<>();

        for (SettingEntry entry : catalog.getEntries()) {
            assertTrue(entry.id, ids.add(entry.id));
            assertNotNull(catalog.find(entry.id));
        }
    }

    @Test
    public void search_findsSubtitles() {
        SettingsCatalog catalog = SettingsCatalog.build(RuntimeEnvironment.getApplication());

        List<SettingEntry> found = SettingsSearch.find(catalog.getEntries(), "subtitles", 10);

        assertFalse(found.isEmpty());
        assertTrue(found.get(0).toString(), found.get(0).toString().toLowerCase().contains("subtitle"));
    }

    /**
     * In the menu a row of a panel opens: Hide content, then Shorts
     */
    @Test
    public void build_rowsOfMenus() {
        SettingsCatalog catalog = SettingsCatalog.build(RuntimeEnvironment.getApplication());
        SettingEntry everywhere = null;

        for (SettingEntry entry : catalog.getEntries()) {
            if (entry.title.equals("Hide shorts everywhere")) {
                everywhere = entry;
            }
        }

        assertNotNull(everywhere);
        assertEquals("Shorts", everywhere.menuItemTitle);
        assertEquals(SettingEntry.KIND_TOGGLE, everywhere.kind);
        assertEquals("Hide shorts everywhere", SettingsSearch.find(catalog.getEntries(), "hide shorts everywhere", 3).get(0).title);
    }

    /**
     * The items of the list a button opens to put in order: Settings isn't one of the Sidebar order, it's always last
     */
    @Test
    public void build_itemsOfOrderLists() {
        SettingsCatalog catalog = SettingsCatalog.build(RuntimeEnvironment.getApplication());
        SettingEntry order = null;

        for (SettingEntry entry : catalog.getEntries()) {
            if (entry.title.equals("Order of the sidebar")) {
                order = entry;
            }
        }

        assertNotNull(order);
        assertFalse(order.choices.isEmpty());
        assertTrue(order.choices.toString(), order.choices.contains("Home"));
        assertFalse(order.choices.contains("Settings"));
    }

    /**
     * The rows of the list a button opens: Music, in Set-up sections of the Sidebar
     */
    @Test
    public void build_rowsOfButtonLists() {
        SettingsCatalog catalog = SettingsCatalog.build(RuntimeEnvironment.getApplication());
        SettingEntry music = null;

        for (SettingEntry entry : catalog.getEntries()) {
            if (entry.title.equals("Music") && entry.path.contains("Set-up sections")) {
                music = entry;
            }
        }

        assertNotNull(music);
        assertEquals("Set-up sections", music.menuItemTitle);
        assertEquals(SettingEntry.KIND_TOGGLE, music.kind);
    }
}
