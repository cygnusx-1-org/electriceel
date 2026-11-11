package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;

import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The presenters see the skin the app shows only through its traits
 */
public class SkinsTest {
    private static final int OTHER_SKIN = 5;

    @After
    public void tearDown() {
        Skins.setProvider(null);
    }

    @Test
    public void classicBeforeTheAppSetsItsSkins() {
        assertSame(SkinTraits.CLASSIC, Skins.getTraits());
        assertEquals(MainUIData.SKIN_CLASSIC, Skins.getAppliedSkinId());
        assertEquals(Arrays.asList(MainUIData.SKIN_CLASSIC), Skins.getSkinIds());
        assertFalse(Skins.replace(null));
    }

    @Test
    public void classicTraitsChangeNothing() {
        SkinTraits classic = SkinTraits.CLASSIC;

        assertFalse(classic.hasOwnSearchKeyboard());
        assertFalse(classic.hasFixedCardSubtitle());
        assertFalse(classic.showsListValuesAsSummary());
        assertFalse(classic.keepsSectionOnChannelError());
        assertFalse(classic.prefetchesFocusedVideo());
    }

    @Test
    public void traitsAreTheAppliedSkins() {
        FakeProvider provider = new FakeProvider(OTHER_SKIN);
        Skins.setProvider(provider);

        assertTrue(Skins.getTraits().hasOwnSearchKeyboard());
        assertEquals(OTHER_SKIN, Skins.getAppliedSkinId());

        provider.mApplied = MainUIData.SKIN_CLASSIC;

        assertFalse(Skins.getTraits().hasOwnSearchKeyboard());
    }

    @Test
    public void unknownTraitsAreClassic() {
        Skins.setProvider(new FakeProvider(99));

        assertSame(SkinTraits.CLASSIC, Skins.getTraits());
    }

    private static final class FakeProvider implements Skins.Provider {
        private int mApplied;

        FakeProvider(int applied) {
            mApplied = applied;
        }

        @Override
        public List<Integer> getSkinIds() {
            return Arrays.asList(MainUIData.SKIN_CLASSIC, OTHER_SKIN);
        }

        @Override
        public int getSkinNameResId(int skinId) {
            return 0;
        }

        @Override
        public int getAppliedSkinId() {
            return mApplied;
        }

        @Override
        public SkinTraits getTraits(int skinId) {
            if (skinId == OTHER_SKIN) {
                return new SkinTraits() {
                    @Override
                    public boolean hasOwnSearchKeyboard() {
                        return true;
                    }

                    @Override
                    public boolean hasFixedCardSubtitle() {
                        return true;
                    }

                    @Override
                    public boolean showsListValuesAsSummary() {
                        return true;
                    }

                    @Override
                    public boolean keepsSectionOnChannelError() {
                        return true;
                    }

                    @Override
                    public boolean prefetchesFocusedVideo() {
                        return false;
                    }
                };
            }

            return skinId == MainUIData.SKIN_CLASSIC ? SkinTraits.CLASSIC : null;
        }

        @Override
        public void applyPickedSkin(Context context) {
            // NOP
        }

        @Override
        public boolean isPickedSkinInPlace(Context context) {
            return true;
        }

        @Override
        public boolean replace(Activity activity) {
            return false;
        }
    }
}
