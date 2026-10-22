package org.churchpresenter.presenter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Bible layouts' fit search runs once per change to what it measures, not on every recomposition:
 * a recomposition from anything else (a band frame, an unrelated setting) reuses the scale it found.
 */
@OptIn(ExperimentalTestApi::class)
class RememberFitScaleTest {

    @Test
    fun `a recomposition with the same keys does not search again, and a changed key does`() = runComposeUiTest {
        var probes = 0
        var verse by mutableStateOf("In the beginning")
        var unrelated by mutableIntStateOf(0)
        var found = 0f
        val seen = mutableListOf<Int>()
        setContent {
            // Read so that changing it recomposes this scope without changing a key.
            seen += unrelated
            found = rememberFitScale(verse, 1920, 1080) { scale ->
                probes++
                scale <= 0.5f
            }
        }
        waitForIdle()
        val firstSearch = probes
        assertTrue(firstSearch > 0)
        assertEquals(0.5f, found, 0.01f)

        unrelated++
        waitForIdle()
        assertEquals(1, seen.last(), "the scope recomposed")
        assertEquals(firstSearch, probes, "an unrelated recomposition reuses the scale")

        verse = "For God so loved the world"
        waitForIdle()
        assertTrue(probes > firstSearch, "a changed verse searches again")
    }
}
