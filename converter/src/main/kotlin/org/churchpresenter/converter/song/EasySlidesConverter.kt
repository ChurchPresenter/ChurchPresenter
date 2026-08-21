package org.churchpresenter.converter.song

import org.w3c.dom.Element
import java.io.File

data class EasySlidesSong(
    val title: String,
    val author: String,
    val copyright: String,
    val number: String,
    val sequence: List<String>,
    val sections: List<SongSection>,
)

/**
 * EasySlides XML exports: one file holding a whole library as a list of `<Item>` elements.
 *
 * The lyrics live in `<Contents>` as plain text with `[V1]`-style markers, alongside `[region N]`
 * lines that are layout instructions rather than sections and have to be dropped. An export whose
 * `<Contents>` carries no markers at all is not malformed — it is the common case for a library
 * typed in by hand — so blank lines are read as the verse breaks instead.
 *
 * `<Sequence>` is EasySlides' verse order, written as single letters rather than words: `c` is the
 * chorus and `t` the *second* chorus, `p`/`q` the two pre-choruses, `b`/`w` the two bridges, `e` the
 * ending, and a bare number is that verse. Reading `t` as "Tag" would put the wrong section on
 * screen, which is why the mapping is spelled out here.
 */
object EasySlidesConverter {

    private val regionMarker = Regex("""^\[region\b""", RegexOption.IGNORE_CASE)
    private val sectionMarker = Regex("""^\[([^]]*)]""")
    private val sequenceSeparators = Regex("""[\s,]+""")

    private val sequenceNames = mapOf(
        "c" to "Chorus", "t" to "Chorus 2",
        "p" to "Pre-Chorus", "q" to "Pre-Chorus 2",
        "b" to "Bridge", "w" to "Bridge 2",
        "e" to "Ending", "i" to "Intro", "o" to "Outro",
    )

    fun parse(file: File): List<EasySlidesSong> =
        parseXmlRoot(readXmlText(file)).descendants("Item").map { songOf(it) }

    fun convert(input: File, outputDir: File): SongConversionResult {
        val songs = parse(input)
        if (songs.isEmpty()) return SongConversionResult(emptyList(), listOf("No <Item> songs in ${input.name}"))
        val taken = mutableSetOf<String>()
        val written = songs.map { song ->
            SongOutput.write(
                outputDir,
                ParsedSong(song.title, song.author, song.copyright, sections = song.sections),
                taken,
                song.number,
            )
        }
        return SongConversionResult(written)
    }

    private fun songOf(item: Element): EasySlidesSong {
        val copyright = listOf("Copyright", "LicenceAdmin1", "LicenceAdmin2")
            .map { item.childText(it) }.filter { it.isNotBlank() }.joinToString(" ")
        return EasySlidesSong(
            title = item.childText("Title1").ifBlank { item.childText("Title2") },
            author = item.childText("Writer").ifBlank { item.childText("Author") },
            copyright = copyright,
            number = item.childText("SongNumber"),
            sequence = sequenceLabels(item.childText("Sequence")),
            sections = sectionsOf(item.childText("Contents")),
        )
    }

    /** Turns `1,c,2,c` into the section labels it names. */
    internal fun sequenceLabels(sequence: String): List<String> =
        sequence.split(sequenceSeparators).filter { it.isNotBlank() }.map { token ->
            val digits = token.filter { it.isDigit() }
            val letters = token.filter { it.isLetter() }.lowercase()
            when {
                letters.isEmpty() -> "Verse $digits"
                else -> sequenceNames[letters] ?: SectionLabel.of(token)
            }
        }

    /** Splits `<Contents>` into sections, by its markers when it has any and by blank lines when not. */
    internal fun sectionsOf(contents: String): List<SongSection> {
        val lines = contents.lines().filterNot { regionMarker.containsMatchIn(it.trim()) }
        val hasMarkers = lines.any { sectionMarker.containsMatchIn(it.trim()) }
        return if (hasMarkers) byMarkers(lines) else byBlankLines(lines)
    }

    private fun byMarkers(lines: List<String>): List<SongSection> {
        val collected = LinkedHashMap<String, MutableList<String>>()
        var marker = "V1"
        for (raw in lines) {
            val trimmed = raw.trim()
            val opened = sectionMarker.find(trimmed)?.groupValues?.get(1)?.trim()
            when {
                opened != null -> marker = opened.ifEmpty { marker }
                trimmed.isEmpty() -> Unit
                else -> collected.getOrPut(marker) { mutableListOf() }.add(trimmed)
            }
        }
        val labels = SectionLabel.tidy(collected.keys.map { SectionLabel.of(it) })
        return collected.values.mapIndexed { index, body -> SongSection(labels[index], body) }
    }

    private fun byBlankLines(lines: List<String>): List<SongSection> {
        val blocks = mutableListOf<MutableList<String>>()
        for (raw in lines) {
            val trimmed = raw.trim()
            when {
                trimmed.isEmpty() -> if (blocks.lastOrNull()?.isNotEmpty() == true) blocks.add(mutableListOf())
                else -> (blocks.lastOrNull() ?: mutableListOf<String>().also { blocks.add(it) }).add(trimmed)
            }
        }
        return blocks.filter { it.isNotEmpty() }.mapIndexed { index, body -> SongSection("Verse ${index + 1}", body) }
    }
}
