package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import org.churchpresenter.theme.LocalThemeCustomization

/** A list row's padding at the Margin chosen in Customize Theme, from the amount it is written with. */
@Composable
@ReadOnlyComposable
internal fun rowPad(base: Dp): Dp = base * LocalThemeCustomization.current.rowSpacing

/** The text a one-line row always keeps room for, whatever the Margin. */
private val ROW_TEXT_FLOOR = 20.dp

/**
 * A one-line row's minimum height at the chosen Margin: only the room around the text shrinks, so a
 * thinner Margin never clips the label. Use with `heightIn(min = ...)`, so a larger text size still
 * grows the row.
 */
@Composable
@ReadOnlyComposable
internal fun rowSpan(base: Dp): Dp = lerp(ROW_TEXT_FLOOR, base, LocalThemeCustomization.current.rowSpacing)
