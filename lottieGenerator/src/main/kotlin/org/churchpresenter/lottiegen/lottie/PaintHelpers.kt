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

/**
 * A linear gradient through [colors], spread evenly from [startPt] to [endPt].
 *
 * [stopAlphas] gives each stop its own opacity, 0..1, which is how a gradient fades out rather than
 * merely darkening, and [rampEnd] is where the last colour is reached, 0..1 — past it the
 * gradient holds that colour, so a lower end finishes the blend sooner.
 *
 * Lottie keeps colour stops and alpha stops in the same array — every colour stop first, then
 * every alpha stop — so the two are written one after the other below. Omitting the alphas
 * leaves the gradient opaque and [opacity] alone governs it, which is what a plain ramp wants.
 */
fun makeGradientFillStops(
    colors: List<List<Double>>,
    opacity: Double = 100.0,
    startPt: List<Double>,
    endPt: List<Double>,
    stopAlphas: List<Double> = List(colors.size) { 1.0 },
    rampEnd: Double = 1.0,
): JsonObject = buildJsonObject {
    require(colors.size >= 2) { "A gradient needs at least two colours" }
    require(stopAlphas.size == colors.size) { "A gradient needs one alpha per colour stop" }
    // The blend is squeezed into 0..rampEnd; past the last stop a Lottie gradient holds that
    // colour, so a lower end means the transition finishes sooner and stays flat after it.
    val step = rampEnd.coerceIn(MIN_RAMP_END, 1.0) / (colors.size - 1)
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
                stopAlphas.forEachIndexed { i, a ->
                    add(JsonPrimitive(i * step)); add(JsonPrimitive(a.coerceIn(0.0, 1.0)))
                }
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

/** A ramp has to have some length; zero would put every stop on top of the last. */
private const val MIN_RAMP_END = 0.01
