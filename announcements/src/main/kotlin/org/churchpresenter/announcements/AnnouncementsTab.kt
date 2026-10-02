package org.churchpresenter.announcements

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.WindowPlacement
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.churchpresenter.sharedui.utils.PreviewOutput
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.churchpresenter.settings.utils.isSystemUsing24HourFormat
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import org.churchpresenter.strings.generated.resources.timer_am
import org.churchpresenter.strings.generated.resources.timer_pm
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.rememberSystemFonts
import org.churchpresenter.sharedui.utils.Utils
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.components.SegmentTrackItem
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.elevationPalette
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LocalContentColor
import org.churchpresenter.sharedui.composables.DragHandle

internal val ANNOUNCEMENT_STEP_KEY_HEIGHT = 20.dp
internal val ANNOUNCEMENT_TRANSPARENT_KEY_PADDING = 10.dp
internal val ANNOUNCEMENT_MIN_BACKGROUND_FIELD_WIDTH = 150.dp

/** The preview, fitted to the output's shape inside the right panel. */
internal const val ANNOUNCEMENTS_PREVIEW_TAG = "announcements_preview"

/** The announcement's text box, which the divider under the text card makes taller. */
internal const val ANNOUNCEMENTS_TEXT_BOX_TAG = "announcements_text_box"

/** The divider between the text card and the timer card. */
internal const val ANNOUNCEMENTS_TEXT_DIVIDER_TAG = "announcements_text_divider"

/** The scrolling timer card under the divider. */
internal const val ANNOUNCEMENTS_TIMER_CARD_TAG = "announcements_timer_card"

/** The divider between the text and timer column and the preview column, dragged to resize them. */
internal const val ANNOUNCEMENTS_SPLIT_DIVIDER_TAG = "announcements_split_divider"

/** A time picker unit's + key ([up]) or - key, by the unit's label. */
internal fun timerStepTag(label: String, up: Boolean) = "announcements_step_${label}_${if (up) "up" else "down"}"

/** A time picker unit's digits, by the unit's label. */
internal fun timerFieldTag(label: String) = "announcements_field_$label"

/** The shortest the dragged text box may be: one line and its inset. */
internal val ANNOUNCEMENT_MIN_TEXT_HEIGHT = 40.dp

/** What the dragged text box always leaves for the timer card below it. */
internal val ANNOUNCEMENT_MIN_SETTINGS_HEIGHT = 200.dp

/** The divider between the text card and the timer card. */
internal val ANNOUNCEMENT_DIVIDER_HEIGHT = 8.dp

/** The split panel's bottom padding and the text card's top padding. */
internal val ANNOUNCEMENT_SPLIT_PANEL_INSETS = 8.dp
private val ANNOUNCEMENT_STEP_KEY_WIDTH = 40.dp
internal val ANNOUNCEMENT_STEP_GAP = 3.dp
private val ANNOUNCEMENT_WELL_WIDTH = 46.dp
internal val ANNOUNCEMENT_WELL_HEIGHT = 36.dp
internal const val ANNOUNCEMENT_HOURS_PER_HALF_DAY = 12
internal const val ANNOUNCEMENT_HOUR_WRAP_OFFSET = 11
internal const val ANNOUNCEMENT_HOURS_PER_DAY = 24
internal const val ANNOUNCEMENT_MAX_SECOND = 59
internal const val ANNOUNCEMENT_MILLIS_PER_SECOND_F = 1000f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnnouncementsTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    output: AnnouncementsOutput? = null,
    onAddToSchedule: ((settings: AnnouncementsSettings) -> Unit)? = null,
    /** Save preset, to the left of Add to Schedule: the same text or timer, kept for the Calendar Manager. */
    onSavePreset: ((settings: AnnouncementsSettings) -> Unit)? = null,
    /** The output the preview stands for; 1920x1080 when the app has not said. */
    previewOutput: PreviewOutput = FallbackPreviewOutput,
    /** The screens set up as stage monitors, which Send to Stage Monitor locks to announcements. */
    stageMonitorScreens: List<Int> = emptyList(),
    /** The app's picker for which output the preview stands for; drawn above the preview. */
    outputPicker: @Composable () -> Unit = {},
    /** Whether the Specific Time picker counts hours 0-23 rather than 1-12 with AM/PM; the system's choice. */
    use24HourClock: Boolean = isSystemUsing24HourFormat(),
) {
    val viewModel = remember { AnnouncementsViewModel() }

    DisposableEffect(Unit) { onDispose { viewModel.dispose() } }

    // Sync from settings on load / settings change
    LaunchedEffect(appSettings.announcementsSettings) {
        viewModel.syncFromSettings(appSettings.announcementsSettings)
    }

    val availableFonts = rememberSystemFonts()

    val density = LocalDensity.current
    val onSettingsChangeState = rememberUpdatedState(onSettingsChange)
    val windowState = LocalMainWindowState.current
    val isMaximized = windowState?.placement != WindowPlacement.Floating
    val currentLayout = if (isMaximized) appSettings.maximizedLayout else appSettings.windowedLayout
    val labels = rememberAnnouncementsLabels()
    val panels = rememberAnnouncementsPanelState(currentLayout, isMaximized, density)
    // Remembered, keyed on everything it holds: a new scope on every recomposition would hand the
    // pieces new lambdas each time, and a click handler keyed on its lambda would restart.
    val scope = remember(
        appSettings, onSettingsChange, output, onAddToSchedule, onSavePreset, availableFonts,
        labels, density, onSettingsChangeState, isMaximized, panels, previewOutput, stageMonitorScreens, outputPicker,
        use24HourClock,
    ) {
        AnnouncementsTabScope(
            AnnouncementsTabInputs(
                appSettings = appSettings,
                onSettingsChange = onSettingsChange,
                output = output,
                onAddToSchedule = onAddToSchedule,
                onSavePreset = onSavePreset,
                screens = AnnouncementsScreens(previewOutput, stageMonitorScreens, outputPicker),
            ),
            AnnouncementsTabEnvironment(
                availableFonts = availableFonts,
                labels = labels,
                onSettingsChangeState = onSettingsChangeState,
                panels = panels,
                display = AnnouncementsDisplay(density, isMaximized, use24HourClock),
            ),
        )
    }
    with(scope) {
        Column(modifier = modifier.fillMaxSize()) {
            // ── Resizable split panel ─────────────────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged {
                twoColHeightPx = it.height
                twoColWidthPx = it.width
            }) {
                Row(modifier = Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, bottom = 4.dp)) {
                    // ── LEFT: text + timer ──────────────
                    AnnouncementsLeftColumn(viewModel)

                    // Drag handle
                    DragHandle(
                        modifier = Modifier.testTag(ANNOUNCEMENTS_SPLIT_DIVIDER_TAG),
                        onDragEnd = { saveLeftPanel() },
                    ) { delta ->
                        leftPanelPx = (leftPanelPx + delta).coerceIn(
                            with(density) { 150.dp.toPx() },
                            (twoColWidthPx - with(density) { 100.dp.toPx() }).coerceAtLeast(
                                with(density) { 150.dp.toPx() },
                            )
                        )
                    }

                    // ── RIGHT COLUMN: preview + animation/loop/speed ──────────
                    AnnouncementsRightColumn(viewModel, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The four timer types in one sunken track, two to a row. */
@Composable
internal fun TimerModeTrack(
    modes: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sunken(AppShape(10.dp), elevationPalette())
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        modes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { (mode, label) ->
                    SegmentTrackItem(
                        selected = mode == selected,
                        onClick = { onSelect(mode) },
                        modifier = Modifier.weight(1f).height(30.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (mode == selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = LocalContentColor.current,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** One unit of the time picker: a raised + key, the digits in a sunken well, a raised - key, the unit. */
@Composable
internal fun TimerColumn(
    value: String,
    label: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onValueChange: (String) -> Unit
) {
    val keyShape = AppShape(9.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ANNOUNCEMENT_STEP_GAP)
    ) {
        KeyButton(
            onClick = onIncrement,
            modifier = Modifier.height(ANNOUNCEMENT_STEP_KEY_HEIGHT).width(ANNOUNCEMENT_STEP_KEY_WIDTH)
                .testTag(timerStepTag(label, up = true)),
            shape = keyShape,
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            modifier = Modifier
                .testTag(timerFieldTag(label))
                .size(ANNOUNCEMENT_WELL_WIDTH, ANNOUNCEMENT_WELL_HEIGHT)
                .sunken(AppShape(10.dp), elevationPalette())
                .padding(horizontal = 2.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { inner() }
            }
        )
        KeyButton(
            onClick = onDecrement,
            modifier = Modifier.height(ANNOUNCEMENT_STEP_KEY_HEIGHT).width(ANNOUNCEMENT_STEP_KEY_WIDTH)
                .testTag(timerStepTag(label, up = false)),
            shape = keyShape,
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(12.dp))
        }
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
internal fun AmPmToggle(isPm: Boolean, onToggle: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ANNOUNCEMENT_STEP_GAP)
    ) {
        Spacer(Modifier.height(ANNOUNCEMENT_STEP_KEY_HEIGHT))
        KeyButton(
            onClick = onToggle,
            modifier = Modifier.size(40.dp, ANNOUNCEMENT_WELL_HEIGHT),
            shape = AppShape(10.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = stringResource(if (isPm) Res.string.timer_pm else Res.string.timer_am),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(ANNOUNCEMENT_STEP_KEY_HEIGHT))
    }
}

/** The preview's text background: the chosen colour, or none when it is set to transparent. */
internal fun previewTextBackground(hex: String): Color =
    if (hex == Constants.COLOR_VALUE_TRANSPARENT) Color.Transparent else Utils.parseHexColor(hex)
