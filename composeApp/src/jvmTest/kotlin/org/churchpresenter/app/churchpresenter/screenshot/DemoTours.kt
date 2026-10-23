@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import java.io.File
import kotlin.test.Test

/** The demo videos — recorded only by `scripts/record_demos.sh`, never in CI (see [recordTour]). */
class DemoTours {

    @Test
    fun bible() = recordTour(
        "bible",
        startTab = "Songs",
        settings = { s ->
            secondBible()?.let { s.copy(bibleSettings = s.bibleSettings.copy(secondaryBible = it.name)) } ?: s
        },
    ) {
        title("The Bible tab", "Browse, search, schedule and go live")
        step("Open the Bible tab", "Every translation you have installed lives here", tab("Bible"))
        // The tab opens with the search box focused; leaving it collapses a banner and moves the lists.
        quietClick(find(hasText("Genesis")) { it.left > dp(380f) })

        val romans = scrollList("Genesis", hasText("Romans")) { it.left > dp(380f) }
        step("Pick a book", "The whole Bible, in order", romans)
        step("Pick a chapter", "Romans 12", find(hasText("12")) { it.left > dp(580f) && it.right < dp(720f) })
        step("Click a verse", "It shows in the preview, ready to go", text("1. I beseech you therefore"))
        shiftClick("Shift-click to select a range", "Verses 1 and 2 go up together", text("2. And be not conformed"))
        goLive(sub = "Sends the verses to the projector")
        showLive(sub = "Romans 12:1-2, in both translations")

        type(
            "Or type a reference", "\"John 3:16\", \"jn 3 16\", \"mat 1\" — then Enter",
            searchField(), "John 3:16", enter = true,
        )
        val verse = reveal { text("16. For God so loved the world") }
        step("Double-click to go live", "The quickest way to put a verse up", verse, double = true) {
            verse.node.performTouchInput { doubleClick() }
            ui.waitForIdle()
            settle()
        }
        showLive(sub = "John 3:16 in two languages")
        if (secondBible() != null) {
            swap("Swap which comes first", "One click flips the order", leading = "Ибо так возлюбил")
            showLive("Flipped", "Now the second translation leads")
            swap("Click again to flip back", "Whichever your congregation reads first", leading = "For God so loved")
        }

        val again = text("16. For God so loved the world")
        rightClick("Right-click a verse for more", "Go Live, Add to Schedule, Copy, Cross References", again)
        step("Add to Schedule", "Queue it for later in the service", find(hasText("Add to Schedule")))
        val row = reveal { find(hasText("John 3:16", substring = true)) { it.right < dp(380f) } }
        note("Added to your schedule", "Run the whole service from this list", row)

        step(
            "Cross references",
            "Related verses for the selected one — optional",
            find(hasText("Refs")) { it.left > dp(380f) },
        )
        pause(2.0)

        val modeTip = "Auto guesses; Reference and Text are explicit"
        step("Switch the search to Text", modeTip, find(hasText("Auto")) { it.left > dp(380f) })
        val reference = find(hasText("Reference")) { it.left > dp(380f) && it.top < dp(120f) }
        step("Switch the search to Text", modeTip, reference)
        type("Search for a word", "Every verse that contains it", searchField(), "jesus")
        waitFor("the results are listed") { hasNode(hasText("result(s)", substring = true)) }
        pause(0.3)
        note("Every verse that mentions it", "Matches are highlighted", text("result(s)"))
        val result = findAll(hasText("Jesus", substring = true)) {
            it.left > dp(380f) && it.top > dp(160f) && it.bottom < dp(800f) && it.right < dp(1310f)
        }[2]
        step("Pick a verse", "Jumps straight to it", result)
        goLive(sub = "Puts the verse you picked on screen")
        showLive(sub = "Found by searching a word")
        step(
            "History",
            "Every verse you showed today — click to go back",
            find(hasText("John 3:16", substring = true)) { it.left > dp(380f) && it.top > dp(820f) },
        )
        title("That's the Bible tab", "Browse · Search · Schedule · Go Live", 2.4)
    }

    @Test
    fun songs() = recordTour("songs") {
        title("The Songs tab", "Find a song, show it, run it from the keyboard")
        step("Open the Songs tab", "Your whole song library", tab("Songs"))

        val search = searchField()
        type("Search by title", "The list narrows as you type", search, "grace")
        type("…or by a line of the lyrics", "Each match says where it was found", search, "wretch like me")
        step("Clear the search", "Back to the whole library", find(hasContentDescription("Clear search")))

        step("Pick a song book", "Hymnals, collections, your own", find(hasText("All Song Books")) { it.left > dp(380f) })
        step("Gospel Songs", "Only that book's songs", find(hasText("Gospel Songs")) { it.top < dp(400f) })
        step("Back to every book", "", find(hasText("Gospel Songs")) { it.left > dp(580f) && it.top < dp(120f) })
        step("All Song Books", "", find(hasText("All Song Books")) { it.top > dp(100f) })
        step("Choose how the search matches", "Contains, Starts With or Exact Match", find(hasText("Contains")) { it.left > dp(380f) })
        step("Starts With", "Titles that begin with what you type", find(hasText("Starts With")))
        step("Sort the list", "Click any column heading — again to reverse", find(hasText("Title")) { it.left > dp(380f) })

        step("Click a song", "Its verses appear on the right", text("Amazing Grace"))
        step("Click a verse", "Ready in the preview", text("Amazing grace! how sweet the sound"))
        goLive(sub = "Sends the verse to the projector")
        showLive(sub = "Verse 1 of Amazing Grace")
        press("Next verse", "↓ and ↑ move through the verses while live", text("'Twas grace that taught"), Key.DirectionDown, "↓") {
            settle()
        }
        showLive("Verse 2", "Straight from the keyboard")
        val verse3 = text("Through many dangers")
        step("Double-click a verse", "Goes live in one move", verse3, double = true) {
            verse3.node.performTouchInput { doubleClick() }
            ui.waitForIdle()
            settle()
        }
        showLive(sub = "Verse 3, live")

        val star = find(hasContentDescription("Add to favorites")) { it.left > dp(380f) && it.top > dp(170f) }
        step("Star a song", "Favorites stay pinned above the list", star)
        pause(1.2)
        rightClick("Right-click a song for more", "Go Live, Add to Schedule, Favorites, Edit, Delete", text("It Is Well With My Soul"))
        step("Add to Schedule", "Queue it for the service", find(hasText("Add to Schedule")))
        val row = reveal { find(hasText("It Is Well", substring = true)) { it.right < dp(380f) } }
        note("Added to your schedule", "Run the whole service from this list", row)

        step("Choose the columns", "Number, Title, Song Book, Author, play count…", find(hasContentDescription("Filter columns")))
        pause(1.5)
        quietClick(text("Amazing Grace"))
        note("Edit a song", "Lyrics, chords and transpose, translations, its own look", find(hasContentDescription("Edit Song")))
        note("Write a new song", "Or import one", find(hasContentDescription("New Song")))
        title("That's the Songs tab", "Search · Pick · Go Live · Arrow keys", 2.4)
    }
}

/** Clicks Swap and waits until the verse list leads with [leading], then republishes what is live. */
private fun DemoTour.swap(heading: String, sub: String, leading: String) {
    val swap = find(hasContentDescription("Swap"))
    step(heading, sub, swap) {
        swap.node.performClick()
        waitFor("the swap reached the selection") { selectionLeads(leading) }
        settle()
    }
}

/** A second translation for the two-language part, from this machine's own library; null skips it. */
private fun secondBible(): File? {
    val source = File(
        System.getenv("CP_DEMO_SECOND_BIBLE") ?: "${System.getenv("HOME")}/.churchpresenter/Bibles/RUS_SYN.spb",
    )
    if (!source.isFile) return null
    return File(LIBRARY, "Bibles/${source.name}").also { source.copyTo(it, overwrite = true) }
}
