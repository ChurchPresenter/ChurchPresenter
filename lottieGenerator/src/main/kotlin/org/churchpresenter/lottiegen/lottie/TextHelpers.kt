package org.churchpresenter.lottiegen.lottie

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

fun makeTextData(
    text: String,
    fontFamily: String,
    fontSizePx: Double,
    fontWeight: Int,
    color: List<Double>,
    transform: String,
    justify: Int = 0
): JsonObject {
    val displayText = if (transform == "uppercase") text.uppercase() else text
    val style = if (fontWeight >= 700) "Bold" else "Regular"
    val fName = "$fontFamily-$style"
    return buildJsonObject {
        put("d", buildJsonObject {
            put("k", buildJsonArray {
                add(buildJsonObject {
                    put("s", buildJsonObject {
                        put("s", JsonPrimitive(fontSizePx))
                        put("f", JsonPrimitive(fName))
                        put("t", JsonPrimitive(displayText))
                        put("ca", JsonPrimitive(0))
                        put("j", JsonPrimitive(justify))
                        put("tr", JsonPrimitive(0))
                        put("lh", JsonPrimitive(fontSizePx * LINE_HEIGHT_FACTOR))
                        put("ls", JsonPrimitive(0))
                        put("fc", jsonArrayOf(color))
                    })
                    put("t", JsonPrimitive(0))
                })
            })
        })
        put("p", buildJsonObject { })
        put("m", buildJsonObject {
            put("g", JsonPrimitive(1))
            put("a", buildJsonObject {
                put("a", JsonPrimitive(0))
                put("k", jsonArrayOf(0.0, 0.0))
            })
        })
        put("a", buildJsonArray { })
    }
}

/**
 * Same as makeTextData but with text animators (reveal, random fade, etc.) included.
 * In JS this is done by mutating textData.a.push(...), but in Kotlin we build it immutably.
 */
fun makeTextDataWithAnimators(
    text: String,
    fontFamily: String,
    fontSizePx: Double,
    fontWeight: Int,
    color: List<Double>,
    transform: String,
    justify: Int = 0,
    animators: List<JsonObject>
): JsonObject {
    val displayText = if (transform == "uppercase") text.uppercase() else text
    val style = if (fontWeight >= 700) "Bold" else "Regular"
    val fName = "$fontFamily-$style"
    return buildJsonObject {
        put("d", buildJsonObject {
            put("k", buildJsonArray {
                add(buildJsonObject {
                    put("s", buildJsonObject {
                        put("s", JsonPrimitive(fontSizePx))
                        put("f", JsonPrimitive(fName))
                        put("t", JsonPrimitive(displayText))
                        put("ca", JsonPrimitive(0))
                        put("j", JsonPrimitive(justify))
                        put("tr", JsonPrimitive(0))
                        put("lh", JsonPrimitive(fontSizePx * LINE_HEIGHT_FACTOR))
                        put("ls", JsonPrimitive(0))
                        put("fc", jsonArrayOf(color))
                    })
                    put("t", JsonPrimitive(0))
                })
            })
        })
        put("p", buildJsonObject { })
        put("m", buildJsonObject {
            put("g", JsonPrimitive(1))
            put("a", buildJsonObject {
                put("a", JsonPrimitive(0))
                put("k", jsonArrayOf(0.0, 0.0))
            })
        })
        put("a", buildJsonArray { animators.forEach { add(it) } })
    }
}

/**
 * Build a text animator for per-character reveal (letters pop from a position offset).
 * Characters start displaced + invisible, then appear sequentially as range selector sweeps.
 */
fun makeTextRevealAnimator(
    startPct: Double,
    endPct: Double,
    posOffset: Double,
    inFrames: Int,
    holdFrames: Int,
    outFrames: Int
): JsonObject {
    val totalOut = inFrames + holdFrames
    val e = Easing.DEFAULT

    val inKFs = listOf(
        KeyframeInput(0.0, jsonArrayOf(0.0)),
        KeyframeInput(startPct, jsonArrayOf(0.0)),
        KeyframeInput(endPct, jsonArrayOf(100.0))
    )

    // Build full keyframe sequence (in + hold + out with reverse)
    val kfs = mutableListOf<JsonObject>()
    for (input in inKFs) {
        kfs.add(kf(pctToFrame(input.pct, inFrames), input.value, e))
    }
    // Hold at final value
    kfs.add(kf(totalOut, jsonArrayOf(FULL_PERCENT_D), e))
    // Out: reverse (100 → 0, characters re-enter selection and disappear)
    val reversedIn = inKFs.reversed()
    for (i in 1 until reversedIn.size) {
        val pctFromEnd = 100.0 - reversedIn[i].pct
        val frame = totalOut + pctToFrame(pctFromEnd, outFrames)
        val isLast = i == reversedIn.size - 1
        kfs.add(
            if (isLast) kf(frame, reversedIn[i].value)
            else kf(frame, reversedIn[i].value, e)
        )
    }

    return buildJsonObject {
        put("nm", JsonPrimitive("Reveal"))
        put("s", buildJsonObject {
            put("t", JsonPrimitive(0))
            put("b", JsonPrimitive(1))
            put("sh", JsonPrimitive(1))
            put("r", JsonPrimitive(1))
            put("o", buildJsonObject { put("a", JsonPrimitive(0)); put("k", JsonPrimitive(0)) })
            put("s", buildJsonObject {
                put("a", JsonPrimitive(1))
                put("k", buildJsonArray { kfs.forEach { add(it) } })
            })
            put("e", buildJsonObject { put("a", JsonPrimitive(0)); put("k", JsonPrimitive(FULL_PERCENT)) })
            put("ne", buildJsonObject { put("a", JsonPrimitive(0)); put("k", JsonPrimitive(0)) })
            put("xe", buildJsonObject { put("a", JsonPrimitive(0)); put("k", JsonPrimitive(0)) })
            put("a", buildJsonObject { put("a", JsonPrimitive(0)); put("k", JsonPrimitive(FULL_PERCENT)) })
        })
        put("a", buildJsonObject {
            put("o", buildJsonObject { put("a", JsonPrimitive(0)); put("k", JsonPrimitive(0)) })
            put("p", buildJsonObject { put("a", JsonPrimitive(0)); put("k", jsonArrayOf(0.0, posOffset, 0.0)) })
        })
    }
}

/**
 * Build a text animator for random per-character fade-in (opacity only, no position offset).
 */
fun makeRandomFadeAnimator(
    startPct: Double,
    endPct: Double,
    inFrames: Int,
    holdFrames: Int,
    outFrames: Int
): JsonObject {
    val totalOut = inFrames + holdFrames
    val e = Easing.DEFAULT

    val inKFs = listOf(
        KeyframeInput(0.0, jsonArrayOf(0.0)),
        KeyframeInput(startPct, jsonArrayOf(0.0)),
        KeyframeInput(endPct, jsonArrayOf(100.0))
    )

    val kfs = mutableListOf<JsonObject>()
    for (input in inKFs) {
        kfs.add(kf(pctToFrame(input.pct, inFrames), input.value, e))
    }
    kfs.add(kf(totalOut, jsonArrayOf(FULL_PERCENT_D), e))
    val reversedIn = inKFs.reversed()
    for (i in 1 until reversedIn.size) {
        val pctFromEnd = 100.0 - reversedIn[i].pct
        val frame = totalOut + pctToFrame(pctFromEnd, outFrames)
        val isLast = i == reversedIn.size - 1
        kfs.add(
            if (isLast) kf(frame, reversedIn[i].value)
            else kf(frame, reversedIn[i].value, e)
        )
    }

    return buildJsonObject {
        put("nm", JsonPrimitive("Random Fade"))
        put("s", buildJsonObject {
            put("t", JsonPrimitive(0))
            put("b", JsonPrimitive(1))
            put("sh", JsonPrimitive(1))
            put("rn", JsonPrimitive(1))
            put("r", JsonPrimitive(1))
            put("o", buildJsonObject { put("a", JsonPrimitive(0)); put("k", JsonPrimitive(0)) })
            put("s", buildJsonObject {
                put("a", JsonPrimitive(1))
                put("k", buildJsonArray { kfs.forEach { add(it) } })
            })
            put("e", buildJsonObject { put("a", JsonPrimitive(0)); put("k", JsonPrimitive(FULL_PERCENT)) })
            put("ne", buildJsonObject { put("a", JsonPrimitive(0)); put("k", JsonPrimitive(0)) })
            put("xe", buildJsonObject { put("a", JsonPrimitive(0)); put("k", JsonPrimitive(0)) })
            put("a", buildJsonObject { put("a", JsonPrimitive(0)); put("k", JsonPrimitive(FULL_PERCENT)) })
        })
        put("a", buildJsonObject {
            put("o", buildJsonObject { put("a", JsonPrimitive(0)); put("k", JsonPrimitive(0)) })
        })
    }
}
