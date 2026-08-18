package lottiegen.lottie.styles

import kotlinx.serialization.json.buildJsonArray
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
import lottiegen.lottie.makeRect
import lottiegen.lottie.makeTextData
import lottiegen.lottie.remToPx
import lottiegen.model.LottieGenConfig
import kotlin.math.max

class Style10DoubleLine : StyleGenerator {
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
        val lineGap = emToPx(0.4, baseSize)
        val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
        val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
        val linePx = max(2.0, cfg.borderThickness * baseSize * 0.1)

        val accentLottie = hexToLottie(cfg.accentColor)
        val nameCLottie = hexToLottie(cfg.nameColor)
        val infoCLottie = hexToLottie(cfg.infoColor)

        val isRight = cfg.align == "right"
        val isCenter = cfg.align == "center"

        val canvasW = cfg.canvasW.toDouble()
        val canvasH = cfg.canvasH.toDouble()

        // Logo space
        val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)
        val logoMargin = emToPx(1.0, baseSize)
        val hasLogo = cfg.logoEnabled && cfg.logoData != null
        val logoSpace = if (hasLogo) logoSizePx + logoMargin else 0.0

        // Line width = extends well beyond text
        val nameContentW = if (cfg.hideName) 0.0 else nameM.width + paddingX * 2
        val infoContentW = if (cfg.hideInfo) 0.0 else infoM.width + paddingX * 2
        val textW = max(max(nameContentW, infoContentW), emToPx(4.0, baseSize))
        val lineW = textW + emToPx(0.5, baseSize)

        // Vertical positioning: top line, name, bottom line, info
        val nameBlockH = if (cfg.hideName) 0.0 else nameSizePx
        val infoBlockH = if (cfg.hideInfo) 0.0 else infoSizePx
        val gapName = if (cfg.hideName) 0.0 else lineGap
        val gapInfo = if (cfg.hideInfo) 0.0 else lineGap
        val totalH = linePx + gapName + nameBlockH + gapName + linePx + gapInfo + infoBlockH
        val blockTopY = canvasH - marginVPx - totalH

        val topLineCY = blockTopY + linePx / 2
        val nameCY = blockTopY + linePx + gapName + nameBlockH / 2
        val bottomLineCY = blockTopY + linePx + gapName + nameBlockH + gapName + linePx / 2
        val infoCY = bottomLineCY + linePx / 2 + gapInfo + infoBlockH / 2

        // Horizontal positioning
        val linePosX: Double
        val nameTextX: Double
        val infoTextX: Double
        val justify: Int
        val lineAnchorX: Double

        if (isCenter) {
            nameTextX = canvasW / 2
            infoTextX = canvasW / 2
            justify = 2
            linePosX = canvasW / 2
            lineAnchorX = 0.0
        } else if (isRight) {
            val textEdge = canvasW - marginHPx - logoSpace
            nameTextX = textEdge
            infoTextX = textEdge
            justify = 1
            linePosX = textEdge
            lineAnchorX = lineW / 2
        } else {
            val textEdge = marginHPx + logoSpace
            nameTextX = textEdge
            infoTextX = textEdge
            justify = 0
            linePosX = textEdge
            lineAnchorX = -lineW / 2
        }

        val nameTextY = nameCY + nameSizePx * 0.35
        val infoTextY = infoCY + infoSizePx * 0.35

        // --- Animation timing ---
        val topLineInKFs = buildKeyframes(
            listOf(
                KeyframeInput(0.0, jsonArrayOf(0.0, linePx)),
                KeyframeInput(40.0, jsonArrayOf(lineW, linePx)),
                KeyframeInput(100.0, jsonArrayOf(lineW, linePx))
            ), inF, holdF, outF, Easing.DEFAULT
        )

        val bottomLineInKFs = buildKeyframes(
            listOf(
                KeyframeInput(0.0, jsonArrayOf(0.0, linePx)),
                KeyframeInput(15.0, jsonArrayOf(0.0, linePx)),
                KeyframeInput(45.0, jsonArrayOf(lineW, linePx)),
                KeyframeInput(100.0, jsonArrayOf(lineW, linePx))
            ), inF, holdF, outF, Easing.DEFAULT
        )

        // Name slides up from bottom line to its position
        val nameSlideKFs = buildKeyframes(
            listOf(
                KeyframeInput(0.0, jsonArrayOf(nameTextX, bottomLineCY + nameSizePx * 1.2, 0.0)),
                KeyframeInput(25.0, jsonArrayOf(nameTextX, bottomLineCY + nameSizePx * 1.2, 0.0)),
                KeyframeInput(55.0, jsonArrayOf(nameTextX, nameTextY, 0.0)),
                KeyframeInput(100.0, jsonArrayOf(nameTextX, nameTextY, 0.0))
            ), inF, holdF, outF, Easing.DEFAULT
        )

        // Name mask
        val nameMaskH = nameBlockH + gapName * 2
        val nameMaskCY = nameCY

        // ============= LAYERS (top to bottom render order) =============

        // --- Name mask + text (slides up from behind bottom line) ---
        if (!cfg.hideName) {
            builder.addShapeLayer(
                "Name Mask",
                buildJsonArray {
                    add(makeGroup(listOf(makeRect(lineW, nameMaskH, 0.0), makeFill(listOf(1.0, 1.0, 1.0)))))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(linePosX, nameMaskCY, 0.0),
                    anchor = LottieBuilder.staticPropArray(lineAnchorX, 0.0, 0.0)
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.nameWeight)
            builder.addTextLayer(
                "Name",
                makeTextData(cfg.nameText, cfg.fontFamily, nameSizePx, cfg.nameWeight, nameCLottie, cfg.nameTransform, justify),
                LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.nameColorAlpha),
                    position = LottieBuilder.animatedProp(nameSlideKFs)
                ),
                tt = 1
            )
        }

        // --- Info mask + text (slides down from behind bottom line) ---
        if (!cfg.hideInfo) {
            val infoMaskH = infoBlockH + gapInfo * 2
            val infoMaskCY = infoCY

            builder.addShapeLayer(
                "Info Mask",
                buildJsonArray {
                    add(makeGroup(listOf(makeRect(lineW, infoMaskH, 0.0), makeFill(listOf(1.0, 1.0, 1.0)))))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(linePosX, infoMaskCY, 0.0),
                    anchor = LottieBuilder.staticPropArray(lineAnchorX, 0.0, 0.0)
                ),
                td = 1
            )

            val infoSlideKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(infoTextX, bottomLineCY - infoSizePx * 0.5, 0.0)),
                    KeyframeInput(30.0, jsonArrayOf(infoTextX, bottomLineCY - infoSizePx * 0.5, 0.0)),
                    KeyframeInput(60.0, jsonArrayOf(infoTextX, infoTextY, 0.0)),
                    KeyframeInput(100.0, jsonArrayOf(infoTextX, infoTextY, 0.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            builder.addFont(cfg.fontFamily, cfg.infoWeight)
            builder.addTextLayer(
                "Info",
                makeTextData(cfg.infoText, cfg.fontFamily, infoSizePx, cfg.infoWeight, infoCLottie, cfg.infoTransform, justify),
                LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.infoColorAlpha),
                    position = LottieBuilder.animatedProp(infoSlideKFs)
                ),
                tt = 1
            )
        }

        // --- Top line (expands from alignment side) ---
        builder.addShapeLayer(
            "Top Line",
            buildJsonArray {
                add(makeGroup(listOf(
                    makeAnimatedRect(topLineInKFs, 0.0),
                    makeFill(accentLottie, cfg.accentColorAlpha.toDouble())
                )))
            },
            LottieBuilder.defaultTransform(
                position = LottieBuilder.staticPropArray(linePosX, topLineCY, 0.0),
                anchor = LottieBuilder.staticPropArray(lineAnchorX, 0.0, 0.0)
            )
        )

        // --- Bottom line (expands from alignment side, slightly faster) ---
        builder.addShapeLayer(
            "Bottom Line",
            buildJsonArray {
                add(makeGroup(listOf(
                    makeAnimatedRect(bottomLineInKFs, 0.0),
                    makeFill(accentLottie, cfg.accentColorAlpha.toDouble())
                )))
            },
            LottieBuilder.defaultTransform(
                position = LottieBuilder.staticPropArray(linePosX, bottomLineCY, 0.0),
                anchor = LottieBuilder.staticPropArray(lineAnchorX, 0.0, 0.0)
            )
        )

        // --- Logo (optional) ---
        if (hasLogo) {
            val lH = logoSizePx
            val _logoScale = (lH / cfg.logoH.toDouble()) * 100

            val logoCX = if (isCenter) {
                val maxW = max(if (cfg.hideName) 0.0 else nameM.width.toDouble(), if (cfg.hideInfo) 0.0 else infoM.width.toDouble())
                canvasW / 2 - maxW / 2 - logoMargin - logoSizePx / 2
            } else if (isRight) {
                canvasW - marginHPx - logoSizePx / 2
            } else {
                marginHPx + logoSizePx / 2
            }
            val logoCY = (topLineCY + bottomLineCY) / 2

            val logoScaleKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, 0.0, 100.0)),
                    KeyframeInput(25.0, jsonArrayOf(_logoScale, _logoScale, 100.0)),
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
        }
    }
}
