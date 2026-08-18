package songlibrary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The window's own palette and metrics.
 *
 * A song library is a dense table — a hundred rows of eight columns — and Material's default
 * controls are built for a form: 56dp fields, 40dp buttons, generous padding. At that size a
 * screenful is a dozen songs. These are the sizes and colours of the design instead: 38dp rows,
 * 12.5sp text, and a chrome that recedes so the songs are what is read.
 */
data class LibraryColors(
    val background: Color,
    val surface: Color,
    val rowSurface: Color,
    val hairline: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val accent: Color,
    val accentText: Color,
    val accentSurface: Color,
    val accentBorder: Color,
    val primary: Color,
    val onPrimary: Color,
    val inputSurface: Color,
    val popupSurface: Color,
    val danger: Color,
    val dangerSurface: Color,
    val dangerBorder: Color,
    val warning: Color,
)

private val DARK = LibraryColors(
    background = Color(0xFF0E1013),
    surface = Color(0xFF131518),
    rowSurface = Color(0xFF0E1013),
    hairline = Color(0xFF16181B),
    border = Color(0xFF24272C),
    text = Color(0xFFE6E9EE),
    textMuted = Color(0xFF8B9099),
    textFaint = Color(0xFF585D64),
    accent = Color(0xFF5B9DF5),
    accentText = Color(0xFF8AB8F8),
    accentSurface = Color(0xFF121A26),
    accentBorder = Color(0xFF1E2B3D),
    primary = Color(0xFF2F6FD0),
    onPrimary = Color.White,
    inputSurface = Color(0xFF16181B),
    popupSurface = Color(0xFF17191D),
    danger = Color(0xFFF08A92),
    dangerSurface = Color(0xFF2C1518),
    dangerBorder = Color(0xFF47222A),
    warning = Color(0xFFE8A33D),
)

/** The same design in the app's light theme, which the window follows rather than fighting. */
private val LIGHT = LibraryColors(
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    rowSurface = Color(0xFFFFFFFF),
    hairline = Color(0xFFECEEF1),
    border = Color(0xFFD8DCE2),
    text = Color(0xFF1A1D21),
    textMuted = Color(0xFF5C636D),
    textFaint = Color(0xFF8B9099),
    accent = Color(0xFF2F6FD0),
    accentText = Color(0xFF1F5BB5),
    accentSurface = Color(0xFFE9F0FB),
    accentBorder = Color(0xFFC6D9F5),
    primary = Color(0xFF2F6FD0),
    onPrimary = Color.White,
    inputSurface = Color(0xFFFFFFFF),
    popupSurface = Color(0xFFFFFFFF),
    danger = Color(0xFFB3261E),
    dangerSurface = Color(0xFFFBEAEA),
    dangerBorder = Color(0xFFF0C4C4),
    warning = Color(0xFFB26A00),
)

val LocalLibraryColors = staticCompositionLocalOf { DARK }

/** Provides the palette that matches the theme the app is in. */
@Composable
fun LibraryTheme(content: @Composable () -> Unit) {
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    CompositionLocalProvider(LocalLibraryColors provides if (light) LIGHT else DARK, content = content)
}

val colors: LibraryColors
    @Composable get() = LocalLibraryColors.current

/** The type scale of the design: small, tight, and even down the table. */
object LibraryType {
    val title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
    val body = TextStyle(fontSize = 12.5.sp)
    val bodyStrong = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    val small = TextStyle(fontSize = 11.5.sp)
    val button = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val columnHead = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.9.sp)
}

/** Corner radii, control heights and the row height the whole table is built on. */
object LibraryMetrics {
    val control = 32.dp
    val radius = 9.dp
    val panelRadius = 14.dp
    val rowHeight = 38.dp
    val headerHeight = 32.dp
}

/** A filled button in the accent colour: the one action a bar is really offering. */
@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true, icon: (@Composable () -> Unit)? = null) {
    val c = colors
    Row(
        Modifier.height(LibraryMetrics.control)
            .clip(RoundedCornerShape(LibraryMetrics.radius))
            .background(if (enabled) c.primary else c.inputSurface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Box(Modifier.size(7.dp))
        }
        Text(label, style = LibraryType.button, color = if (enabled) c.onPrimary else c.textFaint)
    }
}

/** An outlined button: everything that is not the one action. */
@Composable
fun QuietButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    val c = colors
    val border = when {
        !enabled -> c.hairline
        danger -> c.dangerBorder
        else -> c.border
    }
    val text = when {
        !enabled -> c.textFaint
        danger -> c.danger
        else -> c.text
    }
    Row(
        Modifier.height(LibraryMetrics.control)
            .clip(RoundedCornerShape(LibraryMetrics.radius))
            .background(if (danger && enabled) c.dangerSurface else c.inputSurface)
            .border(1.dp, border, RoundedCornerShape(LibraryMetrics.radius))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Box(Modifier.size(7.dp))
        }
        Text(label, style = LibraryType.button, color = text)
    }
}

/** The tick box the table and the menus share, drawn at the size the design uses. */
@Composable
fun LibraryCheckbox(checked: Boolean, indeterminate: Boolean = false, onToggle: (() -> Unit)? = null) {
    val c = colors
    Box(
        Modifier.size(15.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked || indeterminate) c.primary else Color.Transparent)
            .border(1.5.dp, if (checked || indeterminate) c.primary else c.border, RoundedCornerShape(4.dp))
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            checked -> Text("✓", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = c.onPrimary)
            indeterminate -> Box(Modifier.width(7.dp).height(2.dp).background(c.onPrimary))
        }
    }
}
