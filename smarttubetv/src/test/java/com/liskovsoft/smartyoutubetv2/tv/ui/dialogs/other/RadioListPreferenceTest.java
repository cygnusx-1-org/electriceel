package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.app.Application;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The modern skin shows a radio list's value as its summary, and ListPreference formats summaries
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
// The app's Conscrypt has no native library here, and no TLS is used
@ConscryptMode(ConscryptMode.Mode.OFF)
public class RadioListPreferenceTest {
    /**
     * Settings → Player → Audio: the volume's "100%" threw UnknownFormatConversionException while the row was bound,
     * so the Volume row and the Audio checkboxes after it were missing
     */
    @Test
    public void summaryWithPercentIsShownAsSet() {
        RadioListPreference pref = createVolumeList();

        pref.setSummary("100%");

        assertEquals("100%", pref.getSummary().toString());
    }

    @Test
    public void summaryWithFormatSpecifiersIsShownAsSet() {
        RadioListPreference pref = createVolumeList();

        pref.setSummary("%s %d 50%");

        assertEquals("%s %d 50%", pref.getSummary().toString());
    }

    @Test
    public void clearedSummaryIsNull() {
        RadioListPreference pref = createVolumeList();

        pref.setSummary("100%");
        pref.setSummary(null);

        assertNull(pref.getSummary());
    }

    private static RadioListPreference createVolumeList() {
        RadioListPreference pref = new RadioListPreference(getContext());
        pref.setEntries(new CharSequence[] {"95%", "100%", "105%"});
        pref.setEntryValues(new CharSequence[] {"a", "b", "c"});
        pref.setValue("b");
        return pref;
    }

    private static Context getContext() {
        return RuntimeEnvironment.getApplication();
    }
}
