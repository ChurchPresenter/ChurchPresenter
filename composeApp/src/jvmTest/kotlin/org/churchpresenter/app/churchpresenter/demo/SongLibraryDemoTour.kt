@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.screenshot.LIBRARY
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.dialogs.songEditorBackgroundButton
import org.churchpresenter.songlibrary.ui.CompareTranslationsContent
import org.churchpresenter.songlibrary.ui.SongLibraryApp
import org.churchpresenter.songs.EditSongContent
import org.churchpresenter.theme.ThemeMode
import java.io.File
import kotlin.test.Test

/** The Song Library Manager: the whole library in one grid, edited many songs at a time. */
class SongLibraryDemoTour {
    @Test
    fun songLibrary() {
        // A copy, so nothing the tour types reaches the demo library the other tours read.
        val folder = File(System.getProperty("user.home"), "demo-song-library").apply {
            deleteRecursively()
            File(LIBRARY, "Songs").copyRecursively(this)
            File(this, "Hymnal/78 - Holy, Holy, Holy.song").writeText(BILINGUAL)
        }
        val bilingual = File(folder, "Hymnal/78 - Holy, Holy, Holy.song").path
        recordTour(
            "song-library",
            tab = "Song Library Manager",
            liveLabel = "Your library",
            steps = 10,
            heading = "The " to "",
            panel = { settings, _ ->
                Box(Modifier.size(LIBRARY_W.dp, LIBRARY_H.dp)) {
                    SongLibraryApp(
                        libraryFolder = folder,
                        typicalSeconds = { song -> TYPICAL[song.number] },
                        // The row's pencil opens the app's own editor, as in the app.
                        songEditor = { editing ->
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                EditSongContent(
                                    song = editing.song,
                                    backgroundButton = songEditorBackgroundButton,
                                    songbooks = editing.songbooks,
                                    existingSongs = editing.allSongs,
                                    isNewSong = false,
                                    theme = ThemeMode.DARK,
                                    languageNames = listOf("English", "Español", "Deutsch", ""),
                                    onLanguageNamesChange = {},
                                    appSettings = settings,
                                    onDismiss = editing.onDismiss,
                                    onSave = { _, _ -> editing.onDismiss() },
                                )
                            }
                        },
                    )
                    // The comparison opens in a window of its own in the app; here it is drawn in place.
                    comparing.value?.let { song ->
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            CompareTranslationsContent(song, onDismiss = {}, onSave = {})
                        }
                    }
                }
            },
        ) { libraryTour(bilingual) }
    }

    private fun DemoTour.libraryTour(bilingual: String) {
        say("intro")
        intro("Your whole song library in one screen, edited many songs at a time.")
        say("why")
        caption("Many songs at once", "Renumber, fill in, file into books")
        say("open")
        val help = runCatching { find(hasText("Help")) { it.top < dp(60f) } }.getOrNull()
        val keys = "Or Ctrl+Shift+L, Cmd+Shift+L on a Mac"
        if (help != null) note("Help menu", keys, help) else caption("Help menu", keys)
        openPanel("Song Library Manager", LIBRARY_W, LIBRARY_H)
        waitFor("the library to load") { hasNode(hasText("Amazing Grace", substring = true)) }
        grid()
        editing()
        selecting()
        perSong()
        translations(bilingual)
        closePanel()
        say("outro")
        outro()
    }

    private fun DemoTour.grid() {
        say("window")
        point("Every song, one grid", "Number, title, book, author, CCLI", hasText("song books", substring = true))
        say("search")
        val search = seek(hasSetTextAction())
        panelEdit("Search", "Title, author or number", search, "grace")
        search.node.performTextReplacement("")
        pause(0.3)
        say("books")
        point("Song book", "One book, or songs in none", hasText("All Song Books"))
        filterBook("Gospel Songs")
        point("Just that book", "6 of 32 songs", hasText("of 32 songs", substring = true))
        filterBook("All Song Books")
        say("sort")
        panelNote("Sort", "Click a heading", seek(hasText("Title", ignoreCase = true)), click = true, numbered = true)
        say("columns")
        point("Columns", "Show or hide what's in the grid", hasText("Columns"))
        quietPanelClick(hasText("Columns"))
        menusInPlace = true
        val list = menuEntry(hasText("Show all"))
        if (list != null) panelNote("Every column", "Tick to show, untick to hide", list)
        // Escape closes the menu, as it does for a user; it must be gone before Duration is shown.
        runCatching { panelItems(hasText("Show all")).last().node.performKeyInput { pressKey(Key.Escape) } }
        ui.waitForIdle()
        menusInPlace = false
        pause(0.2)
        say("duration")
        scrollGrid(GRID_SCROLL_PX)
        point("Duration", "Measured over real services", hasText("Duration", ignoreCase = true))
        scrollGrid(-GRID_SCROLL_PX)
    }

    /** The grid is wider than the window: Duration and each song's buttons sit past its right edge. */
    private fun DemoTour.scrollGrid(dx: Float) {
        panelItems(hasScrollAction()).forEach { scroller ->
            runCatching { scroller.node.performSemanticsAction(SemanticsActions.ScrollBy) { it(dx, 0f) } }
        }
        ui.waitForIdle()
        pause(0.3)
    }

    private fun DemoTour.editing() {
        say("edit-cell")
        panelNote("Click a field", "Enter keeps it, Escape throws it away", seek(hasText("Carl Boberg")), click = true)
        runCatching {
            val field = seek(hasSetTextAction() and hasText("Carl Boberg"))
            panelEdit("Type", "Changed in place", field, "Carl Gustav Boberg")
            // Enter keeps it, as the narration says; until then Revert has nothing to undo.
            panelItem(hasSetTextAction() and hasText("Carl Gustav Boberg")).node.performKeyInput { pressKey(Key.Enter) }
        }
        pause(0.3)
        say("book-cell")
        point("Song book field", "A menu of your books, never a typo", hasText("Hymnal"))
        say("pending")
        val pending = hasText("unsaved", substring = true) or hasText("Save Changes")
        point("Nothing written yet", "Revert, or Save Changes", pending)
        quietPanelClick(hasText("Revert"))
    }

    private fun DemoTour.selecting() {
        say("select")
        // The first toggle is the header's select-all; the songs' own ticks follow it.
        panelItems(isToggleable()).drop(1).take(3).forEach { tick ->
            runCatching { tick.node.performTouchInput { click() } }
        }
        pause(0.3)
        point("Selected", "Shift-click a range, Ctrl/Cmd-click one", hasText("selected", substring = true))
        say("batch")
        val batch = seek(hasText("Batch Edit", substring = true))
        panelNote("Batch Edit…", "For every selected song", batch, click = true, numbered = true)
        say("batch-fields")
        caption("Tick a field, type its value", "Unticked fields stay as they are")
        // The sheet is a pop-up over the whole window, outside the panel's own bounds.
        runCatching { dialogPopupItem(hasText("Cancel")).node.performClick() }
        ui.waitForIdle()
        pause(0.3)
        say("new-book")
        newSongBook()
        quietPanelClick(hasText("Clear"))
        // The ticks again, should Clear have missed: the next steps want no songs selected.
        if (panelItems(hasText("selected", substring = true)).isNotEmpty()) {
            panelItems(isToggleable()).drop(1).take(3).forEach { tick ->
                runCatching { tick.node.performTouchInput { click() } }
            }
            pause(0.2)
        }
    }

    /**
     * [m] inside the open menu, not its namesake elsewhere: a book's name also fills the grid's Song
     * Book cells, and "All Song Books" is the menu's own button too.
     */
    private fun DemoTour.menuEntry(m: SemanticsMatcher): Target? =
        panelItems(m and hasAnyAncestor(isPopup())).firstOrNull()

    /** Picks [book] in the song book menu, shown open first. */
    private fun DemoTour.filterBook(book: String) {
        // The menu's button, at the top: the book's name also fills the grid's Song Book cells below it.
        quietPanelClick(hasText("All Song Books") or hasText("Gospel Songs"))
        menusInPlace = true
        val entry = menuEntry(hasText(book, substring = true))
        if (entry != null) panelNote(book, "Pick it in the menu", entry, click = true)
        menusInPlace = false
        pause(0.3)
    }

    /** The song book menu's New Song Book…, and the sheet it opens, named and cancelled. */
    private fun DemoTour.newSongBook() {
        quietPanelClick(hasText("All Song Books"))
        // A dropdown is drawn where it opens; the sheet it leads to is centered, like an alert.
        menusInPlace = true
        val entry = menuEntry(hasText("New Song Book", substring = true))
        if (entry == null) {
            caption("New Song Book…", "In the song book menu")
            menusInPlace = false
            return
        }
        panelNote("New Song Book…", "In the song book menu", entry)
        entry.node.performClick()
        ui.waitForIdle()
        menusInPlace = false
        pause(0.3)
        runCatching {
            panelEdit("Name it", "Like Christmas 2026", dialogPopupItem(hasSetTextAction()), "Christmas 2026")
            val assign = dialogPopupItem(hasText("File the", substring = true))
            panelNote("File them under it", "The songs you selected", assign)
            dialogPopupItem(hasText("Cancel")).node.performClick()
            ui.waitForIdle()
        }
        pause(0.3)
    }

    private fun DemoTour.perSong() {
        say("edit-song")
        scrollGrid(GRID_SCROLL_PX)
        val edit = seek(hasContentDescription("Edit song"))
        panelNote("Edit song", "The Songs tab's own editor", edit, numbered = true)
        edit.node.performClick()
        ui.waitForIdle()
        pause(2.0)
        runCatching { quietPanelClick(hasText("Cancel")) }
        say("new-song")
        point("New Song", "Adds a song to the library", hasText("New Song"))
        say("delete")
        point("Delete", "Asked first; it can't be undone", hasContentDescription("Delete song"))
        scrollGrid(-GRID_SCROLL_PX)
    }

    private fun DemoTour.translations(bilingual: String) {
        say("problems")
        val mark = runCatching { seek(hasContentDescription("line up", substring = true)) }.getOrNull()
        if (mark != null) {
            // The tooltip opens under the mark, so it is drawn where it opens.
            menusInPlace = true
            panelHover(mark)
            panelNote("The flashing !", "Hover it to see what's wrong", mark, numbered = true)
            menusInPlace = false
        } else {
            caption("The flashing !", "Hover it to see what's wrong")
        }
        say("compare")
        scrollGrid(GRID_SCROLL_PX)
        val compare = hasContentDescription("Compare translations —", substring = true)
        point("Compare translations", "Amber when sections don't line up", compare)
        comparing.value = SongFileParser().parseSongFile(bilingual, "Hymnal")
        scrollGrid(-GRID_SCROLL_PX)
        pause(0.5)
        point("Side by side", "Every problem marked", hasText("need attention", substring = true))
        say("compare-problem")
        // The first match is verse 2's own mark; the legend at the foot repeats the words.
        point("Verse 2: line count differs", "4 lines in English, 3 in Spanish", hasText("Line count differs"))
        point("What's short", "Language 2 has 3", hasText("has 3", substring = true))
        say("compare-show")
        val third = runCatching { seek(hasText("Language 3", substring = true)) }.getOrNull()
        if (third != null) {
            panelNote("Hide a language", "Click its chip", third, click = true)
            pause(0.6)
            panelNote("Bring it back", "Click it again", seek(hasText("Language 3", substring = true)), click = true)
        } else {
            caption("Hide a language", "Click its chip")
        }
        say("compare-only")
        panelNote("Only problems", "Straight to what needs fixing", seek(hasText("Only problems")), click = true)
        comparing.value = null
    }
}

private val comparing = mutableStateOf<SongItem?>(null)

private const val LIBRARY_W = 1420f
private const val GRID_SCROLL_PX = 2_000f
private const val LIBRARY_H = 880f

private val TYPICAL = mapOf(
    "12" to 262, "78" to 241, "103" to 318, "320" to 274, "455" to 296, "45" to 205, "367" to 287,
)

private val BILINGUAL = """
---
author: Reginald Heber
---

[Primary]
title: Holy, Holy, Holy

[Verse 1]
Holy, holy, holy! Lord God Almighty!
Early in the morning our song shall rise to Thee;
Holy, holy, holy! merciful and mighty!
God in three Persons, blessed Trinity!
[Verse 2]
Holy, holy, holy! all the saints adore Thee,
Casting down their golden crowns around the glassy sea;
Cherubim and seraphim falling down before Thee,
Who wert, and art, and evermore shalt be.

[Secondary]
title: Santo, Santo, Santo

[Verse 1]
¡Santo, Santo, Santo! Señor omnipotente,
siempre el labio mío loores te dará;
¡Santo, Santo, Santo! te adoro reverente,
Dios en tres personas, bendita Trinidad.
[Verse 2]
¡Santo, Santo, Santo! en numeroso coro,
santos escogidos te adoran sin cesar,
de alegría llenos, y sus coronas de oro

[Translation 3]
title: Heilig, heilig, heilig

[Verse 1]
Heilig, heilig, heilig! Herr, Gott, allmächtig!
Früh am Morgen steigt zu dir unser Lied empor;
Heilig, heilig, heilig! gnädig und mächtig!
Gott in drei Personen, selige Dreieinigkeit!
[Verse 2]
Heilig, heilig, heilig! Alle Heil'gen preisen dich,
legen ihre goldnen Kronen nieder am gläsernen Meer;
Cherubim und Seraphim fallen vor dir nieder,
der du warst und bist und ewig sein wirst.
""".trimStart()
