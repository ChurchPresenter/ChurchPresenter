package org.churchpresenter.songlibrary

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTranslation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslationComparisonTest {

    private fun song(vararg languages: SongTranslation): SongItem =
        SongItem(number = "1", title = languages[0].title, lyrics = languages[0].lyrics)
            .withTranslations(languages.drop(1))

    private fun language(title: String, vararg lyrics: String) =
        SongTranslation(title = title, lyrics = lyrics.toList())

    // ── Splitting ─────────────────────────────────────────────────────────────

    @Test
    fun `lyrics are cut at every header, each body trimmed of blank edges`() {
        val sections = TranslationComparison.sectionsOf(
            listOf("[Verse 1]", "", "One", "Two", "", "[Chorus]", "Three", ""),
        )

        assertEquals(
            listOf(RawSection("[Verse 1]", listOf("One", "Two")), RawSection("[Chorus]", listOf("Three"))),
            sections,
        )
    }

    @Test
    fun `words before the first header are a section with no header`() {
        val sections = TranslationComparison.sectionsOf(listOf("", "Intro line", "[Verse 1]", "One"))

        assertEquals(listOf(null, "[Verse 1]"), sections.map { it.header })
        assertEquals(listOf("Intro line"), sections.first().body)
    }

    @Test
    fun `a header with nothing under it is still a section, and a chord is not a header`() {
        val sections = TranslationComparison.sectionsOf(listOf("[Verse 1]", "[Am]", "[Chorus]"))

        assertEquals(listOf("[Verse 1]", "[Chorus]"), sections.map { it.header })
        assertEquals(listOf("[Am]"), sections.first().body)
        assertEquals(emptyList(), sections.last().body)
    }

    @Test
    fun `slides count only the lines that reach the screen`() {
        val body = listOf("[G]One", "[C] [D]", "Two", "[---]", "", "Three", "[background: image]")

        assertEquals(listOf(2, 1), TranslationComparison.slidesOf(body))
        assertEquals(3, TranslationComparison.presentableLines(body))
    }

    // ── Status ────────────────────────────────────────────────────────────────

    @Test
    fun `a section is missing, out of step or lined up against the reference`() {
        val reference = listOf("One", "Two")

        assertEquals(SectionStatus.MISSING, TranslationComparison.statusOf(listOf("", " "), reference))
        assertEquals(SectionStatus.MISMATCH, TranslationComparison.statusOf(listOf("Uno"), reference))
        assertEquals(SectionStatus.OK, TranslationComparison.statusOf(listOf("Uno", "Dos"), reference))
    }

    @Test
    fun `the same lines split across different slides are out of step`() {
        val status = TranslationComparison.statusOf(listOf("Uno", "[---]", "Dos"), listOf("One", "Two"))

        assertEquals(SectionStatus.MISMATCH, status)
    }

    // ── Putting edits back ────────────────────────────────────────────────────

    @Test
    fun `an edited section is written back and the others are left as they were`() {
        val original = TranslationComparison.sectionsOf(listOf("[Куплет 1]", "Один", "", "[Припев]", "Два"))

        val lyrics = TranslationComparison.rebuild(original, mapOf(1 to listOf("Два", "Три")), listOf(null, null))

        assertEquals(listOf("[Куплет 1]", "Один", "", "[Припев]", "Два", "Три"), lyrics)
    }

    @Test
    fun `a section typed into a slot the language never had takes the header of that position`() {
        val original = TranslationComparison.sectionsOf(listOf("[Куплет 1]", "Один"))

        val lyrics = TranslationComparison.rebuild(
            original,
            mapOf(2 to listOf("Три")),
            listOf("[Verse 1]", "[Verse 2]", "[Verse 3]"),
        )

        // The gap stays a header, so verse 3 is still paired with verse 3 on screen.
        assertEquals(listOf("[Куплет 1]", "Один", "", "[Verse 2]", "", "[Verse 3]", "Три"), lyrics)
    }

    @Test
    fun `a blank section past the end of the language is dropped rather than written`() {
        val original = TranslationComparison.sectionsOf(listOf("[Verse 1]", "One"))

        val lyrics = TranslationComparison.rebuild(original, mapOf(1 to listOf("")), listOf(null, "[Verse 2]"))

        assertEquals(listOf("[Verse 1]", "One"), lyrics)
    }

    @Test
    fun `new lyrics go to the language they were edited in`() {
        val base = song(language("Grace", "One"), language("Благодать", "Один"), language("Ласка", "Одна"))

        val primary = TranslationComparison.withLyrics(base, 0, listOf("Uno"))
        val third = TranslationComparison.withLyrics(base, 2, listOf("Інша"))

        assertEquals(listOf("Uno"), primary.lyrics)
        assertEquals(listOf("Один"), primary.extraTranslations()[0].lyrics)
        assertEquals(listOf("Інша"), third.extraTranslations()[1].lyrics)
        assertEquals("Ласка", third.extraTranslations()[1].title, "the title is left alone")
    }

    // ── Problems ──────────────────────────────────────────────────────────────

    @Test
    fun `a one-language song has nothing to compare`() {
        val problems = TranslationComparison.problemsOf(song(language("Grace", "[Verse 1]", "One")))

        assertTrue(problems.isEmpty)
    }

    @Test
    fun `a section whose languages disagree on line count is counted once`() {
        val problems = TranslationComparison.problemsOf(
            song(
                language("Grace", "[Verse 1]", "One", "Two", "[Verse 2]", "Three"),
                language("Благодать", "[Куплет 1]", "Один", "[Куплет 2]", "Три"),
                language("Ласка", "[Куплет 1]", "Одна", "[Куплет 2]", "Три"),
            ),
        )

        assertEquals(TranslationProblems(mismatchedSections = 1), problems)
    }

    @Test
    fun `a section one language lacks is a mismatch`() {
        val problems = TranslationComparison.problemsOf(
            song(
                language("Grace", "[Verse 1]", "One", "[Verse 2]", "Two"),
                language("Благодать", "[Куплет 1]", "Один"),
            ),
        )

        assertEquals(1, problems.mismatchedSections)
    }

    @Test
    fun `a translation with lyrics but no title is flagged by its number`() {
        val problems = TranslationComparison.problemsOf(
            song(language("Grace", "One"), language("", "Один")),
        )

        assertEquals(listOf(2), problems.untitledLanguages)
        assertEquals(0, problems.mismatchedSections)
    }

    @Test
    fun `a translation with a title but no lyrics is flagged, not counted as out of step`() {
        val problems = TranslationComparison.problemsOf(
            song(language("Grace", "[Verse 1]", "One"), language("Благодать")),
        )

        assertEquals(TranslationProblems(lyriclessLanguages = listOf(2)), problems)
    }

    @Test
    fun `the primary is flagged for missing lyrics only when another language has some`() {
        val titlesOnly = TranslationComparison.problemsOf(song(language("Grace"), language("Благодать")))
        val translatedOnly = TranslationComparison.problemsOf(song(language("Grace"), language("Благодать", "Один")))

        assertEquals(listOf(2), titlesOnly.lyriclessLanguages)
        assertEquals(listOf(1), translatedOnly.lyriclessLanguages)
    }

    @Test
    fun `any one kind of problem is enough to make the song a problem`() {
        assertTrue(TranslationProblems().isEmpty)
        assertFalse(TranslationProblems(mismatchedSections = 1).isEmpty)
        assertFalse(TranslationProblems(untitledLanguages = listOf(2)).isEmpty)
        assertFalse(TranslationProblems(lyriclessLanguages = listOf(2)).isEmpty)
    }
}
