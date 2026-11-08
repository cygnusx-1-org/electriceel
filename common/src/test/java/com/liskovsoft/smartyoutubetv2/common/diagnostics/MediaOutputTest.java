package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.AudioDeviceInfoBuilder;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

/**
 * Where the sound of a video goes: the media route can't tell HDMI on a TV box ("unknown"), or whether a Bluetooth speaker took
 * it (9lKpEOEbr0U's replays happened only with one, its reporter said)
 */
@RunWith(RobolectricTestRunner.class)
public class MediaOutputTest {
    @Test
    @Config(sdk = 30)
    public void aConnectedBluetoothOutputTakesIt() {
        AudioManager audioManager = getAudioManager();
        shadowOf(audioManager).setOutputDevices(Arrays.asList(output(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
                output(AudioDeviceInfo.TYPE_HDMI), output(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)));

        assertEquals("bluetooth_a2dp", DeviceCapabilities.getMediaOutput(audioManager));
    }

    @Test
    @Config(sdk = 30)
    public void withoutOneHdmiTakesIt() {
        AudioManager audioManager = getAudioManager();
        shadowOf(audioManager).setOutputDevices(Arrays.asList(output(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
                output(AudioDeviceInfo.TYPE_HDMI)));

        assertEquals("hdmi", DeviceCapabilities.getMediaOutput(audioManager));
    }

    @Test
    @Config(sdk = 30)
    public void withoutOutputsItsUnknown() {
        AudioManager audioManager = getAudioManager();
        shadowOf(audioManager).setOutputDevices(Collections.emptyList());

        assertNull(DeviceCapabilities.getMediaOutput(audioManager));
    }

    /**
     * Android 13+ says where media goes
     */
    @Test
    @Config(sdk = 33)
    public void android13SaysWhereItGoes() {
        AudioManager audioManager = getAudioManager();
        shadowOf(audioManager).setOutputDevices(Arrays.asList(output(AudioDeviceInfo.TYPE_HDMI),
                output(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)));
        AudioAttributes media = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build();
        shadowOf(audioManager).setAudioDevicesForAttributes(media, ImmutableList.of(output(AudioDeviceInfo.TYPE_HDMI)));

        assertEquals("hdmi", DeviceCapabilities.getMediaOutput(audioManager));
    }

    private static AudioDeviceInfo output(int type) {
        return AudioDeviceInfoBuilder.newBuilder().setType(type).build();
    }

    private static AudioManager getAudioManager() {
        return (AudioManager) RuntimeEnvironment.getApplication().getSystemService(Context.AUDIO_SERVICE);
    }
}
