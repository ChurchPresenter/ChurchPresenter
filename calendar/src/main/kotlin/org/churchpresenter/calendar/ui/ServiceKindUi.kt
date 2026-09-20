package org.churchpresenter.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_items_count
import org.churchpresenter.calendar.generated.resources.calendar_items_count_one
import org.churchpresenter.calendar.generated.resources.calendar_kind_midweek
import org.churchpresenter.calendar.generated.resources.calendar_kind_special
import org.churchpresenter.calendar.generated.resources.calendar_kind_midweek_short
import org.churchpresenter.calendar.generated.resources.calendar_kind_special_short
import org.churchpresenter.calendar.generated.resources.calendar_kind_sunday
import org.churchpresenter.calendar.generated.resources.calendar_kind_sunday_short
import org.churchpresenter.calendar.model.ServiceKind
import org.jetbrains.compose.resources.stringResource

private const val HEX_RADIX = 16
private const val OPAQUE = 0xFF000000L

/**
 * The dot color for a kind of service.
 *
 * A stored hex rather than a theme role, for the same reason a schedule label stores one: these are
 * *category* colors that have to stay distinguishable from each other, and a role would collapse
 * two of them together under some of the nine themes. They are the same three the window was
 * designed with.
 */
fun kindColor(kind: ServiceKind): Color = parseHex(kind.colorHex)

@Composable
fun kindLabel(kind: ServiceKind): String = stringResource(
    when (kind) {
        ServiceKind.SUNDAY -> Res.string.calendar_kind_sunday
        ServiceKind.MIDWEEK -> Res.string.calendar_kind_midweek
        ServiceKind.SPECIAL -> Res.string.calendar_kind_special
    }
)

/** `#RRGGBB` to an opaque [Color]. Anything unparseable is grey rather than a crash — the file is
 *  hand-editable and a bad value must not stop the window opening. */
internal fun parseHex(hex: String): Color {
    val digits = hex.removePrefix("#")
    val value = digits.toLongOrNull(HEX_RADIX) ?: return Color.Gray
    return Color(value or OPAQUE)
}

/** The short form the segmented type selector and the chips use — `Sunday`, not `Sunday service`. */
@Composable
fun kindShortLabel(kind: ServiceKind): String = stringResource(
    when (kind) {
        ServiceKind.SUNDAY -> Res.string.calendar_kind_sunday_short
        ServiceKind.MIDWEEK -> Res.string.calendar_kind_midweek_short
        ServiceKind.SPECIAL -> Res.string.calendar_kind_special_short
    }
)

/** `1 item` / `4 items` — one place, so the chip and the run-of-show header cannot disagree. */
@Composable
fun itemCountLabel(count: Int): String =
    if (count == 1) {
        stringResource(Res.string.calendar_items_count_one, count)
    } else {
        stringResource(Res.string.calendar_items_count, count)
    }
