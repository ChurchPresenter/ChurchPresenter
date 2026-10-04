package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.composables.tvScreenBoxWidthFor
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.background_follows_default
import org.churchpresenter.strings.generated.resources.background_following_default
import org.churchpresenter.strings.generated.resources.background_scope_default_lower_third_meta
import org.churchpresenter.strings.generated.resources.background_scope_default_meta
import org.churchpresenter.strings.generated.resources.background_set_explicitly
import org.churchpresenter.strings.generated.resources.background_surfaces
import org.churchpresenter.strings.generated.resources.background_use_default
import org.churchpresenter.strings.generated.resources.display_lower_third
import org.churchpresenter.strings.generated.resources.full_screen
import org.churchpresenter.strings.generated.resources.song_background_sample_line
import org.churchpresenter.app.churchpresenter.composables.BackgroundConfigFill
import org.churchpresenter.sharedui.presenter.BACKGROUND_REFERENCE_WIDTH
import org.churchpresenter.presenter.BibleLottieStillFrame
import org.churchpresenter.presenter.resolveAboveBand
import org.churchpresenter.presenter.backgroundBlurRadius
import org.churchpresenter.app.churchpresenter.composables.PreviewOutputPicker
import org.churchpresenter.app.churchpresenter.composables.TvScreenBox
import org.churchpresenter.app.churchpresenter.composables.rememberPreviewOutput
import org.churchpresenter.app.churchpresenter.dialogs.PanelCaption
import org.churchpresenter.app.churchpresenter.viewmodel.BackgroundSettingsViewModel
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
fun BackgroundSettingsTab(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    /** Where the Bible band generator saves; null hides its button and leaves the picker. */
    bibleLowerThirdsDir: File? = null,
) {
    val viewModel = remember { BackgroundSettingsViewModel() }
    var scope by remember { mutableStateOf(BackgroundScope.DEFAULT) }
    val backgrounds = settings.backgroundSettings
    // The band each surface's own content type draws, so the preview and the output agree. Bible
    // and Songs carry separate heights, which is why this is asked per surface rather than once.
    val bandFraction = settings.bandFractionFor(scope)
    // The shape of the screen this goes out on. The band is a percentage of that screen's height,
    // so a preview shaped like some other monitor moves the band and everything inside it. Which
    // output that is is the operator's call on a multi-output rig -- see PreviewOutputPicker below.
    val previewOutput = rememberPreviewOutput(settings, Constants.PREVIEW_TAB_BACKGROUND, scope.previewMode())
    val outputAspect = previewOutput.size.aspectRatio
    val onConfigChange: (BackgroundConfig) -> Unit = { config ->
        viewModel.updateBackground(scope, config, onSettingsChange)
    }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        BackgroundScopeRail(
            backgrounds = backgrounds,
            selected = scope,
            bandFractionFor = settings::bandFractionFor,
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
                    bibleLowerThirdsDir = bibleLowerThirdsDir,
                    modifier = Modifier.width(CONTROLS_WIDTH).fillMaxHeight()
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    PreviewOutputPicker(
                        settings = settings,
                        tabId = Constants.PREVIEW_TAB_BACKGROUND,
                        mode = scope.previewMode(),
                        onSettingsChange = onSettingsChange,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                    BackgroundStagePreview(
                        config = backgrounds.resolvedConfigFor(scope),
                        // Resolved separately: `resolvedConfigFor` walks the *band's* chain, and
                        // the wash has one of its own — a surface with a picture of its own is not
                        // inheriting a band, but its wash may still be coming from the Default.
                        aboveBand = resolveAboveBand(backgrounds, backgrounds.configFor(scope)).fill,
                        coverage = scope.coverage,
                        bandFraction = bandFraction,
                        stageAspect = outputAspect,
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
    bandFractionFor: (BackgroundScope) -> Float,
    onSelect: (BackgroundScope) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
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
                            bandFraction = bandFractionFor(scope),
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
            .clip(AppShape(9.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_TINT_ALPHA)
                else Color.Transparent
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                AppShape(9.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = SCOPE_CHIP_WIDTH, height = SCOPE_CHIP_HEIGHT)
                .clip(AppShape(5.dp))
                // Black under the fill, so the part of the screen this surface does not paint
                // reads as unpainted output rather than as the rail showing through.
                .background(Color.Black)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShape(5.dp))
        ) {
            BackgroundCoverageFill(
                config = backgrounds.resolvedConfigFor(scope),
                aboveBand = resolveAboveBand(backgrounds, backgrounds.configFor(scope)).fill,
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
            .background(MaterialTheme.colorScheme.surfaceContainer)
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
            .clip(AppShape(8.dp))
            .background(
                if (inheriting) MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_TINT_ALPHA)
                else Color.Transparent
            )
            .border(
                1.dp,
                if (inheriting) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                AppShape(8.dp)
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
    /** The wash over the area above the band, already resolved; null where nothing is drawn. */
    aboveBand: Color?,
    coverage: BackgroundCoverage,
    bandFraction: Float,
    stageAspect: Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // Sized here rather than with an aspect-ratio modifier: the set has to fit the column in
        // both directions at once, and stop growing once it is big enough to read. The height goes
        // through tvScreenBoxWidthFor because the bezel and the stand are 28dp of height that is
        // not screen -- counting them as screen made the box short and put the band, which is a
        // percentage of the screen's height, somewhere the projector will not put it.
        val width = minOf(maxWidth, STAGE_MAX_WIDTH, tvScreenBoxWidthFor(maxHeight, stageAspect))
        // How much smaller this stage is than the 1920-wide output every stored size is measured
        // against. The blur goes through the presenters' own helper so there is one definition of
        // it; the sample line below is scaled by the same factor.
        val stageScale = width.value / BACKGROUND_REFERENCE_WIDTH
        TvScreenBox(
            modifier = Modifier.width(width),
            screenAspectRatio = stageAspect,
            bezelColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            screenColor = Color.Black
        ) {
            BackgroundCoverageFill(
                config = config,
                aboveBand = aboveBand,
                coverage = coverage,
                bandFraction = bandFraction,
                // Clipped to TvScreenBox's own screen corner radius. Without it a picture's square
                // corners poke past the rounded border, and a blurred one — overscanned 8% the way
                // the presenter overscans it — spills out over the bezel entirely.
                modifier = Modifier.fillMaxSize().clip(AppShape(TV_SCREEN_RADIUS)),
                blurRadius = backgroundBlurRadius(config.blur, width)
            )
            if (config.backgroundType != Constants.BACKGROUND_LOTTIE) Text(
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
                    // Every lower-third surface puts its text in the band it paints, the default
                    // lower third included.
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
                    .clip(AppShape(5.dp))
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
 * Every lower-third background *is* the band — the default one included, since the output draws
 * that one over the band and nothing above it either. Filling the whole screen for a lower third
 * would show the operator a look the output never produces, which is what the four coverage badges
 * on the old cards were for.
 */
@Composable
private fun BackgroundCoverageFill(
    config: BackgroundConfig,
    aboveBand: Color?,
    coverage: BackgroundCoverage,
    bandFraction: Float,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 0.dp
) {
    Column(modifier) {
        if (coverage == BackgroundCoverage.BAND) {
            // Not empty any more when the surface washes the area above its band: the operator has
            // to be able to see that choice here, since it is the one part of a lower third the
            // band below it cannot show.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f - bandFraction)
                    .then(if (aboveBand != null) Modifier.background(aboveBand) else Modifier)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(
                    when (coverage) {
                        BackgroundCoverage.FULL_SCREEN -> 1f
                        BackgroundCoverage.BAND -> bandFraction
                    }
                )
                // Clipped for the same reason the presenter clips its band: a blurred fill is
                // overscanned, and without this it spills above the band line.
                .clipToBounds()
        ) {
            if (config.backgroundType == Constants.BACKGROUND_LOTTIE) {
                // The template carries its own sample text, so it stands in for the fill and the
                // sample line both.
                BibleLottieStillFrame(config.backgroundLottie, Modifier.fillMaxSize())
            } else {
                BackgroundConfigFill(config, Modifier.fillMaxSize(), blurRadius)
                if (config.dim > 0) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = config.dim / PERCENT)))
                }
            }
            // Drawn over the fill rather than between the two boxes: a divider in the layout would
            // take a device-independent pixel out of the weights, and the band would come out
            // fractionally short of the percentage the presenters draw it at.
            if (coverage == BackgroundCoverage.BAND) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

// ── Labels ───────────────────────────────────────────────────────────────────────────────────

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
