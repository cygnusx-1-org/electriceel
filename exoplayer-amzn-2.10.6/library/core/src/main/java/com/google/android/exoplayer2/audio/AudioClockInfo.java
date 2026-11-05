package com.google.android.exoplayer2.audio;

/**
 * For diagnostics: what the audio clock of the AudioTrack in use goes by. Written on the playback thread, read on any.
 */
public final class AudioClockInfo {
  /** No sample yet: no AudioTrack, or the one in use hasn't output anything. The others are AudioTimestampPoller's states. */
  public static final int TIMESTAMP_STATE_UNKNOWN = -1;

  public volatile int timestampState = TIMESTAMP_STATE_UNKNOWN;
  /** Of the frames the AudioTrack has played, -1 when unknown. */
  public volatile long playheadMs = -1;
  /** Taken off the playhead's position, -1 when unknown. */
  public volatile long latencyMs = -1;
  /** Timestamps rejected for their frames being too far off the playhead. */
  public volatile int framesMismatches;
  /** Timestamps rejected for their time being too far off the system clock. */
  public volatile int clockMismatches;
  /** Latencies ignored for being impossibly large. */
  public volatile int badLatencies;
  /**
   * Times the position stopped waiting on a timestamp that didn't advance, the playhead having got too far past it: it went by the
   * playhead from there (see AudioTrackPositionTracker#MAX_TIMESTAMP_HOLD_US).
   */
  public volatile int holdReleases;
  /** The longest the position waited on a timestamp that didn't advance: how far the playhead got past it meanwhile. */
  public volatile long longestHoldMs;
  /**
   * Of all the AudioTracks so far, never reset: what they go up by over a while is that while's, whatever tracks it had (e.g. a seek
   * makes another).
   */
  public volatile int totalFramesMismatches;
  public volatile int totalClockMismatches;
  public volatile int totalHoldReleases;

  /** Another AudioTrack, or none: the totals go on. */
  /* package */ void reset() {
    timestampState = TIMESTAMP_STATE_UNKNOWN;
    playheadMs = -1;
    latencyMs = -1;
    framesMismatches = 0;
    clockMismatches = 0;
    badLatencies = 0;
    holdReleases = 0;
    longestHoldMs = 0;
  }
}
