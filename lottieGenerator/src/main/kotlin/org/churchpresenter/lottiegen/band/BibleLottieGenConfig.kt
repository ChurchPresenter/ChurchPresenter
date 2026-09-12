package org.churchpresenter.lottiegen.band

import kotlinx.serialization.Serializable

/** The shapes a band is drawn with. */
enum class BandStyle { SOLID_BAR, GRADIENT_BAR, ACCENT_EDGE_BAR, GLASS_PANEL, RIBBON }

/**
 * How the band arrives. The exit is always the mirror of the entrance, so `SLIDE_UP` leaves by
 * sliding back down and `UNROLL` rolls back up.
 */
enum class BandEntrance {
    FADE, SLIDE_UP, SLIDE_DOWN, SLIDE_LEFT, SLIDE_RIGHT, WIPE_LEFT, WIPE_RIGHT,

    /** Grows from its bottom edge, like a blind being lowered from the floor up. */
    UNROLL,

    /** Grows from its centre line outwards, like a scroll being opened. */
    SCROLL_OPEN,

    /** An accent bar sweeps across and the band fills in behind it. */
    SWIPE,

    /** Scales up from the centre. */
    GROW,
}

/**
 * How the verse text arrives, changes and leaves. The keyframed ones are baked into the file;
 * `TYPEWRITER`, `TYPEWRITER_WORDS` and `TICKER` are emitted static and driven by the player,
 * which knows the text — a range selector cannot reveal a string the file has never seen.
 */
enum class TextAnimation {
    FADE, SLIDE_UP, SLIDE_DOWN, SLIDE_LEFT, SLIDE_RIGHT, WIPE, TYPEWRITER, TYPEWRITER_WORDS, TICKER;

    /** Whether the text motion is the player's to drive rather than the file's. */
    val isRuntimeDriven: Boolean get() = this == TYPEWRITER || this == TYPEWRITER_WORDS || this == TICKER
}

/** How many translations the band has room for, and how they share it. */
enum class SlotLayout { SINGLE, SIDE_BY_SIDE, STACKED }

enum class ReferencePlacement { ABOVE, BELOW }

/**
 * Everything the Bible band generator needs. Sizes are pixels on a [canvasW] × [canvasH] canvas,
 * which the host sizes to one output's band; durations are seconds.
 *
 * The `preview*` fields only shape the sample the generator shows: the player replaces text and
 * typography with the live verse and the Bible settings, so nothing here about the text survives
 * into the output except the slot geometry.
 */
@Serializable
data class BibleLottieGenConfig(
    val canvasW: Int = DEFAULT_CANVAS_W,
    val canvasH: Int = DEFAULT_CANVAS_H,
    val bandStyle: BandStyle = BandStyle.SOLID_BAR,
    val entrance: BandEntrance = BandEntrance.SLIDE_UP,
    val textAnimation: TextAnimation = TextAnimation.FADE,
    val layout: SlotLayout = SlotLayout.SINGLE,
    val referencePlacement: ReferencePlacement = ReferencePlacement.BELOW,
    val bgColor: String = "#101820",
    val bgAlpha: Int = DEFAULT_BG_ALPHA,
    val accentColor: String = "#D54141",
    val accentAlpha: Int = FULL_ALPHA,
    val gradientColor: String = "#000000",
    val borderColor: String = "#FFFFFF",
    val borderAlpha: Int = DEFAULT_BORDER_ALPHA,
    val borderThickness: Int = 0,
    val cornerRadiusPx: Int = 0,
    val insetPx: Int = 0,
    val paddingPx: Int = DEFAULT_PADDING,
    val referenceHeightFraction: Float = DEFAULT_REFERENCE_FRACTION,
    val bgInSeconds: Float = DEFAULT_BG_IN,
    val textInSeconds: Float = DEFAULT_TEXT_IN,
    val holdSeconds: Float = DEFAULT_HOLD,
    val textOutSeconds: Float = DEFAULT_TEXT_OUT,
    val bgOutSeconds: Float = DEFAULT_BG_OUT,
    val tickerPxPerSecond: Int = DEFAULT_TICKER_SPEED,
    val previewFontFamily: String = "Arial",
    val previewTextSizePx: Int = DEFAULT_PREVIEW_TEXT_SIZE,
    val previewReferenceSizePx: Int = DEFAULT_PREVIEW_REFERENCE_SIZE,
    val previewTextColor: String = "#FFFFFF",
    val previewReferenceColor: String = "#FFFFFF",
    val previewBold: Boolean = false,
    val previewText1: String = DEFAULT_PREVIEW_TEXT,
    val previewReference1: String = "John 3:16 (KJV)",
    val previewText2: String = DEFAULT_PREVIEW_TEXT_2,
    val previewReference2: String = "Juan 3:16 (RVR)",
) {
    companion object {
        const val DEFAULT_CANVAS_W = 1920
        const val DEFAULT_CANVAS_H = 356
        const val FULL_ALPHA = 100
        const val DEFAULT_BG_ALPHA = 90
        const val DEFAULT_BORDER_ALPHA = 30
        const val DEFAULT_PADDING = 40
        const val DEFAULT_REFERENCE_FRACTION = 0.28f
        const val DEFAULT_BG_IN = 0.6f
        const val DEFAULT_TEXT_IN = 0.5f
        const val DEFAULT_HOLD = 2f
        const val DEFAULT_TEXT_OUT = 0.4f
        const val DEFAULT_BG_OUT = 0.6f
        const val DEFAULT_TICKER_SPEED = 120
        const val DEFAULT_PREVIEW_TEXT_SIZE = 56
        const val DEFAULT_PREVIEW_REFERENCE_SIZE = 40
        const val DEFAULT_PREVIEW_TEXT =
            "For God so loved the world, that he gave his only begotten Son, that whosoever " +
                "believeth in him should not perish, but have everlasting life."
        const val DEFAULT_PREVIEW_TEXT_2 =
            "Porque de tal manera amó Dios al mundo, que ha dado a su Hijo unigénito, para que " +
                "todo aquel que en él cree, no se pierda, mas tenga vida eterna."
    }
}
