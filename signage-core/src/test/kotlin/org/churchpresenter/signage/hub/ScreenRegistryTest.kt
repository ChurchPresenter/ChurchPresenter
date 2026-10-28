package org.churchpresenter.signage.hub

import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.churchpresenter.signage.core.Platform
import java.security.SecureRandom
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class ScreenRegistryTest {
    private val dir = createTempDirectory("signage-registry")
    private val file = dir.resolve("screens.json")
    private val scope = SaveScope()

    @AfterTest
    fun cleanUp() {
        // A save still writing would hold the folder open as it is deleted.
        runBlocking { scope.settle() }
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun registry() = ScreenRegistry(file, scope, clock = { 1_000 }, random = SecureRandom())

    @Test
    fun aScreenPairedByAnotherComputerSharingTheFileIsReadOnTheNextLook() = runTest {
        val here = registry().also { it.load() }
        scope.settle()
        val there = registry().also { it.load() }
        assertEquals(here.ownerId, there.ownerId)
        // Nothing changed elsewhere yet.
        assertFalse(here.reloadIfChanged())
        there.pair(PendingScreen("player-9", "1234", Platform.LINUX_ARM64, "26.0.32", 1920, 1080, "10.0.0.9"), "Hall")
        scope.settle()
        assertTrue(here.reloadIfChanged())
        assertEquals(listOf("Hall"), here.screens.value.map { it.name })
        assertFalse(here.reloadIfChanged())
    }

    @Test
    fun aNewRegistryMakesAnOwnerIdAndKeepsIt() = runTest {
        val first = registry().also { it.load() }
        scope.settle()
        val again = registry().also { it.load() }
        assertTrue(first.ownerId.isNotBlank())
        assertEquals(first.ownerId, again.ownerId)
    }

    @Test
    fun pairingKeepsOnlyTheTokensHashAndFindsTheScreenByIt() = runTest {
        val registry = registry().also { it.load() }
        val token = registry.pair(pending(), "Lobby")
        scope.settle()
        assertEquals("Lobby", registry.byToken(token)?.name)
        assertNull(registry.byToken("not-$token"))
        assertTrue(token !in file.readText(), "the token itself is never written")

        val reloaded = registry().also { it.load() }
        assertEquals("Lobby", reloaded.screen("player-1")?.name)
    }

    @Test
    fun pairingAgainKeepsTheNameButIssuesANewToken() = runTest {
        val registry = registry().also { it.load() }
        val first = registry.pair(pending(), "Lobby")
        registry.rename("player-1", "Front door")
        val second = registry.pair(pending(), "Screen 2")
        assertNotEquals(first, second)
        assertNull(registry.byToken(first))
        assertEquals("Front door", registry.screen("player-1")?.name)
    }

    @Test
    fun aHelloKeepsWhatThePlayerSaysAboutItselfCurrent() = runTest {
        val registry = registry().also { it.load() }
        registry.pair(pending(), "Lobby")
        registry.refreshFrom(hello(code = null, version = "26.0.9"))
        assertEquals("26.0.9", registry.screen("player-1")?.agentVersion)
        registry.refreshFrom(hello(id = "stranger"))
        assertEquals(1, registry.screens.value.size)
        assertEquals(Platform.LINUX_X64, registry.screen("player-1")?.platform)
    }

    @Test
    fun renamingIgnoresABlankNameAndRemovingForgets() = runTest {
        val registry = registry().also { it.load() }
        registry.pair(pending(), "Lobby")
        registry.rename("player-1", "  ")
        registry.rename("nobody", "x")
        assertEquals("Lobby", registry.screen("player-1")?.name)
        registry.rename("player-1", " Hall ")
        assertEquals("Hall", registry.screen("player-1")?.name)
        registry.remove("player-1")
        scope.settle()
        assertEquals(emptyList(), registry().also { it.load() }.screens.value)
    }

    @Test
    fun theFileEndsWithTheLastChangeHoweverQuicklyTheyCome() = runTest {
        val registry = registry().also { it.load() }
        repeat(SCREENS) { registry.pair(pending(id = "p$it"), "Screen $it") }
        scope.settle()
        assertEquals(SCREENS, registry().also { it.load() }.screens.value.size)
    }

    @Test
    fun aDamagedFileStartsAfreshRatherThanFailing() = runTest {
        file.writeText("{ not json")
        val registry = registry().also { it.load() }
        assertTrue(registry.ownerId.isNotBlank())
        assertEquals(emptyList(), registry.screens.value)
    }

    @Test
    fun theLaunchPingCountsPairedScreensByPlatformWithoutStartingAFile() = runTest {
        assertEquals(emptyMap(), SignageHub.pairedScreensByPlatform(dir))
        assertTrue(!file.toFile().exists(), "counting must not make a registry")
        val registry = registry().also { it.load() }
        registry.pair(pending(id = "a").copy(platform = Platform.LINUX_ARM64), "Lobby")
        registry.pair(pending(id = "b").copy(platform = Platform.LINUX_ARM64), "Hall")
        registry.pair(pending(id = "c").copy(platform = Platform.WINDOWS_X64), "Cafe")
        scope.settle()
        assertEquals(mapOf("linux_arm64" to 2, "windows_x64" to 1), SignageHub.pairedScreensByPlatform(dir))
    }

    @Test
    fun aFileThatCannotBeReadCountsAsNoScreens() {
        file.writeText("{ not json")
        assertEquals(emptyMap(), countByPlatform(file))
        // A folder where the file should be cannot be read at all.
        assertEquals(emptyMap(), countByPlatform(dir))
    }

    private companion object {
        const val SCREENS = 20
    }
}
