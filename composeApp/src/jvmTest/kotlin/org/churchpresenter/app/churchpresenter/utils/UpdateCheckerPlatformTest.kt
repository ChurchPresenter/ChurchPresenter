package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Installer selection for the in-app updater. Picking the wrong asset hands the user an installer
 * that won't run on their machine — an Intel Mac offered the arm64 .dmg, or a Windows user offered
 * a .deb. Both helpers are private and read `os.name`/`os.arch` at call time, so each case is
 * driven by temporarily overriding those properties.
 */
class UpdateCheckerPlatformTest {

    private val realOsName: String? = System.getProperty("os.name")
    private val realOsArch: String? = System.getProperty("os.arch")

    @AfterTest
    fun restorePlatform() {
        realOsName?.let { System.setProperty("os.name", it) }
        realOsArch?.let { System.setProperty("os.arch", it) }
    }

    private fun asPlatform(osName: String, osArch: String) {
        System.setProperty("os.name", osName)
        System.setProperty("os.arch", osArch)
    }

    @Suppress("UNCHECKED_CAST")
    private fun selectDownloadUrl(urls: List<String>): String? {
        val method = UpdateChecker::class.java
            .getDeclaredMethod("selectDownloadUrl", List::class.java)
            .apply { isAccessible = true }
        return method.invoke(UpdateChecker, urls) as String?
    }

    private fun currentPlatformId(): String {
        val method = UpdateChecker::class.java
            .getDeclaredMethod("currentPlatformId")
            .apply { isAccessible = true }
        return method.invoke(UpdateChecker) as String
    }

    /** The four assets a release publishes, in an arbitrary order. */
    private val releaseAssets = listOf(
        "https://example.org/ChurchPresenter-26.1.0.deb",
        "https://example.org/ChurchPresenter-26.1.0-arm64.dmg",
        "https://example.org/ChurchPresenter-26.1.0.msi",
        "https://example.org/ChurchPresenter-26.1.0.dmg",
    )

    @Test
    fun `windows gets the msi`() {
        asPlatform("Windows 11", "amd64")
        assertEquals("https://example.org/ChurchPresenter-26.1.0.msi", selectDownloadUrl(releaseAssets))
        assertEquals("windows", currentPlatformId())
    }

    @Test
    fun `apple silicon gets the arm64 dmg`() {
        asPlatform("Mac OS X", "aarch64")
        assertEquals("https://example.org/ChurchPresenter-26.1.0-arm64.dmg", selectDownloadUrl(releaseAssets))
        assertEquals("macos_arm64", currentPlatformId())
    }

    @Test
    fun `intel mac gets the non-arm64 dmg`() {
        asPlatform("Mac OS X", "x86_64")
        // The important half: it must NOT hand an Intel Mac the arm64 build.
        assertEquals("https://example.org/ChurchPresenter-26.1.0.dmg", selectDownloadUrl(releaseAssets))
        assertEquals("macos_x64", currentPlatformId())
    }

    @Test
    fun `linux gets the deb`() {
        asPlatform("Linux", "amd64")
        assertEquals("https://example.org/ChurchPresenter-26.1.0.deb", selectDownloadUrl(releaseAssets))
        assertEquals("linux", currentPlatformId())
    }

    @Test
    fun `an unrecognised os falls through to the linux deb`() {
        asPlatform("FreeBSD", "amd64")
        assertEquals("https://example.org/ChurchPresenter-26.1.0.deb", selectDownloadUrl(releaseAssets))
        assertEquals("linux", currentPlatformId())
    }

    @Test
    fun `extension matching is case-insensitive`() {
        asPlatform("Windows 10", "amd64")
        assertEquals("https://example.org/Setup.MSI", selectDownloadUrl(listOf("https://example.org/Setup.MSI")))
    }

    @Test
    fun `a release missing this platform's asset yields null rather than a wrong installer`() {
        asPlatform("Windows 11", "amd64")
        val noMsi = releaseAssets.filterNot { it.endsWith(".msi") }
        assertNull(selectDownloadUrl(noMsi), "better no download than the wrong one")

        asPlatform("Mac OS X", "aarch64")
        val noArm = releaseAssets.filterNot { it.contains("arm64") }
        assertNull(selectDownloadUrl(noArm), "an arm64 Mac must not be given the x64 dmg")
    }

    @Test
    fun `an empty asset list yields null on every platform`() {
        for ((os, arch) in listOf("Windows 11" to "amd64", "Mac OS X" to "aarch64", "Mac OS X" to "x86_64", "Linux" to "amd64")) {
            asPlatform(os, arch)
            assertNull(selectDownloadUrl(emptyList()), "$os/$arch")
        }
    }

    @Test
    fun `platform id matches the branch that selected the installer`() {
        // The beacon must report the platform whose installer is actually being downloaded,
        // so the two helpers have to agree on every branch.
        val expectations = listOf(
            Triple("Windows 11", "amd64", "windows"),
            Triple("Mac OS X", "aarch64", "macos_arm64"),
            Triple("Mac OS X", "x86_64", "macos_x64"),
            Triple("Linux", "amd64", "linux"),
        )
        for ((os, arch, id) in expectations) {
            asPlatform(os, arch)
            assertEquals(id, currentPlatformId())
        }
    }
}
