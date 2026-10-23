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
        tab = "Bible",
        liveLabel = "Project a verse",
        steps = 18,
        startTab = "Songs",
        settings = { s ->
            secondBible()?.let { s.copy(bibleSettings = s.bibleSettings.copy(secondaryBible = it.name)) } ?: s
        },
    ) {
        say("the-bible-tab")
        intro("Search scripture and put any verse on the projector.")
        say("open-tab")
        openTab("Bible", "Every translation you have installed lives here")
        // The tab opens with the search box focused; leaving it collapses a banner and moves the lists.
        quietClick(find(hasText("Genesis")) { it.left > dp(380f) })

        val romans = scrollList("Genesis", hasText("Romans")) { it.left > dp(380f) }
        say("pick-a-book")
        step("Pick a book", "The whole Bible, in order", romans)
        say("pick-a-chapter")
        step("Pick a chapter", "Romans 12", find(hasText("12")) { it.left > dp(580f) && it.right < dp(720f) })
        say("click-a-verse")
        step("Click a verse", "It shows in the preview, ready to go", text("1. I beseech you therefore"))
        say("shift-click-to-select-a-range")
        shiftClick("Shift-click to select a range", "Verses 1 and 2 go up together", text("2. And be not conformed"))
        say("sends-the-verses-to-the-projecto")
        goLive(sub = "Sends the verses to the projector")
        say("romans-12-1-2-in-both-translatio")
        showLive(sub = "Romans 12:1-2, in both translations")

        say("or-type-a-reference")
        type(
            "Or type a reference", "\"John 3:16\", \"jn 3 16\", \"mat 1\" — then Enter",
            searchField(), "John 3:16", enter = true,
        )
        val verse = reveal { text("16. For God so loved the world") }
        say("double-click-to-go-live")
        step("Double-click to go live", "The quickest way to put a verse up", verse, double = true, live = true) {
            verse.node.performTouchInput { doubleClick() }
            ui.waitForIdle()
            settle()
        }
        say("john-3-16-in-two-languages")
        showLive(sub = "John 3:16 in two languages")
        if (secondBible() != null) {
            say("swap-which-comes-first")
            swap("Swap which comes first", "One click flips the order", leading = "Ибо так возлюбил")
            say("flipped")
            showLive("Flipped", "Now the second translation leads")
            say("click-again-to-flip-back")
            swap("Click again to flip back", "Whichever your congregation reads first", leading = "For God so loved")
        }

        val again = text("16. For God so loved the world")
        say("right-click-a-verse-for-more")
        rightClick("Right-click a verse for more", "Go Live, Add to Schedule, Copy, Cross References", again)
        say("add-to-schedule")
        step("Add to Schedule", "Queue it for later in the service", find(hasText("Add to Schedule")))
        val row = reveal { find(hasText("John 3:16", substring = true)) { it.right < dp(380f) } }
        say("added-to-your-schedule")
        note("Added to your schedule", "Run the whole service from this list", row)

        say("cross-references")
        step(
            "Cross references",
            "Related verses for the selected one — optional",
            find(hasText("Refs")) { it.left > dp(380f) },
        )
        pause(2.0)

        val modeTip = "Auto guesses; Reference and Text are explicit"
        say("switch-the-search-to-text")
        step("Switch the search to Text", modeTip, find(hasText("Auto")) { it.left > dp(380f) })
        val reference = find(hasText("Reference")) { it.left > dp(380f) && it.top < dp(120f) }
        step("Switch the search to Text", modeTip, reference)
        say("search-for-a-word")
        type("Search for a word", "Every verse that contains it", searchField(), "jesus")
        waitFor("the results are listed") { hasNode(hasText("result(s)", substring = true)) }
        pause(0.3)
        say("every-verse-that-mentions-it")
        note("Every verse that mentions it", "Matches are highlighted", text("result(s)"))
        val result = findAll(hasText("Jesus", substring = true)) {
            it.left > dp(380f) && it.top > dp(160f) && it.bottom < dp(800f) && it.right < dp(1310f)
        }[2]
        say("pick-a-verse")
        step("Pick a verse", "Jumps straight to it", result)
        say("puts-the-verse-you-picked-on-scr")
        goLive(sub = "Puts the verse you picked on screen")
        say("found-by-searching-a-word")
        showLive(sub = "Found by searching a word")
        say("history")
        step(
            "History",
            "Every verse you showed today — click to go back",
            find(hasText("John 3:16", substring = true)) { it.left > dp(380f) && it.top > dp(820f) },
        )
        say("that-s-the-bible-tab")
        outro("Songs")
    }

    @Test
    fun songs() = recordTour("songs", tab = "Songs", liveLabel = "Lead worship", steps = 19) {
        say("the-songs-tab")
        intro("Find any song and lead worship from the keyboard.")
        say("open-tab")
        openTab("Songs", "Your whole song library")

        val search = searchField()
        say("search-by-title")
        type("Search by title", "The list narrows as you type", search, "grace")
        say("or-by-a-line-of-the-lyrics")
        type("…or by a line of the lyrics", "Each match says where it was found", search, "wretch like me")
        say("clear-the-search")
        step("Clear the search", "Back to the whole library", find(hasContentDescription("Clear search")))

        say("pick-a-song-book")
        step(
            "Pick a song book",
            "Hymnals, collections, your own",
            find(hasText("All Song Books")) { it.left > dp(380f) },
        )
        say("gospel-songs")
        step("Gospel Songs", "Only that book's songs", menuItem("Gospel Songs"))
        say("back-to-every-book")
        step(
            "Back to every book",
            "Open the list again",
            find(hasText("Gospel Songs")) { it.left > dp(580f) && it.top < dp(120f) },
        )
        say("all-song-books")
        step("All Song Books", "The whole library again", menuItem("All Song Books"))
        say("choose-how-the-search-matches")
        step(
            "Choose how the search matches",
            "Contains, Starts With or Exact Match",
            find(hasText("Contains")) { it.left > dp(380f) },
        )
        say("starts-with")
        step("Starts With", "Titles that begin with what you type", menuItem("Starts With"))
        say("sort-the-list")
        step(
            "Sort the list",
            "Click any column heading — again to reverse",
            find(hasText("Title")) { it.left > dp(380f) },
        )

        songsLive()
    }

    private fun DemoTour.songsLive() {
        say("click-a-song")
        step("Click a song", "Its verses appear on the right", text("Amazing Grace"))
        say("click-a-verse")
        step("Click a verse", "Ready in the preview", text("Amazing grace! how sweet the sound"))
        say("sends-the-verse-to-the-projector")
        goLive(sub = "Sends the verse to the projector")
        say("verse-1-of-amazing-grace")
        showLive(sub = "Verse 1 of Amazing Grace")
        say("next-verse")
        press(
            "Next verse",
            "↓ and ↑ move through the verses while live",
            text("'Twas grace that taught"),
            Key.DirectionDown,
            "↓",
        ) {
            settle()
        }
        say("verse-2")
        showLive("Verse 2", "Straight from the keyboard")
        val verse3 = text("Through many dangers")
        say("double-click-a-verse")
        step("Double-click a verse", "Goes live in one move", verse3, double = true, live = true) {
            verse3.node.performTouchInput { doubleClick() }
            ui.waitForIdle()
            settle()
        }
        say("verse-3-live")
        showLive(sub = "Verse 3, live")

        val star = find(hasContentDescription("Add to favorites")) { it.left > dp(380f) && it.top > dp(170f) }
        say("star-a-song")
        step("Star a song", "Favorites stay pinned above the list", star)
        pause(1.2)
        say("right-click-a-song-for-more")
        rightClick(
            "Right-click a song for more",
            "Go Live, Add to Schedule, Favorites, Edit, Delete",
            text("It Is Well With My Soul"),
        )
        say("add-to-schedule")
        step("Add to Schedule", "Queue it for the service", menuItem("Add to Schedule"))
        val row = reveal { find(hasText("It Is Well", substring = true)) { it.right < dp(380f) } }
        say("added-to-your-schedule")
        note("Added to your schedule", "Run the whole service from this list", row)
        say("back-to-live")
        note("Back to Live", "Browsing away from the live song? One click returns to it", text("Back to Live"))

        say("choose-the-columns")
        step(
            "Choose the columns",
            "Number, Title, Song Book, Author, play count…",
            find(hasContentDescription("Filter columns")),
        )
        pause(1.5)
        quietClick(text("Amazing Grace"))
        say("edit-a-song")
        note(
            "Edit a song",
            "Lyrics, chords and transpose, translations, its own look",
            find(hasContentDescription("Edit Song")),
        )
        say("write-a-new-song")
        note("Write a new song", "Or import one", find(hasContentDescription("New Song")))
        say("that-s-the-songs-tab")
        outro("Pictures")
    }
}

/** Clicks Swap and waits until the verse list leads with [leading], then republishes what is live. */
private fun DemoTour.swap(heading: String, sub: String, leading: String) {
    val swap = find(hasContentDescription("Swap"))
    step(heading, sub, swap, live = true) {
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
