package com.google.android.exoplayer2.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;

import com.google.android.exoplayer2.C;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The clock of a track whose AudioTrack#getTimestamp doesn't advance while it plays, as a SHIELD's. Here, not with the vendored ExoPlayer:
 * that module's tests don't compile. In ExoPlayer's package: the tracker is package-private.
 */
@RunWith(RobolectricTestRunner.class)
public final class AudioTrackPositionTrackerTest {
    private static final int SAMPLE_RATE = 48_000;
    private static final int FRAME_SIZE = 4;
    private static final int BUFFER_SIZE = FRAME_SIZE * SAMPLE_RATE / 5;

    private AudioTrack mAudioTrack;
    private AudioTrackPositionTracker mTracker;
    private int mListenerMismatches;
    private int mPlayheadFrames;
    private long mTimestampFrames;

    @Before
    public void setUp() {
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE, AudioTrack.MODE_STREAM) {
            @Override
            public int getPlayState() {
                return PLAYSTATE_PLAYING;
            }

            @Override
            public int getSampleRate() {
                return SAMPLE_RATE;
            }

            @Override
            public int getPlaybackHeadPosition() {
                return mPlayheadFrames;
            }

            @Override
            public boolean getTimestamp(AudioTimestamp timestamp) {
                timestamp.framePosition = mTimestampFrames;
                timestamp.nanoTime = System.nanoTime();
                return true;
            }
        };

        mTracker = new AudioTrackPositionTracker(new AudioTrackPositionTracker.Listener() {
            @Override
            public void onPositionFramesMismatch(long frames, long timestampUs, long systemUs, long playbackUs) {
                mListenerMismatches++;
            }

            @Override
            public void onSystemTimeUsMismatch(long frames, long timestampUs, long systemUs, long playbackUs) {
                mListenerMismatches++;
            }

            @Override
            public void onInvalidLatency(long latencyUs) {
            }

            @Override
            public void onUnderrun(int bufferSize, long bufferSizeMs) {
            }
        }, false);
        setTrack();
        mTracker.start();
    }

    @Test
    public void aTimestampThatDoesntAdvanceDoesntHoldThePosition() throws InterruptedException {
        long firstUs = playMs(100);
        long laterUs = playMs(1_000);

        assertEquals(1, mTracker.getClockInfo().timestampState); // AudioTimestampPoller's STATE_TIMESTAMP
        assertTrue("moved " + (laterUs - firstUs), laterUs - firstUs >= 500_000);
    }

    @Test
    public void aTimestampThatAdvancesIsUsed() throws InterruptedException {
        playMs(100);
        mTimestampFrames = 3 * SAMPLE_RATE;
        long positionUs = playMs(100);

        assertEquals(2, mTracker.getClockInfo().timestampState); // AudioTimestampPoller's STATE_TIMESTAMP_ADVANCING
        assertTrue("position " + positionUs, positionUs >= 3_000_000);
        assertEquals(0, mTracker.getClockInfo().holdReleases);
    }

    @Test
    public void theWaitOnATimestampThatDoesntAdvanceIsCountedWhenLetGo() throws InterruptedException {
        playMs(300);
        AudioClockInfo info = mTracker.getClockInfo();

        assertEquals(0, info.holdReleases);
        assertTrue("held " + info.longestHoldMs, info.longestHoldMs >= 200 && info.longestHoldMs < 500);

        playMs(1_000);

        // Once, not at each position after it
        assertEquals(1, info.holdReleases);
        assertEquals(1, info.totalHoldReleases);
        assertTrue("held " + info.longestHoldMs, info.longestHoldMs < 500);
    }

    @Test
    public void theTotalsGoOnWithAnotherTrack() throws InterruptedException {
        playMs(1_000);
        setTrack();
        mPlayheadFrames = 0;
        mTracker.start();

        assertEquals(0, mTracker.getClockInfo().holdReleases);
        assertEquals(0, mTracker.getClockInfo().longestHoldMs);
        assertEquals(1, mTracker.getClockInfo().totalHoldReleases);

        playMs(1_000);

        assertEquals(1, mTracker.getClockInfo().holdReleases);
        assertEquals(2, mTracker.getClockInfo().totalHoldReleases);
    }

    /**
     * Its track released (e.g. a seek, another video): what it went by isn't the next one's
     */
    @Test
    public void aResetTellsNothingOfTheTrackBefore() throws InterruptedException {
        playMs(1_000);
        mTracker.reset();
        AudioClockInfo info = mTracker.getClockInfo();

        assertEquals(AudioClockInfo.TIMESTAMP_STATE_UNKNOWN, info.timestampState);
        assertEquals(-1, info.playheadMs);
        assertEquals(0, info.holdReleases);
        assertEquals(0, info.longestHoldMs);
        assertEquals(1, info.totalHoldReleases);
    }

    @Test
    public void withTimestampsDisabledThePlayheadIsAllItGoesBy() throws InterruptedException {
        mTracker.setTimestampsDisabled(true);
        playMs(100);
        mTimestampFrames = 3 * SAMPLE_RATE;
        long positionUs = playMs(500);

        assertEquals(0, mTracker.getClockInfo().timestampState); // AudioTimestampPoller's STATE_INITIALIZING: never polled
        assertTrue("position " + positionUs, positionUs < 2_000_000);
    }

    @Test
    public void aNewTrackIsPolledWithItsTimestampsDisabledToo() throws InterruptedException {
        mTracker.setTimestampsDisabled(true);
        playMs(100);
        setTrack();
        mTimestampFrames = 3 * SAMPLE_RATE;
        mTracker.start();
        long positionUs = playMs(100);

        assertEquals(0, mTracker.getClockInfo().timestampState);
        assertTrue("position " + positionUs, positionUs < 2_000_000);
    }

    @Test
    public void timestampsFarOffThePlayheadAreCounted() throws InterruptedException {
        playMs(100);
        mPlayheadFrames += 6 * SAMPLE_RATE;
        playMs(100);

        assertTrue(mListenerMismatches >= 1);
        assertEquals(mListenerMismatches, mTracker.getClockInfo().framesMismatches);
        assertEquals(mListenerMismatches, mTracker.getClockInfo().totalFramesMismatches);
        assertTrue(mTracker.getClockInfo().playheadMs >= 6_000);
    }

    @Test
    public void theClockInfoStartsOverWithTheTrack() throws InterruptedException {
        playMs(100);
        mPlayheadFrames += 6 * SAMPLE_RATE;
        playMs(100);
        setTrack();

        assertEquals(0, mTracker.getClockInfo().framesMismatches);
        assertEquals(mListenerMismatches, mTracker.getClockInfo().totalFramesMismatches);
        assertEquals(-1, mTracker.getClockInfo().playheadMs);
        assertEquals(AudioClockInfo.TIMESTAMP_STATE_UNKNOWN, mTracker.getClockInfo().timestampState);
    }

    private void setTrack() {
        mTracker.setAudioTrack(mAudioTrack, C.ENCODING_PCM_16BIT, FRAME_SIZE, BUFFER_SIZE, false);
    }

    /**
     * In real time, as the tracker's samples are spaced in it
     */
    private long playMs(int playedMs) throws InterruptedException {
        long positionUs = 0;

        for (int i = 0; i < playedMs / 20; i++) {
            mPlayheadFrames += SAMPLE_RATE / 50;
            Thread.sleep(20);
            positionUs = mTracker.getCurrentPositionUs(false);
        }

        return positionUs;
    }
}
