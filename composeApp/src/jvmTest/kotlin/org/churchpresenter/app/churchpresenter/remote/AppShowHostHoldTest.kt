package org.churchpresenter.app.churchpresenter.remote

import kotlinx.coroutines.runBlocking
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.ScreenHold
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.showcontrol.ActionRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The `holdScreen` action: a screen by its number or all, held, blanked, released or toggled. */
class AppShowHostHoldTest {

    private val pm = PresenterManager(showPresenterWindowInitially = false).apply { setScreenCount(2) }
    private val log = mutableListOf<String>()

    private val host = AppShowHost(pm, { AppSettings() }, ShowOutlets(log = { log += it }))

    private fun run(action: Action) = runBlocking { ActionRunner(host, this).perform(listOf(action)) }

    private fun refused(action: Action): String {
        log.clear()
        run(action)
        return log.single().substringAfter(": ")
    }

    @Test
    fun `a screen is held by its number from one, blanked, and released`() {
        run(Action.HoldScreen("2", "hold", on = true))
        assertEquals(mapOf(1 to ScreenHold.HOLD), pm.screenHolds.value)
        run(Action.HoldScreen(" 1 ", "BLANK", on = true))
        assertEquals(mapOf(0 to ScreenHold.BLANK, 1 to ScreenHold.HOLD), pm.screenHolds.value)
        run(Action.HoldScreen("2", on = false))
        assertEquals(mapOf(0 to ScreenHold.BLANK), pm.screenHolds.value)
    }

    @Test
    fun `with no on, a screen toggles`() {
        run(Action.HoldScreen("1"))
        assertEquals(mapOf(0 to ScreenHold.HOLD), pm.screenHolds.value)
        run(Action.HoldScreen("1"))
        assertTrue(pm.screenHolds.value.isEmpty())
    }

    @Test
    fun `all holds every screen, and toggles off only once every one is held`() {
        pm.setScreenHold(0, ScreenHold.HOLD)
        run(Action.HoldScreen("all"))
        assertEquals(mapOf(0 to ScreenHold.HOLD, 1 to ScreenHold.HOLD), pm.screenHolds.value)
        run(Action.HoldScreen("ALL"))
        assertTrue(pm.screenHolds.value.isEmpty())
        run(Action.HoldScreen("all", "blank", on = true))
        assertEquals(mapOf(0 to ScreenHold.BLANK, 1 to ScreenHold.BLANK), pm.screenHolds.value)
        run(Action.HoldScreen("all", on = false))
        assertTrue(pm.screenHolds.value.isEmpty())
    }

    @Test
    fun `a screen or mode that is not there is refused`() {
        assertEquals("No screen called stage", refused(Action.HoldScreen("stage")))
        assertEquals("No hold mode called freeze", refused(Action.HoldScreen("1", "freeze")))
        assertTrue(pm.screenHolds.value.isEmpty())
    }
}
