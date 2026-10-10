package org.churchpresenter.server

import java.io.IOException
import java.security.MessageDigest
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The pinned cloudflared release, and the digest check every download passes before it can run. */
class CloudflaredPinsTest {

    private val pins = CloudflaredPins.load()

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `every platform's asset has a pinned digest`() {
        val assets = listOf(true, false).flatMap { win ->
            listOf(true, false).flatMap { mac -> listOf(true, false).map { arm -> cloudflaredAsset(win, mac, arm) } }
        }.toSet()

        assets.forEach { asset ->
            assertTrue(Regex("[0-9a-f]{64}").matches(pins.sha256(asset)), "no SHA-256 pinned for $asset")
        }
    }

    @Test
    fun `the download is a versioned release, never latest`() {
        val url = pins.downloadUrl("cloudflared-linux-amd64")

        assertTrue(Regex("\\d{4}\\.\\d+\\.\\d+").matches(pins.version), pins.version)
        assertEquals(
            "https://github.com/cloudflare/cloudflared/releases/download/${pins.version}/cloudflared-linux-amd64",
            url,
        )
        assertFalse("latest" in url)
    }

    @Test
    fun `bytes matching the pin are handed back`() {
        val bytes = byteArrayOf(1, 2, 3)

        assertContentEquals(bytes, verifiedDownload(bytes, sha256(bytes)))
        assertContentEquals(bytes, verifiedDownload(bytes, sha256(bytes).uppercase()), "hex case does not matter")
    }

    @Test
    fun `a tampered download is refused`() {
        val pinned = sha256(byteArrayOf(1, 2, 3))

        val e = assertFailsWith<IOException> { verifiedDownload(byteArrayOf(1, 2, 4), pinned) }
        assertTrue("verification" in e.message.orEmpty(), e.message)
    }

    @Test
    fun `a build with no pinned digest is refused`() {
        assertFailsWith<IOException> { verifiedDownload(byteArrayOf(1), "") }
        assertEquals("", CloudflaredPins(Properties()).sha256("cloudflared-linux-amd64"))
    }

    @Test
    fun `cloudflared is fetched when missing or installed from another pin`() {
        val pin = "2026.9.3"
        assertTrue(needsCloudflaredDownload(binaryExists = false, installedVersion = pin, pinnedVersion = pin))
        assertTrue(needsCloudflaredDownload(binaryExists = true, installedVersion = null, pinnedVersion = pin))
        assertTrue(needsCloudflaredDownload(binaryExists = true, installedVersion = "2025.1.0", pinnedVersion = pin))
        assertFalse(needsCloudflaredDownload(binaryExists = true, installedVersion = "$pin\n", pinnedVersion = pin))
    }
}
