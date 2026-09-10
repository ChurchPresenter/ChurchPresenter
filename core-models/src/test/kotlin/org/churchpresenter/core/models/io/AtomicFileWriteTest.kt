package org.churchpresenter.core.models.io

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The write that a reader — or a sync client — can never catch half-finished.
 *
 * Every file this protects lives in `~/.churchpresenter`, which people sync between machines, so
 * the two things that matter are that the previous content survives a failed write and that no
 * scratch file is ever left in the folder for a sync daemon to pick up.
 */
class AtomicFileWriteTest {

    private val root: File = Files.createTempDirectory("atomic-write").toFile()

    @AfterTest
    fun removeTempDir() {
        root.deleteRecursively()
    }

    private fun strays(dir: File): List<String> =
        dir.listFiles().orEmpty().map { it.name }.filter { it.endsWith(".writing") }

    @Test
    fun `the content lands and no scratch file is left behind`() {
        val target = File(root, "data.json")

        target.writeTextAtomically("""{"ok":true}""")

        assertEquals("""{"ok":true}""", target.readText())
        assertEquals(emptyList(), strays(root))
    }

    @Test
    fun `an existing file is replaced whole`() {
        val target = File(root, "data.json")
        target.writeText("old and much longer than what replaces it")

        target.writeTextAtomically("new")

        assertEquals("new", target.readText())
        assertEquals(emptyList(), strays(root))
    }

    @Test
    fun `a failed write leaves the previous content and no scratch file`() {
        // A directory where the file should be: the move cannot succeed.
        val target = File(root, "blocked")
        target.mkdirs()
        File(target, "occupant.txt").writeText("still here")

        assertFailsWith<Exception> { target.writeTextAtomically("replacement") }

        assertTrue(target.isDirectory, "the target must be untouched")
        assertEquals("still here", File(target, "occupant.txt").readText())
        assertEquals(emptyList(), strays(root))
    }

    @Test
    fun `missing parent directories are created`() {
        val target = File(root, "nested/deeper/data.json")

        target.writeTextAtomically("made it")

        assertEquals("made it", target.readText())
    }

    @Test
    fun `bytes round-trip unchanged`() {
        val target = File(root, "clip.bin")
        val bytes = ByteArray(512) { (it % 256).toByte() }

        target.writeBytesAtomically(bytes)

        assertTrue(bytes.contentEquals(target.readBytes()))
    }

    @Test
    fun `a reader sees either the old content or the new, never a truncated file`() {
        val target = File(root, "data.json")
        val old = "o".repeat(200_000)
        val new = "n".repeat(200_000)
        target.writeTextAtomically(old)

        val seen = mutableSetOf<Int>()
        val reader = Thread {
            repeat(400) { seen += runCatching { target.readText().length }.getOrDefault(-1) }
        }
        reader.start()
        repeat(40) { target.writeTextAtomically(if (it % 2 == 0) new else old) }
        reader.join()

        assertEquals(setOf(old.length), seen, "a partial read means the swap was not atomic")
    }
}
