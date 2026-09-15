package org.churchpresenter.lottiegen.band

import org.churchpresenter.lottiegen.band.BandGeometry.ARCH_QUARTER_H
import org.churchpresenter.lottiegen.band.BandGeometry.ARCH_QUARTER_W
import org.churchpresenter.lottiegen.band.BandGeometry.BOOKMARK_NOTCH
import org.churchpresenter.lottiegen.band.BandGeometry.BOOKMARK_STRIPE
import org.churchpresenter.lottiegen.band.BandGeometry.BOOKMARK_W
import org.churchpresenter.lottiegen.band.BandGeometry.BOOKMARK_X
import org.churchpresenter.lottiegen.band.BandGeometry.BOTTOM_BAND_H
import org.churchpresenter.lottiegen.band.BandGeometry.BOTTOM_BAND_RULE
import org.churchpresenter.lottiegen.band.BandGeometry.BRACKET_ARM_H
import org.churchpresenter.lottiegen.band.BandGeometry.BRACKET_ARM_W
import org.churchpresenter.lottiegen.band.BandGeometry.BLOCK_EDGE
import org.churchpresenter.lottiegen.band.BandGeometry.BLOCK_W
import org.churchpresenter.lottiegen.band.BandGeometry.CHAMFER_CUT
import org.churchpresenter.lottiegen.band.BandGeometry.CHAMFER_STRIPE
import org.churchpresenter.lottiegen.band.BandGeometry.CHECKER_ROWS
import org.churchpresenter.lottiegen.band.BandGeometry.DOUBLE_RULE_H
import org.churchpresenter.lottiegen.band.BandGeometry.PANEL_INSET_X
import org.churchpresenter.lottiegen.band.BandGeometry.PANEL_INSET_Y
import org.churchpresenter.lottiegen.band.BandGeometry.PENNANT_INNER_W
import org.churchpresenter.lottiegen.band.BandGeometry.PENNANT_INNER_Y
import org.churchpresenter.lottiegen.band.BandGeometry.PENNANT_W
import org.churchpresenter.lottiegen.band.BandGeometry.SIDE_TAB_W
import org.churchpresenter.lottiegen.band.BandGeometry.SLASH_1
import org.churchpresenter.lottiegen.band.BandGeometry.SLASH_GAP
import org.churchpresenter.lottiegen.band.BandGeometry.SLASH_W
import org.churchpresenter.lottiegen.band.BandGeometry.STEP_2_W
import org.churchpresenter.lottiegen.band.BandGeometry.STEP_3_W
import org.churchpresenter.lottiegen.band.BandGeometry.STEP_DROP
import org.churchpresenter.lottiegen.band.BandGeometry.STEP_W
import org.churchpresenter.lottiegen.band.BandGeometry.STRIPE_1
import org.churchpresenter.lottiegen.band.BandGeometry.STRIPE_GAP
import org.churchpresenter.lottiegen.band.BandGeometry.STRIPE_THIN
import org.churchpresenter.lottiegen.band.BandGeometry.STRIPE_W
import org.churchpresenter.lottiegen.band.BandGeometry.TAB_GAP
import org.churchpresenter.lottiegen.band.BandGeometry.TAB_H
import org.churchpresenter.lottiegen.band.BandGeometry.TAB_STEP
import org.churchpresenter.lottiegen.band.BandGeometry.TAB_W
import org.churchpresenter.lottiegen.band.BandGeometry.TAB_X
import org.churchpresenter.lottiegen.band.BandGeometry.TAB_Y
import org.churchpresenter.lottiegen.band.BandGeometry.TOP_TAB_H
import org.churchpresenter.lottiegen.band.BandGeometry.TOP_TAB_RULE
import org.churchpresenter.lottiegen.band.BandGeometry.TOP_TAB_W
import org.churchpresenter.lottiegen.band.BandGeometry.TWIN_GAP
import org.churchpresenter.lottiegen.band.BandGeometry.TWIN_W
import org.churchpresenter.lottiegen.band.BandGeometry.TWIN_X
import org.churchpresenter.lottiegen.band.BandGeometry.UNDERLINE_GAP
import org.churchpresenter.lottiegen.band.BandGeometry.UNDERLINE_H
import org.churchpresenter.lottiegen.band.BandGeometry.UNDERLINE_RULE_H
import org.churchpresenter.lottiegen.band.BandGeometry.ZIGZAG_H
import org.churchpresenter.lottiegen.band.BandGeometry.ZIGZAG_TEETH
import org.churchpresenter.lottiegen.band.BandGeometry.ZIGZAG_VALLEY
import org.churchpresenter.lottiegen.lottie.hexToLottie
import org.churchpresenter.lottiegen.lottie.makeEllipse
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makeRect
import org.churchpresenter.lottiegen.lottie.makeStroke

private const val PANEL_FRAME_PX = 4.0
private const val BRACKET_PX = 6.0
private const val HALF = 0.5
private const val TWO = 2.0

/**
 * The second score of styles — shapes, not gradients — drawn the same way as the first: geometry
 * in canvas pixels off the band's rectangle, proportions as fractions of its width and height.
 * [BandDecorations] hands every style it does not draw itself to this.
 */
internal class BandDecorationsMore(private val cfg: BibleLottieGenConfig, b: SlotBox) : BandShapes(b) {

    fun pieces(): List<BandPiece> = when (cfg.bandStyle) {
        BandStyle.UNDERLINE_BAR -> listOf(
            accent(x, bottom - h * UNDERLINE_H, w, h * UNDERLINE_H),
            tertiary(x, bottom - h * (UNDERLINE_H + UNDERLINE_GAP + UNDERLINE_RULE_H), w, h * UNDERLINE_RULE_H),
        )
        BandStyle.DOUBLE_RULE -> listOf(
            accent(x, y, w, h * DOUBLE_RULE_H),
            accent(x, bottom - h * DOUBLE_RULE_H, w, h * DOUBLE_RULE_H),
        )
        BandStyle.SIDE_TABS -> listOf(
            second(x, y, w * SIDE_TAB_W, h),
            accent(right - w * SIDE_TAB_W, y, w * SIDE_TAB_W, h),
        )
        BandStyle.BOOKMARK -> bookmark()
        BandStyle.STEPPED_LEFT -> listOf(
            second(x, y, w * STEP_W, h),
            tertiary(fx(STEP_W), y + h * STEP_DROP, w * STEP_2_W, h * (1 - STEP_DROP)),
            accent(fx(STEP_W + STEP_2_W), y + h * STEP_DROP * TWO, w * STEP_3_W, h * (1 - STEP_DROP * TWO)),
        )
        BandStyle.DIAGONAL_STRIPES -> listOf(
            slant(BandColorRole.SECOND, STRIPE_1, STRIPE_1 + STRIPE_W),
            slant(BandColorRole.ACCENT, STRIPE_1 + STRIPE_W + STRIPE_GAP, STRIPE_1 + STRIPE_W * TWO + STRIPE_GAP),
            slant(
                BandColorRole.TERTIARY,
                STRIPE_1 + STRIPE_W * TWO + STRIPE_GAP * TWO,
                STRIPE_1 + STRIPE_W * TWO + STRIPE_GAP * TWO + STRIPE_THIN,
            ),
        )
        BandStyle.CORNER_BRACKETS -> brackets()
        BandStyle.LEFT_BLOCK -> listOf(
            accent(fx(BLOCK_W), y, w * BLOCK_EDGE, h),
            second(x, y, w * BLOCK_W, h),
        )
        BandStyle.TOP_TAB -> listOf(
            accent(x, y + h * TOP_TAB_H, w, h * TOP_TAB_RULE),
            second(x, y, w * TOP_TAB_W, h * TOP_TAB_H),
        )
        BandStyle.CHECKER_EDGE -> checker()
        BandStyle.SPLIT_VERTICAL -> listOf(second(x + w * HALF, y, w * HALF, h))
        BandStyle.QUARTER_ARCH -> listOf(
            BandPiece.Shaped(
                BandColorRole.SECOND, makeEllipse(w * ARCH_QUARTER_W, h * ARCH_QUARTER_H, listOf(x, bottom)),
            ),
        )
        BandStyle.TWIN_RULES -> listOf(
            second(fx(TWIN_X), y, w * TWIN_W, h),
            tertiary(fx(TWIN_X + TWIN_W + TWIN_GAP), y, w * TWIN_W, h),
        )
        BandStyle.INNER_PANEL -> panel()
        BandStyle.ZIGZAG_EDGE -> listOf(zigzag())
        BandStyle.PENNANT -> listOf(
            poly(
                BandColorRole.ACCENT,
                x to y + h * PENNANT_INNER_Y, fx(PENNANT_INNER_W) to b.centerY, x to bottom - h * PENNANT_INNER_Y,
            ),
            poly(BandColorRole.SECOND, x to y, fx(PENNANT_W) to b.centerY, x to bottom),
        )
        BandStyle.SLASHES -> listOf(
            slant(BandColorRole.ACCENT, SLASH_1, SLASH_1 + SLASH_W),
            slant(BandColorRole.TERTIARY, SLASH_1 + SLASH_W + SLASH_GAP, SLASH_1 + SLASH_W * TWO + SLASH_GAP),
        )
        BandStyle.STACKED_TABS -> listOf(
            second(fx(TAB_X), y + h * TAB_Y, w * TAB_W, h * TAB_H),
            accent(fx(TAB_X), y + h * (TAB_Y + TAB_GAP * 1), w * (TAB_W - TAB_STEP), h * TAB_H),
            tertiary(fx(TAB_X), y + h * (TAB_Y + TAB_GAP * TWO), w * (TAB_W - TAB_STEP * TWO), h * TAB_H),
        )
        BandStyle.BOTTOM_BAND -> listOf(
            accent(x, bottom - h * BOTTOM_BAND_H, w, h * BOTTOM_BAND_RULE),
            second(x, bottom - h * BOTTOM_BAND_H, w, h * BOTTOM_BAND_H),
        )
        BandStyle.CHAMFER_BLOCK -> chamferBlock()
        else -> emptyList()
    }

    /** The ribbon: a strip from the top with its foot notched to a point, and a stripe down its left third. */
    private fun bookmark(): List<BandPiece> {
        val left = fx(BOOKMARK_X)
        val rightEdge = fx(BOOKMARK_X + BOOKMARK_W)
        val mid = fx(BOOKMARK_X + BOOKMARK_W / TWO)
        return listOf(
            accent(left, y, w * BOOKMARK_STRIPE, h * (1 - BOOKMARK_NOTCH)),
            poly(
                BandColorRole.SECOND,
                left to y, rightEdge to y, rightEdge to bottom, mid to bottom - h * BOOKMARK_NOTCH, left to bottom,
            ),
        )
    }

    /** Two rectangles per corner, meeting in an L. */
    private fun brackets(): List<BandPiece> {
        val armW = w * BRACKET_ARM_W
        val armH = h * BRACKET_ARM_H
        return listOf(
            accent(x, y, armW, BRACKET_PX), accent(x, y, BRACKET_PX, armH),
            accent(right - armW, y, armW, BRACKET_PX), accent(right - BRACKET_PX, y, BRACKET_PX, armH),
            accent(x, bottom - BRACKET_PX, armW, BRACKET_PX), accent(x, bottom - armH, BRACKET_PX, armH),
            accent(right - armW, bottom - BRACKET_PX, armW, BRACKET_PX),
            accent(right - BRACKET_PX, bottom - armH, BRACKET_PX, armH),
        )
    }

    /** Two columns of squares the height of a row each, colours alternating both ways. */
    private fun checker(): List<BandPiece> {
        val cell = h / CHECKER_ROWS
        return buildList {
            for (row in 0 until CHECKER_ROWS) for (col in 0 until 2) {
                val role = if ((row + col) % 2 == 0) BandColorRole.SECOND else BandColorRole.TERTIARY
                add(rect(role, x + col * cell, y + row * cell, cell, cell))
            }
        }
    }

    /** The inset panel, then its frame above it. */
    private fun panel(): List<BandPiece> {
        val pw = w * (1 - PANEL_INSET_X * TWO)
        val ph = h * (1 - PANEL_INSET_Y * TWO)
        val rectShape = makeRect(pw, ph, cfg.cornerRadiusPx.toDouble(), listOf(b.centerX, b.centerY))
        val frame = makeStroke(hexToLottie(cfg.accentColor), PANEL_FRAME_PX, cfg.accentAlpha.toDouble())
        return listOfNotNull(
            frame?.let { BandPiece.Painted(makeGroup(listOf(rectShape, it))) },
            BandPiece.Shaped(BandColorRole.SECOND, rectShape),
        )
    }

    /** Teeth along the bottom, their points at the edge and their valleys [ZIGZAG_H] up, filled below. */
    private fun zigzag(): BandPiece {
        val tooth = w / ZIGZAG_TEETH
        val top = bottom - h * ZIGZAG_H
        val valley = bottom - h * ZIGZAG_VALLEY
        val points = buildList {
            add(x to bottom)
            for (i in 0..ZIGZAG_TEETH) add(x + i * tooth to if (i % 2 == 0) top else valley)
            add(right to bottom)
        }
        return polyOf(BandColorRole.SECOND, points)
    }

    /** The block with its top-right corner cut off at 45°, and a stripe down the cut. */
    private fun chamferBlock(): List<BandPiece> {
        val edge = fx(BLOCK_W)
        val cut = h * CHAMFER_CUT
        val stripe = w * CHAMFER_STRIPE
        return listOf(
            poly(
                BandColorRole.TERTIARY,
                edge - cut - stripe to y, edge - cut to y, edge to y + cut, edge - stripe to y + cut,
            ),
            poly(BandColorRole.SECOND, x to y, edge - cut to y, edge to y + cut, edge to bottom, x to bottom),
        )
    }
}
