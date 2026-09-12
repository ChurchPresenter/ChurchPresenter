package org.churchpresenter.lottiegen.band

import kotlinx.serialization.Serializable

/**
 * The shapes a band is drawn with. Every style paints the background colour — over a picture,
 * when one is set — and [usesSecond] and [usesTertiary] say which of the other colours it also
 * draws from, so the generator shows only the pickers that matter.
 */
enum class BandStyle(
    val usesSecond: Boolean = false,
    val usesTertiary: Boolean = false,
) {
    SOLID_BAR,
    GRADIENT_BAR(usesSecond = true),
    ACCENT_EDGE_BAR,
    GLASS_PANEL,
    RIBBON,

    /** A gradient running left to right, background colour to second. */
    GRADIENT_HORIZONTAL(usesSecond = true),

    /** A gradient running corner to corner, background colour to second. */
    GRADIENT_ANGLED(usesSecond = true),

    /** A three-stop gradient left to right: background, accent, second. */
    GRADIENT_TRIO(usesSecond = true),

    /** Top half in the background colour, bottom half in the second. */
    SPLIT_SHUTTER(usesSecond = true),

    /** Three stacked horizontal bands: accent, background, second. */
    HORIZONTAL_BANDS(usesSecond = true),

    /** The whole band split by two diagonals into three colours: accent, second, third. */
    TRICOLOR_DIAGONAL(usesSecond = true, usesTertiary = true),

    /** A slanted blade in the second colour down the left side. */
    ANGLED_BLADE(usesSecond = true),

    /** Slanted thirds: an accent region on the left, the second colour on the right, a third-colour divider. */
    SLANTED_THIRDS(usesSecond = true, usesTertiary = true),

    /** Two wide bands crossing on the right side: accent leaning one way, second the other. */
    CROSSED_BANDS(usesSecond = true),

    /** A chevron in the second colour on the left, echoed by a thin stripe in the third. */
    CHEVRON_TAG(usesSecond = true, usesTertiary = true),

    /**
     * A big wedge in the top-left corner in the second colour, one in the bottom-right in the
     * accent, and a third-colour tip.
     */
    CORNER_WEDGES(usesSecond = true, usesTertiary = true),

    /** An arch rising from the bottom edge in the second colour, ringed in the accent, on a third-colour base. */
    ARCH_DECK(usesSecond = true, usesTertiary = true),

    /** An accent glow fading in from the left, an edge in the second colour, underlined in the third. */
    SPOTLIGHT_BAND(usesSecond = true, usesTertiary = true),

    /** A diagonal wedge on the right in the second colour, split by an accent rule, ruled in the third. */
    DIAGONAL_SPLIT(usesSecond = true, usesTertiary = true),

    /** A ribbon hanging down the left in the second colour, folded over in the accent, banded in the third. */
    RIBBON_FOLD(usesSecond = true, usesTertiary = true),

    /**
     * A wave rolling along the bottom in the second colour, with an accent crest and a
     * third-colour swell behind it.
     */
    WAVE_DECK(usesSecond = true, usesTertiary = true),
}

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
 * How a slot's text is justified. `FOLLOW_SETTINGS` leaves it to the Bible settings at play time
 * (the preview centres it); the other three are pinned by the template and override them.
 */
enum class BandTextAlign { FOLLOW_SETTINGS, LEFT, CENTER, RIGHT }

/** What the band is for: the words in the generator change with it, the file does not. */
enum class BandContentKind { BIBLE, SONG }

/** The four colour slots a style draws from; each can be a colour or a picture. */
enum class BandColorRole { BACKGROUND, SECOND, ACCENT, TERTIARY }

/** A picture as a data URL with its pixel size, and the file name it came from for the UI. */
@Serializable
data class BandImage(val data: String, val width: Int, val height: Int, val name: String) {
    val isUsable: Boolean get() = width > 0 && height > 0
}

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
    val kind: BandContentKind = BandContentKind.BIBLE,
    val bandStyle: BandStyle = BandStyle.SOLID_BAR,
    val entrance: BandEntrance = BandEntrance.SLIDE_UP,
    val textAnimation: TextAnimation = TextAnimation.FADE,
    val layout: SlotLayout = SlotLayout.SINGLE,
    val referencePlacement: ReferencePlacement = ReferencePlacement.BELOW,
    val textAlign: BandTextAlign = BandTextAlign.FOLLOW_SETTINGS,
    val referenceAlign: BandTextAlign = BandTextAlign.FOLLOW_SETTINGS,
    val bgColor: String = "#101820",
    val bgAlpha: Int = DEFAULT_BG_ALPHA,
    val accentColor: String = "#D54141",
    val accentAlpha: Int = FULL_ALPHA,
    /** The second colour: the gradient's end, a split's other half, a wedge, a deck. */
    val gradientColor: String = "#3A0CA3",
    val tertiaryColor: String = "#F2C94C",
    val tertiaryAlpha: Int = FULL_ALPHA,
    /**
     * Pictures standing in for colours: any role can be a photo instead of a flat colour. Each is
     * scaled to cover the band and shows through exactly the shapes that role paints; the
     * background role's picture sits under everything with the background colour as a tint.
     */
    val images: Map<BandColorRole, BandImage> = emptyMap(),
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
    /** Whether the background role is a picture, which turns its colour into a tint. */
    val hasBackgroundImage: Boolean get() = images.containsKey(BandColorRole.BACKGROUND)

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
