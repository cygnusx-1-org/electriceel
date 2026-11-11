package com.google.android.exoplayer2.audio;

import static com.google.common.truth.Truth.assertThat;

import android.media.AudioTrack;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowAudioTrack;

/**
 * SmartTube fix: {@link DefaultAudioSink} in tunneling mode at the end of the stream.
 *
 * <p>Its AudioTrack is full (see {@link ShadowFullAudioTrack}): the buffer handled is left in the
 * sink, as the last one was when a video ended.
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 28, shadows = DefaultAudioSinkTunnelingTest.ShadowFullAudioTrack.class)
public final class DefaultAudioSinkTunnelingTest {

  private static final int SAMPLE_RATE_48 = 48_000;
  private static final int CHANNEL_COUNT_STEREO = 2;

  /**
   * What's left of the last buffer is written with that buffer's time: it threw
   * IllegalStateException, no AV sync time for it (9TJXo63g6JA at 3:21.3 of 3:21.8, opus over the
   * silence detector's tee).
   */
  @Test
  public void playsToEndOfStreamWithABufferLeft() throws Exception {
    DefaultAudioSink sink =
        new DefaultAudioSink(
            AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES,
            new AudioProcessor[] {new TeeAudioProcessor(new NoAudioBufferSink())});
    sink.enableTunnelingV21(/* tunnelingAudioSessionId= */ 1);
    sink.configure(
        C.ENCODING_PCM_16BIT,
        CHANNEL_COUNT_STEREO,
        SAMPLE_RATE_48,
        /* specifiedBufferSize= */ 0,
        /* outputChannels= */ null,
        /* trimStartFrames= */ 0,
        /* trimEndFrames= */ 0);

    // Taken by the tee, none of it written: it's the sink's output left
    assertThat(sink.handleBuffer(createSilence(), /* presentationTimeUs= */ 201_000_000)).isTrue();

    sink.playToEndOfStream();

    // Still to be written: the AudioTrack takes none
    assertThat(sink.isEnded()).isFalse();
  }

  /** Half a second of 48 kHz stereo 16-bit silence */
  private static ByteBuffer createSilence() {
    return ByteBuffer.allocateDirect(SAMPLE_RATE_48 / 2 * CHANNEL_COUNT_STEREO * 2)
        .order(ByteOrder.nativeOrder());
  }

  /** An AudioTrack that takes nothing: it's full */
  @Implements(AudioTrack.class)
  public static final class ShadowFullAudioTrack extends ShadowAudioTrack {

    @Implementation
    @Override
    protected int native_write_byte(
        byte[] audioData, int offsetInBytes, int sizeInBytes, int format, boolean isBlocking) {
      return 0;
    }

    @Implementation
    @Override
    protected int native_write_native_bytes(
        Object audioData, int positionInBytes, int sizeInBytes, int format, boolean isBlocking) {
      return 0;
    }
  }

  private static final class NoAudioBufferSink implements TeeAudioProcessor.AudioBufferSink {

    @Override
    public void flush(int sampleRateHz, int channelCount, int encoding) {}

    @Override
    public void handleBuffer(ByteBuffer buffer) {}
  }
}
