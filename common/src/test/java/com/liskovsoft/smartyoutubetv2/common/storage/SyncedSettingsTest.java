package com.liskovsoft.smartyoutubetv2.common.storage;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.prefs.RemoteControlData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SponsorBlockData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SyncedSettings;
import com.liskovsoft.smartyoutubetv2.common.prefs.SyncedSettings.Field;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The registry's indices are the data classes' own: a setting set through its class is read back under its key, and a value
 * written under its key is the class's value once it reads the data again
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SyncedSettingsTest {
    private Context mContext;
    private AppPrefs mPrefs;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mPrefs = AppPrefs.instance(mContext);
        mPrefs.onAccountChanged(null);
    }

    @After
    public void tearDown() {
        Utils.sHandler.removeCallbacksAndMessages(null);
    }

    private String data(Field field) {
        String key = field.isAccount ? mPrefs.getAccountDataKey(null, field.dataKey) : field.dataKey;
        return FileHelpers.getFileContents(mPrefs.getDataFile(key));
    }

    private String read(String key) {
        Field field = SyncedSettings.get(key);
        assertNotNull(key, field);
        return SyncValues.read(field, data(field));
    }

    @Test
    public void everyTypedSettingIsAValueOfItsType() {
        // Saved: only a save that's waiting is done by persistPendingData
        GeneralData.instance(mContext).persistNow();
        PlayerData.instance(mContext).persistNow();
        PlayerTweaksData.instance(mContext).persistNow();
        MainUIData.instance(mContext).persistNow();
        SponsorBlockData sponsorBlock = SponsorBlockData.instance(mContext);
        sponsorBlock.setSponsorBlockEnabled(sponsorBlock.isSponsorBlockEnabled());
        SearchData search = SearchData.instance(mContext);
        search.setSearchOptions(search.getSearchOptions());
        SidebarService.instance(mContext).persistState();
        RemoteControlData remote = RemoteControlData.instance(mContext);
        remote.setConnectedBefore(remote.isConnectedBefore());
        mPrefs.persistPendingData();

        Set<String> keys = new HashSet<>();

        for (Field field : SyncedSettings.all()) {
            assertTrue("listed twice: " + field.dataKey + " " + field.index, field.isWhole() || keys.add(field.dataKey + "#" + field.index));

            if (field.isWhole() || field.type == SyncedSettings.Type.RAW || field.type == SyncedSettings.Type.STRING) {
                continue;
            }

            // A primitive is always saved: a wrong index reads another type, or a removed value ("null")
            assertNotNull(field.key + " (" + field.dataKey + " " + field.index + ") isn't a " + field.type.name,
                    SyncValues.read(field, data(field)));
        }
    }

    @Test
    public void readsWhatTheClassesSave() {
        GeneralData general = GeneralData.instance(mContext);
        general.setScreensaverTimeoutMs(123_000);
        PlayerData player = PlayerData.instance(mContext);
        player.setSubtitleScale(0.8f);
        player.setAudioLanguage("de");
        PlayerTweaksData tweaks = PlayerTweaksData.instance(mContext);
        boolean loop = !tweaks.isLoopShortsEnabled();
        tweaks.setLoopShortsEnabled(loop);
        SponsorBlockData.instance(mContext).setIgnoredDurationMs(1234);
        SearchData.instance(mContext).setSearchOptions(5);
        // Saved without the main thread running: persistPendingData saves what's waiting
        mPrefs.persistPendingData();

        assertEquals("123000", read("general.screensaverTimeoutMs"));
        assertEquals("0.8", read("player.subtitleScale"));
        assertEquals("\"de\"", read("player.audioLanguage"));
        assertEquals(String.valueOf(loop), read("tweaks.loopShorts"));
        assertEquals("1234", read("sponsorBlock.ignoredDurationMs"));
        assertEquals("5", read("search.options"));
    }

    @Test
    public void everyChoiceFitsItsSetting() {
        int withChoices = 0;

        for (Field field : SyncedSettings.all()) {
            Set<Long> values = new HashSet<>();

            for (SyncedSettings.Option option : field.options) {
                assertTrue(field.key + " lists " + option.value + " twice", values.add(option.value));
                assertNotNull(field.key + ": " + option.value + " isn't a " + field.type.name,
                        SyncValues.canonical(field, String.valueOf(option.value)));
                assertFalse(field.key + ": no label for " + option.value, option.label.trim().isEmpty());
            }

            withChoices += field.options.isEmpty() ? 0 : 1;
        }

        assertTrue("choices of " + withChoices + " settings", withChoices >= 19);
    }

    @Test
    public void childModeAndPasswordsArentSynced() {
        GeneralData general = GeneralData.instance(mContext);
        general.setChildModeEnabled(true);
        general.setSettingsPassword("1234");
        mPrefs.persistPendingData();
        Field any = SyncedSettings.get("general.screensaverTimeoutMs");

        assertTrue(SyncedSettings.isChildMode(data(any)));

        for (Field field : SyncedSettings.all()) {
            if (field.dataKey != null && field.dataKey.equals(any.dataKey)) {
                assertFalse(field.key, field.index == 33 || field.index == 34 || field.index == 41);
            }
        }

        general.setChildModeEnabled(false);
        general.setSettingsPassword(null);
        mPrefs.persistPendingData();
        assertFalse(SyncedSettings.isChildMode(data(any)));
    }

    @Test
    public void aWrittenValueIsReadAgainByItsClass() {
        PlayerData player = PlayerData.instance(mContext);
        player.setSubtitleScale(1.0f);
        mPrefs.persistPendingData();
        Field scale = SyncedSettings.get("player.subtitleScale");
        String key = mPrefs.getAccountDataKey(null, scale.dataKey);

        mPrefs.setData(key, SyncValues.write(scale, data(scale), "2"));
        mPrefs.onProfileDataSynced(Collections.singleton(scale.dataKey));
        ShadowLooper.idleMainLooper();

        assertEquals(2.0f, player.getSubtitleScale(), 0.0001f);

        // Another data key changed: this class keeps its values
        player.setSubtitleScale(1.25f);
        mPrefs.onProfileDataSynced(Collections.singleton("general_data"));
        assertEquals(1.25f, player.getSubtitleScale(), 0.0001f);
        // The formats depend on the device: not synced
        assertNull(SyncedSettings.get("player.videoFormat"));
    }
}
