package org.churchpresenter.signage.hub

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.churchpresenter.signage.core.CecState
import org.churchpresenter.signage.core.OverrideAction
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.core.ScreenOverride
import org.churchpresenter.signage.core.ServerMessage
import org.churchpresenter.signage.core.UpdateState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenLinksTest {
    private var now = 1_000L
    private val links = ScreenLinks { now }

    @Test
    fun aConnectionIsListedWithWhatItCanDoUntilItCloses() {
        val session = links.opened("p", "10.0.0.5", "v1", listOf("preview"))
        assertEquals(listOf("preview"), links.links.value.getValue("p").features)
        links.closed("p", session)
        assertTrue("p" !in links.links.value)
    }

    @Test
    fun anOldConnectionClosingLeavesTheNewOne() {
        val old = links.opened("p", "a", null)
        links.opened("p", "b", null)
        links.closed("p", old)
        assertEquals("b", links.links.value.getValue("p").address)
    }

    @Test
    fun reportsAreKeptOnTheLink() {
        links.opened("p", "a", "v1")
        now = 2_000
        links.reported("p", PlayerMessage.Status(showingPlaylistId = "welcome", cec = CecState.WORKING))
        links.reported("p", PlayerMessage.Preview("/9j/", 480, 270, 5))
        links.reported("p", PlayerMessage.UpdateProgress("26.0.9", UpdateState.CHECKING))
        links.reported("p", hello())
        links.synced("p", "v2")
        val link = links.links.value.getValue("p")
        assertEquals("welcome", link.status?.showingPlaylistId)
        assertEquals(2_000L, link.statusAtMs)
        assertEquals(480, link.preview?.width)
        assertEquals(2_000L, link.previewAtMs)
        assertEquals(UpdateState.CHECKING, link.update?.state)
        assertEquals("v2", link.manifestVersion)
    }

    @Test
    fun reportsFromAScreenThatIsNotConnectedAreDropped() {
        links.reported("ghost", PlayerMessage.Status())
        links.synced("ghost", "v")
        assertTrue(links.links.value.isEmpty())
    }

    @Test
    fun messagesReachOnlyTheirScreenAndOnlyWhileConnected() = runTest {
        assertFalse(links.send("p", ServerMessage.Refresh), "not connected")
        val received = async { links.messagesFor("p").take(1).toList() }
        runCurrent()
        links.opened("p", "a", null)
        links.opened("q", "b", null)
        assertTrue(links.send("q", ServerMessage.Refresh))
        assertTrue(links.send("p", ServerMessage.Identify()))
        assertEquals(listOf<ServerMessage>(ServerMessage.Identify()), received.await())
    }

    @Test
    fun aSwitchMadeWhileAwayWaitsForTheScreen() {
        val override = ScreenOverride(OverrideAction.TvOff, setAtMs = 1)
        links.setOverride("p", override)
        assertEquals(ServerMessage.SetOverride(override), links.takeHeldOverride("p"))
        assertNull(links.takeHeldOverride("p"), "taken once")

        links.setOverride("p", null)
        links.forget("p")
        assertNull(links.takeHeldOverride("p"))

        links.opened("p", "a", null)
        links.setOverride("p", override)
        assertNull(links.takeHeldOverride("p"), "sent straight away to a connected screen")
    }
}
