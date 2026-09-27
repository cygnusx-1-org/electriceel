package com.liskovsoft.smartyoutubetv2.common.prefs.common;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs.ProfileChangeListener;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class DataSaverBase extends DataChangeBase {
    private final AppPrefs mAppPrefs;
    private final boolean mPersistImmediately;
    private final boolean mIsProfileData;
    private final String mDataKey;
    private final List<Object> mValues;
    private final Runnable mPersistStateInt = this::persistStateInt;
    private final ProfileChangeListener mOnProfileChanged = this::onProfileChanged;

    private interface Converter {
        Object convert(String input);
    }

    public DataSaverBase(Context context) {
        this(context, false);
    }

    public DataSaverBase(Context context, boolean persistImmediately) {
        this(context, persistImmediately, false);
    }

    /**
     * @param isProfileData each account has its own values while "Use separate settings per each account" is on
     */
    public DataSaverBase(Context context, boolean persistImmediately, boolean isProfileData) {
        mAppPrefs = AppPrefs.instance(context);
        mPersistImmediately = persistImmediately;
        mIsProfileData = isProfileData;
        mDataKey = this.getClass().getSimpleName();
        mValues = new ArrayList<>();
        restoreState();

        if (isProfileData) {
            mAppPrefs.addListener(mOnProfileChanged);
        }
    }

    protected boolean getBoolean(int index) {
        return getBoolean(index, false);
    }

    protected boolean getBoolean(int index, boolean defaultValue) {
        return getValue(index, defaultValue, Helpers::parseBoolean);
    }

    protected void setBoolean(int index, boolean value) {
        setValue(index, value);
    }

    protected int getInt(int index) {
        return getInt(index, -1);
    }

    protected int getInt(int index, int defaultValue) {
        return getValue(index, defaultValue, Helpers::parseInt);
    }

    protected void setInt(int index, int value) {
        setValue(index, value);
    }

    @SuppressWarnings("unchecked")
    private <T> T getValue(int index, T defaultValue, Converter converter) {
        if (index >= mValues.size() || mValues.get(index) == null) {
            return defaultValue;
        }

        Object rawValue = mValues.get(index);
        if (rawValue instanceof String) {
            Object value = converter.convert((String) rawValue);
            mValues.set(index, value);
            return (T) value;
        } else {
            return (T) rawValue;
        }
    }

    private <T> void setValue(int index, T value) {
        checkCapacity(index);
        mValues.set(index, value);
        persistState();
    }

    private void checkCapacity(int index) {
        int size = mValues.size();
        if (size <= index) { // fill with nulls
            for (int i = size; i <= index; i++) {
                mValues.add(null);
            }
        }
    }

    private void restoreState() {
        String data = mIsProfileData ? mAppPrefs.getProfileData(mDataKey) : mAppPrefs.getData(mDataKey);

        String[] split = Helpers.splitData(data);

        if (split != null) {
            mValues.addAll(Arrays.asList(split));
        }
    }

    public void persistNow() {
        Utils.post(mPersistStateInt);
    }

    private void persistState() {
        onDataChange();

        if (mPersistImmediately) {
            Utils.post(mPersistStateInt);
        } else {
            Utils.postDelayed(mPersistStateInt, 10_000);
        }
    }

    private void persistStateInt() {
        String data = Helpers.mergeData(mValues.toArray());

        if (mIsProfileData) {
            mAppPrefs.setProfileData(mDataKey, data);
        } else {
            mAppPrefs.setData(mDataKey, data);
        }
    }

    /**
     * The account (or the per account switch) has changed, the values are the new account's
     */
    private void onProfileChanged() {
        // Already the new account: a pending save would put the old values there
        Utils.removeCallbacks(mPersistStateInt);
        mValues.clear();
        restoreState();
        onDataChange();
    }
}
