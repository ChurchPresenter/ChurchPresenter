package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants

/**
 * Brings saved screen assignments into line with the displays and DeckLink devices actually present,
 * at startup and before the UI renders.
 *
 * Two things are fixed here:
 * - **Missing slots are added.** There is one slot per non-primary display plus one per DeckLink
 *   device. A slot with no display behind it (a DeckLink-only slot) is created as
 *   [Constants.KEY_TARGET_NONE] rather than left at auto, which is the whole reason this runs before
 *   the UI: an unresolved slot would otherwise render as if it were pointed at a screen.
 * - **`-1` (auto) is resolved.** An assignment saved as auto takes the display in its own position,
 *   or becomes `KEY_TARGET_NONE` when that position has no display — the case where someone unplugs
 *   the second monitor between services.
 *
 * Saved assignments that already name a display are left completely alone, including when that
 * display is currently absent: the operator chose it, and a monitor that is unplugged today is
 * usually plugged back in tomorrow. Silently repointing it would move the output somewhere the
 * operator never asked for.
 *
 * Returns **null when nothing needed changing**, so a normal launch does not rewrite the settings
 * file. Callers persist only a non-null result.
 */
fun reconcileScreenAssignments(
    saved: List<ScreenAssignment>,
    nonPrimaryDisplays: List<ResolvedDisplay>,
    deckLinkCount: Int,
): List<ScreenAssignment>? {
    val slotCount = (nonPrimaryDisplays.size + deckLinkCount).coerceAtLeast(0)
    var changed = false
    val assignments = saved.toMutableList()

    while (assignments.size < slotCount) {
        val display = nonPrimaryDisplays.getOrNull(assignments.size)
        assignments.add(
            ScreenAssignment(
                targetDisplay = display?.deviceIndex ?: Constants.KEY_TARGET_NONE,
                targetBoundsX = display?.x ?: Int.MIN_VALUE,
                targetBoundsY = display?.y ?: Int.MIN_VALUE,
                targetBoundsW = display?.width ?: 0,
                targetBoundsH = display?.height ?: 0,
            )
        )
        changed = true
    }

    for (idx in assignments.indices) {
        if (assignments[idx].targetDisplay != AUTO_TARGET_DISPLAY) continue
        val display = nonPrimaryDisplays.getOrNull(idx)
        assignments[idx] = if (display != null) {
            assignments[idx].copy(
                targetDisplay = display.deviceIndex,
                targetBoundsX = display.x,
                targetBoundsY = display.y,
                targetBoundsW = display.width,
                targetBoundsH = display.height,
            )
        } else {
            assignments[idx].copy(targetDisplay = Constants.KEY_TARGET_NONE)
        }
        changed = true
    }

    return if (changed) assignments else null
}

/** `targetDisplay` value meaning "decide at startup" — see [ScreenAssignment.targetDisplay]. */
internal const val AUTO_TARGET_DISPLAY = -1
