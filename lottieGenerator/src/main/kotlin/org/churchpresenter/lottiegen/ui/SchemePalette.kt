package org.churchpresenter.lottiegen.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.lerp

private const val HOVER_SHIFT = 0.12f
private const val DIM_ALPHA = 0.7f
private const val PLACEHOLDER_ALPHA = 0.5f
private const val THUMB_ALPHA = 0.5f

/**
 * The panel chrome's 51 roles read off the host's Material scheme, so the generator embedded in
 * ChurchPresenter is drawn in whichever of the app's nine themes is on rather than in the tool's
 * own teal-on-black. The standalone windows keep [DarkPalette] and [LightPalette]; this is the
 * embedded path only.
 *
 * Surfaces step through Material's container tones — fields on the lowest, cards and menus on
 * the higher ones — and every accent is the scheme's primary.
 */
fun paletteFrom(scheme: ColorScheme): LottieGenPalette = LottieGenPalette(
    appBg = scheme.background,
    panelBg = scheme.surface,
    cardBg = scheme.surfaceContainer,
    cardBorder = scheme.outlineVariant,
    cardBorderOpen = scheme.outline,
    divider = scheme.outlineVariant,
    headBgOpen = scheme.surfaceContainerHigh,
    headBgHover = scheme.surfaceContainerHigh,

    tick = scheme.tertiary,
    titleText = scheme.onSurface,
    hintText = scheme.onSurfaceVariant,
    caret = scheme.onSurfaceVariant,

    fieldBg = scheme.surfaceContainerLowest,
    fieldBorder = scheme.outlineVariant,
    fieldBorderHover = scheme.outline,
    fieldLabel = scheme.onSurfaceVariant,

    primaryText = scheme.onSurface,
    inputText = scheme.onSurface,
    labelText = scheme.onSurfaceVariant,
    valueText = scheme.onSurface,
    hexText = scheme.onSurface,
    outlineText = scheme.onSurface,
    smallBtnText = scheme.onSurfaceVariant,
    segInactive = scheme.onSurfaceVariant,
    unitText = scheme.onSurfaceVariant.copy(alpha = DIM_ALPHA),
    dimText = scheme.onSurfaceVariant.copy(alpha = DIM_ALPHA),
    placeholder = scheme.onSurfaceVariant.copy(alpha = PLACEHOLDER_ALPHA),

    trackBg = scheme.surfaceContainerHighest,
    transportTrack = scheme.surfaceContainerHighest,
    fillStart = scheme.primary,
    fillEnd = scheme.primary,

    accent = scheme.primary,
    accentHover = lerp(scheme.primary, scheme.onPrimary, HOVER_SHIFT),
    onAccent = scheme.onPrimary,
    subtleBg = scheme.surfaceContainerHigh,
    subtleBorder = scheme.outlineVariant,
    borderHover = scheme.outline,
    outlineBg = scheme.surfaceContainer,
    checkOffBorder = scheme.outline,
    segBorder = scheme.outlineVariant,

    previewBg = scheme.surfaceContainerLowest,
    previewDivider = scheme.outlineVariant,
    canvasBg = scheme.surfaceContainerLowest,
    canvasChecker = scheme.surfaceContainerLow,
    badgeBg = scheme.surfaceContainer,
    badgeBorder = scheme.outlineVariant,
    liveDot = scheme.tertiary,

    logoChipBg = scheme.primaryContainer,
    logoIcon = scheme.onPrimaryContainer,

    scrollThumb = scheme.outline.copy(alpha = THUMB_ALPHA),
    scrollThumbHover = scheme.outline,
)
