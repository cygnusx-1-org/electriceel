package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered sections of the navigation rail.<br/>
 * Index semantics match the classic headers: -1 or out of range appends.
 */
public class RailModel {
    private final List<BrowseSection> mSections = new ArrayList<>();

    public void add(int index, BrowseSection section) {
        remove(section.getId());

        if (index < 0 || index > mSections.size()) {
            mSections.add(section);
        } else {
            mSections.add(index, section);
        }
    }

    public void remove(int id) {
        int index = indexOf(id);

        if (index != -1) {
            mSections.remove(index);
        }
    }

    public void clear() {
        mSections.clear();
    }

    public int indexOf(int id) {
        for (int i = 0; i < mSections.size(); i++) {
            if (mSections.get(i).getId() == id) {
                return i;
            }
        }

        return -1;
    }

    public int size() {
        return mSections.size();
    }

    public BrowseSection get(int index) {
        return mSections.get(index);
    }
}
