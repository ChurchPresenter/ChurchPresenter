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
    /** The book's second language, one section per entry of [sections]; empty for a book with one. */
    val secondarySections: List<SongSection> = emptyList(),
)

/** A song's sections in each of its languages; [secondary] is empty for a book that has only one. */
data class BookSections(
    val primary: List<SongSection>,
    val secondary: List<SongSection> = emptyList(),
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
 *  - **The text carries the book's own colors.** A bilingual book wraps its second language in
 *    `<cAARRGGBB>` … `</c>`, which is styling from another program's slides — and which hides the
 *    end marker when the marker is written inside it. See [colorMarkup].
 *  - **A bilingual verse is one block with a rule through it.** The two languages are separated by
 *    a row of dashes inside the same verse, so they become the `.song` file's `[Primary]` and
 *    `[Secondary]` halves rather than one section with a line of dashes in the middle. See
 *    [languageSeparator].
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

    /**
     * VideoPsalm's inline color markup: `<cAARRGGBB>` … `</c>`, which a bilingual book wraps its
     * second language in to give it a color of its own.
     *
     * It is one program's slide styling rather than a lyric, and ChurchPresenter colors a song from
     * its own background settings — so it is dropped rather than carried across. Dropping it
     * also puts the end marker back within reach of [endMarker]: a book that writes the marker
     * inside the colour it just used, as `<cFF00D800>***</c>`, was leaving a line of asterisks at
     * the end of the last verse because the tags around it are not `*`, `<` or `>`.
     */
    private val colorMarkup = Regex("""</?c[0-9a-fA-F]*>""")

    /**
     * The rule a bilingual book draws between its two languages: a line of nothing but dashes,
     * inside the verse both languages share.
     *
     * Everything above it is the verse, everything below it the translation — which is what the
     * `.song` format keeps in separate halves, so the app can show either language or both.
     */
    private val languageSeparator = Regex("""^-{2,}$""")

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
                    secondarySections = song.secondarySections,
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

    private fun songOf(node: LooseJson): VideoPsalmSong {
        val sections = sectionsOf(node.children("Verses"))
        return VideoPsalmSong(
            // `Alias` is the number the book prints; `ID` is its position, and they differ in a book
            // that numbers from something other than one.
            number = node.text("Alias").ifBlank { node.text("ID") }.trim(),
            title = flattened(node.text("Text")),
            author = flattened(node.text("Author")),
            composer = flattened(node.text("Composer")),
            copyright = flattened(node.text("Copyright")),
            ccli = node.text("CCLI").trim(),
            sequence = sequenceLabels(node.text("Sequence")),
            sections = sections.primary,
            secondarySections = sections.secondary,
        )
    }

    /** One line: an author or a copyright is stored with the line breaks its slide had. */
    private fun flattened(value: String): String =
        withoutMarkup(value).replace(runsOfWhitespace, " ").trim()

    /** [text] with the book's color markup taken out. */
    private fun withoutMarkup(text: String): String = text.replace(colorMarkup, "")

    internal fun sectionsOf(verses: List<LooseJson>): BookSections {
        val alreadySung = mutableSetOf<String>()
        val used = mutableSetOf<String>()
        val primary = mutableListOf<SongSection>()
        val secondary = mutableListOf<SongSection>()
        for (verse in verses) {
            val lines = linesOf(verse.text("Text"))
            // A chorus stored once per singing is one section: the repeats are matched on their
            // lyrics — both languages of them, since a verse that differs only in translation is
            // still a different verse.
            if (lines.isEmpty() || !alreadySung.add(lines.joinToString("\n").lowercase())) continue
            val label = labelOf(verse, used)
            val rule = lines.indexOfFirst { languageSeparator.matches(it) }
            primary.add(SongSection(label, if (rule < 0) lines else lines.take(rule)))
            secondary.add(SongSection(label, if (rule < 0) emptyList() else lines.drop(rule + 1)))
        }
        val tidied = SectionLabel.tidy(primary.map { it.label })
        return BookSections(
            primary = primary.mapIndexed { index, section -> section.copy(label = tidied[index]) },
            // One for one with the primary sections and under the same labels: the app pairs the
            // two halves by position, so a verse the book never translated has to hold its place
            // as an empty section rather than be left out.
            secondary = if (secondary.any { it.lines.isNotEmpty() }) {
                secondary.mapIndexed { index, section -> section.copy(label = tidied[index]) }
            } else {
                emptyList()
            },
        )
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
        withoutMarkup(text).lines().map { it.trim() }.filterNot { it.isEmpty() || endMarker.matches(it) }

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
