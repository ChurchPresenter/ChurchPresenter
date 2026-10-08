package org.churchpresenter.app.churchpresenter

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.Macro
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the main screen's input holders do with a callback nobody passed: nothing. A root built in a
 * test, or by a caller that does not wire a feature, relies on these being safe to call.
 */
class MainDesktopInputsDefaultsTest {

    private val row = ScheduleItem.AnnouncementItem("a1", "Welcome")

    @Test
    fun `the live output callbacks left out do nothing, and dev mode is off`() {
        val live = LiveOutputCallbacks(presenting = {}, onVerseSelected = {}, onSongItemSelected = {})
        live.onAllSectionsChanged(emptyList())
        live.onSectionIndexChanged(1)
        live.onLineIndexChanged(1)
        live.onRowWentLive(row)
        live.onRowActions(row, emptyList())
        live.onRunMacro(Macro("m1", "Walk in"))
        assertNull(live.controlHub)
        assertEquals(false, live.devMode)
    }

    @Test
    fun `the sidebar's show control left out runs no macro`() {
        SidebarShowControl().onRunMacro(Macro("m1", "Walk in"))
    }

    @Test
    fun `a schedule with no planned service loads, saves and fires nothing`() {
        val plan = ServicePlanLink()
        plan.onLoadServiceNow(true)
        plan.onSaveScheduleToCalendar()
        plan.onPresentCue(ScheduleItem.CueItem(id = "c1", action = ""))
        assertNull(plan.typicalSongSeconds(SongItem(number = "1", title = "A Song")))
        assertNull(plan.onAddScheduleToCalendar)
    }

    @Test
    fun `an unlinked instance connects and disconnects nowhere`() {
        val link = InstanceLinkBridge()
        link.onConnect()
        link.onDisconnect()
        assertNull(link.onSecondaryBibleFilePathChanged)
    }

    @Test
    fun `web access left out starts no tunnel and keeps no display url`() {
        val web = WebAccessState()
        web.onStartTunnel()
        web.onStopTunnel()
        web.onQaDisplayUrlChanged("http://example.invalid/qa")
        web.onPresentationDisplayUrlChanged("http://example.invalid/present")
        web.onFreezeToggle()
        web.onClearPresentation()
        assertEquals("", web.presentationDisplayUrl)
    }
}
