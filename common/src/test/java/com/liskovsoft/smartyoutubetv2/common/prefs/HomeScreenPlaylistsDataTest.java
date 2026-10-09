package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;

import com.liskovsoft.leanbackassistant.media.AccountChannels;
import com.liskovsoft.leanbackassistant.media.ClipService;
import com.liskovsoft.leanbackassistant.media.Playlist;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HomeScreenPlaylistsDataTest {
    private static final String DATA_KEY = "home_screen_playlists_data"; // see AppPrefs
    private static final String CHANNELS_KEY = "home_screen_channels_data"; // see AppPrefs

    @Before
    public void setUp() {
        getPrefs().onAccountChanged(null); // the anonymous account
        TestAccounts.clearSaved(getPrefs(), DATA_KEY);
        TestAccounts.clearSaved(getPrefs(), CHANNELS_KEY);
        HomeScreenPlaylistsData.resetInstanceForTesting();
        getData().setAccountsSourceForTesting(accounts(null)); // signed out
    }

    @After
    public void tearDown() {
        Utils.sHandler.removeCallbacksAndMessages(null);
        ClipService.setChannelsSource(null);
        getPrefs().onAccountChanged(null); // the anonymous account
        TestAccounts.clearSaved(getPrefs(), DATA_KEY);
        TestAccounts.clearSaved(getPrefs(), CHANNELS_KEY);
        HomeScreenPlaylistsData.resetInstanceForTesting();
    }

    @Test
    public void noneByDefault() {
        assertTrue(getData().getPlaylists().isEmpty());
        assertFalse(getData().contains("PLfirst"));
        assertFalse(getData().contains(null));
    }

    @Test
    public void keptInTheOrderAdded() {
        HomeScreenPlaylistsData data = getData();

        data.add("PLsecond", "Second");
        data.add("PLfirst", "First");
        data.add("local", "Local");

        assertEquals(Arrays.asList("PLsecond", "PLfirst", "local"), new ArrayList<>(data.getPlaylists().keySet()));
        assertEquals("First", data.getPlaylists().get("PLfirst"));
        assertTrue(data.contains("local"));
    }

    /**
     * The channel is the same one, under the new title
     */
    @Test
    public void addedAgainKeepsItsPlace() {
        HomeScreenPlaylistsData data = getData();

        data.add("PLfirst", "First");
        data.add("PLsecond", "Second");
        data.add("PLfirst", "Renamed");

        Map<String, String> playlists = data.getPlaylists();

        assertEquals(Arrays.asList("PLfirst", "PLsecond"), new ArrayList<>(playlists.keySet()));
        assertEquals("Renamed", playlists.get("PLfirst"));
    }

    @Test
    public void removedOneByOne() {
        HomeScreenPlaylistsData data = getData();

        data.add("PLfirst", "First");
        data.add("PLsecond", "Second");
        data.remove("PLfirst");
        data.remove("PLmissing");

        assertEquals(Arrays.asList("PLsecond"), new ArrayList<>(data.getPlaylists().keySet()));

        data.remove("PLsecond");

        assertTrue(data.getPlaylists().isEmpty());
    }

    /**
     * A playlist renamed in the app isn't added by it
     */
    @Test
    public void renamedOnlyWhenAdded() {
        HomeScreenPlaylistsData data = getData();

        data.add("PLfirst", "First");

        assertTrue(data.rename("PLfirst", "Renamed"));
        assertFalse(data.rename("PLsecond", "Second"));
        assertEquals("Renamed", data.getPlaylists().get("PLfirst"));
        assertFalse(data.contains("PLsecond"));
    }

    /**
     * Titles are whatever the user typed: the delimiters of the older saved data among them
     */
    @Test
    public void titlesKeptAsTheyAre() {
        HomeScreenPlaylistsData data = getData();

        data.add("PLfirst", "Music, live | 2026");
        data.add("PLsecond", null);

        assertEquals("Music, live | 2026", data.getPlaylists().get("PLfirst"));
        assertEquals("PLsecond", data.getPlaylists().get("PLsecond"));
    }

    @Test
    public void eachAccountHasItsOwn() {
        HomeScreenPlaylistsData data = getData();

        getPrefs().onAccountChanged(TestAccounts.FIRST);
        data.add("PLfirst", "First");
        getPrefs().onAccountChanged(TestAccounts.SECOND);

        assertTrue(data.getPlaylists().isEmpty());

        data.add("PLsecond", "Second");
        getPrefs().onAccountChanged(TestAccounts.FIRST);

        assertEquals(Arrays.asList("PLfirst"), new ArrayList<>(data.getPlaylists().keySet()));

        getPrefs().onAccountChanged(null);

        assertTrue(data.getPlaylists().isEmpty());
    }

    /**
     * The launcher shows every account's channels, each named after its account, and the ones chosen signed out
     */
    @Test
    public void channelsOfEveryAccount() {
        HomeScreenPlaylistsData.registerChannelsSource(getContext());
        HomeScreenPlaylistsData data = getData();
        data.setAccountsSourceForTesting(accounts(TestAccounts.SECOND, TestAccounts.FIRST, TestAccounts.SECOND));

        data.setChannelEnabled(ClipService.RECOMMENDED_PROVIDER_ID, true); // signed out
        getPrefs().onAccountChanged(TestAccounts.FIRST);
        data.setChannelEnabled(ClipService.HISTORY_PROVIDER_ID, true);
        data.add("PLfirst", "Climbing");
        getPrefs().onAccountChanged(TestAccounts.SECOND);
        data.setChannelEnabled(ClipService.SUBSCRIPTIONS_PROVIDER_ID, true);
        data.add("PLsecond", "Bouldering");
        data.add("local", "Local");

        List<AccountChannels> accounts = getClipService().getAccountChannels();

        assertEquals(3, accounts.size());
        assertChannels(accounts.get(0), "First/2", "First - History", "First/playlist_PLfirst", "First - Climbing");
        assertChannels(accounts.get(1), "Second/1", "Second - Subscriptions", "Second/playlist_PLsecond", "Second - Bouldering",
                "Second/playlist_local", "Second - Local");
        // Signed out: there's no account to name
        assertChannels(accounts.get(2), "anonymous/3", "Recommended");
    }

    /**
     * Read the same whichever account is selected
     */
    @Test
    public void channelsOfEveryAccountWhicheverIsSelected() {
        HomeScreenPlaylistsData.registerChannelsSource(getContext());
        HomeScreenPlaylistsData data = getData();

        getPrefs().onAccountChanged(TestAccounts.FIRST);
        data.add("PLfirst", "Climbing");
        getPrefs().onAccountChanged(TestAccounts.SECOND);
        data.add("PLsecond", "Bouldering");

        getPrefs().onAccountChanged(TestAccounts.FIRST);
        data.setAccountsSourceForTesting(accounts(TestAccounts.FIRST, TestAccounts.FIRST, TestAccounts.SECOND));
        List<AccountChannels> whenFirst = getClipService().getAccountChannels();
        getPrefs().onAccountChanged(null);
        data.setAccountsSourceForTesting(accounts(null, TestAccounts.FIRST, TestAccounts.SECOND));
        List<AccountChannels> signedOut = getClipService().getAccountChannels();

        for (List<AccountChannels> accounts : Arrays.asList(whenFirst, signedOut)) {
            assertChannels(accounts.get(0), "First/playlist_PLfirst", "First - Climbing");
            assertChannels(accounts.get(1), "Second/playlist_PLsecond", "Second - Bouldering");
            assertChannels(accounts.get(2));
        }
    }

    /**
     * Two accounts with the same name: numbered the same way in the launcher and in the settings, each with its own channels
     */
    @Test
    public void sameNameAccountsNumbered() {
        Account first = TestAccounts.withProfile("Nathan Grennan", "first@example.com", "Nathan_Grennan", null);
        Account second = TestAccounts.withProfile("Nathan Grennan", "second@example.com", "Nathan_Grennan#2", "Nathan_Grennan");
        HomeScreenPlaylistsData.registerChannelsSource(getContext());
        HomeScreenPlaylistsData data = getData();

        getPrefs().onAccountChanged(first);
        data.setChannelEnabled(ClipService.HISTORY_PROVIDER_ID, true);
        getPrefs().onAccountChanged(second);
        data.setChannelEnabled(ClipService.HISTORY_PROVIDER_ID, true);
        data.add("PLbouldering", "Bouldering");
        data.setAccountsSourceForTesting(accounts(second, second, TestAccounts.FIRST, first));

        assertEquals("Nathan Grennan 2", data.getAccountName());

        List<AccountChannels> accounts = getClipService().getAccountChannels();

        assertChannels(accounts.get(0), "Nathan_Grennan#2/2", "Nathan Grennan 2 - History",
                "Nathan_Grennan#2/playlist_PLbouldering", "Nathan Grennan 2 - Bouldering");
        assertChannels(accounts.get(1));
        assertChannels(accounts.get(2), "Nathan_Grennan/2", "Nathan Grennan 1 - History");

        clearSaved("Nathan_Grennan");
        clearSaved("Nathan_Grennan#2");
    }

    /**
     * In the order of their profiles, whatever the order of the accounts. Numbered only while another account has the name.
     */
    @Test
    public void accountsNumberedOnlyWhenTheNameIsShared() {
        Account first = TestAccounts.withProfile("Nathan Grennan", null, "Nathan_Grennan", null);
        Account second = TestAccounts.withProfile("Nathan Grennan", null, "Nathan_Grennan#2", null);
        Account tenth = TestAccounts.withProfile("Nathan Grennan", null, "Nathan_Grennan#10", null);
        List<Account> accounts = Arrays.asList(tenth, TestAccounts.FIRST, second, first);

        assertEquals("Nathan Grennan 1", HomeScreenPlaylistsData.getAccountName(first, accounts));
        assertEquals("Nathan Grennan 2", HomeScreenPlaylistsData.getAccountName(second, accounts));
        assertEquals("Nathan Grennan 3", HomeScreenPlaylistsData.getAccountName(tenth, accounts));
        assertEquals("First", HomeScreenPlaylistsData.getAccountName(TestAccounts.FIRST, accounts));

        assertEquals("Nathan Grennan 1", HomeScreenPlaylistsData.getAccountName(second, Arrays.asList(tenth, second)));
        assertEquals("Nathan Grennan", HomeScreenPlaylistsData.getAccountName(second, Arrays.asList(second, TestAccounts.FIRST)));
    }

    /**
     * Named by the email when they have no name: numbered the same way
     */
    @Test
    public void accountsWithoutNameNumberedByEmail() {
        Account first = TestAccounts.withProfile(null, "someone@example.com", "anonymous", null);
        Account second = TestAccounts.withProfile(null, "someone@example.com", "anonymous#2", null);

        assertEquals("someone@example.com 2", HomeScreenPlaylistsData.getAccountName(second, Arrays.asList(second, first)));
        assertEquals("someone@example.com", HomeScreenPlaylistsData.getAccountName(first, Arrays.asList(first)));
    }

    /**
     * None is shown until the account chooses it
     */
    @Test
    public void appChannelsOffByDefault() {
        HomeScreenPlaylistsData data = getData();

        assertFalse(data.isChannelEnabled(ClipService.HISTORY_PROVIDER_ID));
        assertFalse(data.isChannelEnabled(ClipService.RECOMMENDED_PROVIDER_ID));
        assertFalse(data.isChannelEnabled(ClipService.SUBSCRIPTIONS_PROVIDER_ID));
        assertFalse(data.isChannelEnabled(ClipService.MY_VIDEOS_PROVIDER_ID));
        assertFalse(data.isChannelEnabled(ClipService.MY_SHORTS_PROVIDER_ID));
    }

    @Test
    public void myVideosChosenPerAccount() {
        HomeScreenPlaylistsData data = getData();

        getPrefs().onAccountChanged(TestAccounts.FIRST);
        data.setChannelEnabled(ClipService.MY_VIDEOS_PROVIDER_ID, true);
        data.setChannelEnabled(ClipService.MY_SHORTS_PROVIDER_ID, true);
        data.setChannelEnabled(ClipService.MY_SHORTS_PROVIDER_ID, false);

        assertTrue(data.isChannelEnabled(ClipService.MY_VIDEOS_PROVIDER_ID));
        assertFalse(data.isChannelEnabled(ClipService.MY_SHORTS_PROVIDER_ID));

        getPrefs().onAccountChanged(TestAccounts.SECOND);

        assertFalse(data.isChannelEnabled(ClipService.MY_VIDEOS_PROVIDER_ID));

        getPrefs().onAccountChanged(TestAccounts.FIRST);

        assertTrue(data.isChannelEnabled(ClipService.MY_VIDEOS_PROVIDER_ID));
        assertFalse(data.isChannelEnabled(ClipService.HISTORY_PROVIDER_ID));
    }

    @Test
    public void appChannelsChosenPerAccount() {
        HomeScreenPlaylistsData data = getData();

        getPrefs().onAccountChanged(TestAccounts.FIRST);
        data.setChannelEnabled(ClipService.HISTORY_PROVIDER_ID, true);
        data.setChannelEnabled(ClipService.SUBSCRIPTIONS_PROVIDER_ID, true);
        data.setChannelEnabled(ClipService.SUBSCRIPTIONS_PROVIDER_ID, false);

        assertTrue(data.isChannelEnabled(ClipService.HISTORY_PROVIDER_ID));
        assertFalse(data.isChannelEnabled(ClipService.RECOMMENDED_PROVIDER_ID));
        assertFalse(data.isChannelEnabled(ClipService.SUBSCRIPTIONS_PROVIDER_ID));

        getPrefs().onAccountChanged(TestAccounts.SECOND);

        assertFalse(data.isChannelEnabled(ClipService.HISTORY_PROVIDER_ID));

        getPrefs().onAccountChanged(TestAccounts.FIRST);

        assertTrue(data.isChannelEnabled(ClipService.HISTORY_PROVIDER_ID));
    }

    @Test
    public void accountNamedByNameElseEmail() {
        assertEquals("Second", HomeScreenPlaylistsData.getAccountName(TestAccounts.SECOND));
        assertEquals("someone@example.com", HomeScreenPlaylistsData.getAccountName(
                TestAccounts.withProfile(null, "someone@example.com", "anonymous", null)));
        assertNull(HomeScreenPlaylistsData.getAccountName(null));
    }

    /**
     * Nothing is known about them: their channels are kept as they are
     */
    @Test
    public void noChannelsUntilTheAppSaysWhereTheyAre() {
        getData().add("PLfirst", "First");

        assertNull(getClipService().getAccountChannels());
    }

    /**
     * Each provider id with its title, in the order of the channels
     */
    private static void assertChannels(AccountChannels account, String... idsAndTitles) {
        List<String> actual = new ArrayList<>();

        for (Playlist playlist : getClipService().getChannels(account)) {
            actual.add(playlist.getPlaylistId());
            actual.add(playlist.getName());
        }

        assertEquals(Arrays.asList(idsAndTitles), actual);
    }

    /**
     * @param selected null: signed out
     */
    private static HomeScreenPlaylistsData.AccountsSource accounts(Account selected, Account... accounts) {
        return new HomeScreenPlaylistsData.AccountsSource() {
            @Override
            public List<Account> getAccounts() {
                return Arrays.asList(accounts);
            }

            @Override
            public Account getSelectedAccount() {
                return selected;
            }
        };
    }

    private static void clearSaved(String profileName) {
        getPrefs().setData(profileName + "_" + DATA_KEY, "");
        getPrefs().setData(profileName + "_" + CHANNELS_KEY, "");
    }

    private static ClipService getClipService() {
        return ClipService.instance(getContext());
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }

    private static AppPrefs getPrefs() {
        return AppPrefs.instance(getContext());
    }

    private static HomeScreenPlaylistsData getData() {
        return HomeScreenPlaylistsData.instance(getContext());
    }
}
