package org.churchpresenter.updater

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertNull
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

    @Suppress("MaxLineLength")
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
    fun `release notes of a real release's length come through whole`() {
        val notes = (1..11).joinToString("\n\n") { group ->
            val bullets = (1..10).joinToString("\n") { "- A change of a realistic length, number $it (#$group$it)" }
            "**Group $group**\n$bullets"
        }
        val result = select(releases(release("v26.2.0", notes = notes)))
        val available = assertIs<UpdateCheckResult.Available>(result)
        assertEquals(notes, available.info.releaseNotes, "every group and every bullet reaches the window")
    }

    @Test
    fun `notes over the ceiling are cut back to the last whole line`() {
        val capped = capReleaseNotes("- first line\n- second line\n- third line", max = 20)
        assertEquals("- first line", capped, "no bullet is shown half-written")
    }

    @Test
    fun `the offer carries when it was released, how big its installer is and the version it replaces`() {
        val sized = """[
            {"browser_download_url":"https://example.org/app.msi","size":634941440},
            {"browser_download_url":"https://example.org/app-arm64.dmg","size":601405516},
            {"browser_download_url":"https://example.org/app.dmg","size":620001927},
            {"browser_download_url":"https://example.org/app.deb","size":597752606}
        ]"""
        val dated = release("v26.2.0", assets = sized).replace(
            "\"tag_name\"",
            "\"published_at\":\"2026-10-08T09:01:10Z\",\"tag_name\"",
        )
        val info = assertIs<UpdateCheckResult.Available>(select(releases(dated), current = "26.1.0")).info
        assertEquals(Instant.parse("2026-10-08T09:01:10Z"), info.publishedAt)
        assertEquals("26.1.0", info.currentVersion)
        val sizes = mapOf(
            "https://example.org/app.msi" to 634_941_440L,
            "https://example.org/app-arm64.dmg" to 601_405_516L,
            "https://example.org/app.dmg" to 620_001_927L,
            "https://example.org/app.deb" to 597_752_606L,
        )
        assertEquals(sizes[info.downloadUrl], info.downloadSize, "the size is the chosen installer's own")
    }

    @Test
    fun `a release that gives no date or size offers neither rather than a wrong one`() {
        val info = assertIs<UpdateCheckResult.Available>(select(releases(release("v26.2.0")))).info
        assertNull(info.publishedAt)
        assertNull(info.downloadSize)
    }

    @Test
    fun `absent release notes become empty rather than null`() {
        val result = select(releases(release("v26.2.0", notes = null)))
        val available = assertIs<UpdateCheckResult.Available>(result)
        assertEquals("", available.info.releaseNotes)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `a release with no assets field at all is skipped`() {
        val body =
            """[{"tag_name":"v26.2.0","draft":false,"prerelease":false,"html_url":"https://example.org/r","body":"n"}]"""

        assertIs<UpdateCheckResult.UpToDate>(select(body))
    }

    @Test
    fun `an asset with no download url does not count as an installer`() {
        val body = releases(release("v26.2.0", assets = """[{"name":"app.msi"}]"""))

        assertIs<UpdateCheckResult.UpToDate>(select(body))
    }

    @Test
    fun `a release whose assets list is empty is skipped`() {
        assertIs<UpdateCheckResult.UpToDate>(select(releases(release("v26.2.0", assets = "[]"))))
    }

    @Test
    fun `a draft that is also newer is still skipped`() {
        assertIs<UpdateCheckResult.UpToDate>(select(releases(release("v99.0.0", draft = true))))
    }

    @Test
    fun `the first offerable release wins when several are newer`() {
        val result = select(releases(release("v26.3.0"), release("v26.2.0")))

        assertEquals("26.3.0", assertIs<UpdateCheckResult.Available>(result).info.latestVersion)
    }

    @Test
    fun `a release whose flags are not true or false is treated as a normal release`() {
        val oddFlags = """{"tag_name":"v26.2.0","draft":"maybe","prerelease":"maybe","assets":$allInstallers}"""
        val available = assertIs<UpdateCheckResult.Available>(select(releases(oddFlags)))
        assertEquals(false, available.info.isPrerelease)
    }

    @Test
    fun `a release with no flags at all, no page and no notes still offers its installer`() {
        val bare = """{"tag_name":"v26.2.0","assets":$allInstallers}"""
        val available = assertIs<UpdateCheckResult.Available>(select(releases(bare)))
        assertTrue(available.info.releaseNotes.isEmpty())
    }

    @Test
    fun `a rolling tag in front of a real release is stepped over`() {
        val body = releases(release("nightly", prerelease = true), release("v26.3.0"))
        val result = select(body, includePrereleases = true)
        assertEquals("26.3.0", assertIs<UpdateCheckResult.Available>(result).info.latestVersion)
    }

    @Test
    fun `a release with no tag or no installers is not offered`() {
        val untagged = """{"assets":$allInstallers}"""
        val noAssets = """{"tag_name":"v26.2.0"}"""
        assertIs<UpdateCheckResult.UpToDate>(select(releases(untagged, noAssets)))
    }
}
