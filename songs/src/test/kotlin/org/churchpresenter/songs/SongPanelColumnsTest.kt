package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which languages the lyrics panel gives a column (#1024), and what each column holds per section. */
class SongPanelColumnsTest {

    private fun section(vararg languages: List<String>) = LyricSection(
        lines = languages.first(),
        translations = languages.drop(1).map { SectionTranslation(lines = it) },
    )

    private val verse = section(listOf("Praise"), listOf("Yin"), listOf("Gloire"))
    private val chorus = section(listOf("Amen"), emptyList(), listOf("Ainsi"))

    @Test
    fun `every shown language with words gets a column`() =
        assertEquals(listOf(0, 1, 2), panelColumnSlots(listOf(verse, chorus), listOf(0, 1, 2)))

    @Test
    fun `a hidden language gets none`() =
        assertEquals(listOf(0, 2), panelColumnSlots(listOf(verse, chorus), listOf(0, 2)))

    @Test
    fun `a language the song never fills takes no room`() {
        val untranslated = section(listOf("Praise"), emptyList(), listOf("Gloire"))
        assertEquals(listOf(0, 2), panelColumnSlots(listOf(untranslated), listOf(0, 1, 2)))
    }

    @Test
    fun `with no shown language carrying words the first shown keeps its column`() {
        val primaryOnly = section(listOf("Praise"))
        assertEquals(listOf(1), panelColumnSlots(listOf(primaryOnly), listOf(1, 2)))
        assertEquals(listOf(0), panelColumnSlots(emptyList(), emptyList()))
    }

    @Test
    fun `a language missing from one section keeps a blank column there`() =
        assertEquals(listOf(listOf("Amen"), emptyList(), listOf("Ainsi")), panelColumnLines(chorus, listOf(0, 1, 2)))

    @Test
    fun `a section with nothing in any shown column shows its own lines instead`() =
        assertEquals(listOf(listOf("Amen")), panelColumnLines(chorus, listOf(1)))
}
