package org.churchpresenter.presenter

import androidx.compose.ui.graphics.Matrix
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/*
 * A text layer's own transform, read from the file and played back at any frame — so the band's
 * second-voice colour can follow words the template slides, scales or turns in.
 *
 * The player's transform is not something it hands out, so the band reads the same keyframes it
 * plays: the anchor, position, scale and rotation, each a still value or keyframes eased by the
 * file's own bezier handles or held. A motion path's curve, a parent layer's motion, skew and
 * expressions are not followed; the band templates use none of them on their text.
 */

/** One property of a transform: its value, as numbers, at any frame of the layer's own time. */
internal fun interface Track {
    fun at(frame: Float): List<Float>
}

/** One keyframe: where it starts, its value, and the bezier handles easing it into the next. */
internal data class Keyframe(
    val time: Float,
    val value: List<Float>,
    val hold: Boolean = false,
    val outX: Float = 0f,
    val outY: Float = 0f,
    val inX: Float = 1f,
    val inY: Float = 1f,
)

/** A property that changes: each keyframe's value until the next, eased between them. */
internal class KeyedTrack(private val keys: List<Keyframe>) : Track {
    override fun at(frame: Float): List<Float> {
        val next = keys.indexOfFirst { it.time > frame }
        return when {
            next == 0 -> keys.first().value
            next < 0 -> keys.last().value
            keys[next - 1].hold -> keys[next - 1].value
            else -> between(keys[next - 1], keys[next], frame)
        }
    }

    private fun between(from: Keyframe, to: Keyframe, frame: Float): List<Float> {
        val linear = (frame - from.time) / (to.time - from.time)
        val eased = cubicBezier(linear, from.outX, from.outY, from.inX, from.inY)
        return from.value.indices.map { i ->
            val end = to.value.getOrElse(i) { from.value[i] }
            from.value[i] + (end - from.value[i]) * eased
        }
    }
}

/**
 * How far along a curve from (0, 0) to (1, 1) with handles ([x1], [y1]) and ([x2], [y2]) is at
 * [progress] across it — the CSS cubic-bezier, solved for x by halving.
 */
internal fun cubicBezier(progress: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    if (progress <= 0f) return 0f
    if (progress >= 1f) return 1f
    // The cubic's 3·(1−t)²·t·a + 3·(1−t)·t²·b + t³, written as one sum of powers of t.
    fun curve(t: Float, a: Float, b: Float): Float {
        val u = 1f - t
        return BEZIER_WEIGHT * u * t * (u * a + t * b) + t * t * t
    }
    var low = 0f
    var high = 1f
    var t = progress
    repeat(BEZIER_STEPS) {
        if (curve(t, x1, x2) < progress) low = t else high = t
        t = (low + high) / 2f
    }
    return curve(t, y1, y2)
}

/** A layer's transform, from its keyframes: the matrix the player draws it through at a frame. */
class LayerMotion internal constructor(
    private val anchor: Track,
    private val position: Track,
    private val scale: Track,
    private val rotation: Track,
    /** Where the layer's own time starts, in the composition's frames. */
    private val startTime: Float = 0f,
) {
    /** Translate · rotate · scale about the anchor — the order the player builds a layer's matrix in. */
    fun matrixAt(frame: Float): Matrix {
        val time = frame - startTime
        val p = position.at(time)
        val a = anchor.at(time)
        val s = scale.at(time)
        return Matrix().apply {
            translate(p.getOrElse(0) { 0f }, p.getOrElse(1) { 0f })
            rotateZ(rotation.at(time).firstOrNull() ?: 0f)
            scale(s.getOrElse(0) { PERCENT } / PERCENT, s.getOrElse(1) { PERCENT } / PERCENT)
            translate(-a.getOrElse(0) { 0f }, -a.getOrElse(1) { 0f })
        }
    }
}

/** The text layers whose own transform is keyframed, and how each moves. Still ones are left out. */
internal fun readTextMotions(layers: List<JsonObject>): Map<String, LayerMotion> =
    layers.filter { it["ty"]?.jsonPrimitive?.intOrNull == TEXT_LAYER_TYPE }.mapNotNull { layer ->
        val name = layer["nm"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val ks = layer["ks"] as? JsonObject ?: return@mapNotNull null
        val rotationKey = if (ks.containsKey("r")) "r" else "rz"
        fun property(key: String) = ks[key] as? JsonObject
        if (listOf("a", "p", "s", rotationKey).none { property(it)?.isAnimated() == true }) return@mapNotNull null
        fun track(key: String) = property(key)?.let(::readTrack)
        name to LayerMotion(
            anchor = track("a") ?: Track { listOf(0f, 0f) },
            position = track("p") ?: Track { listOf(0f, 0f) },
            scale = track("s") ?: Track { listOf(PERCENT, PERCENT) },
            rotation = track(rotationKey) ?: Track { listOf(0f) },
            startTime = layer["st"]?.jsonPrimitive?.floatOrNull ?: 0f,
        )
    }.toMap()

/** Whether a property changes over time — directly, or through a position split into x and y. */
private fun JsonObject.isAnimated(): Boolean =
    this["a"]?.jsonPrimitive?.intOrNull == 1 ||
        listOf("x", "y").any { (this[it] as? JsonObject)?.get("a")?.jsonPrimitive?.intOrNull == 1 }

/** A property as a track: still, keyframed, or a position split into x and y tracks of its own. */
internal fun readTrack(property: JsonObject): Track? {
    if (property["s"]?.jsonPrimitive?.intOrNull == 1 || property["s"]?.jsonPrimitive?.contentOrNull == "true") {
        val x = (property["x"] as? JsonObject)?.let(::readTrack) ?: return null
        val y = (property["y"] as? JsonObject)?.let(::readTrack) ?: return null
        return Track { frame -> listOf(x.at(frame).firstOrNull() ?: 0f, y.at(frame).firstOrNull() ?: 0f) }
    }
    val k = property["k"] ?: return null
    val keyframes = (k as? JsonArray)?.mapNotNull { it as? JsonObject }?.takeIf { it.isNotEmpty() }
    if (keyframes == null) {
        val still = numbers(k) ?: return null
        return Track { still }
    }
    val keys = keyframes.mapIndexedNotNull { i, key ->
        val time = key["t"]?.jsonPrimitive?.floatOrNull ?: return@mapIndexedNotNull null
        // An old-style keyframe gives its end value as `e`; a newer one leaves it to the next `s`.
        val value = numbers(key["s"]) ?: keyframes.getOrNull(i - 1)?.get("e")?.let(::numbers)
            ?: return@mapIndexedNotNull null
        Keyframe(
            time = time,
            value = value,
            hold = key["h"]?.jsonPrimitive?.intOrNull == 1,
            outX = handle(key, "o", "x") ?: 0f,
            outY = handle(key, "o", "y") ?: 0f,
            inX = handle(key, "i", "x") ?: 1f,
            inY = handle(key, "i", "y") ?: 1f,
        )
    }
    return if (keys.isEmpty()) null else KeyedTrack(keys)
}

/** A value as numbers: a single number or an array of them. */
private fun numbers(element: Any?): List<Float>? = when (element) {
    is JsonPrimitive -> element.floatOrNull?.let { listOf(it) }
    is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }.takeIf { it.isNotEmpty() }
    else -> null
}

/** A keyframe's bezier handle coordinate — one number for every dimension, or the first of several. */
private fun handle(key: JsonObject, side: String, axis: String): Float? =
    (key[side] as? JsonObject)?.get(axis)?.let(::numbers)?.firstOrNull()

/** A Lottie layer's `ty` for text. */
private const val TEXT_LAYER_TYPE = 5

/** A cubic bezier's weight on each of its two handles. */
private const val BEZIER_WEIGHT = 3f

/** Halvings that solve a bezier for x — 20 put it within a millionth of a frame's fraction. */
private const val BEZIER_STEPS = 20
