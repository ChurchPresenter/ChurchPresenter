@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SourcePropertiesBibleBookNamesTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-canvas-book-names").toFile()
        SpbFixture.spbFile(dir, name = "kjv.spb")
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    private fun settings(overrides: Map<Int, String>) = AppSettings(
        bibleSettings = BibleSettings(storageDirectory = dir.absolutePath).withTranslations(
            listOf(BibleTranslationSettings(fileName = "kjv.spb", bookNameOverrides = overrides)),
        ),
    )

    @Test
    fun `the loaded source carries the names it is given`() {
        val bible = loadBibleSource(dir.absolutePath, "kjv.spb", mapOf(1 to "Bereshit"))

        assertEquals("Bereshit", bible?.getBookName(1))
        assertEquals("John", bible?.getBookName(43))
    }

    @Test
    fun `the picker lists the override and Insert writes it into the reference`() {
        sourcePanel(Fixture.bible(), appSettings = settings(mapOf(1 to "Bereshit"))) { get ->
            waitUntil("the Insert button must appear", timeoutMillis = 5_000) { countOf("Insert Verse") == 1 }

            onNodeWithText("Insert Verse").performScrollTo().performClick()
            waitForIdle()

            assertEquals("Bereshit 1:1", (get() as SceneSource.BibleSource).referenceText)
        }
    }

    @Test
    fun `a translation with no overrides keeps the file's names`() {
        sourcePanel(Fixture.bible(), appSettings = settings(emptyMap())) { get ->
            waitUntil("the Insert button must appear", timeoutMillis = 5_000) { countOf("Insert Verse") == 1 }

            onNodeWithText("Insert Verse").performScrollTo().performClick()
            waitForIdle()

            assertEquals("Genesis 1:1", (get() as SceneSource.BibleSource).referenceText)
        }
    }
}
