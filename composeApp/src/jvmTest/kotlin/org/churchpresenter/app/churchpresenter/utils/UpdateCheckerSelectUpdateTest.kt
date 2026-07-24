package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [UpdateChecker.selectUpdate] is the decision the network call feeds into: given GitHub's releases
 * JSON, which release (if any) do we offer. It walks newest→oldest and stops at the first release
 * that both has an installer for this OS and is newer than the running build; drafts and (unless
 * asked) prereleases are skipped, and anything malformed degrades to "up to date" rather than
 * throwing on the network path. Each release fixture carries all four platform installers, so the
 * asset match succeeds regardless of which OS the test runs on.
 */
class UpdateCheckerSelectUpdateTest {

    private val allInstallers = """[
        {"browser_download_url":"https://example.org/app.msi"},
        {"browser_download_url":"https://example.org/app-arm64.dmg"},
        {"browser_download_url":"https://example.org/app.dmg"},
        {"browser_download_url":"https://example.org/app.deb"}
    ]"""

    private fun release(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        htmlUrl: String? = "https://example.org/release-page",
        notes: String? = "Release notes",
        assets: String = allInstallers,
    ): String {
        val html = htmlUrl?.let { "\"$it\"" } ?: "null"
        val body = notes?.let { "\"$it\"" } ?: "null"
        return """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,"html_url":$html,"body":$body,"assets":$assets}"""
    }

    private fun releases(vararg entries: String) = "[" + entries.joinToString(",") + "]"

    private fun select(body: String, includePrereleases: Boolean = false, current: String = "26.1.0") =
        UpdateChecker.selectUpdate(body, includePrereleases, current)

    @Test
    fun `a newer OS-matching release is offered with its installer`() {
        val result = select(releases(release("v26.2.0")))
        val available = assertIs<UpdateCheckResult.Available>(result)
        assertEquals("26.2.0", available.info.latestVersion)
        assertNotNull(available.info.downloadUrl, "an offered release must carry a concrete installer url")
        assertEquals(false, available.info.isPrerelease)
        assertEquals("https://example.org/release-page", available.info.releaseUrl)
    }

    @Test
    fun `an equal or older latest release reads as up to date`() {
        assertIs<UpdateCheckResult.UpToDate>(select(releases(release("v26.1.0"))))
        assertIs<UpdateCheckResult.UpToDate>(select(releases(release("v25.9.9"))))
    }

    @Test
    fun `a draft release is skipped`() {
        // Only a (newer) draft is present, so the walk finds nothing installable.
        assertIs<UpdateCheckResult.UpToDate>(select(releases(release("v27.0.0", draft = true))))
    }

    @Test
    fun `a prerelease is skipped unless prereleases are requested`() {
        assertIs<UpdateCheckResult.UpToDate>(
            select(releases(release("v27.0.0", prerelease = true)), includePrereleases = false),
        )
    }

    @Test
    fun `a prerelease is offered when prereleases are requested`() {
        val result = select(releases(release("v27.0.0", prerelease = true)), includePrereleases = true)
        val available = assertIs<UpdateCheckResult.Available>(result)
        assertEquals("27.0.0", available.info.latestVersion)
        assertTrue(available.info.isPrerelease)
    }

    @Test
    fun `a release without an installer for this OS is skipped for the next one`() {
        val result = select(releases(release("v27.0.0", assets = "[]"), release("v26.5.0")))
        val available = assertIs<UpdateCheckResult.Available>(result)
        assertEquals("26.5.0", available.info.latestVersion, "the newer release had no asset, so the next wins")
    }

    @Test
    fun `a release missing its tag is skipped`() {
        val noTag = """{"draft":false,"prerelease":false,"html_url":"h","body":"n","assets":$allInstallers}"""
        assertIs<UpdateCheckResult.UpToDate>(select(releases(noTag)))
    }

    @Test
    fun `an empty release list is up to date`() {
        assertIs<UpdateCheckResult.UpToDate>(select("[]"))
    }

    @Test
    fun `malformed json degrades to up to date rather than throwing`() {
        assertIs<UpdateCheckResult.UpToDate>(select("not json at all"))
        assertIs<UpdateCheckResult.UpToDate>(select("""{"unexpected":"object"}"""))
    }

    @Test
    fun `a missing html_url falls back to the releases page`() {
        val result = select(releases(release("v26.2.0", htmlUrl = null)))
        val available = assertIs<UpdateCheckResult.Available>(result)
        assertEquals(UpdateChecker.RELEASES_URL, available.info.releaseUrl)
    }

    @Test
    fun `release notes are capped at 500 characters`() {
        val result = select(releases(release("v26.2.0", notes = "x".repeat(600))))
        val available = assertIs<UpdateCheckResult.Available>(result)
        assertEquals(500, available.info.releaseNotes.length)
    }

    @Test
    fun `absent release notes become empty rather than null`() {
        val result = select(releases(release("v26.2.0", notes = null)))
        val available = assertIs<UpdateCheckResult.Available>(result)
        assertEquals("", available.info.releaseNotes)
    }
}
