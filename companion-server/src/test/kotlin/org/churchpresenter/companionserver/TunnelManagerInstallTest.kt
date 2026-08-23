package org.churchpresenter.companionserver

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TunnelManagerInstallTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-tunnel-install").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun file(name: String, bytes: ByteArray = byteArrayOf(1, 2, 3)) =
        File(dir, name).apply { writeBytes(bytes) }

    @Test
    fun `a downloaded binary is moved into place`() {
        val downloaded = file("cloudflared.tmp")
        val target = File(dir, "cloudflared")

        moveBinaryIntoPlace(downloaded, target)

        assertTrue(target.exists())
        assertFalse(downloaded.exists(), "the temporary download must not be left behind")
        assertContentEquals(byteArrayOf(1, 2, 3), target.readBytes())
    }

    @Test
    fun `an existing binary is replaced by the new one`() {
        val downloaded = file("cloudflared.tmp", byteArrayOf(9, 9))
        val target = file("cloudflared", byteArrayOf(1, 1))

        moveBinaryIntoPlace(downloaded, target)

        assertContentEquals(byteArrayOf(9, 9), target.readBytes(), "the update must actually replace the old build")
        assertFalse(downloaded.exists())
    }

    @Test
    fun `a download that is not there fails rather than leaving a half-install`() {
        val missing = File(dir, "never-downloaded.tmp")
        val target = File(dir, "cloudflared")

        assertFailsWith<IOException> { moveBinaryIntoPlace(missing, target) }
        assertFalse(target.exists())
    }

    @Test
    fun `a clean extraction that produced the binary is accepted`() {
        checkExtracted(exitCode = 0, binaryExists = true)
    }

    @Test
    fun `an extraction that failed is refused`() {
        val error = assertFailsWith<IOException> { checkExtracted(exitCode = 2, binaryExists = true) }

        assertTrue(error.message.orEmpty().contains("2"), "the exit code has to reach the operator: ${error.message}")
    }

    @Test
    fun `an extraction that produced nothing is refused even when it claimed success`() {
        assertFailsWith<IOException> { checkExtracted(exitCode = 0, binaryExists = false) }
    }

    @Test
    fun `a tunnel that was connected and then exited reports a disconnection`() {
        val status = tunnelExitStatus(foundUrl = true, current = TunnelStatus.Connected("https://x.trycloudflare.com"))

        assertEquals(TunnelStatus.Error("Tunnel disconnected"), status)
    }

    @Test
    fun `a tunnel that never produced a url reports a failure to start`() {
        assertEquals(TunnelStatus.Error("Tunnel failed to start"), tunnelExitStatus(false, TunnelStatus.Starting))
    }

    @Test
    fun `a tunnel stopped by the operator is not reported as an error`() {
        assertNull(
            tunnelExitStatus(foundUrl = true, current = TunnelStatus.Idle),
            "stop() sets Idle first, so this exit is the one the operator asked for",
        )
    }

    @Test
    fun `a tunnel that failed after producing a url is not reported twice`() {
        assertNull(tunnelExitStatus(foundUrl = true, current = TunnelStatus.Error("Tunnel disconnected")))
    }

    @Test
    fun `a url is picked out of a cloudflared log line`() {
        val line = "2026-08-14T20:00:00Z INF |  https://calm-river-1234.trycloudflare.com  |"

        assertEquals("https://calm-river-1234.trycloudflare.com", extractTunnelUrl(line))
    }

    @Test
    fun `a log line with no url in it yields nothing`() {
        assertNull(extractTunnelUrl("2026-08-14T20:00:00Z INF Registered tunnel connection"))
    }
}
