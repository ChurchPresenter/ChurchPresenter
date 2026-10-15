package org.churchpresenter.outputguard

import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuardFilesTest {

    private val dir: File = Files.createTempDirectory("guard-files").toFile()
    private val files = GuardFiles(File(dir, "guard"))

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `a still is read back as the pixels it was written with`() {
        // Two pixels, BGRA: an opaque (R 3, G 2, B 1), then a half-transparent white.
        val bgra = byteArrayOf(1, 2, 3, -1, -1, -1, -1, 0x80.toByte())
        files.writeSnapshot(id = 4, width = 2, height = 1, bgra = bgra)

        val still = assertNotNull(files.readSnapshot(4))

        assertEquals(2, still.width)
        assertEquals(1, still.height)
        assertEquals(0xFF030201.toInt(), still.getRGB(0, 0))
        assertEquals(0x80FFFFFF.toInt(), still.getRGB(1, 0))
        val left = files.dir.listFiles().orEmpty().map { it.name }
        assertTrue(left.none { it.endsWith(".tmp") }, "the temporary file was moved into place: $left")
    }

    @Test
    fun `a still that is missing, cut short or impossibly large reads as none`() {
        assertNull(files.readSnapshot(1))

        files.writeSnapshot(id = 2, width = 2, height = 2, bgra = ByteArray(16))
        val cut = File(files.dir, "output-2.still")
        cut.writeBytes(cut.readBytes().copyOf(12))
        assertNull(files.readSnapshot(2))

        DataOutputStream(File(files.dir, "output-3.still").outputStream()).use {
            it.writeInt(100_000)
            it.writeInt(100_000)
        }
        assertNull(files.readSnapshot(3))

        DataOutputStream(File(files.dir, "output-4.still").outputStream()).use {
            it.writeInt(0)
            it.writeInt(10)
        }
        assertNull(files.readSnapshot(4))
    }

    @Test
    fun `a deleted still is gone`() {
        files.writeSnapshot(id = 5, width = 1, height = 1, bgra = ByteArray(4))

        files.deleteSnapshot(5)

        assertNull(files.readSnapshot(5))
    }

    @Test
    fun `the relaunch command is read back argument by argument`() {
        assertEquals(emptyList(), files.readRelaunchCommand(), "an app that never said")

        val command = listOf("/Applications/Church Presenter.app/Contents/MacOS/Church Presenter", "-Xmx2g")
        files.writeRelaunchCommand(command)
        assertEquals(command, files.readRelaunchCommand())

        files.writeRelaunchCommand(listOf("app", "", "-x"))
        assertEquals(listOf("app", "-x"), files.readRelaunchCommand(), "an empty line is no argument")
    }

    @Test
    fun `the live state is written whole where the relaunched app reads it`() {
        files.writeLiveState("""{"mode":"BIBLE"}""")

        assertEquals("""{"mode":"BIBLE"}""", files.liveState.readText())
    }

    @Test
    fun `the user's folder is beside the app's other state`() {
        assertEquals(
            File(System.getProperty("user.home"), ".churchpresenter/output-guard"),
            GuardFiles.forUser().dir,
        )
    }
}
