package org.churchpresenter.converter.song

import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile

data class QueleaSong(
    val title: String,
    val author: String,
    val copyright: String,
    val ccli: String,
    val sequence: List<String>,
    val sections: List<SongSection>,
)

/**
 * Quelea song packs (`.qsp`) and the loose `<song>` XML files they are made of.
 *
 * A pack is an ordinary zip holding one XML document per song. Its entries are **not** filtered by
 * name here on purpose: Quelea writes a `.pdf` extension onto the second and later songs that share
 * a title, so a pack of hymns can hide half its songs behind the wrong extension. Every entry is
 * offered to the parser instead and the ones that are not songs are reported, not dropped silently.
 *
 * Inside, each section is `<section title="Verse 1"><lyrics>…</lyrics></section>` with its lines
 * newline-separated. The title is Quelea's own numbering rather than the section's name, and the
 * body carries both the real heading and any chords — see [sectionsOf].
 */
object QueleaConverter {

    private const val ZIP_MARK_1 = 'P'.code.toByte()
    private const val ZIP_MARK_2 = 'K'.code.toByte()

    fun parse(file: File): List<QueleaSong> =
        if (isZip(file)) parsePack(file).first else listOf(songOf(xmlRootOf(file, "song")))

    fun convert(input: File, outputDir: File): SongConversionResult {
        val (songs, errors) =
            if (isZip(input)) parsePack(input) else parse(input) to emptyList<String>()
        if (songs.isEmpty()) {
            return SongConversionResult(emptyList(), errors.ifEmpty { listOf("No songs in ${input.name}") })
        }
        val taken = mutableSetOf<String>()
        val written = songs.map { song ->
            val parsed = ParsedSong(song.title, song.author, song.copyright, sections = song.sections)
            SongOutput.write(outputDir, parsed, taken)
        }
        return SongConversionResult(written, errors)
    }

    internal fun isZip(file: File): Boolean = file.inputStream().use { stream ->
        val head = ByteArray(2)
        stream.read(head) == 2 && head[0] == ZIP_MARK_1 && head[1] == ZIP_MARK_2
    }

    /** Every entry of a pack that parses as a song, plus a line for each that does not. */
    private fun parsePack(file: File): Pair<List<QueleaSong>, List<String>> {
        val songs = mutableListOf<QueleaSong>()
        val errors = mutableListOf<String>()
        ZipFile(file).use { zip ->
            for (entry in zip.entries().toList().sortedBy { it.name }) {
                if (entry.isDirectory) continue
                val text = zip.getInputStream(entry).use { decodeXmlText(it.readBytes()) }
                val root = runCatching { parseXmlRoot(text) }.getOrNull()
                if (root != null && root.tagName.equals("song", ignoreCase = true)) {
                    songs.add(songOf(root))
                } else {
                    errors.add("Skipped ${entry.name}: not a Quelea song")
                }
            }
        }
        return songs to errors
    }

    private fun songOf(root: Element): QueleaSong = QueleaSong(
        title = root.childText("title"),
        author = root.childText("author"),
        copyright = listOf(root.childText("copyright"), root.childText("year"), root.childText("publisher"))
            .filter { it.isNotBlank() }.joinToString(" "),
        ccli = root.childText("ccli"),
        sequence = root.childText("sequence").split(Regex("""[\s,]+""")).filter { it.isNotBlank() },
        sections = sectionsOf(root.childElement("lyrics")),
    )

    /**
     * The song's sections, named by what the lyrics say rather than by what the file claims.
     *
     * Quelea's `title=` attribute is not the section's name — it numbers the sections in order, so a
     * chorus in third place is stored as `Verse 3`. The name the person who entered the song wrote
     * is the first line of the body, and 570 of the 3,134 songs in Quelea's own English pack
     * disagree with the attribute that way. So a heading found in the body wins, and once one
     * section has one the attribute is dropped for the whole song — mixing the two renumbers the
     * sections that have no heading against a scale the rest no longer use.
     *
     * Older packs store the whole song as plain text in one `<lyrics>` element, read by blank-line
     * blocks.
     */
    internal fun sectionsOf(lyrics: Element?): List<SongSection> {
        if (lyrics == null) return emptyList()
        val sections = lyrics.childElements("section")
        if (sections.isEmpty()) return LyricBlocks.split(lyrics.textWithBreaks())

        val read = sections.mapNotNull { section ->
            val lines = (section.childElement("lyrics") ?: section).textWithBreaks()
                .lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            val heading = lines.firstOrNull()?.let { LyricBlocks.headingOf(it) }
            val body = (if (heading == null) lines else lines.drop(1)).map(::lyricLine)
            // A section that is nothing but a heading points at one written out elsewhere — Quelea
            // songs repeat a chorus as a bare `[Chorus]` — and has no lyrics of its own to show.
            if (body.isEmpty()) null else Section(heading, section.getAttribute("title").trim(), body)
        }

        val named = read.any { it.heading != null }
        val names = read.map { section -> section.heading ?: section.title.takeIf { !named } }
        return LyricBlocks.labels(names).mapIndexed { index, label -> SongSection(label, read[index].body) }
    }

    private data class Section(val heading: String?, val title: String, val body: List<String>)

    /** Chords are written above the words with nothing marking them as chords, so they are read. */
    private fun lyricLine(line: String): String =
        if (ChordLines.isChordLine(line)) ChordLines.bracket(line) else line.trim()
}
