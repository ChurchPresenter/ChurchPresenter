package org.churchpresenter.songs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.composables.SongSectionKind
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class EditSongOrderTest {

    private val lyrics = listOf(
        "[Verse 1]", "Amazing grace how sweet the sound",
        "{Chorus}", "Praise the Lord",
        "[Verse 2]", "Twas grace that taught my heart",
        "[Bridge]", "Through many dangers",
    )

    private val song = SongItem(number = "12", title = "Amazing Grace", lyrics = lyrics)

    private class Saved {
        var song: SongItem? = null
    }

    private fun editor(
        song: SongItem = this.song,
        block: ComposeUiTest.(Saved) -> Unit,
    ) {
        val saved = Saved()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    EditSongContent(
                        backgroundButton = testBackgroundButton,
                        song = song,
                        songbooks = listOf("Hymnal"),
                        existingSongs = emptyList(),
                        isNewSong = false,
                        theme = ThemeMode.LIGHT,
                        appSettings = AppSettings(),
                        onDismiss = {},
                        onSave = { s, _ -> saved.song = s },
                    )
                }
            }
            // The sections are worked out off the event thread; the chips arrive once they are.
            waitUntil(timeoutMillis = WAIT_MS) {
                onAllNodesWithTag(ORDER_CHIP_TAG + "[Verse 1]").fetchSemanticsNodes().isNotEmpty()
            }
            block(saved)
        }
    }

    // The order column and the right pane sit past the test window's right edge, so every control
    // here is pressed through its click action rather than a pointer.
    private fun SemanticsNodeInteraction.press() = performSemanticsAction(SemanticsActions.OnClick)

    private fun ComposeUiTest.pressText(text: String) {
        onNodeWithText(text).press()
        waitForIdle()
    }

    private fun ComposeUiTest.save() = pressText("Save")

    @Test
    fun `a song with no order shows its default, and saving keeps none`() = editor { saved ->
        onNodeWithText("Default").assertExistsOnce()
        // The chorus repeats after each verse by default.
        onNodeWithText("5 slides").assertExistsOnce()
        save()
        assertEquals(emptyList(), saved.song?.sectionOrder)
    }

    @Test
    fun `a preset becomes the song's order`() = editor { saved ->
        onNodeWithTag(ORDER_PRESETS_TAG).press()
        waitForIdle()
        pressText("Chorus after each verse")
        onNodeWithText("Custom").assertExistsOnce()
        save()
        assertEquals(listOf("[Verse 1]", "{Chorus}", "[Verse 2]", "{Chorus}"), saved.song?.sectionOrder)
    }

    @Test
    fun `the presets that need a bridge are offered when the song has one`() = editor { saved ->
        onNodeWithTag(ORDER_PRESETS_TAG).press()
        waitForIdle()
        pressText("Bridge before last chorus")
        save()
        assertEquals(
            listOf("[Verse 1]", "{Chorus}", "[Verse 2]", "{Chorus}", "[Bridge]", "{Chorus}"),
            saved.song?.sectionOrder,
        )
    }

    @Test
    fun `Add section puts a section at the end of the order`() = editor { saved ->
        onNodeWithTag(ORDER_ADD_SECTION_TAG).press()
        waitForIdle()
        onAllNodesWithText("unused").assertCountEquals(0)
        onAllNodesWithText("×1").assertCountEquals(3)
        onAllNodesWithText("×2").assertCountEquals(1)
        // The menu's entry: the only node with both the name and how often the order plays it.
        onNode(hasText("Bridge") and hasText("×1")).press()
        waitForIdle()
        save()
        assertEquals(
            listOf("[Verse 1]", "{Chorus}", "[Verse 2]", "{Chorus}", "[Bridge]", "[Bridge]"),
            saved.song?.sectionOrder,
        )
    }

    @Test
    fun `removing a section takes it out of the order`() = editor(
        song.copy(sectionOrder = listOf("[Verse 1]", "{Chorus}", "[Verse 2]")),
    ) { saved ->
        onNodeWithContentDescription("Remove Chorus").press()
        waitForIdle()
        save()
        assertEquals(listOf("[Verse 1]", "[Verse 2]"), saved.song?.sectionOrder)
    }

    @Test
    fun `an order entry the lyrics no longer have is flagged and dropped on save`() = editor(
        song.copy(sectionOrder = listOf("[Verse 1]", "[Verse 9]")),
    ) { saved ->
        onNodeWithText("Not in lyrics").assertExistsOnce()
        save()
        assertEquals(listOf("[Verse 1]"), saved.song?.sectionOrder)
    }

    @Test
    fun `the In order tab plays the song in its order and marks repeats`() = editor(
        song.copy(sectionOrder = listOf("[Verse 1]", "{Chorus}", "[Verse 2]", "{Chorus}", "[Verse 9]")),
    ) { _ ->
        onNodeWithTag(RIGHT_PANE_IN_ORDER_TAG).press()
        waitForIdle()
        onNodeWithText("Repeat").assertExistsOnce()
        onNodeWithText("Not in lyrics").assertExistsOnce()
        onNodeWithText("NOT IN LYRICS").assertExistsOnce()
        // One cue field per section it plays, the repeat and the missing one taking none.
        onAllNodes(hasTestTagStartingWith(SECTION_CUE_FIELD_TAG)).assertCountEquals(3)
    }

    @Test
    fun `a cue typed in the In order tab is saved with its section`() = editor { saved ->
        onNodeWithTag(RIGHT_PANE_IN_ORDER_TAG).press()
        waitForIdle()
        onNodeWithTag(SECTION_CUE_FIELD_TAG + "[Verse 1]").performTextReplacement("Piano\nonly ")
        waitForIdle()
        save()
        assertEquals(mapOf("[Verse 1]" to "Piano only"), saved.song?.sectionNotes)
    }

    @Test
    fun `clearing a cue takes it off the song`() = editor(
        song.copy(sectionNotes = mapOf("[Verse 1]" to "Piano only", "{Chorus}" to "Full band")),
    ) { saved ->
        onNodeWithTag(RIGHT_PANE_IN_ORDER_TAG).press()
        waitForIdle()
        onNodeWithText("Piano only").assertExistsOnce()
        onNodeWithTag(SECTION_CUE_FIELD_TAG + "[Verse 1]").performTextReplacement("")
        waitForIdle()
        save()
        assertEquals(mapOf("{Chorus}" to "Full band"), saved.song?.sectionNotes)
    }

    @Test
    fun `a cue whose section is gone from the lyrics is not saved`() = editor(
        song.copy(sectionNotes = mapOf("[Verse 9]" to "Gone", "[Verse 2]" to " ")),
    ) { saved ->
        save()
        assertEquals(emptyMap(), saved.song?.sectionNotes)
    }

    @Test
    fun `cues still written keep their sections and lose their padding`() {
        assertEquals(
            mapOf("[Verse 1]" to "Piano"),
            sectionNotesStillWritten(
                mapOf("[Verse 1]" to " Piano ", "[Verse 9]" to "Gone", "{Chorus}" to "  "),
                lyrics,
                title = "",
                number = "",
            ),
        )
        assertEquals(emptyMap(), sectionNotesStillWritten(emptyMap(), lyrics, "", ""))
    }

    @Test
    fun `a written section abbreviates by its first letter and its number`() {
        val sections = editSongSectionsOf("[1 куплет]\na\n{Chorus}\nb\n[]\nc", repeatChorus = false).written
        assertEquals(listOf("К1", "C", ""), sections.map { it.abbreviation })
        assertEquals(SongSectionKind.CHORUS, sections[1].kind)
        assertNull(editSongSectionsOf("", repeatChorus = false).written.firstOrNull()?.label?.ifEmpty { null })
    }

    @Test
    fun `the menus and the In order tab name an untitled section and an unused one`() = editor(
        song.copy(
            lyrics = listOf("an opening line") + lyrics,
            sectionOrder = listOf("[Verse 1]", "{Chorus}"),
        ),
    ) { _ ->
        onNodeWithTag(ORDER_ADD_SECTION_TAG).press()
        waitForIdle()
        onNodeWithText("Untitled section").assertExistsOnce()
        onAllNodesWithText("unused").assertCountEquals(3)
        onNode(hasText("Untitled section") and hasText("unused")).press()
        waitForIdle()
        onNodeWithTag(RIGHT_PANE_IN_ORDER_TAG).press()
        waitForIdle()
        // Its chip in the Order column, and its label, upper-cased, in the In order tab.
        onNodeWithText("Untitled section").assertExistsOnce()
        onNodeWithText("UNTITLED SECTION").assertExistsOnce()
    }

    @Test
    fun `dragging a chip past the end moves its section last`() = editor(
        song.copy(sectionOrder = listOf("[Verse 1]", "{Chorus}", "[Verse 2]", "[Bridge]")),
    ) { saved ->
        // Well past the last chip, in steps: the drop lands on the last place, whatever the slop took.
        onNodeWithTag(ORDER_CHIP_TAG + "[Verse 1]").performTouchInput {
            down(center)
            repeat(DRAG_STEPS) { moveBy(Offset(0f, height.toFloat())) }
            up()
        }
        waitForIdle()
        save()
        assertEquals(listOf("{Chorus}", "[Verse 2]", "[Bridge]", "[Verse 1]"), saved.song?.sectionOrder)
    }

    @Test
    fun `presets need a chorus and a verse, and the bridge ones a bridge`() {
        fun presets(text: String) = orderPresetsOf(editSongSectionsOf(text, repeatChorus = false)).map { it.kind }
        assertEquals(listOf(OrderPresetKind.DEFAULT), presets("[Verse 1]\na\n[Verse 2]\nb"))
        assertEquals(listOf(OrderPresetKind.DEFAULT), presets("{Chorus}\na\n[Bridge]\nb"))
        assertEquals(
            listOf(
                OrderPresetKind.DEFAULT,
                OrderPresetKind.CHORUS_AFTER_EACH_VERSE,
                OrderPresetKind.START_WITH_CHORUS,
                OrderPresetKind.END_WITH_DOUBLE_CHORUS,
            ),
            presets("[Verse 1]\na\n{Chorus}\nb"),
        )
        assertEquals(OrderPresetKind.entries, presets("[Verse 1]\na\n{Chorus}\nb\n[Bridge]\nc"))
    }

    @Test
    fun `a chorus named in square brackets repeats after each verse and is not taken for a verse`() {
        val text = "[Verse 1]\na\n[Chorus]\nb\n[Verse 2]\nc\n[Verse 3]\nd"
        val sections = editSongSectionsOf(text, repeatChorus = true)
        val eachVerse = listOf("[Verse 1]", "[Chorus]", "[Verse 2]", "[Chorus]", "[Verse 3]", "[Chorus]")

        assertEquals(eachVerse, sections.defaultOrder)
        assertEquals(
            eachVerse,
            orderPresetsOf(sections).single { it.kind == OrderPresetKind.CHORUS_AFTER_EACH_VERSE }.keys,
        )
        assertEquals(
            listOf("[Verse 1]", "[Chorus]", "[Verse 2]", "[Verse 3]"),
            editSongSectionsOf(text, repeatChorus = false).defaultOrder,
        )
    }

    @Test
    fun `a chorus named in another language repeats too`() {
        val sections = editSongSectionsOf("[Куплет 1]\na\n[Припев]\nb\n[Куплет 2]\nc", repeatChorus = true)
        assertEquals(listOf("[Куплет 1]", "[Припев]", "[Куплет 2]", "[Припев]"), sections.defaultOrder)
    }

    @Test
    fun `an order that names no written section sings the song as written`() {
        val slides = lyricSlidesOf(lyrics, title = "", number = "")
        val plan = SectionPlan(keys = listOf("[Verse 1]", "{Chorus}", "[Verse 2]", "[Bridge]"), order = listOf(9))
        assertEquals(slides.map { it.header }, arrangeSections(slides, plan).map { it.header })
    }

    @Test
    fun `without an order the chorus repeats after each verse when Settings say so`() {
        val slides = lyricSlidesOf(lyrics, title = "", number = "")
        val plan = SectionPlan(keys = listOf("[Verse 1]", "{Chorus}", "[Verse 2]", "[Bridge]"), repeatChorus = true)
        assertEquals(
            listOf("[Verse 1]", "{Chorus}", "[Verse 2]", "{Chorus}", "[Bridge]"),
            arrangeSections(slides, plan).map { it.header },
        )
        assertEquals(
            listOf("[Verse 1]", "{Chorus}", "[Verse 2]", "{Chorus}", "[Bridge]"),
            arrangeSections(slides, plan).map { it.sectionKey },
        )
    }

    private fun SemanticsNodeInteraction.assertExistsOnce() = assertExists()

    private fun hasTestTagStartingWith(prefix: String) = SemanticsMatcher("test tag starts with $prefix") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val DRAG_STEPS = 8
    }
}
