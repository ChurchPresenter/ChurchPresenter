package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The small value types around the update check, plus the dev-build guard on the download beacon.
 */
class UpdateCheckerModelTest {

    @Test
    fun `UpdateInfo leaves the installer url absent and the release stable by default`() {
        val info = UpdateInfo(latestVersion = "26.2.0", releaseUrl = "https://example.org/rel", releaseNotes = "notes")
        assertNull(info.downloadUrl, "a release with no matching installer carries a null download url")
        assertFalse(info.isPrerelease)
    }

    @Test
    fun `UpdateInfo keeps every field it is given`() {
        val info = UpdateInfo(
            latestVersion = "26.2.0",
            releaseUrl = "https://example.org/rel",
            releaseNotes = "notes",
            downloadUrl = "https://example.org/app.dmg",
            isPrerelease = true,
        )
        assertEquals("26.2.0", info.latestVersion)
        assertEquals("https://example.org/rel", info.releaseUrl)
        assertEquals("notes", info.releaseNotes)
        assertEquals("https://example.org/app.dmg", info.downloadUrl)
        assertTrue(info.isPrerelease)
    }

    @Test
    fun `two UpdateInfos with the same fields are equal`() {
        val a = UpdateInfo("26.2.0", "u", "n")
        assertEquals(a, UpdateInfo("26.2.0", "u", "n"))
        assertEquals(a.hashCode(), UpdateInfo("26.2.0", "u", "n").hashCode())
    }

    @Test
    fun `copy overrides a single field`() {
        val info = UpdateInfo("26.2.0", "u", "n")
        assertTrue(info.copy(isPrerelease = true).isPrerelease)
        assertEquals("https://example.org/app.msi", info.copy(downloadUrl = "https://example.org/app.msi").downloadUrl)
    }

    @Test
    fun `reportDownloadStarted is a no-op on a dev build`() {
        UpdateChecker.reportDownloadStarted("26.2.0")
    }
}
