package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.window.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.Macro
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.showcontrol.MediaCommand
import java.util.concurrent.Executor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShowControlRootTest {

    private val queued = ArrayDeque<Runnable>()
    private val scope = CoroutineScope(SupervisorJob() + Executor { queued.addLast(it) }.asCoroutineDispatcher())
    private val root = AppRootState(
        object : ApplicationScope {
            override fun exitApplication() = Unit
        },
        scope,
        secondaryDisplays = { emptyList() },
    )
    private val obs = listOf<Action>(Action.ObsScene("Wide"))
    private val first = ScheduleItem.AnnouncementItem("r1", "Welcome")
    private val second = ScheduleItem.AnnouncementItem("r2", "Notices")

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `the host finds a saved macro by name and logs what it could not run`() {
        root.appSettings = root.appSettings.copy(macros = listOf(Macro("m1", "Walk in", obs)))
        val host = root.appShowHost()
        assertEquals(obs, host.macro("Walk in"))
        assertNull(host.macro("Missing"))
        host.reportError(obs.first(), IllegalStateException("offline"))
    }

    @Test
    fun `media, OBS, ATEM and Companion actions reach their outlets`() = runBlocking {
        val host = root.appShowHost()
        host.media(MediaCommand.STOP)
        host.obsScene("Wide")
        assertFailsWith<IllegalArgumentException> { host.atemMacro(1) }
        val error = assertFailsWith<IllegalArgumentException> { host.companion(Action.CompanionPress("c1", 3)) }
        assertEquals("No Companion surface for c1", error.message)
    }

    @Test
    fun `go live from an action selects the row and makes it the live one`() = runBlocking {
        val selected = mutableListOf<String>()
        root.scheduleActions = ScheduleActions(selectItem = { selected += it })
        root.currentScheduleItems = listOf(first, second)
        root.appShowHost().goLive(Action.GoLive(rowId = "r2"))
        assertEquals(listOf("r2"), selected)
        assertEquals("r2", root.lastLiveRowId)
    }

    @Test
    fun `to preview is refused with preview mode off and cues the row with it on`() = runBlocking {
        root.currentScheduleItems = listOf(first)
        val host = root.appShowHost()
        assertFailsWith<IllegalArgumentException> { host.toPreview(Action.ToPreview(rowId = "r1")) }
        root.appSettings = root.appSettings.copy(
            projectionSettings = root.appSettings.projectionSettings.copy(previewModeEnabled = true),
        )
        host.toPreview(Action.ToPreview(rowId = "r1"))
    }

    @Test
    fun `next steps from the selected row and starts that row's own actions`() = runBlocking {
        root.scheduleActions = ScheduleActions(currentActions = { mapOf("r2" to obs) })
        root.currentScheduleItems = listOf(first, second)
        root.selectedScheduleItemId = "r1"
        root.appShowHost().next()
        assertEquals("r2", root.lastLiveRowId)
        assertTrue(root.showRunner.isRunning("r2"))
    }

    @Test
    fun `a row's actions and a macro are handed to the runner under their own keys`() {
        root.runRowActions(first, obs)
        assertTrue(root.showRunner.isRunning("r1"))
        root.runRowActionsNow(second)
        assertTrue(!root.showRunner.isRunning("r2"))
        root.runMacro(Macro("m1", "Walk in", obs))
        assertTrue(root.showRunner.isRunning("macro:m1"))
    }
}
