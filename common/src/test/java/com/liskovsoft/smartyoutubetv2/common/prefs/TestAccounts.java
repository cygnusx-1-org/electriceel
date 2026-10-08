package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

/**
 * Signed in accounts for the settings each account has its own of: only the name matters
 */
final class TestAccounts {
    static final Account FIRST = named("First");
    static final Account SECOND = named("Second");

    private TestAccounts() {
    }

    /**
     * AppPrefs outlives the test, so do the saved values: the shared ones and the ones of each account the tests use
     */
    static void clearSaved(AppPrefs prefs, String key) {
        prefs.setData(key, "");
        prefs.setData("anonymous_" + key, "");
        prefs.setData(FIRST.getName() + "_" + key, "");
        prefs.setData(SECOND.getName() + "_" + key, "");
    }

    static Account named(String name) {
        return new Account() {
            @Override
            public int getId() {
                return name.hashCode();
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getEmail() {
                return null;
            }

            @Override
            public String getAvatarImageUrl() {
                return null;
            }

            @Override
            public boolean isSelected() {
                return true;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }
        };
    }
}
