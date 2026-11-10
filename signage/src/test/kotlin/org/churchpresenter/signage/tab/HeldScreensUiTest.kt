@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.signage.hub.PairingQueue
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** The cards for screens removed from here and held back, and Pair here, which asks one back. */
class HeldScreensUiTest {
    private fun cards(queue: PairingQueue, block: ComposeUiTest.() -> Unit) =
        runComposeUiTest {
            setContent {
                ChurchPresenterTheme(ThemeMode.LIGHT) {
                    Surface(color = MaterialTheme.colorScheme.background) { HeldScreenCards(HeldScreens(queue)) }
                }
            }
            block()
        }

    @Test
    fun pairHereAsksTheScreenBackAndTheCardGoes() {
        val queue = PairingQueue()
        queue.hold("player-1", "Lobby", "10.0.0.8")
        cards(queue) {
            onNodeWithText("Lobby was removed").assertExists()
            onNodeWithText("Pair here").performClick()
            onNodeWithText("Lobby was removed").assertDoesNotExist()
        }
        assertEquals(listOf("10.0.0.8"), queue.activeInvites().map { it.address })
    }

    @Test
    fun aScreenWithNoNameIsNamedByItsIdAndOnePastItsHoldIsNotShown() {
        val queue = PairingQueue()
        queue.hold("player-1", "")
        // Held long enough ago that its hold has run out by the wall clock the cards read.
        val stale = PairingQueue { System.currentTimeMillis() - PairingQueue.HOLD_MS - 1 }
        stale.hold("player-2", "Hall")
        cards(queue) { onNodeWithText("player-1 was removed").assertExists() }
        cards(stale) { onNodeWithText("Hall was removed").assertDoesNotExist() }
    }
}
