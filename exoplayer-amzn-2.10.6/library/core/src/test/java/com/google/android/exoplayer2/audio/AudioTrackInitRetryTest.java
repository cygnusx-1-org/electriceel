package com.google.android.exoplayer2.audio;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Unit test for {@link AudioTrackInitRetry} (SmartTube fix, #53): a refused AudioTrack creation is
 * retried for a short window before it's reported.
 *
 * <p>Plain JUnit (no Robolectric), like {@code MediaCodecRendererTest}: the class takes the time as
 * a parameter, so no Android clock is needed.
 */
public class AudioTrackInitRetryTest {

  private static final long DURATION_MS = 200;
  private static final long INTERVAL_MS = 20;

  @Test
  public void firstAttempt_isDue() {
    AudioTrackInitRetry retry = new AudioTrackInitRetry(DURATION_MS, INTERVAL_MS);

    assertThat(retry.isAttemptDue(1_000)).isTrue();
  }

  @Test
  public void refusal_withinWindow_isNotReported() throws Exception {
    AudioTrackInitRetry retry = new AudioTrackInitRetry(DURATION_MS, INTERVAL_MS);

    retry.onFailure(refusal(), 1_000);
    retry.onFailure(refusal(), 1_000 + DURATION_MS - 1);
  }

  @Test
  public void nextAttempt_waitsForTheInterval() throws Exception {
    AudioTrackInitRetry retry = new AudioTrackInitRetry(DURATION_MS, INTERVAL_MS);

    retry.onFailure(refusal(), 1_000);

    assertThat(retry.isAttemptDue(1_000 + INTERVAL_MS - 1)).isFalse();
    assertThat(retry.isAttemptDue(1_000 + INTERVAL_MS)).isTrue();
  }

  @Test
  public void refusal_atEndOfWindow_isReported() throws Exception {
    AudioTrackInitRetry retry = new AudioTrackInitRetry(DURATION_MS, INTERVAL_MS);
    AudioSink.InitializationException last = refusal();

    retry.onFailure(refusal(), 1_000);
    try {
      retry.onFailure(last, 1_000 + DURATION_MS);
      fail("The refusal should be reported once the window has passed");
    } catch (AudioSink.InitializationException e) {
      assertThat(e).isSameInstanceAs(last);
    }
  }

  @Test
  public void reportedRefusal_startsANewWindow() throws Exception {
    AudioTrackInitRetry retry = new AudioTrackInitRetry(DURATION_MS, INTERVAL_MS);
    retry.onFailure(refusal(), 1_000);
    try {
      retry.onFailure(refusal(), 1_000 + DURATION_MS);
    } catch (AudioSink.InitializationException expected) {
      // Reported
    }

    // E.g. after the app reloads the video
    assertThat(retry.isAttemptDue(1_000 + DURATION_MS)).isTrue();
    retry.onFailure(refusal(), 5_000);
  }

  @Test
  public void reset_startsANewWindow() throws Exception {
    AudioTrackInitRetry retry = new AudioTrackInitRetry(DURATION_MS, INTERVAL_MS);
    retry.onFailure(refusal(), 1_000);

    // A success or a seek
    retry.reset();

    assertThat(retry.isAttemptDue(1_001)).isTrue();
    retry.onFailure(refusal(), 1_000 + DURATION_MS);
  }

  private static AudioSink.InitializationException refusal() {
    // As thrown on the Shield: AudioTrack.STATE_UNINITIALIZED, 44.1 kHz stereo AAC
    return new AudioSink.InitializationException(0, 44100, 12, 44100);
  }
}
