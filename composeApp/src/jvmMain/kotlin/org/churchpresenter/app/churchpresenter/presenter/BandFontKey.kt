package org.churchpresenter.app.churchpresenter.presenter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The face a slot is set in, as the Bible settings describe it. */
internal data class BandFontKey(val family: String, val bold: Boolean, val italic: Boolean) {
    /** Lottie's name for the face; the style words are what the player derives weight and slant from. */
    val styleName: String get() = listOfNotNull(if (bold) "Bold" else null, if (italic) "Italic" else null)
        .ifEmpty { listOf("Regular") }.joinToString(" ")
    val lottieName: String get() = "$family-${styleName.replace(" ", "")}"
}

/**
 * The template with its text set in the Bible's own faces: one font-list entry per distinct face,
 * each text layer's document pointing at its own, and the embedded glyph outlines dropped — they
 * only cover the characters the generator's sample used, and a player that falls back to them
 * silently skips every character they lack.
 */
internal fun rewriteTemplateFonts(json: String, fontsByLayer: Map<String, BandFontKey>): String {
    val obj = Json.parseToJsonElement(json).jsonObject
    val faces = fontsByLayer.values.distinct()
    val fontList = buildJsonArray {
        faces.forEach { face ->
            add(
                buildJsonObject {
                    put("fName", JsonPrimitive(face.lottieName))
                    put("fFamily", JsonPrimitive(face.family))
                    put("fStyle", JsonPrimitive(face.styleName))
                    put("ascent", JsonPrimitive(SYNTHETIC_FONT_ASCENT))
                },
            )
        }
    }
    val layers = obj["layers"]?.jsonArray.orEmpty().map { element ->
        val layer = element as? JsonObject ?: return@map element
        val name = layer["nm"]?.jsonPrimitive?.contentOrNull
        val face = name?.let(fontsByLayer::get) ?: return@map element
        withFontName(layer, face.lottieName)
    }
    val rewritten = buildJsonObject {
        obj.forEach { (key, value) ->
            when (key) {
                "chars" -> Unit
                "layers" -> put(key, JsonArray(layers))
                "fonts" -> put(key, buildJsonObject { put("list", fontList) })
                else -> put(key, value)
            }
        }
        if ("fonts" !in obj) put("fonts", buildJsonObject { put("list", fontList) })
    }
    return rewritten.toString()
}

private fun withFontName(layer: JsonObject, fontName: String): JsonObject {
    val t = layer["t"] as? JsonObject ?: return layer
    val d = t["d"] as? JsonObject ?: return layer
    val k = d["k"]?.jsonArray ?: return layer
    val keyframes = k.map { kf ->
        val kfObj = kf as? JsonObject ?: return@map kf
        val s = kfObj["s"] as? JsonObject ?: return@map kf
        val doc = JsonObject(s + ("f" to JsonPrimitive(fontName)))
        JsonObject(kfObj + ("s" to doc))
    }
    val newD = JsonObject(d + ("k" to JsonArray(keyframes)))
    val newT = JsonObject(t + ("d" to newD))
    return JsonObject(layer + ("t" to newT))
}


/** The same synthetic ascent the generator writes; the player ignores it after its first copy anyway. */
private const val SYNTHETIC_FONT_ASCENT = 72.6
