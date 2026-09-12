package org.churchpresenter.lottiegen.band

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Reading a generated band the way the player will, for the assertions to share. */
internal object BandJson {
    fun layers(doc: JsonObject): List<JsonObject> = doc["layers"]!!.jsonArray.map { it.jsonObject }
    fun layerNames(doc: JsonObject): List<String> = layers(doc).map { it.name }
    fun layer(doc: JsonObject, name: String): JsonObject = layers(doc).first { it.name == name }
    fun markers(doc: JsonObject): Map<String, Pair<Int, Int>> = doc["markers"]!!.jsonArray.associate {
        val m = it.jsonObject
        m["cm"]!!.jsonPrimitive.content to (m["tm"]!!.jsonPrimitive.int() to m["dr"]!!.jsonPrimitive.int())
    }
    fun meta(doc: JsonObject): JsonObject = doc[BibleLottieGenerator.METADATA_KEY]!!.jsonObject
    fun textDocument(layer: JsonObject): JsonObject =
        layer["t"]!!.jsonObject["d"]!!.jsonObject["k"]!!.jsonArray.first().jsonObject["s"]!!.jsonObject
    fun shapeTypes(layer: JsonObject): List<String> = layer["shapes"]!!.jsonArray.flatMap { group ->
        group.jsonObject["it"]!!.jsonArray.map { it.jsonObject["ty"]!!.jsonPrimitive.content }
    }
    fun assets(doc: JsonObject): JsonArray = doc["assets"]!!.jsonArray

    val JsonObject.name: String get() = this["nm"]!!.jsonPrimitive.content
    val JsonObject.type: Int get() = this["ty"]!!.jsonPrimitive.intOrNull ?: -1
    fun JsonObject.intField(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    fun JsonObject.stringField(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun kotlinx.serialization.json.JsonPrimitive.int(): Int = content.toDouble().toInt()
}
