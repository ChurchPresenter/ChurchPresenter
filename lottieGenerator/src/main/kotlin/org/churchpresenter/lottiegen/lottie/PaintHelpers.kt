package org.churchpresenter.lottiegen.lottie

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject


fun makeFill(color: List<Double>, opacity: Double = 100.0): JsonObject =
    buildJsonObject {
        put("ty", JsonPrimitive("fl"))
        put("c", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", jsonArrayOf(color + listOf(1.0)))
        })
        put("o", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", JsonPrimitive(opacity))
        })
        put("r", JsonPrimitive(1))
        put("bm", JsonPrimitive(0))
    }


fun makeGradientFill(
    color: List<Double>,
    opacity: Double = 100.0,
    startPt: List<Double>,
    endPt: List<Double>
): JsonObject = buildJsonObject {
    val r = color[0]
    val g = color[1]
    val b = color[2]
    put("ty", JsonPrimitive("gf"))
    put("o", buildJsonObject {
        put("a", JsonPrimitive(0))
        put("k", JsonPrimitive(opacity))
    })
    put("r", JsonPrimitive(1))
    put("bm", JsonPrimitive(0))
    put("t", JsonPrimitive(1))
    put("s", buildJsonObject {
        put("a", JsonPrimitive(0))
        put("k", jsonArrayOf(startPt))
    })
    put("e", buildJsonObject {
        put("a", JsonPrimitive(0))
        put("k", jsonArrayOf(endPt))
    })
    put("g", buildJsonObject {
        put("p", JsonPrimitive(2))
        put("k", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", buildJsonArray {
                // Color stops
                add(JsonPrimitive(0.0)); add(JsonPrimitive(r)); add(JsonPrimitive(g)); add(JsonPrimitive(b))
                add(JsonPrimitive(1.0)); add(JsonPrimitive(r)); add(JsonPrimitive(g)); add(JsonPrimitive(b))
                // Opacity stops
                add(JsonPrimitive(0.0)); add(JsonPrimitive(1.0))
                add(JsonPrimitive(1.0)); add(JsonPrimitive(0.0))
            })
        })
    })
}


/** A linear gradient from [startColor] at [startPt] to [endColor] at [endPt], fully opaque at both ends. */
fun makeTwoColorGradientFill(
    startColor: List<Double>,
    endColor: List<Double>,
    opacity: Double = 100.0,
    startPt: List<Double>,
    endPt: List<Double>,
): JsonObject = makeGradientFillStops(listOf(startColor, endColor), opacity, startPt, endPt)

/** A linear gradient through [colors], spread evenly from [startPt] to [endPt], fully opaque throughout. */
fun makeGradientFillStops(
    colors: List<List<Double>>,
    opacity: Double = 100.0,
    startPt: List<Double>,
    endPt: List<Double>,
): JsonObject = buildJsonObject {
    require(colors.size >= 2) { "A gradient needs at least two colours" }
    val step = 1.0 / (colors.size - 1)
    put("ty", JsonPrimitive("gf"))
    put("o", buildJsonObject {
        put("a", JsonPrimitive(0))
        put("k", JsonPrimitive(opacity))
    })
    put("r", JsonPrimitive(1))
    put("bm", JsonPrimitive(0))
    put("t", JsonPrimitive(1))
    put("s", buildJsonObject {
        put("a", JsonPrimitive(0))
        put("k", jsonArrayOf(startPt))
    })
    put("e", buildJsonObject {
        put("a", JsonPrimitive(0))
        put("k", jsonArrayOf(endPt))
    })
    put("g", buildJsonObject {
        put("p", JsonPrimitive(colors.size))
        put("k", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", buildJsonArray {
                colors.forEachIndexed { i, c ->
                    add(JsonPrimitive(i * step)); c.take(RGB_CHANNELS).forEach { add(JsonPrimitive(it)) }
                }
                add(JsonPrimitive(0.0)); add(JsonPrimitive(1.0))
                add(JsonPrimitive(1.0)); add(JsonPrimitive(1.0))
            })
        })
    })
}

fun makeStroke(color: List<Double>, width: Double, opacity: Double = 100.0, dashPx: Double = 0.0): JsonObject? {
    if (width <= 0) return null
    return buildJsonObject {
        put("ty", JsonPrimitive("st"))
        put("c", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", jsonArrayOf(color + listOf(1.0)))
        })
        put("o", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", JsonPrimitive(opacity))
        })
        put("w", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", JsonPrimitive(width))
        })
        put("lc", JsonPrimitive(1))
        put("lj", JsonPrimitive(1))
        put("ml", JsonPrimitive(MITER_LIMIT))
        put("bm", JsonPrimitive(0))
        if (dashPx > 0) put("d", makeDashArray(dashPx))
    }
}


fun makeAnimatedStroke(
    color: List<Double>,
    widthKFs: JsonArray,
    opacity: Double = 100.0,
    dashPx: Double = 0.0
): JsonObject =
    buildJsonObject {
        put("ty", JsonPrimitive("st"))
        put("c", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", jsonArrayOf(color + listOf(1.0)))
        })
        put("o", buildJsonObject {
            put("a", JsonPrimitive(0))
            put("k", JsonPrimitive(opacity))
        })
        put("w", buildJsonObject {
            put("a", JsonPrimitive(1))
            put("k", widthKFs)
        })
        put("lc", JsonPrimitive(1))
        put("lj", JsonPrimitive(1))
        put("ml", JsonPrimitive(MITER_LIMIT))
        put("bm", JsonPrimitive(0))
        if (dashPx > 0) put("d", makeDashArray(dashPx))
    }

/** Equal dash/gap pattern. */


/** Equal dash/gap pattern. */
private fun makeDashArray(dashPx: Double): JsonArray = buildJsonArray {
    add(buildJsonObject {
        put("n", JsonPrimitive("d"))
        put("nm", JsonPrimitive("dash"))
        put("v", buildJsonObject {
            put("a", JsonPrimitive(0)); put("k", JsonPrimitive(dashPx))
        })
    })
    add(buildJsonObject {
        put("n", JsonPrimitive("g"))
        put("nm", JsonPrimitive("gap"))
        put("v", buildJsonObject {
            put("a", JsonPrimitive(0)); put("k", JsonPrimitive(dashPx))
        })
    })
}

/** A gradient stop carries red, green and blue; alpha is a separate stop list. */
private const val RGB_CHANNELS = 3
