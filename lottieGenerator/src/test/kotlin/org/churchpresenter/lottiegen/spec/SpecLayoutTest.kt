package org.churchpresenter.lottiegen.spec

import org.churchpresenter.lottiegen.lottie.TextMeasurer
import org.churchpresenter.lottiegen.lottie.emToPx
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the generic layout engine reproduces Style1Bar's hand-written geometry
 * for all three alignments — the fidelity contract behind the Style 1 spec port.
 * Expected values are computed with the exact formulas from Style1Bar.kt.
 */
class SpecLayoutTest {

    private val defaultSpec = StyleSpec()
    private val eps = 1e-6

    private fun cfg(align: String, logoEnabled: Boolean = false) = LottieGenConfig(
        align = align,
        logoEnabled = logoEnabled
    )

    /** Style1Bar's own geometry math, verbatim. */
    private class Style1Expectations(cfg: LottieGenConfig) {
        val baseSize = cfg.baseSize.toDouble()
        val nameSizePx = emToPx(cfg.nameSize.toDouble(), baseSize)
        val infoSizePx = emToPx(cfg.infoSize.toDouble(), baseSize)
        val nameM = TextMeasurer.measure(
            cfg.nameText, cfg.fontFamily, nameSizePx.toFloat(), cfg.nameWeight, cfg.nameTransform,
        )
        val infoM = TextMeasurer.measure(
            cfg.infoText, cfg.fontFamily, infoSizePx.toFloat(), cfg.infoWeight, cfg.infoTransform,
        )

        val barWidth = emToPx(0.3, baseSize)
        val barHeight = emToPx(3.5, baseSize)
        val textMargin = emToPx(1.2, baseSize)
        val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
        val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
        val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
        val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)
        val logoMargin = emToPx(0.8, baseSize)

        val textBlockW = max(nameM.width, infoM.width) + textMargin + 10
        val totalContentW = textBlockW + barWidth + if (cfg.logoEnabled) logoSizePx + logoMargin else 0.0
        val bgW = totalContentW + emToPx(2.0, baseSize)
        val bgH = barHeight + emToPx(2.0, baseSize)

        val canvasW = cfg.canvasW.toDouble()
        val canvasH = cfg.canvasH.toDouble()
        val baseY = canvasH - marginVPx - barHeight / 2

        val isRight = cfg.align == "right"
        val isCenter = cfg.align == "center"

        val baseX = when {
            isRight -> canvasW - marginHPx - totalContentW / 2
            isCenter -> canvasW / 2
            else -> marginHPx + totalContentW / 2
        }

        val logoSpace = if (cfg.logoEnabled) logoSizePx + logoMargin else 0.0
        val barX = when {
            isRight -> baseX + totalContentW / 2 - logoSpace - barWidth / 2
            else -> baseX - totalContentW / 2 + logoSpace + barWidth / 2
        }

        val textBaseX = when {
            isRight -> barX - textMargin
            isCenter -> barX + barWidth / 2 + textBlockW / 2
            else -> barX + barWidth / 2 + textMargin
        }

        val nameY = baseY - lineSpacingPx / 2 - nameSizePx * 0.1
        val infoY = baseY + lineSpacingPx / 2 + infoSizePx * 0.9
    }

    @Test
    fun blockGeometryMatchesStyle1ForAllAlignments() {
        for (align in listOf("left", "center", "right")) {
            for (logo in listOf(false, true)) {
                val config = cfg(align, logo)
                val expect = Style1Expectations(config)
                val layout = SpecLayoutContext(defaultSpec, config)

                assertEquals(expect.totalContentW, layout.totalContentW, eps, "totalContentW $align logo=$logo")
                assertEquals(expect.baseX, layout.baseX, eps, "baseX $align logo=$logo")
                assertEquals(expect.baseY, layout.baseY, eps, "baseY $align logo=$logo")
                assertEquals(expect.nameY, layout.nameLineY, eps, "nameLineY $align logo=$logo")
                assertEquals(expect.infoY, layout.infoLineY, eps, "infoLineY $align logo=$logo")
            }
        }
    }

    @Test
    fun accentSlotCenterMatchesStyle1BarX() {
        for (align in listOf("left", "center", "right")) {
            for (logo in listOf(false, true)) {
                val config = cfg(align, logo)
                val expect = Style1Expectations(config)
                val layout = SpecLayoutContext(defaultSpec, config)

                val accentCenter = layout.resolve(
                    Placement(slot = "accent", anchorIn = AnchorIn.CENTER, line = LineAnchor.BLOCK_CENTER)
                )
                assertEquals(expect.barX, accentCenter.x, eps, "barX $align logo=$logo")
                assertEquals(expect.baseY, accentCenter.y, eps, "barY $align logo=$logo")
            }
        }
    }

    @Test
    fun textAnchorMatchesStyle1TextBaseXWithPortOverrides() {
        // The port's placement: START anchor, with the center/right overrides carried
        // in style1_bar_port.json.
        val placement = Placement(
            slot = "text",
            anchorIn = AnchorIn.START,
            line = LineAnchor.NAME_LINE,
            alignOverrides = mapOf(
                "center" to PlacementOverride(anchorIn = AnchorIn.CENTER, offsetXEm = -0.6),
                "right" to PlacementOverride(offsetXEm = -0.15)
            )
        )
        for (align in listOf("left", "center", "right")) {
            val config = cfg(align)
            val expect = Style1Expectations(config)
            val layout = SpecLayoutContext(defaultSpec, config)

            val point = layout.resolve(placement)
            assertEquals(expect.textBaseX, point.x, eps, "textBaseX $align")
            assertEquals(expect.nameY, point.y, eps, "nameY $align")
        }
    }

    @Test
    fun collapsedLogoSlotRemovesCoreAndGaps() {
        val withLogo = SpecLayoutContext(defaultSpec, cfg("left", logoEnabled = true))
        val without = SpecLayoutContext(defaultSpec, cfg("left", logoEnabled = false))
        val baseSize = 24.0
        val logoSpace = emToPx(3.5, baseSize) + emToPx(0.8, baseSize)
        assertEquals(logoSpace, withLogo.totalContentW - without.totalContentW, eps)
    }

    @Test
    fun contentDerivedSizeMatchesStyle1Background() {
        for (align in listOf("left", "center", "right")) {
            val config = cfg(align, logoEnabled = true)
            val expect = Style1Expectations(config)
            val layout = SpecLayoutContext(defaultSpec, config)

            val (w, h) = layout.sizeOf(SizeSpec.ContentDerived(1.0, 1.0))
            assertEquals(expect.bgW, w, eps, "bgW $align")
            assertEquals(expect.bgH, h, eps, "bgH $align")
        }
    }

    @Test
    fun flowOffsetsMirrorOnRightAlignment() {
        val leftLayout = SpecLayoutContext(defaultSpec, cfg("left"))
        val rightLayout = SpecLayoutContext(defaultSpec, cfg("right"))
        val placement = Placement(slot = "accent", anchorIn = AnchorIn.CENTER, offsetXEm = 10.0)

        val leftBar = leftLayout.resolve(placement.copy(offsetXEm = 0.0))
        val rightBar = rightLayout.resolve(placement.copy(offsetXEm = 0.0))
        val leftOff = leftLayout.resolve(placement)
        val rightOff = rightLayout.resolve(placement)

        assertEquals(leftBar.x + emToPx(10.0, 24.0), leftOff.x, eps)
        assertEquals(rightBar.x - emToPx(10.0, 24.0), rightOff.x, eps)

        val unmirrored = rightLayout.resolve(placement.copy(mirror = MirrorMode.NONE))
        assertEquals(rightBar.x + emToPx(10.0, 24.0), unmirrored.x, eps)
    }

    @Test
    fun blockPseudoSlotResolvesToBlockGeometry() {
        for (align in listOf("left", "center", "right")) {
            val config = cfg(align)
            val layout = SpecLayoutContext(defaultSpec, config)
            val center = layout.resolve(Placement(slot = SpecLayoutContext.BLOCK_SLOT, anchorIn = AnchorIn.CENTER))
            assertEquals(layout.baseX, center.x, eps, "block center $align")

            val start = layout.resolve(Placement(slot = SpecLayoutContext.BLOCK_SLOT, anchorIn = AnchorIn.START))
            assertEquals(layout.blockStartX, start.x, eps, "block start $align")
        }
    }

    @Test
    fun unknownSlotFallsBackToBlockCenterWithWarning() {
        val layout = SpecLayoutContext(defaultSpec, cfg("left"))
        val point = layout.resolve(Placement(slot = "nope"))
        assertEquals(layout.baseX, point.x, eps)
        assertTrue(layout.warnings.any { it.contains("nope") })
    }
}
