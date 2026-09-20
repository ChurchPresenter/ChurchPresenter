package org.churchpresenter.calendar.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The window's metrics and type, taken from the design it was drawn from.
 *
 * Here rather than inline at each call site so the sizes are stated once and the screens read as
 * layout. **Colors are deliberately absent** — those come from `MaterialTheme.colorScheme` and
 * `:theme`'s semantic roles, so the window follows all nine themes; the design's own palette is a
 * single dark one and is not reproduced.
 *
 * The numbers are the design's, kept as `dp`/`sp` at the values it specifies (a 10.5px label stays
 * 10.5.sp) rather than rounded to a scale, because the panes are dense and rounding them drifts the
 * whole column.
 */
object CalendarMetrics {
    /** The month pane: `clamp(232px, 21%, 296px)` in the design. */
    val monthPaneMin = 232.dp
    val monthPaneMax = 296.dp

    val dayHeaderHeight = 45.dp
    val sectionHeaderHeight = 30.dp
    val serviceChipHeight = 42.dp
    val addServiceButtonHeight = 28.dp

    /** The square month-navigation buttons either side of the month label. */
    val monthNavButton = 24.dp

    val rowIcon = 21.dp
    val rowAction = 19.dp
    val rowTimeColumn = 36.dp
    /** The same column when times carry `AM`/`PM`, which `10:00` alone was sized for. */
    val rowTimeColumnWide = 58.dp
    val durationFieldWidth = 44.dp

    val dayCellRadius = RoundedCornerShape(7.dp)
    val rowRadius = RoundedCornerShape(9.dp)
    val chipRadius = 9.dp
    val smallRadius = RoundedCornerShape(5.dp)
    val buttonRadius = RoundedCornerShape(8.dp)

    val dayDot = 4.dp
    val legendDot = 7.dp

    /** The today marker is an underline bar, not a border — see the design's month cell. */
    val todayBarWidth = 11.dp
    val todayBarHeight = 2.dp

    /** The colored bar down the left of a section heading and a service chip. */
    val accentBarWidth = 3.dp
    val sectionBarHeight = 12.dp
    val chipAccentHeight = 24.dp
}

/** The dialogs' own sizes -- the header, the tab strip, the row card and their buttons. */
object SheetMetrics {
    val radius = RoundedCornerShape(13.dp)
    val cardRadius = RoundedCornerShape(9.dp)
    val headerIcon = 28.dp
    val closeButton = 26.dp
    val tabHeight = 27.dp
    val smallButton = 22.dp
    val rowButton = 24.dp
    val doneHeight = 30.dp
}

/**
 * The uppercase, letter-spaced, heavy micro-heading the design uses for `SERVICE TYPES` and
 * `RUN OF SHOW`.
 */
@Composable
@ReadOnlyComposable
fun overlineStyle(letterSpacingEm: Float = OVERLINE_TRACKING): TextStyle =
    MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (10 * letterSpacingEm).sp,
    )

/** The weekday headings over the month grid — smaller again than [overlineStyle]. */
@Composable
@ReadOnlyComposable
fun weekdayStyle(): TextStyle =
    MaterialTheme.typography.labelSmall.copy(
        fontSize = 9.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.45.sp,
    )

private const val OVERLINE_TRACKING = 0.1f
