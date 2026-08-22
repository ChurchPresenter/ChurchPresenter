package org.churchpresenter.lottiegen.lottie.styles

import org.churchpresenter.lottiegen.lottie.FULL_PERCENT_D
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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

/** A reveal mask is filled opaque white; only its shape matters. */
private val WHITE = listOf(1.0, 1.0, 1.0)

/** Where each element's slide-in has settled, as a percentage of the animation. */
private const val SETTLE_PCT = 40
private const val BAND_START_PCT = 18.0
private const val TICKER_START_PCT = 26.0

/** The badge's slash leans by this much either side of its centre line. */
private const val HALF_LEAN = 0.5

/** The slash is drawn under the name at just over half its opacity. */
private const val SLASH_ALPHA_FACTOR = 0.55

/** The reveal masks sit just inside their bands so the wipe edge never shows. */
private const val BAND_MASK_W_FACTOR = 0.96
private const val BAND_MASK_H_FACTOR = 0.9
private const val TICKER_MASK_FACTOR = 0.88

/** The ticker's rule is drawn lighter than the configured border weight. */
private const val TICKER_BORDER_FACTOR = 0.7


class Style12NewsBadge : StyleGenerator {
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

        val bandH = emToPx(3.2, baseSize)
        val tickerH = emToPx(1.5, baseSize)
        val bandGap = emToPx(0.12, baseSize)
        val slashW = emToPx(0.32, baseSize)
        val slashGap = emToPx(0.55, baseSize)
        val halfBH = bandH / 2
        val slashH = bandH * 1.06
        val slashLean = slashH * 0.28

        val badgeFrac = if (isCenter) 0.0 else 0.22

        val bandW = canvasW - marginHPx * 2
        val halfBW = bandW / 2
        val bandCX = marginHPx + halfBW

        val tickerCY = canvasH - marginVPx - tickerH / 2
        val bandCY = tickerCY - tickerH / 2 - bandGap - halfBH

        val slideFromY = canvasH + bandH + tickerH + 60

        fun makeBandKFs(delayPct: Double, cx: Double, finalY: Double): JsonArray {
            return buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(cx, slideFromY, 0.0)),
                    KeyframeInput(delayPct, jsonArrayOf(cx, slideFromY, 0.0)),
                    KeyframeInput(delayPct + SETTLE_PCT, jsonArrayOf(cx, finalY, 0.0)),
                    KeyframeInput(FULL_PERCENT_D, jsonArrayOf(cx, finalY, 0.0))
                ), inF, holdF, outF, Easing.DEFAULT
            )
        }

        val bandPosKFs = makeBandKFs(BAND_START_PCT, bandCX, bandCY)
        val tickerPosKFs = makeBandKFs(TICKER_START_PCT, bandCX, tickerCY)

        // Badge boundary in local (band-centred) space
        val badgeVerts: List<List<Double>>?
        var badgeBoundaryX = 0.0
        if (!isCenter) {
            if (isRight) {
                badgeBoundaryX = halfBW - bandW * badgeFrac
                badgeVerts = listOf(
                    listOf(badgeBoundaryX, -halfBH),
                    listOf(halfBW, -halfBH),
                    listOf(halfBW, halfBH),
                    listOf(badgeBoundaryX - slashLean, halfBH)
                )
            } else {
                badgeBoundaryX = -halfBW + bandW * badgeFrac
                badgeVerts = listOf(
                    listOf(-halfBW, -halfBH),
                    listOf(badgeBoundaryX + slashLean, -halfBH),
                    listOf(badgeBoundaryX, halfBH),
                    listOf(-halfBW, halfBH)
                )
            }
        } else {
            badgeVerts = null
        }

        // Two slash stripes positioned at the badge inner boundary
        fun makeSlash(cx: Double): JsonObject {
            val lean = if (isRight) -slashLean else slashLean
            return makeGroup(listOf(
                makePath(listOf(
                    listOf(cx - slashW / 2 + lean * HALF_LEAN, -slashH / 2),
                    listOf(cx + slashW / 2 + lean * HALF_LEAN, -slashH / 2),
                    listOf(cx + slashW / 2 - lean * HALF_LEAN, slashH / 2),
                    listOf(cx - slashW / 2 - lean * HALF_LEAN, slashH / 2)
                )),
                makeFill(nameCLottie, (cfg.nameColorAlpha * SLASH_ALPHA_FACTOR).roundToInt().toDouble())
            ))
        }

        // Position two slashes flanking the badge boundary
        val slash1LocalX = if (isRight) {
            badgeBoundaryX + slashGap * 0.5 + slashW
        } else {
            badgeBoundaryX - slashGap * 0.5 - slashW
        }
        val slash2LocalX = if (isRight) {
            badgeBoundaryX + slashGap * 0.5 + slashW * 2.8
        } else {
            badgeBoundaryX - slashGap * 0.5 - slashW * 2.8
        }

        // Text X positions (world space)
        val namePadX = emToPx(1.0, baseSize)
        val mainLeft = if (isRight) {
            marginHPx
        } else {
            marginHPx + bandW * badgeFrac + slashLean + slashW * 4
        }
        val mainRight = if (isRight) {
            canvasW - marginHPx - bandW * badgeFrac - slashLean - slashW * 4
        } else {
            canvasW - marginHPx
        }
        val mainCX = (mainLeft + mainRight) / 2

        val nameTextX: Double
        val justify: Int
        if (isCenter) {
            nameTextX = canvasW / 2
            justify = 2
        } else {
            nameTextX = mainCX
            justify = 2 // centred in main area
        }
        val nameTextY = bandCY + nameSizePx * 0.35
        val infoTextX = if (isRight) canvasW - marginHPx - namePadX else marginHPx + namePadX
        val infoTextY = tickerCY + infoSizePx * 0.35
        val infoJustify = if (isRight) 1 else if (isCenter) 2 else 0

        // ============= LAYERS (top-to-bottom render order) =============

        // --- Name text (clipped to main area, slides in from side) ---
        if (!cfg.hideName) {
            val mainW = mainRight - mainLeft
            val maskCX = mainCX
            builder.addShapeLayer(
                "Name Mask",
                buildJsonArray {
                    add(
                        makeGroup(
                            listOf(
                                makeRect(mainW * BAND_MASK_W_FACTOR, bandH * BAND_MASK_H_FACTOR, 0.0),
                                makeFill(WHITE),
                            ),
                        ),
                    )
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(makeBandKFs(BAND_START_PCT, maskCX, bandCY))
                ),
                td = 1
            )

            builder.addFont(cfg.fontFamily, cfg.nameWeight)
            val nameSlide = if (isRight) emToPx(5.0, baseSize) else -emToPx(5.0, baseSize)
            val namePosKFs = buildKeyframes(
                listOf(
                    KeyframeInput(0.0, jsonArrayOf(nameTextX + nameSlide, nameTextY, 0.0)),
                    KeyframeInput(40.0, jsonArrayOf(nameTextX + nameSlide, nameTextY, 0.0)),
                    KeyframeInput(76.0, jsonArrayOf(nameTextX, nameTextY, 0.0)),
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

        // --- Info text (clipped to ticker, slides from opposite side) ---
        if (!cfg.hideInfo) {
            val infoMaskW = bandW - emToPx(0.4, baseSize)
            builder.addShapeLayer(
                "Info Mask",
                buildJsonArray {
                    add(
                        makeGroup(listOf(makeRect(infoMaskW, tickerH * TICKER_MASK_FACTOR, 0.0), makeFill(WHITE))),
                    )
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(makeBandKFs(TICKER_START_PCT, bandCX, tickerCY))
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

        // --- Logo inside badge area ---
        val hasLogo = cfg.logoEnabled && cfg.logoData != null
        val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)
        if (hasLogo && !isCenter) {
            val _logoScale = (logoSizePx / max(cfg.logoW, cfg.logoH).toDouble()) * 100
            val logoCX = if (isRight) {
                canvasW - marginHPx - bandW * badgeFrac / 2
            } else {
                marginHPx + bandW * badgeFrac / 2
            }
            builder.addImageAsset("logo", cfg.logoData, cfg.logoW, cfg.logoH)
            builder.addImageLayer(
                "Logo", "logo",
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(makeBandKFs(BAND_START_PCT, logoCX, bandCY)),
                    anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
                    scale = LottieBuilder.staticPropArray(_logoScale, _logoScale, FULL_PERCENT_D)
                )
            )
        }

        // --- Two diagonal slash dividers ---
        if (cfg.bgEnabled && !isCenter && badgeVerts != null) {
            builder.addShapeLayer(
                "Slash Dividers",
                buildJsonArray {
                    add(makeSlash(slash1LocalX))
                    add(makeSlash(slash2LocalX))
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(bandPosKFs)
                )
            )
        }

        // --- Badge background (accent color parallelogram) ---
        if (cfg.bgEnabled && !isCenter && badgeVerts != null) {
            builder.addShapeLayer(
                "Badge BG",
                buildJsonArray {
                    add(
                        makeGroup(
                            listOf(makePath(badgeVerts), makeFill(accentLottie, cfg.accentColorAlpha.toDouble())),
                        ),
                    )
                },
                LottieBuilder.defaultTransform(
                    position = LottieBuilder.animatedProp(bandPosKFs)
                )
            )
        }

        // --- Main band (bg color full rect) ---
        if (cfg.bgEnabled) {
            val bandItems = mutableListOf(
                makeRect(bandW, bandH, cornerPx * 0.3),
                makeFill(bgLottie, cfg.bgColorAlpha.toDouble())
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

        // --- Ticker bar (accent color) ---
        if (cfg.bgEnabled) {
            val tickerItems = mutableListOf(
                makeRect(bandW, tickerH, cornerPx * 0.2),
                makeFill(accentLottie, cfg.accentColorAlpha.toDouble())
            )
            makeStroke(
                borderLottie,
                borderPx * TICKER_BORDER_FACTOR,
                cfg.borderColorAlpha.toDouble(),
            )?.let { tickerItems.add(it) }
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
