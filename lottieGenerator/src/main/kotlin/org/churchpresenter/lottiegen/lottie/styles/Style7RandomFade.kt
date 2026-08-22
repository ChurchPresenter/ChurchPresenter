package org.churchpresenter.lottiegen.lottie.styles

import org.churchpresenter.lottiegen.lottie.makeTextDataWithAnimators
import org.churchpresenter.lottiegen.lottie.Easing
import org.churchpresenter.lottiegen.lottie.KeyframeInput
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.TextMeasurer
import org.churchpresenter.lottiegen.lottie.buildKeyframes
import org.churchpresenter.lottiegen.lottie.emToPx
import org.churchpresenter.lottiegen.lottie.hexToLottie
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.lottie.makeRandomFadeAnimator
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max

class Style7RandomFade : StyleGenerator {
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

        val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
        val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
        val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)

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

        // Vertical positioning
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

        // --- Name text (letters fade in randomly) ---
        if (!cfg.hideName) {
            val nameAnimator = makeRandomFadeAnimator(0.0, 55.0, inF, holdF, outF)
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

        // --- Info text (letters fade in randomly, slightly delayed) ---
        if (!cfg.hideInfo) {
            val infoAnimator = makeRandomFadeAnimator(25.0, 70.0, inF, holdF, outF)
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
                isCenter -> {
                    val maxW = max(
                        if (cfg.hideName) 0.0 else nameM.width.toDouble(),
                        if (cfg.hideInfo) 0.0 else infoM.width.toDouble()
                    )
                    canvasW / 2 - maxW / 2 - logoMargin - logoSizePx / 2
                }
                isRight -> canvasW - marginHPx - logoSizePx / 2
                else -> marginHPx + logoSizePx / 2
            }
            val logoCY = (nameCY + infoCY) / 2

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
    }
}
