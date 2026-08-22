package org.churchpresenter.lottiegen.lottie.styles

import org.churchpresenter.lottiegen.lottie.FULL_PERCENT_D
import kotlinx.serialization.json.buildJsonArray
import org.churchpresenter.lottiegen.lottie.Easing
import org.churchpresenter.lottiegen.lottie.KeyframeInput
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.TextMeasurer
import org.churchpresenter.lottiegen.lottie.buildKeyframes
import org.churchpresenter.lottiegen.lottie.emToPx
import org.churchpresenter.lottiegen.lottie.hexToLottie
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.lottie.kf
import org.churchpresenter.lottiegen.lottie.makeFill
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makePath
import org.churchpresenter.lottiegen.lottie.makeStroke
import org.churchpresenter.lottiegen.lottie.makeTextData
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max
import kotlin.math.roundToInt

/** The wipe's mask crosses the text halfway through the in and out phases. */
private const val WIPE_MIDPOINT = 0.5

/**
 * Opacity turning points, as fractions of the in and out phases: the text is up almost at once,
 * holds until just before the end, and leaves the same way.
 */
private const val FADE_IN_END = 0.08
private const val FADE_OUT_START = 0.05
private const val HOLD_START = 0.95


class Style9DiagonalWipe : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val inF = builder.inFrames
        val holdF = builder.holdFrames
        val outF = builder.outFrames
        val totalOut = inF + holdF
        val e = Easing.DEFAULT
        val L = Easing.LINEAR

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

        val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
        val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
        val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)

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

        // Vertical positioning: name above info
        val nameBlockH = if (cfg.hideName) 0.0 else nameSizePx
        val infoBlockH = if (cfg.hideInfo) 0.0 else infoSizePx
        val gap = if (!cfg.hideName && !cfg.hideInfo) lineSpacingPx else 0.0
        val totalH = nameBlockH + gap + infoBlockH
        val blockTopY = canvasH - marginVPx - totalH
        val nameCY = blockTopY + nameBlockH / 2
        val infoCY = blockTopY + nameBlockH + gap + infoBlockH / 2

        // Horizontal positioning
        val nameTextX: Double
        val infoTextX: Double
        val justify: Int
        if (isCenter) {
            nameTextX = canvasW / 2
            infoTextX = canvasW / 2
            justify = 2
        } else if (isRight) {
            nameTextX = canvasW - marginHPx - logoSpace
            infoTextX = canvasW - marginHPx - logoSpace
            justify = 1
        } else {
            nameTextX = marginHPx + logoSpace
            infoTextX = marginHPx + logoSpace
            justify = 0
        }

        val nameTextY = nameCY + nameSizePx * 0.35
        val infoTextY = infoCY + infoSizePx * 0.35

        // Diagonal wipe parameters
        val slant = emToPx(1.0, baseSize)
        val padding = emToPx(1.0, baseSize)
        val maskH = totalH + padding * 2
        val lineThickness = max(2.0, baseSize * 0.08)

        // Text bounds
        val maxTextW = max(
            if (cfg.hideName) 0.0 else nameM.width.toDouble(),
            if (cfg.hideInfo) 0.0 else infoM.width.toDouble(),
        )
        val textLeftX: Double
        val textRightX: Double
        if (isCenter) {
            textLeftX = canvasW / 2 - maxTextW / 2 - logoSpace
            textRightX = canvasW / 2 + maxTextW / 2
        } else if (isRight) {
            textRightX = canvasW - marginHPx
            textLeftX = textRightX - maxTextW - logoSpace
        } else {
            textLeftX = marginHPx
            textRightX = textLeftX + maxTextW + logoSpace
        }
        val wipeMargin = padding
        val wipeLeft = textLeftX - wipeMargin - slant
        val wipeRight = textRightX + wipeMargin + slant

        // Mask is oversized so it fully covers text at any wipe position
        val maskW = (wipeRight - wipeLeft) + slant + padding * 2
        val halfMW = maskW / 2
        val halfMH = maskH / 2

        // For right alignment: mirror the slant and wipe direction

        // Mask polygon vertices
        val maskVertices: List<List<Double>> = if (isRight) listOf(
            listOf(-halfMW, -halfMH),
            listOf(halfMW, -halfMH),
            listOf(halfMW, halfMH),
            listOf(-halfMW + slant, halfMH)
        ) else listOf(
            listOf(-halfMW, -halfMH),
            listOf(halfMW, -halfMH),
            listOf(halfMW - slant, halfMH),
            listOf(-halfMW, halfMH)
        )

        // Diagonal line vertices
        val lineVertices: List<List<Double>> = if (isRight) listOf(
            listOf(0.0, -halfMH),
            listOf(slant, halfMH)
        ) else listOf(
            listOf(0.0, -halfMH),
            listOf(-slant, halfMH)
        )

        val maskCY = blockTopY + totalH / 2

        // offStart = mask hidden (before wipe), offEnd = mask fully covering text
        val offStart: Double
        val offEnd: Double
        val lineEdgeOffset: Double
        if (isRight) {
            offStart = wipeRight + halfMW + slant
            offEnd = wipeLeft + halfMW - slant * 2
            lineEdgeOffset = -halfMW
        } else {
            offStart = wipeLeft - halfMW - slant
            offEnd = wipeRight - halfMW + slant * 2
            lineEdgeOffset = halfMW
        }

        val fadeIn = 0.08

        // Custom keyframe builders
        fun buildWipeMaskKFs() = buildJsonArray {
            add(kf(0, jsonArrayOf(offStart, maskCY, 0.0), e))
            add(kf((inF * fadeIn).roundToInt(), jsonArrayOf(offStart, maskCY, 0.0), L))
            add(kf((inF * WIPE_MIDPOINT).roundToInt(), jsonArrayOf(offEnd, maskCY, 0.0), L))
            add(kf(inF, jsonArrayOf(offStart, maskCY, 0.0), e))
            add(kf(totalOut, jsonArrayOf(offStart, maskCY, 0.0), e))
            add(kf((totalOut + outF * WIPE_MIDPOINT).roundToInt(), jsonArrayOf(offEnd, maskCY, 0.0), L))
            add(kf((totalOut + outF * (1 - fadeIn)).roundToInt(), jsonArrayOf(offStart, maskCY, 0.0), e))
            add(kf(totalOut + outF, jsonArrayOf(offStart, maskCY, 0.0)))
        }

        fun buildRevealMaskKFs() = buildJsonArray {
            add(kf(0, jsonArrayOf(offStart, maskCY, 0.0), e))
            add(kf((inF * fadeIn).roundToInt(), jsonArrayOf(offStart, maskCY, 0.0), L))
            add(kf((inF * WIPE_MIDPOINT).roundToInt(), jsonArrayOf(offEnd, maskCY, 0.0), e))
            add(kf(inF, jsonArrayOf(offEnd, maskCY, 0.0), e))
            add(kf(totalOut, jsonArrayOf(offEnd, maskCY, 0.0), e))
            add(kf((totalOut + outF * WIPE_MIDPOINT).roundToInt(), jsonArrayOf(offEnd, maskCY, 0.0), e))
            add(kf((totalOut + outF * (1 - fadeIn)).roundToInt(), jsonArrayOf(offStart, maskCY, 0.0), e))
            add(kf(totalOut + outF, jsonArrayOf(offStart, maskCY, 0.0)))
        }

        fun buildLineKFs() = buildJsonArray {
            add(kf(0, jsonArrayOf(offStart + lineEdgeOffset, maskCY, 0.0), e))
            add(kf((inF * fadeIn).roundToInt(), jsonArrayOf(offStart + lineEdgeOffset, maskCY, 0.0), L))
            add(kf((inF * WIPE_MIDPOINT).roundToInt(), jsonArrayOf(offEnd + lineEdgeOffset, maskCY, 0.0), L))
            add(kf(inF, jsonArrayOf(offStart + lineEdgeOffset, maskCY, 0.0), e))
            add(kf(totalOut, jsonArrayOf(offStart + lineEdgeOffset, maskCY, 0.0), e))
            add(kf((totalOut + outF * WIPE_MIDPOINT).roundToInt(), jsonArrayOf(offEnd + lineEdgeOffset, maskCY, 0.0), L))
            add(kf((totalOut + outF * (1 - fadeIn))
                .roundToInt(), jsonArrayOf(offStart + lineEdgeOffset, maskCY, 0.0), e))
            add(kf(totalOut + outF, jsonArrayOf(offStart + lineEdgeOffset, maskCY, 0.0)))
        }

        fun buildLineOpacityKFs() = buildJsonArray {
            add(kf(0, jsonArrayOf(0.0), e))
            add(kf((inF * FADE_IN_END).roundToInt(), jsonArrayOf(FULL_PERCENT_D), e))
            add(kf((inF * HOLD_START).roundToInt(), jsonArrayOf(FULL_PERCENT_D), e))
            add(kf(inF, jsonArrayOf(0.0), e))
            add(kf(totalOut, jsonArrayOf(0.0), e))
            add(kf((totalOut + outF * FADE_OUT_START).roundToInt(), jsonArrayOf(FULL_PERCENT_D), e))
            add(kf((totalOut + outF * HOLD_START).roundToInt(), jsonArrayOf(FULL_PERCENT_D), e))
            add(kf(totalOut + outF, jsonArrayOf(0.0)))
        }

        val wipeMaskKFs = buildWipeMaskKFs()
        val revealMaskKFs = buildRevealMaskKFs()
        val lineKFs = buildLineKFs()
        val lineOpacityKFs = buildLineOpacityKFs()

        // ============= LAYERS (top to bottom render order) =============

        // --- Diagonal line (visual element) ---
        val lineShapes = buildJsonArray {
            val items = mutableListOf(
                makePath(lineVertices, false)
            )
            makeStroke(accentLottie, lineThickness, cfg.accentColorAlpha.toDouble())?.let { items.add(it) }
            add(makeGroup(items))
        }
        builder.addShapeLayer(
            "Diagonal Line", lineShapes,
            LottieBuilder.defaultTransform(
                opacity = LottieBuilder.animatedProp(lineOpacityKFs),
                position = LottieBuilder.animatedProp(lineKFs)
            )
        )

        // --- Accent name text (mask + text) ---
        if (!cfg.hideName) {
            builder.addShapeLayer(
                "Accent Name Mask",
                buildJsonArray {
                    add(makeGroup(listOf(makePath(maskVertices), makeFill(listOf(1.0, 1.0, 1.0)))))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(wipeMaskKFs)
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.nameWeight)
            builder.addTextLayer(
                "Accent Name",
                makeTextData(
                    cfg.nameText,
                    cfg.fontFamily,
                    nameSizePx,
                    cfg.nameWeight,
                    accentLottie,
                    cfg.nameTransform,
                    justify,
                ),
                LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.accentColorAlpha),
                    position = LottieBuilder.staticPropArray(nameTextX, nameTextY, 0.0)
                ),
                tt = 1
            )
        }

        // --- Accent info text (mask + text) ---
        if (!cfg.hideInfo) {
            builder.addShapeLayer(
                "Accent Info Mask",
                buildJsonArray {
                    add(makeGroup(listOf(makePath(maskVertices), makeFill(listOf(1.0, 1.0, 1.0)))))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(wipeMaskKFs)
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.infoWeight)
            builder.addTextLayer(
                "Accent Info",
                makeTextData(
                    cfg.infoText,
                    cfg.fontFamily,
                    infoSizePx,
                    cfg.infoWeight,
                    accentLottie,
                    cfg.infoTransform,
                    justify,
                ),
                LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.infoColorAlpha),
                    position = LottieBuilder.staticPropArray(infoTextX, infoTextY, 0.0)
                ),
                tt = 1
            )
        }

        // --- Normal name text (revealed by separate mask, stays visible) ---
        if (!cfg.hideName) {
            builder.addShapeLayer(
                "Name Reveal Mask",
                buildJsonArray {
                    add(makeGroup(listOf(makePath(maskVertices), makeFill(listOf(1.0, 1.0, 1.0)))))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(revealMaskKFs)
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.nameWeight)
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
                    position = LottieBuilder.staticPropArray(nameTextX, nameTextY, 0.0)
                ),
                tt = 1
            )
        }

        // --- Normal info text (revealed by separate mask, stays visible) ---
        if (!cfg.hideInfo) {
            builder.addShapeLayer(
                "Info Reveal Mask",
                buildJsonArray {
                    add(makeGroup(listOf(makePath(maskVertices), makeFill(listOf(1.0, 1.0, 1.0)))))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(revealMaskKFs)
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.infoWeight)
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
                    position = LottieBuilder.staticPropArray(infoTextX, infoTextY, 0.0)
                ),
                tt = 1
            )
        }

        // --- Logo (optional) ---
        if (hasLogo) {
            val lH = logoSizePx
            val _logoScale = (lH / cfg.logoH.toDouble()) * 100

            val logoCX = if (isCenter) {
                val maxW = max(
                    if (cfg.hideName) 0.0 else nameM.width.toDouble(),
                    if (cfg.hideInfo) 0.0 else infoM.width.toDouble(),
                )
                canvasW / 2 - maxW / 2 - logoMargin - logoSizePx / 2
            } else if (isRight) {
                canvasW - marginHPx - logoSizePx / 2
            } else {
                marginHPx + logoSizePx / 2
            }
            val logoCY = (infoCY + nameCY) / 2

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
