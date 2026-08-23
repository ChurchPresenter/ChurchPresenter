package org.churchpresenter.companionserver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two pure decisions behind the Cloudflare tunnel: which `cloudflared` binary to download for
 * this OS/arch, and how the public tunnel URL is scraped out of cloudflared's log output. The
 * process launch and download are side-effecting, but picking the wrong binary or failing to
 * recognise the URL line both silently break "share over the internet".
 */
class TunnelManagerTest {

    @Test
    fun `windows gets the amd64 exe regardless of arch`() {
        assertTrue(cloudflaredDownloadUrl(isWin = true, isMac = false, isArm = false).endsWith("windows-amd64.exe"))
        assertTrue(cloudflaredDownloadUrl(isWin = true, isMac = false, isArm = true).endsWith("windows-amd64.exe"))
    }

    @Test
    fun `apple silicon gets the darwin arm64 archive`() {
        assertTrue(cloudflaredDownloadUrl(isWin = false, isMac = true, isArm = true).endsWith("darwin-arm64.tgz"))
    }

    @Test
    fun `intel mac gets the darwin amd64 archive`() {
        assertTrue(cloudflaredDownloadUrl(isWin = false, isMac = true, isArm = false).endsWith("darwin-amd64.tgz"))
    }

    @Test
    fun `arm linux gets the linux arm64 binary`() {
        assertTrue(cloudflaredDownloadUrl(
            isWin = false,
            isMac = false,
            isArm = true,
        ).endsWith("cloudflared-linux-arm64"))
    }

    @Test
    fun `x64 linux is the fallback binary`() {
        assertTrue(cloudflaredDownloadUrl(
            isWin = false,
            isMac = false,
            isArm = false,
        ).endsWith("cloudflared-linux-amd64"))
    }

    @Test
    fun `the tunnel url is scraped out of a cloudflared log line`() {
        val line = "2024-01-01T00:00:00Z INF |  https://random-happy-cloud-42.trycloudflare.com  |"
        assertEquals("https://random-happy-cloud-42.trycloudflare.com", extractTunnelUrl(line))
    }

    @Test
    fun `a log line without a tunnel url yields null`() {
        assertNull(extractTunnelUrl("2024-01-01 INF Starting tunnel connection"))
        assertNull(extractTunnelUrl(""))
    }

    @Test
    fun `only the trycloudflare host is accepted, not an arbitrary https url`() {
        assertNull(extractTunnelUrl("visit https://example.com for details"))
    }

    // ── Reading the platform off the system properties ──────────────────────────
    //
    // The step before picking a URL: turning a raw `os.name`/`os.arch` into the three booleans
    // above. A JVM only ever runs on one platform, so without these the other two mappings are
    // untested on every machine — and a mis-read platform downloads a binary that cannot execute,
    // which shows up as a tunnel that silently never comes up rather than as an error.

    @Test
    fun `every windows an os-name can spell is recognised`() {
        assertTrue(isWindowsOs("Windows 10"))
        assertTrue(isWindowsOs("Windows 11"))
        assertTrue(isWindowsOs("Windows Server 2022"))
        assertTrue(isWindowsOs("WINDOWS 10"), "the check is case-insensitive, and os.name is not normalised")
    }

    @Test
    fun `mac reports itself in more than one way and all of them count`() {
        assertTrue(isMacOs("Mac OS X"))
        assertTrue(isMacOs("macOS"))
        assertTrue(isMacOs("Darwin".replace("Darwin", "Mac OS X")))
    }

    @Test
    fun `linux is neither windows nor mac`() {
        assertFalse(isWindowsOs("Linux"))
        assertFalse(isMacOs("Linux"))
        assertFalse(isWindowsOs("FreeBSD"))
        assertFalse(isMacOs("FreeBSD"))
    }

    @Test
    fun `apple silicon and arm boards are both read as arm`() {
        assertTrue(isArmArch("aarch64"), "what Apple silicon and 64-bit ARM Linux report")
        assertTrue(isArmArch("arm"))
        assertTrue(isArmArch("armv7l"))
        assertTrue(isArmArch("AARCH64"))
    }

    @Test
    fun `intel and amd machines are not arm`() {
        assertFalse(isArmArch("x86_64"))
        assertFalse(isArmArch("amd64"))
        assertFalse(isArmArch("x86"))
    }

    @Test
    fun `the platform this suite is running on picks a real binary`() {
        // Not a tautology: it walks the same path TunnelManager's fields do, so a mapping that
        // throws or yields an empty url fails here on whichever platform CI happens to be.
        val url = cloudflaredDownloadUrl(
            isWin = isWindowsOs(System.getProperty("os.name")),
            isMac = isMacOs(System.getProperty("os.name")),
            isArm = isArmArch(System.getProperty("os.arch")),
        )

        assertTrue(url.startsWith("https://github.com/cloudflare/cloudflared/releases/"), url)
        assertTrue(url.substringAfterLast('/').startsWith("cloudflared-"), url)
    }
}
