package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [TokenGate] has no UI of its own — it's the "consume this incrementing signal token exactly
 * once" bookkeeping shared by Bible auto-follow's go-live consumers. Every test drives it through
 * a real composition (`rememberTokenGate` needs one for its `remember`), via an external
 * `mutableStateOf` token that each test bumps and lets recompose with [ComposeUiTest.waitForIdle],
 * then asserts on the resulting [TokenGate] from outside composition — `consume()`/`lastHandled`
 * are plain calls, safe anywhere once the gate reference is captured.
 */
@OptIn(ExperimentalTestApi::class)
class TokenGateTest {

    @Test
    fun `consume returns false for the token value present at first composition`() = runComposeUiTest {
        lateinit var gate: TokenGate
        setContent {
            MaterialTheme {
                gate = rememberTokenGate(5)
            }
        }
        assertFalse(gate.consume(), "the value already current at first composition must not be treated as new")
    }

    @Test
    fun `consume returns true for a new non-zero token and updates lastHandled`() = runComposeUiTest {
        var externalToken by mutableStateOf(5)
        lateinit var gate: TokenGate
        setContent {
            MaterialTheme {
                gate = rememberTokenGate(externalToken)
            }
        }
        externalToken = 7
        waitForIdle()

        assertTrue(gate.consume(), "a new non-zero token must be consumable")
        assertEquals(7, gate.lastHandled, "lastHandled must reflect the just-consumed token")
    }

    @Test
    fun `consume returns false when called again without the token changing`() = runComposeUiTest {
        var externalToken by mutableStateOf(5)
        lateinit var gate: TokenGate
        setContent {
            MaterialTheme {
                gate = rememberTokenGate(externalToken)
            }
        }
        externalToken = 7
        waitForIdle()
        gate.consume()

        assertFalse(gate.consume(), "consuming the same token twice must only report true the first time")
    }

    @Test
    fun `consume returns false when the token is zero, even if it differs from lastHandled`() = runComposeUiTest {
        var externalToken by mutableStateOf(5)
        lateinit var gate: TokenGate
        setContent {
            MaterialTheme {
                gate = rememberTokenGate(externalToken)
            }
        }
        externalToken = 7
        waitForIdle()
        gate.consume()

        externalToken = 0
        waitForIdle()

        assertFalse(gate.consume(), "a zero token must never be consumable, regardless of lastHandled")
    }

    @Test
    fun `consume can fire again for a later, different non-zero token`() = runComposeUiTest {
        var externalToken by mutableStateOf(5)
        lateinit var gate: TokenGate
        setContent {
            MaterialTheme {
                gate = rememberTokenGate(externalToken)
            }
        }
        externalToken = 7
        waitForIdle()
        gate.consume()

        externalToken = 9
        waitForIdle()

        assertTrue(gate.consume(), "a later distinct token must be consumable again")
        assertEquals(9, gate.lastHandled)
    }

    @Test
    fun `the gate's state survives recomposition with an unchanged token`() = runComposeUiTest {
        var externalToken by mutableStateOf(5)
        lateinit var gate: TokenGate
        setContent {
            MaterialTheme {
                gate = rememberTokenGate(externalToken)
            }
        }
        externalToken = 9
        waitForIdle()
        gate.consume()

        // Recompose with the same token value again — remember must not reset the gate.
        externalToken = 9
        waitForIdle()

        assertFalse(gate.consume(), "remember must persist lastHandled across recomposition, not reset it")
        assertEquals(9, gate.lastHandled)
    }
}
