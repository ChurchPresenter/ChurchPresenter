package org.churchpresenter.lottiegen.lottie

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.lang.Character.UnicodeScript
import java.text.BreakIterator
import kotlin.math.ceil

/**
 * How a player draws a file's text, chosen in the generator and written into the file.
 *
 * Compottie draws text one letter at a time by default, which is what lets it space letters out
 * (tracking) but breaks every script whose letters change shape or join with their neighbours —
 * Tamil vowel signs come loose, Arabic stops joining. Drawing whole lines shapes the text
 * properly and gives up letter spacing.
 */
@Serializable
enum class TextShaping(val key: String) {
    /** Whole lines only when the text holds a script that needs shaping; letter by letter otherwise. */
    AUTO("auto"),
    WHOLE_LINES("lines"),
    PER_LETTER("letters");

    companion object {
        /** The mode stored under [key]; a missing or unknown key is [AUTO]. */
        fun fromKey(key: String?): TextShaping = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/**
 * Reads and writes [TextShaping] under the file's `cp` object — the key ChurchPresenter keeps
 * what Lottie has no field for; other players skip it — and decides, for a player, whether to
 * pass Compottie's `enableTextGrouping`.
 *
 * Both ways of measuring a line still sum per-letter widths (Compottie's own line splitter and
 * the band player's wrap), so a shaped line in a script like Tamil comes out a little narrower
 * than measured: it breaks early, never overflows.
 */
object LottieTextShaping {
    const val METADATA_KEY = "cp"
    const val TEXT_SHAPING = "textShaping"

    private const val TEXT_LAYER_TYPE = "5"

    /** Scripts Compottie's letter-by-letter drawing gets right. */
    private val lettersDrawAlone = setOf(
        UnicodeScript.LATIN,
        UnicodeScript.GREEK,
        UnicodeScript.CYRILLIC,
        UnicodeScript.ARMENIAN,
        UnicodeScript.GEORGIAN,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
    )

    /** True when [text] holds a script whose letters have to be shaped together. */
    fun needsShaping(text: String): Boolean =
        text.codePoints().anyMatch { UnicodeScript.of(it) !in lettersDrawAlone }

    /** Whether to draw whole lines for [mode], given the [texts] about to be drawn. */
    fun groupsText(mode: TextShaping, texts: Iterable<String>): Boolean = when (mode) {
        TextShaping.WHOLE_LINES -> true
        TextShaping.PER_LETTER -> false
        TextShaping.AUTO -> texts.any(::needsShaping)
    }

    /**
     * Whether to draw [json]'s text as whole lines: its own [TextShaping], with Auto judged on the
     * text baked into the file plus any [liveTexts] a player puts in at run time. False for
     * anything that does not parse, which is how Compottie draws by default.
     */
    fun groupsText(json: String, liveTexts: Iterable<String> = emptyList()): Boolean {
        val lottie = parse(json) ?: return false
        return groupsText(modeOf(lottie), staticTexts(lottie) + liveTexts)
    }

    /** The mode [lottie] was written with; [TextShaping.AUTO] for a file that says nothing. */
    fun modeOf(lottie: JsonObject): TextShaping {
        val meta = lottie[METADATA_KEY] as? JsonObject
        return TextShaping.fromKey((meta?.get(TEXT_SHAPING) as? JsonPrimitive)?.content)
    }

    /** [modeOf] from the file's JSON text. */
    fun modeOf(json: String): TextShaping = parse(json)?.let(::modeOf) ?: TextShaping.AUTO

    /** [lottie] with [mode] stored under its `cp` object, keeping whatever else is there. */
    fun withMode(lottie: JsonObject, mode: TextShaping): JsonObject = buildJsonObject {
        lottie.forEach { (key, value) -> if (key != METADATA_KEY) put(key, value) }
        put(
            METADATA_KEY,
            buildJsonObject {
                (lottie[METADATA_KEY] as? JsonObject)?.forEach { (key, value) -> put(key, value) }
                put(TEXT_SHAPING, JsonPrimitive(mode.key))
            },
        )
    }

    /** Every string the file's text layers draw, precomposition layers included. */
    fun staticTexts(lottie: JsonObject): List<String> {
        val assetLayers = (lottie["assets"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.get("layers") as? JsonArray }
            .flatten()
        return ((lottie["layers"] as? JsonArray).orEmpty() + assetLayers)
            .asSequence()
            .mapNotNull { it as? JsonObject }
            .filter { (it["ty"] as? JsonPrimitive)?.content == TEXT_LAYER_TYPE }
            .mapNotNull { ((it["t"] as? JsonObject)?.get("d") as? JsonObject)?.get("k") as? JsonArray }
            .flatten()
            .mapNotNull { ((it as? JsonObject)?.get("s") as? JsonObject)?.get("t") as? JsonPrimitive }
            .map { it.content }
            .toList()
    }

    /**
     * The first [fraction] of [text] a typewriter has typed, counted in whole characters as a
     * reader sees them — a Tamil letter with its vowel sign, an emoji — so a reveal never shows a
     * sign split from its letter.
     */
    fun typedText(text: String, fraction: Float): String {
        val breaks = BreakIterator.getCharacterInstance().apply { setText(text) }
        val ends = generateSequence { breaks.next().takeIf { it != BreakIterator.DONE } }.toList()
        val count = ceil(ends.size * fraction.coerceIn(0f, 1f)).toInt()
        return if (count == 0) "" else text.substring(0, ends[count - 1])
    }

    private fun parse(json: String): JsonObject? = try {
        Json.parseToJsonElement(json) as? JsonObject
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
