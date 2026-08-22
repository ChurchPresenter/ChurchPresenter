package org.churchpresenter.lottiegen.lottie.styles

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.churchpresenter.lottiegen.lottie.Easing
import org.churchpresenter.lottiegen.lottie.KeyframeInput
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.TextMeasurer
import org.churchpresenter.lottiegen.lottie.buildKeyframes
import org.churchpresenter.lottiegen.lottie.emToPx
import org.churchpresenter.lottiegen.lottie.makeAnimatedRect
import org.churchpresenter.lottiegen.lottie.hexToLottie
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.lottie.makeFill
import org.churchpresenter.lottiegen.lottie.makeGradientFill
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makeRect
import org.churchpresenter.lottiegen.lottie.makeStroke
import org.churchpresenter.lottiegen.lottie.makeTextData
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max

class Style5GradientBar : StyleGenerator {

    /**
     * 4-stop gradient fill for center alignment: fades on both edges.
     */
    private fun makeCenterGradientFill(
        color: List<Double>,
        opacity: Double,
        barW: Double,
        gradientExtra: Double,
    ): JsonObject {
        val r = color[0]
        val g = color[1]
        val b = color[2]
        val fadeHalf = gradientExtra / 2
        val fadeRatio = fadeHalf / barW
        return buildJsonObject {
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
                put("k", jsonArrayOf(-barW / 2, 0.0))
            })
            put("e", buildJsonObject {
                put("a", JsonPrimitive(0))
                put("k", jsonArrayOf(barW / 2, 0.0))
            })
            put("g", buildJsonObject {
                put("p", JsonPrimitive(4))
                put("k", buildJsonObject {
                    put("a", JsonPrimitive(0))
                    put("k", buildJsonArray {
                        // Color stops
                        add(JsonPrimitive(0.0)); add(JsonPrimitive(r)); add(JsonPrimitive(g)); add(JsonPrimitive(b))
                        add(JsonPrimitive(fadeRatio))
                        add(JsonPrimitive(r)); add(JsonPrimitive(g)); add(JsonPrimitive(b))
                        add(JsonPrimitive(1 - fadeRatio))
                        add(JsonPrimitive(r)); add(JsonPrimitive(g)); add(JsonPrimitive(b))
                        add(JsonPrimitive(1.0)); add(JsonPrimitive(r)); add(JsonPrimitive(g)); add(JsonPrimitive(b))
                        // Opacity stops
                        add(JsonPrimitive(0.0)); add(JsonPrimitive(0.0))
                        add(JsonPrimitive(fadeRatio)); add(JsonPrimitive(1.0))
                        add(JsonPrimitive(1 - fadeRatio)); add(JsonPrimitive(1.0))
                        add(JsonPrimitive(1.0)); add(JsonPrimitive(0.0))
                    })
                })
            })
        }
    }

    /**
     * Produces gradient fill appropriate for the current alignment.
     */
    private fun makeBarGradient(
        color: List<Double>,
        opacity: Double,
        barW: Double,
        isCenter: Boolean,
        isRight: Boolean,
        gradientExtra: Double,
    ): JsonObject {
        return if (isCenter) {
            makeCenterGradientFill(color, opacity, barW, gradientExtra)
        } else if (isRight) {
            makeGradientFill(color, opacity, listOf(-barW / 2 + gradientExtra, 0.0), listOf(-barW / 2, 0.0))
        } else {
            makeGradientFill(color, opacity, listOf(barW / 2 - gradientExtra, 0.0), listOf(barW / 2, 0.0))
        }
    }

    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val inF = builder.inFrames
        val holdF = builder.holdFrames
        val outF = builder.outFrames

        val baseSize = cfg.baseSize.toDouble()
        val nameSizePx = emToPx(cfg.nameSize.toDouble(), baseSize)
        val infoSizePx = emToPx(cfg.infoSize.toDouble(), baseSize)
        val nameM = TextMeasurer.measure(
            cfg.nameText,
            cfg.fontFamily,
            nameSizePx.toFloat(),
            cfg.nameWeight,
            cfg.nameTransform,
        )
        val infoM = TextMeasurer.measure(
            cfg.infoText,
            cfg.fontFamily,
            infoSizePx.toFloat(),
            cfg.infoWeight,
            cfg.infoTransform,
        )

        val paddingX = emToPx(1.0, baseSize)
        val paddingY = emToPx(0.5, baseSize)
        val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
        val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
        val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
        val cornerPx = emToPx(cfg.corners.toDouble(), baseSize)
        val borderPx = cfg.borderThickness * baseSize * 0.1

        val bgLottie = hexToLottie(cfg.bgColor)
        val accentLottie = hexToLottie(cfg.accentColor)
        val borderLottie = hexToLottie(cfg.borderColor)
        val nameCLottie = hexToLottie(cfg.nameColor)
        val infoCLottie = hexToLottie(cfg.infoColor)

        val canvasW = cfg.canvasW.toDouble()
        val canvasH = cfg.canvasH.toDouble()

        val isRight = cfg.align == "right"
        val isCenter = cfg.align == "center"

        // Bar dimensions
        val gradientExtra = emToPx(3.0, baseSize)
        val nameBarW = nameM.width + paddingX * 2 + gradientExtra
        val nameBarH = nameSizePx + paddingY * 2
        val infoBarW = infoM.width + paddingX * 2 + gradientExtra
        val infoBarH = infoSizePx + paddingY * 1.5

        // Vertical positioning
        val nameBlockH = if (cfg.hideName) 0.0 else nameBarH
        val infoBlockH = if (cfg.hideInfo) 0.0 else infoBarH
        val gap = if (!cfg.hideName && !cfg.hideInfo) lineSpacingPx else 0.0
        val totalBlockH = nameBlockH + gap + infoBlockH
        val baseY = canvasH - marginVPx - totalBlockH / 2
        val nameBgCY = if (cfg.hideName) baseY else baseY - (gap + infoBlockH) / 2
        val infoBarCY = if (cfg.hideInfo) baseY else baseY + (nameBlockH + gap) / 2

        // Logo space calculation
        val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)
        val logoMargin = emToPx(0.5, baseSize)
        val hasLogo = cfg.logoEnabled && cfg.logoData != null
        val logoBgSize = logoSizePx + emToPx(0.8, baseSize)
        val logoSpace = if (hasLogo) logoBgSize + logoMargin else 0.0

        // Horizontal positioning
        val nameBarCX: Double
        val infoBarCX: Double
        val nameTextX: Double
        val infoTextX: Double
        val justify: Int

        if (isCenter) {
            val centerOffset = logoSpace / 2
            nameBarCX = canvasW / 2 + centerOffset
            infoBarCX = canvasW / 2 + centerOffset
            nameTextX = canvasW / 2 + centerOffset
            infoTextX = canvasW / 2 + centerOffset
            justify = 2
        } else if (isRight) {
            nameBarCX = canvasW - marginHPx - logoSpace - nameBarW / 2
            infoBarCX = canvasW - marginHPx - logoSpace - infoBarW / 2
            nameTextX = canvasW - marginHPx - logoSpace - paddingX
            infoTextX = canvasW - marginHPx - logoSpace - paddingX
            justify = 1
        } else {
            nameBarCX = marginHPx + logoSpace + nameBarW / 2
            infoBarCX = marginHPx + logoSpace + infoBarW / 2
            nameTextX = marginHPx + logoSpace + paddingX
            infoTextX = marginHPx + logoSpace + paddingX
            justify = 0
        }

        val nameTextY = nameBgCY + nameSizePx * 0.35
        val infoTextY = infoBarCY + infoSizePx * 0.35

        val slideDistance = canvasW

        // ============= LAYERS =============

        // --- Name mask + text ---
        if (!cfg.hideName) {
            val nameMaskW = nameM.width + paddingX * 2
            val nameMaskSizeKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, nameBarH)),
                    KeyframeInput(15.0, jsonArrayOf(0.0, nameBarH)),
                    KeyframeInput(55.0, jsonArrayOf(nameMaskW, nameBarH))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            val nameMaskX: Double
            val nameMaskAnchorX: Double
            if (isCenter) {
                nameMaskX = canvasW / 2 + logoSpace / 2
                nameMaskAnchorX = 0.0
            } else if (isRight) {
                nameMaskX = canvasW - marginHPx - logoSpace
                nameMaskAnchorX = nameMaskW / 2
            } else {
                nameMaskX = marginHPx + logoSpace
                nameMaskAnchorX = -nameMaskW / 2
            }

            builder.addShapeLayer(
                "Name Mask",
                buildJsonArray {
                    add(makeGroup(listOf(
                        makeAnimatedRect(nameMaskSizeKFs, 0.0),
                        makeFill(listOf(1.0, 1.0, 1.0))
                    )))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(nameMaskX, nameBgCY, 0.0),
                    anchor = LottieBuilder.staticPropArray(nameMaskAnchorX, 0.0, 0.0)
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.nameWeight)
            val nameSlideDir = if (isRight) 1.0 else -1.0
            val nameSlideOffset = if (isCenter) -slideDistance else nameSlideDir * slideDistance
            val namePosKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(nameTextX + nameSlideOffset, nameTextY, 0.0)),
                    KeyframeInput(15.0, jsonArrayOf(nameTextX + nameSlideOffset, nameTextY, 0.0)),
                    KeyframeInput(55.0, jsonArrayOf(nameTextX, nameTextY, 0.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            builder.addTextLayer(
                "Name",
                makeTextData(
                    cfg.nameText,
                    cfg.fontFamily,
                    nameSizePx,
                    cfg.nameWeight,
                    nameCLottie,
                    cfg.nameTransform,
                    justify,
                ),
                LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.nameColorAlpha),
                    position = LottieBuilder.animatedProp(namePosKFs)
                ),
                tt = 1
            )
        }

        // --- Info mask + text ---
        if (!cfg.hideInfo) {
            val infoMaskW = infoM.width + paddingX * 2
            val infoMaskSizeKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, infoBarH)),
                    KeyframeInput(40.0, jsonArrayOf(0.0, infoBarH)),
                    KeyframeInput(80.0, jsonArrayOf(infoMaskW, infoBarH))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            val infoMaskX: Double
            val infoMaskAnchorX: Double
            if (isCenter) {
                infoMaskX = canvasW / 2 + logoSpace / 2
                infoMaskAnchorX = 0.0
            } else if (isRight) {
                infoMaskX = canvasW - marginHPx - logoSpace
                infoMaskAnchorX = infoMaskW / 2
            } else {
                infoMaskX = marginHPx + logoSpace
                infoMaskAnchorX = -infoMaskW / 2
            }

            builder.addShapeLayer(
                "Info Mask",
                buildJsonArray {
                    add(makeGroup(listOf(
                        makeAnimatedRect(infoMaskSizeKFs, 0.0),
                        makeFill(listOf(1.0, 1.0, 1.0))
                    )))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(infoMaskX, infoBarCY, 0.0),
                    anchor = LottieBuilder.staticPropArray(infoMaskAnchorX, 0.0, 0.0)
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.infoWeight)
            val infoSlideDir = if (isRight) 1.0 else -1.0
            val infoSlideOffset = if (isCenter) slideDistance else infoSlideDir * slideDistance
            val infoPosKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(infoTextX + infoSlideOffset, infoTextY, 0.0)),
                    KeyframeInput(40.0, jsonArrayOf(infoTextX + infoSlideOffset, infoTextY, 0.0)),
                    KeyframeInput(80.0, jsonArrayOf(infoTextX, infoTextY, 0.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            builder.addTextLayer(
                "Info",
                makeTextData(
                    cfg.infoText,
                    cfg.fontFamily,
                    infoSizePx,
                    cfg.infoWeight,
                    infoCLottie,
                    cfg.infoTransform,
                    justify,
                ),
                LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.infoColorAlpha),
                    position = LottieBuilder.animatedProp(infoPosKFs)
                ),
                tt = 1
            )
        }

        // --- Logo (optional) ---
        if (hasLogo) {
            val lH = logoSizePx
            val _logoScale = (lH / cfg.logoH) * 100

            val logoCX: Double = if (isCenter) {
                val maxBarW = max(nameBarW, infoBarW)
                val centerOffset = logoSpace / 2
                canvasW / 2 + centerOffset - maxBarW / 2 - logoMargin - logoBgSize / 2
            } else if (isRight) {
                canvasW - marginHPx - logoBgSize / 2
            } else {
                marginHPx + logoBgSize / 2
            }
            val logoCY = (nameBgCY + infoBarCY) / 2

            val logoScaleKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, 0.0, 100.0)),
                    KeyframeInput(30.0, jsonArrayOf(_logoScale, _logoScale, 100.0)),
                    KeyframeInput(100.0, jsonArrayOf(_logoScale, _logoScale, 100.0))
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

            // Solid accent BG behind logo
            val logoBgScaleKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, 0.0, 100.0)),
                    KeyframeInput(25.0, jsonArrayOf(100.0, 100.0, 100.0)),
                    KeyframeInput(100.0, jsonArrayOf(100.0, 100.0, 100.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            val logoBgItems = mutableListOf(
                makeRect(logoBgSize, logoBgSize, cornerPx),
                makeFill(accentLottie, cfg.accentColorAlpha.toDouble())
            )
            if (borderPx > 0) {
                val stroke = makeStroke(borderLottie, borderPx, cfg.borderColorAlpha.toDouble())
                if (stroke != null) logoBgItems.add(stroke)
            }

            builder.addShapeLayer(
                "Logo BG",
                buildJsonArray { add(makeGroup(logoBgItems)) },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(logoCX, logoCY, 0.0),
                    scale = LottieBuilder.animatedProp(logoBgScaleKFs)
                )
            )
        }

        // --- Name gradient bar background ---
        if (!cfg.hideName && cfg.bgEnabled) {
            val nameBarItems = mutableListOf(
                makeRect(nameBarW, nameBarH, cornerPx),
                makeBarGradient(bgLottie, 100.0, nameBarW, isCenter, isRight, gradientExtra)
            )
            if (borderPx > 0) {
                val stroke = makeStroke(borderLottie, borderPx, cfg.borderColorAlpha.toDouble())
                if (stroke != null) nameBarItems.add(stroke)
            }

            val nameBarTransform: JsonObject
            if (isCenter) {
                val nameBarScaleKFs = buildKeyframes(
                    listOf(
                        KeyframeInput(0.0, jsonArrayOf(0.0, 100.0, 100.0)),
                        KeyframeInput(10.0, jsonArrayOf(0.0, 100.0, 100.0)),
                        KeyframeInput(50.0, jsonArrayOf(100.0, 100.0, 100.0))
                    ), inF, holdF, outF, Easing.DEFAULT
                )
                nameBarTransform = LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.bgColorAlpha),
                    position = LottieBuilder.staticPropArray(nameBarCX, nameBgCY, 0.0),
                    scale = LottieBuilder.animatedProp(nameBarScaleKFs)
                )
            } else {
                val nameBarSlideStart = if (isRight) slideDistance else -slideDistance
                val nameBarPosKFs = buildKeyframes(
                    listOf(
                        KeyframeInput(0.0, jsonArrayOf(nameBarCX + nameBarSlideStart, nameBgCY, 0.0)),
                        KeyframeInput(10.0, jsonArrayOf(nameBarCX + nameBarSlideStart, nameBgCY, 0.0)),
                        KeyframeInput(50.0, jsonArrayOf(nameBarCX, nameBgCY, 0.0))
                    ), inF, holdF, outF, Easing.DEFAULT
                )
                nameBarTransform = LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.bgColorAlpha),
                    position = LottieBuilder.animatedProp(nameBarPosKFs)
                )
            }

            builder.addShapeLayer(
                "Name Gradient Bar",
                buildJsonArray { add(makeGroup(nameBarItems)) },
                nameBarTransform
            )
        }

        // --- Info gradient bar background ---
        if (!cfg.hideInfo && cfg.bgEnabled) {
            val infoBarItems = mutableListOf(
                makeRect(infoBarW, infoBarH, cornerPx),
                makeBarGradient(accentLottie, 100.0, infoBarW, isCenter, isRight, gradientExtra)
            )
            if (borderPx > 0) {
                val stroke = makeStroke(borderLottie, borderPx, cfg.borderColorAlpha.toDouble())
                if (stroke != null) infoBarItems.add(stroke)
            }

            val infoBarTransform: JsonObject
            if (isCenter) {
                val infoBarScaleKFs = buildKeyframes(
                    listOf(
                        KeyframeInput(0.0, jsonArrayOf(0.0, 100.0, 100.0)),
                        KeyframeInput(35.0, jsonArrayOf(0.0, 100.0, 100.0)),
                        KeyframeInput(75.0, jsonArrayOf(100.0, 100.0, 100.0))
                    ), inF, holdF, outF, Easing.DEFAULT
                )
                infoBarTransform = LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.accentColorAlpha),
                    position = LottieBuilder.staticPropArray(infoBarCX, infoBarCY, 0.0),
                    scale = LottieBuilder.animatedProp(infoBarScaleKFs)
                )
            } else {
                val infoBarSlideStart = if (isRight) slideDistance else -slideDistance
                val infoBarPosKFs = buildKeyframes(
                    listOf(
                        KeyframeInput(0.0, jsonArrayOf(infoBarCX + infoBarSlideStart, infoBarCY, 0.0)),
                        KeyframeInput(35.0, jsonArrayOf(infoBarCX + infoBarSlideStart, infoBarCY, 0.0)),
                        KeyframeInput(75.0, jsonArrayOf(infoBarCX, infoBarCY, 0.0))
                    ), inF, holdF, outF, Easing.DEFAULT
                )
                infoBarTransform = LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.accentColorAlpha),
                    position = LottieBuilder.animatedProp(infoBarPosKFs)
                )
            }

            builder.addShapeLayer(
                "Info Gradient Bar",
                buildJsonArray { add(makeGroup(infoBarItems)) },
                infoBarTransform
            )
        }
    }
}
