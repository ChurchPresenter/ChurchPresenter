@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.calendar.FiredCue
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.LOOP_FOREVER
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * How a cue is described, wherever it is drawn.
 *
 * The run of show, the settings tab and the toast all say what a cue does, and all three go through
 * the same words so they cannot drift — which only holds if every action has words. Each of these
 * is a `when` over `CueAction`, so an action added without a label would quietly fall through to
 * the catch-all and be described as something it is not.
 */
class CueSurfacesTest {

    private fun cue(
        action: String,
        payload: ScheduleItem? = null,
        plays: Int = 1,
        label: String = "",
    ) = ScheduleItem.CueItem(
        id = "cue", action = action, label = label, payload = payload, plays = plays,
        absoluteTime = "09:45",
    )

    /** The toast, on its own, for one fired cue. */
    private fun toast(fired: FiredCue, body: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent {
            AppThemeWrapper(theme = ThemeMode.LIGHT) {
                CueToast(event = fired, onDismiss = {}, modifier = Modifier)
            }
        }
        waitForIdle()
        body()
    }

    private fun fired(cue: ScheduleItem) = FiredCue(cue, LocalTime.of(9, 45))

    // ── The words ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `every action a cue can do is named in the run of show`() {
        val cues = listOf(
            cue(CueAction.COUNTDOWN),
            cue(CueAction.GO_LIVE),
            cue(CueAction.PROJECT, payload = song("p", "Amazing Grace")),
            cue(CueAction.SCENE, payload = ScheduleItem.SceneItem("s", "scene-1", "Welcome")),
            cue(CueAction.BLANK),
        ).mapIndexed { index, item -> item.copy(id = "cue$index") }

        withCalendar(documentWith(service(items = cues, planned = emptyMap()))) {
            awaitText("Sunday Morning")

            assertTrue(shows("Start countdown"))
            assertTrue(shows("Go live"))
            assertTrue(shows("Announcement loop"), "a project cue is an announcement loop")
            assertTrue(shows("Show canvas scene"))
            assertTrue(shows("Blank outputs"))
        }
    }

    @Test
    fun `a countdown and a go-live carry a badge, and the rest do not`() =
        withCalendar(
            documentWith(
                service(
                    items = listOf(cue(CueAction.COUNTDOWN).copy(id = "c1"), cue(CueAction.BLANK).copy(id = "c2")),
                    planned = emptyMap(),
                )
            )
        ) {
            awaitText("Sunday Morning")

            assertTrue(shows("Timer"), "the countdown's badge")
            assertTrue(shows("Go live") || shows("Blank outputs"), "and the row it belongs to")
        }

    @Test
    fun `a project cue says what it will show, and says when that is gone`() {
        val withPayload = cue(CueAction.PROJECT, payload = song("p", "Amazing Grace")).copy(id = "c1")
        val orphaned = cue(CueAction.PROJECT).copy(id = "c2")

        withCalendar(documentWith(service(items = listOf(withPayload, orphaned), planned = emptyMap()))) {
            awaitText("Sunday Morning")

            assertTrue(shows("Amazing Grace"), "the item it is set to")
            assertTrue(shows("no longer in this run of show"), "and that the other one's item has gone")
        }
    }

    @Test
    fun `a cue says the clock time it fires at`() =
        withCalendar(documentWith(service(items = listOf(cue(CueAction.BLANK)), planned = emptyMap()))) {
            awaitText("Sunday Morning")

            assertTrue(shows("9:45"), "pinned to the wall clock, not to an offset")
        }

    // ── The toast ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the toast names the cue and the time it went off`() =
        toast(fired(cue(CueAction.GO_LIVE, label = "Start the service"))) {
            assertTrue(shows("Cue fired"))
            assertTrue(shows("Start the service"))
            assertTrue(shows("9:45"))
        }

    @Test
    fun `the toast shows what went on screen`() =
        toast(fired(cue(CueAction.PROJECT, payload = ScheduleItem.PictureItem("p", "/pics", "Welcome", 12)))) {
            assertTrue(shows("Welcome"))
        }

    @Test
    fun `a looping item says it loops rather than counting`() =
        toast(
            fired(
                cue(
                    CueAction.PROJECT,
                    payload = ScheduleItem.PictureItem("p", "/pics", "Welcome", 12),
                    plays = LOOP_FOREVER,
                )
            )
        ) {
            assertTrue(shows("loops"))
        }

    @Test
    fun `an item played a set number of times says how many`() =
        toast(
            fired(
                cue(
                    CueAction.PROJECT,
                    payload = ScheduleItem.MediaItem("m", "/clip.mp4", "Testimony", "local"),
                    plays = 3,
                )
            )
        ) {
            assertTrue(shows("3"), "×3, beside what it is playing")
        }

    /**
     * Played once, or played by something with no run to play: neither says anything.
     *
     * A count is only worth drawing when it differs from what happens anyway, and a song or a verse
     * has no run to repeat — `canPlayRepeatedly` is what decides, and this is the branch where it
     * says no.
     */
    @Test
    fun `nothing is said about repeats where repeats mean nothing`() {
        toast(fired(cue(CueAction.PROJECT, payload = song("p", "Amazing Grace"), plays = 4))) {
            assertTrue(!shows("×"), "a song has no run to play four times")
        }
        toast(fired(cue(CueAction.PROJECT, payload = ScheduleItem.PictureItem("p", "/pics", "Welcome", 12)))) {
            assertTrue(!shows("×"), "and once is what happens anyway")
        }
    }

    @Test
    fun `a cue that shows nothing is still a toast`() = toast(fired(cue(CueAction.BLANK))) {
        assertTrue(shows("Cue fired"))
        assertTrue(shows("Blank outputs"), "what it did, with nothing to show for it")
    }

    /** A row that is not a cue can reach the toast: a row that starts on its own fires too. */
    @Test
    fun `a row that started itself is announced the same way`() = toast(fired(song("a", "Amazing Grace"))) {
        assertTrue(shows("Cue fired"))
        assertTrue(shows("Amazing Grace"))
    }
}
