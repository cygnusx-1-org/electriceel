package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.VisibleForTesting;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsPanel;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsRows;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppDialogPresenter extends BasePresenter<AppDialogView> implements SettingsRows {
    @SuppressLint("StaticFieldLeak")
    private static AppDialogPresenter sInstance;
    private final Handler mHandler;
    private final Runnable mCloseDialog = this::closeDialog;
    private final Set<Runnable> mOnStart = new HashSet<>();
    private final Set<Runnable> mOnFinish = new HashSet<>();
    private CharSequence mTitle;
    private long mTimeoutMs;
    private boolean mIsTransparent;
    private boolean mIsOverlay;
    private List<OptionCategory> mCategories;
    private boolean mIsExpandable = true;
    private int mId;
    private CharSequence mTitlePrefix;
    private boolean mIsNestedTitlesEnabled;
    private OptionItem mFocusedItem;
    private boolean mIsReplace;
    private ItemMatcher mFocusedItemMatcher;

    /**
     * A row of a dialog built by someone else, e.g. a setting the settings search opens
     */
    public interface ItemMatcher {
        boolean matches(OptionCategory category, OptionItem item);
    }

    private CharSequence mBackupTitle;
    private List<OptionCategory> mBackupCategories;
    private int mBackupId;
    private boolean mBackupIsTransparent;
    private boolean mBackupIsOverlay;
    private boolean mBackupIsExpandable;
    private CharSequence mBackupTitlePrefix;
    private boolean mBackupIsNestedTitlesEnabled;
    private OptionItem mBackupFocusedItem;
    private boolean mBackupIsReplace;

    public AppDialogPresenter(Context context) {
        super(context);
        mCategories = new ArrayList<>();
        mHandler = new Handler(Looper.getMainLooper());
    }

    public static AppDialogPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppDialogPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    /**
     * Called when user pressed back button.
     */
    @Override
    public void onFinish() {
        super.onFinish();
        Utils.runMyCallbacks(mOnFinish);
        clear();
    }

    private void clear() {
        mTimeoutMs = 0;
        mHandler.removeCallbacks(mCloseDialog);
        resetData();
        mBackupCategories = null; // Mem leak fix (UiOption callback holds PlaybackActivity)
        mBackupFocusedItem = null; // Same
        mOnStart.clear();
        mOnFinish.clear();
        mFocusedItemMatcher = null;
    }

    /**
     * Doubled items fix / Empty dialog fix (multiple overlapped dialogs)
     */
    private void backupData() {
        mBackupCategories = mCategories;
        mBackupTitle = mTitle;
        mBackupId = mId;
        mBackupIsExpandable = mIsExpandable;
        mBackupIsTransparent = mIsTransparent;
        mBackupIsOverlay = mIsOverlay;
        mBackupTitlePrefix = mTitlePrefix;
        mBackupIsNestedTitlesEnabled = mIsNestedTitlesEnabled;
        mBackupFocusedItem = mFocusedItem;
        mBackupIsReplace = mIsReplace;
    }

    private void resetData() {
        mCategories = new ArrayList<>();
        mIsExpandable = true;
        mIsTransparent = false;
        mIsOverlay = false;
        mId = 0;
        mTitle = null;
        mFocusedItem = null;
        mIsReplace = false;
    }

    @Override
    public void onViewInitialized() {
        getView().show(mBackupCategories, mBackupTitle, mBackupIsExpandable, mBackupIsTransparent, mBackupIsOverlay, mBackupId);
        Utils.runMyCallbacks(mOnStart);
    }

    /**
     * Called after {@link #onFinish}
     */
    @Override
    public void onViewDestroyed() {
        super.onViewDestroyed();
        clear();
    }

    public void showDialog() {
        showDialog(null, null);
    }

    public void showDialog(CharSequence dialogTitle) {
        showDialog(dialogTitle, null);
    }

    public void showDialog(Runnable onFinish) {
        showDialog(null, onFinish);
    }

    /**
     * The rows of the panel, then the panel shown (see SettingsPanel)
     */
    public void showPanel(CharSequence dialogTitle, SettingsPanel panel, Runnable onFinish) {
        panel.appendRows(this);
        showDialog(dialogTitle, onFinish);
    }

    public void showDialog(CharSequence dialogTitle, Runnable onFinish) {
        if (mFocusedItem == null && mFocusedItemMatcher != null) {
            mFocusedItem = findItem(mCategories, mFocusedItemMatcher);
        }

        // Of this dialog only
        mFocusedItemMatcher = null;

        mTitle = dialogTitle;
        mOnFinish.add(onFinish);
        
        backupData(); // overlapped dialog fix
        resetData(); // prepare to new call

        if (getView() != null) {
            onViewInitialized();
        }

        getViewManager().startView(AppDialogView.class, true);

        setupTimeout();
    }

    /**
     * The next dialog is titled "<prefix> - <title>", e.g. "Player - Video" for the Video card of the Player settings.
     * Null stops it.
     */
    public void setTitlePrefix(CharSequence prefix) {
        mTitlePrefix = prefix;
    }

    /**
     * The lists opened inside the next dialog are titled "<parent> - <title>", e.g. "General - Playback mode".
     * The rows that open them keep their own title.
     */
    public void enableNestedTitles(boolean enable) {
        mIsNestedTitlesEnabled = enable;
    }

    /**
     * The row of the next dialog that has the focus when it opens, instead of the first one: e.g. the one opened last,
     * when the dialog is opened again after it. Of the items appended to it.
     */
    @Override
    public void setFocusedItem(OptionItem item) {
        mFocusedItem = item;
    }

    /**
     * The row of the next dialog that has the focus when it opens: the first item it matches, of the ones appended to it.
     * For a dialog built by someone else (e.g. a setting the settings search opens), whose items are made when it's shown.
     */
    public void setFocusedItemMatcher(ItemMatcher matcher) {
        mFocusedItemMatcher = matcher;
    }

    /**
     * Forgets what was appended for a dialog that wasn't shown (e.g. a panel that failed while the settings search looked it
     * through): the next dialog starts empty
     */
    public void clearPending() {
        resetData();
        mFocusedItemMatcher = null;
    }

    /**
     * @return the first item it matches, null for none
     */
    public static OptionItem findItem(List<OptionCategory> categories, ItemMatcher matcher) {
        for (OptionCategory category : categories) {
            if (category.options == null) {
                continue;
            }

            for (OptionItem item : category.options) {
                if (matcher.matches(category, item)) {
                    return item;
                }
            }
        }

        return null;
    }

    /**
     * Of the dialog shown: null for the first row
     */
    public OptionItem getFocusedItem() {
        return mBackupFocusedItem;
    }

    /**
     * The next dialog takes the place of the lists shown, the ones Back goes to included, instead of opening over them:
     * e.g. a list built again after a change in it. Of a list (not of a single category opened right away).
     */
    public void enableReplace(boolean enable) {
        mIsReplace = enable;
    }

    /**
     * Of the dialog shown
     */
    public boolean isReplaceEnabled() {
        return mBackupIsReplace;
    }

    /**
     * Of the dialog shown: its rows
     */
    @VisibleForTesting
    public List<OptionCategory> getCategories() {
        return mBackupCategories;
    }

    /**
     * Of the dialog shown
     */
    public CharSequence getTitlePrefix() {
        return mBackupTitlePrefix;
    }

    /**
     * Of the dialog shown
     */
    public boolean isNestedTitlesEnabled() {
        return mBackupIsNestedTitlesEnabled;
    }

    public void closeDialog() {
        if (getView() != null) {
            getView().finish();
        }
    }

    public void goBack() {
        if (getView() != null) {
            getView().goBack();
        }
    }

    public void clearBackstack() {
        if (getView() != null) {
            getView().clearBackstack();
        }
    }

    public boolean isDialogShown() {
        // Also check that current dialog almost closed (new view start is pending from a menu item)
        // Hmm. Maybe current dialog is pending. Check that view is null.
        // Also check that we aren't started the same view (nested dialog).
        return (getViewManager().isVisible(getView()) && getView() != null && !getView().isPaused()) ||
                getViewManager().isViewPending(AppDialogView.class);
    }

    public boolean isCommentsDialogShown() {
        return isDialogShown() && isTypeComments();
    }

    @Override
    public void appendCategory(OptionCategory category) {
        mCategories.add(category);
    }

    public void showDialogMessage(String dialogTitle, Runnable onClose, int timeoutMs) {
        showDialog(dialogTitle, onClose);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (getView() != null) {
                getView().finish();
            }
        }, timeoutMs);
    }

    public void setCloseTimeoutMs(long timeoutMs) {
        mTimeoutMs = timeoutMs;
    }

    public void setOnStart(Runnable onStart) {
        Utils.addMyCallback(mOnStart, onStart);
    }

    public void setOnFinish(Runnable onFinish) {
        Utils.addMyCallback(mOnFinish, onFinish);
    }

    public void enableTransparent(boolean enable) {
        mIsTransparent = enable;
    }

    /**
     * Close the dialog on movements keys
     */
    public void enableOverlay(boolean enable) {
        mIsOverlay = enable;
    }

    public boolean isTransparent() {
        return getView() != null && getView().isTransparent();
    }

    public boolean isOverlay() {
        return getView() != null && getView().isOverlay();
    }

    public boolean isComments() {
        if (mBackupCategories == null || mBackupCategories.isEmpty()) {
            return false;
        }

        OptionCategory optionCategory = mBackupCategories.get(0);

        return optionCategory.type == OptionCategory.TYPE_COMMENTS;
    }

    /**
     * Show a category contents instead of title if a single category has been added
     */
    public void enableExpandable(boolean enable) {
        mIsExpandable = enable;
    }

    public void setId(int id) {
        mId = id;
    }

    public int getId() {
        return getView() != null ? getView().getViewId() : mId;
    }

    public boolean isEmpty() {
        return mCategories == null || mCategories.isEmpty();
    }

    private void setupTimeout() {
        mHandler.removeCallbacks(mCloseDialog);

        if (mTimeoutMs > 0) {
            mHandler.postDelayed(mCloseDialog, mTimeoutMs);
        }
    }

    private boolean isTypeComments() {
        if (mBackupCategories == null) {
            return false;
        }

        boolean isComments = false;

        for (OptionCategory category : mBackupCategories) {
            if (category.type == OptionCategory.TYPE_COMMENTS) {
                isComments = true;
                break;
            }
        }

        return isComments;
    }
}
