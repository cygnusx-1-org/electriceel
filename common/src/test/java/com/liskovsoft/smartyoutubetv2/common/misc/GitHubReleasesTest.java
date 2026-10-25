package com.liskovsoft.smartyoutubetv2.common.misc;

import com.liskovsoft.smartyoutubetv2.common.misc.GitHubReleases.Release;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GitHubReleasesTest {
    @Test
    public void parse_keepsTheOrderOfGitHub() throws IOException {
        List<Release> releases = GitHubReleases.parse("[" +
                "{\"tag_name\":\"32.68.3\",\"html_url\":\"https://github.com/o/r/releases/tag/32.68.3\"," +
                "\"body_html\":\"<p>New</p>\",\"published_at\":\"2026-10-25T01:14:33Z\"}," +
                "{\"tag_name\":\"32.68.2\",\"body_html\":\"<p>Old</p>\",\"published_at\":\"2026-10-18T10:08:25Z\"}]");

        assertEquals(2, releases.size());
        assertEquals("32.68.3", releases.get(0).version);
        assertEquals("https://github.com/o/r/releases/tag/32.68.3", releases.get(0).pageUrl);
        assertEquals("<p>New</p>", releases.get(0).bodyHtml);
        assertEquals(1792890873000L, releases.get(0).publishedMs);
        assertEquals("32.68.2", releases.get(1).version);
    }

    @Test
    public void parse_leavesOutDraftsAndUntagged() throws IOException {
        List<Release> releases = GitHubReleases.parse("[" +
                "{\"tag_name\":\"33.0\",\"draft\":true}," +
                "{\"tag_name\":\"\"}," +
                "{\"name\":\"No tag\"}," +
                "{\"tag_name\":\"32.68.3\"}]");

        assertEquals(1, releases.size());
        assertEquals("32.68.3", releases.get(0).version);
    }

    /**
     * Without notes or a date (e.g. published without a body): nothing in them, not "null"
     */
    @Test
    public void parse_missingFieldsAreEmpty() throws IOException {
        Release release = GitHubReleases.parse("[{\"tag_name\":\"32.68.3\",\"body_html\":null,\"published_at\":null}]").get(0);

        assertEquals("", release.pageUrl);
        assertEquals("", release.bodyHtml);
        assertEquals(0, release.publishedMs);
    }

    @Test(expected = IOException.class)
    public void parse_notAList() throws IOException {
        // E.g. the API's error: {"message":"API rate limit exceeded ..."}
        GitHubReleases.parse("{\"message\":\"API rate limit exceeded\"}");
    }

    @Test
    public void parseDate_isUtc() {
        assertEquals(0L, GitHubReleases.parseDate("1970-01-01T00:00:00Z"));
        assertEquals(1792890873000L, GitHubReleases.parseDate("2026-10-25T01:14:33Z"));
    }

    @Test
    public void parseDate_invalidIsZero() {
        assertEquals(0L, GitHubReleases.parseDate(""));
        assertEquals(0L, GitHubReleases.parseDate("yesterday"));
    }
}
