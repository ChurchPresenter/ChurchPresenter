package converter.song

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

data class FreeShowSong(
    val title: String,
    val author: String,
    val copyright: String,
    val number: String,
    val sections: List<SongSection>,
)

/**
 * FreeShow `.show` files — JSON, and shaped by how FreeShow presents rather than how a song reads.
 *
 * Three things have to be honoured or the lyrics come out shuffled or duplicated:
 *  - a file on disk is the pair `[id, show]`, not the show object itself, so the object has to be
 *    unwrapped before anything else;
 *  - `slides` is a **map**, whose iteration order is not the singing order. The order lives in
 *    `layouts[activeLayout].slides`, and a show can carry several layouts;
 *  - a slide with `group: null` is a continuation of the slide that lists it under `children`, so it
 *    belongs to that section rather than opening one of its own.
 */
object FreeShowConverter {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun parse(file: File): FreeShowSong {
        val show = showObject(json.parseToJsonElement(file.readText(Charsets.UTF_8)))
            ?: throw IllegalArgumentException("Not a FreeShow .show file")
        val meta = show["meta"] as? JsonObject
        return FreeShowSong(
            title = text(meta?.get("title")).ifBlank { text(show["name"]) },
            author = text(meta?.get("author")).ifBlank { text(meta?.get("artist")) },
            copyright = text(meta?.get("copyright")),
            number = text(meta?.get("number")),
            sections = sectionsOf(show),
        )
    }

    fun convert(input: File, outputFile: File) {
        val song = parse(input)
        val parsed = ParsedSong(
            title = song.title.ifBlank { input.nameWithoutExtension },
            author = song.author,
            copyright = song.copyright,
            sections = song.sections,
        )
        outputFile.writeText(MarkdownToSongConverter.buildSongContent(parsed), Charsets.UTF_8)
    }

    /** A `.show` on disk is `[id, show]`; a show pasted on its own is the object itself. */
    internal fun showObject(element: JsonElement): JsonObject? = when (element) {
        is JsonArray -> element.filterIsInstance<JsonObject>().firstOrNull()
        is JsonObject -> element
        else -> null
    }

    internal fun sectionsOf(show: JsonObject): List<SongSection> {
        val slides = show["slides"] as? JsonObject ?: return emptyList()
        val sections = mutableListOf<SongSection>()
        for (id in slideOrder(show, slides)) {
            val slide = slides[id] as? JsonObject ?: continue
            val lines = linesOf(slide).toMutableList()
            (slide["children"] as? JsonArray).orEmpty().forEach { child ->
                (slides[text(child)] as? JsonObject)?.let { lines.addAll(linesOf(it)) }
            }
            if (lines.isNotEmpty()) sections.add(SongSection(text(slide["group"]).ifBlank { "Verse" }, lines))
        }
        return sections.withLabels()
    }

    /** The active layout's order, falling back to the map's own order when no layout names it. */
    private fun slideOrder(show: JsonObject, slides: JsonObject): List<String> {
        val layouts = show["layouts"] as? JsonObject
        val active = text((show["settings"] as? JsonObject)?.get("activeLayout"))
        val layout = (layouts?.get(active) ?: layouts?.values?.firstOrNull()) as? JsonObject
        val ordered = (layout?.get("slides") as? JsonArray).orEmpty()
            .mapNotNull { text((it as? JsonObject)?.get("id")).takeIf { id -> id.isNotBlank() } }
        return ordered.ifEmpty { slides.keys.toList() }
    }

    /** Every text run of every text item on a slide, one output line per FreeShow line. */
    private fun linesOf(slide: JsonObject): List<String> =
        (slide["items"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().flatMap { item ->
            (item["lines"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().map { line ->
                (line["text"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
                    .joinToString("") { text(it["value"]) }
            }
        }.map { it.trim() }.filter { it.isNotEmpty() }

    private fun List<SongSection>.withLabels(): List<SongSection> {
        val labels = SectionLabel.tidy(map { SectionLabel.of(it.label) })
        return mapIndexed { index, section -> section.copy(label = labels[index]) }
    }

    private fun text(element: JsonElement?): String =
        (element as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty()

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
}
