package org.churchpresenter.app.churchpresenter.presenter

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TEXT_LAYER_TYPE = 5
private const val MATTE_PAD = 4f

/** A declared slot box is `[x, y, w, h]`. */
private const val BOX_FIELDS = 4
private const val BOX_X = 0
private const val BOX_Y = 1
private const val BOX_W = 2
private const val BOX_H = 3

/**
 * The slot boxes: from the generator's metadata when it is there, otherwise from each text
 * document's wrap box — a hand-made file's `ps` is its box origin, a generated file's is where the
 * sample's baseline sits, which is why the generated one says so separately.
 */
internal fun readSlots(meta: JsonObject?, layers: List<JsonObject>): Map<String, LottieSlotBox> {
    val declared = (meta?.get("slots") as? JsonObject)?.mapNotNull { (name, value) ->
        val box = (value as? JsonArray)?.mapNotNull { it.jsonPrimitive.floatOrNull } ?: return@mapNotNull null
        if (box.size < BOX_FIELDS) null else name to LottieSlotBox(box[BOX_X], box[BOX_Y], box[BOX_W], box[BOX_H])
    }?.toMap()
    if (!declared.isNullOrEmpty()) return declared
    val fromDocuments = readDocumentSlots(layers)
    // A file from before the metadata carried its boxes: where a slot has a matte, the matte is
    // the box (plus a hair of padding), and that is nearer the truth than the baseline `ps`.
    val mattes = readMatteSlots(layers)
    return fromDocuments.mapValues { (name, box) -> mattes[name] ?: box }
}


/** A static property's numbers: `{"a": 0, "k": [..]}` → the list, or null when it is not shaped so. */
private fun JsonObject.staticFloats(key: String): List<Float>? =
    (this[key] as? JsonObject)?.get("k")?.jsonArray?.mapNotNull { it.jsonPrimitive.floatOrNull }

private fun readMatteSlots(layers: List<JsonObject>): Map<String, LottieSlotBox> = buildMap {
    layers.forEach { layer ->
        val name = layer["nm"]?.jsonPrimitive?.contentOrNull ?: return@forEach
        if (!name.endsWith("Matte") || layer["td"]?.jsonPrimitive?.intOrNull != 1) return@forEach
        val slot = name.removeSuffix("Matte")
        if (slot !in BibleLottieTemplate.TEXT_LAYERS) return@forEach
        val group = layer["shapes"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@forEach
        val rect = group["it"]?.jsonArray?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it["ty"]?.jsonPrimitive?.contentOrNull == "rc" } ?: return@forEach
        val size = rect.staticFloats("s") ?: return@forEach
        val pos = rect.staticFloats("p") ?: return@forEach
        if (size.size < 2 || pos.size < 2) return@forEach
        val w = size[0] - MATTE_PAD
        val h = size[1] - MATTE_PAD
        put(slot, LottieSlotBox(pos[0] - w / 2, pos[1] - h / 2, w, h))
    }
}

private fun readDocumentSlots(layers: List<JsonObject>): Map<String, LottieSlotBox> = buildMap {
    layers.forEach { layer ->
        if (layer["ty"]?.jsonPrimitive?.intOrNull != TEXT_LAYER_TYPE) return@forEach
        val name = layer["nm"]?.jsonPrimitive?.contentOrNull ?: return@forEach
        val doc = firstTextDocument(layer) ?: return@forEach
        val sz = doc["sz"]?.jsonArray?.mapNotNull { it.jsonPrimitive.floatOrNull } ?: return@forEach
        val ps = doc["ps"]?.jsonArray?.mapNotNull { it.jsonPrimitive.floatOrNull } ?: return@forEach
        if (sz.size >= 2 && ps.size >= 2) put(name, LottieSlotBox(ps[0], ps[1], sz[0], sz[1]))
    }
}

private fun firstTextDocument(layer: JsonObject): JsonObject? =
    ((layer["t"] as? JsonObject)?.get("d") as? JsonObject)
        ?.get("k")?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("s") as? JsonObject

