package org.churchpresenter.detektrules

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LiveSafeAllowlistTest {

    private val root: File = Files.createTempDirectory("live-safe").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `lines are path, function and call, with comments and blank lines skipped`() {
        val list = LiveSafeAllowlist.parse(
            """
            # a header
            a/B.kt: Draw exists   # why

            c/D.kt:Load   Thread.sleep
            """
        )

        assertEquals(
            listOf(AllowedCall("a/B.kt", "Draw", "exists"), AllowedCall("c/D.kt", "Load", "Thread.sleep")),
            list.entries,
        )
    }

    @Test
    fun `a line that is not path, function and call fails, naming the line`() {
        val error = assertFailsWith<IllegalArgumentException> {
            LiveSafeAllowlist.parse("a/B.kt: Draw\n", source = "list.txt")
        }
        assertTrue("list.txt line 1" in error.message.orEmpty(), error.message)
        assertFailsWith<IllegalArgumentException> { LiveSafeAllowlist.parse("Draw exists\n") }
        assertFailsWith<IllegalArgumentException> { LiveSafeAllowlist.parse(": Draw exists\n") }
        assertFailsWith<IllegalArgumentException> { LiveSafeAllowlist.parse("a/B.kt: Draw exists extra\n") }
    }

    @Test
    fun `the list is found in the nearest directory above a source file`() {
        File(root, "config/allowed.txt").apply { parentFile.mkdirs() }.writeText("m/A.kt: Draw exists\n")
        val source = File(root, "m/src/main/kotlin/A.kt").apply { parentFile.mkdirs() }

        val list = LiveSafeAllowlist.near(source.path, "config/allowed.txt")

        assertEquals(listOf(AllowedCall("m/A.kt", "Draw", "exists")), list.entries)
        assertEquals("config/allowed.txt", list.source)
    }

    @Test
    fun `an edited list is read again, not served from before`() {
        val file = File(root, "allowed.txt").apply { writeText("m/A.kt: Draw exists\n") }
        val source = File(root, "m/A.kt").path
        LiveSafeAllowlist.near(source, "allowed.txt")

        file.writeText("m/A.kt: Draw exists\nm/A.kt: Load readText\n")

        assertEquals(2, LiveSafeAllowlist.near(source, "allowed.txt").entries.size)
    }

    @Test
    fun `no list configured, or none above the file, allows nothing`() {
        val source = File(root, "m/A.kt").path

        assertSame(LiveSafeAllowlist.EMPTY, LiveSafeAllowlist.near(source, ""))
        assertSame(LiveSafeAllowlist.EMPTY, LiveSafeAllowlist.near(source, "no/such/list-${System.nanoTime()}.txt"))
    }
}
