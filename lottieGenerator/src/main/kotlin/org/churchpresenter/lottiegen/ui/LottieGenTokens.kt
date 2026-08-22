package org.churchpresenter.lottiegen.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One value for every colour the LottieGen control surface draws, in one of two palettes.
 *
 * The palette was authored in OKLCH; the values in [DarkPalette] and [LightPalette] are the sRGB
 * rasterisation of those colours (verified against Chrome's own `oklch()` rasteriser), so the
 * desktop UI matches the design reference byte for byte. **Keep the OKLCH source in the comment
 * next to each value** — it is the thing that is actually being tuned when the palette changes, and
 * the two palettes are only comparable through it: light is the same hue and role at a mirrored
 * lightness, not an independent set of hex codes.
 */
/*
 * Suppressed rather than restructured: see the **Theme** section of `lottieGenerator/AGENT.md`.
 *
 * These 51 roles are the hand-drawn panel chrome Material has no equivalent for, and a flat token
 * list is what they want to be. `constructorThreshold` is 7, and no shallower grouping reaches it —
 * the nine natural banner groups are themselves 8-11 members each and would each be flagged in
 * turn, so satisfying the rule needs three levels of nesting (3 super-groups → 14 groups → 51
 * colours) and turns `palette.appBg` into `palette.chrome.surfaces.appBg`. That is contortion for a
 * lint score, and it is what leaving this one finding standing kept the whole module off the CI
 * detekt gate for.
 */
@Suppress("LongParameterList")
@Immutable
class LottieGenPalette(
    // ── Surfaces ────────────────────────────────────────────────────────────
    val appBg: Color,
    val panelBg: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val cardBorderOpen: Color,
    val divider: Color,
    val headBgOpen: Color,
    val headBgHover: Color,
    // ── Section chrome ──────────────────────────────────────────────────────
    val tick: Color,
    val titleText: Color,
    val hintText: Color,
    val caret: Color,
    // ── Fields ──────────────────────────────────────────────────────────────
    val fieldBg: Color,
    val fieldBorder: Color,
    val fieldBorderHover: Color,
    val fieldLabel: Color,
    // ── Text ────────────────────────────────────────────────────────────────
    val primaryText: Color,
    val inputText: Color,
    val labelText: Color,
    val valueText: Color,
    val hexText: Color,
    val outlineText: Color,
    val smallBtnText: Color,
    val segInactive: Color,
    val unitText: Color,
    val dimText: Color,
    val placeholder: Color,
    // ── Sliders ─────────────────────────────────────────────────────────────
    val trackBg: Color,
    val transportTrack: Color,
    val fillStart: Color,
    val fillEnd: Color,
    // ── Accent / actions ────────────────────────────────────────────────────
    val accent: Color,
    val accentHover: Color,
    val onAccent: Color,
    val subtleBg: Color,
    val subtleBorder: Color,
    val borderHover: Color,
    val outlineBg: Color,
    val checkOffBorder: Color,
    val segBorder: Color,
    // ── Preview ─────────────────────────────────────────────────────────────
    val previewBg: Color,
    val previewDivider: Color,
    val canvasBg: Color,
    val canvasChecker: Color,
    val badgeBg: Color,
    val badgeBorder: Color,
    val liveDot: Color,
    // ── Branding ────────────────────────────────────────────────────────────
    val logoChipBg: Color,
    val logoIcon: Color,
    // ── Scrollbar ───────────────────────────────────────────────────────────
    val scrollThumb: Color,
    val scrollThumbHover: Color,
)

/** The palette the tool has always shipped: near-black panels, warm off-white text, teal accent. */
val DarkPalette = LottieGenPalette(
    appBg = Color(0xFF020509),            // oklch(11%   0.014 255)
    panelBg = Color(0xFF04070C),          // oklch(12.5% 0.016 255)
    cardBg = Color(0xFF050910),           // oklch(14%   0.018 255)
    cardBorder = Color(0xFF131921),       // oklch(21%   0.018 255)
    cardBorderOpen = Color(0xFF1D252F),   // oklch(26%   0.022 255)
    divider = Color(0xFF0E141C),          // oklch(19%   0.018 255)
    headBgOpen = Color(0xFF070E16),       // oklch(16%   0.021 255)
    headBgHover = Color(0xFF091019),      // oklch(17%   0.022 255)

    tick = Color(0xFFDD9314),             // oklch(72% 0.15 72)  — amber section marker
    titleText = Color(0xFFDEDAD5),        // oklch(89% 0.008 75)
    hintText = Color(0xFF5A5E63),         // oklch(48% 0.01 255)
    caret = Color(0xFF65696F),            // oklch(52% 0.01 255)

    fieldBg = Color(0xFF091018),          // oklch(17% 0.02 255)
    fieldBorder = Color(0xFF1B222B),      // oklch(25% 0.02 255)
    fieldBorderHover = Color(0xFF343B45), // oklch(35% 0.02 255)
    fieldLabel = Color(0xFF52565B),       // oklch(45% 0.01 255)

    primaryText = Color(0xFFDBD7D2),      // oklch(88% 0.008 75)
    inputText = Color(0xFFE4E1DC),        // oklch(91% 0.008 75)
    labelText = Color(0xFF95928D),        // oklch(66% 0.008 75)
    valueText = Color(0xFFA19E99),        // oklch(70% 0.008 75)
    hexText = Color(0xFFBAB7B2),          // oklch(78% 0.008 75)
    outlineText = Color(0xFFC7C3BE),      // oklch(82% 0.008 75)
    smallBtnText = Color(0xFFAEAAA5),     // oklch(74% 0.008 75)
    segInactive = Color(0xFF9B9893),      // oklch(68% 0.008 75)
    unitText = Color(0xFF494D53),         // oklch(42% 0.01 255)
    dimText = Color(0xFF44484D),          // oklch(40% 0.01 255)
    placeholder = Color(0xFF3F4348),      // oklch(38% 0.01 255)

    trackBg = Color(0xFF1B2026),          // oklch(24% 0.015 255)
    transportTrack = Color(0xFF181D24),   // oklch(23% 0.015 255)
    fillStart = Color(0xFF009192),        // oklch(58% 0.13 195)
    fillEnd = Color(0xFF00BBBD),          // oklch(70% 0.16 195)

    accent = Color(0xFF00A2A4),           // oklch(62% 0.16 195)
    accentHover = Color(0xFF008F91),      // oklch(56% 0.16 195)
    onAccent = Color(0xFF010A0A),         // oklch(13% 0.02 195)
    subtleBg = Color(0xFF0D141E),         // oklch(19% 0.022 255)
    subtleBorder = Color(0xFF222933),     // oklch(28% 0.02 255)
    borderHover = Color(0xFF414853),      // oklch(40% 0.02 255)
    outlineBg = Color(0xFF0C121A),        // oklch(18% 0.02 255)
    checkOffBorder = Color(0xFF2C333D),   // oklch(32% 0.02 255)
    segBorder = Color(0xFF192029),        // oklch(24% 0.02 255)

    previewBg = Color(0xFF020306),        // oklch(10%   0.012 255)
    previewDivider = Color(0xFF0F141B),   // oklch(19%   0.016 255)
    canvasBg = Color(0xFF05080C),         // oklch(13%   0.012 255)
    canvasChecker = Color(0xFF090C11),    // oklch(15.5% 0.012 255)
    badgeBg = Color(0xFF060C13),          // oklch(15% 0.02 255)
    badgeBorder = Color(0xFF171D26),      // oklch(23% 0.02 255)
    liveDot = Color(0xFF4DB956),          // oklch(70% 0.17 145)

    logoChipBg = Color(0xFF002627),       // oklch(24% 0.05 195)
    logoIcon = Color(0xFF00C0C2),         // oklch(72% 0.15 195)

    scrollThumb = Color(0xFF202730),      // oklch(27% 0.02 255)
    scrollThumbHover = Color(0xFF39404A), // oklch(37% 0.02 255)
)

/**
 * The same design at mirrored lightness, for when the host app is in a light theme.
 *
 * Every entry keeps its dark counterpart's hue and role and moves its OKLCH lightness across the
 * midpoint — surfaces rise into the nineties, text drops into the twenties. Three roles are *not*
 * a straight mirror, because lightness alone does not carry them:
 *
 * - **The teal accent darkens rather than brightens** (62% → 52%), and [onAccent] flips from
 *   near-black to near-white. A 62% teal is legible on near-black and washes out on white.
 * - **[canvasChecker] separates further from [canvasBg]** (6 points of lightness against the dark
 *   set's 2.5). The transparency checkerboard is the one place where two adjacent neutrals must
 *   stay *visibly* distinct, and equal steps read as flat once both are light.
 * - **[tick] and [liveDot]** — the amber marker and the green live dot — darken for the same
 *   contrast reason as the accent.
 */
val LightPalette = LottieGenPalette(
    appBg = Color(0xFFEFF2F6),            // oklch(96%   0.006 255)
    panelBg = Color(0xFFF6F9FC),          // oklch(98%   0.005 255)
    cardBg = Color(0xFFFFFFFF),           // oklch(100%  0     255)
    cardBorder = Color(0xFFD7DBE0),       // oklch(89%   0.008 255)
    cardBorderOpen = Color(0xFFC5CBD2),   // oklch(84%   0.012 255)
    divider = Color(0xFFE1E5E9),          // oklch(92%   0.007 255)
    headBgOpen = Color(0xFFF2F5FB),       // oklch(97%   0.008 255)
    headBgHover = Color(0xFFE8EDF4),      // oklch(94.5% 0.01  255)

    tick = Color(0xFFB26F00),             // oklch(60% 0.14 72)  — amber section marker
    titleText = Color(0xFF1D1A17),        // oklch(22% 0.008 75)
    hintText = Color(0xFF65696F),         // oklch(52% 0.01 255)
    caret = Color(0xFF5A5E63),            // oklch(48% 0.01 255)

    fieldBg = Color(0xFFF8FAFD),          // oklch(98.5% 0.004 255)
    fieldBorder = Color(0xFFCDD1D8),      // oklch(86%   0.01  255)
    fieldBorderHover = Color(0xFFA8AFB7), // oklch(75%   0.014 255)
    fieldLabel = Color(0xFF65696F),       // oklch(52%   0.01  255)

    primaryText = Color(0xFF221F1B),      // oklch(24% 0.008 75)
    inputText = Color(0xFF14110E),        // oklch(18% 0.008 75)
    labelText = Color(0xFF504C48),        // oklch(42% 0.008 75)
    valueText = Color(0xFF45423E),        // oklch(38% 0.008 75)
    hexText = Color(0xFF302D29),          // oklch(30% 0.008 75)
    outlineText = Color(0xFF262420),      // oklch(26% 0.008 75)
    smallBtnText = Color(0xFF3A3733),     // oklch(34% 0.008 75)
    segInactive = Color(0xFF4A4743),      // oklch(40% 0.008 75)
    unitText = Color(0xFF6E7277),         // oklch(55% 0.01 255)
    dimText = Color(0xFF73787D),          // oklch(57% 0.01 255)
    placeholder = Color(0xFF7C8186),      // oklch(60% 0.01 255)

    trackBg = Color(0xFFD3D8DE),          // oklch(88% 0.01 255)
    transportTrack = Color(0xFFD6DBE1),   // oklch(89% 0.01 255)
    fillStart = Color(0xFF007D7E),        // oklch(52% 0.12 195)
    fillEnd = Color(0xFF009FA0),          // oklch(62% 0.14 195)

    accent = Color(0xFF007E80),           // oklch(52% 0.13 195)
    accentHover = Color(0xFF006C6E),      // oklch(46% 0.13 195)
    onAccent = Color(0xFFF5FEFE),         // oklch(99% 0.01 195)
    subtleBg = Color(0xFFEEF2F7),         // oklch(96% 0.008 255)
    subtleBorder = Color(0xFFC9CED6),     // oklch(85% 0.012 255)
    borderHover = Color(0xFF989FA8),      // oklch(70% 0.016 255)
    outlineBg = Color(0xFFF4F7FB),        // oklch(97.5% 0.006 255)
    checkOffBorder = Color(0xFFB2B8C0),   // oklch(78% 0.014 255)
    segBorder = Color(0xFFD0D5DB),        // oklch(87% 0.01 255)

    previewBg = Color(0xFFE8EBEF),        // oklch(94% 0.006 255)
    previewDivider = Color(0xFFD4D8DD),   // oklch(88% 0.008 255)
    canvasBg = Color(0xFFE2E5E8),         // oklch(92% 0.005 255)
    canvasChecker = Color(0xFFCED1D5),    // oklch(86% 0.006 255)
    badgeBg = Color(0xFFF2F5FB),          // oklch(97% 0.008 255)
    badgeBorder = Color(0xFFD0D5DB),      // oklch(87% 0.01 255)
    liveDot = Color(0xFF299236),          // oklch(58% 0.16 145)

    logoChipBg = Color(0xFFC7EDED),       // oklch(92% 0.04 195)
    logoIcon = Color(0xFF00787A),         // oklch(50% 0.13 195)

    scrollThumb = Color(0xFFBFC5CC),      // oklch(82% 0.012 255)
    scrollThumbHover = Color(0xFF989FA8), // oklch(70% 0.016 255)
)

/**
 * Which palette the surface under composition draws from.
 *
 * Defaults to [DarkPalette] so a composable rendered outside [LottieGenTheme] — a preview, a test
 * that mounts one widget on its own — still looks like the tool rather than like nothing.
 */
val LocalLottieGenPalette = staticCompositionLocalOf { DarkPalette }

/**
 * The design tokens, read through the ambient palette.
 *
 * Colours are composable getters: `Tokens.CardBg` resolves against whichever palette
 * [LottieGenTheme] (or [ProvideLottieGenPalette]) put in scope, so the same call site is dark in a
 * dark host and light in a light one. Shapes and metrics are theme-independent and stay plain
 * `val`s.
 *
 * A colour read from a **non-composable** lambda — a `Canvas { }` draw block, a `pointerInput`
 * handler — will not compile against these getters. Read it into a local in the composable and
 * capture that; `CheckerBoard` in `PreviewPanel.kt` is the worked example.
 */
object Tokens {

    // ── Surfaces ────────────────────────────────────────────────────────────
    val AppBg: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.appBg
    val PanelBg: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.panelBg
    val CardBg: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.cardBg
    val CardBorder: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.cardBorder
    val CardBorderOpen: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.cardBorderOpen
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.divider
    val HeadBgOpen: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.headBgOpen
    val HeadBgHover: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.headBgHover

    // ── Section chrome ──────────────────────────────────────────────────────
    val Tick: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.tick
    val TitleText: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.titleText
    val HintText: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.hintText
    val Caret: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.caret

    // ── Fields ──────────────────────────────────────────────────────────────
    val FieldBg: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.fieldBg
    val FieldBorder: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.fieldBorder
    val FieldBorderHover: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.fieldBorderHover
    val FieldLabel: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.fieldLabel

    // ── Text ────────────────────────────────────────────────────────────────
    val PrimaryText: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.primaryText
    val InputText: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.inputText
    val LabelText: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.labelText
    val ValueText: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.valueText
    val HexText: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.hexText
    val OutlineText: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.outlineText
    val SmallBtnText: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.smallBtnText
    val SegInactive: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.segInactive
    val UnitText: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.unitText
    val DimText: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.dimText
    val Placeholder: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.placeholder

    // ── Sliders ─────────────────────────────────────────────────────────────
    val TrackBg: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.trackBg
    val TransportTrack: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.transportTrack
    val FillStart: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.fillStart
    val FillEnd: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.fillEnd

    // ── Accent / actions ────────────────────────────────────────────────────
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.accent
    val AccentHover: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.accentHover
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.onAccent
    val SubtleBg: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.subtleBg
    val SubtleBorder: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.subtleBorder
    val BorderHover: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.borderHover
    val OutlineBg: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.outlineBg
    val CheckOffBorder: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.checkOffBorder
    val SegBorder: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.segBorder

    // ── Preview ─────────────────────────────────────────────────────────────
    val PreviewBg: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.previewBg
    val PreviewDivider: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.previewDivider
    val CanvasBg: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.canvasBg
    val CanvasChecker: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.canvasChecker
    val BadgeBg: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.badgeBg
    val BadgeBorder: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.badgeBorder
    val LiveDot: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.liveDot

    // ── Branding ────────────────────────────────────────────────────────────
    val LogoChipBg: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.logoChipBg
    val LogoIcon: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.logoIcon

    // ── Scrollbar ───────────────────────────────────────────────────────────
    val ScrollThumb: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.scrollThumb
    val ScrollThumbHover: Color @Composable @ReadOnlyComposable get() = LocalLottieGenPalette.current.scrollThumbHover

    // ── Shape / metrics ─────────────────────────────────────────────────────
    val CardShape = RoundedCornerShape(10.dp)
    val FieldShape = RoundedCornerShape(8.dp)
    val ChipShape = RoundedCornerShape(7.dp)
    val ButtonShape = RoundedCornerShape(9.dp)

    val HeaderHeight = 46.dp
    val SectionHeaderHeight = 36.dp
    val FieldHeight = 44.dp

    /** Letter spacing on the tiny uppercase field labels (0.09em at 9sp). */
    val FieldLabelTracking = 0.81.sp
    /** Letter spacing on section titles (0.03em at 12sp). */
    val SectionTitleTracking = 0.36.sp
}
