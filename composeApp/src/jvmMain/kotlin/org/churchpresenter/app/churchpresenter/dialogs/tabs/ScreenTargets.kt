package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.churchpresenter.canvas.DeckLinkManager
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.ProjectionSettings

/** The option [assignment]'s picture goes to: DeckLink by index, a screen by bounds. */
internal fun currentPrimaryOption(options: List<DisplayOption>, assignment: ScreenAssignment): DisplayOption =
    matchOption(
        options,
        DisplayOption(
            label = "", targetDisplay = assignment.targetDisplay, targetType = assignment.targetType,
            boundsX = assignment.targetBoundsX, boundsY = assignment.targetBoundsY,
            boundsW = assignment.targetBoundsW, boundsH = assignment.targetBoundsH,
        ),
    )

/** The option [assignment]'s key goes to: DeckLink by index, a screen by bounds. */
internal fun currentKeyOption(options: List<DisplayOption>, assignment: ScreenAssignment): DisplayOption =
    matchOption(
        options,
        DisplayOption(
            label = "", targetDisplay = assignment.keyTargetDisplay, targetType = assignment.keyTargetType,
            boundsX = assignment.keyTargetBoundsX, boundsY = assignment.keyTargetBoundsY,
            boundsW = assignment.keyTargetBoundsW, boundsH = assignment.keyTargetBoundsH,
        ),
    )

private fun matchOption(options: List<DisplayOption>, wanted: DisplayOption): DisplayOption =
    // Match by type+index first for DeckLink (no bounds), then by bounds for screens
    options.find {
        it.targetType == wanted.targetType && it.targetDisplay == wanted.targetDisplay &&
            it.targetType == Constants.TARGET_TYPE_DECKLINK
    } ?: options.find {
        it.targetType == wanted.targetType &&
            it.boundsX == wanted.boundsX && it.boundsY == wanted.boundsY &&
            it.boundsW == wanted.boundsW && it.boundsH == wanted.boundsH
    } ?: options.find {
        it.targetDisplay == wanted.targetDisplay && it.targetType == wanted.targetType
    } ?: options.first()

/** Whether [option] is a DeckLink port already used, or configured, as an input. */
internal fun hasDeckLinkInputConflict(option: DisplayOption, scenes: List<Scene>): Boolean =
    option.targetType == Constants.TARGET_TYPE_DECKLINK && option.targetDisplay >= 0 &&
        (DeckLinkManager.isInputActive(option.targetDisplay) ||
            DeckLinkManager.isInputConfigured(option.targetDisplay, scenes))

/**
 * Points slot [i]'s picture at [option], taking it off any other slot's picture, and off any key
 * (this slot's included), that already used that display or port -- one output, one owner.
 */
internal fun withPrimaryTarget(
    projection: ProjectionSettings,
    i: Int,
    assignment: ScreenAssignment,
    option: DisplayOption,
    numScreens: Int,
): ProjectionSettings {
    var newProj = projection.withAssignment(
        i,
        assignment.copy(
            targetDisplay = option.targetDisplay,
            targetType = option.targetType,
            targetBoundsX = option.boundsX,
            targetBoundsY = option.boundsY,
            targetBoundsW = option.boundsW,
            targetBoundsH = option.boundsH
        ),
    )
    if (option.targetDisplay < 0) return newProj
    for (j in 0 until numScreens) {
        // Clear from other primary displays that target the same output
        val other = newProj.getAssignment(j)
        if (j != i && primaryUses(other, option)) newProj = newProj.withAssignment(j, other.withoutPrimary())
        // Clear from key outputs that target the same output
        val otherLatest = newProj.getAssignment(j)
        if (keyUses(otherLatest, option)) newProj = newProj.withAssignment(j, otherLatest.withoutKey())
    }
    return newProj
}

/**
 * Points slot [i]'s key at [option], taking it off any other slot's picture or key -- and off this
 * slot's own picture -- where it was already used.
 */
internal fun withKeyTarget(
    projection: ProjectionSettings,
    i: Int,
    assignment: ScreenAssignment,
    option: DisplayOption,
    numScreens: Int,
): ProjectionSettings {
    var newProj = projection.withAssignment(
        i,
        assignment.copy(
            keyTargetDisplay = option.targetDisplay,
            keyTargetType = option.targetType,
            keyTargetBoundsX = option.boundsX,
            keyTargetBoundsY = option.boundsY,
            keyTargetBoundsW = option.boundsW,
            keyTargetBoundsH = option.boundsH
        ),
    )
    if (option.targetDisplay < 0) return newProj
    for (j in 0 until numScreens) {
        // Clear from other primary displays that target the same output
        val other = newProj.getAssignment(j)
        if (j != i && primaryUses(other, option)) newProj = newProj.withAssignment(j, other.withoutPrimary())
        // Clear from other key outputs that target the same output
        val otherLatest = newProj.getAssignment(j)
        if (j != i && keyUses(otherLatest, option)) newProj = newProj.withAssignment(j, otherLatest.withoutKey())
    }
    // Also clear if same slot's primary display targets the same output
    val self = newProj.getAssignment(i)
    if (primaryUses(self, option)) newProj = newProj.withAssignment(i, self.withoutPrimary())
    return newProj
}

/** Whether [a]'s picture goes to [option]: the same DeckLink port, or a screen with the same bounds. */
private fun primaryUses(a: ScreenAssignment, option: DisplayOption): Boolean =
    if (option.targetType == Constants.TARGET_TYPE_DECKLINK) {
        a.targetType == Constants.TARGET_TYPE_DECKLINK && a.targetDisplay == option.targetDisplay
    } else {
        option.boundsX != Int.MIN_VALUE &&
            a.targetBoundsX == option.boundsX && a.targetBoundsY == option.boundsY &&
            a.targetBoundsW == option.boundsW && a.targetBoundsH == option.boundsH
    }

/** Whether [a]'s key goes to [option]: the same DeckLink port, or a screen with the same bounds. */
private fun keyUses(a: ScreenAssignment, option: DisplayOption): Boolean =
    if (option.targetType == Constants.TARGET_TYPE_DECKLINK) {
        a.keyTargetType == Constants.TARGET_TYPE_DECKLINK && a.keyTargetDisplay == option.targetDisplay
    } else {
        option.boundsX != Int.MIN_VALUE &&
            a.keyTargetBoundsX == option.boundsX && a.keyTargetBoundsY == option.boundsY &&
            a.keyTargetBoundsW == option.boundsW && a.keyTargetBoundsH == option.boundsH
    }

private fun ScreenAssignment.withoutPrimary(): ScreenAssignment = copy(
    targetDisplay = Constants.KEY_TARGET_NONE,
    targetType = Constants.TARGET_TYPE_SCREEN,
    targetBoundsX = Int.MIN_VALUE,
    targetBoundsY = Int.MIN_VALUE,
    targetBoundsW = 0,
    targetBoundsH = 0
)

private fun ScreenAssignment.withoutKey(): ScreenAssignment = copy(
    keyTargetDisplay = Constants.KEY_TARGET_NONE,
    keyTargetType = Constants.TARGET_TYPE_SCREEN,
    keyTargetBoundsX = Int.MIN_VALUE,
    keyTargetBoundsY = Int.MIN_VALUE,
    keyTargetBoundsW = 0,
    keyTargetBoundsH = 0
)
