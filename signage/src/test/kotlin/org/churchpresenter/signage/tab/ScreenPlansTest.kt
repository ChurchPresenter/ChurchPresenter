package org.churchpresenter.signage.tab

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.churchpresenter.signage.core.MediaKind
import org.churchpresenter.signage.core.Orientation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** How a screen is mounted reaches its manifest and outlives a wipe; a file playlist points at another file. */
class ScreenPlansTest {
    private val world = SignageWorld()
    private val controller = world.controller
    private val hub = world.hub

    @AfterTest
    fun cleanUp() = world.close()

    private suspend fun publishedOrientation(wanted: Orientation) = withTimeout(SignageWorld.WAIT_MS) {
        while (hub.published("player-1")?.manifest?.screen?.orientation != wanted) yield()
    }

    @Test
    fun aScreenMountedOnItsSideIsSentSoAndAWipeKeepsIt() = runBlocking {
        world.file("welcome.jpg")
        world.startAndPairScreen()
        publishedOrientation(Orientation.LANDSCAPE)
        controller.plans.setOrientation("player-1", Orientation.PORTRAIT)
        publishedOrientation(Orientation.PORTRAIT)
        controller.wipe("player-1")
        withTimeout(SignageWorld.WAIT_MS) {
            controller.state.first { it.settings.plan("player-1").playlistId == ScreenPlan.NOTHING }
        }
        assertEquals(Orientation.PORTRAIT, controller.state.value.settings.plan("player-1").orientation)
        // The empty manifest a wipe sends is still turned.
        publishedOrientation(Orientation.PORTRAIT)
    }

    @Test
    fun aFilePlaylistTakesAnotherPictureOrVideoButNothingElse() = runBlocking {
        val first = world.root.resolve("elsewhere/flyer.png").also {
            it.parent.toFile().mkdirs()
            it.toFile().writeText("png")
        }
        val video = world.root.resolve("elsewhere/intro.mp4").also { it.toFile().writeText("mp4") }
        controller.start()
        controller.added.addFile(first)
        val id = withTimeout(SignageWorld.WAIT_MS) {
            controller.state.first { state -> state.settings.added.isNotEmpty() }
        }.settings.added.single().id
        controller.added.updateFile(id, " Intro ", video)
        val saved = { controller.state.value.settings.added.single() as FilePlaylist }
        withTimeout(SignageWorld.WAIT_MS) { while (saved().name != "Intro") yield() }
        assertEquals(FilePlaylist(id, "Intro", video.toString(), MediaKind.VIDEO), saved())
        // Not a picture or video: the name is taken, the file is not.
        controller.added.updateFile(id, "Notes", world.root.resolve("notes.txt"))
        withTimeout(SignageWorld.WAIT_MS) { while (saved().name != "Notes") yield() }
        assertEquals(FilePlaylist(id, "Notes", video.toString(), MediaKind.VIDEO), saved())
    }
}
