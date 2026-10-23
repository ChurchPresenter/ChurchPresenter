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
            live = true,
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
        // The right-click began a drag of the row; a plain click on it ends that drag.
        quietClick(text("It Is Well With My Soul"))
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

/** The Pictures tour: a folder of photos, sent to the screen one by one or as a slideshow. */
class PicturesDemoTour {

    @Test
    fun pictures() {
        seedRecentPictureFolders()
        recordTour("pictures", tab = "Pictures", liveLabel = "Show a photo", steps = 9) { picturesTour() }
    }

    private fun DemoTour.picturesTour() {
        say("intro")
        intro("Show photos on the big screen, one at a time or as a slideshow.")
        say("open-tab")
        openTab("Pictures", "Every photo in a folder, ready to show")
        waitFor("the thumbnails are decoded") { !hasNode(hasText("Loading...")) }
        pause(0.3)

        say("folder")
        note("Pick a folder", "Every picture in it appears below", find(hasText("Select Folder")))
        say("recents")
        note("Recent folders", "One click brings a folder back", find(hasText("Easter Sunday", substring = true)))
        say("click-photo")
        step("Click a photo", "It shows in the preview first", find(hasText("04 Church")))
        say("go-live")
        goLive(sub = "Sends the photo to the projector")
        say("live-1")
        showLive(sub = "The church photo is on the big screen")

        say("arrow")
        val next = find(hasText("05 Cross"))
        press("Next photo", "→ and ← step through the folder while live", next, Key.DirectionRight, "→", live = true) {
            settle()
        }
        say("live-2")
        showLive("Next photo", "Straight from the keyboard")
        say("double-click")
        val candles = find(hasText("07 Candles"))
        step("Double-click a photo", "Goes live in one move", candles, double = true, live = true) {
            candles.node.performTouchInput { doubleClick() }
            ui.waitForIdle()
            settle()
        }
        say("live-3")
        showLive(sub = "Candles, live")

        say("hide")
        val mountains = find(hasText("09 Mountains")).bounds
        val eye = find(hasContentDescription("Hide — skipped by Next and the slideshow")) {
            it.center.y in mountains.top - dp(10f)..mountains.bottom + dp(10f) && it.left > mountains.left
        }
        step("Hide a photo", "It stays in the folder but the slideshow skips it", eye)
        say("play")
        note("Play a slideshow", "Photos change on their own", find(hasContentDescription("Play")))
        say("interval")
        note("How long each photo stays", "Set the seconds between photos", text("AUTO-SCROLL INTERVAL"))
        say("transition")
        note("Transition duration", "How long the change between photos takes", text("TRANSITION DURATION"))
        say("loop")
        note("Loop the slideshow", "It starts again after the last photo", find(hasContentDescription("Loop On")))
        say("animation")
        step("Choose the transition", "Crossfade, Fade, Slide and more", text("Crossfade"))
        say("fade")
        step("Fade", "A gentle fade between photos", menuItem("Fade"))
        say("scale")
        val scale = find(hasContentDescription("Picture scale", substring = true))
        step("Fit or fill the screen", "One click switches how photos are scaled", scale, live = true) {
            scale.node.performClick()
            ui.waitForIdle()
            settle()
        }
        say("live-4")
        showLive("Filled", "The photo now covers the whole screen")
        say("preset")
        note(
            "Save a preset",
            "A named shortcut to this folder for any future service",
            find(hasContentDescription("Save preset")) { it.left > dp(380f) && it.top < dp(140f) },
        )
        say("schedule")
        step(
            "Add the folder to the schedule",
            "Bring the whole gallery back during the service",
            find(hasContentDescription("Add to Schedule")) { it.left > dp(380f) && it.top < dp(140f) },
        )
        say("schedule-row")
        val row = reveal { findAll(hasText("Gallery (12 images)")) { it.right < dp(380f) }.last() }
        note("Added to your schedule", "Run the whole service from this list", row)
        say("outro")
        outro("Presentation")
    }
}

/**
 * Two more picture folders, and the recents list that names them, so the Pictures tab's recent bar has
 * something to show. Written into the test home before the tab first reads it.
 */
private fun seedRecentPictureFolders() {
    library()
    val gallery = File(LIBRARY, "Gallery")
    val photos = gallery.listFiles()?.sorted().orEmpty()
    val folders = listOf("Easter Sunday" to photos.take(4), "Youth Retreat" to photos.drop(8))
        .map { (name, files) ->
            File(LIBRARY, name).apply {
                mkdirs()
                files.forEach { it.copyTo(File(this, it.name), overwrite = true) }
            }
        }
    val recent = File(System.getProperty("user.home"), ".churchpresenter/recent_picture_folders.json")
    recent.parentFile.mkdirs()
    recent.writeText((listOf(gallery) + folders).joinToString(",", "[", "]") { "\"${it.absolutePath}\"" })
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
