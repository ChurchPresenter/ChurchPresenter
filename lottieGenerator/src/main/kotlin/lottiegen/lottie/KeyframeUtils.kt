package lottiegen.lottie

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.roundToInt

/** Cubic-bezier(0.19, 0.76, 0.32, 1) mapped to Lottie tangents */
object Easing {
    val DEFAULT: JsonObject = buildJsonObject {
        put("o", buildJsonObject {
            put("x", buildJsonArray { add(JsonPrimitive(0.4)) })
            put("y", buildJsonArray { add(JsonPrimitive(0.0)) })
        })
        put("i", buildJsonObject {
            put("x", buildJsonArray { add(JsonPrimitive(0.2)) })
            put("y", buildJsonArray { add(JsonPrimitive(1.0)) })
        })
    }

    val LINEAR: JsonObject = buildJsonObject {
        put("o", buildJsonObject {
            put("x", buildJsonArray { add(JsonPrimitive(0.0)) })
            put("y", buildJsonArray { add(JsonPrimitive(0.0)) })
        })
        put("i", buildJsonObject {
            put("x", buildJsonArray { add(JsonPrimitive(1.0)) })
            put("y", buildJsonArray { add(JsonPrimitive(1.0)) })
        })
    }
}

data class KeyframeInput(val pct: Double, val value: JsonElement)

fun pctToFrame(pct: Double, totalFrames: Int): Int =
    ((pct / 100.0) * totalFrames).roundToInt()

/** Create a single keyframe entry */
fun kf(frame: Int, value: JsonElement, easing: JsonObject? = null): JsonObject = buildJsonObject {
    put("t", JsonPrimitive(frame))
    put("s", value)
    if (easing != null) {
        put("i", easing["i"]!!)
        put("o", easing["o"]!!)
    }
}

/**
 * Build in+hold+out keyframes for a property.
 * inKFs: array of {pct, value} for animation-in (0-100%).
 * The animation-out is the reverse.
 */
fun buildKeyframes(
    inKFs: List<KeyframeInput>,
    inFrames: Int,
    holdFrames: Int,
    outFrames: Int,
    easing: JsonObject = Easing.DEFAULT
): JsonArray {
    val kfs = mutableListOf<JsonObject>()
    val totalOut = inFrames + holdFrames

    // Animation-in keyframes (ALL with easing)
    for (input in inKFs) {
        kfs.add(kf(pctToFrame(input.pct, inFrames), input.value, easing))
    }

    // Transition/hold keyframe at end of hold period
    val finalValue = inKFs.last().value
    kfs.add(kf(totalOut, finalValue, easing))

    // Animation-out: reverse of in, skip first (duplicate of transition KF)
    val reversedIn = inKFs.reversed()
    for (i in 1 until reversedIn.size) {
        val pctFromEnd = 100.0 - reversedIn[i].pct
        val frame = totalOut + pctToFrame(pctFromEnd, outFrames)
        kfs.add(kf(frame, reversedIn[i].value, easing))
    }

    return buildJsonArray { kfs.forEach { add(it) } }
}

// Convenience: build JsonArray from a list of doubles
fun jsonArrayOf(vararg values: Double): JsonArray =
    buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }

fun jsonArrayOf(vararg values: Int): JsonArray =
    buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }

fun jsonArrayOf(values: List<Double>): JsonArray =
    buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }
