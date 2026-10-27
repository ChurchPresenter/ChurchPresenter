package org.churchpresenter.signage.tab

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.churchpresenter.signage.core.BlockAction
import org.churchpresenter.signage.core.ClockTime
import org.churchpresenter.signage.core.Day
import org.churchpresenter.signage.core.ScheduleBlock
import org.churchpresenter.signage.core.ServerMessage
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A signage folder another computer shares: what it writes there reaches this one's tab and screens,
 * this one's own cache stays out of it, and rotations and schedules are edited through the controller.
 */
class SharedSetupTest {
    private val world = SignageWorld()
    private val controller = world.controller

    @AfterTest
    fun cleanUp() = world.close()

    private suspend fun settled(predicate: (SignageFolderState) -> Boolean) =
        withTimeout(SignageWorld.WAIT_MS) { controller.state.first(predicate) }

    @Test
    fun whatAnotherComputerSavesIsTakenInAndSent() = runBlocking {
        world.file("welcome.jpg")
        world.startAndPairScreen()
        val sent = with(world) { scope.expect { it as? ServerMessage.ManifestChanged } }
        // The other computer, sharing the folder, saves its own settings.
        SignageStore(world.root.resolve("store")).saveSettings(SignageSettings(pictureSeconds = 33))
        assertEquals(33, settled { it.settings.pictureSeconds == 33 }.settings.pictureSeconds)
        sent.await()
    }

    @Test
    fun rotationsAndSchedulesAreEditedAndKept() = runBlocking {
        world.file("welcome.jpg")
        world.startAndPairScreen()
        controller.added.addRotation(" Evenings ", listOf(RotationStep("folder:")))
        val id = settled { state -> state.playlists.any { it.kind == SourceKind.ROTATION } }.playlists.last().id
        controller.added.updateRotation(id, "Nights", listOf(RotationStep("folder:"), RotationStep("folder:")))
        settled { state -> state.playlists.any { it.name == "Nights" && it.steps == 2 } }
        val block =
            ScheduleBlock("b", setOf(Day.MON), ClockTime.parse("08:00"), ClockTime.parse("09:00"), BlockAction.Play(id))
        controller.schedules.set("player-1", listOf(block))
        settled { it.settings.plan("player-1").schedule == listOf(block) }
        val saved = SignageStore(world.root.resolve("store")).loadSettings()
        assertEquals("Nights", saved.added.filterIsInstance<RotationPlaylist>().single().name)
        assertEquals(listOf(block), saved.plan("player-1").schedule)
    }

    @Test
    fun thisComputersHashCacheStaysOutOfTheSharedFolder() {
        val shared = world.root.resolve("shared")
        val local = world.root.resolve("local")
        val store = SignageStore(shared, local)
        store.saveSettings(SignageSettings())
        store.saveHashes(emptySet())
        assertTrue(shared.resolve("signage.json").exists() && !shared.resolve("hashes.json").exists())
        assertTrue(local.resolve("hashes.json").exists())
    }
}
