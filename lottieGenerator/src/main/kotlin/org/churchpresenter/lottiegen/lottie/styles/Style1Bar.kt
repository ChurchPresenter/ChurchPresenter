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

class Style1Bar : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val inF = builder.inFrames
        val holdF = builder.holdFrames
        val outF = builder.outFrames

        val baseSize = cfg.baseSize.toDouble()
        val nameSizePx = emToPx(cfg.nameSize.toDouble(), baseSize)
        val infoSizePx = emToPx(cfg.infoSize.toDouble(), baseSize)
        val nameM = TextMeasurer.measure(cfg.nameText, cfg.fontFamily, nameSizePx.toFloat(), cfg.nameWeight, cfg.nameTransform)
        val infoM = TextMeasurer.measure(cfg.infoText, cfg.fontFamily, infoSizePx.toFloat(), cfg.infoWeight, cfg.infoTransform)

        val barWidth = emToPx(0.3, baseSize)
        val barHeight = emToPx(3.5, baseSize)
        val textMargin = emToPx(1.2, baseSize)
        val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
        val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
        val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
        val cornerPx = emToPx(cfg.corners.toDouble(), baseSize)
        val borderPx = cfg.borderThickness * baseSize * 0.1
        val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)
        val logoMargin = emToPx(0.8, baseSize)

        val accentLottie = hexToLottie(cfg.accentColor)
        val bgLottie = hexToLottie(cfg.bgColor)
        val borderLottie = hexToLottie(cfg.borderColor)
        val nameCLottie = hexToLottie(cfg.nameColor)
        val infoCLottie = hexToLottie(cfg.infoColor)

        // Calculate total content width
        val textBlockW = max(nameM.width, infoM.width) + textMargin + 10
        val totalContentW = textBlockW + barWidth + if (cfg.logoEnabled) logoSizePx + logoMargin else 0.0
        val bgW = totalContentW + emToPx(2.0, baseSize)
        val bgH = barHeight + emToPx(2.0, baseSize)

        val canvasW = cfg.canvasW.toDouble()
        val canvasH = cfg.canvasH.toDouble()

        // Determine base position based on alignment
        val baseY = canvasH - marginVPx - barHeight / 2

        val isRight = cfg.align == "right"
        val isCenter = cfg.align == "center"

        val baseX = when {
            isRight -> canvasW - marginHPx - totalContentW / 2
            isCenter -> canvasW / 2
            else -> marginHPx + totalContentW / 2
        }

        // Calculate bar position
        val logoSpace = if (cfg.logoEnabled) logoSizePx + logoMargin else 0.0
        val barX = when {
            isRight -> baseX + totalContentW / 2 - logoSpace - barWidth / 2
            else -> baseX - totalContentW / 2 + logoSpace + barWidth / 2
        }

        // --- Logo layer ---
        if (cfg.logoEnabled && cfg.logoData != null) {
            builder.addImageAsset("logo_0", cfg.logoData, cfg.logoW, cfg.logoH)
            val scale = (logoSizePx / max(cfg.logoW, cfg.logoH).toDouble()) * 100

            val logoX = if (isRight) {
                baseX + totalContentW / 2 - logoSizePx / 2
            } else {
                baseX - totalContentW / 2 + logoSizePx / 2
            }

            val logoStartX = barX

            val posKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(logoStartX, baseY, 0.0)),
                    KeyframeInput(55.0, jsonArrayOf(logoStartX, baseY, 0.0)),
                    KeyframeInput(100.0, jsonArrayOf(logoX, baseY, 0.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            val opKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0)),
                    KeyframeInput(55.0, jsonArrayOf(0.0)),
                    KeyframeInput(100.0, jsonArrayOf(100.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            builder.addImageLayer(
                "Logo", "logo_0",
                LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.animatedProp(opKFs),
                    position = LottieBuilder.animatedProp(posKFs),
                    anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
                    scale = LottieBuilder.staticPropArray(scale, scale, 100.0)
                )
            )
        }

        // --- Text layers with masks ---
        val textBaseX = when {
            isRight -> barX - textMargin
            isCenter -> barX + barWidth / 2 + textBlockW / 2
            else -> barX + barWidth / 2 + textMargin
        }

        val nameY = baseY - lineSpacingPx / 2 - nameSizePx * 0.1
        val infoY = baseY + lineSpacingPx / 2 + infoSizePx * 0.9

        // Determine text slide direction and justify
        val nameSlideDir: Double
        val infoSlideDir: Double
        val justify: Int
        when {
            isRight -> {
                nameSlideDir = 1.0; infoSlideDir = 1.0; justify = 1
            }
            isCenter -> {
                nameSlideDir = -1.0; infoSlideDir = 1.0; justify = 2
            }
            else -> {
                nameSlideDir = -1.0; infoSlideDir = -1.0; justify = 0
            }
        }

        val nameSlideOffset = (nameM.width + 30) * nameSlideDir
        val infoSlideOffset = (infoM.width + 30) * infoSlideDir

        val namePctStart = if (isCenter) 50.0 else 45.0
        val infoPctStart = 50.0

        // Name mask + text layer
        if (!cfg.hideName) {
            val nameMaskOffsetX = when {
                isRight -> -nameM.width / 2.0
                isCenter -> 0.0
                else -> nameM.width / 2.0
            }
            val nameMaskShapes = buildJsonArray {
                add(makeGroup(listOf(
                    makeRect(nameM.width + 20.0, nameSizePx * 1.5, 0.0),
                    makeFill(listOf(1.0, 1.0, 1.0))
                )))
            }
            builder.addShapeLayer(
                "Name Mask", nameMaskShapes,
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(textBaseX + nameMaskOffsetX, nameY - nameSizePx * 0.15, 0.0)
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.nameWeight)
            val namePosKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(textBaseX + nameSlideOffset, nameY, 0.0)),
                    KeyframeInput(namePctStart, jsonArrayOf(textBaseX + nameSlideOffset, nameY, 0.0)),
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

        // Info mask + text layer
        if (!cfg.hideInfo) {
            val infoMaskOffsetX = when {
                isRight -> -infoM.width / 2.0
                isCenter -> 0.0
                else -> infoM.width / 2.0
            }
            val infoMaskShapes = buildJsonArray {
                add(makeGroup(listOf(
                    makeRect(infoM.width + 20.0, infoSizePx * 1.5, 0.0),
                    makeFill(listOf(1.0, 1.0, 1.0))
                )))
            }
            builder.addShapeLayer(
                "Info Mask", infoMaskShapes,
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(textBaseX + infoMaskOffsetX, infoY - infoSizePx * 0.15, 0.0)
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.infoWeight)
            val infoPosKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(textBaseX + infoSlideOffset, infoY, 0.0)),
                    KeyframeInput(infoPctStart, jsonArrayOf(textBaseX + infoSlideOffset, infoY, 0.0)),
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

        // --- Accent bar ---
        val slashOffset = emToPx(if (isRight) -6.0 else 10.0, baseSize)
        val slashPosKFs = buildKeyframes(
            listOf(
                KeyframeInput(0.0, jsonArrayOf(barX + slashOffset, baseY, 0.0)),
                KeyframeInput(23.0, jsonArrayOf(barX + slashOffset, baseY, 0.0)),
                KeyframeInput(75.0, jsonArrayOf(barX, baseY, 0.0)),
                KeyframeInput(100.0, jsonArrayOf(barX, baseY, 0.0))
            ), inF, holdF, outF, Easing.DEFAULT
        )

        val slashOpKFs = buildKeyframes(
            listOf(
                KeyframeInput(0.0, jsonArrayOf(0.0)),
                KeyframeInput(23.0, jsonArrayOf(0.0)),
                KeyframeInput(50.0, jsonArrayOf(100.0)),
                KeyframeInput(100.0, jsonArrayOf(100.0))
            ), inF, holdF, outF, Easing.DEFAULT
        )

        val barShapes = buildJsonArray {
            add(makeGroup(listOf(
                makeRect(barWidth, barHeight, 0.0),
                makeFill(accentLottie, cfg.accentColorAlpha.toDouble())
            )))
        }

        builder.addShapeLayer(
            "Accent Bar", barShapes,
            LottieBuilder.defaultTransform(
                opacity = LottieBuilder.animatedProp(slashOpKFs),
                position = LottieBuilder.animatedProp(slashPosKFs)
            )
        )

        // --- Background ---
        if (cfg.bgEnabled) {
            val bgOverhang = emToPx(1.0, baseSize)
            val bgX: Double
            val anchorX: Double
            when {
                isRight -> {
                    bgX = baseX + totalContentW / 2 + bgOverhang
                    anchorX = bgW / 2
                }
                isCenter -> {
                    bgX = baseX
                    anchorX = 0.0
                }
                else -> {
                    bgX = baseX - totalContentW / 2 - bgOverhang
                    anchorX = -bgW / 2
                }
            }

            val bgSizeKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, bgH)),
                    KeyframeInput(30.0, jsonArrayOf(0.0, bgH)),
                    KeyframeInput(100.0, jsonArrayOf(bgW, bgH))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            val bgShapeItems = mutableListOf(
                makeAnimatedRect(bgSizeKFs, cornerPx * 1.1),
                makeFill(bgLottie, cfg.bgColorAlpha.toDouble())
            )
            if (borderPx > 0) {
                val borderWidthKFs = buildKeyframes(
                    listOf(
                        KeyframeInput(0.0, jsonArrayOf(0.0)),
                        KeyframeInput(30.0, jsonArrayOf(0.0)),
                        KeyframeInput(100.0, jsonArrayOf(borderPx))
                    ), inF, holdF, outF, Easing.DEFAULT
                )
                bgShapeItems.add(makeAnimatedStroke(borderLottie, borderWidthKFs, cfg.borderColorAlpha.toDouble()))
            }

            builder.addShapeLayer(
                "Background", buildJsonArray { add(makeGroup(bgShapeItems)) },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(bgX, baseY, 0.0),
                    anchor = LottieBuilder.staticPropArray(anchorX, 0.0, 0.0)
                )
            )
        }
    }
}
