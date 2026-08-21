package org.churchpresenter.lottiegen.lottie.styles

import kotlinx.serialization.json.buildJsonArray
import org.churchpresenter.lottiegen.lottie.Easing
import org.churchpresenter.lottiegen.lottie.KeyframeInput
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.TextMeasurer
import org.churchpresenter.lottiegen.lottie.buildKeyframes
import org.churchpresenter.lottiegen.lottie.emToPx
import org.churchpresenter.lottiegen.lottie.hexToLottie
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.lottie.makeAnimatedRect
import org.churchpresenter.lottiegen.lottie.makeAnimatedStroke
import org.churchpresenter.lottiegen.lottie.makeFill
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makeRect
import org.churchpresenter.lottiegen.lottie.makeStroke
import org.churchpresenter.lottiegen.lottie.makeTextData
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max

class Style3Circular : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val inF = builder.inFrames
        val holdF = builder.holdFrames
        val outF = builder.outFrames

        val baseSize = cfg.baseSize.toDouble()
        val nameSizePx = emToPx(cfg.nameSize.toDouble(), baseSize)
        val infoSizePx = emToPx(cfg.infoSize.toDouble(), baseSize)
        val nameM = TextMeasurer.measure(cfg.nameText, cfg.fontFamily, nameSizePx.toFloat(), cfg.nameWeight, cfg.nameTransform)
        val infoM = TextMeasurer.measure(cfg.infoText, cfg.fontFamily, infoSizePx.toFloat(), cfg.infoWeight, cfg.infoTransform)

        val circleSize = emToPx(5.5, baseSize)
        val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
        val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
        val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
        val cornerPx = emToPx(cfg.corners.toDouble(), baseSize)
        val borderPx = cfg.borderThickness * baseSize * 0.1
        val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)

        val accentLottie = hexToLottie(cfg.accentColor)
        val bgLottie = hexToLottie(cfg.bgColor)
        val borderLottie = hexToLottie(cfg.borderColor)
        val nameCLottie = hexToLottie(cfg.nameColor)
        val infoCLottie = hexToLottie(cfg.infoColor)

        val canvasW = cfg.canvasW.toDouble()
        val canvasH = cfg.canvasH.toDouble()

        val isRight = cfg.align == "right"
        val isCenter = cfg.align == "center"

        val textBlockW = max(nameM.width, infoM.width) + 20
        val bgBarW = textBlockW + emToPx(1.0, baseSize)
        val bgBarH = circleSize - emToPx(0.5, baseSize)

        val baseY = canvasH - marginVPx - circleSize / 2

        val hasLogo = cfg.logoEnabled && cfg.logoData != null

        val circleX: Double = when {
            isRight -> canvasW - marginHPx - circleSize / 2
            isCenter -> {
                val totalW = (if (hasLogo) circleSize else 0.0) + bgBarW
                canvasW / 2 - totalW / 2 + circleSize / 2
            }
            else -> marginHPx + circleSize / 2
        }

        // --- Logo image ---
        if (hasLogo) {
            val rotDir = if (isRight) -90.0 else 90.0
            val logoBgRotKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(rotDir)),
                    KeyframeInput(40.0, jsonArrayOf(0.0)),
                    KeyframeInput(100.0, jsonArrayOf(0.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            builder.addImageAsset("logo_0", cfg.logoData, cfg.logoW, cfg.logoH)
            val maxDim = emToPx(4.5, baseSize)
            val scale = (maxDim / max(cfg.logoW, cfg.logoH).toDouble()) * 100

            val logoScaleKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, 0.0, 100.0)),
                    KeyframeInput(40.0, jsonArrayOf(scale, scale, 100.0)),
                    KeyframeInput(100.0, jsonArrayOf(scale, scale, 100.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            builder.addImageLayer(
                "Logo", "logo_0",
                LottieBuilder.defaultTransform(
                    rotation = LottieBuilder.animatedProp(logoBgRotKFs),
                    position = LottieBuilder.staticPropArray(circleX, baseY, 0.0),
                    anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
                    scale = LottieBuilder.animatedProp(logoScaleKFs)
                )
            )
        }

        // --- Text layers ---
        val textOffset = emToPx(0.75, baseSize)
        val textBaseX: Double = when {
            hasLogo -> if (isRight) circleX - circleSize / 2 - textOffset else circleX + circleSize / 2 + textOffset
            isCenter -> {
                val totalW = bgBarW
                canvasW / 2 - totalW / 2 + textOffset
            }
            else -> if (isRight) canvasW - marginHPx - textOffset else marginHPx + textOffset
        }

        val nameY = baseY - lineSpacingPx / 2 - nameSizePx * 0.1
        val infoY = baseY + lineSpacingPx / 2 + infoSizePx * 0.9
        val slideAmount = circleSize * 1.02
        val justify = if (isRight) 1 else 0

        // Name mask + text
        if (!cfg.hideName) {
            val nameMaskOffsetX = if (isRight) -nameM.width / 2.0 else nameM.width / 2.0
            builder.addShapeLayer(
                "Name Mask",
                buildJsonArray {
                    add(makeGroup(listOf(
                        makeRect(nameM.width + 20.0, nameSizePx * 1.5, 0.0),
                        makeFill(listOf(1.0, 1.0, 1.0))
                    )))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(textBaseX + nameMaskOffsetX, nameY - nameSizePx * 0.15, 0.0)
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.nameWeight)
            val namePosKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(textBaseX, nameY + slideAmount, 0.0)),
                    KeyframeInput(70.0, jsonArrayOf(textBaseX, nameY + slideAmount, 0.0)),
                    KeyframeInput(100.0, jsonArrayOf(textBaseX, nameY, 0.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            builder.addTextLayer(
                "Name",
                makeTextData(cfg.nameText, cfg.fontFamily, nameSizePx, cfg.nameWeight, nameCLottie, cfg.nameTransform, justify),
                LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.nameColorAlpha),
                    position = LottieBuilder.animatedProp(namePosKFs)
                ),
                tt = 1
            )
        }

        // Info mask + text
        if (!cfg.hideInfo) {
            val infoMaskOffsetX = if (isRight) -infoM.width / 2.0 else infoM.width / 2.0
            builder.addShapeLayer(
                "Info Mask",
                buildJsonArray {
                    add(makeGroup(listOf(
                        makeRect(infoM.width + 20.0, infoSizePx * 1.5, 0.0),
                        makeFill(listOf(1.0, 1.0, 1.0))
                    )))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(textBaseX + infoMaskOffsetX, infoY - infoSizePx * 0.15, 0.0)
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.infoWeight)
            val infoPosKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(textBaseX, infoY - slideAmount, 0.0)),
                    KeyframeInput(70.0, jsonArrayOf(textBaseX, infoY - slideAmount, 0.0)),
                    KeyframeInput(100.0, jsonArrayOf(textBaseX, infoY, 0.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            builder.addTextLayer(
                "Info",
                makeTextData(cfg.infoText, cfg.fontFamily, infoSizePx, cfg.infoWeight, infoCLottie, cfg.infoTransform, justify),
                LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.infoColorAlpha),
                    position = LottieBuilder.animatedProp(infoPosKFs)
                ),
                tt = 1
            )
        }

        // --- Logo BG (rounded square) ---
        if (hasLogo) {
            val rotDir = if (isRight) -90.0 else 90.0
            val logoBgScaleKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, 0.0, 100.0)),
                    KeyframeInput(40.0, jsonArrayOf(100.0, 100.0, 100.0)),
                    KeyframeInput(100.0, jsonArrayOf(100.0, 100.0, 100.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )
            val logoBgRotKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(rotDir)),
                    KeyframeInput(40.0, jsonArrayOf(0.0)),
                    KeyframeInput(100.0, jsonArrayOf(0.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            val logoBgItems = mutableListOf(
                makeRect(circleSize, circleSize, cornerPx),
                makeFill(accentLottie, cfg.accentColorAlpha.toDouble())
            )
            val stroke = if (borderPx > 0) makeStroke(borderLottie, borderPx, cfg.borderColorAlpha.toDouble()) else null
            if (stroke != null) {
                logoBgItems.add(stroke)
            }

            builder.addShapeLayer(
                "Logo BG",
                buildJsonArray { add(makeGroup(logoBgItems)) },
                LottieBuilder.defaultTransform(
                    rotation = LottieBuilder.animatedProp(logoBgRotKFs),
                    position = LottieBuilder.staticPropArray(circleX, baseY, 0.0),
                    scale = LottieBuilder.animatedProp(logoBgScaleKFs)
                )
            )
        }

        // --- Background bar ---
        if (cfg.bgEnabled) {
            val bgEdgeX: Double
            val bgCenterEnd: Double

            if (hasLogo) {
                bgEdgeX = if (isRight) circleX - circleSize / 2 else circleX + circleSize / 2
            } else if (isCenter) {
                bgEdgeX = canvasW / 2 - bgBarW / 2
            } else {
                bgEdgeX = if (isRight) canvasW - marginHPx else marginHPx
            }
            bgCenterEnd = if (isCenter && !hasLogo) canvasW / 2
                          else if (isRight) bgEdgeX - bgBarW / 2
                          else bgEdgeX + bgBarW / 2

            val bgSizeKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, bgBarH)),
                    KeyframeInput(40.0, jsonArrayOf(0.0, bgBarH)),
                    KeyframeInput(100.0, jsonArrayOf(bgBarW, bgBarH))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            val bgPosKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(bgEdgeX, baseY, 0.0)),
                    KeyframeInput(40.0, jsonArrayOf(bgEdgeX, baseY, 0.0)),
                    KeyframeInput(100.0, jsonArrayOf(bgCenterEnd, baseY, 0.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            val bgBorderKFs = if (borderPx > 0) buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0)),
                    KeyframeInput(40.0, jsonArrayOf(0.0)),
                    KeyframeInput(100.0, jsonArrayOf(borderPx))
                ), inF, holdF, outF, Easing.DEFAULT
            ) else null

            val bgShapeItems = mutableListOf(
                makeAnimatedRect(bgSizeKFs, cornerPx * 1.1),
                makeFill(bgLottie, cfg.bgColorAlpha.toDouble())
            )
            if (bgBorderKFs != null) {
                bgShapeItems.add(makeAnimatedStroke(borderLottie, bgBorderKFs, cfg.borderColorAlpha.toDouble()))
            }

            builder.addShapeLayer(
                "Background Bar",
                buildJsonArray { add(makeGroup(bgShapeItems)) },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(bgPosKFs)
                )
            )
        }
    }
}
