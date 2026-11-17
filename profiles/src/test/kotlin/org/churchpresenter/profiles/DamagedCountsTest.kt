package org.churchpresenter.profiles

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which stacked Bibles get the warning mark in Settings, and how many verses it says. */
class DamagedCountsTest {

    private val dir = Files.createTempDirectory("damaged-counts").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun write(name: String, vararg rows: String) =
        dir.resolve(name).writeText("##Title:\tT\n-----\n" + rows.joinToString("\n") + "\n")

    @Test
    fun `only a Bible with unreadable ids is counted, with how many`() {
        write(
            "bad.spb",
            "B047C010V004\t47\t10\t4\tFour",
            "B047C010V0050\t47\t10\t5\tFive",
            "B047C010V0060\t47\t10\t6\tSix",
        )
        write("good.spb", "B047C010V004\t47\t10\t4\tFour")
        assertEquals(
            mapOf("bad.spb" to 2),
            damagedCounts(dir.absolutePath, listOf("good.spb", "bad.spb", "missing.spb")),
        )
    }

    @Test
    fun `no stack means nothing to warn about`() {
        assertEquals(emptyMap(), damagedCounts(dir.absolutePath, emptyList()))
    }
}
