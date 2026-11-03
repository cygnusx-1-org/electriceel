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

  /* package */ void reset() {
    timestampState = TIMESTAMP_STATE_UNKNOWN;
    playheadMs = -1;
    latencyMs = -1;
    framesMismatches = 0;
    clockMismatches = 0;
    badLatencies = 0;
  }
}
