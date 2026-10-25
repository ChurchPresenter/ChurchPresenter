@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import kotlin.test.Test

/** The Dictionary tour: a Greek word looked up, read in Scripture, and put on screen. */
class DictionaryDemoTour {

    @Test
    fun dictionary() = recordTour("dictionary", tab = "Dictionary", liveLabel = "Explain a word", steps = 8) {
        dictionaryTour()
    }

    private fun DemoTour.dictionaryTour() {
        say("intro")
        intro("Look up the Hebrew and Greek behind any word of Scripture.")
        say("open-tab")
        openTab("Dictionary", "Strong's Hebrew and Greek dictionary")
        say("filters")
        tour(
            stop("All", "Every entry", inTab(hasText("All"))),
            stop("Hebrew", "The Old Testament words", inTab(hasText("Hebrew"))),
            stop("Greek", "The New Testament words", inTab(hasText("Greek"))),
        )
        say("search")
        type("Search", "By number, by word, or by meaning", searchField(), "G26")
        say("pick")
        val entry = findAll(hasText("G26")) { it.left > dp(380f) && it.top > dp(250f) && it.right < dp(720f) }.first()
        step("Pick an entry", "Its full meaning opens on the right", entry)
        say("details")
        tour(
            stop("The original word", "In Greek, with its number", detail("G26")),
            stop("Transliteration", "How it's spelled in English letters", detail("Transliteration")),
            stop("Pronunciation", "How to say it", detail("Pronunciation")),
        )
        say("definition")
        tour(
            stop("Definition", "What the word means", detail("Definition")),
            stop("KJV Usage", "How the King James translates it", detail("KJV Usage")),
        )
        say("scripture")
        waitFor("the verses are found", 60_000L) { hasNode(hasText("Found in", substring = true)) }
        tour(
            stop("In Scripture", "Every verse that uses this word", detail("In Scripture")),
            stop("Found in", "And how many there are", detail("Found in")),
        )
        say("words")
        val found = detail("Found in").bounds
        val chip = findAll(hasClickAction()) {
            it.left > dp(700f) && it.top > found.bottom + dp(60f) && it.width < dp(140f) && it.height < dp(60f)
        }.first()
        note("Each word of the verse", "Click one to look it up next", chip)
        say("history")
        tour(
            stop(
                "Back",
                "To the word you looked at before",
                find(hasContentDescription("Back")) { it.left > dp(700f) },
            ),
            stop("Forward", "And forward again", find(hasContentDescription("Forward")) { it.left > dp(700f) }),
        )
        say("language")
        note(
            "Dictionary language",
            "Read the definitions in another language",
            find(hasText("EN")) { it.left > dp(700f) && it.top < dp(140f) },
        )
        say("translation")
        tour(
            stop(
                "Bible translation",
                "Which Bible the verses come from",
                inTab(hasText("SELECT BIBLE TRANSLATION", ignoreCase = true)),
            ),
            stop("Book", "Only one book of the Bible", inTab(hasText("BOOK", ignoreCase = true))),
        )
        say("go-live")
        goLive(sub = "Puts the word and its meaning on the projector")
        say("live")
        showLive(sub = "Agapē, explained on the big screen")
        say("schedule")
        step(
            "Add it to the schedule",
            "Bring it back during the sermon",
            find(hasContentDescription("Add to Schedule")) { it.left > dp(700f) && it.top < dp(140f) },
        )
        say("outro")
        outro()
    }

    private fun stop(title: String, body: String, target: Target) = Triple(title, body, target)

    private fun DemoTour.inTab(m: SemanticsMatcher) = find(m) { it.left > dp(380f) && it.right < dp(720f) }

    /** A label in the entry on the right. */
    private fun DemoTour.detail(label: String) =
        find(hasText(label, substring = true)) { it.left > dp(700f) && it.left < dp(1310f) }
}
