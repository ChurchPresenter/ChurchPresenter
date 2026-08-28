package org.churchpresenter.converter.song

import java.io.File

data class VideoPsalmSong(
    val number: String,
    val title: String,
    val author: String = "",
    val composer: String = "",
    val copyright: String = "",
    val ccli: String = "",
    val sequence: List<String> = emptyList(),
    val sections: List<SongSection> = emptyList(),
)

data class VideoPsalmBook(
    val title: String,
    val songs: List<VideoPsalmSong>,
)

/**
 * VideoPsalm song books: one file — near-JSON, read by [LooseJson] — holding a whole numbered book.
 *
 * A verse says what kind of section it is with a numeric `Tag` and which one of that kind with an
 * `ID`, and the book repeats a chorus in full every time it is sung, so the array is the song as
 * performed rather than its structure. Three things follow, and each of them loses or mislabels
 * sections when it is skipped:
 *
 *  - **The repeats have to go.** A `.song` file lists each section once and the presenter picks the
 *    order, so a hymn with four verses arrives as four verses and one chorus, not as eight slides.
 *    Identical lyrics are what identifies a repeat — the ids do not: a book writes the same chorus
 *    as `C1` four times and then as `C2`, and it is the same chorus every time.
 *  - **`ID` runs out.** Real books stop writing it past the ninth verse, so verses ten onward all
 *    claim to be `ID` 1. Numbering each section as the first free slot of its kind restores 10, 11
 *    and 12 instead of stacking three "Verse 1"s.
 *  - **The last verse carries the book's end marker** — a line of `***` or `<><><>` — which is
 *    punctuation for the operator, not a lyric.
 *
 * `Sequence` ("V1 C1 V2 C1 V3 C2") is read for the preview only. It is authored separately from the
 * verses and drifts from them — 117 of the 2,564 songs in one real book disagree — so it is not
 * something to label sections from.
 */
object VideoPsalmConverter {

    /**
     * VideoPsalm's verse kinds, in the order its own editor lists them, which is what `Tag` indexes.
     *
     * Only 0 and 1 are confirmed against real books; the rest are the documented list in order. A
     * tag outside it keeps the section rather than dropping it — see [NUMBERED_SECTION].
     */
    private val tagNames = listOf(
        "Verse", "Chorus", "Pre-Chorus", "Bridge", "Tag",
        "Intro", "Outro", "Slide", "Instrumental", "Other",
    )

    /** The letters `Sequence` names those same kinds with — `r` is *refrain*, the French chorus. */
    private val sequenceTags = mapOf(
        'v' to 0, 'c' to 1, 'r' to 1, 'p' to 2, 'b' to 3,
        't' to 4, 'e' to 5, 'o' to 6, 's' to 7, 'i' to 8, 'n' to 9,
    )

    private const val NUMBERED_SECTION = "Section"

    /** A line of nothing but `*` or `<>`: how a book marks the end of a song inside its last verse. */
    private val endMarker = Regex("""^[*<>\s]+$""")

    private val runsOfWhitespace = Regex("""\s+""")
    private val sequenceSeparators = Regex("""[\s,]+""")

    fun parse(file: File): VideoPsalmBook {
        val book = LooseJson.parse(file.readText(Charsets.UTF_8))
        return VideoPsalmBook(book.text("Text").trim(), book.children("Songs").map { songOf(it) })
    }

    fun convert(input: File, outputDir: File): SongConversionResult {
        val book = parse(input)
        if (book.songs.isEmpty()) {
            return SongConversionResult(emptyList(), listOf("No songs in ${input.name}"))
        }
        val folder = File(outputDir, targetFolderName(input))
        val taken = mutableSetOf<String>()
        val written = book.songs.map { song ->
            SongOutput.write(
                folder,
                ParsedSong(
                    title = song.title,
                    author = song.author,
                    copyright = song.copyright,
                    composer = song.composer,
                    ccli = song.ccli,
                    sections = song.sections,
                ),
                taken,
                song.number,
            )
        }
        return SongConversionResult(written)
    }

    /** The book's own title names the folder, since that is the song book name the app then shows. */
    fun targetFolderName(input: File): String {
        val title = runCatching { SongOutput.sanitizeName(parse(input).title) }.getOrDefault("")
        return title.ifBlank { input.nameWithoutExtension }
    }

    private fun songOf(node: LooseJson): VideoPsalmSong = VideoPsalmSong(
        // `Alias` is the number the book prints; `ID` is its position, and they differ in a book
        // that numbers from something other than one.
        number = node.text("Alias").ifBlank { node.text("ID") }.trim(),
        title = flattened(node.text("Text")),
        author = flattened(node.text("Author")),
        composer = flattened(node.text("Composer")),
        copyright = flattened(node.text("Copyright")),
        ccli = node.text("CCLI").trim(),
        sequence = sequenceLabels(node.text("Sequence")),
        sections = sectionsOf(node.children("Verses")),
    )

    /** One line: an author or a copyright is stored with the line breaks its slide had. */
    private fun flattened(value: String): String = value.replace(runsOfWhitespace, " ").trim()

    internal fun sectionsOf(verses: List<LooseJson>): List<SongSection> {
        val alreadySung = mutableSetOf<String>()
        val used = mutableSetOf<String>()
        val sections = mutableListOf<SongSection>()
        for (verse in verses) {
            val lines = linesOf(verse.text("Text"))
            if (lines.isEmpty()) continue
            if (!alreadySung.add(lines.joinToString("\n").lowercase())) continue
            sections.add(SongSection(labelOf(verse, used), lines))
        }
        val tidied = SectionLabel.tidy(sections.map { it.label })
        return sections.mapIndexed { index, section -> section.copy(label = tidied[index]) }
    }

    /** This verse's kind and the first number of that kind [used] has not claimed. */
    private fun labelOf(verse: LooseJson, used: MutableSet<String>): String {
        val tag = verse.text("Tag").toIntOrNull() ?: 0
        val name = tagNames.getOrElse(tag) { "$NUMBERED_SECTION $tag" }
        var number = verse.text("ID").toIntOrNull() ?: 1
        while (!used.add("$name $number")) number++
        return "$name $number"
    }

    private fun linesOf(text: String): List<String> =
        text.lines().map { it.trim() }.filterNot { it.isEmpty() || endMarker.matches(it) }

    /** Turns `V1 C1 V2 C2` into the section names it calls for. */
    internal fun sequenceLabels(sequence: String): List<String> =
        sequence.split(sequenceSeparators).filter { it.isNotBlank() }.map { token ->
            val letters = token.takeWhile { it.isLetter() }.lowercase()
            val digits = token.dropWhile { it.isLetter() }.filter { it.isDigit() }
            // A single letter and nothing else: `Coda` opens with one the map knows and is a
            // section name the book wrote out, not a chorus.
            val name = letters.singleOrNull()?.let { sequenceTags[it] }?.let { tagNames[it] }
            when {
                name == null -> SectionLabel.of(token)
                digits.isEmpty() -> name
                else -> "$name $digits"
            }
        }
}
