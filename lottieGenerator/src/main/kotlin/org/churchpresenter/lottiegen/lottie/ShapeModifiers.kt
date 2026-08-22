package org.churchpresenter.lottiegen.lottie

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject


/**
 * Trim Paths — progressively reveals the preceding path/stroke along its length
 * (the "line draws itself" primitive). [start]/[end] are 0-100 percent of the path,
 * each either static or animated via keyframes.
 */
fun makeTrimPath(
    start: Double = 0.0,
    end: Double = 100.0,
    startKFs: JsonArray? = null,
    endKFs: JsonArray? = null
): JsonObject = buildJsonObject {
    put("ty", JsonPrimitive("tm"))
    put("s", buildJsonObject {
        if (startKFs != null) {
            put("a", JsonPrimitive(1)); put("k", startKFs)
        } else {
            put("a", JsonPrimitive(0)); put("k", JsonPrimitive(start))
        }
    })
    put("e", buildJsonObject {
        if (endKFs != null) {
            put("a", JsonPrimitive(1)); put("k", endKFs)
        } else {
            put("a", JsonPrimitive(0)); put("k", JsonPrimitive(end))
        }
    })
    put("o", buildJsonObject {
        put("a", JsonPrimitive(0)); put("k", JsonPrimitive(0))
    })
    put("m", JsonPrimitive(1))
}

/**
 * Repeater — draws [copies] instances of the preceding shapes, each offset/rotated/scaled
 * relative to the previous copy. [endOpacity] < 100 fades the copies out progressively.
 */


/**
 * Repeater — draws [copies] instances of the preceding shapes, each offset/rotated/scaled
 * relative to the previous copy. [endOpacity] < 100 fades the copies out progressively.
 */
fun makeRepeater(
    copies: Int,
    offsetPx: List<Double>,
    rotationDeg: Double = 0.0,
    scalePct: Double = 100.0,
    endOpacity: Double = 100.0
): JsonObject = buildJsonObject {
    put("ty", JsonPrimitive("rp"))
    put("c", buildJsonObject {
        put("a", JsonPrimitive(0)); put("k", JsonPrimitive(copies))
    })
    put("o", buildJsonObject {
        put("a", JsonPrimitive(0)); put("k", JsonPrimitive(0))
    })
    put("m", JsonPrimitive(1))
    put("tr", buildJsonObject {
        put("ty", JsonPrimitive("tr"))
        put("p", buildJsonObject {
            put("a", JsonPrimitive(0)); put("k", jsonArrayOf(offsetPx))
        })
        put("a", buildJsonObject {
            put("a", JsonPrimitive(0)); put("k", jsonArrayOf(0.0, 0.0))
        })
        put("s", buildJsonObject {
            put("a", JsonPrimitive(0)); put("k", jsonArrayOf(scalePct, scalePct))
        })
        put("r", buildJsonObject {
            put("a", JsonPrimitive(0)); put("k", JsonPrimitive(rotationDeg))
        })
        put("so", buildJsonObject {
            put("a", JsonPrimitive(0)); put("k", JsonPrimitive(FULL_PERCENT))
        })
        put("eo", buildJsonObject {
            put("a", JsonPrimitive(0)); put("k", JsonPrimitive(endOpacity))
        })
    })
}
