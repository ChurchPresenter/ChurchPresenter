package org.churchpresenter.serverui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.calendar_computer_code_expiry
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.sharedui.utils.AppWindowRoot
import org.churchpresenter.sharedui.utils.LocalMainWindowState
import org.churchpresenter.sharedui.utils.centeredOnMainWindow
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.RaisedButton

@Composable
internal fun StepDots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(1, 2).forEach { dot ->
            Box(
                Modifier.size(width = 18.dp, height = 5.dp).background(
                    if (dot <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    AppShape(3.dp),
                ),
            )
        }
    }
}

/** A small tinted button inside a field: Paste, Open file. */
@Composable
internal fun TokenButton(text: String, onClick: () -> Unit) {
    GhostButton(
        onClick = onClick,
        shape = AppShape(7.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.height(26.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = TOKEN_ALPHA),
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
}

/** A dialog's top: its chip, title and subtitle, and [trailing] at the right. */
@Composable
internal fun DialogHeader(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconChip(icon, tint, size = 36.dp, iconSize = 18.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            DimText(subtitle)
        }
        trailing()
    }
}

@Composable
internal fun DialogBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** A rule, then the dialog's buttons at the right. */
@Composable
internal fun DialogFooter(content: @Composable RowScope.() -> Unit) {
    Spacer(Modifier.height(18.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Draws a calendar dialog in place instead of in a window of its own -- for a test, which has no
 * screen to open one on. The app never sets it.
 */
internal val LocalCalendarDialogInPlace = staticCompositionLocalOf { false }

/**
 * The window both dialogs share, over the main one in the app's theme. It takes the height its
 * content needs, measured each time that changes -- a step that adds a line grows the window.
 */
@Composable
internal fun CalendarDialog(
    title: String,
    onDismiss: () -> Unit,
    width: Dp = CALENDAR_DIALOG_WIDTH,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalCalendarDialogInPlace.current) {
        Column(content = content)
        return
    }
    val isDark = MaterialTheme.colorScheme.background.luminance() < DARK_LUMINANCE
    val mainWindowState = LocalMainWindowState.current
    val state = rememberDialogState(
        position = centeredOnMainWindow(mainWindowState, width, INITIAL_HEIGHT),
        width = width,
        height = INITIAL_HEIGHT,
    )
    DialogWindow(onCloseRequest = onDismiss, state = state, title = title, resizable = false) {
        val density = LocalDensity.current
        AppWindowRoot(theme = if (isDark) ThemeMode.DARK else ThemeMode.LIGHT) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .onSizeChanged { measured ->
                            val insets = window.insets
                            val chrome = (insets.top + insets.bottom).takeIf { it > 0 } ?: TITLE_BAR_FALLBACK
                            val wanted = with(density) { measured.height.toDp() } + chrome.dp
                            if (wanted != state.size.height) state.size = DpSize(width, wanted)
                        },
                    content = content,
                )
            }
        }
    }
}

private val CALENDAR_DIALOG_WIDTH = 460.dp
private val INITIAL_HEIGHT = 440.dp
private const val TITLE_BAR_FALLBACK = 28
private const val DARK_LUMINANCE = 0.5f
private const val TOKEN_ALPHA = 0.14f

/**
 * A dialog's one filled button. Disabled, it is a solid gray block rather than the raised key at
 * 40%, which all but vanished into the dialog's background.
 */
@Composable
internal fun DialogPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = AppShape(8.dp)
    if (enabled) {
        RaisedButton(shape = shape, onClick = onClick) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        Box(
            modifier = Modifier
                .defaultMinSize(ButtonDefaults.MinWidth, ButtonDefaults.MinHeight)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    disabled()
                }
                .padding(ButtonDefaults.ContentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_INK_ALPHA),
            )
        }
    }
}

private const val DISABLED_INK_ALPHA = 0.45f

@Composable
internal fun ExpiryChip() {
    val warning = MaterialTheme.semantic.warning
    Row(
        modifier = Modifier
            .background(warning.copy(alpha = CHIP_WASH_ALPHA), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = warning, modifier = Modifier.size(11.dp))
        Text(
            stringResource(Res.string.calendar_computer_code_expiry),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = warning,
        )
    }
}

@Composable
internal fun RowScope.WideKey(text: String, icon: ImageVector, onClick: () -> Unit) {
    KeyButton(
        shape = AppShape(8.dp),
        contentPadding = COMPACT_PADDING,
        modifier = Modifier.weight(1f).height(34.dp),
        onClick = onClick,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}
private const val CHIP_WASH_ALPHA = 0.16f
