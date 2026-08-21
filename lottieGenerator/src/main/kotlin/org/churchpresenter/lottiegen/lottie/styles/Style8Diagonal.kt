package org.churchpresenter.lottiegen.lottie.styles

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.lottiegen.lottie.Easing
import org.churchpresenter.lottiegen.lottie.KeyframeInput
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.TextMeasurer
import org.churchpresenter.lottiegen.lottie.buildKeyframes
import org.churchpresenter.lottiegen.lottie.emToPx
import org.churchpresenter.lottiegen.lottie.hexToLottie
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.lottie.makeFill
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makePath
import org.churchpresenter.lottiegen.lottie.makeStroke
import org.churchpresenter.lottiegen.lottie.makeTextRevealAnimator
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig

class Style8Diagonal : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val inF = builder.inFrames
        val holdF = builder.holdFrames
        val outF = builder.outFrames

        val baseSize = cfg.baseSize.toDouble()
        val nameSizePx = emToPx(cfg.nameSize.toDouble(), baseSize)
        val infoSizePx = emToPx(cfg.infoSize.toDouble(), baseSize)
        val paddingX = emToPx(1.0, baseSize)
        val paddingY = emToPx(0.6, baseSize)
        val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
        val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
        val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
        val borderPx = cfg.borderThickness * baseSize * 0.15

        val canvasW = cfg.canvasW.toDouble()
        val canvasH = cfg.canvasH.toDouble()

        val isRight = cfg.align == "right"
        val isCenter = cfg.align == "center"
        val slant = if (isCenter) 0.0 else emToPx(2.5, baseSize)

        val bgLottie = hexToLottie(cfg.bgColor)
        val borderLottie = hexToLottie(cfg.borderColor)
        val nameCLottie = hexToLottie(cfg.nameColor)
        val infoCLottie = hexToLottie(cfg.infoColor)

        // Text measurements
        val nameM = TextMeasurer.measure(cfg.nameText, cfg.fontFamily, nameSizePx.toFloat(), cfg.nameWeight, cfg.nameTransform)
        val infoM = TextMeasurer.measure(cfg.infoText, cfg.fontFamily, infoSizePx.toFloat(), cfg.infoWeight, cfg.infoTransform)

        // Bar dimensions
        val overflow = emToPx(2.0, baseSize)
        val barLeft = -overflow
        val barRight = canvasW + overflow

        // Content height within the bar
        val infoBlockH = if (cfg.hideInfo) 0.0 else infoSizePx
        val nameBlockH = if (cfg.hideName) 0.0 else nameSizePx
        val gap = if (!cfg.hideName && !cfg.hideInfo) lineSpacingPx * 0.05 else 0.0
        val contentH = infoBlockH + gap + nameBlockH + paddingY * 5
        val barH = contentH + slant

        // Bar Y position
        val barFinalY = canvasH - barH / 2

        // Diagonal shape vertices
        val halfW = (barRight - barLeft) / 2
        val halfH = barH / 2
        val vertices: MutableList<MutableList<Double>> = if (isRight) {
            mutableListOf(
                mutableListOf(-halfW, -halfH + slant),  // top-left (lower)
                mutableListOf(halfW, -halfH),            // top-right (higher)
                mutableListOf(halfW, halfH),             // bottom-right
                mutableListOf(-halfW, halfH)             // bottom-left
            )
        } else {
            mutableListOf(
                mutableListOf(-halfW, -halfH),           // top-left (higher)
                mutableListOf(halfW, -halfH + slant),    // top-right (lower)
                mutableListOf(halfW, halfH),             // bottom-right
                mutableListOf(-halfW, halfH)             // bottom-left
            )
        }

        // Shift BG and border down together
        val bgInset = if (isCenter) emToPx(-0.3, baseSize) else emToPx(1.5, baseSize)
        vertices[0] = mutableListOf(vertices[0][0], vertices[0][1] + bgInset)
        vertices[1] = mutableListOf(vertices[1][0], vertices[1][1] + bgInset)
        val lineVertices = listOf(vertices[0].toList(), vertices[1].toList())

        // Text positioning
        val justify = if (isCenter) 2 else if (isRight) 1 else 0
        val textX = when {
            isCenter -> canvasW / 2
            isRight -> canvasW - marginHPx - paddingX
            else -> marginHPx + paddingX
        }

        // Text Y positions relative to bar center
        val contentTopY = -halfH + slant + paddingY * 2.0
        val infoRelY = if (cfg.hideInfo) contentTopY else contentTopY + infoSizePx * 0.4
        val nameRelY = infoRelY +
            (if (cfg.hideInfo) 0.0 else infoBlockH + gap) +
            (if (cfg.hideName) 0.0 else nameSizePx * 0.7)

        // Slide-up animation for the bar
        val slideFrom = canvasH + barH
        val barPosKFs = buildKeyframes(
            listOf(
                KeyframeInput(0.0, jsonArrayOf(canvasW / 2, slideFrom, 0.0)),
                KeyframeInput(0.0, jsonArrayOf(canvasW / 2, slideFrom, 0.0)),
                KeyframeInput(40.0, jsonArrayOf(canvasW / 2, barFinalY, 0.0))
            ), inF, holdF, outF, Easing.DEFAULT
        )

        // --- Info text ---
        if (!cfg.hideInfo) {
            val infoAnimator = makeTextRevealAnimator(30.0, 65.0, -infoSizePx * 0.6, inF, holdF, outF)
            val infoTextData = makeTextDataWithAnimators(
                cfg.infoText, cfg.fontFamily, infoSizePx, cfg.infoWeight,
                infoCLottie, cfg.infoTransform, justify, listOf(infoAnimator)
            )

            builder.addFont(cfg.fontFamily, cfg.infoWeight)
            val infoPosKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(textX, slideFrom + infoRelY, 0.0)),
                    KeyframeInput(0.0, jsonArrayOf(textX, slideFrom + infoRelY, 0.0)),
                    KeyframeInput(40.0, jsonArrayOf(textX, barFinalY + infoRelY, 0.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            builder.addTextLayer(
                "Info", infoTextData,
                LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.infoColorAlpha),
                    position = LottieBuilder.animatedProp(infoPosKFs)
                )
            )
        }

        // --- Name text ---
        if (!cfg.hideName) {
            val nameAnimator = makeTextRevealAnimator(35.0, 70.0, -nameSizePx * 0.6, inF, holdF, outF)
            val nameTextData = makeTextDataWithAnimators(
                cfg.nameText, cfg.fontFamily, nameSizePx, cfg.nameWeight,
                nameCLottie, cfg.nameTransform, justify, listOf(nameAnimator)
            )

            builder.addFont(cfg.fontFamily, cfg.nameWeight)
            val namePosKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(textX, slideFrom + nameRelY, 0.0)),
                    KeyframeInput(0.0, jsonArrayOf(textX, slideFrom + nameRelY, 0.0)),
                    KeyframeInput(40.0, jsonArrayOf(textX, barFinalY + nameRelY, 0.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            builder.addTextLayer(
                "Name", nameTextData,
                LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.nameColorAlpha),
                    position = LottieBuilder.animatedProp(namePosKFs)
                )
            )
        }

        // --- Diagonal line (top edge) ---
        if (borderPx > 0) {
            val stroke = makeStroke(borderLottie, borderPx, cfg.borderColorAlpha.toDouble())
            if (stroke != null) {
                val lineItems = listOf(
                    makePath(lineVertices, false),
                    stroke
                )

                builder.addShapeLayer(
                    "Diagonal Line",
                    buildJsonArray { add(makeGroup(lineItems)) },
                    LottieBuilder.defaultTransform(
                        position = LottieBuilder.animatedProp(barPosKFs)
                    )
                )
            }
        }

        // --- Diagonal bar background ---
        if (cfg.bgEnabled) {
            val verticesList = vertices.map { it.toList() }
            val barItems = listOf(
                makePath(verticesList),
                makeFill(bgLottie, cfg.bgColorAlpha.toDouble())
            )

            builder.addShapeLayer(
                "Diagonal BG",
                buildJsonArray { add(makeGroup(barItems)) },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(barPosKFs)
                )
            )
        }
    }
}

/**
 * Build text data with animators included in the "a" array.
 * Same structure as makeTextData but with animators.
 */
private fun makeTextDataWithAnimators(
    text: String, fontFamily: String, fontSizePx: Double, fontWeight: Int,
    color: List<Double>, transform: String, justify: Int = 0,
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
                        put("lh", JsonPrimitive(fontSizePx * 1.2))
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
