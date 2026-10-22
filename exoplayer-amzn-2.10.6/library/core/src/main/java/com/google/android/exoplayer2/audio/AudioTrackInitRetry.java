package com.google.android.exoplayer2.audio;

import com.google.android.exoplayer2.C;

/**
 * SmartTube fix: retries a refused {@link android.media.AudioTrack} creation for a short window,
 * instead of failing playback on the first refusal.
 *
 * <p>Every seek releases the old AudioTrack and builds a new one. The platform frees the old track's
 * share of the app's AudioFlinger memory only once the mixer has played out its last frames, tens of
 * milliseconds later, so for that moment the app needs room for two tracks. When that memory is
 * nearly full (10 MB per app on an Nvidia Shield), the new track is refused ("AudioTrack init
 * failed: 0") although the same request succeeds as soon as the old track is gone. Newer ExoPlayer
 * versions retry AudioTrack creation the same way.
 */
/* package */ final class AudioTrackInitRetry {

  private final long retryDurationMs;
  private final long retryIntervalMs;
  private long firstFailureMs;
  private long nextAttemptMs;

  /**
   * @param retryDurationMs How long after the first refusal the creation is retried before the
   *     refusal is reported.
   * @param retryIntervalMs The minimum time between two attempts.
   */
  public AudioTrackInitRetry(long retryDurationMs, long retryIntervalMs) {
    this.retryDurationMs = retryDurationMs;
    this.retryIntervalMs = retryIntervalMs;
    firstFailureMs = C.TIME_UNSET;
  }

  /** Returns whether an attempt to create the AudioTrack may be made at {@code nowMs}. */
  public boolean isAttemptDue(long nowMs) {
    return firstFailureMs == C.TIME_UNSET || nowMs >= nextAttemptMs;
  }

  /**
   * Records a refused attempt made at {@code nowMs}.
   *
   * @throws AudioSink.InitializationException The refusal, once the retry window has passed.
   */
  public void onFailure(AudioSink.InitializationException e, long nowMs)
      throws AudioSink.InitializationException {
    if (firstFailureMs == C.TIME_UNSET) {
      firstFailureMs = nowMs;
    }
    if (nowMs - firstFailureMs >= retryDurationMs) {
      reset();
      throw e;
    }
    nextAttemptMs = nowMs + retryIntervalMs;
  }

  /** Forgets earlier refusals, after a successful attempt or a flush. */
  public void reset() {
    firstFailureMs = C.TIME_UNSET;
  }
}
