package lottiegen.lottie.styles

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import lottiegen.lottie.Easing
import lottiegen.lottie.KeyframeInput
import lottiegen.lottie.LottieBuilder
import lottiegen.lottie.TextMeasurer
import lottiegen.lottie.buildKeyframes
import lottiegen.lottie.emToPx
import lottiegen.lottie.hexToLottie
import lottiegen.lottie.jsonArrayOf
import lottiegen.lottie.makeAnimatedRect
import lottiegen.lottie.makeFill
import lottiegen.lottie.makeGroup
import lottiegen.lottie.makeTextData
import lottiegen.lottie.makeTextRevealAnimator
import lottiegen.lottie.remToPx
import lottiegen.model.LottieGenConfig
import kotlin.math.max

class Style6LineSplit : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val inF = builder.inFrames
        val holdF = builder.holdFrames
        val outF = builder.outFrames

        val baseSize = cfg.baseSize.toDouble()
        val nameSizePx = emToPx(cfg.nameSize.toDouble(), baseSize)
        val infoSizePx = emToPx(cfg.infoSize.toDouble(), baseSize)
        val nameM = TextMeasurer.measure(cfg.nameText, cfg.fontFamily, nameSizePx.toFloat(), cfg.nameWeight, cfg.nameTransform)
        val infoM = TextMeasurer.measure(cfg.infoText, cfg.fontFamily, infoSizePx.toFloat(), cfg.infoWeight, cfg.infoTransform)

        val paddingX = emToPx(0.6, baseSize)
        val lineGap = emToPx(0.6, baseSize)
        val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
        val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
        val linePx = max(2.0, cfg.borderThickness * baseSize * 0.1)

        val borderLottie = hexToLottie(cfg.borderColor)
        val nameCLottie = hexToLottie(cfg.nameColor)
        val infoCLottie = hexToLottie(cfg.infoColor)

        val canvasW = cfg.canvasW.toDouble()
        val canvasH = cfg.canvasH.toDouble()

        val isRight = cfg.align == "right"
        val isCenter = cfg.align == "center"

        // Logo space
        val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)
        val logoMargin = emToPx(1.0, baseSize)
        val hasLogo = cfg.logoEnabled && cfg.logoData != null
        val logoSpace = if (hasLogo) logoSizePx + logoMargin else 0.0

        // Line width = max of name/info text widths + padding
        val nameContentW = if (cfg.hideName) 0.0 else nameM.width + paddingX * 2
        val infoContentW = if (cfg.hideInfo) 0.0 else infoM.width + paddingX * 2
        val lineW = max(max(nameContentW, infoContentW), emToPx(4.0, baseSize))

        // Vertical positioning
        val nameBlockH = if (cfg.hideName) 0.0 else nameSizePx
        val infoBlockH = if (cfg.hideInfo) 0.0 else infoSizePx
        val gapAbove = if (cfg.hideName) 0.0 else lineGap
        val gapBelow = if (cfg.hideInfo) 0.0 else lineGap
        val totalH = nameBlockH + gapAbove + linePx + gapBelow + infoBlockH
        val blockTopY = canvasH - marginVPx - totalH
        val lineCY = blockTopY + nameBlockH + gapAbove + linePx / 2

        // Horizontal positioning
        val lineCX: Double
        val nameTextX: Double
        val infoTextX: Double
        val justify: Int

        if (isCenter) {
            lineCX = canvasW / 2
            nameTextX = canvasW / 2
            infoTextX = canvasW / 2
            justify = 2
        } else if (isRight) {
            val lineRight = canvasW - marginHPx - logoSpace
            lineCX = lineRight
            nameTextX = lineRight
            infoTextX = lineRight
            justify = 1
        } else {
            val lineLeft = marginHPx + logoSpace
            lineCX = lineLeft
            nameTextX = lineLeft
            infoTextX = lineLeft
            justify = 0
        }

        val nameTextY = lineCY - lineGap - linePx / 2
        val infoTextY = lineCY + lineGap + linePx / 2 + infoSizePx * 0.425

        // --- Name text (letters pop up from line) ---
        if (!cfg.hideName) {
            val nameAnimator = makeTextRevealAnimator(20.0, 60.0, nameSizePx * 0.8, inF, holdF, outF)
            val nameTextData = makeTextDataWithAnimators(
                cfg.nameText, cfg.fontFamily, nameSizePx, cfg.nameWeight,
                nameCLottie, cfg.nameTransform, justify, listOf(nameAnimator)
            )

            builder.addFont(cfg.fontFamily, cfg.nameWeight)
            builder.addTextLayer(
                "Name", nameTextData,
                LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.nameColorAlpha),
                    position = LottieBuilder.staticPropArray(nameTextX, nameTextY, 0.0)
                )
            )
        }

        // --- Info text (letters pop down from line) ---
        if (!cfg.hideInfo) {
            val infoAnimator = makeTextRevealAnimator(35.0, 75.0, -infoSizePx * 0.8, inF, holdF, outF)
            val infoTextData = makeTextDataWithAnimators(
                cfg.infoText, cfg.fontFamily, infoSizePx, cfg.infoWeight,
                infoCLottie, cfg.infoTransform, justify, listOf(infoAnimator)
            )

            builder.addFont(cfg.fontFamily, cfg.infoWeight)
            builder.addTextLayer(
                "Info", infoTextData,
                LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.infoColorAlpha),
                    position = LottieBuilder.staticPropArray(infoTextX, infoTextY, 0.0)
                )
            )
        }

        // --- Logo (optional) ---
        if (hasLogo) {
            val logoScale = (logoSizePx / cfg.logoH.toDouble()) * 100

            val logoCX = when {
                isCenter -> canvasW / 2 - lineW / 2 - logoMargin - logoSizePx / 2
                isRight -> canvasW - marginHPx - logoSizePx / 2
                else -> marginHPx + logoSizePx / 2
            }
            val logoCY = lineCY

            val logoScaleKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, 0.0, 100.0)),
                    KeyframeInput(25.0, jsonArrayOf(logoScale, logoScale, 100.0)),
                    KeyframeInput(100.0, jsonArrayOf(logoScale, logoScale, 100.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            builder.addImageAsset("logo", cfg.logoData, cfg.logoW, cfg.logoH)
            builder.addImageLayer(
                "Logo", "logo",
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(logoCX, logoCY, 0.0),
                    anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
                    scale = LottieBuilder.animatedProp(logoScaleKFs)
                )
            )
        }

        // --- Horizontal line (expands from alignment edge or center) ---
        val lineAnchorX = when {
            isCenter -> 0.0
            isRight -> lineW / 2
            else -> -lineW / 2
        }

        val lineSizeKFs = buildKeyframes(
            listOf(
                KeyframeInput(0.0, jsonArrayOf(0.0, linePx)),
                KeyframeInput(0.0, jsonArrayOf(0.0, linePx)),
                KeyframeInput(40.0, jsonArrayOf(lineW, linePx))
            ), inF, holdF, outF, Easing.DEFAULT
        )

        val lineShapes = buildJsonArray {
            add(makeGroup(listOf(
                makeAnimatedRect(lineSizeKFs, 0.0),
                makeFill(borderLottie, cfg.borderColorAlpha.toDouble())
            )))
        }

        builder.addShapeLayer(
            "Line", lineShapes,
            LottieBuilder.defaultTransform(
                position = LottieBuilder.staticPropArray(lineCX, lineCY, 0.0),
                anchor = LottieBuilder.staticPropArray(lineAnchorX, 0.0, 0.0)
            )
        )
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
