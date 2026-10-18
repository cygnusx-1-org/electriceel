package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuManager;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers.ContextMenuProvider;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData.ColorScheme;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MainUIDataTest {
    private static final String DATA_KEY = "main_ui_data2";
    private static final Account FIRST_ACCOUNT = TestAccounts.FIRST;
    private static final Account SECOND_ACCOUNT = TestAccounts.SECOND;

    @Before
    public void setUp() {
        getPrefs().onAccountChanged(null); // the anonymous account
        getPrefs().resetSharedSettingsProfileForTesting();
        clearSaved();
    }

    @After
    public void tearDown() {
        Utils.sHandler.removeCallbacksAndMessages(null);
        getPrefs().onAccountChanged(null);
        getPrefs().resetSharedSettingsProfileForTesting();
        clearSaved();
    }

    /**
     * The shared settings are the first account's (e.g. from before each account had its own): an added account doesn't get them
     */
    @Test
    public void addedAccountStartsFromTheDefaultColorScheme() {
        AppPrefs prefs = getPrefs();
        MainUIData data = getData();
        int defaultId = data.getColorScheme().id;

        prefs.setData(DATA_KEY, sharedSettings(getOtherScheme(data, defaultId)));
        prefs.onAccountChanged(SECOND_ACCOUNT);

        assertEquals(defaultId, data.getColorScheme().id);
    }

    /**
     * SmartTube's backup or a version before this one: the shared settings are the account's they were made with
     */
    @Test
    public void accountTheSharedSettingsWereMadeWithKeepsThem() {
        AppPrefs prefs = getPrefs();
        MainUIData data = getData();
        int defaultId = data.getColorScheme().id;
        ColorScheme picked = getOtherScheme(data, defaultId);

        prefs.setData(DATA_KEY, sharedSettings(picked));
        prefs.onAccountChanged(FIRST_ACCOUNT);
        prefs.initSharedSettingsProfile(); // the app starts with these settings
        prefs.onAccountChanged(FIRST_ACCOUNT);

        assertEquals(picked.id, data.getColorScheme().id);

        prefs.onAccountChanged(SECOND_ACCOUNT);

        assertEquals(defaultId, data.getColorScheme().id);

        // Copied to the account's own: a later change of the shared ones doesn't reach it
        prefs.setData(DATA_KEY, sharedSettings(getOtherScheme(data, defaultId, picked.id)));
        prefs.onAccountChanged(FIRST_ACCOUNT);

        assertEquals(picked.id, data.getColorScheme().id);
    }

    /**
     * "Use separate settings per each account" was off: every account used the shared settings, and keeps them
     */
    @Test
    public void sharedSettingsInUseStayWithEachAccount() {
        AppPrefs prefs = getPrefs();
        MainUIData data = getData();
        int defaultId = data.getColorScheme().id;
        ColorScheme picked = getOtherScheme(data, defaultId);
        String stale = sharedSettings(getOtherScheme(data, defaultId, picked.id));

        prefs.setData(DATA_KEY, sharedSettings(picked));
        prefs.setData(FIRST_ACCOUNT.getName() + "_" + DATA_KEY, stale); // from before the switch was turned off
        prefs.setData(SECOND_ACCOUNT.getName() + "_" + DATA_KEY, stale);
        prefs.putBoolean("multi_profiles", false);

        prefs.migrateSharedSettings(); // the app starts with these settings

        prefs.onAccountChanged(FIRST_ACCOUNT);

        assertEquals(picked.id, data.getColorScheme().id);

        prefs.onAccountChanged(SECOND_ACCOUNT);

        assertEquals(picked.id, data.getColorScheme().id);
    }

    @Test
    public void eachAccountHasItsOwnColorScheme() {
        AppPrefs prefs = getPrefs();
        MainUIData data = getData();
        int defaultId = data.getColorScheme().id;
        ColorScheme picked = getOtherScheme(data, defaultId);

        prefs.onAccountChanged(FIRST_ACCOUNT);
        data.setColorScheme(picked);
        ShadowLooper.shadowMainLooper().idle();

        prefs.onAccountChanged(SECOND_ACCOUNT);

        assertEquals(defaultId, data.getColorScheme().id);

        prefs.onAccountChanged(FIRST_ACCOUNT);

        assertEquals(picked.id, data.getColorScheme().id);
    }

    @Test
    public void copiedSettingsReplaceTheAccountsOwn() {
        AppPrefs prefs = getPrefs();
        MainUIData data = getData();
        int defaultId = data.getColorScheme().id;
        ColorScheme picked = getOtherScheme(data, defaultId);
        ColorScheme other = getOtherScheme(data, defaultId, picked.id);

        prefs.onAccountChanged(FIRST_ACCOUNT);
        data.setColorScheme(picked);
        ShadowLooper.shadowMainLooper().idle();
        prefs.onAccountChanged(SECOND_ACCOUNT);
        data.setColorScheme(other); // not saved yet

        prefs.copyProfileData(FIRST_ACCOUNT);
        ShadowLooper.shadowMainLooper().idle();

        assertEquals(picked.id, data.getColorScheme().id);

        prefs.onAccountChanged(FIRST_ACCOUNT);

        assertEquals(picked.id, data.getColorScheme().id);

        prefs.onAccountChanged(SECOND_ACCOUNT); // saved too

        assertEquals(picked.id, data.getColorScheme().id);
    }

    @Test
    public void copiedFromAccountWithoutItsOwnGivesTheDefaults() {
        AppPrefs prefs = getPrefs();
        MainUIData data = getData();
        int defaultId = data.getColorScheme().id;

        prefs.onAccountChanged(SECOND_ACCOUNT);
        data.setColorScheme(getOtherScheme(data, defaultId));
        ShadowLooper.shadowMainLooper().idle();

        prefs.copyProfileData(FIRST_ACCOUNT);

        assertEquals(defaultId, data.getColorScheme().id);
    }

    @Test
    public void copiedFromTheAccountTheSharedSettingsWereMadeWith() {
        AppPrefs prefs = getPrefs();
        MainUIData data = getData();
        ColorScheme picked = getOtherScheme(data, data.getColorScheme().id);

        prefs.setData(DATA_KEY, sharedSettings(picked));
        prefs.onAccountChanged(FIRST_ACCOUNT);
        prefs.initSharedSettingsProfile(); // the app starts with these settings

        // The first account never read its settings since: none of them copied to its own yet
        prefs.onAccountChanged(SECOND_ACCOUNT);
        prefs.copyProfileData(FIRST_ACCOUNT);

        assertEquals(picked.id, data.getColorScheme().id);
    }

    /**
     * Its settings are all the copied ones: none of the shared ones come back
     */
    @Test
    public void copiedIntoTheAccountTheSharedSettingsWereMadeWith() {
        AppPrefs prefs = getPrefs();
        MainUIData data = getData();
        int defaultId = data.getColorScheme().id;
        ColorScheme picked = getOtherScheme(data, defaultId);

        prefs.setData(DATA_KEY, sharedSettings(picked));
        prefs.onAccountChanged(SECOND_ACCOUNT);
        prefs.initSharedSettingsProfile(); // the app starts with these settings
        prefs.onAccountChanged(SECOND_ACCOUNT);

        assertEquals(picked.id, data.getColorScheme().id);

        prefs.copyProfileData(FIRST_ACCOUNT);

        assertEquals(defaultId, data.getColorScheme().id);
    }

    @Test
    public void shareQrLinkIsInTheMenuByDefault() {
        assertTrue(getData().isMenuItemEnabled(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_SHARE_QR_LINK));
    }

    /**
     * It was in the menu settings before it was on by default: one turned off stays off
     */
    @Test
    public void shareQrLinkTurnedOffStaysOff() {
        getData().setMenuItemDisabled(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_SHARE_QR_LINK);
        ShadowLooper.shadowMainLooper().idle();
        MainUIData.resetInstanceForTesting();

        assertFalse(getData().isMenuItemEnabled(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_SHARE_QR_LINK));
    }

    @Test
    public void markAsWatchedIsInTheMenuByDefault() {
        assertTrue(getData().isMenuItemEnabled(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_MARK_AS_WATCHED));
    }

    /**
     * It was in the menu settings before it was on by default: one turned off stays off
     */
    @Test
    public void markAsWatchedTurnedOffStaysOff() {
        getData().setMenuItemDisabled(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_MARK_AS_WATCHED);
        ShadowLooper.shadowMainLooper().idle();
        MainUIData.resetInstanceForTesting();

        assertFalse(getData().isMenuItemEnabled(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_MARK_AS_WATCHED));
    }

    @Test
    public void likeIsNotInTheMenuByDefault() {
        assertFalse(getData().isMenuItemEnabled(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_LIKE));
    }

    @Test
    public void likeComesAfterWatchLater() {
        MainUIData data = getData();

        assertEquals(data.getMenuItemIndex(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_ADD_TO_WATCH_LATER) + 1,
                data.getMenuItemIndex(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_LIKE));
    }

    @Test
    public void likeTurnedOnStaysOn() {
        getData().setMenuItemEnabled(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_LIKE);
        ShadowLooper.shadowMainLooper().idle();
        MainUIData.resetInstanceForTesting();

        assertTrue(getData().isMenuItemEnabled(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_LIKE));
    }

    /**
     * The enabled ones put in order: the others come after them, in their order
     */
    @Test
    public void menuItemsOrderPutsTheOthersAfterThem() {
        MainUIData data = getData();
        List<Long> rest = new ArrayList<>(data.getMenuItemsOrdered(MainUIData.MENU_VIDEOS));
        List<Long> order = Arrays.asList(MainUIData.MENU_ITEM_SHARE_QR_LINK, MainUIData.MENU_ITEM_PIN_TO_SIDEBAR);
        rest.removeAll(order);

        data.setMenuItemsOrder(MainUIData.MENU_VIDEOS, order);

        List<Long> result = data.getMenuItemsOrdered(MainUIData.MENU_VIDEOS);
        assertEquals(order, result.subList(0, order.size()));
        assertEquals(rest, result.subList(order.size(), result.size()));
    }

    @Test
    public void menuItemsOrderStaysAfterReload() {
        List<Long> order = Arrays.asList(MainUIData.MENU_ITEM_SHARE_QR_LINK, MainUIData.MENU_ITEM_PIN_TO_SIDEBAR);

        getData().setMenuItemsOrder(MainUIData.MENU_VIDEOS, order);
        ShadowLooper.shadowMainLooper().idle();
        MainUIData.resetInstanceForTesting();

        assertEquals(order, getData().getMenuItemsOrdered(MainUIData.MENU_VIDEOS).subList(0, order.size()));
    }

    @Test
    public void menuItemsOrderWithAnUnknownItemIsIgnored() {
        MainUIData data = getData();
        List<Long> before = new ArrayList<>(data.getMenuItemsOrdered(MainUIData.MENU_VIDEOS));

        data.setMenuItemsOrder(MainUIData.MENU_VIDEOS, Arrays.asList(MainUIData.MENU_ITEM_SHARE_QR_LINK, 0L)); // no item has it

        assertEquals(before, data.getMenuItemsOrdered(MainUIData.MENU_VIDEOS));
    }

    @Test
    public void menuItemsOrderWithAnItemTwiceIsIgnored() {
        MainUIData data = getData();
        List<Long> before = new ArrayList<>(data.getMenuItemsOrdered(MainUIData.MENU_VIDEOS));

        data.setMenuItemsOrder(MainUIData.MENU_VIDEOS, Arrays.asList(MainUIData.MENU_ITEM_SHARE_QR_LINK, MainUIData.MENU_ITEM_SHARE_QR_LINK));

        assertEquals(before, data.getMenuItemsOrdered(MainUIData.MENU_VIDEOS));
    }

    @Test
    public void menuItemsOrderWithAnItemOfAnotherMenuIsIgnored() {
        MainUIData data = getData();
        List<Long> before = new ArrayList<>(data.getMenuItemsOrdered(MainUIData.MENU_VIDEOS));

        data.setMenuItemsOrder(MainUIData.MENU_VIDEOS, Arrays.asList(MainUIData.MENU_ITEM_SHARE_QR_LINK, MainUIData.MENU_ITEM_MOVE_SECTION_UP));

        assertEquals(before, data.getMenuItemsOrdered(MainUIData.MENU_VIDEOS));
    }

    @Test
    public void eachMenuHasItsOwnItemsOn() {
        MainUIData data = getData();

        data.setMenuItemDisabled(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_SHARE_QR_LINK);

        assertFalse(data.isMenuItemEnabled(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_SHARE_QR_LINK));
        assertTrue(data.isMenuItemEnabled(MainUIData.MENU_SUBSCRIPTIONS, MainUIData.MENU_ITEM_SHARE_QR_LINK));
        assertTrue(data.isMenuItemEnabled(MainUIData.MENU_HISTORY, MainUIData.MENU_ITEM_SHARE_QR_LINK));
        assertTrue(data.isMenuItemEnabled(MainUIData.MENU_PLAYLISTS, MainUIData.MENU_ITEM_SHARE_QR_LINK));
    }

    @Test
    public void eachMenuHasItsOwnOrder() {
        MainUIData data = getData();
        List<Long> before = new ArrayList<>(data.getMenuItemsOrdered(MainUIData.MENU_HISTORY));
        List<Long> order = Arrays.asList(MainUIData.MENU_ITEM_SHARE_QR_LINK, MainUIData.MENU_ITEM_PIN_TO_SIDEBAR);

        data.setMenuItemsOrder(MainUIData.MENU_VIDEOS, order);

        assertEquals(order, data.getMenuItemsOrdered(MainUIData.MENU_VIDEOS).subList(0, order.size()));
        assertEquals(before, data.getMenuItemsOrdered(MainUIData.MENU_HISTORY));
    }

    /**
     * It's new there: not on until it's turned on
     */
    @Test
    public void notInterestedIsOffInSubscriptionsByDefault() {
        MainUIData data = getData();

        assertTrue(data.isMenuItemEnabled(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_NOT_INTERESTED));
        assertTrue(data.getMenuItemsOrdered(MainUIData.MENU_SUBSCRIPTIONS).contains(MainUIData.MENU_ITEM_NOT_INTERESTED));
        assertFalse(data.isMenuItemEnabled(MainUIData.MENU_SUBSCRIPTIONS, MainUIData.MENU_ITEM_NOT_INTERESTED));
    }

    @Test
    public void notInterestedTurnedOnInSubscriptionsStaysOn() {
        getData().setMenuItemEnabled(MainUIData.MENU_SUBSCRIPTIONS, MainUIData.MENU_ITEM_NOT_INTERESTED);
        ShadowLooper.shadowMainLooper().idle();
        MainUIData.resetInstanceForTesting();

        assertTrue(getData().isMenuItemEnabled(MainUIData.MENU_SUBSCRIPTIONS, MainUIData.MENU_ITEM_NOT_INTERESTED));
    }

    /**
     * Each menu lists only the items that can show up in it
     */
    @Test
    public void menusHaveOnlyTheirItems() {
        assertMenus(MainUIData.MENU_ITEM_NOT_INTERESTED, MainUIData.MENU_VIDEOS, MainUIData.MENU_SUBSCRIPTIONS);
        assertMenus(MainUIData.MENU_ITEM_NOT_RECOMMEND_CHANNEL, MainUIData.MENU_VIDEOS);
        assertMenus(MainUIData.MENU_ITEM_REMOVE_FROM_SUBSCRIPTIONS, MainUIData.MENU_SUBSCRIPTIONS);
        assertMenus(MainUIData.MENU_ITEM_REMOVE_FROM_HISTORY, MainUIData.MENU_HISTORY);
        assertMenus(MainUIData.MENU_ITEM_CLEAR_HISTORY, MainUIData.MENU_HISTORY, MainUIData.MENU_SIDEBAR);
        assertMenus(MainUIData.MENU_ITEM_RENAME_PLAYLIST, MainUIData.MENU_PLAYLISTS);
        assertMenus(MainUIData.MENU_ITEM_CREATE_PLAYLIST, MainUIData.MENU_PLAYLISTS, MainUIData.MENU_SIDEBAR);
        assertMenus(MainUIData.MENU_ITEM_MOVE_SECTION_UP, MainUIData.MENU_SIDEBAR);
        assertMenus(MainUIData.MENU_ITEM_UPDATE_CHECK, MainUIData.MENU_SIDEBAR);
        assertMenus(MainUIData.MENU_ITEM_PIN_TO_SIDEBAR, MainUIData.MENU_VIDEOS, MainUIData.MENU_SUBSCRIPTIONS,
                MainUIData.MENU_HISTORY, MainUIData.MENU_PLAYLISTS, MainUIData.MENU_SIDEBAR);
        assertMenus(MainUIData.MENU_ITEM_SHARE_LINK, MainUIData.MENU_VIDEOS, MainUIData.MENU_SUBSCRIPTIONS,
                MainUIData.MENU_HISTORY, MainUIData.MENU_PLAYLISTS);
        assertMenus(MainUIData.MENU_ITEM_EXIT_FROM_PIP); // always there, not in the settings
    }

    @Test
    public void providersAreInTheMenusOfTheirType() {
        for (ContextMenuProvider provider : new ContextMenuManager(RuntimeEnvironment.getApplication()).getProviders()) {
            if (provider.getMenuType() == ContextMenuProvider.MENU_TYPE_SECTION) {
                assertMenus(provider.getId(), MainUIData.MENU_SIDEBAR);
            } else {
                assertMenus(provider.getId(), MainUIData.MENU_VIDEOS, MainUIData.MENU_SUBSCRIPTIONS, MainUIData.MENU_HISTORY,
                        MainUIData.MENU_PLAYLISTS);
            }
        }
    }

    @Test
    public void anItemOfAnotherMenuIsNotTurnedOn() {
        MainUIData data = getData();

        data.setMenuItemEnabled(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_REMOVE_FROM_SUBSCRIPTIONS);

        assertFalse(data.isMenuItemEnabled(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_REMOVE_FROM_SUBSCRIPTIONS));
    }

    /**
     * In the order it had before it followed one
     */
    @Test
    public void sidebarMenuStartsInItsOldOrder() {
        List<Long> order = getData().getMenuItemsOrdered(MainUIData.MENU_SIDEBAR);

        assertEquals(Arrays.asList(MainUIData.MENU_ITEM_PIN_TO_SIDEBAR, MainUIData.MENU_ITEM_ADD_TO_HOME_SCREEN,
                MainUIData.MENU_ITEM_ADD_TO_HOME_ROWS, MainUIData.MENU_ITEM_SELECT_ACCOUNT, MainUIData.MENU_ITEM_MOVE_SECTION_UP,
                MainUIData.MENU_ITEM_MOVE_SECTION_DOWN, MainUIData.MENU_ITEM_RENAME_SECTION, MainUIData.MENU_ITEM_CREATE_PLAYLIST,
                MainUIData.MENU_ITEM_CREATE_LOCAL_PLAYLIST, MainUIData.MENU_ITEM_TOGGLE_HISTORY, MainUIData.MENU_ITEM_CLEAR_HISTORY,
                MainUIData.MENU_ITEM_UPDATE_CHECK), order.subList(0, 12));
    }

    /**
     * Saved before each menu had its own: the items on and the order of all of them, as far as the menu has them.
     * The sidebar one keeps its fixed order. Not interested is new to Subscriptions: off there.
     */
    @Test
    public void menusStartFromTheSettingsOfAllOfThem() {
        long menuItems = (MainUIData.MENU_ITEM_DEFAULT | MainUIData.MENU_ITEM_LIKE) & ~MainUIData.MENU_ITEM_SHARE_QR_LINK
                & ~MainUIData.MENU_ITEM_MOVE_SECTION_UP;
        // All the items, or the missing ones would be added with their default
        List<Long> order = new ArrayList<>();
        order.add(MainUIData.MENU_ITEM_SHARE_LINK);
        order.add(MainUIData.MENU_ITEM_EXIT_FROM_PIP);
        for (int menu = 0; menu < MainUIData.MENU_COUNT; menu++) {
            for (Long menuItem : getData().getMenuItemsOrdered(menu)) {
                if (!order.contains(menuItem)) {
                    order.add(menuItem);
                }
            }
        }
        getPrefs().setProfileData(DATA_KEY, oldMenuSettings(menuItems, order));
        MainUIData.resetInstanceForTesting();

        MainUIData data = getData();

        for (int menu : new int[] {MainUIData.MENU_VIDEOS, MainUIData.MENU_SUBSCRIPTIONS, MainUIData.MENU_HISTORY, MainUIData.MENU_PLAYLISTS}) {
            assertTrue("menu " + menu, data.isMenuItemEnabled(menu, MainUIData.MENU_ITEM_LIKE));
            assertFalse("menu " + menu, data.isMenuItemEnabled(menu, MainUIData.MENU_ITEM_SHARE_QR_LINK));
            assertEquals("menu " + menu, 0, data.getMenuItemIndex(menu, MainUIData.MENU_ITEM_SHARE_LINK));
            assertEquals("menu " + menu, -1, data.getMenuItemIndex(menu, MainUIData.MENU_ITEM_UPDATE_CHECK));
        }

        assertTrue(data.isMenuItemEnabled(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_NOT_INTERESTED));
        assertFalse(data.isMenuItemEnabled(MainUIData.MENU_SUBSCRIPTIONS, MainUIData.MENU_ITEM_NOT_INTERESTED));
        assertFalse(data.isMenuItemEnabled(MainUIData.MENU_SIDEBAR, MainUIData.MENU_ITEM_MOVE_SECTION_UP));
        assertTrue(data.isMenuItemEnabled(MainUIData.MENU_SIDEBAR, MainUIData.MENU_ITEM_MOVE_SECTION_DOWN));
        assertEquals(0, data.getMenuItemIndex(MainUIData.MENU_SIDEBAR, MainUIData.MENU_ITEM_PIN_TO_SIDEBAR));
        assertEquals(-1, data.getMenuItemIndex(MainUIData.MENU_SIDEBAR, MainUIData.MENU_ITEM_SHARE_LINK));
    }

    /**
     * Child mode: all off, then its own on in each menu that has them, and back to the defaults
     */
    @Test
    public void allMenusAtOnce() {
        MainUIData data = getData();

        data.setMenuItemsDisabled(Integer.MAX_VALUE);
        data.setMenuItemsEnabled(MainUIData.MENU_ITEM_PLAY_NEXT);

        assertTrue(data.isMenuItemEnabled(MainUIData.MENU_HISTORY, MainUIData.MENU_ITEM_PLAY_NEXT));
        assertFalse(data.isMenuItemEnabled(MainUIData.MENU_HISTORY, MainUIData.MENU_ITEM_REMOVE_FROM_HISTORY));
        assertFalse(data.isMenuItemEnabled(MainUIData.MENU_SIDEBAR, MainUIData.MENU_ITEM_PLAY_NEXT));

        data.setDefaultMenuItemsEnabled();

        assertTrue(data.isMenuItemEnabled(MainUIData.MENU_HISTORY, MainUIData.MENU_ITEM_REMOVE_FROM_HISTORY));
        assertTrue(data.isMenuItemEnabled(MainUIData.MENU_VIDEOS, MainUIData.MENU_ITEM_NOT_INTERESTED));
        assertFalse(data.isMenuItemEnabled(MainUIData.MENU_SUBSCRIPTIONS, MainUIData.MENU_ITEM_NOT_INTERESTED));
    }

    private static void assertMenus(long menuItem, int... menus) {
        for (int menu = 0; menu < MainUIData.MENU_COUNT; menu++) {
            assertEquals("menu " + menu + " item " + menuItem, contains(menus, menu),
                    getData().getMenuItemsOrdered(menu).contains(menuItem));
        }
    }

    /**
     * Saved like MainUIData did before each menu had its own items and order
     */
    private static String oldMenuSettings(long menuItems, List<Long> menuItemsOrdered) {
        Object[] data = new Object[18];
        data[12] = menuItems;
        data[17] = Helpers.mergeList(menuItemsOrdered);
        return Helpers.mergeData(data);
    }

    /**
     * Saved like MainUIData does, the values after the color scheme left out: the defaults
     */
    private static String sharedSettings(ColorScheme scheme) {
        return Helpers.mergeData(null, 1.0f, 1.0f, scheme.id);
    }

    private static ColorScheme getOtherScheme(MainUIData data, int... ids) {
        for (ColorScheme scheme : data.getColorSchemes()) {
            if (!contains(ids, scheme.id)) {
                return scheme;
            }
        }

        throw new AssertionError("No other color scheme");
    }

    private static boolean contains(int[] ids, int id) {
        for (int item : ids) {
            if (item == id) {
                return true;
            }
        }

        return false;
    }

    private static void clearSaved() {
        TestAccounts.clearSaved(getPrefs(), DATA_KEY);
        MainUIData.resetInstanceForTesting();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(RuntimeEnvironment.getApplication());
    }

    private static MainUIData getData() {
        return MainUIData.instance(RuntimeEnvironment.getApplication());
    }
}
