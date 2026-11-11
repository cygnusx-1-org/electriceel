package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser2;
import com.google.android.exoplayer2.source.dash.manifest.RangedUri;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * A live format has no init range: its URL alone is answered with the newest whole segment, loaded as the init before the first
 * segment played (E3RZYG50: 3.2 MB and 1.7 s of a 4K start). Its segments start with their init.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LiveSegmentInitTest {
    private static final String LIVE_URL = "https://rr1---sn-test.googlevideo.com/videoplayback?source=yt_live_broadcast&live=1&itag=";

    @Test
    public void liveFormatsWithoutAnInitRange_haveNoInitSegment() {
        DashManifest manifest = parse(format("299", "video/mp4; codecs=\"avc1.64002a\"", null),
                format("140", "audio/mp4; codecs=\"mp4a.40.2\"", null));

        List<Representation> representations = representationsOf(manifest);
        assertEquals(2, representations.size());

        for (Representation representation : representations) {
            assertNull(representation.format.id, representation.getInitializationUri());
        }
    }

    @Test
    public void aLiveFormatWithAnInitRange_keepsIt() {
        Representation representation = representationsOf(parse(format("140", "audio/mp4; codecs=\"mp4a.40.2\"", "0-631"))).get(0);

        RangedUri init = representation.getInitializationUri();
        assertNotNull(init);
        assertEquals(0, init.start);
        assertEquals(632, init.length);
        assertEquals(LIVE_URL + "140", init.resolveUriString(representation.baseUrl));
    }

    private static DashManifest parse(MediaFormat... formats) {
        return new DashManifestParser2().parse(proxy(MediaItemFormatInfo.class, map(
                "getVideoId", "1YXN4bsps9Y",
                "getLengthSeconds", "0",
                "isLive", true,
                "getAdaptiveFormats", Arrays.asList(formats),
                "getSegmentDurationUs", 5_000_000,
                "getStartSegmentNum", 9092)));
    }

    private static List<Representation> representationsOf(DashManifest manifest) {
        List<Representation> result = new ArrayList<>();

        for (AdaptationSet set : manifest.getPeriod(0).adaptationSets) {
            result.addAll(set.representations);
        }

        return result;
    }

    private static MediaFormat format(String itag, String mimeType, String init) {
        return proxy(MediaFormat.class, map(
                "getITag", itag,
                "getMimeType", mimeType,
                "getUrl", LIVE_URL + itag,
                "getBitrate", "144000",
                "getInit", init,
                "getTargetDurationSec", 5));
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> result = new HashMap<>();

        for (int i = 0; i < keysAndValues.length; i += 2) {
            result.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }

        return result;
    }

    /**
     * Answers the named getters, every other call gets the type's default
     */
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            if (values.containsKey(method.getName())) {
                return values.get(method.getName());
            } else if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(self);
            } else if ("equals".equals(method.getName())) {
                return self == args[0];
            } else if ("toString".equals(method.getName())) {
                return type.getSimpleName() + values;
            }

            Class<?> returnType = method.getReturnType();

            if (returnType == boolean.class) {
                return false;
            } else if (returnType == int.class) {
                return 0;
            } else if (returnType == long.class) {
                return 0L;
            } else if (returnType == float.class) {
                return 0f;
            }

            return null;
        });
    }
}
