package converter.song

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
 * newline-separated. Older packs store the whole song as plain text in one `<lyrics>` element, which
 * is read by blank-line blocks.
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

    internal fun sectionsOf(lyrics: Element?): List<SongSection> {
        if (lyrics == null) return emptyList()
        val sections = lyrics.childElements("section")
        return if (sections.isNotEmpty()) sections.mapIndexed { index, section ->
            val body = (section.childElement("lyrics") ?: section).textWithBreaks()
            SongSection(
                section.getAttribute("title").trim().ifBlank { "Verse ${index + 1}" },
                body.lines().map { it.trim() }.filter { it.isNotEmpty() },
            )
        } else {
            blocksOf(lyrics.textWithBreaks())
        }
    }

    private fun blocksOf(text: String): List<SongSection> {
        val blocks = text.split(Regex("""\n\s*\n"""))
            .map { block -> block.lines().map { it.trim() }.filter { it.isNotEmpty() } }
            .filter { it.isNotEmpty() }
        return blocks.mapIndexed { index, lines -> SongSection("Verse ${index + 1}", lines) }
    }
}
