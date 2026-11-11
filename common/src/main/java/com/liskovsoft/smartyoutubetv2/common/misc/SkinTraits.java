package com.liskovsoft.smartyoutubetv2.common.misc;

/**
 * What a skin (the look and navigation of the app, User interface > Skin) does differently, as far as the presenters need
 * to know it. They ask for a trait, never for a skin. The skins themselves are in the app (SkinRegistry), see {@link Skins}.
 */
public interface SkinTraits {
    /**
     * The traits of the classic look, also used before the app has set its skins (e.g. in unit tests)
     */
    SkinTraits CLASSIC = new SkinTraits() {
        @Override
        public boolean hasOwnSearchKeyboard() {
            return false;
        }

        @Override
        public boolean hasFixedCardSubtitle() {
            return false;
        }

        @Override
        public boolean showsListValuesAsSummary() {
            return false;
        }

        @Override
        public boolean keepsSectionOnChannelError() {
            return false;
        }

        @Override
        public boolean prefetchesFocusedVideo() {
            return false;
        }
    };

    /**
     * Search has its own on-screen keyboard: the workaround for the system keyboard doesn't apply (Search > Keyboard fix)
     */
    boolean hasOwnSearchKeyboard();

    /**
     * The cards always show the channel and the views or the date on two lines (Cards > Multiline subtitle)
     */
    boolean hasFixedCardSubtitle();

    /**
     * A list of the settings shows its current value under its title
     */
    boolean showsListValuesAsSummary();

    /**
     * A grid section shows the videos of one of its channels in place: a load error of the channel doesn't reload the section
     */
    boolean keepsSectionOnChannelError();

    /**
     * The format info of the focused video is loaded ahead, so it opens without waiting for it
     */
    boolean prefetchesFocusedVideo();
}
