package org.churchpresenter.lottiegen.lottie.styles

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import org.churchpresenter.lottiegen.lottie.Easing
import org.churchpresenter.lottiegen.lottie.KeyframeInput
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.buildKeyframes
import org.churchpresenter.lottiegen.lottie.emToPx
import org.churchpresenter.lottiegen.lottie.hexToLottie
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.lottie.makeFill
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makePath
import org.churchpresenter.lottiegen.lottie.makeRect
import org.churchpresenter.lottiegen.lottie.makeStroke
import org.churchpresenter.lottiegen.lottie.makeTextData
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max
import kotlin.math.roundToInt

class Style11NewsTicker : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val inF = builder.inFrames
        val holdF = builder.holdFrames
        val outF = builder.outFrames

        val baseSize = cfg.baseSize.toDouble()
        val nameSizePx = emToPx(cfg.nameSize.toDouble(), baseSize)
        val infoSizePx = emToPx(cfg.infoSize.toDouble(), baseSize)

        val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
        val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
        val cornerPx = emToPx(cfg.corners.toDouble(), baseSize)
        val borderPx = cfg.borderThickness * baseSize * 0.1

        val accentLottie = hexToLottie(cfg.accentColor)
        val bgLottie = hexToLottie(cfg.bgColor)
        val borderLottie = hexToLottie(cfg.borderColor)
        val nameCLottie = hexToLottie(cfg.nameColor)
        val infoCLottie = hexToLottie(cfg.infoColor)

        val isRight = cfg.align == "right"
        val isCenter = cfg.align == "center"

        val canvasW = cfg.canvasW.toDouble()
        val canvasH = cfg.canvasH.toDouble()

        // Band dimensions
        val bandH = emToPx(3.2, baseSize)
        val tickerH = emToPx(1.5, baseSize)
        val bandGap = emToPx(0.12, baseSize)
        val slantW = emToPx(2.4, baseSize)
        val badgeFrac = if (isCenter) 0.0 else 0.28
        val halfBH = bandH / 2

        val bandW = canvasW - marginHPx * 2
        val halfBW = bandW / 2
        val bandCX = marginHPx + halfBW

        val tickerCY = canvasH - marginVPx - tickerH / 2
        val bandCY = tickerCY - tickerH / 2 - bandGap - halfBH

        // Slide-up animation (entire band enters from below)
        val slideFromY = canvasH + bandH + tickerH + 60

        fun makeBandKFs(delayPct: Double, cx: Double, finalY: Double): JsonArray {
            return buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(cx, slideFromY, 0.0)),
                    KeyframeInput(delayPct, jsonArrayOf(cx, slideFromY, 0.0)),
                    KeyframeInput(delayPct + 42, jsonArrayOf(cx, finalY, 0.0)),
                    KeyframeInput(100.0, jsonArrayOf(cx, finalY, 0.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )
        }

        val bandPosKFs = makeBandKFs(18.0, bandCX, bandCY)
        val tickerPosKFs = makeBandKFs(26.0, bandCX, tickerCY)

        // Badge parallelogram vertices (in local space centred on bandCX, bandCY)
        val badgeVerts: List<List<Double>>? = if (!isCenter) {
            if (isRight) {
                val bx = -halfBW + bandW * badgeFrac
                listOf(
                    listOf(-halfBW, -halfBH),
                    listOf(bx, -halfBH),
                    listOf(bx - slantW, halfBH),
                    listOf(-halfBW, halfBH)
                )
            } else {
                val bx = halfBW - bandW * badgeFrac
                listOf(
                    listOf(bx + slantW, -halfBH),
                    listOf(halfBW, -halfBH),
                    listOf(halfBW, halfBH),
                    listOf(bx, halfBH)
                )
            }
        } else null

        // Text horizontal positions (world space)
        val namePadX = emToPx(1.2, baseSize)
        val nameTextX: Double
        val justify: Int
        val nameAreaW: Double
        if (isCenter) {
            nameTextX = canvasW / 2
            justify = 2
            nameAreaW = bandW
        } else if (isRight) {
            nameTextX = canvasW - marginHPx - bandW * badgeFrac - slantW - namePadX
            justify = 1
            nameAreaW = bandW * (1 - badgeFrac) - slantW
        } else {
            nameTextX = marginHPx + namePadX
            justify = 0
            nameAreaW = bandW * (1 - badgeFrac) - slantW
        }
        val nameTextY = bandCY + nameSizePx * 0.35
        val infoTextX = if (isRight) canvasW - marginHPx - namePadX else marginHPx + namePadX
        val infoTextY = tickerCY + infoSizePx * 0.35
        val infoJustify = if (isRight) 1 else if (isCenter) 2 else 0

        // ============= LAYERS (top-to-bottom render order) =============

        // --- Name text (clipped to headline area, slides from alignment edge) ---
        if (!cfg.hideName) {
            val maskW = nameAreaW - namePadX * 0.4
            val maskCX = if (isRight) {
                canvasW - marginHPx - bandW * badgeFrac - slantW - maskW / 2
            } else if (isCenter) {
                canvasW / 2
            } else {
                marginHPx + maskW / 2
            }

            builder.addShapeLayer(
                "Name Mask",
                buildJsonArray {
                    add(makeGroup(listOf(makeRect(maskW, bandH * 0.9, 0.0), makeFill(listOf(1.0, 1.0, 1.0)))))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(makeBandKFs(18.0, maskCX, bandCY))
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.nameWeight)
            val nameSlide = if (isRight) maskW * 0.35 else -maskW * 0.35
            val namePosKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(nameTextX + nameSlide, nameTextY, 0.0)),
                    KeyframeInput(42.0, jsonArrayOf(nameTextX + nameSlide, nameTextY, 0.0)),
                    KeyframeInput(78.0, jsonArrayOf(nameTextX, nameTextY, 0.0)),
                    KeyframeInput(100.0, jsonArrayOf(nameTextX, nameTextY, 0.0))
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

        // --- Info text (clipped to ticker, slides from opposite edge) ---
        if (!cfg.hideInfo) {
            val infoMaskW = bandW - emToPx(0.4, baseSize)
            builder.addShapeLayer(
                "Info Mask",
                buildJsonArray {
                    add(makeGroup(listOf(makeRect(infoMaskW, tickerH * 0.88, 0.0), makeFill(listOf(1.0, 1.0, 1.0)))))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(makeBandKFs(26.0, bandCX, tickerCY))
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.infoWeight)
            val infoSlide = if (isRight) -infoMaskW * 0.4 else infoMaskW * 0.4
            val infoPosKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(infoTextX + infoSlide, infoTextY, 0.0)),
                    KeyframeInput(50.0, jsonArrayOf(infoTextX + infoSlide, infoTextY, 0.0)),
                    KeyframeInput(86.0, jsonArrayOf(infoTextX, infoTextY, 0.0)),
                    KeyframeInput(100.0, jsonArrayOf(infoTextX, infoTextY, 0.0))
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
                    infoJustify,
                ),
                LottieBuilder.defaultTransform(
                    opacity = LottieBuilder.staticProp(cfg.infoColorAlpha),
                    position = LottieBuilder.animatedProp(infoPosKFs)
                ),
                tt = 1
            )
        }

        // --- Logo inside badge (optional) ---
        val hasLogo = cfg.logoEnabled && cfg.logoData != null
        val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)
        if (hasLogo && !isCenter) {
            val _logoScale = (logoSizePx / max(cfg.logoW, cfg.logoH).toDouble()) * 100
            val logoCX = if (isRight) {
                marginHPx + bandW * badgeFrac / 2
            } else {
                canvasW - marginHPx - bandW * badgeFrac / 2
            }
            builder.addImageAsset("logo", cfg.logoData, cfg.logoW, cfg.logoH)
            builder.addImageLayer(
                "Logo", "logo",
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(makeBandKFs(18.0, logoCX, bandCY)),
                    anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
                    scale = LottieBuilder.staticPropArray(_logoScale, _logoScale, 100.0)
                )
            )
        }

        // --- Decorative diagonal stripes on badge (3 thin parallelograms) ---
        if (badgeVerts != null && cfg.bgEnabled) {
            val sW = emToPx(0.28, baseSize)
            val sH = bandH * 1.12
            val sLean = sH * 0.3
            val sAlpha = (cfg.accentColorAlpha * 0.38).roundToInt().toDouble()
            val stripeCX = if (isRight) {
                -halfBW + bandW * badgeFrac * 0.5
            } else {
                halfBW - bandW * badgeFrac * 0.5
            }
            val sSpacing = emToPx(0.9, baseSize)

            val stripeShapes = buildJsonArray {
                for (i in listOf(-1, 0, 1)) {
                    val sx = stripeCX + i * sSpacing
                    val lean = if (isRight) -sLean else sLean
                    add(makeGroup(listOf(
                        makePath(listOf(
                            listOf(sx - sW / 2 + lean * 0.5, -sH / 2),
                            listOf(sx + sW / 2 + lean * 0.5, -sH / 2),
                            listOf(sx + sW / 2 - lean * 0.5, sH / 2),
                            listOf(sx - sW / 2 - lean * 0.5, sH / 2)
                        )),
                        makeFill(accentLottie, sAlpha)
                    )))
                }
            }

            builder.addShapeLayer(
                "Stripes", stripeShapes,
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(bandPosKFs)
                )
            )

            // Badge background (parallelogram in bg color)
            builder.addShapeLayer(
                "Badge BG",
                buildJsonArray {
                    add(makeGroup(listOf(makePath(badgeVerts), makeFill(bgLottie, cfg.bgColorAlpha.toDouble()))))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(bandPosKFs)
                )
            )
        }

        // --- Main band (full-width accent-color rect) ---
        if (cfg.bgEnabled) {
            val bandItems = mutableListOf(
                makeRect(bandW, bandH, cornerPx * 0.3),
                makeFill(accentLottie, cfg.accentColorAlpha.toDouble())
            )
            makeStroke(borderLottie, borderPx, cfg.borderColorAlpha.toDouble())?.let { bandItems.add(it) }
            builder.addShapeLayer(
                "Main Band",
                buildJsonArray { add(makeGroup(bandItems)) },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(bandPosKFs)
                )
            )
        }

        // --- Ticker bar (slightly darker accent, full width) ---
        if (cfg.bgEnabled) {
            val tickerAlpha = max(10.0, (cfg.accentColorAlpha * 0.72).roundToInt().toDouble())
            val tickerItems = mutableListOf(
                makeRect(bandW, tickerH, cornerPx * 0.2),
                makeFill(accentLottie, tickerAlpha)
            )
            makeStroke(borderLottie, borderPx * 0.7, cfg.borderColorAlpha.toDouble())?.let { tickerItems.add(it) }
            builder.addShapeLayer(
                "Ticker Bar",
                buildJsonArray { add(makeGroup(tickerItems)) },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(tickerPosKFs)
                )
            )
        }
    }
}
