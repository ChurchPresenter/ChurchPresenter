package org.churchpresenter.songlibrary

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.songchords.ChordTransposer

/** One section of one language as written: its header line, and every line under it, chords and breaks included. */
data class RawSection(val header: String?, val body: List<String>)

/**
 * What is wrong with a song's languages, for the grid to flag: sections out of step, languages that
 * carry lyrics but no title, and languages that carry a title but no lyrics — numbered from 1, the
 * primary being language 1.
 */
data class TranslationProblems(
    val mismatchedSections: Int = 0,
    val untitledLanguages: List<Int> = emptyList(),
    val lyriclessLanguages: List<Int> = emptyList(),
) {
    val isEmpty: Boolean
        get() = mismatchedSections == 0 && untitledLanguages.isEmpty() && lyriclessLanguages.isEmpty()
}

/** How one language's section stands against the reference language's. */
enum class SectionStatus { OK, MISMATCH, MISSING }

/**
 * Lining up a song's languages section by section, the way the presenter pairs them, so a line one
 * of them is missing shows up here rather than on the screen during a service.
 *
 * The presenter pairs languages by section and by slide within it — verse 3 against verse 3 — and
 * then line against line, so a section is out of step when its presentable lines, or the slides
 * they are split across, differ in number from the reference's.
 */
object TranslationComparison {

    /** [lyrics] cut at every section header, each body trimmed of the blank lines around it. */
    fun sectionsOf(lyrics: List<String>): List<RawSection> {
        val sections = mutableListOf<RawSection>()
        var header: String? = null
        var body = mutableListOf<String>()
        var open = false

        fun close() {
            if (open) sections.add(RawSection(header, body.trimBlankEdges()))
        }

        for (line in lyrics) {
            if (ChordTransposer.isSectionHeader(line)) {
                close()
                header = line.trim()
                body = mutableListOf()
                open = true
            } else {
                // Words before the first header are a section of their own, with no header.
                if (!open && line.isNotBlank()) open = true
                if (open) body.add(line)
            }
        }
        close()
        return sections
    }

    /** The lines of [body] that reach the screen: chords off, and breaks, directives and blanks skipped. */
    fun presentableLines(body: List<String>): Int = slidesOf(body).sum()

    /** How many presentable lines each slide of [body] holds, split at its slide breaks. */
    fun slidesOf(body: List<String>): List<Int> {
        val slides = mutableListOf<Int>()
        var count = 0
        for (line in body) {
            when {
                ChordTransposer.isSlideBreak(line) -> {
                    if (count > 0) slides.add(count)
                    count = 0
                }
                ChordTransposer.isBackgroundDirective(line) -> Unit
                ChordTransposer.stripChords(line).isNotBlank() -> count++
            }
        }
        if (count > 0) slides.add(count)
        return slides
    }

    /** [body] against [reference]: missing when it holds nothing, out of step when its slides differ. */
    fun statusOf(body: List<String>, reference: List<String>): SectionStatus = when {
        body.all(String::isBlank) -> SectionStatus.MISSING
        slidesOf(body) != slidesOf(reference) -> SectionStatus.MISMATCH
        else -> SectionStatus.OK
    }

    /** Language [language] of [song] — an index into [SongItem.translationList] — with [lyrics] in place of its own. */
    fun withLyrics(song: SongItem, language: Int, lyrics: List<String>): SongItem =
        if (language == 0) song.copy(lyrics = lyrics)
        else song.withTranslation(language - 1) { it.copy(lyrics = lyrics) }

    /**
     * [original]'s sections with [edited] bodies put in place, as lyrics again.
     *
     * A section this language never had is written under [headers]' header for that position, so a
     * verse typed into an empty slot lands where the presenter will pair it. One it never had and
     * that is still blank past its last section is dropped rather than written as an empty header.
     */
    fun rebuild(original: List<RawSection>, edited: Map<Int, List<String>>, headers: List<String?>): List<String> {
        val count = maxOf(original.size, (edited.keys.maxOrNull() ?: -1) + 1)
        val sections = (0 until count).map { index ->
            val own = original.getOrNull(index)
            RawSection(own?.header ?: headers.getOrNull(index), edited[index] ?: own?.body.orEmpty())
        }.toMutableList()
        while (sections.size > original.size && sections.last().body.all(String::isBlank)) sections.removeLast()
        return sections.flatMapIndexed { index, section ->
            buildList {
                if (index > 0) add("")
                section.header?.let(::add)
                addAll(section.body.trimBlankEdges())
            }
        }
    }

    /**
     * How many sections of [song] do not line up between the primary and some other language.
     *
     * A language with no lyrics at all is not counted: it is untranslated rather than out of step,
     * and flagging every section of it would bury the songs that are nearly right.
     */
    fun mismatchCount(song: SongItem): Int {
        val languages = song.translationList().map { sectionsOf(it.lyrics) }
        val primary = languages.first()
        val others = languages.drop(1).filter { it.isNotEmpty() }
        if (others.isEmpty()) return 0
        val rows = (listOf(primary) + others).maxOf { it.size }
        return (0 until rows).count { index ->
            val reference = primary.getOrNull(index)?.body.orEmpty()
            others.any { statusOf(it.getOrNull(index)?.body.orEmpty(), reference) != SectionStatus.OK }
        }
    }

    /** Every problem [song] has across its languages — see [TranslationProblems]. */
    fun problemsOf(song: SongItem): TranslationProblems {
        val languages = song.translationList()
        fun hasLyrics(language: Int) = languages[language].lyrics.any(String::isNotBlank)
        val extras = (1 until languages.size).filter { !languages[it].isEmpty }
        return TranslationProblems(
            mismatchedSections = mismatchCount(song),
            // The primary's title is the song's own and never blank: a file without one is named for it.
            untitledLanguages = extras.filter { languages[it].title.isBlank() }.map { it + 1 },
            // A song that is only a title so far is not a problem; one whose other languages have words
            // and whose primary has none is.
            lyriclessLanguages = (listOf(0).filter { extras.any(::hasLyrics) } + extras)
                .filterNot(::hasLyrics)
                .map { it + 1 },
        )
    }

    private fun List<String>.trimBlankEdges(): List<String> =
        dropWhile(String::isBlank).dropLastWhile(String::isBlank)
}
