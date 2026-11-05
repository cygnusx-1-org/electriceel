package com.liskovsoft.smartyoutubetv2.common.storage;

import com.liskovsoft.smartyoutubetv2.common.prefs.SyncedSettings;
import com.liskovsoft.smartyoutubetv2.common.prefs.SyncedSettings.Field;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SyncValuesTest {
    private static Field field(String key) {
        Field field = SyncedSettings.get(key);

        if (field == null) {
            throw new AssertionError("not listed: " + key);
        }

        return field;
    }

    @Test
    public void readsEachTypeFromItsIndex() {
        // ui.cardTextScrollSpeed is index 11, a float
        String data = "0%OB%5%OB%null%OB%1%OB%true%OB%false%OB%true%OB%0%OB%null%OB%null%OB%null%OB%1.50";
        assertEquals("1.5", SyncValues.read(field("ui.cardTextScrollSpeed"), data));
        assertEquals("true", SyncValues.read(field("player.seekConfirmPause"), data));
        assertEquals("1", SyncValues.read(field("player.seekPreviewMode"), data));
        // Beyond the saved values: none, the default applies
        assertNull(SyncValues.read(field("player.pitch"), data));
        assertNull(SyncValues.read(field("ui.cardTextScrollSpeed"), null));
    }

    @Test
    public void refusesValuesThatDontFit() {
        Field bool = field("player.seekConfirmPause");
        Field integer = field("player.seekPreviewMode");
        Field string = field("player.audioLanguage");
        assertNull(SyncValues.decode(bool, "1"));
        assertNull(SyncValues.decode(integer, "1.5"));
        assertNull(SyncValues.decode(integer, "true"));
        assertNull(SyncValues.decode(integer, String.valueOf(1L << 40)));
        assertEquals(String.valueOf(1L << 40), SyncValues.decode(field("ui.menuItems"), String.valueOf(1L << 40)));
        // The data's delimiter would split the data in two
        assertNull(SyncValues.decode(string, "\"de%OB%x\""));
        assertNull(SyncValues.decode(string, "{"));
        assertEquals("null", SyncValues.decode(string, "null"));
    }

    @Test
    public void writesOneValueAndKeepsTheOthers() {
        Field pitch = field("player.pitch"); // index 58
        String written = SyncValues.write(pitch, "1%OB%2", "1.25");
        String[] parts = written.split("%OB%", -1);
        assertEquals(59, parts.length);
        assertEquals("1", parts[0]);
        assertEquals("2", parts[1]);
        assertEquals("null", parts[2]);
        assertEquals("1.25", parts[58]);
        assertEquals("1.25", SyncValues.read(pitch, written));
        assertNull(SyncValues.write(pitch, "1%OB%2", "\"fast\""));
    }

    @Test
    public void oneFormPerValue() {
        Field speed = field("player.subtitleScale");
        assertEquals("1.5", SyncValues.canonical(speed, "1.50"));
        assertEquals("2", SyncValues.canonical(speed, "2.0"));
        assertEquals("0.1", SyncValues.canonical(speed, "0.1"));
        // Escaped differently by another JSON encoder, the same string
        Field language = field("player.audioLanguage");
        assertEquals(SyncValues.canonical(language, "\"a/b\""), SyncValues.canonical(language, "\"a\\/b\""));
    }

    @Test
    public void wholeDataKeys() {
        Field deArrow = field("filters.deArrow");
        String data = "true%OB%false%OB%true";
        String json = SyncValues.read(deArrow, data);
        assertEquals(data, SyncValues.decode(deArrow, json));
        assertEquals(data, SyncValues.write(deArrow, "anything", json));
        assertNull(SyncValues.read(deArrow, " "));
    }
}
