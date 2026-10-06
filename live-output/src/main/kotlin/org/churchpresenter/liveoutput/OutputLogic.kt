package org.churchpresenter.liveoutput

import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.sharedui.models.Presenting

/** How many DeckLink outputs are available, which is none at all when the driver is not present. */
fun deckLinkOutputCount(available: Boolean, deviceCount: () -> Int): Int =
    if (available) deviceCount() else 0

/** Whether going live with [mode] should also raise the output windows — clearing must not. */
fun shouldShowPresenterWindowFor(mode: Presenting): Boolean = mode != Presenting.NONE

/** Whether this output draws the configured background, which is a separate switch per layout. */
fun showsOutputBackground(profile: OutputProfile): Boolean =
    if (profile.isLowerThird) profile.look.background.lowerThird
    else profile.look.background.fullscreen

/** What an output is showing: its own lock when it has one, otherwise whatever is live. */
fun effectiveOutputMode(
    locks: Map<Int, Presenting>,
    index: Int,
    slideContent: Presenting,
): Presenting = locks[index] ?: slideContent

/** Whether this output's picture goes out over SDI rather than to a display. */
fun isDeckLinkPrimaryOutput(assignment: ScreenAssignment): Boolean =
    assignment.targetType == Constants.TARGET_TYPE_DECKLINK

/** Whether this output's key signal is aimed at a DeckLink device rather than a display. */
fun isDeckLinkKeyOutput(assignment: ScreenAssignment): Boolean =
    assignment.keyTargetType == Constants.TARGET_TYPE_DECKLINK

/**
 * Whether this output's key signal goes to a DeckLink device that has actually been chosen.
 *
 * The call sites this replaced also tested `keyTargetDisplay >= 0` alongside [hasKeyOutput], which
 * is the very expression [hasKeyOutput] is defined as — so that clause could never fail once the
 * first had passed, and is dropped rather than carried as a condition no input can reach.
 */
fun hasDeckLinkKeyOutput(assignment: ScreenAssignment): Boolean =
    assignment.hasKeyOutput && isDeckLinkKeyOutput(assignment)

/** Whether this output's key signal goes to an ordinary display instead. */
fun hasScreenKeyOutput(assignment: ScreenAssignment): Boolean =
    assignment.hasKeyOutput && assignment.keyTargetType == Constants.TARGET_TYPE_SCREEN

/**
 * Which display a key output lands on.
 *
 * Matched on the bounds saved with the assignment first, because display indices are reordered by
 * the OS when monitors are plugged or unplugged; the saved index is only the fallback for a layout
 * whose bounds no longer match anything.
 */
fun keyOutputScreenIndex(matchedByBounds: Int?, savedIndex: Int): Int =
    matchedByBounds ?: savedIndex

/** Whether an index names a display that is actually attached. */
fun isScreenIndexValid(index: Int, screenCount: Int): Boolean = index in 0 until screenCount
