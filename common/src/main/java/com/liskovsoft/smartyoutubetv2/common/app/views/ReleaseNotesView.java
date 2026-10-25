package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.smartyoutubetv2.common.misc.GitHubReleases.Release;

/**
 * The notes of a release on the whole screen, as on GitHub (see ReleaseNotesPresenter)
 */
public interface ReleaseNotesView {
    void update(Release release);
}
