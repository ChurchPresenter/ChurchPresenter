package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.ic_close
import org.churchpresenter.resources.generated.resources.ic_undo
import org.churchpresenter.resources.generated.resources.ic_warning
import org.churchpresenter.resources.generated.resources.shortcut_capture_conflict
import org.churchpresenter.resources.generated.resources.shortcut_capture_title
import org.churchpresenter.resources.generated.resources.shortcut_rebind_hint
import org.churchpresenter.resources.generated.resources.shortcut_recording_stop
import org.churchpresenter.resources.generated.resources.shortcut_settings_clear
import org.churchpresenter.resources.generated.resources.shortcut_settings_reset
import org.churchpresenter.resources.generated.resources.shortcut_unbound
import org.churchpresenter.ui.ConditionalTooltipArea
import org.churchpresenter.ui.TooltipIconButton
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.utils.keyCaps
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Chords of a multi-key binding are drawn as two cap groups separated by this. */
private const val CAP_GROUP_SEPARATOR = "/"

/** Wide enough that a lone letter cap is still a square-ish key rather than a sliver. */
private val CAP_MIN_WIDTH = 24.dp

private val ROW_SHAPE = RoundedCornerShape(9.dp)
private val CAP_SHAPE = RoundedCornerShape(5.dp)
private val CHIP_SHAPE = RoundedCornerShape(7.dp)

/** One row of the list, whether it holds a rebindable action or a pointer gesture. */
private val ROW_MIN_HEIGHT = 44.dp

/**
 * One rebindable action: what it does, what it is bound to, and the two controls that change it.
 *
 * The binding is drawn as **one cap per key** rather than as a single `Ctrl+Shift+N` string, which
 * is why `KeyChord.keyCaps()` exists — the rendered label cannot be split back up on macOS, where
 * the modifiers carry no separator.
 *
 * Clicking the caps starts listening for a new combination in place, so rebinding never leaves the
 * list. **Escape is recorded like any other key** — it is the default binding for Clear Output, so
 * treating it as "cancel" would make that one action impossible to rebind; the way out is the stop
 * button beside the listening chip.
 *
 * A clash is shown, not prevented. The user is told which action already answers to the combination
 * and can decide; the toolbar's filter collects every row in that state.
 */
@Composable
internal fun ShortcutBindingRow(
    action: ShortcutAction,
    chords: List<KeyChord>,
    customized: Boolean,
    conflictsWith: String?,
    categoryName: String?,
    recording: Boolean,
    onRecord: () -> Unit,
    onStopRecording: () -> Unit,
    onCaptured: (KeyChord) -> Unit,
    onRevert: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        recording -> colors.primaryContainer.copy(alpha = 0.45f)
        conflictsWith != null -> colors.errorContainer.copy(alpha = 0.35f)
        else -> colors.surface
    }
    val outline = when {
        recording -> colors.primary
        conflictsWith != null -> colors.error.copy(alpha = 0.6f)
        else -> colors.outlineVariant.copy(alpha = 0.5f)
    }

    ShortcutRowFrame(background = background, outline = outline) {
        ShortcutRowLabel(
            description = stringResource(action.descriptionRes),
            conflictsWith = conflictsWith,
            categoryName = categoryName,
            modifier = Modifier.weight(1f),
        )

        // The binding sits in a column of its own width so the caps, and the button after them,
        // line up down the list however wide one row's combination happens to be.
        Box(
            modifier = Modifier.width(BINDING_COLUMN_WIDTH),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (recording) {
                RecordingChip(onCaptured = onCaptured, modifier = Modifier.testTag(shortcutRecordingTag(action)))
            } else {
                KeycapBinding(
                    chords = chords,
                    label = shortcutBindingLabel(chords),
                    customized = customized,
                    conflicted = conflictsWith != null,
                    onClick = onRecord,
                    modifier = Modifier.testTag(shortcutChipTag(action)),
                )
            }
        }

        if (recording) {
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_close),
                text = stringResource(Res.string.shortcut_recording_stop),
                onClick = onStopRecording,
                iconSize = 11.dp,
                buttonSize = 26.dp,
                iconTint = colors.primary,
            )
        } else {
            // One button, two meanings: an untouched row can only be unbound, a customized one can
            // be put back. Offering both at once would widen every row for a control most never
            // need.
            TooltipIconButton(
                painter = painterResource(if (customized) Res.drawable.ic_undo else Res.drawable.ic_close),
                text = stringResource(
                    if (customized) Res.string.shortcut_settings_reset else Res.string.shortcut_settings_clear
                ),
                onClick = onRevert,
                iconSize = 13.dp,
                buttonSize = 26.dp,
                iconTint = colors.onSurfaceVariant.copy(alpha = if (customized) 1f else 0.4f),
                modifier = Modifier.testTag(shortcutRevertTag(action)),
            )
        }
    }
}

/**
 * One pointer gesture — a description and the gesture, drawn as a dashed chip.
 *
 * Deliberately not a [ShortcutBindingRow] with its controls hidden: a gesture has no registry entry
 * to render from and nothing to rebind, and the dashed outline is what says so at a glance.
 */
@Composable
internal fun ShortcutGestureRow(
    gesture: String,
    description: String,
    categoryName: String?,
) {
    val colors = MaterialTheme.colorScheme
    ShortcutRowFrame(
        background = colors.surface,
        outline = colors.outlineVariant.copy(alpha = 0.5f),
    ) {
        ShortcutRowLabel(
            description = description,
            conflictsWith = null,
            categoryName = categoryName,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier.width(BINDING_COLUMN_WIDTH),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .background(colors.surfaceVariant.copy(alpha = 0.4f), CHIP_SHAPE)
                    .border(1.dp, colors.outlineVariant, CHIP_SHAPE)
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = gesture,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        // Keeps the gesture chip clear of the edge the rebindable rows give to their button.
        Spacer(modifier = Modifier.width(26.dp))
    }
}

/** The shared plate every row sits on, so a gesture row and a binding row line up exactly. */
@Composable
private fun ShortcutRowFrame(
    background: Color,
    outline: Color,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .background(background, ROW_SHAPE)
            .border(1.dp, outline, ROW_SHAPE)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The left-hand column: what the shortcut does, plus whatever qualifies it. */
@Composable
private fun ShortcutRowLabel(
    description: String,
    conflictsWith: String?,
    categoryName: String?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (conflictsWith != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_warning),
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = colors.error,
                )
                Text(
                    text = stringResource(Res.string.shortcut_capture_conflict, conflictsWith),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Only when the list is not already one category — otherwise every row would repeat the
        // heading directly above it.
        if (categoryName != null) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

/**
 * The binding, as clickable keycaps.
 *
 * Carries the whole binding as its content description: the caps are separate `Text`s, so nothing
 * else in the row states "Ctrl+Shift+N" as one string for a screen reader — or for a test.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeycapBinding(
    chords: List<KeyChord>,
    label: String,
    customized: Boolean,
    conflicted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        conflicted -> colors.errorContainer.copy(alpha = 0.5f)
        customized -> colors.primaryContainer.copy(alpha = 0.6f)
        else -> colors.surfaceVariant
    }
    val outline = when {
        conflicted -> colors.error
        customized -> colors.primary
        else -> colors.outlineVariant
    }
    val content = when {
        conflicted -> colors.onErrorContainer
        else -> colors.onSurface
    }

    ConditionalTooltipArea(tooltip = { RebindTooltip() }) {
    Row(
        modifier = modifier
            .heightIn(min = 30.dp)
            .border(1.dp, outline.copy(alpha = 0.45f), CHIP_SHAPE)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 7.dp, vertical = 4.dp)
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chords.isEmpty()) {
            Text(
                text = stringResource(Res.string.shortcut_unbound),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        chords.forEachIndexed { index, chord ->
            if (index > 0) {
                Text(
                    text = CAP_GROUP_SEPARATOR,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            // Built with a loop rather than a mapped list: keyCaps() reads string resources and so
            // has to be called from a composable context.
            chord.keyCaps().forEachIndexed { part, cap ->
                if (part > 0) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                Keycap(text = cap, background = background, outline = outline, content = content)
            }
        }
    }
    }
}

/** Says the caps are a control, which nothing else in the row does. */
@Composable
private fun RebindTooltip() {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        shape = MaterialTheme.shapes.extraSmall,
        tonalElevation = 4.dp,
    ) {
        Text(
            text = stringResource(Res.string.shortcut_rebind_hint),
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** A single key, drawn as a cap: rounded, outlined, and seated on a heavier bottom edge. */
@Composable
private fun Keycap(text: String, background: Color, outline: Color, content: Color) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = CAP_MIN_WIDTH)
            .height(21.dp)
            .background(outline.copy(alpha = 0.55f), CAP_SHAPE)
            .padding(bottom = 2.dp)
            .background(background, CAP_SHAPE)
            .border(1.dp, outline.copy(alpha = 0.7f), CAP_SHAPE)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
        )
    }
}

/**
 * The listening state, which takes the keyboard for as long as it is shown.
 *
 * Every key-down is swallowed whether or not it yields a chord, so a stray Tab or Enter cannot
 * escape into the dialog's buttons mid-capture.
 */
@Composable
private fun RecordingChip(onCaptured: (KeyChord) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }

    Box(
        modifier = modifier
            .height(30.dp)
            .width(RECORDING_CHIP_WIDTH)
            .background(colors.primaryContainer, CHIP_SHAPE)
            .border(1.dp, colors.primary, CHIP_SHAPE)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                capturedChord(event)?.let(onCaptured)
                event.type == KeyEventType.KeyDown
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.shortcut_capture_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Fits the prompt without the row reflowing when the caps it replaces were narrower. */
private val RECORDING_CHIP_WIDTH = 168.dp

/** Holds the widest binding the app ships — and the listening chip that replaces it. */
private val BINDING_COLUMN_WIDTH = 176.dp

/** The whole binding as one string, for the caps' content description and for a test to read. */
@Composable
private fun shortcutBindingLabel(chords: List<KeyChord>): String {
    if (chords.isEmpty()) return stringResource(Res.string.shortcut_unbound)
    val groups = mutableListOf<String>()
    chords.forEach { chord -> groups.add(chord.keyCaps().joinToString("+")) }
    return groups.joinToString(" $CAP_GROUP_SEPARATOR ")
}
