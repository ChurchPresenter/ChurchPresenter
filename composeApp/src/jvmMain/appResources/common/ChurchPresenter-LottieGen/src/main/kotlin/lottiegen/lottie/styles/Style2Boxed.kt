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
import lottiegen.lottie.makeAnimatedStroke
import lottiegen.lottie.makeFill
import lottiegen.lottie.makeGroup
import lottiegen.lottie.makeRect
import lottiegen.lottie.makeStroke
import lottiegen.lottie.makeTextData
import lottiegen.lottie.remToPx
import lottiegen.model.LottieGenConfig
import kotlin.math.max

class Style2Boxed : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val inF = builder.inFrames
        val holdF = builder.holdFrames
        val outF = builder.outFrames

        val baseSize = cfg.baseSize.toDouble()
        val nameSizePx = emToPx(cfg.nameSize.toDouble(), baseSize)
        val infoSizePx = emToPx(cfg.infoSize.toDouble(), baseSize)
        val nameM = TextMeasurer.measure(cfg.nameText, cfg.fontFamily, nameSizePx.toFloat(), cfg.nameWeight, cfg.nameTransform)
        val infoM = TextMeasurer.measure(cfg.infoText, cfg.fontFamily, infoSizePx.toFloat(), cfg.infoWeight, cfg.infoTransform)

        val paddingX = emToPx(1.3, baseSize)
        val paddingY = emToPx(0.5, baseSize)
        val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
        val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
        val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
        val cornerPx = emToPx(cfg.corners.toDouble(), baseSize)
        val borderPx = cfg.borderThickness * baseSize * 0.1
        val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)
        val logoMargin = emToPx(0.2, baseSize)

        val accentLottie = hexToLottie(cfg.accentColor)
        val bgLottie = hexToLottie(cfg.bgColor)
        val borderLottie = hexToLottie(cfg.borderColor)
        val nameCLottie = hexToLottie(cfg.nameColor)
        val infoCLottie = hexToLottie(cfg.infoColor)

        val canvasW = cfg.canvasW.toDouble()
        val canvasH = cfg.canvasH.toDouble()

        val isRight = cfg.align == "right"
        val isCenter = cfg.align == "center"

        val nameBoxW = nameM.width + paddingX * 2
        val nameBoxH = nameSizePx + paddingY * 2
        val infoBoxW = infoM.width + paddingX * 2
        val infoBoxH = infoSizePx + paddingY * 2

        val totalH = nameBoxH + lineSpacingPx + infoBoxH
        var baseX: Double
        val baseY = canvasH - marginVPx - totalH / 2

        baseX = when {
            isRight -> canvasW - marginHPx
            isCenter -> canvasW / 2
            else -> marginHPx
        }

        // Logo — calculate position and shift baseX, but add layers later
        var _logoX = 0.0
        var _logoBgW = 0.0
        var _logoBgH = 0.0
        var _logoSlideOffset = 0.0
        var _logoScale = 0.0
        if (cfg.logoEnabled && cfg.logoData != null) {
            _logoBgW = logoSizePx + emToPx(1.2, baseSize)
            _logoBgH = logoSizePx + emToPx(0.8, baseSize)
            val logoGap = lineSpacingPx
            val maxBoxW = max(nameBoxW, infoBoxW)
            if (isRight) {
                _logoX = baseX - _logoBgW / 2 - logoGap
                baseX -= _logoBgW + logoGap * 2
            } else if (isCenter) {
                _logoX = baseX - maxBoxW / 2 - logoGap - _logoBgW / 2
            } else {
                _logoX = baseX + _logoBgW / 2 + logoGap
                baseX += _logoBgW + logoGap * 2
            }
            _logoSlideOffset = totalH + _logoBgH
            _logoScale = (logoSizePx / max(cfg.logoW, cfg.logoH).toDouble()) * 100
        }

        // Background/mask X positions (center of each box)
        val nameX = when {
            isRight -> baseX - nameBoxW / 2
            isCenter -> baseX
            else -> baseX + nameBoxW / 2
        }

        val infoX = when {
            isRight -> baseX - infoBoxW / 2
            isCenter -> baseX
            else -> baseX + infoBoxW / 2
        }

        // Text X positions
        val nameTextX: Double
        val infoTextX: Double
        when {
            isRight -> {
                nameTextX = baseX - paddingX
                infoTextX = baseX - paddingX
            }
            isCenter -> {
                nameTextX = nameX
                infoTextX = infoX
            }
            else -> {
                nameTextX = baseX + paddingX
                infoTextX = baseX + paddingX
            }
        }

        // Background/mask Y positions
        val nameBgY = baseY - lineSpacingPx / 2 - infoBoxH / 2
        val infoBgY = baseY + nameBoxH / 2 + lineSpacingPx / 2

        // Text Y positions
        val nameTextY = nameBgY + nameSizePx * 0.35
        val infoTextY = infoBgY + infoSizePx * 0.35

        // --- Logo image (added first = renders on top) ---
        if (cfg.logoEnabled && cfg.logoData != null) {
            builder.addImageAsset("logo_0", cfg.logoData, cfg.logoW, cfg.logoH)
            val logoPosKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(_logoX, baseY + _logoSlideOffset, 0.0)),
                    KeyframeInput(50.0, jsonArrayOf(_logoX, baseY + _logoSlideOffset, 0.0)),
                    KeyframeInput(100.0, jsonArrayOf(_logoX, baseY, 0.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )
            val logoOpKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0)),
                    KeyframeInput(50.0, jsonArrayOf(0.0)),
                    KeyframeInput(100.0, jsonArrayOf(100.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )
            builder.addImageLayer(
                "Logo", "logo_0",
                LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.animatedProp(logoOpKFs),
                    position = LottieBuilder.animatedProp(logoPosKFs),
                    anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
                    scale = LottieBuilder.staticPropArray(_logoScale, _logoScale, 100.0)
                )
            )
        }

        // --- Text layers with masks ---
        val justify = when {
            isRight -> 1
            isCenter -> 2
            else -> 0
        }
        val namePctStart = if (isCenter) 50.0 else 45.0

        val nameSlideDir = if (isRight) 1.0 else -1.0
        val infoSlideDir = if (isCenter) 1.0 else nameSlideDir
        val nameSlideOffset = nameBoxW * 1.02 * nameSlideDir
        val infoSlideOffset = infoBoxW * 1.02 * infoSlideDir

        // Name mask + text
        if (!cfg.hideName) {
            builder.addShapeLayer(
                "Name Mask",
                buildJsonArray {
                    add(makeGroup(listOf(
                        makeRect(nameBoxW, nameBoxH, 0.0),
                        makeFill(listOf(1.0, 1.0, 1.0))
                    )))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(nameX, nameBgY, 0.0)
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.nameWeight)
            val namePosKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(nameTextX + nameSlideOffset, nameTextY, 0.0)),
                    KeyframeInput(namePctStart, jsonArrayOf(nameTextX + nameSlideOffset, nameTextY, 0.0)),
                    KeyframeInput(100.0, jsonArrayOf(nameTextX, nameTextY, 0.0))
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
            builder.addShapeLayer(
                "Info Mask",
                buildJsonArray {
                    add(makeGroup(listOf(
                        makeRect(infoBoxW, infoBoxH, 0.0),
                        makeFill(listOf(1.0, 1.0, 1.0))
                    )))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(infoX, infoBgY, 0.0)
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.infoWeight)
            val infoPosKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(infoTextX + infoSlideOffset, infoTextY, 0.0)),
                    KeyframeInput(50.0, jsonArrayOf(infoTextX + infoSlideOffset, infoTextY, 0.0)),
                    KeyframeInput(100.0, jsonArrayOf(infoTextX, infoTextY, 0.0))
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

        // --- Name background rect ---
        if (!cfg.hideName && cfg.bgEnabled) {
            val nameBgSizeKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(nameBoxW, 0.0)),
                    KeyframeInput(namePctStart, jsonArrayOf(nameBoxW, 0.0)),
                    KeyframeInput(100.0, jsonArrayOf(nameBoxW, nameBoxH))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            val nameBorderKFs = if (borderPx > 0) buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0)),
                    KeyframeInput(namePctStart, jsonArrayOf(0.0)),
                    KeyframeInput(100.0, jsonArrayOf(borderPx))
                ), inF, holdF, outF, Easing.DEFAULT
            ) else null

            val nameRectItems = mutableListOf(
                makeAnimatedRect(nameBgSizeKFs, cornerPx * 1.1),
                makeFill(accentLottie, cfg.accentColorAlpha.toDouble())
            )
            if (nameBorderKFs != null) {
                nameRectItems.add(makeAnimatedStroke(borderLottie, nameBorderKFs, cfg.borderColorAlpha.toDouble()))
            }

            builder.addShapeLayer(
                "Name BG",
                buildJsonArray { add(makeGroup(nameRectItems)) },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(nameX, nameBgY, 0.0)
                )
            )
        }

        // --- Info background rect ---
        if (!cfg.hideInfo && cfg.bgEnabled) {
            val infoBgSizeKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(infoBoxW, 0.0)),
                    KeyframeInput(50.0, jsonArrayOf(infoBoxW, 0.0)),
                    KeyframeInput(100.0, jsonArrayOf(infoBoxW, infoBoxH))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            val infoBorderKFs = if (borderPx > 0) buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0)),
                    KeyframeInput(50.0, jsonArrayOf(0.0)),
                    KeyframeInput(100.0, jsonArrayOf(borderPx))
                ), inF, holdF, outF, Easing.DEFAULT
            ) else null

            val infoRectItems = mutableListOf(
                makeAnimatedRect(infoBgSizeKFs, cornerPx * 1.1),
                makeFill(bgLottie, cfg.bgColorAlpha.toDouble())
            )
            if (infoBorderKFs != null) {
                infoRectItems.add(makeAnimatedStroke(borderLottie, infoBorderKFs, cfg.borderColorAlpha.toDouble()))
            }

            builder.addShapeLayer(
                "Info BG",
                buildJsonArray { add(makeGroup(infoRectItems)) },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.staticPropArray(infoX, infoBgY, 0.0)
                )
            )
        }

        // --- Logo background rect ---
        if (cfg.logoEnabled && cfg.logoData != null) {
            val logoBgScaleKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(0.0, 0.0, 100.0)),
                    KeyframeInput(45.0, jsonArrayOf(100.0, 100.0, 100.0)),
                    KeyframeInput(100.0, jsonArrayOf(100.0, 100.0, 100.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )

            val logoBgItems = mutableListOf(
                makeRect(_logoBgW, _logoBgH, cornerPx),
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
                    position = LottieBuilder.staticPropArray(_logoX, baseY, 0.0),
                    scale = LottieBuilder.animatedProp(logoBgScaleKFs)
                )
            )
        }
    }
}
