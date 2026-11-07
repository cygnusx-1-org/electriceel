package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import android.net.Uri;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.source.chunk.ChunkExtractorWrapper;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SubtitleDecoder;
import com.google.android.exoplayer2.text.SubtitleDecoderFactory;
import com.google.android.exoplayer2.text.SubtitleInputBuffer;
import com.google.android.exoplayer2.text.SubtitleOutputBuffer;
import com.google.android.exoplayer2.upstream.ByteArrayDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * A live stream's caption segment (itag 387) is an mp4 whose sample entry says tx3g, its one sample a WebVTT document with cue
 * times relative to the segment, said to be a byte longer than it is and not a sync sample (issue yuliskov/SmartTube#5681). Built
 * here as YouTube serves it, minus the emsg.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LiveCaptionChunkTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int TIMESCALE = 90_000;
    private static final long SEGMENT_START_US = 45_150_000_000L; // 12.5 hours into the stream
    private static final String WEBVTT =
            "WEBVTT\nX-TIMESTAMP-MAP=LOCAL:00:00.000,MPEGTS:4066374311\nKind: captions\n\n"
                    + "00:00:00.000 --> 00:00:02.000 align:start position:0% line:68%\nREPORTING FROM BANGOR\n\n"
                    + "00:00:02.000 --> 00:00:05.000 align:start position:0% line:68%\nMAINE\n\n";

    @Test
    public void captionSample_isDecodedAsWebVtt_atTheSegmentsTime() throws Exception {
        Output output = extract(textManifestFormat(MimeTypes.TEXT_VTT));

        assertEquals(MimeTypes.TEXT_VTT, output.format.sampleMimeType);
        assertEquals(Format.OFFSET_SAMPLE_RELATIVE, output.format.subsampleOffsetUs);
        assertEquals(".en.eEY6OEpapPo", output.format.id);
        assertEquals("english", output.format.language);
        assertEquals(SEGMENT_START_US, output.sampleTimeUs);
        // The whole document, cut where the mdat ends though the mp4 says it's a byte longer
        assertEquals(WEBVTT, new String(output.sample, UTF_8));
        // Kept by a sample queue, which drops everything before the first sync sample
        assertEquals(C.BUFFER_FLAG_KEY_FRAME, output.sampleFlags & C.BUFFER_FLAG_KEY_FRAME);

        SubtitleOutputBuffer subtitle = decode(output);

        // Cue times are the segment's plus the document's
        assertEquals(SEGMENT_START_US, subtitle.getEventTime(0));
        assertEquals(SEGMENT_START_US + 5_000_000, subtitle.getEventTime(subtitle.getEventTimeCount() - 1));
        assertEquals("REPORTING FROM BANGOR", text(subtitle.getCues(SEGMENT_START_US + 1_000_000)));
        assertEquals("MAINE", text(subtitle.getCues(SEGMENT_START_US + 3_000_000)));
    }

    @Test
    public void tx3gTrack_staysTx3g_unlessTheManifestSaysWebVtt() throws Exception {
        assertEquals(MimeTypes.APPLICATION_TX3G, extract(textManifestFormat(MimeTypes.APPLICATION_TX3G)).format.sampleMimeType);
        assertEquals(MimeTypes.APPLICATION_TX3G, extract(null).format.sampleMimeType);
    }

    private static Format textManifestFormat(String sampleMimeType) {
        return Format.createTextContainerFormat(".en.eEY6OEpapPo", null, MimeTypes.APPLICATION_MP4, sampleMimeType, null,
                Format.NO_VALUE, 0, C.ROLE_FLAG_SUBTITLE, "English", Format.NO_VALUE);
    }

    private static Output extract(@Nullable Format manifestFormat) throws IOException, InterruptedException {
        byte[] segment = captionSegment();
        Extractor extractor = new FragmentedMp4Extractor(0, null, null, null, new ArrayList<>(), null);
        ChunkExtractorWrapper wrapper = new ChunkExtractorWrapper(extractor, C.TRACK_TYPE_TEXT, manifestFormat);
        Output output = new Output();
        wrapper.init((id, type) -> output, C.TIME_UNSET, C.TIME_UNSET);

        ByteArrayDataSource dataSource = new ByteArrayDataSource(segment);
        dataSource.open(new DataSpec(Uri.EMPTY));
        ExtractorInput input = new DefaultExtractorInput(dataSource, 0, segment.length);
        PositionHolder positionHolder = new PositionHolder();

        while (extractor.read(input, positionHolder) == Extractor.RESULT_CONTINUE) {
            // Reads the segment through
        }

        assertNotNull("no sample", output.sample);

        return output;
    }

    private static SubtitleOutputBuffer decode(Output output) throws Exception {
        SubtitleDecoder decoder = SubtitleDecoderFactory.DEFAULT.createDecoder(output.format);

        try {
            SubtitleInputBuffer input = decoder.dequeueInputBuffer();
            input.ensureSpaceForWrite(output.sample.length);
            input.data.put(output.sample);
            input.timeUs = output.sampleTimeUs;
            input.subsampleOffsetUs = output.format.subsampleOffsetUs; // as TextRenderer sets it
            input.flip();
            decoder.queueInputBuffer(input);

            for (int i = 0; i < 200; i++) {
                SubtitleOutputBuffer result = decoder.dequeueOutputBuffer();

                if (result != null) {
                    return result;
                }

                Thread.sleep(10);
            }

            throw new AssertionError("not decoded");
        } finally {
            decoder.release();
        }
    }

    private static String text(List<Cue> cues) {
        assertEquals(1, cues.size());
        return cues.get(0).text.toString();
    }

    /**
     * ftyp, moov (one text track, tx3g sample entry), moof (one sample at SEGMENT_START_US), mdat (the WebVTT document)
     */
    private static byte[] captionSegment() {
        byte[] sample = WEBVTT.getBytes(UTF_8);

        byte[] moov = box("moov",
                fullBox("mvhd", 0, 0, ints(0, 0, TIMESCALE, 0, 0x00010000), shorts(0x0100), zeros(10), matrix(), zeros(24), ints(2)),
                box("mvex", fullBox("trex", 0, 0, ints(1, 1, 0, 0, 0))),
                box("trak",
                        fullBox("tkhd", 0, 7, ints(0, 0, 1, 0, 0), zeros(8), zeros(8), matrix(), ints(0, 0)),
                        box("mdia",
                                fullBox("mdhd", 0, 0, ints(0, 0, TIMESCALE, 0), shorts(0x15C7, 0)), // "eng"
                                fullBox("hdlr", 0, 0, ints(0), ascii("text"), zeros(12), zeros(1)),
                                box("minf",
                                        fullBox("nmhd", 0, 0),
                                        box("dinf", fullBox("dref", 0, 0, ints(1), fullBox("url ", 0, 1))),
                                        box("stbl",
                                                fullBox("stsd", 0, 0, ints(1), box("tx3g", zeros(6), shorts(1), zeros(30))),
                                                fullBox("stts", 0, 0, ints(0)),
                                                fullBox("stsc", 0, 0, ints(0)),
                                                fullBox("stco", 0, 0, ints(0)),
                                                fullBox("stsz", 0, 0, ints(0, 0)))))));

        long baseMediaDecodeTime = SEGMENT_START_US * TIMESCALE / C.MICROS_PER_SECOND;
        int moofSize = 8 + 16 + (8 + 32 + 20 + 20);
        byte[] moof = box("moof",
                fullBox("mfhd", 0, 0, ints(1)),
                box("traf",
                        // As YouTube's: base is moof, sample description index, default duration, size and flags. The size is a byte
                        // more than the mdat holds, the flags say it isn't a sync sample.
                        fullBox("tfhd", 0, 0x02003a, ints(1, 1, 5 * TIMESCALE, sample.length + 1, 0x00010000)),
                        fullBox("tfdt", 1, 0, longs(baseMediaDecodeTime)),
                        fullBox("trun", 0, 0x000001, ints(1, moofSize + 8)))); // data offset
        assertEquals(moofSize, moof.length);

        return concat(box("ftyp", ascii("iso6"), ints(0), ascii("iso6"), ascii("dash")), moov, moof, box("mdat", sample));
    }

    private static byte[] box(String type, byte[]... payload) {
        byte[] body = concat(payload);
        return concat(ints(8 + body.length), ascii(type), body);
    }

    private static byte[] fullBox(String type, int version, int flags, byte[]... payload) {
        return box(type, concat(ints((version << 24) | flags), concat(payload)));
    }

    private static byte[] matrix() {
        return ints(0x00010000, 0, 0, 0, 0x00010000, 0, 0, 0, 0x40000000);
    }

    private static byte[] ints(int... values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        for (int value : values) {
            out.write(value >>> 24);
            out.write(value >>> 16);
            out.write(value >>> 8);
            out.write(value);
        }

        return out.toByteArray();
    }

    private static byte[] longs(long value) {
        return concat(ints((int) (value >>> 32)), ints((int) value));
    }

    private static byte[] shorts(int... values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        for (int value : values) {
            out.write(value >>> 8);
            out.write(value);
        }

        return out.toByteArray();
    }

    private static byte[] zeros(int count) {
        return new byte[count];
    }

    private static byte[] ascii(String text) {
        return text.getBytes(UTF_8);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }

        return out.toByteArray();
    }

    private static class Output implements TrackOutput {
        private Format format;
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        private byte[] sample;
        private long sampleTimeUs = C.TIME_UNSET;
        private int sampleFlags;

        @Override
        public void format(Format format) {
            this.format = format;
        }

        @Override
        public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput) throws IOException, InterruptedException {
            byte[] buffer = new byte[length];
            int read = input.read(buffer, 0, length);

            if (read == C.RESULT_END_OF_INPUT) {
                if (allowEndOfInput) {
                    return read;
                }

                throw new EOFException(); // as a sample queue does
            }

            pending.write(buffer, 0, read);

            return read;
        }

        @Override
        public void sampleData(ParsableByteArray data, int length) {
            byte[] buffer = new byte[length];
            data.readBytes(buffer, 0, length);
            pending.write(buffer, 0, length);
        }

        @Override
        public void sampleMetadata(long timeUs, int flags, int size, int offset, @Nullable CryptoData cryptoData) {
            byte[] all = pending.toByteArray();
            sample = new byte[size];
            System.arraycopy(all, all.length - size - offset, sample, 0, size);
            sampleFlags = flags;
            sampleTimeUs = timeUs;
            pending.reset();
        }
    }
}
