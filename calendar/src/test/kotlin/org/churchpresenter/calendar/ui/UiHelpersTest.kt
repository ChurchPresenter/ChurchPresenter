@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.time.LocalTime
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The small composables and helpers every sheet is built from, each on its own. */
class UiHelpersTest {

    private fun themed(body: ComposeUiTest.() -> Unit, content: @Composable () -> Unit) = runComposeUiTest {
        setContent { AppThemeWrapper(theme = ThemeMode.LIGHT) { content() } }
        waitForIdle()
        body()
    }

    // ── Scrollable containers ───────────────────────────────────────────────────

    @Test
    fun `a scrollable list draws its rows with or without its options`() = themed({
        onNodeWithText("plain").assertExists()
        onNodeWithText("spaced").assertExists()
    }) {
        ScrollableList(Modifier.height(200.dp)) { item { Text("plain") } }
        ScrollableList(
            Modifier.height(200.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(2.dp),
        ) { item { Text("spaced") } }
    }

    @Test
    fun `a scrollable grid draws its cells with or without its options`() = themed({
        onNodeWithText("cell").assertExists()
        onNodeWithText("spaced cell").assertExists()
    }) {
        ScrollableGrid(GridCells.Fixed(2), Modifier.height(200.dp)) { item { Text("cell") } }
        ScrollableGrid(
            GridCells.Fixed(2),
            Modifier.height(200.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { item { Text("spaced cell") } }
    }

    @Test
    fun `a scrollable column draws its content with or without its options`() = themed({
        onNodeWithText("body").assertExists()
        onNodeWithText("padded body").assertExists()
    }) {
        ScrollableColumn(Modifier.height(200.dp)) { Text("body") }
        ScrollableColumn(
            Modifier.height(200.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(2.dp),
        ) { Text("padded body") }
    }

    // ── Selectors ───────────────────────────────────────────────────────────────

    @Test
    fun `a segmented selector reports the option that was clicked`() {
        var chosen = "a"
        themed({
            onNodeWithText("B").performClick()
            waitForIdle()
            assertEquals("b", chosen)
        }) {
            var selected by remember { mutableStateOf("a") }
            SegmentedSelector(
                options = listOf("a", "b"),
                selected = selected,
                label = { it.uppercase() },
                onSelect = {
                    selected = it
                    chosen = it
                },
            )
        }
    }

    @Test
    fun `an option chip is drawn selected or not and clicks through`() {
        var clicks = 0
        themed({
            onNodeWithText("On").performClick()
            onNodeWithText("Off").performClick()
            waitForIdle()
            assertEquals(2, clicks)
        }) {
            OptionChip("On", selected = true) { clicks++ }
            OptionChip("Off", selected = false) { clicks++ }
        }
    }

    // ── Fields ──────────────────────────────────────────────────────────────────

    @Test
    fun `a compact field shows its placeholder only while empty`() = themed({
        onNodeWithText("Type here").assertExists()
        onNodeWithText("Filled").assertExists()
        onNodeWithText("Never seen").assertDoesNotExist()
    }) {
        CompactTextField(value = "", onValueChange = {}, placeholder = "Type here", focused = true)
        CompactTextField(
            value = "Filled",
            onValueChange = {},
            placeholder = "Never seen",
            errorBorder = true,
            leading = { Text("»") },
        )
    }

    @Test
    fun `pressing Enter in a field commits what changed`() {
        var commits = 0
        themed({
            onNode(hasSetTextAction()).performTextInput("x")
            onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
            waitForIdle()
            assertEquals(1, commits)
        }) {
            var text by remember { mutableStateOf("") }
            CompactTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.commitOnExit(hasChanges = text.isNotEmpty()) { commits++ },
            )
        }
    }

    @Test
    fun `Enter with nothing changed, and any other key, commit nothing`() {
        var commits = 0
        themed({
            onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
            onNode(hasSetTextAction()).performKeyInput { pressKey(Key.A) }
            waitForIdle()
            assertEquals(0, commits)
        }) {
            CompactTextField(
                value = "",
                onValueChange = {},
                modifier = Modifier.commitOnExit(hasChanges = false) { commits++ },
            )
        }
    }

    // ── Timing drafts ───────────────────────────────────────────────────────────

    @Test
    fun `a draft with a start time is pinned, and one that follows keeps following only without a start`() {
        val pinned = TimingDraft(startText = "9:40", followsPrevious = true, repeats = 2, atEnd = RowEnd.NEXT)
            .toTiming()
        val follows = TimingDraft(followsPrevious = true).toTiming()

        assertEquals(RowTiming(startAt = "09:40", repeats = 2, atEnd = RowEnd.NEXT), pinned)
        assertEquals(RowTiming(followsPrevious = true), follows)
        assertNull(TimingDraft(startText = "   ").startTime())
    }

    @Test
    fun `a draft is read back from a timing with its repeats and its length`() {
        val draft = TimingDraft.of(RowTiming(startAt = "09:40", repeats = 3), plannedSeconds = 90, use24Hour = true)
        val bare = TimingDraft.of(RowTiming.DEFAULT, plannedSeconds = null, use24Hour = true)

        assertEquals("09:40", draft.startText)
        assertEquals("3", draft.repeatsText)
        assertEquals("1:30", draft.durationText)
        assertEquals("", bare.startText)
        assertEquals("", bare.repeatsText)
        assertEquals("", bare.durationText)
    }

    private fun summaries(vararg drafts: TimingDraft): List<String> {
        val out = mutableListOf<String>()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalUse24HourClock provides true) {
                    drafts.forEach { out += timingSummary(it) }
                }
            }
            waitForIdle()
        }
        return out
    }

    @Test
    fun `the timing summary says how a row starts, runs and ends`() {
        val lines = summaries(
            TimingDraft(startText = "9:40", durationText = "2:00", atEnd = RowEnd.NEXT),
            TimingDraft(repeats = 0, durationText = "2:00", atEnd = RowEnd.BLANK),
            TimingDraft(repeats = 0),
            TimingDraft(repeats = 3, durationText = "1:00"),
            TimingDraft(repeats = 3),
            TimingDraft(),
        )

        assertEquals("Starts on its own at 09:40 · runs 2:00 · then advances", lines[0])
        assertEquals("Starts when cued · loops for 2:00 · then blanks", lines[1])
        assertEquals("Starts when cued · loops until cued · then holds", lines[2])
        assertEquals("Starts when cued · plays 3× (3:00) · then holds", lines[3])
        assertEquals("Starts when cued · plays 3× (uses the item’s own length) · then holds", lines[4])
        assertEquals("Starts when cued · uses the item’s own length · then holds", lines[5])
    }

    // ── The run clock ───────────────────────────────────────────────────────────

    private fun withRunClock(service: PlannedService?, body: (() -> RunClockState) -> Unit) {
        lateinit var state: RunClockState
        runComposeUiTest {
            setContent { state = rememberRunClock(service, today = TODAY, clock = { LocalTime.of(9, 0) }) }
            waitForIdle()
            body {
                waitForIdle()
                state
            }
        }
    }

    @Test
    fun `a service on another day has no clock, and stepping one previews from before its start`() {
        withRunClock(service(date = TODAY.plusDays(7), start = "10:00")) { clock ->
            assertNull(clock().now)
            assertFalse(clock().previewing)

            clock().step()
            assertEquals(LocalTime.of(9, 45), clock().now, "twenty minutes before, plus one step")
            assertTrue(clock().previewing)

            clock().step()
            assertEquals(LocalTime.of(9, 50), clock().now)

            clock().reset()
            assertNull(clock().now)
        }
    }

    @Test
    fun `today's service reads the wall clock and steps on from it`() {
        withRunClock(service(date = TODAY, start = "10:00")) { clock ->
            assertEquals(LocalTime.of(9, 0), clock().now)

            clock().step()
            assertEquals(LocalTime.of(9, 5), clock().now, "on from now, not back to the start")
        }
    }

    @Test
    fun `with no service there is nothing to step`() {
        withRunClock(null) { clock ->
            clock().step()
            assertNull(clock().now)
            assertFalse(clock().previewing)
        }
    }

    // ── Thumbnails ──────────────────────────────────────────────────────────────

    private fun blank(width: Int, height: Int) = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

    @Test
    fun `a large picture is scaled down, a small one kept, and a non-picture is nothing`() {
        val folder = Files.createTempDirectory("calendar-thumbs").toFile()
        try {
            val big = File(folder, "big.png").also { ImageIO.write(blank(400, 200), "png", it) }
            val small = File(folder, "small.png").also { ImageIO.write(blank(20, 10), "png", it) }
            val text = File(folder, "notes.txt").also { it.writeText("not a picture") }

            val scaled = assertNotNull(thumbnailOf(big))
            assertEquals(192, scaled.width)
            assertEquals(96, scaled.height)
            assertEquals(20, assertNotNull(thumbnailOf(small)).width)
            assertNull(thumbnailOf(text))
        } finally {
            folder.deleteRecursively()
        }
    }
}
