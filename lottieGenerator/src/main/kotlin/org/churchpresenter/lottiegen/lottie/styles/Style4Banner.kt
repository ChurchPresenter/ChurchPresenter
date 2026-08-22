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
import org.churchpresenter.lottiegen.lottie.makeTextData
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max

/** A stroke straddles its path, so the visible edge sits a quarter of its width off centre. */
private const val BORDER_CENTRE_FACTOR = 0.25


class Style4Banner : StyleGenerator {
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

        val paddingX = emToPx(1.0, baseSize)
        val paddingY = emToPx(0.5, baseSize)
        val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
        val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
        val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
        val borderPx = cfg.borderThickness * baseSize * 0.1

        val accentLottie = hexToLottie(cfg.accentColor)
        val bgLottie = hexToLottie(cfg.bgColor)
        val borderLottie = hexToLottie(cfg.borderColor)
        val nameCLottie = hexToLottie(cfg.nameColor)
        val infoCLottie = hexToLottie(cfg.infoColor)

        val canvasW = cfg.canvasW.toDouble()
        val canvasH = cfg.canvasH.toDouble()

        val isRight = cfg.align == "right"

        // --- Dimensions ---
        val nameBoxW = nameM.width + paddingX * 2
        val nameBoxH = nameSizePx + paddingY * 2
        val accentW = nameBoxH * 0.55
        val topLineH = max(2.0, baseSize * 0.1)
        val accentH = nameBoxH + (if (borderPx > 0) borderPx * 1.5 else 0.0)
        val accentWFinal = accentW + (if (borderPx > 0) borderPx else 0.0)
        val topLineW = nameBoxW + accentW
        val infoBarW = topLineW
        val infoBarH = infoSizePx + paddingY * 1.5

        // --- Vertical positioning ---
        val baseY = canvasH - marginVPx - (nameBoxH + lineSpacingPx + infoBarH) / 2

        val nameBgCY = baseY - lineSpacingPx / 2 - infoBarH / 2
        val infoBarCY = baseY + nameBoxH / 2 + lineSpacingPx / 2
        val topLineY = nameBgCY - nameBoxH / 2 - topLineH / 2 - 1

        // --- Horizontal positioning ---
        val nameEdgeX: Double
        val accentCX: Double
        val topLineEdgeX: Double
        val infoEdgeX: Double
        val nameAnchorX: Double
        val topLineAnchorX: Double
        val infoAnchorX: Double

        if (isRight) {
            nameEdgeX = canvasW - marginHPx - nameBoxW
            nameAnchorX = -nameBoxW / 2
            accentCX = nameEdgeX - accentW / 2
            topLineEdgeX = canvasW - marginHPx
            topLineAnchorX = topLineW / 2
            infoEdgeX = canvasW - marginHPx
            infoAnchorX = infoBarW / 2
        } else {
            nameEdgeX = marginHPx + nameBoxW
            nameAnchorX = nameBoxW / 2
            accentCX = nameEdgeX + accentW / 2
            topLineEdgeX = marginHPx
            topLineAnchorX = -topLineW / 2
            infoEdgeX = marginHPx
            infoAnchorX = -infoBarW / 2
        }

        // --- Text X positions ---
        val justify = if (isRight) 1 else 0
        val nameTextX: Double
        val infoTextX: Double
        if (isRight) {
            nameTextX = canvasW - marginHPx - paddingX
            infoTextX = canvasW - marginHPx - paddingX
        } else {
            nameTextX = marginHPx + paddingX
            infoTextX = marginHPx + paddingX
        }

        val nameTextY = nameBgCY + nameSizePx * 0.35
        val infoTextY = infoBarCY + infoSizePx * 0.35

        val dir = if (isRight) -1.0 else 1.0

        // ============= LAYERS (top to bottom render order) =============

        // --- Name mask + text ---
        if (!cfg.hideName) {
            val nameBgSizeKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, nameBoxH)),
                    KeyframeInput(15.0, jsonArrayOf(0.0, nameBoxH)),
                    KeyframeInput(55.0, jsonArrayOf(nameBoxW, nameBoxH))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            builder.addShapeLayer(
                "Name Mask",
                buildJsonArray {
                    add(makeGroup(listOf(
                        makeAnimatedRect(nameBgSizeKFs, 0.0),
                        makeFill(listOf(1.0, 1.0, 1.0))
                    )))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(nameEdgeX, nameBgCY, 0.0),
                    anchor = LottieBuilder.staticPropArray(nameAnchorX, 0.0, 0.0)
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.nameWeight)
            val nameSlideOffset = nameBoxW * dir
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
            val infoBarSizeKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, infoBarH)),
                    KeyframeInput(40.0, jsonArrayOf(0.0, infoBarH)),
                    KeyframeInput(80.0, jsonArrayOf(infoBarW, infoBarH))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            builder.addShapeLayer(
                "Info Mask",
                buildJsonArray {
                    add(makeGroup(listOf(
                        makeAnimatedRect(infoBarSizeKFs, 0.0),
                        makeFill(listOf(1.0, 1.0, 1.0))
                    )))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(infoEdgeX, infoBarCY, 0.0),
                    anchor = LottieBuilder.staticPropArray(infoAnchorX, 0.0, 0.0)
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.infoWeight)
            val infoSlideOffset = infoBarW * -dir
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

        // --- Top line ---
        if (borderPx > 0) {
            val topLineSizeKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, topLineH)),
                    KeyframeInput(30.0, jsonArrayOf(0.0, topLineH)),
                    KeyframeInput(70.0, jsonArrayOf(topLineW, topLineH))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            builder.addShapeLayer(
                "Top Line",
                buildJsonArray {
                    add(makeGroup(listOf(
                        makeAnimatedRect(topLineSizeKFs, 0.0),
                        makeFill(accentLottie, cfg.accentColorAlpha.toDouble())
                    )))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(topLineEdgeX, topLineY, 0.0),
                    anchor = LottieBuilder.staticPropArray(topLineAnchorX, 0.0, 0.0)
                )
            )
        }

        // --- Accent block ---
        if (cfg.bgEnabled) {
            val accentScaleKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, 0.0, 100.0)),
                    KeyframeInput(20.0, jsonArrayOf(100.0, 100.0, 100.0)),
                    KeyframeInput(100.0, jsonArrayOf(100.0, 100.0, 100.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            builder.addShapeLayer(
                "Accent Block",
                buildJsonArray {
                    add(makeGroup(listOf(
                        makeRect(accentWFinal, accentH, 0.0),
                        makeFill(accentLottie, cfg.accentColorAlpha.toDouble())
                    )))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(
                        accentCX,
                        nameBgCY + (if (borderPx > 0) borderPx * BORDER_CENTRE_FACTOR else 0.0),
                        0.0,
                    ),
                    scale = LottieBuilder.animatedProp(accentScaleKFs)
                )
            )
        }

        // --- Name BG (behind text) ---
        if (!cfg.hideName && cfg.bgEnabled) {
            val nameBgExpandKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, nameBoxH)),
                    KeyframeInput(15.0, jsonArrayOf(0.0, nameBoxH)),
                    KeyframeInput(55.0, jsonArrayOf(nameBoxW, nameBoxH))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            val nameBgItems = mutableListOf(
                makeAnimatedRect(nameBgExpandKFs, 0.0),
                makeFill(bgLottie, cfg.bgColorAlpha.toDouble())
            )
            if (borderPx > 0) {
                val bkf = buildKeyframes(
                    listOf(
                        KeyframeInput(0.0, jsonArrayOf(0.0)),
                        KeyframeInput(15.0, jsonArrayOf(0.0)),
                        KeyframeInput(55.0, jsonArrayOf(borderPx))
                    ), inF, holdF, outF, Easing.DEFAULT
                )
                nameBgItems.add(makeAnimatedStroke(borderLottie, bkf, cfg.borderColorAlpha.toDouble()))
            }

            builder.addShapeLayer(
                "Name BG",
                buildJsonArray { add(makeGroup(nameBgItems)) },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(nameEdgeX, nameBgCY, 0.0),
                    anchor = LottieBuilder.staticPropArray(nameAnchorX, 0.0, 0.0)
                )
            )
        }

        // --- Info bar BG (behind info text) ---
        if (!cfg.hideInfo && cfg.bgEnabled) {
            val infoBgExpandKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, infoBarH)),
                    KeyframeInput(40.0, jsonArrayOf(0.0, infoBarH)),
                    KeyframeInput(80.0, jsonArrayOf(infoBarW, infoBarH))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            val infoBgItems = mutableListOf(
                makeAnimatedRect(infoBgExpandKFs, 0.0),
                makeFill(accentLottie, cfg.accentColorAlpha.toDouble())
            )
            if (borderPx > 0) {
                val bkf = buildKeyframes(
                    listOf(
                        KeyframeInput(0.0, jsonArrayOf(0.0)),
                        KeyframeInput(40.0, jsonArrayOf(0.0)),
                        KeyframeInput(80.0, jsonArrayOf(borderPx))
                    ), inF, holdF, outF, Easing.DEFAULT
                )
                infoBgItems.add(makeAnimatedStroke(borderLottie, bkf, cfg.borderColorAlpha.toDouble()))
            }

            builder.addShapeLayer(
                "Info Bar",
                buildJsonArray { add(makeGroup(infoBgItems)) },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(infoEdgeX, infoBarCY, 0.0),
                    anchor = LottieBuilder.staticPropArray(infoAnchorX, 0.0, 0.0)
                )
            )
        }
    }
}
