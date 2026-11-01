package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AppDialogPresenterTest {
    private AppDialogPresenter mPresenter;

    @Before
    public void setUp() {
        mPresenter = new AppDialogPresenter(RuntimeEnvironment.getApplication());
    }

    /**
     * Back from the notes of a release: its row has the focus in the list opened again
     */
    @Test
    public void focusedItem_ofTheDialogShown() {
        OptionItem first = UiOptionItem.from("32.68.3", option -> {});
        OptionItem opened = UiOptionItem.from("32.68.1", option -> {});

        mPresenter.appendSingleButton(first);
        mPresenter.appendSingleButton(opened);
        mPresenter.setFocusedItem(opened);
        mPresenter.showDialog("Release notes");

        assertSame(opened, mPresenter.getFocusedItem());
    }

    /**
     * Of one dialog only: the next one opens on its first row
     */
    @Test
    public void focusedItem_notKeptForTheNextDialog() {
        OptionItem opened = UiOptionItem.from("32.68.1", option -> {});

        mPresenter.appendSingleButton(opened);
        mPresenter.setFocusedItem(opened);
        mPresenter.showDialog("Release notes");

        mPresenter.appendSingleButton(UiOptionItem.from("Check for updates", option -> {}));
        mPresenter.showDialog("Updates");

        assertNull(mPresenter.getFocusedItem());
    }

    /**
     * Closed: the item isn't held (its callback can hold an activity)
     */
    @Test
    public void focusedItem_clearedWhenTheDialogCloses() {
        OptionItem opened = UiOptionItem.from("32.68.1", option -> {});

        mPresenter.appendSingleButton(opened);
        mPresenter.setFocusedItem(opened);
        mPresenter.showDialog("Release notes");
        mPresenter.onFinish();

        assertNull(mPresenter.getFocusedItem());
    }

    @Test
    public void focusedItem_noneByDefault() {
        mPresenter.appendSingleButton(UiOptionItem.from("Check for updates", option -> {}));
        mPresenter.showDialog("Updates");

        assertNull(mPresenter.getFocusedItem());
    }

    /**
     * The Debugging panel built again after a submission: in place of the one shown
     */
    @Test
    public void replace_ofTheDialogShown() {
        mPresenter.appendSingleButton(UiOptionItem.from("Submit data", option -> {}));
        mPresenter.enableReplace(true);
        mPresenter.showDialog("Debugging");

        assertTrue(mPresenter.isReplaceEnabled());
    }

    /**
     * Of one dialog only: the QR code opened next is over the panel
     */
    @Test
    public void replace_notKeptForTheNextDialog() {
        mPresenter.appendSingleButton(UiOptionItem.from("Submit data", option -> {}));
        mPresenter.enableReplace(true);
        mPresenter.showDialog("Debugging");

        mPresenter.appendQrCode("Submitted", "https://github.com");
        mPresenter.showDialog("Submit data");

        assertFalse(mPresenter.isReplaceEnabled());
    }

    @Test
    public void replace_offByDefault() {
        mPresenter.appendSingleButton(UiOptionItem.from("Check for updates", option -> {}));
        mPresenter.showDialog("Updates");

        assertFalse(mPresenter.isReplaceEnabled());
    }
}
