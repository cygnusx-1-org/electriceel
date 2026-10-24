package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.TeeAudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * How long the decoded sound has been silent (see PlaybackHealth): a copy of the PCM the player outputs, the output itself isn't
 * changed. Encoded sound sent as it is (passthrough) isn't seen: it reads as not silent.
 */
public final class SilenceDetector implements TeeAudioProcessor.AudioBufferSink {
    private static final SilenceDetector sInstance = new SilenceDetector();
    /**
     * Digital silence, with dither: ±2 of 16 bits
     */
    private static final int MAX_SILENT_16BIT = 2;
    private static final float MAX_SILENT_FLOAT = 2f / Short.MAX_VALUE;
    private volatile int mSampleRateHz;
    private volatile int mChannelCount;
    private volatile int mEncoding = C.ENCODING_INVALID;
    private volatile long mSilentFrames;

    private SilenceDetector() {
    }

    /**
     * For the player's audio processors (see CustomOverridesRenderersFactory#buildAudioProcessors)
     */
    public static AudioProcessor createProcessor() {
        return new TeeAudioProcessor(sInstance);
    }

    /**
     * Silent this long, up to the last sound decoded. 0 when the sound isn't PCM.
     */
    public static long getSilentMs() {
        int sampleRateHz = sInstance.mSampleRateHz;

        return sampleRateHz > 0 ? sInstance.mSilentFrames * 1_000 / sampleRateHz : 0;
    }

    /**
     * On the playback thread: a new format, a seek
     */
    @Override
    public void flush(int sampleRateHz, int channelCount, int encoding) {
        mSampleRateHz = sampleRateHz;
        mChannelCount = channelCount;
        mEncoding = encoding;
        mSilentFrames = 0;
    }

    /**
     * On the playback thread: read only
     */
    @Override
    public void handleBuffer(ByteBuffer buffer) {
        int channelCount = mChannelCount;

        if (channelCount <= 0) {
            return;
        }

        ByteBuffer data = buffer.order(ByteOrder.nativeOrder());
        boolean isSilent;
        int frames;

        if (mEncoding == C.ENCODING_PCM_16BIT) {
            isSilent = isSilent16Bit(data);
            frames = data.remaining() / (2 * channelCount);
        } else if (mEncoding == C.ENCODING_PCM_FLOAT) {
            isSilent = isSilentFloat(data);
            frames = data.remaining() / (4 * channelCount);
        } else {
            // Not looked at: never silent
            mSilentFrames = 0;
            return;
        }

        mSilentFrames = isSilent ? mSilentFrames + frames : 0;
    }

    private static boolean isSilent16Bit(ByteBuffer data) {
        for (int i = data.position(); i + 1 < data.limit(); i += 2) {
            short sample = data.getShort(i);

            if (sample > MAX_SILENT_16BIT || sample < -MAX_SILENT_16BIT) {
                return false;
            }
        }

        return true;
    }

    private static boolean isSilentFloat(ByteBuffer data) {
        for (int i = data.position(); i + 3 < data.limit(); i += 4) {
            float sample = data.getFloat(i);

            if (sample > MAX_SILENT_FLOAT || sample < -MAX_SILENT_FLOAT) {
                return false;
            }
        }

        return true;
    }
}
