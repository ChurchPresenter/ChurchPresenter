package org.churchpresenter.companionserver

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
