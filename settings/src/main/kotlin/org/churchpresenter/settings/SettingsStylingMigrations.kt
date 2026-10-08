package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

// The raw-JSON steps that reshape song and Bible styling: the title slide's number (16), the
// section label (18), Bible offsets to boxes (19).

/** Version 19: an offset no further down the frame than this percentage becomes a box aligned to its top. */
private const val OFFSET_TOP_THIRD = 33

/** Version 19: an offset at least this far down the frame becomes a box aligned to its bottom. */
private const val OFFSET_BOTTOM_THIRD = 67

/** The flat song-number field each [SongCreditStyle] property of the title slide's is seeded from. */
private val titleSlideNumberSeedFields = mapOf(
    "color" to "Color",
    "fontType" to "FontType",
    "fontSize" to "FontSize",
    "bold" to "Bold",
    "italic" to "Italic",
    "underline" to "Underline",
    "strikethrough" to "Strikethrough",
    "shadow" to "Shadow",
    "shadowColor" to "ShadowColor",
    "shadowSize" to "ShadowSize",
    "shadowOpacity" to "ShadowOpacity",
    "horizontalAlignment" to "HorizontalAlignment",
    "letterSpacing" to "LetterSpacing",
    "wordSpacing" to "WordSpacing",
    "transform" to "Transform",
)

/**
 * Schema version 16. The title slide's song number has its own style now, where it used to share
 * the lyric slides' one, so every document is given the look it was actually drawing.
 *
 * Without this the split would silently reset the title slide to the defaults for anyone who had
 * styled their number at all -- the new record's own defaults are the *stock* number's, not
 * theirs. Seeded, nothing on screen changes and the two can then be pulled apart.
 *
 * Runs over the document's `songSettings` **and every profile's own copy of it**: a profile
 * carries a whole `SongSettings`, so seeding only the document would leave every output that has
 * ever been customised drawing stock styling. Corner and offset are deliberately not seeded --
 * their defaults mean "in the flow with the title", which is where the title slide's number has
 * always been.
 */
internal fun migrateTitleSlideNumberStyle(raw: String): String = mapEverySongSettings(raw, ::seedTitleSlideNumber)

/**
 * [transform] applied to the document's `songSettings` and to every profile's own copy of it --
 * a profile carries a whole `SongSettings`, so a song step that rewrites only the document leaves
 * every customised output behind.
 */
private fun mapEverySongSettings(raw: String, transform: (JsonObject) -> JsonObject): String {
    val root = parseSettingsRoot(raw) ?: return raw
    val seeded = root["songSettings"]?.jsonObject?.let(transform)
    val projection = root["projectionSettings"]?.jsonObject
    val profiles = projection?.get("outputProfiles")?.jsonArray
    val newProfiles = profiles?.map { element ->
        val profile = element as? JsonObject ?: return@map element
        val song = profile["songSettings"]?.jsonObject ?: return@map element
        JsonObject(profile + ("songSettings" to transform(song)))
    }
    var updated = root
    if (seeded != null) updated = JsonObject(updated + ("songSettings" to seeded))
    if (projection != null && newProfiles != null) {
        val newProjection = JsonObject(projection + ("outputProfiles" to JsonArray(newProfiles)))
        updated = JsonObject(updated + ("projectionSettings" to newProjection))
    }
    return updated.toString()
}

/**
 * Schema version 18. The section label is a song element with a whole style per output, where it
 * had a few flat fields shared by both, and an X/Y offset that its drag move replaces.
 *
 * The flat fields become the full-screen style and are copied to the lower third, which drew with
 * the same ones, so no label changes look. The offset is dropped: a label held on the lyrics has
 * no absolute place for it to mean, and the one the operator set floated the label off them.
 */
internal fun migrateSectionLabelStyle(raw: String): String = mapEverySongSettings(raw) { song ->
    val extras = song["layoutExtras"]?.jsonObject ?: return@mapEverySongSettings song
    val label = extras["sectionLabel"]?.jsonObject ?: return@mapEverySongSettings song
    // Written by a build that already has this: keep what it stored.
    if (label["fullScreen"] != null) return@mapEverySongSettings song
    // The old record's own defaults, for a document that left them out: the record they now
    // land in defaults to the credits' face and size, which the label never drew with.
    val oldDefaults = mapOf(
        "fontType" to JsonPrimitive(""),
        "fontSize" to JsonPrimitive(SongSectionLabel.DEFAULT_FONT_SIZE),
    )
    val style = JsonObject(oldDefaults + label.filterKeys { it in sectionLabelStyleFields })
    val migrated = JsonObject(
        label.filterKeys { it !in sectionLabelStyleFields && it != "offset" } +
            mapOf("fullScreen" to style, "lowerThird" to style),
    )
    JsonObject(song + ("layoutExtras" to JsonObject(extras + ("sectionLabel" to migrated))))
}

/**
 * Schema version 19. A Bible translation's verse text and reference are placed by a text box,
 * where they used to take an X/Y offset, so every offset becomes the box that places it.
 *
 * An offset put its element somewhere in the frame inside the margins -- the band's, on a lower
 * third -- by a percentage of the room left around it. The box it becomes covers that frame, so
 * [TextBoxOptions.insideMargins] is turned on for any page that had one, and places the text
 * by its old vertical percentage: the top, the middle or the bottom of the frame. Across, the
 * element's own alignment places it, as it does everywhere a box is used.
 *
 * Runs over the document's `bibleSettings` and every profile's own copy of it.
 */
internal fun migrateBibleOffsetsToBoxes(raw: String): String = mapEveryBibleSettings(raw) { bible ->
    val translations = bible["translations"]?.jsonArray ?: return@mapEveryBibleSettings bible
    val boxes = mutableMapOf<String, JsonElement>()
    val cleaned = translations.map { element ->
        val translation = element as? JsonObject ?: return@map element
        val fileName = translation["fileName"]?.jsonPrimitive?.contentOrNull.orEmpty()
        bibleOffsetFields.forEach { (field, item, lowerThird) ->
            val offset = translation[field] as? JsonObject ?: return@forEach
            boxes[textBoxKey(item, lowerThird, fileName)] = boxForOffset(offset)
        }
        JsonObject(translation.filterKeys { key -> bibleOffsetFields.none { it.first == key } })
    }
    if (boxes.isEmpty()) return@mapEveryBibleSettings bible
    val existing = bible["textBoxes"]?.jsonObject ?: JsonObject(emptyMap())
    val options = bible["textBoxOptions"]?.jsonObject ?: JsonObject(emptyMap())
    JsonObject(
        bible + mapOf(
            "translations" to JsonArray(cleaned),
            "textBoxes" to JsonObject(boxes + existing),
            "textBoxOptions" to JsonObject(options + ("insideMargins" to JsonPrimitive(true))),
        ),
    )
}

/** Each offset field version 19 turns into a box: the field, the box's item, and whether it is the band's. */
private val bibleOffsetFields = listOf(
    Triple("textOffset", BIBLE_TEXT_BOX, false),
    Triple("referenceOffset", BIBLE_REFERENCE_BOX, false),
    Triple("lowerThirdTextOffset", BIBLE_TEXT_BOX, true),
    Triple("lowerThirdReferenceOffset", BIBLE_REFERENCE_BOX, true),
)

/** The box covering the whole frame that places its text where [offset]'s vertical percentage did. */
private fun boxForOffset(offset: JsonObject): JsonObject {
    val y = offset["yPercent"]?.jsonPrimitive?.intOrNull ?: ElementOffset.CENTRE
    val vertical = when {
        y <= OFFSET_TOP_THIRD -> Constants.TOP
        y >= OFFSET_BOTTOM_THIRD -> Constants.BOTTOM
        else -> Constants.MIDDLE
    }
    return JsonObject(
        mapOf(
            "enabled" to JsonPrimitive(true),
            "xPercent" to JsonPrimitive(0f),
            "yPercent" to JsonPrimitive(0f),
            "widthPercent" to JsonPrimitive(TextBox.FULL_PERCENT),
            "heightPercent" to JsonPrimitive(TextBox.FULL_PERCENT),
            "vertical" to JsonPrimitive(vertical),
        ),
    )
}

/** [transform] applied to the document's `bibleSettings` and to every profile's own copy. */
private fun mapEveryBibleSettings(raw: String, transform: (JsonObject) -> JsonObject): String {
    val root = parseSettingsRoot(raw) ?: return raw
    val seeded = root["bibleSettings"]?.jsonObject?.let(transform)
    val projection = root["projectionSettings"]?.jsonObject
    val profiles = projection?.get("outputProfiles")?.jsonArray
    val newProfiles = profiles?.map { element ->
        val profile = element as? JsonObject ?: return@map element
        val bible = profile["bibleSettings"]?.jsonObject ?: return@map element
        JsonObject(profile + ("bibleSettings" to transform(bible)))
    }
    var updated = root
    if (seeded != null) updated = JsonObject(updated + ("bibleSettings" to seeded))
    if (projection != null && newProfiles != null) {
        val newProjection = JsonObject(projection + ("outputProfiles" to JsonArray(newProfiles)))
        updated = JsonObject(updated + ("projectionSettings" to newProjection))
    }
    return updated.toString()
}

/** The flat fields version 18 folds into the label's per-output [SongCreditStyle]; same names there. */
private val sectionLabelStyleFields = setOf(
    "fontSize", "color", "bold", "italic", "underline", "shadow", "outline", "fontType", "horizontalAlignment",
)

/** One `songSettings` object with `layoutExtras.titleSlideNumber` filled in from its flat fields. */
private fun seedTitleSlideNumber(song: JsonObject): JsonObject {
    val extras = song["layoutExtras"]?.jsonObject ?: JsonObject(emptyMap())
    // A document written by a build that already has this keeps its own: rolling forward from a
    // newer build through an older one must not overwrite what the newer one stored.
    if (extras["titleSlideNumber"] != null) return song
    val outlines = song["outlines"]?.jsonObject
    fun style(prefix: String, outlineKey: String): JsonObject {
        val fields = titleSlideNumberSeedFields.mapNotNull { (target, suffix) ->
            song["$prefix$suffix"]?.let { target to it }
        }
        // The backdrop is a nested record of its own, and the outline lives in `outlines`
        // rather than beside the flat fields -- miss that and every upgraded install loses its
        // number's stroke.
        val backdrop = song["${prefix}Backdrop"]?.let { "backdrop" to it }
        val outline = outlines?.get(outlineKey)?.let { "outline" to it }
        return JsonObject((fields + listOfNotNull(backdrop, outline)).toMap())
    }
    val titleSlideNumber = JsonObject(
        mapOf(
            "fullScreen" to style("songNumber", "songNumber"),
            "lowerThird" to style("songNumberLowerThird", "songNumberLowerThird"),
        ),
    )
    val newExtras = JsonObject(extras + ("titleSlideNumber" to titleSlideNumber))
    return JsonObject(song + ("layoutExtras" to newExtras))
}
