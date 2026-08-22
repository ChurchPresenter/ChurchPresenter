package org.churchpresenter.lottiegen.lottie

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject


fun makeRect(w: Double, h: Double, cornerRadius: Double = 0.0, position: List<Double>? = null): JsonObject =
    buildJsonObject {
        put("ty", JsonPrimitive("rc"))
        put("d", JsonPrimitive(1))
        put("s", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", jsonArrayOf(w, h))
        })
        put("p", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", jsonArrayOf(position ?: listOf(0.0, 0.0)))
        })
        put("r", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", JsonPrimitive(cornerRadius))
        })
    }


fun makeAnimatedRect(sizeKFs: JsonArray, cornerRadius: Double = 0.0, position: List<Double>? = null): JsonObject =
    buildJsonObject {
        put("ty", JsonPrimitive("rc"))
        put("d", JsonPrimitive(1))
        put("s", buildJsonObject {
            put("a", JsonPrimitive(1))
            put("k", sizeKFs)
        })
        put("p", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", jsonArrayOf(position ?: listOf(0.0, 0.0)))
        })
        put("r", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", JsonPrimitive(cornerRadius))
        })
    }


fun makeEllipse(w: Double, h: Double, position: List<Double>? = null): JsonObject =
    buildJsonObject {
        put("ty", JsonPrimitive("el"))
        put("d", JsonPrimitive(1))
        put("s", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", jsonArrayOf(w, h))
        })
        put("p", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", jsonArrayOf(position ?: listOf(0.0, 0.0)))
        })
    }


fun makePath(vertices: List<List<Double>>, closed: Boolean = true): JsonObject =
    buildJsonObject {
        put("ty", JsonPrimitive("sh"))
        put("d", JsonPrimitive(1))
        put("ks", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", buildJsonObject {
                put("v", buildJsonArray {
                    vertices.forEach { add(jsonArrayOf(it)) }
                })
                put("i", buildJsonArray {
                    vertices.forEach { _ -> add(jsonArrayOf(0.0, 0.0)) }
                })
                put("o", buildJsonArray {
                    vertices.forEach { _ -> add(jsonArrayOf(0.0, 0.0)) }
                })
                put("c", JsonPrimitive(closed))
            })
        })
    }

/**
 * Bezier path with real tangent handles. [inTangents]/[outTangents] are per-vertex and
 * relative to that vertex (Lottie convention); must match [vertices] in size.
 */


/**
 * Bezier path with real tangent handles. [inTangents]/[outTangents] are per-vertex and
 * relative to that vertex (Lottie convention); must match [vertices] in size.
 */
fun makeCurvedPath(
    vertices: List<List<Double>>,
    inTangents: List<List<Double>>,
    outTangents: List<List<Double>>,
    closed: Boolean = false
): JsonObject = buildJsonObject {
    put("ty", JsonPrimitive("sh"))
    put("d", JsonPrimitive(1))
    put("ks", buildJsonObject {
        put("a", JsonPrimitive(0))
        put("k", buildJsonObject {
            put("v", buildJsonArray {
                vertices.forEach { add(jsonArrayOf(it)) }
            })
            put("i", buildJsonArray {
                inTangents.forEach { add(jsonArrayOf(it)) }
            })
            put("o", buildJsonArray {
                outTangents.forEach { add(jsonArrayOf(it)) }
            })
            put("c", JsonPrimitive(closed))
        })
    })
}

/**
 * Trim Paths — progressively reveals the preceding path/stroke along its length
 * (the "line draws itself" primitive). [start]/[end] are 0-100 percent of the path,
 * each either static or animated via keyframes.
 */


fun makeGroup(items: List<JsonObject>, transform: JsonObject? = null): JsonObject =
    buildJsonObject {
        put("ty", JsonPrimitive("gr"))
        put("it", buildJsonArray {
            items.forEach { add(it) }
            // Add transform item
            add(buildJsonObject {
                put("ty", JsonPrimitive("tr"))
                put("p", transform?.get("p") ?: buildJsonObject {
                    put("a", JsonPrimitive(0)); put("k", jsonArrayOf(0.0, 0.0))
                })
                put("a", transform?.get("a") ?: buildJsonObject {
                    put("a", JsonPrimitive(0)); put("k", jsonArrayOf(0.0, 0.0))
                })
                put("s", transform?.get("s") ?: buildJsonObject {
                    put("a", JsonPrimitive(0)); put("k", jsonArrayOf(FULL_PERCENT_D, FULL_PERCENT_D))
                })
                put("r", transform?.get("r") ?: buildJsonObject {
                    put("a", JsonPrimitive(0)); put("k", JsonPrimitive(0))
                })
                put("o", transform?.get("o") ?: buildJsonObject {
                    put("a", JsonPrimitive(0)); put("k", JsonPrimitive(FULL_PERCENT))
                })
                put("sk", buildJsonObject {
                    put("a", JsonPrimitive(0)); put("k", JsonPrimitive(0))
                })
                put("sa", buildJsonObject {
                    put("a", JsonPrimitive(0)); put("k", JsonPrimitive(0))
                })
            })
        })
    }
