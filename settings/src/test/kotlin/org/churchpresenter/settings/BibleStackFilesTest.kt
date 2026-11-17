package org.churchpresenter.settings

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The stacked Bibles that are on disk, which the Bible Verse Fixer offers first. */
class BibleStackFilesTest {

    private val dir = Files.createTempDirectory("bible-stack-files").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun stack(vararg names: String) = BibleSettings(storageDirectory = dir.absolutePath)
        .withTranslations(names.map { BibleTranslationSettings(fileName = it) })

    @Test
    fun `stacked Bibles on disk come back in stack order and missing ones are left out`() {
        dir.resolve("b.spb").writeText("b")
        dir.resolve("a.spb").writeText("a")
        assertEquals(
            listOf("b.spb", "a.spb"),
            stack("b.spb", "gone.spb", "a.spb").stackFiles().map { it.name },
        )
    }

    @Test
    fun `an empty stack has no files`() {
        assertEquals(emptyList(), stack().stackFiles())
    }
}
