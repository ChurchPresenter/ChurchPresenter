package converter.song

import converter.library.RtfText
import org.w3c.dom.Element
import java.io.File
import java.util.Base64

data class ProPresenterSong(
    val title: String,
    val author: String,
    val copyright: String,
    val ccli: String,
    val sections: List<SongSection>,
)

/**
 * ProPresenter documents, versions 4 through 7.
 *
 * Two entirely different files wear the same product name. Versions 4, 5 and 6 are XML, and version
 * 7 is protocol buffers — so `.pro4`/`.pro5`/`.pro6` and `.pro` share only the lyrics themselves,
 * which in every version are **RTF**, not text (see [RtfText] for why that matters beyond stripping
 * a few backslashes).
 *
 * ### The XML versions
 *
 * The nesting differs per version — v4 lists slides directly, v5 groups them, v6 renames every
 * container to `<array rvXMLIvarName="…">` — so rather than three parsers this reads whichever
 * shape is present: slide groups where there are any, loose slides where there are not. That also
 * keeps the two spellings of the RTF payload in one place: v4 and v5 put it in an `RTFData`
 * *attribute*, v6 in the *text* of an `<NSString rvXMLIvarName="RTFData">`. Both are base64.
 *
 * The metadata attributes were renamed between v5 and v6 (`CCLICopyrightInfo` became
 * `CCLICopyrightYear`, `CCLILicenseNumber` became `CCLISongNumber`), so both spellings are read and
 * whichever is present wins.
 *
 * ### Version 7
 *
 * The field numbers below are the path through ProPresenter's own message definitions. Slide order
 * is **not** the order the cues appear in: `cue_groups` holds the arrangement — the group name that
 * becomes the section label, and the identifiers of the cues in it — so the cues are indexed by
 * UUID and then walked in group order. A song whose cues were read in file order would come out
 * with its verses shuffled.
 */
// Split into one small function per step, which is what keeps the readers below within the
// complexity and nesting limits. Splitting the object itself would scatter one file format across
// several files instead.
@Suppress("TooManyFunctions")
object ProPresenterConverter {

    // Presentation
    private const val PRESENTATION_NAME = 3
    private const val PRESENTATION_CUE_GROUPS = 12
    private const val PRESENTATION_CUES = 13
    private const val PRESENTATION_CCLI = 14

    // Presentation.CueGroup / Group
    private const val CUE_GROUP_GROUP = 1
    private const val CUE_GROUP_CUE_IDENTIFIERS = 2
    private const val GROUP_NAME = 2

    // Presentation.CCLI
    private const val CCLI_AUTHOR = 1
    private const val CCLI_ARTIST_CREDITS = 2
    private const val CCLI_SONG_TITLE = 3
    private const val CCLI_PUBLISHER = 4
    private const val CCLI_SONG_NUMBER = 6

    // Cue → Action → slide → text
    private const val CUE_UUID = 1
    private const val CUE_ACTIONS = 10
    private const val ACTION_SLIDE = 23
    private const val SLIDE_TYPE_PRESENTATION = 2
    private const val PRESENTATION_SLIDE_BASE = 1
    private const val SLIDE_ELEMENTS = 1
    private const val SLIDE_ELEMENT_GRAPHICS = 1
    private const val GRAPHICS_ELEMENT_TEXT = 13
    private const val GRAPHICS_TEXT_RTF = 5

    /** `UUID { string string = 1; }` — the identifier is a plain string field inside a wrapper. */
    private const val UUID_STRING = 1

    private val XML_EXTENSIONS = setOf("pro4", "pro5", "pro6")

    fun parse(file: File): ProPresenterSong =
        if (file.extension.lowercase() in XML_EXTENSIONS) parseXml(file) else parseProto(file)

    fun convert(input: File, outputFile: File) {
        val song = parse(input)
        val parsed = ParsedSong(song.title, song.author, song.copyright, sections = song.sections)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(MarkdownToSongConverter.buildSongContent(parsed), Charsets.UTF_8)
    }

    // --- ProPresenter 4/5/6: XML ---

    private fun parseXml(file: File): ProPresenterSong {
        val root = xmlRootOf(file, "RVPresentationDocument")
        val title = root.getAttribute("CCLISongTitle").trim().ifBlank { file.nameWithoutExtension }
        val author = firstAttribute(root, "CCLIAuthor", "author", "CCLIArtistCredits", "artist")
        // Renamed between v5 and v6; whichever the document carries is the one that means it.
        val copyright = firstAttribute(root, "CCLICopyrightInfo", "CCLICopyrightYear")
        val ccli = firstAttribute(root, "CCLILicenseNumber", "CCLISongNumber")

        val groups = root.descendants("RVSlideGrouping")
        val labelled = if (groups.isNotEmpty()) {
            groups.flatMap { group ->
                val name = group.getAttribute("name").trim()
                group.descendants("RVDisplaySlide").map { name to it }
            }
        } else {
            // v4 has no groups at all; a slide's own label is the only name it has.
            root.descendants("RVDisplaySlide").map { it.getAttribute("label").trim() to it }
        }

        val slides = labelled.map { (name, slide) -> name to slideRtf(slide) }
        return ProPresenterSong(title, author, copyright, ccli, sectionsOf(slides))
    }

    /** The first of [names] the element actually carries a value for. */
    private fun firstAttribute(element: Element, vararg names: String): String =
        names.firstNotNullOfOrNull { element.getAttribute(it).trim().ifBlank { null } } ?: ""

    /**
     * The slide's RTF, from whichever of the two places its version keeps it.
     *
     * A slide with no text element at all is normal — it is an image or a blank — and comes back
     * empty rather than raising, so one picture in a song does not fail the whole file.
     */
    private fun slideRtf(slide: Element): String {
        for (text in slide.descendants("RVTextElement")) {
            // v4/v5: the payload is an attribute of the text element.
            val attribute = text.getAttribute("RTFData").trim()
            if (attribute.isNotEmpty()) return decodeRtf(attribute)
            // v6: it is the text of a child <NSString rvXMLIvarName="RTFData">.
            val nsString = text.descendants("NSString")
                .firstOrNull { it.getAttribute("rvXMLIvarName") == "RTFData" }
            if (nsString != null) return decodeRtf(nsString.textContent.trim())
        }
        return ""
    }

    private fun decodeRtf(base64: String): String {
        val bytes = runCatching { Base64.getMimeDecoder().decode(base64) }.getOrNull() ?: return ""
        return RtfText.toPlainText(bytes.toString(Charsets.ISO_8859_1))
    }

    // --- ProPresenter 7: protocol buffers ---

    private fun parseProto(file: File): ProPresenterSong {
        val document = ProtoMessage.parseOrNull(file.readBytes())
            ?: throw IllegalArgumentException("Not a ProPresenter 7 document")

        val ccli = document.message(PRESENTATION_CCLI)
        val title = ccli?.string(CCLI_SONG_TITLE)?.trim().orElse(
            document.string(PRESENTATION_NAME)?.trim().orElse(file.nameWithoutExtension)
        )
        val author = listOfNotNull(ccli?.string(CCLI_AUTHOR), ccli?.string(CCLI_ARTIST_CREDITS))
            .map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: ""
        val copyright = ccli?.string(CCLI_PUBLISHER)?.trim() ?: ""
        val number = ccli?.number(CCLI_SONG_NUMBER)?.takeIf { it > 0 }?.toString() ?: ""

        // Index every cue's slide text by the cue's UUID, then walk the arrangement.
        val textByCue = HashMap<String, String>()
        for (cue in document.messages(PRESENTATION_CUES)) {
            val id = cue.message(CUE_UUID)?.string(UUID_STRING) ?: continue
            textByCue[id] = cueText(cue)
        }

        val slides = mutableListOf<Pair<String, String>>()
        for (cueGroup in document.messages(PRESENTATION_CUE_GROUPS)) {
            val name = cueGroup.message(CUE_GROUP_GROUP)?.string(GROUP_NAME)?.trim() ?: ""
            for (identifier in cueGroup.messages(CUE_GROUP_CUE_IDENTIFIERS)) {
                val id = identifier.string(UUID_STRING) ?: continue
                slides.add(name to textByCue.getOrDefault(id, ""))
            }
        }
        // A document with no arrangement still has cues; file order is all there is to go on.
        if (slides.isEmpty()) {
            document.messages(PRESENTATION_CUES).forEach { slides.add("" to cueText(it)) }
        }

        return ProPresenterSong(title, author, copyright, number, sectionsOf(slides))
    }

    /** The text of the first slide any of a cue's actions puts on screen. */
    private fun cueText(cue: ProtoMessage): String {
        for (action in cue.messages(CUE_ACTIONS)) {
            val slide = action.message(ACTION_SLIDE)
                ?.message(SLIDE_TYPE_PRESENTATION)
                ?.message(PRESENTATION_SLIDE_BASE)
                ?: continue
            for (element in slide.messages(SLIDE_ELEMENTS)) {
                val rtf = element.message(SLIDE_ELEMENT_GRAPHICS)
                    ?.message(GRAPHICS_ELEMENT_TEXT)
                    ?.bytes(GRAPHICS_TEXT_RTF)
                    ?.firstOrNull()
                    ?: continue
                val text = RtfText.toPlainText(rtf.toString(Charsets.ISO_8859_1)).trim()
                if (text.isNotEmpty()) return text
            }
        }
        return ""
    }

    // --- Shared ---

    /**
     * Turns `(group name, slide text)` pairs into sections, dropping what is not a lyric.
     *
     * Empty slides are not a defect to report: every ProPresenter song written from the stock
     * template opens with an empty "Blank" group, and a song's own image slides carry no text
     * either. Numbering is assigned after the drop, so the verses come out 1, 2, 3.
     */
    private fun sectionsOf(slides: List<Pair<String, String>>): List<SongSection> {
        val bodies = slides.map { (name, text) -> name to lyricLines(text) }.filter { it.second.isNotEmpty() }
        val names = bodies.map { (name, _) -> name.takeIf { isSectionName(it, bodies.map { pair -> pair.first }) } }
        return LyricBlocks.labels(names).mapIndexed { index, label -> SongSection(label, bodies[index].second) }
    }

    /**
     * Whether a group name says which section this is, rather than just naming the song.
     *
     * ProPresenter groups are free text and default to one group called "Song" or "Blank" holding
     * everything, so a document can carry a name on every slide and still have no structure at all.
     * A name is taken as a section only when it maps onto a known one, or when the document uses
     * more than one name — which is what tells a deliberate `Antiphon`/`Refrain` arrangement apart
     * from a whole song sitting in `Song`.
     */
    private fun isSectionName(name: String, all: List<String>): Boolean {
        if (name.isBlank() || name.equals("Blank", ignoreCase = true)) return false
        return SectionLabel.of(name) != name || all.filter { it.isNotBlank() }.distinct().size > 1
    }

    /**
     * The lines of a slide that are actually sung.
     *
     * A line with no letter or digit in it is not a lyric. ProPresenter writes a stray punctuation
     * run ahead of the text on every slide of some documents — the reference parser reproduces the
     * same `',`, so it is in the file rather than a fault in reading it — and it would otherwise be
     * the first line of every converted section.
     */
    private fun lyricLines(text: String): List<String> =
        text.lines().map { it.trim() }.filter { line -> line.any { it.isLetterOrDigit() } }

    private fun String?.orElse(fallback: String): String =
        this?.takeIf { it.isNotEmpty() } ?: fallback
}
