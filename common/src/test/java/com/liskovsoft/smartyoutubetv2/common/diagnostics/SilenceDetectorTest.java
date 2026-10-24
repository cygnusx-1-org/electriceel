package com.liskovsoft.smartyoutubetv2.common.diagnostics;

import com.google.android.exoplayer2.C;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;

public class SilenceDetectorTest {
    private static final int RATE = 48_000;

    @Test
    public void silenceAddsUpAndSoundEndsIt() throws Exception {
        TeeSink sink = new TeeSink();
        sink.flush(RATE, 2, C.ENCODING_PCM_16BIT);

        sink.handleBuffer(pcm16(RATE, 0)); // 1 s of digital silence
        sink.handleBuffer(pcm16(RATE, 1)); // dither
        assertEquals(2_000, SilenceDetector.getSilentMs());

        sink.handleBuffer(pcm16(RATE / 10, 1_000));
        assertEquals(0, SilenceDetector.getSilentMs());
    }

    @Test
    public void floatSilenceToo() {
        TeeSink sink = new TeeSink();
        sink.flush(RATE, 2, C.ENCODING_PCM_FLOAT);

        ByteBuffer buffer = ByteBuffer.allocate(RATE * 2 * 4).order(ByteOrder.nativeOrder());
        sink.handleBuffer(buffer);

        assertEquals(1_000, SilenceDetector.getSilentMs());
    }

    @Test
    public void otherEncodingsArentSilent() {
        TeeSink sink = new TeeSink();
        sink.flush(RATE, 2, C.ENCODING_PCM_24BIT);
        sink.handleBuffer(ByteBuffer.allocate(RATE * 2 * 3));

        assertEquals(0, SilenceDetector.getSilentMs());
    }

    /**
     * Stereo frames, every sample the value
     */
    private static ByteBuffer pcm16(int frames, int value) {
        ByteBuffer buffer = ByteBuffer.allocate(frames * 2 * 2).order(ByteOrder.nativeOrder());

        for (int i = 0; i < frames * 2; i++) {
            buffer.putShort((short) value);
        }

        buffer.flip();

        return buffer.asReadOnlyBuffer();
    }

    /**
     * The detector the processor feeds
     */
    private static final class TeeSink {
        private final com.google.android.exoplayer2.audio.TeeAudioProcessor.AudioBufferSink mSink;

        TeeSink() {
            try {
                java.lang.reflect.Field field = SilenceDetector.class.getDeclaredField("sInstance");
                field.setAccessible(true);
                mSink = (com.google.android.exoplayer2.audio.TeeAudioProcessor.AudioBufferSink) field.get(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        void flush(int rate, int channels, int encoding) {
            mSink.flush(rate, channels, encoding);
        }

        void handleBuffer(ByteBuffer buffer) {
            mSink.handleBuffer(buffer);
        }
    }
}
