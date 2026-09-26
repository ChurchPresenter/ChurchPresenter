package org.churchpresenter.songlibrary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.churchpresenter.core.models.songs.SongField
import org.churchpresenter.core.models.songs.SongTranslation
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongLibraryLanguagesTest {

    private val root: File = Files.createTempDirectory("songlibrary-languages").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun write(name: String, body: String) {
        File(root, "$name.song").writeText(body, Charsets.UTF_8)
    }

    private fun loaded(): SongLibraryState =
        SongLibraryState(root).also { runBlocking { it.reloadAsync(Dispatchers.Unconfined) } }

    @Test
    fun `the language 3 and 4 columns are only offered when a song uses that language`() {
        write("Two", "[Primary]\ntitle: Two\n\nOne\n\n[Secondary]\ntitle: Два\n\nОдин\n")
        assertFalse(SongField.THIRD_TITLE in loaded().availableColumns)

        write(
            "Three",
            "[Primary]\ntitle: Three\n\nOne\n\n[Secondary]\ntitle: Два\n\nОдин\n\n" +
                "[Translation 3]\ntitle: Три\n\nОдна\n",
        )
        val state = loaded()

        assertTrue(SongField.THIRD_TITLE in state.availableColumns)
        assertFalse(SongField.FOURTH_TITLE in state.availableColumns)
    }

    @Test
    fun `the language 3 and 4 columns start hidden and can be turned on`() {
        write(
            "Four",
            "[Primary]\ntitle: Four\n\nOne\n\n[Secondary]\ntitle: Два\n\n" +
                "[Translation 3]\ntitle: Три\n\n[Translation 4]\ntitle: Төрт\n",
        )
        val state = loaded()

        assertFalse(SongField.THIRD_TITLE in state.visibleColumns)
        state.toggleColumn(SongField.THIRD_TITLE)
        assertTrue(SongField.THIRD_TITLE in state.visibleColumns)
        assertFalse(SongField.FOURTH_TITLE in state.visibleColumns)
    }

    @Test
    fun `the number column comes before the title`() {
        write("One", "[Primary]\ntitle: One\n\nOne\n")

        assertEquals(listOf(SongField.NUMBER, SongField.TITLE), loaded().visibleColumns.take(2))
    }

    @Test
    fun `only songs with a language problem are flagged`() {
        write("Fine", "[Primary]\ntitle: Fine\n\n[Verse 1]\nOne\n\n[Secondary]\ntitle: Добре\n\n[Куплет 1]\nОдин\n")
        write(
            "Short",
            "[Primary]\ntitle: Short\n\n[Verse 1]\nOne\nTwo\n\n[Secondary]\ntitle: Коротко\n\n[Куплет 1]\nОдин\n",
        )
        write("Alone", "[Primary]\ntitle: Alone\n\nOne\n")
        val state = loaded()

        val flagged = state.translationProblems.mapKeys { (file, _) -> File(file).nameWithoutExtension }
        assertEquals(setOf("Short"), flagged.keys)
        assertEquals(1, flagged.getValue("Short").mismatchedSections)
    }

    @Test
    fun `a song back from an editor keeps every language, not just the first two`() {
        write("Grace", "[Primary]\ntitle: Grace\n\nOne\n\n[Secondary]\ntitle: Два\n\nОдин\n")
        val state = loaded()
        val song = state.songs.single()

        state.replace(
            song.withTranslations(
                song.extraTranslations() + SongTranslation(title = "Три", lyrics = listOf("Одна")),
            ),
        )

        val replaced = state.songs.single()
        assertEquals(listOf("Два", "Три"), replaced.extraTranslations().map { it.title })
        assertEquals(listOf("Одна"), replaced.extraTranslations()[1].lyrics)
        assertTrue(state.isDirty)
    }
}
