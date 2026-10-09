package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.HelperAction

// The rules that answer a question about the app rather than do or show something.

private val WHATS_LIVE = listOf(
    "what's live", "whats live", "what is live", "what's on screen", "what is on screen", "what's on the screen",
    "what is on the screen", "what's showing", "what is showing", "what are we showing", "what's projected",
    "what is projected", "what's up on screen", "what's on air", "what is on air", "is anything live",
)

internal fun whatsLiveRule(r: Request): Resolution? =
    if (r.hasPhrase(WHATS_LIVE)) act(HelperAction.WhatsLive) else null

private val VERSION = listOf(
    "what version", "which version", "app version", "version of the app", "version am i", "version is this",
    "check for updates", "check for update", "check for an update", "any updates", "an update", "is there an update",
    "update the app", "latest version", "up to date", "newer version", "new version of the app",
)

/** "What version is this", "check for updates". A Bible's version is the Bible rules'. */
internal fun versionRule(r: Request): Resolution? {
    if (Vocabulary.BIBLE_NAMES.any { r.text.containsWordPrefix(it) }) return null
    return if (r.hasPhrase(VERSION)) act(HelperAction.CheckForUpdates) else null
}
