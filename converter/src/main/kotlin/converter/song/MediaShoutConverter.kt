package converter.song

import converter.library.RtfText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

data class MediaShoutSong(
    val title: String,
    val sections: List<SongSection>,
)

/**
 * MediaShout 7 scripts — `.sc7x` with its media embedded, `.sc7` without.
 *
 * A script is three things concatenated: a twenty-byte header of offsets, a PNG thumbnail, and a
 * zip. The zip holds `scriptModel.json`, which is the script itself — the two are separated by a
 * null byte, and both are located through the header rather than by scanning, because a PNG can
 * contain anything a scan would trip over.
 *
 * Inside the JSON, a script is a list of cues; a cue of type 1 is a song, its pages are the slides,
 * and a page's text item holds **RTF**, not text. The `.sc7` variant differs only in what media it
 * carries, so both are read by the same path.
 *
 * The JSON is walked as a tree rather than deserialized into classes: it is a .NET object graph with
 * `$type`/`$id`/`$ref` annotations throughout and hundreds of presentation properties, of which a
 * lyric import wants four. Modelling the rest would be a large surface that breaks whenever
 * MediaShout adds a field.
 */
// Split into one small function per step, which is what keeps the readers below within the
// complexity and nesting limits. Splitting the object itself would scatter one file format across
// several files instead.
@Suppress("TooManyFunctions")
object MediaShoutConverter {

    private const val HEADER_SIZE = 20
    private const val MAGIC = "sc7x"
    private const val ZIP_OFFSET_AT = 12
    private const val ZIP_LENGTH_AT = 16
    private const val SCRIPT_MODEL = "scriptModel.json"

    private const val CUE_TYPE_LYRIC = 1
    private const val TEXT_ITEM = "VisualItem+Text"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun isScript(file: File): Boolean {
        if (!file.isFile || file.length() < HEADER_SIZE) return false
        val magic = ByteArray(MAGIC.length)
        file.inputStream().use { it.read(magic) }
        return String(magic, Charsets.US_ASCII) == MAGIC
    }

    fun parse(file: File): List<MediaShoutSong> {
        val bytes = file.readBytes()
        require(bytes.size >= HEADER_SIZE) { "${file.name} is too small to be a MediaShout script" }
        require(String(bytes, 0, MAGIC.length, Charsets.US_ASCII) == MAGIC) {
            "${file.name} is not a MediaShout script"
        }

        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val zipOffset = header.getInt(ZIP_OFFSET_AT)
        val zipLength = header.getInt(ZIP_LENGTH_AT)
        require(zipOffset in HEADER_SIZE until bytes.size && zipLength > 0) {
            "${file.name} does not point at a readable archive"
        }

        val model = readEntry(bytes, zipOffset, zipLength.coerceAtMost(bytes.size - zipOffset), SCRIPT_MODEL)
            ?: throw IllegalArgumentException("No $SCRIPT_MODEL in ${file.name}")
        return songsOf(json.parseToJsonElement(model.toString(Charsets.UTF_8)) as? JsonObject ?: JsonObject(emptyMap()))
    }

    fun convert(input: File, outputDir: File): SongConversionResult {
        val songs = runCatching { parse(input) }.getOrElse { error ->
            return SongConversionResult(emptyList(), listOf("${input.name}: ${error.message}"))
        }
        if (songs.isEmpty()) return SongConversionResult(emptyList(), listOf("No songs in ${input.name}"))
        val taken = mutableSetOf<String>()
        val written = songs.map { song ->
            SongOutput.write(outputDir, ParsedSong(song.title, sections = song.sections), taken)
        }
        return SongConversionResult(written)
    }

    /** Every lyric cue in the script, in the order the service runs them. */
    internal fun songsOf(script: JsonObject): List<MediaShoutSong> =
        (script["Cues"] as? JsonArray).orEmpty()
            .filterIsInstance<JsonObject>()
            .filter { number(it["Properties"] as? JsonObject, "Type") == CUE_TYPE_LYRIC }
            .mapNotNull { cue ->
                val properties = cue["Properties"] as? JsonObject
                val sections = sectionsOf(cue["Pages"] as? JsonArray)
                if (sections.isEmpty()) return@mapNotNull null
                MediaShoutSong(text(properties, "Name").ifBlank { "Song" }, sections)
            }

    /** One section per page, named by whatever the operator called it in the sidebar. */
    private fun sectionsOf(pages: JsonArray?): List<SongSection> {
        val sections = pages.orEmpty().filterIsInstance<JsonObject>().mapNotNull { pageSection(it) }
        return LyricBlocks.labels(sections.map { it.first })
            .mapIndexed { index, label -> SongSection(label, sections[index].second) }
    }

    /**
     * One page as the name it offers and the lines on it, or null when it is not part of the song.
     *
     * A page the operator marked skipped, or one holding a picture rather than words, is not
     * presented and so is not a section.
     */
    private fun pageSection(page: JsonObject): Pair<String?, List<String>>? {
        val properties = page["Properties"] as? JsonObject
        if ((properties?.get("IsSkipped") as? JsonPrimitive)?.contentOrNull == "true") return null

        val lines = pageText(page["Items"] as? JsonArray)
            ?.let { rtf -> RtfText.toPlainText(rtf).lines().map { it.trim() }.filter { it.isNotEmpty() } }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val name = text(properties, "CustomName").ifBlank { text(properties, "Name") }
        return name.takeIf { it.isNotBlank() && LyricBlocks.isLabel(it) } to lines
    }

    /** The RTF of a page's first text item that actually carries any. */
    private fun pageText(items: JsonArray?): String? =
        items.orEmpty().filterIsInstance<JsonObject>()
            .filter { (it["TypeId"] as? JsonPrimitive)?.contentOrNull == TEXT_ITEM }
            .firstNotNullOfOrNull { item ->
                text(item["Properties"] as? JsonObject, "Text").takeIf { it.isNotBlank() }
            }

    private fun text(properties: JsonObject?, key: String): String =
        (properties?.get(key) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    /**
     * A number that MediaShout may have written plainly or wrapped as `{"$type": …, "$value": N}`,
     * which is how Json.NET serialises a typed enum.
     */
    private fun number(properties: JsonObject?, key: String): Int? {
        return when (val value = properties?.get(key)) {
            is JsonPrimitive -> value.intOrNull
            is JsonObject -> (value["\$value"] as? JsonPrimitive)?.intOrNull
            else -> null
        }
    }

    /** The bytes of [name] from the zip embedded at [offset] in [bytes]. */
    private fun readEntry(bytes: ByteArray, offset: Int, length: Int, name: String): ByteArray? {
        ZipInputStream(ByteArrayInputStream(bytes, offset, length)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (entry.name.substringAfterLast('/').equals(name, ignoreCase = true)) {
                    val out = ByteArrayOutputStream()
                    zip.copyTo(out)
                    return out.toByteArray()
                }
            }
        }
    }

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}
