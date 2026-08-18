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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.ui.theme.SemanticColors
import org.churchpresenter.app.churchpresenter.ui.theme.semantic

/**
 * The window's metrics, and the roles it reads the app's theme through.
 *
 * A song library is a dense table — a hundred rows of eight columns — and Material's default
 * controls are built for a form: 56dp fields, 40dp buttons, generous padding. At that size a
 * screenful is a dozen songs. These are the sizes of the design instead: 38dp rows, 12.5sp text,
 * and a chrome that recedes so the songs are what is read.
 *
 * The **colours are not the design's own**. They were, once — two hand-written palettes of forty
 * literals each, picked by how light `MaterialTheme.colorScheme.background` came out. That made the
 * window blue in all nine of the app's themes, and it is not this module's call to make: the
 * operator picked Forest or Mocha in the app, and this window opens inside the app's
 * `AppThemeWrapper`. So every colour below is a role of the scheme in force, and the recessive
 * chrome the design wants comes from **alpha over that scheme** rather than from a darker literal —
 * a hairline is the theme's own text at 7%, not `#16181B`. A colour literal belongs in `:theme` or
 * nowhere.
 */
@Immutable
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
    /** What is legible ON [dangerSurface] — which [danger] is not, in every dark theme. */
    val onDangerSurface: Color,
    val dangerBorder: Color,
    val warning: Color,
    /** The bars a skeleton row is drawn from, and the highlight that sweeps along them. */
    val skeleton: Color,
    val skeletonHighlight: Color,
)

/** The alphas the recessive chrome is built from, over whatever the scheme's own text colour is. */
private const val HAIRLINE_ALPHA = 0.07f
private const val FAINT_TEXT_ALPHA = 0.55f
private const val ACCENT_SURFACE_ALPHA = 0.10f
private const val ACCENT_BORDER_ALPHA = 0.28f
private const val SKELETON_ALPHA = 0.09f
private const val SKELETON_HIGHLIGHT_ALPHA = 0.20f

/**
 * The table's roles, read off [scheme] and [semantic].
 *
 * `surfaceContainerHigh` is the theme's declared role for an input field and is what the search box,
 * the quiet buttons and the menus sit on; `warning` has no M3 role at all, which is why `:theme`
 * defines it.
 */
internal fun libraryColorsFor(scheme: ColorScheme, semantic: SemanticColors) = LibraryColors(
    background = scheme.background,
    surface = scheme.surfaceContainer,
    rowSurface = scheme.background,
    hairline = scheme.onSurface.copy(alpha = HAIRLINE_ALPHA),
    border = scheme.outlineVariant,
    text = scheme.onSurface,
    textMuted = scheme.onSurfaceVariant,
    textFaint = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
    accent = scheme.primary,
    accentText = scheme.primary,
    accentSurface = scheme.primary.copy(alpha = ACCENT_SURFACE_ALPHA),
    accentBorder = scheme.primary.copy(alpha = ACCENT_BORDER_ALPHA),
    primary = scheme.primary,
    onPrimary = scheme.onPrimary,
    inputSurface = scheme.surfaceContainerHigh,
    popupSurface = scheme.surfaceContainerHigh,
    danger = scheme.error,
    dangerSurface = scheme.errorContainer,
    // `error` on `errorContainer` is not a pairing M3 promises anything about, and in this app's
    // dark themes they are #F44336 on #D32F2F -- two reds a shade apart, which is what a delete
    // button drawn in them looked like. `onErrorContainer` is the role that is meant to be read on
    // it, and it is white there and near-black in the light themes.
    onDangerSurface = scheme.onErrorContainer,
    dangerBorder = scheme.error,
    warning = semantic.warning,
    skeleton = scheme.onSurface.copy(alpha = SKELETON_ALPHA),
    skeletonHighlight = scheme.onSurface.copy(alpha = SKELETON_HIGHLIGHT_ALPHA),
)

/**
 * Errors rather than defaulting: there is no palette that is right for an unknown theme, and a
 * silent fallback is how the window came to paint its own blue over eight of the app's nine.
 */
val LocalLibraryColors = staticCompositionLocalOf<LibraryColors> {
    error("LibraryColors read outside LibraryTheme")
}

/** Provides the table's roles, resolved from the theme this window was opened inside. */
@Composable
fun LibraryTheme(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val semantic = MaterialTheme.semantic
    val resolved = remember(scheme, semantic) { libraryColorsFor(scheme, semantic) }
    CompositionLocalProvider(LocalLibraryColors provides resolved, content = content)
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
        // Drawn on `dangerSurface` below, so it is that surface's own foreground, not `danger`.
        danger -> c.onDangerSurface
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
            checked -> Icon(Icons.Default.Check, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(11.dp))
            indeterminate -> Box(Modifier.width(7.dp).height(2.dp).background(c.onPrimary))
        }
    }
}
