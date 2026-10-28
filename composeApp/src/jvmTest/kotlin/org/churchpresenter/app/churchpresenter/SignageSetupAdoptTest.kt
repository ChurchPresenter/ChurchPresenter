package org.churchpresenter.app.churchpresenter

import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Moving to a shared signage folder: this computer's setup is brought along, but never over another's. */
@OptIn(ExperimentalPathApi::class)
class SignageSetupAdoptTest {
    private val root = createTempDirectory("signage-adopt")
    private val local = root.resolve("local").also { Files.createDirectories(it) }

    @AfterTest
    fun cleanUp() = root.deleteRecursively()

    @Test
    fun aNewFolderGetsThisComputersSetup() {
        local.resolve("screens.json").writeText("""{"ownerId":"here"}""")
        local.resolve("signage.json").writeText("""{"pictureSeconds":12}""")
        val shared = root.resolve("shared")
        adoptSignageSetup(local, shared)
        assertEquals("""{"ownerId":"here"}""", shared.resolve("screens.json").readText())
        assertEquals("""{"pictureSeconds":12}""", shared.resolve("signage.json").readText())
    }

    @Test
    fun aFolderHoldingAnotherComputersSetupIsJoinedAsItStands() {
        local.resolve("screens.json").writeText("""{"ownerId":"here"}""")
        local.resolve("signage.json").writeText("""{"pictureSeconds":12}""")
        val shared = root.resolve("shared").also { Files.createDirectories(it) }
        shared.resolve("screens.json").writeText("""{"ownerId":"there"}""")
        adoptSignageSetup(local, shared)
        assertEquals("""{"ownerId":"there"}""", shared.resolve("screens.json").readText())
        assertFalse(Files.exists(shared.resolve("signage.json")))
    }

    @Test
    fun aFolderThatCannotBeMadeLeavesTheSetupWhereItWas() {
        local.resolve("screens.json").writeText("""{"ownerId":"here"}""")
        // A file where the folder should go: it cannot be made, and nothing is thrown.
        val blocked = root.resolve("blocked").also { it.writeText("not a folder") }
        adoptSignageSetup(local, blocked.resolve("signage"))
        assertEquals("""{"ownerId":"here"}""", local.resolve("screens.json").readText())
        assertFalse(Files.exists(blocked.resolve("signage")))
    }
}
