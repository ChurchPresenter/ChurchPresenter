package org.churchpresenter.app.churchpresenter.remote

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.propsOnAir
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ClearGroup
import org.churchpresenter.settings.MessageTemplate
import org.churchpresenter.settings.PropDefinition
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.showcontrol.ActionRunner
import org.churchpresenter.showcontrol.MediaCommand
import org.churchpresenter.showcontrol.ShowHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * What the app's show host keeps true after any run of show-control actions, not only the ones
 * [AppShowHostTest] scripts: the order an operator, a Companion button, MIDI, OSC and a remote can
 * fire them in together. Each run goes through [ActionRunner] to a real [PresenterManager], as a
 * fired cue does, and every action is checked against the state before it.
 *
 * The seed is pinned, so every run plays the same sequences, and kotest shrinks a failing one to
 * the fewest actions that still fail.
 */
class AppShowHostPropertyTest {

    private val song = ScheduleItem.SongItem("s1", 1, "Amazing", "Hymns", "Hymns::1")
    private val heading = ScheduleItem.LabelItem("h", "Word", "#FFFFFF", "#000000")
    private val verse = ScheduleItem.SongItem("s2", 2, "Grace", "Hymns", "Hymns::2")
    private val rows = listOf(song, heading, verse)
    private val contentRowIds = setOf(song.id, verse.id)

    private val clearText = ClearGroup("clear1", "Clear text", listOf("SLIDE", "MESSAGES"))
    private val baseSettings = AppSettings(
        clearGroups = listOf(clearText),
        messageTemplates = listOf(MessageTemplate("msg1", "Nursery", "Parent of {number}", durationSeconds = 30)),
        props = listOf(PropDefinition("prop1", "Logo")),
    )

    /** The layers that come down whole when cleared by name -- the slide and media follow their content. */
    private val wholeLayers = listOf(Layer.CAPTIONS, Layer.GRAPHICS, Layer.ANNOUNCEMENTS, Layer.MESSAGES, Layer.PROPS)

    private val rowId = Arb.element("s1", "s2", "h", "nope")
    private val action: Arb<Action> = Arb.choice(
        rowId.map { Action.GoLive(rowId = it) },
        rowId.map { Action.ToPreview(rowId = it) },
        Arb.element("", "SLIDE").map { Action.Take(it) },
        Arb.element(wholeLayers.map { it.name.lowercase() } + listOf("slide", "background", "stage"))
            .map { Action.Clear(it) },
        Arb.constant(Action.ClearAll),
        Arb.element("clear text", "clear1", "x").map { Action.ClearGroup(it) },
        Arb.element("Hello", " ").map { Action.Message(text = it) },
        Arb.element("msg1", "Choir").map { Action.Message(template = it, tokens = mapOf("number" to "12")) },
        Arb.bind(Arb.element("logo", "prop1", "Clock"), Arb.boolean().orNull()) { prop, on -> Action.Prop(prop, on) },
        Arb.element("Pastor", "").map { Action.LowerThird(it) },
        Arb.element(
            Action.Timer(TimerModes.DURATION, seconds = 90),
            Action.Timer(TimerModes.COUNT_UP),
            Action.Timer(TimerModes.CLOCK, until = "23:59"),
            Action.Timer(TimerModes.CLOCK, until = "soon"),
            Action.Timer(TimerModes.CLOCK_DISPLAY),
            Action.Timer("lap"),
        ),
        Arb.element(MediaCommand.entries).map { Action.Media(it) },
        Arb.constant(Action.NextItem),
        Arb.constant(Action.PreviousItem),
        Arb.constant(Action.Wait(0.0)),
        Arb.element("walk in", "loop", "nope").map { Action.RunMacro(it) },
    )

    /** One run's app: a fresh output, the schedule's current row, and what the host reported. */
    private inner class Show(previewMode: Boolean) {
        val pm = PresenterManager(showPresenterWindowInitially = false)
        var current: String? = null
        val errors = mutableListOf<Pair<Action, Throwable>>()
        private val settings =
            baseSettings.copy(projectionSettings = ProjectionSettings(previewModeEnabled = previewMode))
        private val appHost = AppShowHost(
            pm,
            { settings },
            ShowOutlets(
                rows = { rows },
                currentRowId = { current },
                goLive = { item, _ ->
                    current = item.id
                    pm.setPresentingMode(Presenting.LYRICS)
                },
                macro = { name ->
                    when (name) {
                        "walk in" -> listOf(Action.ClearAll, Action.Message(text = "Welcome"))
                        "loop" -> listOf(Action.RunMacro("loop"))
                        else -> null
                    }
                },
            ),
        )

        /** The app's host, with what it reports kept so each failure's type can be checked. */
        val host: ShowHost = object : ShowHost by appHost {
            override fun reportError(action: Action, error: Throwable) {
                errors += action to error
                appHost.reportError(action, error)
            }
        }
    }

    @Test
    fun `every run of actions leaves the live output as the actions asked`() = runBlocking<Unit> {
        checkAll(
            PropTestConfig(seed = 20_261_011L, iterations = 200),
            Arb.boolean(),
            Arb.list(action, 1..30),
        ) { previewMode, actions ->
            val show = Show(previewMode)
            try {
                actions.forEach { step(show, it) }
            } finally {
                show.pm.pauseAnnouncementTimer()
            }
        }
    }

    private suspend fun step(show: Show, action: Action) {
        val pm = show.pm
        val before = pm.program.value
        val reported = show.errors.size
        val wasCurrent = show.current
        coroutineScope { ActionRunner(show.host, this).perform(listOf(action)) }
        val after = pm.program.value
        val refused = show.errors.size > reported

        show.errors.drop(reported).forEach { (failed, error) ->
            if (error !is IllegalArgumentException && error !is IllegalStateException) {
                fail("$failed failed with ${error::class.simpleName}, not a refusal: ${error.message}")
            }
        }
        assertTrue(after.all { (layer, cue) -> cue.layer == layer }, "$action put a cue on another layer: $after")
        show.current?.let { assertTrue(it in contentRowIds, "$action left the schedule on $it") }

        when (action) {
            is Action.Clear -> wholeLayers.firstOrNull { it.name.equals(action.layer, ignoreCase = true) }?.let {
                assertFalse(it in after, "$action left $it up")
            }
            is Action.ClearGroup -> if (!refused) {
                assertFalse(Layer.MESSAGES in after, "$action left the message up")
                assertFalse(Layer.SLIDE in after, "$action left the slide up")
            }
            Action.ClearAll -> assertTrue(
                !pm.anythingLive || pm.clearDisplayRequested.value,
                "$action left $after up with no clear under way",
            )
            is Action.Message -> if (!refused) {
                assertEquals(setOf(Layer.MESSAGES), after.keys - Layer.BACKGROUND, "$action left others up")
            }
            is Action.Prop -> if (!refused) action.on?.let { on ->
                assertEquals(on, "prop1" in pm.propsOnAir, "$action")
            }
            is Action.Timer -> if (!refused) assertTrue(pm.isLive(Presenting.ANNOUNCEMENTS), "$action")
            is Action.ToPreview -> if (refused) assertEquals(before, after, "$action was refused but changed the air")
            Action.NextItem -> if (wasCurrent == null) assertEquals(song.id, show.current, "$action from nothing")
            else -> Unit
        }
    }
}
