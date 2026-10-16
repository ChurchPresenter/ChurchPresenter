package org.churchpresenter.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.close
import org.churchpresenter.theme.elevationPalette
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The chip behind a live tool's icon, and the icon on it. */
data class LiveToolTint(val chip: Color, val icon: Color)

/** The live tools, each with its own hue so Message, Props and Macros read as different things. */
enum class LiveTool(private val dark: LiveToolTint, private val light: LiveToolTint) {
    MESSAGE(LiveToolTint(Color(0xFF1E3A56), Color(0xFF92D0FF)), LiveToolTint(Color(0xFFD8EEFF), Color(0xFF0465AF))),
    PROPS(LiveToolTint(Color(0xFF3D3052), Color(0xFFD5BAFF)), LiveToolTint(Color(0xFFEFE6FF), Color(0xFF714CA6))),
    MACROS(LiveToolTint(Color(0xFF1B412A), Color(0xFF8EE0AB)), LiveToolTint(Color(0xFFD7F4E0), Color(0xFF00793D))),
    CLEAR(LiveToolTint(Color(0xFF532B28), Color(0xFFFFAEA6)), LiveToolTint(Color(0xFFFFE2DE), Color(0xFFA43B38))),
    PREVIEW(LiveToolTint(Color(0xFF043F4D), Color(0xFF6EDBFC)), LiveToolTint(Color(0xFFCFF2FE), Color(0xFF00739A))),
    ;

    /** This tool's tint in the current theme. */
    val tint: LiveToolTint
        @Composable get() = if (elevationPalette().isDark) dark else light
}

/** A live tool's icon on its colored chip; [onAir] turns it the error red, for what is up now. */
@Composable
fun LiveToolChip(
    tool: LiveTool,
    painter: Painter,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    iconSize: Dp = 14.dp,
    onAir: Boolean = false,
) {
    val tint = if (onAir) LiveTool.CLEAR.tint.copy(icon = MaterialTheme.colorScheme.error) else tool.tint
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * CHIP_ROUNDING))
            .background(if (onAir) MaterialTheme.colorScheme.error.copy(alpha = ON_AIR_CHIP_ALPHA) else tint.chip),
    ) {
        Image(
            painter,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            colorFilter = ColorFilter.tint(tint.icon),
        )
    }
}

/** A live tool dialog's head: the tool's chip, its name, what it is for in a line, and Close. */
@Composable
fun LiveToolDialogHeader(tool: LiveTool, painter: Painter, title: String, purpose: String, onClose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 20.dp),
    ) {
        LiveToolChip(tool, painter, size = 36.dp, iconSize = 18.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(purpose, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TooltipIconButton(
            painter = painterResource(IconRes.drawable.ic_close),
            text = stringResource(Res.string.close),
            onClick = onClose,
            iconSize = 15.dp,
            buttonSize = 30.dp,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A small uppercase heading over one part of a live tool dialog. */
@Composable
fun LiveToolSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.11.em,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * A live tool dialog's foot, under a rule: the quiet secondary actions first, then -- after a
 * `Spacer(Modifier.weight(1f))` the caller places -- Close and the one filled primary.
 */
@Composable
fun LiveToolDialogFooter(content: @Composable RowScope.() -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 22.dp, top = 12.dp, bottom = 16.dp),
            content = content,
        )
    }
}

/** A list row in a live tool dialog: a slightly sunken panel with a hairline border. */
@Composable
fun Modifier.liveToolPanel(): Modifier {
    val shape = RoundedCornerShape(10.dp)
    return this
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
}

/**
 * The red button that puts something on air -- Go Live, Take -- with its dot. Off, it lies flat in
 * gray with muted ink instead of fading the red, so it reads as not ready rather than as broken.
 */
@Composable
fun LiveActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
) {
    if (enabled) {
        RaisedButton(
            onClick = onClick,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            contentPadding = PaddingValues(horizontal = 18.dp),
            modifier = modifier,
        ) { LiveActionLabel(label, MaterialTheme.colorScheme.onError) }
    } else {
        val off = if (elevationPalette().isDark) OFF_DARK else OFF_LIGHT
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = modifier
                .defaultMinSize(minHeight = 36.dp)
                .clip(shape)
                .background(off.chip)
                .semantics {
                    role = Role.Button
                    disabled()
                }
                .padding(horizontal = 18.dp),
        ) { LiveActionLabel(label, off.icon) }
    }
}

@Composable
private fun RowScope.LiveActionLabel(label: String, ink: Color) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(ink))
    Spacer(Modifier.width(7.dp))
    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink)
}

/** The off key's fill and ink, from the design. */
private val OFF_DARK = LiveToolTint(Color(0xFF2C2E34), Color(0xFF5E636C))
private val OFF_LIGHT = LiveToolTint(Color(0xFFE3E6EA), Color(0xFF9AA0A8))

/** Rounded share of a chip's size its corners take, as in the design (26dp → 8dp). */
private const val CHIP_ROUNDING = 0.31f
private const val ON_AIR_CHIP_ALPHA = 0.16f

/** Padding the body of a live tool dialog takes at its sides. */
val LIVE_TOOL_BODY_PADDING = 22.dp
