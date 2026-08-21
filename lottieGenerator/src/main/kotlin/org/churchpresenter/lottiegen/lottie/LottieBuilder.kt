package org.churchpresenter.lottiegen.lottie

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class LottieBuilder(
    val w: Int,
    val h: Int,
    val fr: Int = 30
) {
    val layers = mutableListOf<JsonObject>()
    val assets = mutableListOf<JsonObject>()
    val fonts = mutableListOf<JsonObject>()
    val ip = 0
    var op = 0
        private set

    var inFrames = 0
        private set
    var holdFrames = 0
        private set
    var outFrames = 0
        private set

    private var layerIndex = 0

    fun setDuration(inFrames: Int, holdFrames: Int, outFrames: Int) {
        this.inFrames = inFrames
        this.holdFrames = holdFrames
        this.outFrames = outFrames
        this.op = inFrames + holdFrames + outFrames
    }

    fun addImageAsset(id: String, dataUrl: String, w: Int, h: Int) {
        assets.add(buildJsonObject {
            put("id", JsonPrimitive(id))
            put("w", JsonPrimitive(w))
            put("h", JsonPrimitive(h))
            put("u", JsonPrimitive(""))
            put("p", JsonPrimitive(dataUrl))
            put("e", JsonPrimitive(1))
        })
    }

    fun addFont(family: String, weight: Int): String {
        val style = if (weight >= 700) "Bold" else "Regular"
        val fName = "$family-$style"
        if (fonts.none { (it["fName"] as? JsonPrimitive)?.content == fName }) {
            fonts.add(buildJsonObject {
                put("fName", JsonPrimitive(fName))
                put("fFamily", JsonPrimitive(family))
                put("fStyle", JsonPrimitive(style))
                put("ascent", JsonPrimitive(72.6))
            })
        }
        return fName
    }

    fun addShapeLayer(
        name: String,
        shapes: JsonArray,
        transform: JsonObject,
        td: Int? = null,
        tt: Int? = null,
        parent: Int? = null
    ): Int {
        val idx = layerIndex++
        val layer = buildJsonObject {
            put("ddd", JsonPrimitive(0))
            put("ind", JsonPrimitive(idx))
            put("ty", JsonPrimitive(4))
            put("nm", JsonPrimitive(name))
            put("sr", JsonPrimitive(1))
            put("ks", transform)
            put("ao", JsonPrimitive(0))
            put("shapes", shapes)
            put("ip", JsonPrimitive(ip))
            put("op", JsonPrimitive(op))
            put("st", JsonPrimitive(0))
            put("bm", JsonPrimitive(0))
            if (td != null) put("td", JsonPrimitive(td))
            if (tt != null) put("tt", JsonPrimitive(tt))
            if (parent != null) put("parent", JsonPrimitive(parent))
        }
        layers.add(layer)
        return idx
    }

    fun addTextLayer(
        name: String,
        textData: JsonObject,
        transform: JsonObject,
        td: Int? = null,
        tt: Int? = null,
        parent: Int? = null
    ): Int {
        val idx = layerIndex++
        val layer = buildJsonObject {
            put("ddd", JsonPrimitive(0))
            put("ind", JsonPrimitive(idx))
            put("ty", JsonPrimitive(5))
            put("nm", JsonPrimitive(name))
            put("sr", JsonPrimitive(1))
            put("ks", transform)
            put("ao", JsonPrimitive(0))
            put("t", textData)
            put("ip", JsonPrimitive(ip))
            put("op", JsonPrimitive(op))
            put("st", JsonPrimitive(0))
            put("bm", JsonPrimitive(0))
            if (td != null) put("td", JsonPrimitive(td))
            if (tt != null) put("tt", JsonPrimitive(tt))
            if (parent != null) put("parent", JsonPrimitive(parent))
        }
        layers.add(layer)
        return idx
    }

    fun addImageLayer(
        name: String,
        assetId: String,
        transform: JsonObject,
        td: Int? = null,
        tt: Int? = null,
        parent: Int? = null
    ): Int {
        val idx = layerIndex++
        val layer = buildJsonObject {
            put("ddd", JsonPrimitive(0))
            put("ind", JsonPrimitive(idx))
            put("ty", JsonPrimitive(2))
            put("nm", JsonPrimitive(name))
            put("refId", JsonPrimitive(assetId))
            put("sr", JsonPrimitive(1))
            put("ks", transform)
            put("ao", JsonPrimitive(0))
            put("ip", JsonPrimitive(ip))
            put("op", JsonPrimitive(op))
            put("st", JsonPrimitive(0))
            put("bm", JsonPrimitive(0))
            if (td != null) put("td", JsonPrimitive(td))
            if (tt != null) put("tt", JsonPrimitive(tt))
            if (parent != null) put("parent", JsonPrimitive(parent))
        }
        layers.add(layer)
        return idx
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("v", JsonPrimitive("5.7.4"))
        put("fr", JsonPrimitive(fr))
        put("ip", JsonPrimitive(ip))
        put("op", JsonPrimitive(op))
        put("w", JsonPrimitive(w))
        put("h", JsonPrimitive(h))
        put("nm", JsonPrimitive("Lower Third"))
        put("ddd", JsonPrimitive(0))
        put("assets", buildJsonArray { assets.forEach { add(it) } })
        put("layers", buildJsonArray { layers.forEach { add(it) } })
        if (fonts.isNotEmpty()) {
            put("fonts", buildJsonObject {
                put("list", buildJsonArray { fonts.forEach { add(it) } })
            })
            // Embedded glyph outlines: players without the named fonts installed (or any
            // way to resolve them) fall back to these and still render crisp vector text.
            GlyphExtractor.buildCharsArray(layers, fonts)?.let { put("chars", it) }
        }
    }

    companion object {
        fun animatedProp(keyframes: JsonArray): JsonObject = buildJsonObject {
            put("a", JsonPrimitive(1))
            put("k", keyframes)
        }

        fun staticProp(value: JsonElement): JsonObject = buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", value)
        }

        fun staticProp(value: Double): JsonObject = staticProp(JsonPrimitive(value))
        fun staticProp(value: Int): JsonObject = staticProp(JsonPrimitive(value))

        fun staticPropArray(vararg values: Double): JsonObject = staticProp(jsonArrayOf(*values))
        fun staticPropArray(vararg values: Int): JsonObject = staticProp(jsonArrayOf(*values))

        fun defaultTransform(
            opacity: JsonObject? = null,
            rotation: JsonObject? = null,
            position: JsonObject? = null,
            anchor: JsonObject? = null,
            scale: JsonObject? = null
        ): JsonObject = buildJsonObject {
            put("o", opacity ?: staticProp(100))
            put("r", rotation ?: staticProp(0))
            put("p", position ?: staticPropArray(0.0, 0.0, 0.0))
            put("a", anchor ?: staticPropArray(0.0, 0.0, 0.0))
            put("s", scale ?: staticPropArray(100.0, 100.0, 100.0))
        }
    }
}
