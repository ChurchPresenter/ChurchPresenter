@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.dialogs.songEditorBackgroundButton
import org.churchpresenter.songs.EditSongContent
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test

/** Adding and editing songs: lyrics, chords, languages, backgrounds, the song's own look and its timing. */
class EditSongsDemoTour {

    @Test
    fun editSongs() = recordTour(
        "edit-songs",
        tab = "Songs",
        liveLabel = "Write a song",
        steps = 14,
        heading = "Adding & Editing " to "",
        panel = { settings, _ ->
            Box(Modifier.size(EDITOR_W.dp, EDITOR_H.dp)) {
                EditSongContent(
                    song = song.value,
                    backgroundButton = songEditorBackgroundButton,
                    songbooks = listOf("Hymnal", "Gospel Songs"),
                    existingSongs = emptyList(),
                    isNewSong = song.value.title.isBlank(),
                    theme = ThemeMode.DARK,
                    typicalSeconds = TYPICAL_SECONDS,
                    languageNames = listOf("English", "", "", ""),
                    onLanguageNamesChange = {},
                    appSettings = settings,
                    onDismiss = {},
                    onSave = { _, _ -> },
                )
            }
        },
    ) { editSongsTour() }

    private fun DemoTour.editSongsTour() {
        say("intro")
        intro("Lyrics, chords, languages, backgrounds and a look of its own.")
        say("open-tab")
        openTab("Songs", "Your song library")
        say("new-song")
        step("New Song", "The song editor opens", find(hasContentDescription("New Song"))) {}
        openPanel("New Song", EDITOR_W, EDITOR_H)
        menusInPlace = true
        write()
        chords()
        languages()
        backgrounds()
        look()
        say("timing")
        panelNote("How long it usually runs", "Timed as you use it", panelItem(hasText("usually", substring = true)))
        say("save")
        panelNote("Save", "It joins your library", panelItems(hasText("Save")).maxBy { it.bounds.top }, numbered = true)
        closePanel()
        say("edit")
        note("Edit Song", "Change it any time", find(hasContentDescription("Edit Song")))
        say("outro")
        outro()
    }

    /** The title, the details and the lyrics with their sections. */
    private fun DemoTour.write() {
        say("title")
        panelEdit("Song title", "What the song is called", field("ENGLISH TITLE"), "Holy, Holy, Holy")
        say("details")
        panelNote("Song details", "Song book, number, author, CCLI", panelItem(hasText("SONG BOOK")))
        say("lyrics")
        panelEdit("Lyrics", "Each section starts with its name", lyricsField(), LYRICS)
        say("insert")
        panelNote("Insert a section", "Verse, Chorus, Bridge and more", panelItem(hasText("Chorus")), numbered = true)
    }

    private fun DemoTour.chords() {
        say("chords")
        panelNote("Chords", "In brackets, before the syllable", panelItem(hasText("[G]lyric")), numbered = true)
        say("chords-pane")
        panelNote("Chords tab", "The song as the band reads it", rightPane("Chords"), click = true)
        say("transpose")
        panelNote("Transpose", "Up or down a step", panelItem(hasContentDescription("Transpose up")))
        say("palette")
        panelNote("The key's chords", "Click one to insert it", panelItem(hasText("CHORDS IN")))
        say("slide-break")
        panelNote("Slide Break", "Split a long verse", panelItem(hasText("Slide Break")), numbered = true)
        say("slide-split")
        panelNote("Two slides", "Verse 2, one and two", panelItem(hasText("1/2")))
    }

    private fun DemoTour.languages() {
        say("add-language")
        panelNote("Add a language", "Up to five", panelItem(hasText("Add a language")), numbered = true)
        say("language-tab")
        panelNote("Language 2", "Its own lyrics", panelItem(hasText("Language 2")), click = true)
        say("language-name")
        panelEdit("Language name", "Shared by every song", field("LANGUAGE NAME"), "Español")
        say("translation")
        panelEdit("Translated lyrics", "The same sections", lyricsField(), SPANISH)
        say("titles")
        val spanish = "¡Santo! ¡Santo! ¡Santo!"
        panelEdit("A title in each language", spanish, field("ESPAÑOL TITLE"), spanish)
    }

    /** One background for the song, another for its second verse. */
    private fun DemoTour.backgrounds() {
        say("background")
        panelNote(
            "Background",
            "This song's own",
            panelItem(hasText("Background", substring = true)),
            click = true,
            numbered = true,
        )
        say("custom")
        panelNote("Custom", "A color, picture or video", panelItem(hasText("Custom")), click = true)
        say("song-color")
        panelNote("Deep Navy", "For the whole song", panelItem(hasText("Deep Navy")), click = true)
        say("applies-to")
        panelNote("Applies to", "The whole song, or one section", panelItem(hasText("Whole song")), click = true)
        say("verse-two")
        panelNote("Verse 2", "Its own background", panelItems(hasText("Verse 2")).maxBy { it.bounds.top }, click = true)
        say("verse-color")
        panelNote("Ember", "Just for verse two", panelItem(hasText("Ember")), click = true)
        say("save-background")
        panelNote("Save", "Keep the backgrounds", backgroundSave(), click = true)
    }

    /** The Output pane and this song's own font, size and color. */
    private fun DemoTour.look() {
        // Back to the English lyrics, so the preview shows the song as most will see it.
        panelItem(hasText("English")).node.performClick()
        ui.waitForIdle()
        say("output")
        panelNote("Output", "As the projector shows it", rightPane("Output"), click = true, numbered = true)
        say("look")
        panelNote("This song's look", "Font, size and color", panelItem(hasText("This song's look")))
        say("larger")
        val larger = panelItem(hasContentDescription("Larger"))
        panelNote("Larger", "For this song only", larger, click = true)
        larger.node.performClick()
        ui.waitForIdle()
    }

    /** The Background pop-up's own Save, not the editor's. */
    private fun DemoTour.backgroundSave() = panelItems(hasText("Save")).minBy { it.bounds.top }

    /** The text field right under the caption [label]. */
    private fun DemoTour.field(label: String): Target {
        val caption = panelItem(hasText(label)).bounds
        return panelItems(hasSetTextAction()).filter {
            it.bounds.top >= caption.top && it.bounds.left >= caption.left - dp(30f) && it.bounds.left < caption.right
        }.minBy { it.bounds.top }
    }

    private fun DemoTour.lyricsField() = panelItems(hasSetTextAction()).maxBy { it.bounds.height }

    /** A tab of the right-hand pane: Output or Chords. */
    private fun DemoTour.rightPane(label: String) = panelItems(hasText(label)).maxBy { it.bounds.left }
}

private const val EDITOR_W = 1150f
private const val EDITOR_H = 780f
private const val TYPICAL_SECONDS = 262

/** The song open in the editor: blank for a new one. */
private val song = mutableStateOf(SongItem(number = "", title = ""))

private val LYRICS = """
[Verse 1]
[D]Holy, holy, [Bm]holy! [G]Lord God Al[D]mighty!
Early in the [A]morning our [D]song shall rise to [A]Thee;
[D]Holy, holy, [Bm]holy! [G]Merciful and [D]mighty!
[G]God in three [D]Persons, [A]blessed [D]Trinity!

[Verse 2]
[D]Holy, holy, [Bm]holy! [G]All the saints a[D]dore Thee,
Casting down their [A]golden crowns a[D]round the glassy [A]sea;
[---]
[D]Cherubim and [Bm]seraphim [G]falling down be[D]fore Thee,
[G]Which wert, and [D]art, and [A]evermore shalt [D]be.
""".trimIndent()

private val SPANISH = """
[Verse 1]
¡Santo! ¡Santo! ¡Santo! Señor omnipotente,
siempre el labio mío loores te dará;
¡Santo! ¡Santo! ¡Santo! Te adoro reverente,
Dios en tres personas, bendita Trinidad.
""".trimIndent()
