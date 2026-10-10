package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.window.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.churchpresenter.controlin.ControlMapping
import org.churchpresenter.controlin.OutputEvents
import org.churchpresenter.controlin.Trigger
import org.churchpresenter.controlin.TriggerKinds
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.showcontrol.Action
import java.util.concurrent.Executor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ControlMappingRunTest {

    private val scope = CoroutineScope(SupervisorJob() + Executor { }.asCoroutineDispatcher())

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a mapping's trigger runs its actions under the mapping's own key`() {
        TestSingletons.latchToTestHome()
        val root = AppRootState(
            object : ApplicationScope {
                override fun exitApplication() = Unit
            },
            scope,
            secondaryDisplays = { emptyList() },
        )
        val mapping = ControlMapping(
            id = "m1",
            trigger = Trigger(TriggerKinds.OSC, address = "/go"),
            actions = listOf(Action.Wait(60.0)),
        )
        root.runControlMapping(mapping)
        assertTrue(root.showRunner.isRunning("control:m1"))
    }

    @Test
    fun `going live and clearing are told apart from what stays on air`() {
        val songs = setOf(Presenting.LYRICS)
        val both = setOf(Presenting.LYRICS, Presenting.BIBLE)
        assertEquals(emptyList(), liveOutputEvents(emptySet(), emptySet()))
        assertEquals(emptyList(), liveOutputEvents(songs, songs))
        assertEquals(listOf(OutputEvents.GO_LIVE), liveOutputEvents(emptySet(), songs))
        assertEquals(listOf(OutputEvents.GO_LIVE), liveOutputEvents(songs, both))
        assertEquals(emptyList(), liveOutputEvents(both, songs), "one layer of two went off, nothing cleared")
        assertEquals(listOf(OutputEvents.CLEAR), liveOutputEvents(songs, emptySet()))
    }
}
