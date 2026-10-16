package org.churchpresenter.canvas

import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ResolvedMerge
import org.churchpresenter.settings.resolvedMerges

/** A DeckLink device's output mode as width to height, or null when it cannot say. */
fun deckLinkModeSize(device: Int): Pair<Int, Int>? =
    DeckLinkManager.getOutputInfo(device)?.let { it.width to it.height }

/** Every drawable merge by its members' output keys, DeckLink members sized by their device's mode. */
fun ProjectionSettings.liveMerges(): Map<String, ResolvedMerge> = resolvedMerges(::deckLinkModeSize)
