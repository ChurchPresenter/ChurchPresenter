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
            put("a", JsonPrimitive(0)); put("k", JsonPrimitive(100))
        })
        put("eo", buildJsonObject {
            put("a", JsonPrimitive(0)); put("k", JsonPrimitive(endOpacity))
        })
    })
}

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
        put("ml", JsonPrimitive(4))
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
        put("ml", JsonPrimitive(4))
        put("bm", JsonPrimitive(0))
        if (dashPx > 0) put("d", makeDashArray(dashPx))
    }

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
                    put("a", JsonPrimitive(0)); put("k", jsonArrayOf(100.0, 100.0))
                })
                put("r", transform?.get("r") ?: buildJsonObject {
                    put("a", JsonPrimitive(0)); put("k", JsonPrimitive(0))
                })
                put("o", transform?.get("o") ?: buildJsonObject {
                    put("a", JsonPrimitive(0)); put("k", JsonPrimitive(100))
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
