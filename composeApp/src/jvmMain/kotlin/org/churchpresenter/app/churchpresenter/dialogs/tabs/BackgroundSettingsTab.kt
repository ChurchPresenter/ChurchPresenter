package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import org.churchpresenter.app.churchpresenter.utils.presenterAspectRatio
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.atem_upload_background_1_tooltip
import churchpresenter.composeapp.generated.resources.atem_upload_background_2_tooltip
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.ok
import churchpresenter.composeapp.generated.resources.background_color_option
import churchpresenter.composeapp.generated.resources.background_default
import churchpresenter.composeapp.generated.resources.background_follow_default_option
import churchpresenter.composeapp.generated.resources.background_follows_default
import churchpresenter.composeapp.generated.resources.background_following_default
import churchpresenter.composeapp.generated.resources.background_group_defaults
import churchpresenter.composeapp.generated.resources.background_image_option
import churchpresenter.composeapp.generated.resources.background_scope_default
import churchpresenter.composeapp.generated.resources.background_scope_default_lower_third
import churchpresenter.composeapp.generated.resources.background_scope_default_lower_third_meta
import churchpresenter.composeapp.generated.resources.background_scope_default_meta
import churchpresenter.composeapp.generated.resources.background_scope_title
import churchpresenter.composeapp.generated.resources.background_set_explicitly
import churchpresenter.composeapp.generated.resources.background_surfaces
import churchpresenter.composeapp.generated.resources.background_transparent_option
import churchpresenter.composeapp.generated.resources.background_use_default
import churchpresenter.composeapp.generated.resources.background_video_option
import churchpresenter.composeapp.generated.resources.bible
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.full_screen
import churchpresenter.composeapp.generated.resources.gradient_enabled
import churchpresenter.composeapp.generated.resources.quick_background_add
import churchpresenter.composeapp.generated.resources.quick_backgrounds
import churchpresenter.composeapp.generated.resources.quick_backgrounds_help
import churchpresenter.composeapp.generated.resources.remove
import churchpresenter.composeapp.generated.resources.song_background_sample_line
import churchpresenter.composeapp.generated.resources.songs
import churchpresenter.composeapp.generated.resources.stock_library_tooltip
import churchpresenter.composeapp.generated.resources.stock_photo_browse_tooltip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.composables.BackgroundConfigFill
import org.churchpresenter.app.churchpresenter.presenter.BACKGROUND_REFERENCE_WIDTH
import org.churchpresenter.app.churchpresenter.presenter.backgroundBlurRadius
import org.churchpresenter.app.churchpresenter.composables.FileImagePicker
import org.churchpresenter.app.churchpresenter.composables.FileVideoPicker
import org.churchpresenter.app.churchpresenter.composables.QUICK_BACKGROUND_SLOTS
import org.churchpresenter.app.churchpresenter.composables.TvScreenBox
import org.churchpresenter.app.churchpresenter.composables.newQuickBackground
import org.churchpresenter.app.churchpresenter.composables.quickBackgroundLabel
import org.churchpresenter.app.churchpresenter.data.StockMediaClient
import org.churchpresenter.app.churchpresenter.dialogs.LocalLibraryDialog
import org.churchpresenter.app.churchpresenter.dialogs.PanelCaption
import org.churchpresenter.app.churchpresenter.dialogs.SONG_BACKGROUND_PANEL_HEIGHT
import org.churchpresenter.app.churchpresenter.dialogs.SONG_BACKGROUND_PANEL_WIDTH
import org.churchpresenter.app.churchpresenter.dialogs.SongBackgroundFill
import org.churchpresenter.app.churchpresenter.dialogs.SongBackgroundPanel
import org.churchpresenter.app.churchpresenter.dialogs.SongBackgroundPanelPosition
import org.churchpresenter.app.churchpresenter.dialogs.StockMediaBrowserDialog
import org.churchpresenter.app.churchpresenter.viewmodel.BackgroundSettingsViewModel
import org.churchpresenter.atem.AtemClient
import org.churchpresenter.atem.AtemFrameEncoder
import org.churchpresenter.atem.AtemUploadStatus
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.AtemSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.QuickBackground
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.math.roundToInt

private const val PREVIEW_DEBOUNCE_MS = 800L


/**
 * Settings → Background: every surface the app draws a background on, one at a time.
 *
 * The rail down the left lists all six — the two Defaults, then Bible and Songs in their
 * full-screen and lower-third shapes — each with a chip showing what that surface actually
 * projects, inheritance followed. Picking one opens it in the editor beside the rail: what it is,
 * how it looks, and a preview of the output it produces. The quick tray's own backgrounds sit
 * under that preview, which is where the operator meets them.
 *
 * The four cards this replaced showed the same six surfaces at once, each with its own type
 * dropdown and its own set of sliders, and none of them showed what the surface would project.
 */

@Composable
fun BackgroundSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val viewModel = remember { BackgroundSettingsViewModel() }
    var scope by remember { mutableStateOf(BackgroundScope.DEFAULT) }
    val backgrounds = settings.backgroundSettings
    // The band the presenters actually draw, from Projection settings — a preview that assumed a
    // third would disagree with the output and with the desktop tab's live preview.
    val bandFraction = settings.projectionSettings.lowerThirdHeightPercent / PERCENT
    val onConfigChange: (BackgroundConfig) -> Unit = { config ->
        viewModel.updateBackground(scope, config, onSettingsChange)
    }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        BackgroundScopeRail(
            backgrounds = backgrounds,
            selected = scope,
            bandFraction = bandFraction,
            onSelect = { scope = it },
            modifier = Modifier.width(SCOPE_RAIL_WIDTH).fillMaxHeight()
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            BackgroundEditorHeader(
                scope = scope,
                config = backgrounds.configFor(scope),
                onConfigChange = onConfigChange
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                BackgroundControlsColumn(
                    scope = scope,
                    settings = settings,
                    onConfigChange = onConfigChange,
                    onSettingsChange = onSettingsChange,
                    modifier = Modifier.width(CONTROLS_WIDTH).fillMaxHeight()
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    BackgroundStagePreview(
                        config = backgrounds.resolvedConfigFor(scope),
                        coverage = scope.coverage,
                        bandFraction = bandFraction,
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(14.dp)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    QuickBackgroundsRail(settings = settings, onSettingsChange = onSettingsChange)
                }
            }
        }
    }
}

// ── The surface rail ─────────────────────────────────────────────────────────────────────────

/**
 * Every surface the tab can edit, grouped the way an operator thinks of them: the two the others
 * fall through to, then the content types that fall through.
 */
@Composable
private fun BackgroundScopeRail(
    backgrounds: BackgroundSettings,
    selected: BackgroundScope,
    bandFraction: Float,
    onSelect: (BackgroundScope) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(SECTION_HEADER_HEIGHT).padding(horizontal = 11.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            PanelCaption(stringResource(Res.string.background_surfaces))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            BackgroundScopeGroup.entries.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    PanelCaption(
                        text = stringResource(backgroundGroupLabel(group)),
                        modifier = Modifier.padding(start = 3.dp, bottom = 2.dp)
                    )
                    BackgroundScope.entries.filter { it.group == group }.forEach { scope ->
                        BackgroundScopeRow(
                            scope = scope,
                            backgrounds = backgrounds,
                            selected = scope == selected,
                            bandFraction = bandFraction,
                            onClick = { onSelect(scope) }
                        )
                    }
                }
            }
        }
    }
}

/** One surface: what it projects, what it is called, and whether it was set here or inherited. */
@Composable
private fun BackgroundScopeRow(
    scope: BackgroundScope,
    backgrounds: BackgroundSettings,
    selected: Boolean,
    bandFraction: Float,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_TINT_ALPHA)
                else Color.Transparent
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = SCOPE_CHIP_WIDTH, height = SCOPE_CHIP_HEIGHT)
                .clip(RoundedCornerShape(5.dp))
                // Black under the fill, so the part of the screen this surface does not paint
                // reads as unpainted output rather than as the rail showing through.
                .background(Color.Black)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))
        ) {
            BackgroundCoverageFill(
                config = backgrounds.resolvedConfigFor(scope),
                coverage = scope.coverage,
                bandFraction = bandFraction,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = stringResource(backgroundScopeName(scope)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = backgroundScopeMeta(scope, backgrounds),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (backgrounds.isSetExplicitly(scope)) {
            Box(
                Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary)
            )
        }
    }
}

/** The rail's second line: where the surface gets its background from, in the operator's words. */
@Composable
private fun backgroundScopeMeta(scope: BackgroundScope, backgrounds: BackgroundSettings): String {
    val own = backgrounds.configFor(scope).backgroundType
    return when {
        scope == BackgroundScope.DEFAULT -> stringResource(Res.string.background_scope_default_meta)
        scope == BackgroundScope.DEFAULT_LOWER_THIRD && own == scope.inheritType ->
            stringResource(Res.string.background_follows_default)
        scope == BackgroundScope.DEFAULT_LOWER_THIRD ->
            stringResource(Res.string.background_scope_default_lower_third_meta)
        own == scope.inheritType -> stringResource(Res.string.background_follows_default)
        else -> stringResource(backgroundTypeLabel(own))
    }
}

// ── The editor header ────────────────────────────────────────────────────────────────────────

/** Which surface is open, and the one button that takes it back to inheriting. */
@Composable
private fun BackgroundEditorHeader(
    scope: BackgroundScope,
    config: BackgroundConfig,
    onConfigChange: (BackgroundConfig) -> Unit
) {
    val inheritType = scope.inheritType
    val inheriting = inheritType != null && config.backgroundType == inheritType
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EDITOR_HEADER_HEIGHT)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = backgroundScopeTitle(scope),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (inheriting) stringResource(Res.string.background_follows_default)
                       else stringResource(Res.string.background_set_explicitly),
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (inheritType != null) {
            InheritToggleButton(
                inheriting = inheriting,
                onClick = {
                    onConfigChange(
                        config.copy(
                            backgroundType =
                                if (inheriting) Constants.BACKGROUND_COLOR else inheritType,
                            gradientEnabled = false
                        )
                    )
                }
            )
        }
    }
}

/** "Use Default" while the surface has its own look; "Following Default" once it does not. */
@Composable
private fun InheritToggleButton(inheriting: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(27.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (inheriting) MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_TINT_ALPHA)
                else Color.Transparent
            )
            .border(
                1.dp,
                if (inheriting) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (inheriting) stringResource(Res.string.background_following_default)
                   else stringResource(Res.string.background_use_default),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = if (inheriting) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── The stage preview ────────────────────────────────────────────────────────────────────────

/**
 * The output, as this surface will draw it: the resolved background faded, blurred and dimmed the
 * way the presenter does it, with the lower-third band drawn in when that is what is being edited.
 *
 * The blur radius is stored against a 1920-wide output, so it is scaled to whatever width the
 * preview ends up with — exactly what the presenters do with the same number.
 */
@Composable
private fun BackgroundStagePreview(
    config: BackgroundConfig,
    coverage: BackgroundCoverage,
    bandFraction: Float,
    modifier: Modifier = Modifier
) {
    // The shape of the screen this will actually go out on, not a fixed 16:10 guess — the band is
    // a percentage of the height, so getting the height wrong moves the band and everything in it.
    val stageAspect = remember { presenterAspectRatio() }
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // Sized here rather than with an aspect-ratio modifier: the set has to fit the column in
        // both directions at once, and stop growing once it is big enough to read.
        val width = minOf(maxWidth, STAGE_MAX_WIDTH, maxHeight * stageAspect)
        // How much smaller this stage is than the 1920-wide output every stored size is measured
        // against. The blur goes through the presenters' own helper so there is one definition of
        // it; the sample line below is scaled by the same factor.
        val stageScale = width.value / BACKGROUND_REFERENCE_WIDTH
        TvScreenBox(
            modifier = Modifier.width(width).height(width / stageAspect),
            bezelColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            screenColor = Color.Black
        ) {
            BackgroundCoverageFill(
                config = config,
                coverage = coverage,
                bandFraction = bandFraction,
                // Clipped to TvScreenBox's own screen corner radius. Without it a picture's square
                // corners poke past the rounded border, and a blurred one — overscanned 8% the way
                // the presenter overscans it — spills out over the bezel entirely.
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(TV_SCREEN_RADIUS)),
                blurRadius = backgroundBlurRadius(config.blur, width)
            )
            Text(
                text = stringResource(Res.string.song_background_sample_line),
                // Sized off the output's own default rather than a theme style: a line set in
                // bodyMedium is the dialog's idea of body text, which on a band a tenth of the
                // screen tall comes out several times the size the output draws it at.
                fontSize = (
                    if (coverage == BackgroundCoverage.FULL_SCREEN) SAMPLE_FULL_SCREEN_SIZE
                    else SAMPLE_LOWER_THIRD_SIZE
                    ) * stageScale,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    // By the surface, not by the area it paints. Every lower-third surface puts
                    // its text in the band, the default lower third included — that one paints
                    // above the band but the output it belongs to still reads from the bottom.
                    .align(
                        if (coverage == BackgroundCoverage.FULL_SCREEN) Alignment.Center
                        else Alignment.BottomCenter
                    )
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            )
            Text(
                text = stringResource(
                    if (coverage == BackgroundCoverage.FULL_SCREEN) Res.string.full_screen
                    else Res.string.display_lower_third
                ).uppercase(),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
                color = Color.White.copy(alpha = STAGE_BADGE_ALPHA),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = STAGE_BADGE_SCRIM_ALPHA))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * A background drawn over exactly the part of the output its surface paints, with the rest of the
 * screen left dark and a hairline showing where the lower-third band starts.
 *
 * A content type's lower-third background *is* the band; the default lower-third background is
 * what the band is drawn over. Filling the whole screen for either would show the operator a look
 * the output never produces — which is what the four coverage badges on the old cards were for.
 */
@Composable
private fun BackgroundCoverageFill(
    config: BackgroundConfig,
    coverage: BackgroundCoverage,
    bandFraction: Float,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 0.dp
) {
    Column(modifier) {
        if (coverage == BackgroundCoverage.BAND) {
            Box(Modifier.fillMaxWidth().weight(1f - bandFraction))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(
                    when (coverage) {
                        BackgroundCoverage.FULL_SCREEN -> 1f
                        BackgroundCoverage.ABOVE_BAND -> 1f - bandFraction
                        BackgroundCoverage.BAND -> bandFraction
                    }
                )
        ) {
            BackgroundConfigFill(config, Modifier.fillMaxSize(), blurRadius)
            if (config.dim > 0) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = config.dim / PERCENT)))
            }
            // Drawn over the fill rather than between the two boxes: a divider in the layout would
            // take a device-independent pixel out of the weights, and the band would come out
            // fractionally short of the percentage the presenters draw it at.
            if (coverage != BackgroundCoverage.FULL_SCREEN) {
                Box(
                    Modifier
                        .align(
                            if (coverage == BackgroundCoverage.BAND) Alignment.TopCenter
                            else Alignment.BottomCenter
                        )
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        if (coverage == BackgroundCoverage.ABOVE_BAND) {
            Box(Modifier.fillMaxWidth().weight(bandFraction))
        }
    }
}

// ── Labels ───────────────────────────────────────────────────────────────────────────────────

internal fun backgroundGroupLabel(group: BackgroundScopeGroup): StringResource = when (group) {
    BackgroundScopeGroup.DEFAULTS -> Res.string.background_group_defaults
    BackgroundScopeGroup.BIBLE -> Res.string.bible
    BackgroundScopeGroup.SONGS -> Res.string.songs
}

private fun backgroundScopeName(scope: BackgroundScope): StringResource = when (scope) {
    BackgroundScope.DEFAULT -> Res.string.background_scope_default
    BackgroundScope.DEFAULT_LOWER_THIRD -> Res.string.background_scope_default_lower_third
    else -> if (scope.lowerThird) Res.string.display_lower_third else Res.string.full_screen
}

/** "Bible · Lower Third" for a content surface; a Default surface stands on its own name. */
@Composable
private fun backgroundScopeTitle(scope: BackgroundScope): String =
    if (scope.group == BackgroundScopeGroup.DEFAULTS) stringResource(backgroundScopeName(scope))
    else stringResource(
        Res.string.background_scope_title,
        stringResource(backgroundGroupLabel(scope.group)),
        stringResource(backgroundScopeName(scope))
    )

internal fun backgroundTypeLabel(type: String): StringResource = when (type) {
    Constants.BACKGROUND_COLOR -> Res.string.background_color_option
    Constants.BACKGROUND_IMAGE -> Res.string.background_image_option
    Constants.BACKGROUND_VIDEO -> Res.string.background_video_option
    Constants.BACKGROUND_TRANSPARENT -> Res.string.background_transparent_option
    Constants.BACKGROUND_GRADIENT -> Res.string.gradient_enabled
    Constants.BACKGROUND_FOLLOW_DEFAULT -> Res.string.background_follow_default_option
    else -> Res.string.background_default
}

/** The panel's own tooltip, for a control that is present but cannot be used. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HintTooltip(hint: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = hint,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp)
        )
    ) {
        content()
    }
}

// ── The quick tray's shelf ───────────────────────────────────────────────────────────────────

/**
 * The backgrounds the preview panel's quick tray keeps one click away, under the preview because
 * that is where they are used from.
 *
 * The tray is a live control that writes nothing, so this is the only place a quick background is
 * added, edited, reordered or removed. A tile opens the very panel a song's own background is
 * edited in — the same swatch library, the same Look presets, the same dim and blur — because a
 * quick background *is* one of those, just kept on a shelf instead of inside a song.
 */
@Composable
private fun QuickBackgroundsRail(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit
) {
    val entries = settings.quickBackgrounds
    var openId by remember { mutableStateOf<String?>(null) }
    // A removal, or a settings import, can take the open entry away underneath the panel.
    LaunchedEffect(entries.map { it.id }) {
        if (entries.none { it.id == openId }) openId = null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PanelCaption(stringResource(Res.string.quick_backgrounds))
            Text(
                text = "${entries.size} / $QUICK_BACKGROUND_SLOTS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
        Text(
            text = stringResource(Res.string.quick_backgrounds_help),
            fontSize = 10.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        QuickBackgroundStrip(
            entries = entries,
            openId = openId,
            onOpenChange = { openId = it },
            onAdd = {
                val added = newQuickBackground()
                openId = added.id
                onSettingsChange { s -> s.copy(quickBackgrounds = s.quickBackgrounds + added) }
            },
            onChange = { updated ->
                onSettingsChange { s ->
                    s.copy(quickBackgrounds = s.quickBackgrounds.map { if (it.id == updated.id) updated else it })
                }
            },
            onRemove = { id ->
                if (openId == id) openId = null
                onSettingsChange { s -> s.copy(quickBackgrounds = s.quickBackgrounds.filterNot { it.id == id }) }
            },
            onReorder = { from, to ->
                onSettingsChange { s -> s.copy(quickBackgrounds = s.quickBackgrounds.moved(from, to)) }
            }
        )
    }
}

private val SCOPE_RAIL_WIDTH = 232.dp
private val CONTROLS_WIDTH = 336.dp
private val SECTION_HEADER_HEIGHT = 30.dp
private val EDITOR_HEADER_HEIGHT = 46.dp
private val SCOPE_CHIP_WIDTH = 32.dp
private val SCOPE_CHIP_HEIGHT = 20.dp

/** How strongly a selected row or an engaged toggle is washed with the theme's primary. */
private const val SELECTED_TINT_ALPHA = 0.14f

/** The width past which the TV stops growing; its shape comes from the presenter screen. */
private val STAGE_MAX_WIDTH = 620.dp

/**
 * The sample line's size at full output width, from `SongSettings.lyricsFontSize` and
 * `lyricsLowerThirdFontSize` — the sizes a song is actually drawn at, scaled down with the stage.
 */
private val SAMPLE_FULL_SCREEN_SIZE = 70.sp
private val SAMPLE_LOWER_THIRD_SIZE = 28.sp

/** [TvScreenBox] rounds its screen by this much; a fill drawn in it has to be cut to the same. */
private val TV_SCREEN_RADIUS = 4.dp
private const val STAGE_BADGE_ALPHA = 0.9f
private const val STAGE_BADGE_SCRIM_ALPHA = 0.55f

/** A percentage as a fraction. */
private const val PERCENT = 100f

// ── Pickers and ATEM uploads ──────────────────────────────────────────────

/**
 * Scales [src] to cover a [dw]×[dh] box (uniform scale by the larger of the two axis ratios,
 * so the result never falls short of either dimension) then crops the centered overflow — no
 * distortion, unlike a plain non-uniform stretch to the exact target size.
 */
private fun coverCropArgb(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
    val srcImg = BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB)
    srcImg.setRGB(0, 0, sw, sh, src, 0, sw)
    val scale = maxOf(dw.toDouble() / sw, dh.toDouble() / sh)
    val scaledW = (sw * scale).roundToInt().coerceAtLeast(dw)
    val scaledH = (sh * scale).roundToInt().coerceAtLeast(dh)
    val scaledImg = BufferedImage(scaledW, scaledH, BufferedImage.TYPE_INT_ARGB)
    val g = scaledImg.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(srcImg, 0, 0, scaledW, scaledH, null)
    } finally {
        g.dispose()
    }
    val cropX = (scaledW - dw) / 2
    val cropY = (scaledH - dh) / 2
    val dst = IntArray(dw * dh)
    scaledImg.getRGB(cropX, cropY, dw, dh, dst, 0, dw)
    return dst
}

/**
 * Uploads [imagePath] as a single still frame to the ATEM media pool, into [slot] — one of the
 * two background slots ([AtemSettings.backgroundSlot1]/[AtemSettings.backgroundSlot2]), separate from the
 * lower-third still/clip slots uploaded from [org.churchpresenter.app.churchpresenter.tabs.LowerThird].
 * Publishes progress through the shared [AtemUploadStatus] so it's visible anywhere that already
 * observes it (e.g. the Lower Third tab's upload bar, if open).
 */
private suspend fun uploadBackgroundToAtem(atemSettings: AtemSettings, imagePath: String, slot: Int) {
    val file = File(imagePath)
    val name = file.nameWithoutExtension
    val argb = withContext(Dispatchers.IO) {
        val img = ImageIO.read(file) ?: throw IOException("Could not read image file")
        val w = atemSettings.renderWidth
        val h = atemSettings.renderHeight
        val src = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, src, 0, img.width)
        if (img.width == w && img.height == h) src
        else coverCropArgb(src, img.width, img.height, w, h)
    }
    val frame = withContext(Dispatchers.IO) {
        AtemFrameEncoder.encodeFrame(atemSettings.renderWidth, atemSettings.renderHeight, argb)
    }
    val id = AtemUploadStatus.begin(name, clip = false, slot + 1)
    try {
        val client = AtemClient(atemSettings.host, atemSettings.port)
        withContext(Dispatchers.IO) { client.connect() }
        try {
            client.uploadStillEncoded(slot, frame, name) { p -> AtemUploadStatus.progress(id, p) }
        } finally {
            client.disconnect()
        }
        AtemUploadStatus.complete(id)
        delay(PREVIEW_DEBOUNCE_MS)
        AtemUploadStatus.clear(id)
    } catch (e: Exception) {
        AtemUploadStatus.fail(id, e.message)
        throw e
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TooltipIconButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit
) {
    TooltipArea(
        tooltip = {
            Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                Text(
                    text = tooltip,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = tooltip)
        }
    }
}

/**
 * One of the two independent "upload background to ATEM slot N" buttons in [ImagePickerRow].
 * Each carries its own busy/error state so clicking one never disables or affects the other —
 * an operator can push the same image to both background slots back to back.
 */
@Composable
private fun AtemUploadIconButton(
    badge: String,
    tooltip: String,
    imagePath: String,
    atemSettings: AtemSettings,
    slot: Int
) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    if (busy) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
    } else {
        Box {
            TooltipIconButton(Icons.Default.CloudUpload, error ?: tooltip) {
                error = null
                busy = true
                scope.launch {
                    try {
                        uploadBackgroundToAtem(atemSettings, imagePath, slot)
                    } catch (e: Exception) {
                        error = e.message ?: tooltip
                    } finally {
                        busy = false
                    }
                }
            }
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 4.dp, end = 4.dp)
                    .size(12.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

@Composable
internal fun ImagePickerRow(
    imagePath: String,
    onImagePathChange: (String) -> Unit,
    pexelsApiKey: String,
    onPexelsApiKeyChange: (String) -> Unit,
    pixabayApiKey: String,
    onPixabayApiKeyChange: (String) -> Unit,
    atemSettings: AtemSettings = AtemSettings(),
    modifier: Modifier = Modifier
) {
    var showBrowser by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    val browseTooltip = stringResource(Res.string.stock_photo_browse_tooltip)
    val libraryTooltip = stringResource(Res.string.stock_library_tooltip)
    val uploadTooltip1 = stringResource(Res.string.atem_upload_background_1_tooltip)
    val uploadTooltip2 = stringResource(Res.string.atem_upload_background_2_tooltip)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FileImagePicker(
            imagePath = imagePath,
            onImagePathChange = onImagePathChange,
            modifier = Modifier.weight(1f)
        )
        TooltipIconButton(Icons.Default.PhotoLibrary, libraryTooltip) { showLibrary = true }
        TooltipIconButton(Icons.Default.Search, browseTooltip) { showBrowser = true }
        if (atemSettings.host.isNotBlank() && imagePath.isNotBlank()) {
            AtemUploadIconButton(
                badge = "1",
                tooltip = uploadTooltip1,
                imagePath = imagePath,
                atemSettings = atemSettings,
                slot = atemSettings.backgroundSlot1
            )
            AtemUploadIconButton(
                badge = "2",
                tooltip = uploadTooltip2,
                imagePath = imagePath,
                atemSettings = atemSettings,
                slot = atemSettings.backgroundSlot2
            )
        }
    }
    if (showBrowser) {
        StockMediaBrowserDialog(
            mediaType = StockMediaClient.StockMediaType.PHOTO,
            pexelsApiKey = pexelsApiKey,
            onPexelsApiKeyChange = onPexelsApiKeyChange,
            pixabayApiKey = pixabayApiKey,
            onPixabayApiKeyChange = onPixabayApiKeyChange,
            onDismiss = { showBrowser = false },
            onMediaDownloaded = onImagePathChange
        )
    }
    if (showLibrary) {
        LocalLibraryDialog(
            mediaType = StockMediaClient.StockMediaType.PHOTO,
            onDismiss = { showLibrary = false },
            onMediaSelected = onImagePathChange
        )
    }
}

@Composable
internal fun VideoPickerRow(
    videoPath: String,
    onVideoPathChange: (String) -> Unit,
    pexelsApiKey: String,
    onPexelsApiKeyChange: (String) -> Unit,
    pixabayApiKey: String,
    onPixabayApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBrowser by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    val browseTooltip = stringResource(Res.string.stock_photo_browse_tooltip)
    val libraryTooltip = stringResource(Res.string.stock_library_tooltip)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FileVideoPicker(
            videoPath = videoPath,
            onVideoPathChange = onVideoPathChange,
            modifier = Modifier.weight(1f)
        )
        TooltipIconButton(Icons.Default.PhotoLibrary, libraryTooltip) { showLibrary = true }
        TooltipIconButton(Icons.Default.Search, browseTooltip) { showBrowser = true }
    }
    if (showBrowser) {
        StockMediaBrowserDialog(
            mediaType = StockMediaClient.StockMediaType.VIDEO,
            pexelsApiKey = pexelsApiKey,
            onPexelsApiKeyChange = onPexelsApiKeyChange,
            pixabayApiKey = pixabayApiKey,
            onPixabayApiKeyChange = onPixabayApiKeyChange,
            onDismiss = { showBrowser = false },
            onMediaDownloaded = onVideoPathChange
        )
    }
    if (showLibrary) {
        LocalLibraryDialog(
            mediaType = StockMediaClient.StockMediaType.VIDEO,
            onDismiss = { showLibrary = false },
            onMediaSelected = onVideoPathChange
        )
    }
}

/**
 * The tray as the operator will see it, in the order it will show it — and where that order is set.
 *
 * The strip *is* the tray, so the slot numbers are on the tiles here too: the first tile is the one
 * Ctrl+1 reaches, and dragging a tile along the strip is how that changes.
 */
@Composable
private fun QuickBackgroundStrip(
    entries: List<QuickBackground>,
    openId: String?,
    onOpenChange: (String?) -> Unit,
    onAdd: () -> Unit,
    onChange: (QuickBackground) -> Unit,
    onRemove: (String) -> Unit,
    onReorder: (Int, Int) -> Unit
) {
    val stridePx = with(LocalDensity.current) { (QUICK_STRIP_TILE_WIDTH + QUICK_STRIP_GAP).toPx() }
    var draggedIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    val dropIndex = dropIndexFor(draggedIndex, dragOffset, stridePx, entries.lastIndex)

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(QUICK_STRIP_GAP),
        verticalAlignment = Alignment.Top
    ) {
        entries.forEachIndexed { index, entry ->
            QuickBackgroundStripTile(
                entry = entry,
                slot = index + 1,
                open = entry.id == openId,
                dragging = index == draggedIndex,
                onOpenChange = { open -> onOpenChange(if (open) entry.id else null) },
                onChange = onChange,
                onRemove = { onRemove(entry.id) },
                modifier = Modifier
                    .zIndex(if (index == draggedIndex) 1f else 0f)
                    .graphicsLayer {
                        translationX = tileShift(index, draggedIndex, dropIndex, dragOffset, stridePx)
                    }
                    .pointerInput(entries.size, index) {
                        detectDragGestures(
                            onDragStart = {
                                draggedIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.x
                            },
                            onDragEnd = {
                                val to = dropIndexFor(index, dragOffset, stridePx, entries.lastIndex)
                                if (to != index) onReorder(index, to)
                                draggedIndex = -1
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggedIndex = -1
                                dragOffset = 0f
                            }
                        )
                    }
            )
        }
        if (entries.size < QUICK_BACKGROUND_SLOTS) {
            QuickBackgroundAddTile(onClick = onAdd)
        }
    }
}

/** One tile in the strip, and the panel it opens. */
@Composable
private fun QuickBackgroundStripTile(
    entry: QuickBackground,
    slot: Int,
    open: Boolean,
    dragging: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onChange: (QuickBackground) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    // What the panel is editing, kept aside until OK. A tile is a saved thing, so abandoning an
    // edit has to put it back exactly — which the tab's own buffer cannot do on its own, since it
    // only knows the whole settings object and not which tile was being played with.
    var draft by remember(open, entry.id) { mutableStateOf(entry) }
    val shown = if (open) draft else entry

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.width(QUICK_STRIP_TILE_WIDTH),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(QUICK_TILE_ASPECT)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { onOpenChange(!open) }
                    .border(
                        width = 2.dp,
                        color = when {
                            open -> MaterialTheme.colorScheme.primary
                            dragging -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = RoundedCornerShape(7.dp)
                    )
            ) {
                SongBackgroundFill(shown.background, Modifier.fillMaxSize())
                SlotChip(slot, Modifier.align(Alignment.TopStart).padding(3.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = QUICK_SCRIM_ALPHA))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.remove),
                        modifier = Modifier.size(9.dp),
                        tint = Color.White
                    )
                }
            }
            Text(
                text = quickBackgroundLabel(shown),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (open) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (open) {
            Popup(
                popupPositionProvider = remember { SongBackgroundPanelPosition },
                // Dismissing by any route other than OK is a cancel: the draft is dropped.
                onDismissRequest = { onOpenChange(false) },
                properties = PopupProperties(focusable = true)
            ) {
                Box(Modifier.size(SONG_BACKGROUND_PANEL_WIDTH, SONG_BACKGROUND_PANEL_HEIGHT)) {
                    SongBackgroundPanel(
                        background = draft.background,
                        lowerThirdBackground = draft.lowerThirdBackground,
                        onBackgroundChange = { draft = draft.copy(background = it) },
                        onLowerThirdBackgroundChange = { draft = draft.copy(lowerThirdBackground = it) },
                        sampleLine = stringResource(Res.string.song_background_sample_line),
                        // A song book is a song idea; a tray tile belongs to no book.
                        onApplyToSongbook = null,
                        onDismiss = { onOpenChange(false) },
                        allowInherit = false,
                        footer = {
                            QuickBackgroundPanelFooter(
                                onCancel = { onOpenChange(false) },
                                onConfirm = {
                                    onChange(draft)
                                    onOpenChange(false)
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

/** OK and Cancel for a tray tile's panel — the tile is a saved setting, so an edit can be dropped. */
@Composable
private fun QuickBackgroundPanelFooter(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            shape = RoundedCornerShape(6.dp),
            onClick = onCancel,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Text(stringResource(Res.string.cancel))
        }
        Spacer(Modifier.width(8.dp))
        Button(shape = RoundedCornerShape(6.dp), onClick = onConfirm) {
            Text(stringResource(Res.string.ok))
        }
    }
}

/** The trailing tile: where a background gets into the tray in the first place. */
@Composable
private fun QuickBackgroundAddTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(QUICK_STRIP_TILE_WIDTH),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(QUICK_TILE_ASPECT)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(7.dp))
                .clickable(onClick = onClick)
                .testTag(QUICK_BACKGROUND_ADD_TAG),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(Res.string.quick_background_add),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The slot number, on a scrim of its own so it reads over a white background and a black one. */
@Composable
private fun SlotChip(slot: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = QUICK_SCRIM_ALPHA))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(text = slot.toString(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/**
 * Where the tile being dragged would land if it were dropped now, or [dragged] when nothing moves.
 *
 * Pulled out of the composable so the drag maths is one expression both the live offsets and the
 * drop itself are computed from — the tiles shifting under the cursor and where the entry actually
 * ends up cannot disagree.
 */
internal fun dropIndexFor(dragged: Int, offset: Float, stridePx: Float, lastIndex: Int): Int {
    if (dragged < 0 || lastIndex < 0 || stridePx <= 0f) return dragged
    return (dragged + (offset / stridePx).roundToInt()).coerceIn(0, lastIndex)
}

/** How far the tile at [index] slides while a drag is in flight, to open a gap at the drop point. */
internal fun tileShift(index: Int, dragged: Int, drop: Int, offset: Float, stridePx: Float): Float = when {
    dragged < 0 -> 0f
    index == dragged -> offset
    dragged < drop && index in (dragged + 1)..drop -> -stridePx
    dragged > drop && index in drop until dragged -> stridePx
    else -> 0f
}

/** [this] with the item at [from] moved to [to], or [this] unchanged when that is a no-op. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

internal const val QUICK_BACKGROUND_ADD_TAG = "quick_background_add"
private val QUICK_STRIP_TILE_WIDTH = 92.dp
private val QUICK_STRIP_GAP = 8.dp
private const val QUICK_TILE_ASPECT = 16f / 10f
private const val QUICK_SCRIM_ALPHA = 0.45f
