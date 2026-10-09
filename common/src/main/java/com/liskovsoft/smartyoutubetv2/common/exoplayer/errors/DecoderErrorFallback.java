package com.liskovsoft.smartyoutubetv2.common.exoplayer.errors;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;

import java.util.Locale;

/**
 * Formats to go on with when a decoder keeps failing (see MediaCodecRenderer.DecoderErrorException): the ones most devices decode,
 * AVC for the video and AAC (mp4a) for the audio.
 */
public final class DecoderErrorFallback {
    // Width, height. A size down each step, 1080p is the largest AVC on YouTube.
    private static final int[][] AVC_SIZES = {{1920, 1080}, {1280, 720}, {854, 480}, {640, 360}};

    private DecoderErrorFallback() {
    }

    /**
     * AVC no larger than the failed format, or a size down when the failed format is AVC already.
     *
     * @return null when there's no smaller AVC to go on with
     */
    @Nullable
    public static FormatItem getVideoFormat(@Nullable Format failed) {
        if (failed == null) {
            return null;
        }

        boolean isAvc = MimeTypes.VIDEO_H264.equals(failed.sampleMimeType);
        int height = TrackSelectorUtil.getRealHeight(failed);

        if (height == -1) {
            // Unknown size: the largest AVC, or a size down from it
            height = isAvc ? AVC_SIZES[0][1] : Integer.MAX_VALUE;
        }

        for (int[] size : AVC_SIZES) {
            if (isAvc ? size[1] < height : size[1] <= height) {
                // The fps is a limit, so 30 fps videos match too
                return ExoFormatItem.fromVideoSpec(String.format(Locale.US, "%d,%d,60,avc", size[0], size[1]), false);
            }
        }

        return null;
    }

    /**
     * AAC in the given language, unless the failed format is AAC already.
     *
     * @param language language of the audio format in use, null for the default one
     * @return null when there's nothing else to go on with
     */
    @Nullable
    public static FormatItem getAudioFormat(@Nullable Format failed, @Nullable String language) {
        if (failed == null || MimeTypes.AUDIO_AAC.equals(failed.sampleMimeType)) {
            return null;
        }

        return ExoFormatItem.fromAudioSpecs(String.format("mp4a,%s", language));
    }
}
