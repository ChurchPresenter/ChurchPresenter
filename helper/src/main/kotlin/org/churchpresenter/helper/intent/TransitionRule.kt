package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.sharedui.models.Tabs

/** Transitions, as they are usually typed too: "transitation", "transistion", "transision". */
private val TRANSITION_WORDS = setOf(
    "transition", "transitions", "transitation", "transitations", "transistion", "transistions", "transision",
    "transisions", "transtion", "transtions", "transiton", "transitons", "crossfade", "crossfades", "fade",
    "fades", "fading",
)
private val TRANSITION_PHRASES = listOf("cross fade", "cross-fade", "fade in", "fade out")

/** What has a transition of its own, not a profile's: pictures and slides fade on their tab's setting. */
private val OWN_TRANSITION = setOf(
    "picture", "pictures", "photo", "photos", "image", "images", "slide", "slides", "slideshow", "presentation",
    "powerpoint", "video", "videos", "media", "screen", "black",
)

/**
 * "Can I change the transition", "make the verses crossfade": the Transition group of the profile's
 * Bible page when the request is about the Bible, or the Songs page otherwise.
 */
internal fun transitionRule(r: Request): Resolution? {
    val aboutTransition = r.words.any { it in TRANSITION_WORDS } || r.hasPhrase(TRANSITION_PHRASES)
    if (!aboutTransition || r.words.any { it in OWN_TRANSITION }) return null
    val bible = r.words.any { it in BIBLE_WORDS } || (r.context.currentTab == Tabs.BIBLE && !r.has(Vocabulary.SONG))
    return act(HelperAction.Highlight(LookTours.transition(songs = !bible)))
}

private val BIBLE_WORDS = setOf("bible", "verse", "verses", "scripture", "scriptures", "passage", "passages")
