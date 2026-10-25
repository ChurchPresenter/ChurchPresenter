package org.churchpresenter.signage.hub

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Platform
import java.io.IOException
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class AgentUpdatesTest {
    private val dir = createTempDirectory("signage-updates")
    private val release = TestRelease()
    private var served = release.files()
    private val fetched = mutableListOf<String>()

    @AfterTest
    fun cleanUp() = dir.deleteRecursively()

    private fun updates() = AgentUpdates(
        dir.resolve("cache"),
        baseUrl = "https://example.test/latest",
        publicKey = release.publicKey,
    ) { url, consume ->
        fetched += url
        val bytes = served[url.substringAfterLast('/')] ?: throw IOException("HTTP 404 for $url")
        bytes.inputStream().use(consume)
    }

    private fun screen(version: String = "26.0.8", platform: Platform = Platform.LINUX_X64) =
        PairedScreen("p", "Lobby", "hash", platform, version, 1920, 1080, 0)

    private val canUpdate = listOf(Features.UPDATE)

    @Test
    fun aSignedReleaseIsBelievedAndOfferedToAnOlderScreen() = runTest {
        val updates = updates()
        assertEquals("26.0.9", updates.check()?.release?.version)
        assertNotNull(updates.offerFor(screen(), canUpdate))
        assertNull(updates.offerFor(screen(version = "26.0.9"), canUpdate), "already on it")
        assertNull(updates.offerFor(screen(), emptyList()), "the player does not update itself")
        assertNull(updates.offerFor(screen(platform = Platform.LINUX_ARM64), canUpdate), "no build for it")
    }

    @Test
    fun aReleaseSignedWithAnotherKeyIsIgnored() = runTest {
        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        served = release.files(signature = release.sign(release.index(), other))
        assertNull(updates().check())
    }

    @Test
    fun aFailedCheckKeepsTheLastGoodRelease() = runTest {
        val updates = updates()
        updates.check()
        served = emptyMap()
        assertEquals("26.0.9", updates.check()?.release?.version)
    }

    @Test
    fun theBuildIsDownloadedOnceCheckedAndServedFromTheCache() = runTest {
        val updates = updates().also { it.check() }
        assertNull(updates.cached(Platform.LINUX_X64), "nothing until a screen is updated")
        val file = updates.build(Platform.LINUX_X64)
        assertContentEquals(release.build, file.readBytes())
        assertEquals(file, updates.cached(Platform.LINUX_X64))
        updates.build(Platform.LINUX_X64)
        assertEquals(1, fetched.count { it.endsWith(release.assetName) })
        assertNull(updates.cached(Platform.LINUX_ARM64))
    }

    @Test
    fun aBuildThatDoesNotMatchTheSignedReleaseIsNotKept() = runTest {
        served = release.files() + (release.assetName to "something else!".toByteArray())
        val updates = updates().also { it.check() }
        assertFailsWith<IOException> { updates.build(Platform.LINUX_X64) }
        assertNull(updates.cached(Platform.LINUX_X64))
    }

    @Test
    fun aBuildLargerThanSignedIsRefused() = runTest {
        served = release.files() + (release.assetName to release.build + release.build)
        val updates = updates().also { it.check() }
        val error = assertFailsWith<IOException> { updates.build(Platform.LINUX_X64) }
        assertTrue("larger" in error.message.orEmpty())
    }

    @Test
    fun withoutACheckedReleaseThereIsNoBuild() = runTest {
        assertFailsWith<IOException> { updates().build(Platform.LINUX_X64) }
        assertNull(updates().cached(Platform.LINUX_X64))
        val updates = updates().also { it.check() }
        assertFailsWith<IOException> { updates.build(Platform.WINDOWS_X64) }
    }

    @Test
    fun aReleaseInAFolderCanBeTriedBeforeItIsPublished() = runTest {
        val folder = dir.resolve("release").createDirectories()
        release.files().forEach { (name, bytes) -> folder.resolve(name).writeBytes(bytes) }
        val base = folder.toUri().toString().trimEnd('/')
        val updates = AgentUpdates(dir.resolve("cache"), baseUrl = base, publicKey = release.publicKey)
        assertEquals("26.0.9", updates.check()?.release?.version)
        assertContentEquals(release.build, updates.build(Platform.LINUX_X64).readBytes())
    }

    @Test
    fun overHttpItFollowsTheRedirectToTheDownloadHost() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val files = release.files()
        server.createContext("/latest/") { exchange ->
            val name = exchange.requestURI.path.substringAfterLast('/')
            exchange.responseHeaders.add("Location", "/objects/$name")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/objects/") { exchange ->
            val bytes = files[exchange.requestURI.path.substringAfterLast('/')]
            if (bytes == null) {
                exchange.sendResponseHeaders(404, -1)
            } else {
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}/latest"
            val updates = AgentUpdates(dir.resolve("cache"), baseUrl = base, publicKey = release.publicKey)
            assertEquals("26.0.9", updates.check()?.release?.version)
            assertContentEquals(release.build, updates.build(Platform.LINUX_X64).readBytes())
            val missing = AgentUpdates(dir.resolve("other"), "http://127.0.0.1:${server.address.port}/nothing")
            assertNull(missing.check(), "a 404 is a failed check, not a crash")
        } finally {
            server.stop(0)
        }
    }
}
