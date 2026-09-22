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
 * The control pane sits on `surfaceContainer`, which is what the app's settings tabs paint their
 * panes with (measured against the Background tab); fields take `surfaceVariant` like the app's
 * dropdowns, menus and cards `surfaceContainerHighest`, and the preview stage `surfaceVariant`,
 * like that tab's stage area. Every accent is the primary.
 */
fun paletteFrom(scheme: ColorScheme): LottieGenPalette = LottieGenPalette(
    panel = LottieGenPalette.Panel(
        surfaces = LottieGenPalette.Panel.Surfaces(
            appBg = scheme.surfaceContainer,
            panelBg = scheme.surfaceContainer,
            cardBg = scheme.surfaceContainerHighest,
            cardBorder = scheme.outlineVariant,
            cardBorderOpen = scheme.outline,
            divider = scheme.outlineVariant,
        ),
        chrome = LottieGenPalette.Panel.Chrome(
            headBgOpen = scheme.surfaceContainerHighest,
            headBgHover = scheme.surfaceContainerHighest,
            tick = scheme.tertiary,
            titleText = scheme.onSurface,
            hintText = scheme.onSurfaceVariant,
            caret = scheme.onSurfaceVariant,
        ),
        fields = LottieGenPalette.Panel.Fields(
            fieldBg = scheme.surfaceVariant,
            fieldBorder = scheme.outlineVariant,
            fieldBorderHover = scheme.outline,
            fieldLabel = scheme.onSurfaceVariant,
        ),
    ),
    content = LottieGenPalette.Content(
        textPrimary = LottieGenPalette.Content.TextPrimary(
            primaryText = scheme.onSurface,
            inputText = scheme.onSurface,
            labelText = scheme.onSurfaceVariant,
            valueText = scheme.onSurface,
            hexText = scheme.onSurface,
            outlineText = scheme.onSurface,
        ),
        textSecondary = LottieGenPalette.Content.TextSecondary(
            smallBtnText = scheme.onSurfaceVariant,
            segInactive = scheme.onSurfaceVariant,
            unitText = scheme.onSurfaceVariant.copy(alpha = DIM_ALPHA),
            dimText = scheme.onSurfaceVariant.copy(alpha = DIM_ALPHA),
            placeholder = scheme.onSurfaceVariant.copy(alpha = PLACEHOLDER_ALPHA),
        ),
        previewCore = LottieGenPalette.Content.PreviewCore(
            previewBg = scheme.surfaceVariant,
            previewDivider = scheme.outlineVariant,
            canvasBg = scheme.surfaceVariant,
            canvasChecker = scheme.surfaceContainerHigh,
        ),
        previewBadge = LottieGenPalette.Content.PreviewBadge(
            badgeBg = scheme.surfaceContainerHigh,
            badgeBorder = scheme.outlineVariant,
            liveDot = scheme.tertiary,
        ),
    ),
    // Tracks sit on the pane and on the popover card alike, so they take the outline tone rather
    // than a surface one that a card could share.
    interaction = LottieGenPalette.Interaction(
        sliders = LottieGenPalette.Interaction.Sliders(
            trackBg = scheme.outlineVariant,
            transportTrack = scheme.outlineVariant,
            fillStart = scheme.primary,
            fillEnd = scheme.primary,
        ),
        accent = LottieGenPalette.Interaction.Accent(
            accent = scheme.primary,
            accentHover = lerp(scheme.primary, scheme.onPrimary, HOVER_SHIFT),
            onAccent = scheme.onPrimary,
            subtleBg = scheme.surfaceContainerHigh,
            subtleBorder = scheme.outlineVariant,
            borderHover = scheme.outline,
        ),
        actionBorders = LottieGenPalette.Interaction.ActionBorders(
            outlineBg = scheme.surfaceContainer,
            checkOffBorder = scheme.outline,
            segBorder = scheme.outlineVariant,
        ),
        branding = LottieGenPalette.Interaction.Branding(
            logoChipBg = scheme.primaryContainer,
            logoIcon = scheme.onPrimaryContainer,
        ),
        scrollbar = LottieGenPalette.Interaction.Scrollbar(
            scrollThumb = scheme.outline.copy(alpha = THUMB_ALPHA),
            scrollThumbHover = scheme.outline,
        ),
    ),
)
